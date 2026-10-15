"""Apply decoded Dishonored animations (Edge, via edge_emu) to the umodel-exported Skm_Player skeleton.

Conventions established from the data:
  * Edge joints are in *skeleton order*: the Skm_Player bone list filtered to the AnimSet's TrackBoneNames
  * UE -> umodel glTF space mirrors y/z: rotation (x, y, z, w) -> (-x, -z, -y, w)
  * the player AnimSets are bAnimRotationOnly, so translations stay at the bind pose
"""
import math
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).parent.parent / "assets"))
from edge_dump import sequences  # noqa: E402
from edge_emu import EdgeDecoder  # noqa: E402
from gltf_mesh import quat_to_mat  # noqa: E402


def track_names(u, set_name):
    from props import read_props
    for e in u.exports:
        if e["class"] == "AnimSet" and e["name"] == set_name:
            p, _ = read_props(u, u.data(e), 4)
            raw = p["TrackBoneNames"][2]
            n = struct.unpack_from("<i", raw, 0)[0]
            return [u.name(*struct.unpack_from("<ii", raw, 4 + 8 * i)) for i in range(n)]
    raise KeyError(set_name)


def to_gltf_quat(q):
    x, y, z, w = q
    return (-x, -z, -y, w)


class AnimLibrary:
    def __init__(self, skeleton_names=None):
        self.u, self.seqs = sequences()
        self.dec = EdgeDecoder()
        self._tracks = {}
        self.skeleton = skeleton_names

    def tracks(self, set_name):
        """Bone name for each Edge joint index of this AnimSet."""
        if set_name not in self._tracks:
            names = set(track_names(self.u, set_name))
            self._tracks[set_name] = [b for b in self.skeleton if b in names]
        return self._tracks[set_name]

    def frames(self, set_name, seq_name):
        """List over frames of {bone_name: glTF-space local quaternion (x, y, z, w)}."""
        stream = self.seqs[(set_name, seq_name)]["stream"]
        names = self.tracks(set_name)
        out = []
        for rows in self.dec.sample(stream):
            pose = {}
            for j, (q, _t, _s) in enumerate(rows):
                if j >= len(names) or any(math.isnan(v) for v in q):
                    continue
                pose[names[j]] = to_gltf_quat(q)
            out.append(pose)
        return out


def pose_world_anim(names, par, local, anim_pose):
    """World matrices with animated local rotations replacing the bind rotations (translations kept)."""
    world = [None] * len(local)
    for i in range(len(local)):
        m = local[i].copy()
        q = anim_pose.get(names[i])
        if q is not None:
            scale = np.linalg.norm(m[:3, :3], axis=0)
            m[:3, :3] = quat_to_mat(q) * scale
        world[i] = m if par[i] < 0 else world[par[i]] @ m
    return world
