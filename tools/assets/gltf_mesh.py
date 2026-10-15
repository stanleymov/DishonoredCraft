"""Minimal glTF reader + CPU skinning + software preview renderer for umodel exports (private assets only)."""
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

COMP = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


class Gltf:
    def __init__(self, path):
        self.path = Path(path)
        self.g = json.loads(self.path.read_text())
        self.bin = (self.path.parent / self.g["buffers"][0]["uri"]).read_bytes()

    def acc(self, i):
        a = self.g["accessors"][i]
        v = self.g["bufferViews"][a["bufferView"]]
        n = NCOMP[a["type"]]
        fmt = COMP[a["componentType"]]
        size = struct.calcsize(fmt)
        stride = v.get("byteStride", size * n)
        off = v.get("byteOffset", 0) + a.get("byteOffset", 0)
        out = np.zeros((a["count"], n), dtype=np.float64)
        for k in range(a["count"]):
            out[k] = struct.unpack_from("<" + fmt * n, self.bin, off + k * stride)
        if a.get("normalized") and fmt in "BH":
            out /= 255.0 if fmt == "B" else 65535.0
        return out

    def node_matrix(self, node):
        if "matrix" in node:
            return np.array(node["matrix"]).reshape(4, 4).T
        t = np.array(node.get("translation", [0, 0, 0]))
        r = node.get("rotation", [0, 0, 0, 1])
        s = np.array(node.get("scale", [1, 1, 1]))
        return trs(t, r, s)

    def mesh(self):
        """Concatenated primitives: positions, normals, uvs, joints, weights, triangle indices."""
        P, N, U, J, W, I = [], [], [], [], [], []
        base = 0
        for m in self.g["meshes"]:
            for p in m["primitives"]:
                at = p["attributes"]
                pos = self.acc(at["POSITION"])
                P.append(pos)
                N.append(self.acc(at["NORMAL"]) if "NORMAL" in at else np.zeros_like(pos))
                U.append(self.acc(at["TEXCOORD_0"]))
                J.append(self.acc(at["JOINTS_0"]) if "JOINTS_0" in at else np.zeros((len(pos), 4)))
                W.append(self.acc(at["WEIGHTS_0"]) if "WEIGHTS_0" in at else np.tile([1, 0, 0, 0], (len(pos), 1)))
                I.append(self.acc(p["indices"]).astype(int).reshape(-1, 3) + base)
                base += len(pos)
        return np.vstack(P), np.vstack(N), np.vstack(U), np.vstack(J).astype(int), np.vstack(W), np.vstack(I)

    def skeleton(self):
        skin = self.g["skins"][0]
        joints = skin["joints"]
        names = [self.g["nodes"][j]["name"] for j in joints]
        parent = {}
        for i, n in enumerate(self.g["nodes"]):
            for c in n.get("children", []):
                parent[c] = i
        local = [self.node_matrix(self.g["nodes"][j]) for j in joints]
        par = [joints.index(parent[j]) if parent.get(j) in joints else -1 for j in joints]
        ibm = self.acc(skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)
        return names, par, local, ibm


def quat_to_mat(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def trs(t, r, s):
    m = np.eye(4)
    m[:3, :3] = quat_to_mat(r) * s
    m[:3, 3] = t
    return m


def rot(axis, deg):
    a = np.radians(deg)
    c, s = np.cos(a), np.sin(a)
    m = np.eye(4)
    if axis == "x":
        m[1:3, 1:3] = [[c, -s], [s, c]]
    elif axis == "y":
        m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    else:
        m[0:2, 0:2] = [[c, -s], [s, c]]
    return m


def pose_world(par, local, overrides=None):
    """World matrices with optional extra local rotations: overrides[i] = 4x4 applied after the bind local."""
    world = [None] * len(local)
    for i in range(len(local)):
        m = local[i] @ (overrides.get(i, np.eye(4)) if overrides else np.eye(4))
        world[i] = m if par[i] < 0 else world[par[i]] @ m
    return world


def skin(P, N, J, W, world, ibm):
    skinmats = np.stack([world[i] @ ibm[i] for i in range(len(world))])
    out_p = np.zeros_like(P)
    out_n = np.zeros_like(N)
    ph = np.hstack([P, np.ones((len(P), 1))])
    for k in range(4):
        m = skinmats[J[:, k]]
        w = W[:, k:k + 1]
        out_p += w * np.einsum("nij,nj->ni", m, ph)[:, :3]
        out_n += w * np.einsum("nij,nj->ni", m[:, :3, :3], N)
    nl = np.linalg.norm(out_n, axis=1, keepdims=True)
    return out_p, out_n / np.maximum(nl, 1e-9)


def preview(P, U, I, tex, view, size=512, scale=200.0, out="preview.png"):
    """Painter's-algorithm preview. view: 4x4 world->view, camera looks down -Z, +Y up."""
    texa = np.asarray(tex.convert("RGB"))
    th, tw = texa.shape[:2]
    ph = np.hstack([P, np.ones((len(P), 1))]) @ view.T
    img = Image.new("RGB", (size, size), (40, 44, 52))
    d = ImageDraw.Draw(img)
    order = np.argsort([ph[t, 2].mean() for t in I])
    for ti in order:
        t = I[ti]
        z = ph[t, 2]
        if (z > -0.01).any():
            continue
        pts = [(size / 2 + ph[v, 0] / -ph[v, 2] * scale, size / 2 - ph[v, 1] / -ph[v, 2] * scale) for v in t]
        uv = U[t].mean(axis=0)
        c = texa[int(np.clip(uv[1], 0, 0.999) * th), int(np.clip(uv[0] % 1, 0, 0.999) * tw)]
        d.polygon(pts, fill=tuple(int(x) for x in c))
    img.save(out)
    return img
