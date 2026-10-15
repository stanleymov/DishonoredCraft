"""Pull Edge (EA05) animation streams out of a decompressed Dishonored package for analysis."""
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from props import read_props  # noqa: E402
from upk import Upk  # noqa: E402
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

PKG = EXTRACT_DIR / "raw/Startup.upk"


def sequences(path=PKG):
    u = Upk(str(path))
    out = {}
    for e in u.exports:
        if e["class"] != "AnimSequence":
            continue
        d = u.data(e)
        p, o = read_props(u, d, 4)
        _offsets_count = struct.unpack_from("<i", d, o)[0]
        n = struct.unpack_from("<i", d, o + 4)[0]
        stream = d[o + 8:o + 8 + n]
        name = p.get("SequenceName", e["name"])
        set_name = u.obj_name(e["outer"])
        out[(set_name, name)] = {"props": p, "stream": stream, "frames": p.get("NumFrames"), "length": p.get("SequenceLength")}
    return u, out


if __name__ == "__main__":
    u, seqs = sequences()
    tags = {}
    for k, v in seqs.items():
        t = v["stream"][:4]
        tags[t] = tags.get(t, 0) + 1
    print("stream tags:", tags)
    want = sys.argv[1] if len(sys.argv) > 1 else "Powers_Cast_BendTime"
    for (s, n), v in seqs.items():
        if n == want:
            b = v["stream"]
            print(s, n, "frames", v["frames"], "len", v["length"], "bytes", len(b))
            for o in range(0, 0x150, 4):
                w = struct.unpack_from("<I", b, o)[0]
                h0, h1 = struct.unpack_from("<HH", b, o)
                print(f"{o:04x}: {b[o:o+4].hex()}  u32={w:<10} u16={h0},{h1}  ->self+{o + w:#06x}" if w < len(b) else f"{o:04x}: {b[o:o+4].hex()}  u32={w}")
