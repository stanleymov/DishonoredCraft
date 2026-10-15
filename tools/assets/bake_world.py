"""Bake Dishonored's UI art, item/power icons and world meshes (+ rat animations) into the private pack.

Everything comes from the player's own install (see import_dishonored.py) and goes into the pack only:
  assets/dishonored/textures/item/*.png         real inventory icons (override the mod's pixel art)
  assets/dishonored/textures/gui/power/*.png    real power icons
  assets/dishonored/textures/gui/hud/*.png      HUD + weapon wheel sprites
  assets/dishonored/fp/world_<name>.json        rat, spring razor, grenade, elixir meshes
  assets/dishonored/fp/world_anims.json         rat animations (decoded by the game's own Edge decoder)

    python tools/assets/bake_world.py --pack "<gameDir>/resourcepacks/Dishonored Original Assets"
"""
import argparse
import glob
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "re"))
from anim_pose import AnimLibrary  # noqa: E402
from bake_anims import bake_anim, r, skeleton_json  # noqa: E402
from edge_dump import sequences  # noqa: E402
from gltf_mesh import Gltf  # noqa: E402
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

E = EXTRACT_DIR

ITEM_ICONS = {
    "folding_blade": "CorvoSword_Large", "pistol": "Gun_Large", "wrist_bow": "Crossbow_Large",
    "pistol_shot": "RegularBullets_Large", "crossbow_bolt": "CrossbowRegularBolt_Large", "sleep_dart": "CrossbowSleepDart_Large",
    "incendiary_bolt": "CrossbowFlareBolt_Large", "grenade": "CorvoGrenade_Large", "spring_razor": "SpringRazor_Large",
    "sokolovs_elixir": "HealthElixir_Large", "pieros_remedy": "ManaElixir_Large", "bone_charm_redirective_blink": "BoneCharms_Large",
}
POWER_ICONS = {"blink": "BlinkBig", "dark_vision": "DarkVisionBig", "bend_time": "BendTimeBig", "possession": "PossessionBig",
               "devouring_swarm": "RatSwarmBig", "windblast": "WindblastBig", "agility": "AgilityBig", "shadow_kill": "ShadowKillBig"}
HUD_SPRITES = ["bar_red_a", "bar_red_b", "bar_grey", "bar_black", "stroke_black", "ink_backdrop", "vial_red", "vial_outline",
               "vial_cyan", "ring_red", "ring_blue", "ring_grey", "ring_glow", "ink_strip", "heart_red",
               "vial_blue", "vial_blue_empty", "aware_white", "aware_red", "aware_glow", "aware_bolt", "aware_bolt_dim", "aware_bolt_glow", "prompt_kill", "prompt_choke", "prompt_drop", "stance_stand", "stance_crouch",
               "mark_outsider", "mark_outsider_glow"]
WHEEL = {"wheel_bkgd": "wh_wheelBkgd", "wheel_stroke_black": "wh_wheelBlackStroke", "wheel_stroke_white": "wh_wheelWhiteStroke",
         "wheel_select": "powerwheel_I98", "wheel_vial_health": "powerwheel_IA0", "wheel_vial_mana": "powerwheel_IA7",
         "wheel_disc": "powerwheel_I30", "wheel_ring": "powerwheel_I32",
         "wheel_slot_ring": "powerwheel_I1E", "wheel_badge": "powerwheel_IAD", "wheel_noise": "bkgd_whiteNoise128"}


def find_png(stem):
    hits = glob.glob(str(E / "**" / f"{stem}*.png"), recursive=True)
    if not hits:
        raise FileNotFoundError(stem)
    return min(hits, key=len)


def resize_rgba(im, size):
    """Resize with premultiplied alpha, so soft edges keep their colour instead of picking up dark fringes."""
    import numpy as np
    a = np.asarray(im.convert("RGBA")).astype(np.float32) / 255
    pm = a.copy()
    pm[..., :3] *= a[..., 3:4]
    chans = [Image.fromarray(pm[..., i]).resize(size, Image.LANCZOS) for i in range(4)]
    out = np.stack([np.asarray(c) for c in chans], -1).clip(0, 1)
    alpha = out[..., 3:4]
    out[..., :3] = np.where(alpha > 1e-4, out[..., :3] / np.maximum(alpha, 1e-4), 0)
    return Image.fromarray((out * 255 + 0.5).clip(0, 255).astype(np.uint8), "RGBA")


