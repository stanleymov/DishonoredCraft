"""Decompress an LZO-compressed UE3 package (Dishonored) into a flat .upk for research tools.

    python ue3_decompress.py <in.upk> <out.upk>
"""
import struct
import sys


def lzo1x_decompress(src: bytes, out_len: int) -> bytes:
    dst = bytearray()
    ip = 0
    n = len(src)

    def copy_match(m_pos, length):
        for _ in range(length):
            dst.append(dst[m_pos])
            m_pos += 1

    state = 0
    if src[0] > 17:
        t = src[0] - 17
        ip = 1
        dst += src[ip:ip + t]
        ip += t
        state = t if t < 4 else 4
    while ip < n:
        t = src[ip]
        ip += 1
        if t < 16:
            if state == 0:
                if t == 0:
                    while src[ip] == 0:
                        t += 255
                        ip += 1
                    t += 15 + src[ip]
                    ip += 1
                t += 3
                dst += src[ip:ip + t]
                ip += t
                state = 4
                continue
            if state != 4:
                m_pos = len(dst) - 1 - (t >> 2) - (src[ip] << 2)
                ip += 1
                copy_match(m_pos, 2)
            else:
                m_pos = len(dst) - 1 - 0x800 - (t >> 2) - (src[ip] << 2)
                ip += 1
                copy_match(m_pos, 3)
            state = t & 3
        elif t >= 64:
            m_pos = len(dst) - 1 - ((t >> 2) & 7) - (src[ip] << 3)
            ip += 1
            copy_match(m_pos, (t >> 5) + 1)
            state = t & 3
        elif t >= 32:
            length = t & 31
            if length == 0:
                while src[ip] == 0:
                    length += 255
                    ip += 1
                length += 31 + src[ip]
                ip += 1
            d = src[ip] | (src[ip + 1] << 8)
            ip += 2
            copy_match(len(dst) - 1 - (d >> 2), length + 2)
            state = d & 3
        else:
            length = t & 7
            if length == 0:
                while src[ip] == 0:
                    length += 255
                    ip += 1
                length += 7 + src[ip]
                ip += 1
            d = src[ip] | (src[ip + 1] << 8)
            ip += 2
            m_pos = len(dst) - ((t & 8) << 11) - (d >> 2)
            if m_pos == len(dst):
                break
            copy_match(m_pos - 0x4000, length + 2)
            state = d & 3
        if state:
            dst += src[ip:ip + state]
            ip += state
    if len(dst) != out_len:
        raise ValueError(f"LZO size mismatch {len(dst)} != {out_len}")
    return bytes(dst)


def read_chunk(b, comp_off):
    tag, block_size, comp_total, uncomp_total = struct.unpack_from("<IIII", b, comp_off)
    assert tag == 0x9E2A83C1, hex(tag)
    blocks = []
    o = comp_off + 16
    remaining = uncomp_total
    while remaining > 0:
        c, u = struct.unpack_from("<II", b, o)
        o += 8
        blocks.append((c, u))
        remaining -= u
    out = bytearray()
    for c, u in blocks:
        out += lzo1x_decompress(b[o:o + c], u)
        o += c
    return bytes(out)


def decompress_package(b: bytes) -> bytes:
    o = 12
    n = struct.unpack_from("<i", b, o)[0]
    o += 4 + n + 32  # folder, flags + 7 table fields
    o += 4 * 4 + 16  # import/export guid offsets/counts, thumbnail offset, guid
    gens = struct.unpack_from("<i", b, o)[0]
    o += 4 + gens * 12
    o += 8  # engine + cooker version
    comp_flags_off = o
    comp_flags, nchunks = struct.unpack_from("<II", b, o)
    o += 8
    chunks = [struct.unpack_from("<IIII", b, o + i * 16) for i in range(nchunks)]
    assert comp_flags == 2, comp_flags
    first = min(c[0] for c in chunks)
    out = bytearray(b[:first])
    struct.pack_into("<II", out, comp_flags_off, 0, 0)  # mark as uncompressed (chunk list left in place)
    for uoff, usize, coff, csize in sorted(chunks):
        data = read_chunk(b, coff)
        assert len(data) == usize
        assert len(out) == uoff, (len(out), uoff)
        out += data
        print(f"  chunk @{uoff}: {usize} bytes", flush=True)
    return bytes(out)


if __name__ == "__main__":
    src = open(sys.argv[1], "rb").read()
    open(sys.argv[2], "wb").write(decompress_package(src))
    print("ok")
