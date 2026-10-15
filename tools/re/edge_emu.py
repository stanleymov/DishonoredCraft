"""Decode Dishonored's Edge (EA05) animations by running the game's own decoder (Dishonored.exe 0x9dada0)
inside the Unicorn CPU emulator. Reads the user's own install; nothing from the game is redistributed.

    from edge_emu import EdgeDecoder
    dec = EdgeDecoder()
    poses = dec.sample(stream_bytes)   # list over frames of {joint_index: (quat xyzw, trans xyz)}
"""
import struct

import pefile
from unicorn import UC_ARCH_X86, UC_HOOK_MEM_INVALID, UC_MODE_32, Uc, UcError
from unicorn.x86_const import UC_X86_REG_ESP

EXE = __import__("os").environ.get("DISHONORED_EXE", r"C:\Program Files (x86)\Steam\steamapps\common\Dishonored\Binaries\Win32\Dishonored.exe")
DECODE_FRAME = 0x9DADA0
STOP = 0x0F000000
STACK, STACK_SIZE = 0x0E000000, 0x00200000
HEAP, HEAP_SIZE = 0x0D000000, 0x00800000


def align(x, a):
    return (x + a - 1) // a * a


class EdgeDecoder:
    def __init__(self, exe=EXE):
        pe = pefile.PE(exe)
        base = pe.OPTIONAL_HEADER.ImageBase
        size = align(pe.OPTIONAL_HEADER.SizeOfImage, 0x1000)
        self.uc = Uc(UC_ARCH_X86, UC_MODE_32)
        self.uc.mem_map(base, size)
        raw = open(exe, "rb").read()
        self.uc.mem_write(base, raw[:pe.OPTIONAL_HEADER.SizeOfHeaders])
        for s in pe.sections:
            data = raw[s.PointerToRawData:s.PointerToRawData + s.SizeOfRawData]
            self.uc.mem_write(base + s.VirtualAddress, data[:max(s.Misc_VirtualSize, len(data))])
        self.image_snapshot = None
        self.uc.mem_map(STACK, STACK_SIZE)
        self.uc.mem_map(HEAP, HEAP_SIZE)
        self.uc.mem_map(STOP, 0x1000)
        self.uc.mem_write(STOP, b"\xc3")  # never executed: emulation stops on reaching it
        self.uc.hook_add(UC_HOOK_MEM_INVALID, self._bad_mem)
        self.last_error = None

    def _bad_mem(self, uc, access, address, size, value, data):
        self.last_error = f"invalid memory access {access} at {address:#x} size {size}"
        return False

    def _call(self, fn, args):
        esp = STACK + STACK_SIZE - 0x1000
        frame = struct.pack("<I", STOP) + b"".join(
            struct.pack("<f", a) if isinstance(a, float) else struct.pack("<I", a & 0xFFFFFFFF) for a in args)
        esp -= len(frame)
        esp &= ~0xF
        esp -= 4  # entry convention: [esp] = return address, 16-byte aligned args like a real call
        self.uc.mem_write(esp, frame)
        self.uc.reg_write(UC_X86_REG_ESP, esp)
        self.uc.emu_start(fn, STOP, timeout=20_000_000)

    @staticmethod
    def header(stream):
        (tag, duration, freq, size_header, num_joints, num_frames, num_sets) = struct.unpack_from("<IffHHHH", stream, 0)
        return dict(duration=duration, freq=freq, size_header=size_header, joints=num_joints, frames=num_frames, sets=num_sets)

    def decode_frame(self, stream, frame):
        """Evaluate the animation at an integer frame. Returns (joint_rows, written_mask) where joint_rows[j] =
        (q, t, s) as tuples of 4 floats, straight from the game's output buffer."""
        h = self.header(stream)
        anim = HEAP + 0x1000
        out = HEAP + 0x200000
        user = HEAP + 0x300000
        self.uc.mem_write(HEAP, b"\0" * HEAP_SIZE)
        self.uc.mem_write(anim, stream + b"\0" * 256)
        marker = struct.pack("<f", float("nan"))
        self.uc.mem_write(out, marker * (h["joints"] * 12 + 64))

        info_ptr = 0x3C + struct.unpack_from("<I", stream, 0x3C)[0]
        dma_ptr = 0x38 + struct.unpack_from("<I", stream, 0x38)[0]
        starts = [struct.unpack_from("<HH", stream, info_ptr + 4 * i) for i in range(h["sets"])]
        s = 0
        for i, (start, _last) in enumerate(starts):
            if frame >= start:
                s = i
        start, last = starts[s]
        offset = struct.unpack_from("<I", stream, dma_ptr + 8 * s + 4)[0]
        local = frame - start
        alpha = 0.0
        if local > last:
            local, alpha = last, 1.0
        self.last_error = None
        try:
            self._call(DECODE_FRAME, [out, user, anim, anim + offset, last, local, alpha])
        except UcError as e:
            raise RuntimeError(f"emulation failed: {e} {self.last_error or ''}")
        rows = []
        raw = self.uc.mem_read(out, h["joints"] * 48)
        for j in range(h["joints"]):
            q = struct.unpack_from("<4f", raw, j * 48)
            t = struct.unpack_from("<4f", raw, j * 48 + 16)
            sc = struct.unpack_from("<4f", raw, j * 48 + 32)
            rows.append((q, t, sc))
        return rows

    def sample(self, stream):
        h = self.header(stream)
        return [self.decode_frame(stream, f) for f in range(h["frames"])]