def power_icon(im, size):
    """Dishonored's power icons carry a faint square guide frame (alpha < ~25%); drop it, then square and resize."""
    import numpy as np
    a = np.asarray(im.convert("RGBA")).copy()
    a[..., 3] = np.where(a[..., 3] < 64, 0, a[..., 3])
    im = Image.fromarray(a, "RGBA")
    bb = im.getbbox()
    if bb:
        im = im.crop(bb)
    side = max(im.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    return resize_rgba(canvas, (size, size))


WHEEL_ICONS = ['ic_item_flareBolt', 'ic_item_regularBolt', 'ic_item_sleepDart', 'ic_item_springRazor', 'ic_item_regularGrenade', 'ic_item_regularBullet', 'ic_pow_Blink', 'ic_pow_bendTime', 'ic_pow_darkVision', 'ic_pow_possession', 'ic_pow_ratSwarm', 'ic_pow_windBlast']


def wheel_sword_icon(size=100):
    """Dishonored's wheel has no sword (it never leaves Corvo's hand): its inventory icon for Corvo's blade."""
    return square(Image.open(find_png("CorvoSword_Large")), size)


def charm_outline(icon, size=64):
    """Empty bone charm slot art: only the outline of the charm icon (dark grey, like vanilla's empty-slot hints)."""
    from PIL import ImageChops, ImageFilter
    mask = square(icon, size).getchannel("A").point(lambda v: 255 if v > 40 else 0)
    edge = ImageChops.subtract(mask, mask.filter(ImageFilter.MinFilter(7)))
    out = Image.new("RGBA", (size, size), (55, 55, 55, 0))
    out.putalpha(edge.point(lambda v: 210 if v else 0))
    return out


def square(im, size):
    im = im.convert("RGBA")
    bb = im.getbbox()
    if bb:
        im = im.crop(bb)
    side = max(im.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    return canvas.resize((size, size), Image.LANCZOS)


def submesh_json(g, materials):
    """Per-material parts. materials: list (by primitive) of dicts {texture, translucent}."""
    P, N, U, J, W = [], [], [], [], []
    parts = []
    base = 0
    prim_i = 0
    for m in g.g["meshes"]:
        for p in m["primitives"]:
            at = p["attributes"]
            pos = g.acc(at["POSITION"])
            P.append(pos)
            N.append(g.acc(at["NORMAL"]) if "NORMAL" in at else np.zeros_like(pos))
            U.append(g.acc(at["TEXCOORD_0"]))
            J.append(g.acc(at["JOINTS_0"]) if "JOINTS_0" in at else np.zeros((len(pos), 4)))
            W.append(g.acc(at["WEIGHTS_0"]) if "WEIGHTS_0" in at else np.tile([1, 0, 0, 0], (len(pos), 1)))
            idx = (g.acc(p["indices"]).astype(int).reshape(-1) + base).tolist()
            mat = materials[min(prim_i, len(materials) - 1)]
            if mat is not None:
                parts.append({"texture": mat["texture"], "translucent": mat.get("translucent", False), "indices": idx})
            base += len(pos)
            prim_i += 1
    P, N, U, J, W = map(np.vstack, (P, N, U, J, W))
    W = W / np.where(W.sum(axis=1, keepdims=True) == 0, 1, W.sum(axis=1, keepdims=True))
    return {"texture": parts[0]["texture"], "positions": r(P), "normals": r(N, 3),
            "uvs": r(np.column_stack([U[:, 0] % 1.0, U[:, 1]]), 4), "joints": [int(x) for x in J.astype(int).ravel()],
            "weights": r(W, 3), "indices": [i for part in parts for i in part["indices"]], "parts": parts}


def static_skeleton():
    return {"names": ["root"], "parents": [-1], "bindT": [[0, 0, 0]], "bindQ": [[0, 0, 0, 1]], "bindS": [[1, 1, 1]],
            "inverseBind": [r(np.eye(4))]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", required=True)
    args = ap.parse_args()
    a = Path(args.pack) / "assets/dishonored"
    for d in ["textures/item", "textures/gui/power", "textures/gui/hud", "textures/world", "fp"]:
        (a / d).mkdir(parents=True, exist_ok=True)

    for item, stem in ITEM_ICONS.items():
        square(Image.open(find_png(stem)), 64).save(a / f"textures/item/{item}.png")
    for power, stem in POWER_ICONS.items():
        power_icon(Image.open(find_png(stem)), 128).save(a / f"textures/gui/power/{power}.png")
        # Smooth (linear) filtering: the brush-stroke art turns to noise with Minecraft's default nearest sampling.
        (a / f"textures/gui/power/{power}.png.mcmeta").write_text('{"texture": {"blur": true}}')
    charm_outline(Image.open(find_png(ITEM_ICONS["bone_charm_redirective_blink"]))).save(a / "textures/gui/hud/charm_slot.png")
    # The dark screen edges (stealth, low health): Dishonored's stealth screen mask, whose red channel fades from the
    # centre (1) to the edges (0).
    mask = np.asarray(Image.open(find_png("Stealth_02_m")).convert("RGB"), dtype=np.float32)[:, :, 0]
    vig = np.zeros(mask.shape + (4,), np.uint8)
    vig[:, :, 3] = np.clip(255 - mask, 0, 255).astype(np.uint8)
    Image.fromarray(vig, "RGBA").save(a / "textures/gui/hud/vignette.png")
    (a / "textures/gui/wheel").mkdir(parents=True, exist_ok=True)
    for name in WHEEL_ICONS:
        # Lower case: Minecraft skips resource files with capitals in their names.
        Image.open(find_png(name)).convert("RGBA").save(a / f"textures/gui/wheel/{name.lower()}.png")
    wheel_sword_icon().save(a / "textures/gui/wheel/ic_item_sword.png")
    for f in (a / "textures/gui/wheel").glob("*.png"):
        f.with_name(f.name + ".mcmeta").write_text('{"texture": {"blur": true}}')
    for name in HUD_SPRITES:
        Image.open(E / "ui/hud" / f"{name}.png").save(a / f"textures/gui/hud/{name}.png")
    for name, stem in WHEEL.items():
        Image.open(find_png(stem)).convert("RGBA").save(a / f"textures/gui/hud/{name}.png")
    # Smooth filtering for all HUD / wheel sprites: they are drawn scaled, and nearest sampling makes their
    # painted edges jagged.
    for f in (a / "textures/gui/hud").glob("*.png"):
        f.with_name(f.name + ".mcmeta").write_text('{"texture": {"blur": true}}')
    print("icons + hud:", len(ITEM_ICONS), len(POWER_ICONS), len(HUD_SPRITES) + len(WHEEL))

    def tex(src, name, size=512, tint=None):
        im = Image.open(find_png(src)).convert("RGB").resize((size, size), Image.LANCZOS)
        if tint:
            gray = ImageOps.grayscale(im)
            im = ImageOps.colorize(gray, black=(0, 0, 0), white=tint)
        im.save(a / f"textures/world/{name}.png")
        return f"dishonored:textures/world/{name}.png"

    worlds = {}
    # Elixirs: one bottle mesh, body + liquid + glass. Health is the same bottle with red liquid.
    bottle = Gltf(E / "models/L_ArtDealer_Block/StaticMesh3/Mana.gltf")
    body = tex("mana_d", "elixir_body")
    glass = tex("mana_d", "elixir_glass", 64, tint=(220, 235, 240))
    for name, liquid in [("elixir_mana", tex("mana_liquid_d", "elixir_liquid_mana", 256, tint=(90, 190, 255))),
                         ("elixir_health", tex("mana_liquid_d", "elixir_liquid_health", 256, tint=(255, 60, 50)))]:
        worlds[name] = {"mesh": submesh_json(bottle, [{"texture": body}, {"texture": liquid}, {"texture": glass, "translucent": True}]),
                        "skeleton": static_skeleton()}
    for name, mesh, t in [("spring_razor", "SpringRazor", "SpringRazor_D"), ("grenade", "Grenade", "Grenade_D")]:
        g = Gltf(E / f"models/Startup/SkeletalMesh3/{mesh}.gltf")
        worlds[name] = {"mesh": submesh_json(g, [{"texture": tex(t, name)}]), "skeleton": skeleton_json(g)}
    rat = Gltf(E / "models/DishonoredGame/SkeletalMesh3/Npc_SmallRat.gltf")
    worlds["rat"] = {"mesh": submesh_json(rat, [{"texture": tex("SmallRat_D", "rat")}]), "skeleton": skeleton_json(rat)}
    for name, w in worlds.items():
        (a / f"fp/world_{name}.json").write_text(json.dumps(w, separators=(",", ":")))
        print(f"world {name}: {len(w['mesh']['indices']) // 3} tris, {len(w['skeleton']['names'])} bones, parts {len(w['mesh']['parts'])}")

    # Rat animations from DishonoredGame.upk, through the same emulated Edge decoder.
    lib = AnimLibrary(worlds["rat"]["skeleton"]["names"])
    lib.u, lib.seqs = sequences(E / "raw/DishonoredGame.upk")
    rat_bind = dict(zip(worlds["rat"]["skeleton"]["names"], worlds["rat"]["skeleton"]["bindQ"]))
    anims = {}
    for seq in ["RunN", "WalkN", "Idle2", "SwarmAttack", "Spawn", "Death", "Jump_Attack_In", "Jump_Attack_Loop", "Jump_Attack_Out"]:
        anims[f"rat:{seq}"] = bake_anim(lib, "Npc_SmallRat_as", seq, rat_bind)
        print(f"  rat:{seq} {anims[f'rat:{seq}']['frames']} frames, {len(anims[f'rat:{seq}']['bones'])} bones")
    (a / "fp/world_anims.json").write_text(json.dumps(anims, separators=(",", ":")))


if __name__ == "__main__":
    main()
