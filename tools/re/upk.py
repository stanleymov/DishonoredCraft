"""Minimal reader for decompressed UE3 packages (Dishonored: version 801, licensee 30). Read-only research tool."""
import struct


class Upk:
    def __init__(self, path):
        self.b = open(path, "rb").read()
        b = self.b
        o = 12
        n = struct.unpack_from("<i", b, o)[0]
        o += 4 + n
        (self.flags, nc, no, ec, eo, ic, io, _do) = struct.unpack_from("<8I", b, o)
        self.names = self._names(nc, no)
        self.imports = self._imports(ic, io)
        self.exports = self._exports(ec, eo)

    def _names(self, count, off):
        out = []
        for _ in range(count):
            n = struct.unpack_from("<i", self.b, off)[0]
            off += 4
            if n < 0:
                s = self.b[off:off - n * 2].decode("utf-16-le").rstrip("\0")
                off += -n * 2
            else:
                s = self.b[off:off + n].decode("latin-1").rstrip("\0")
                off += n
            off += 8  # flags
            out.append(s)
        return out

    def name(self, idx, number=0):
        s = self.names[idx]
        return s if number == 0 else f"{s}_{number - 1}"

    def _imports(self, count, off):
        out = []
        for _ in range(count):
            cp, _, cn, _, pkg, on, onum = struct.unpack_from("<iiiiiii", self.b, off)
            out.append({"class": self.names[cn], "name": self.name(on, onum), "outer": pkg})
            off += 28
        return out

    def _exports(self, count, off):
        out = []
        for _ in range(count):
            cls, sup, outer, on, onum, arch = struct.unpack_from("<iiiiii", self.b, off)
            off += 24
            flags = struct.unpack_from("<Q", self.b, off)[0]
            off += 8
            size, soff, eflags = struct.unpack_from("<iiI", self.b, off)
            off += 12
            nnet = struct.unpack_from("<i", self.b, off)[0]
            off += 4 + 4 * nnet + 16 + 4
            out.append({"class": self.obj_name(cls), "name": self.name(on, onum), "outer": outer, "size": size, "offset": soff})
        return out

    def obj_name(self, idx):
        if idx == 0:
            return "Class"
        if idx < 0:
            return self.imports[-idx - 1]["name"]
        return self.exports[idx - 1]["name"] if idx - 1 < len(getattr(self, "exports", [])) else f"export{idx}"

    def data(self, export):
        return self.b[export["offset"]:export["offset"] + export["size"]]
