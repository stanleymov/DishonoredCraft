"""Carve Wwise soundbanks (.bnk) and their embedded .wem files out of Dishonored's AKPK .pck packs.

Runs on the user's own install. Output stays outside the repo (default: ~/dishonored-extract/audio).
    python tools/assets/akpk_extract.py <pck> [<pck> ...] [--out DIR]
"""
import argparse
import struct
from pathlib import Path
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

CHUNKS = {b"BKHD", b"DIDX", b"DATA", b"HIRC", b"STID", b"STMG", b"ENVS", b"FXPR", b"INIT", b"PLAT", b"DMEM"}


def carve_banks(blob: bytes):
    pos = 0
    while True:
        start = blob.find(b"BKHD", pos)
        if start < 0:
            return
        cur = start
        chunks = {}
        while cur + 8 <= len(blob) and blob[cur:cur + 4] in CHUNKS:
            tag = blob[cur:cur + 4]
            size = struct.unpack_from("<I", blob, cur + 4)[0]
            chunks[tag] = (cur + 8, size)
            cur += 8 + size
        yield start, cur, chunks
        pos = max(cur, start + 4)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pck", nargs="+")
    ap.add_argument("--out", default=str(EXTRACT_DIR / "audio"))
    args = ap.parse_args()
    out = Path(args.out)
    for pck in args.pck:
        pck = Path(pck)
        blob = pck.read_bytes()
        dest = out / pck.stem
        (dest / "wem").mkdir(parents=True, exist_ok=True)
        banks = 0
        wems = 0
        for start, end, chunks in carve_banks(blob):
            bank_id = struct.unpack_from("<I", blob, chunks[b"BKHD"][0] + 4)[0]
            (dest / f"{bank_id}.bnk").write_bytes(blob[start:end])
            banks += 1
            if b"DIDX" in chunks and b"DATA" in chunks:
                didx_off, didx_size = chunks[b"DIDX"]
                data_off, _ = chunks[b"DATA"]
                for i in range(didx_size // 12):
                    wid, off, size = struct.unpack_from("<III", blob, didx_off + i * 12)
                    (dest / "wem" / f"{wid}.wem").write_bytes(blob[data_off + off:data_off + off + size])
                    wems += 1
        # Streamed .wem files live in the AKPK "sounds" table, addressed by id.
        wems += extract_stream_table(blob, dest / "wem")
        # wwiser's .txtp reference "wem/<bank>.bnk" for in-memory sounds, so keep a copy of the bank there too.
        for bnk in dest.glob("*.bnk"):
            (dest / "wem" / bnk.name).write_bytes(bnk.read_bytes())
        print(f"{pck.name}: {banks} banks, {wems} wems -> {dest}")


def extract_stream_table(blob: bytes, out: Path) -> int:
    """AKPK header: magic, header size, version, then sizes of the language map, bank, sound and externals tables.
    Each table: u32 count, then entries of (id, block size, file size, start block, language id)."""
    if blob[:4] != b"AKPK":
        return 0
    # Dishonored's version-1 packs already carry the externals-size field: magic, header size, version,
    # then four table sizes (languages, banks, sounds, externals), so the tables start at byte 28.
    _, version, lang_size, banks_size, sounds_size = struct.unpack_from("<IIIII", blob, 4)
    sounds_off = 28 + lang_size + banks_size
    count = struct.unpack_from("<I", blob, sounds_off)[0]
    written = 0
    for i in range(count):
        wid, block, size, start, _lang = struct.unpack_from("<IIIII", blob, sounds_off + 4 + i * 20)
        off = start * block
        data = blob[off:off + size]
        if data[:4] == b"RIFF":
            (out / f"{wid}.wem").write_bytes(data)
            written += 1
    return written


if __name__ == "__main__":
    main()
