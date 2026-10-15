"""UE3 (v801) tagged-property parser. Returns (props, offset_after_None)."""
import struct


def read_props(u, data, o=0):
    props = {}
    while True:
        ni, nn = struct.unpack_from("<ii", data, o)
        name = u.name(ni, nn)
        o += 8
        if name == "None":
            return props, o
        ti, tn = struct.unpack_from("<ii", data, o)
        typ = u.name(ti, tn)
        o += 8
        size, idx = struct.unpack_from("<ii", data, o)
        o += 8
        extra = None
        if typ == "StructProperty":
            extra = u.name(*struct.unpack_from("<ii", data, o))
            o += 8
        elif typ == "BoolProperty":
            props[name] = bool(data[o])
            o += 1
            continue
        elif typ == "ByteProperty":
            extra = u.name(*struct.unpack_from("<ii", data, o))
            o += 8
        raw = data[o:o + size]
        o += size
        if typ == "IntProperty":
            val = struct.unpack("<i", raw)[0]
        elif typ == "FloatProperty":
            val = struct.unpack("<f", raw)[0]
        elif typ in ("NameProperty",):
            val = u.name(*struct.unpack("<ii", raw))
        elif typ == "ByteProperty" and size == 8:
            val = u.name(*struct.unpack("<ii", raw))
        elif typ == "ObjectProperty":
            val = struct.unpack("<i", raw)[0]
        else:
            val = (typ, extra, raw)
        key = name if idx == 0 else f"{name}[{idx}]"
        props[key] = val
