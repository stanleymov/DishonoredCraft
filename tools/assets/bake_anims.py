"""Bake Corvo's real first-person rig + Dishonored's original animations into the private resource pack.

Animations are decoded by running Dishonored.exe's own Edge decoder in an emulator (tools/re/edge_emu.py),
from the player's own install. Output goes into the pack only (never the mod jar or git):
  assets/dishonored/fp/rig.json            arms mesh (skinned), skeleton, inverse binds
  assets/dishonored/fp/weapon_<name>.json  weapon meshes with their own skeletons
  assets/dishonored/fp/anims.json          every animation: fps, frame count, bones, quaternions per frame
  assets/dishonored/textures/fp/*.png      textures

    python tools/assets/bake_anims.py --pack "<gameDir>/resourcepacks/Dishonored Original Assets"
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "re"))
from anim_pose import AnimLibrary, track_names, to_gltf_quat  # noqa: E402
from gltf_mesh import Gltf  # noqa: E402
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

E = EXTRACT_DIR

ARM_ANIMS = {
    "Ply_Empty_Idle_as": ["Empty_Idle", "Empty_Equip", "Empty_UnEquip"],
    "Ply_Sword_Idle_as": ["Sword_Ready_Equip", "Sword_Ready_UnEquip", "Sword_Ready_Idle", "Sword_Sneak_Idle", "Sword_Sneak_In", "Sword_Sneak_Out"],
    "Ply_Sword_Locomotion_as": ["Sword_Ready_Walk", "Sword_Sneak_Walk", "Sword_Ready_Sprint", "Sword_Ready_Jump",
                                "Sword_Ready_MantleLow", "Sword_Ready_MantleMedium", "Sword_Ready_MantleHigh",
                                "Sword_Sneak_MantleLow", "Sword_Sneak_MantleMedium", "Sword_Sneak_MantleHigh"],
    "Ply_Empty_Locomotion_as": ["Empty_MantleLow", "Empty_MantleMedium", "Empty_MantleHigh"],
    "Ply_Sword_Assassination_as": ["Sword_Ready_Assassination_DropFront_Master", "Sword_Ready_Assassination_Back_Master",
                                   "Sword_Ready_Assassination_Front_Master", "Sword_Ready_Assassination_FastBack_Master"],
    "Ply_Sword_Choke_as": ["Sword_Choke_In_Master", "Sword_Choke_Loop_Master", "Sword_Choke_Win_Master"],
    "Ply_Sword_Attack_as": ["Sword_Ready_AttackRight_A_Small", "Sword_Ready_AttackLeft_A_Small", "Sword_Ready_AttackRight_B_Small",
                            "Sword_Ready_AttackLeft_B_Small", "Sword_Sneak_Attack_Small"],
    "Ply_Sword_Versus_as": ["Sword_Ready_ParryIdle_In", "Sword_Ready_ParryIdle", "Sword_Ready_ParryIdle_Out", "Sword_Ready_Parry_A"],
    "Ply_Powers_as": ["Powers_Idle", "Powers_Ready_Equip", "Powers_Ready_Unequip", "Powers_Walk", "Powers_Sprint", "Powers_Switch",
                      "Powers_OutOfMana", "Powers_Cast_BendTime", "Powers_Cast_Windblast", "Powers_Cast_Swarm", "Powers_Cast_Blink_In",
                      "Powers_Cast_Blink_Loop", "Powers_Cast_Blink_Out", "Powers_Cast_Possession_In", "Powers_Cast_Possession_Loop",
                      "Powers_Cast_Possession_Out", "Powers_SwordSlash_Left", "Powers_SwordSlash_Right"],
    "Ply_Guns_as": ["Guns_Idle", "Guns_Ready_Equip", "Guns_Ready_Unequip", "Guns_Walk", "Guns_Sprint", "Guns_SwordSlash_Left", "Guns_SwordSlash_Right"],
    "Ply_Pistol_as": ["Pistol_Fire", "Pistol_FastReload", "Pistol_Spyglass_In", "Pistol_Spyglass_Idle", "Pistol_Spyglass_Out"],
    "Ply_Crossbow_as": ["Crossbow_Fire", "Crossbow_RapidReload"],
    "Ply_Gadgets_as": ["Gadgets_Idle", "Gadgets_Ready_Equip", "Gadgets_Ready_Unequip", "Gadgets_Walk"],
    "Ply_Gadgets_Grenade_as": ["Gadgets_Grenade_Deploy"],
    "Ply_Gadgets_SpringRazor_as": ["Gadgets_SpringRazor_Deploy"],
}
WEAPONS = {
    "sword": ("Startup", "Wpn_PlySword01", "Wpn_PlySword01_D", (1024, 1024), "PlySword_Anim_as",
              ["Sword_Ready_Equip", "Sword_Ready_UnEquip", "Sword_Ready_Idle"]),
    "pistol": ("Startup", "CorvoGun_01", "CorvoGun_01_D", (1024, 512), "CorvoGun_as", ["CorvoGun_Fire_Hammer", "CorvoGun_Idle", "CorvoGun_Equip", "CorvoGun_FastReload"]),
    "crossbow": ("Startup", "crossbow_01", "Crossbow_01_D", (1024, 512), "Crossbow_as", ["Crossbow_Fire", "Crossbow_Idle", "Crossbow_Equip", "Crossbow_RapidReload"]),
}

# Bolts seated in the crossbow (attached to its bolt_jnt). Their diffuse maps are cooked out of the game, so the
# regular bolt and sleep dart use the bolt's specular map, which carries the same metal detail.
BOLTS = {
    "bolt": ("bolt_01", "Bolt_01_S"),
    "bolt_sleep": ("Bolt_Sleep", "Bolt_01_S"),
    "bolt_flare": ("Bolt_Flare", "Bolt_Flare_D"),
}
# The glass vial of each dart (span along the bolt's length, metres), found from the mesh's cross-section:
# a long cylinder between the collar and the needle. Triangles centred in it glow in game.
BOLT_VIALS = {"bolt_sleep": (0.12, 0.205), "bolt_flare": (0.133, 0.172)}


def r(a, n=5):
    return [round(float(x), n) for x in np.asarray(a).ravel()]


def node_trs(node):
    t = node.get("translation", [0, 0, 0])
    q = node.get("rotation", [0, 0, 0, 1])
    s = node.get("scale", [1, 1, 1])
    return t, q, s


def skeleton_json(g):
    skin = g.g["skins"][0]
    joints = skin["joints"]
    names = [g.g["nodes"][j]["name"] for j in joints]
    parent_of = {c: i for i, n in enumerate(g.g["nodes"]) for c in n.get("children", [])}
    parents = [joints.index(parent_of[j]) if parent_of.get(j) in joints else -1 for j in joints]
    trs = [node_trs(g.g["nodes"][j]) for j in joints]
    ibm = g.acc(skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)  # row-major
    return {
        "names": names,
        "parents": parents,
        "bindT": [r(t) for t, _, _ in trs],
        "bindQ": [r(q, 6) for _, q, _ in trs],
        "bindS": [r(s) for _, _, s in trs],
        "inverseBind": [r(m, 6) for m in ibm],
    }


def mesh_json(g, texture):
    P, N, U, J, W, I = g.mesh()
    wsum = W.sum(axis=1, keepdims=True)
    W = W / np.where(wsum == 0, 1, wsum)
    return {
        "texture": texture,
        "positions": r(P), "normals": r(N, 3), "uvs": r(np.column_stack([U[:, 0] % 1.0, U[:, 1]]), 4),
        "joints": [int(x) for x in J.ravel()], "weights": r(W, 3), "indices": [int(x) for x in I.ravel()],
    }


def to_gltf_pos(t):
    """Edge translation (UE, centimetres) -> glTF local position (metres): (x, y, z) -> (x, z, y) / 100."""
    return (t[0] / 100.0, t[2] / 100.0, t[1] / 100.0)


def bake_anim(lib, set_name, seq, bind_q, bind_t=None):
    """bind_t (bone -> local position) also bakes per-bone translations ("pos"), e.g. the folding blade's
    telescoping segments. The player's arm sets are rotation-only, so they pass None."""
    stream = lib.seqs[(set_name, seq)]["stream"]
    names = lib.tracks(set_name)
    hdr = lib.dec.header(stream)
    frames = lib.dec.sample(stream)
    driven = sorted({j for rows in frames for j, (q, _t, _s) in enumerate(rows) if j < len(names) and not any(math.isnan(v) for v in q)})
    rot = []
    for rows in frames:
        for j in driven:
            q = rows[j][0]
            if any(math.isnan(v) for v in q):
                rot.extend(bind_q[names[j]])  # driven in other frames of this clip only: hold the bind rotation
                continue
            rot.extend(r(to_gltf_quat(q), 5))
    out = {"set": set_name, "fps": hdr["freq"], "frames": hdr["frames"], "bones": [names[j] for j in driven], "rot": rot}
    if bind_t is not None:
        moving = sorted({j for rows in frames for j, (_q, t, _s) in enumerate(rows)
                         if j < len(names) and not any(math.isnan(v) for v in t[:3])
                         and max(abs(a - b) for a, b in zip(to_gltf_pos(t), bind_t[names[j]])) > 1e-3})
        if moving:
            pos = []
            for rows in frames:
                for j in moving:
                    t = rows[j][1]
                    pos.extend(r(bind_t[names[j]] if any(math.isnan(v) for v in t[:3]) else to_gltf_pos(t), 5))
            out["posBones"] = [names[j] for j in moving]
            out["pos"] = pos
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", required=True)
    args = ap.parse_args()
    pack = Path(args.pack)
    fp = pack / "assets/dishonored/fp"
    tex_dir = pack / "assets/dishonored/textures/fp"
    fp.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)

    arms = Gltf(E / "models/Engine/SkeletalMesh3/Skm_Player.gltf")
    rig = {"mesh": mesh_json(arms, "dishonored:textures/fp/arms.png"), "skeleton": skeleton_json(arms)}
    Image.open(E / "textures/Engine/Texture2D/PlayerArmsTatooed_D.png").convert("RGB").resize((1024, 1024), Image.LANCZOS).save(tex_dir / "arms.png")
    (fp / "rig.json").write_text(json.dumps(rig, separators=(",", ":")))
    print("rig:", len(rig["mesh"]["indices"]) // 3, "tris,", len(rig["skeleton"]["names"]), "bones")

    lib = AnimLibrary(rig["skeleton"]["names"])
    arm_bind = dict(zip(rig["skeleton"]["names"], rig["skeleton"]["bindQ"]))
    anims = {}
    for set_name, seqs in ARM_ANIMS.items():
        for seq in seqs:
            anims[seq] = bake_anim(lib, set_name, seq, arm_bind)
            print(f"  {seq:34} {anims[seq]['frames']:4} frames, {len(anims[seq]['bones'])} bones")

    for key, (pkg, mesh, tex, size, anim_set, seqs) in WEAPONS.items():
        g = Gltf(E / f"models/{pkg}/SkeletalMesh3/{mesh}.gltf")
        Image.open(E / f"textures/{pkg}/Texture2D/{tex}.png").convert("RGB").resize(size, Image.LANCZOS).save(tex_dir / f"{key}.png")
        weapon = {"mesh": mesh_json(g, f"dishonored:textures/fp/{key}.png"), "skeleton": skeleton_json(g)}
        (fp / f"weapon_{key}.json").write_text(json.dumps(weapon, separators=(",", ":")))
        wlib_names = weapon["skeleton"]["names"]
        lib.skeleton = wlib_names
        lib._tracks.pop(anim_set, None)
        for seq in seqs:
            try:
                bt = weapon["skeleton"]["bindT"]
                anims[f"{key}:{seq}"] = bake_anim(lib, anim_set, seq, dict(zip(wlib_names, weapon["skeleton"]["bindQ"])),
                                                  dict(zip(wlib_names, bt)))
                print(f"  {key}:{seq:28} {anims[f'{key}:{seq}']['frames']:4} frames")
            except Exception as e:
                print(f"  {key}:{seq} skipped: {e}")
        lib.skeleton = rig["skeleton"]["names"]
    for key, (mesh, tex) in BOLTS.items():
        g = Gltf(E / f"models/Startup/SkeletalMesh3/{mesh}.gltf")
        Image.open(E / f"textures/Startup/Texture2D/{tex}.png").convert("RGB").resize((256, 512), Image.LANCZOS).save(tex_dir / f"{key}.png")
        weapon = {"mesh": mesh_json(g, f"dishonored:textures/fp/{key}.png"), "skeleton": skeleton_json(g)}
        if key in BOLT_VIALS:
            lo, hi = BOLT_VIALS[key]
            m = weapon["mesh"]
            ys = m["positions"][1::3]
            idx = m["indices"]
            metal, vial = [], []
            for t in range(0, len(idx), 3):
                tri = idx[t:t + 3]
                cy = sum(ys[i] for i in tri) / 3
                (vial if lo < cy < hi else metal).extend(tri)
            tex = f"dishonored:textures/fp/{key}.png"
            m["parts"] = [{"texture": tex, "indices": metal}, {"texture": tex, "indices": vial, "glow": True}]
            print(f"  {key}: vial {len(vial) // 3} of {len(idx) // 3} triangles")
        (fp / f"weapon_{key}.json").write_text(json.dumps(weapon, separators=(",", ":")))
        print(f"  {key}: {len(weapon['mesh']['positions']) // 3} verts")
    (fp / "anims.json").write_text(json.dumps(anims, separators=(",", ":")))
    print("anims.json:", round((fp / 'anims.json').stat().st_size / 1e6, 2), "MB,", len(anims), "clips")


if __name__ == "__main__":
    main()
