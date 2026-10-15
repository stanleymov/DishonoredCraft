"""Generates recipes, item models and enchantability tags for the mod. Run from the repo root."""
import json
from pathlib import Path

RES = Path("src/main/resources")
NS = "dishonored"


def write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def shaped(name, pattern, key, count=1):
    write(RES / f"data/{NS}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": f"{NS}:{name}", "count": count},
    })


def shapeless(name, items, count=1):
    write(RES / f"data/{NS}/recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "equipment",
        "ingredients": [{"item": i} for i in items],
        "result": {"id": f"{NS}:{name}", "count": count},
    })


shaped("folding_blade", ["  I", " I ", "S  "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"})
shaped("pistol", ["III", " FW"], {"I": "minecraft:iron_ingot", "F": "minecraft:flint_and_steel", "W": "#minecraft:planks"})
shaped("wrist_bow", ["SIS", "STS", " L "], {"S": "minecraft:string", "I": "minecraft:iron_ingot", "T": "minecraft:tripwire_hook", "L": "minecraft:leather"})
shaped("grenade", [" N ", "NGN", " N "], {"N": "minecraft:iron_nugget", "G": "minecraft:gunpowder"}, 2)
shaped("spring_razor", ["NIN", "ITI", "NIN"], {"N": "minecraft:iron_nugget", "I": "minecraft:iron_ingot", "T": "minecraft:tripwire_hook"}, 2)
shapeless("pistol_shot", ["minecraft:iron_nugget", "minecraft:gunpowder"], 4)
shapeless("crossbow_bolt", ["minecraft:iron_nugget", "minecraft:stick", "minecraft:feather"], 4)
shapeless("sleep_dart", [f"{NS}:crossbow_bolt", f"{NS}:crossbow_bolt", "minecraft:spider_eye"], 2)
shapeless("incendiary_bolt", [f"{NS}:crossbow_bolt", f"{NS}:crossbow_bolt", "minecraft:blaze_powder"], 2)
shapeless("sokolovs_elixir", ["minecraft:glass_bottle", "minecraft:glistering_melon_slice", "minecraft:red_mushroom"])
shapeless("pieros_remedy", ["minecraft:glass_bottle", "minecraft:lapis_lazuli", "minecraft:glowstone_dust"])

ITEMS = ["folding_blade", "pistol", "wrist_bow", "pistol_shot", "crossbow_bolt", "sleep_dart", "incendiary_bolt",
         "grenade", "spring_razor", "sokolovs_elixir", "pieros_remedy"]
HANDHELD = {"folding_blade", "pistol", "wrist_bow"}
for item in ITEMS:
    parent = "minecraft:item/handheld" if item in HANDHELD else "minecraft:item/generated"
    write(RES / f"assets/{NS}/models/item/{item}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{item}"}})

# The folding blade takes sword and tool enchantments.
for tag in ["sword", "sharp_weapon", "weapon", "fire_aspect", "durability", "mining", "mining_loot"]:
    write(RES / f"data/minecraft/tags/item/enchantable/{tag}.json", {"replace": False, "values": [f"{NS}:folding_blade"]})
for tag in ["durability"]:
    write(RES / f"data/minecraft/tags/item/enchantable/{tag}.json",
          {"replace": False, "values": [f"{NS}:folding_blade", f"{NS}:pistol", f"{NS}:wrist_bow"]})
# ---------------------------------------------------------------------------------------------------------
# 3D in-hand models. Each part is a cuboid coloured from textures/model/palette.png (4x4 grid of swatches).
# Every model points "up" (+Y) with its grip near the origin; the display block turns it in the hand.
STEEL, STEEL_MID, STEEL_DARK, BRASS, BRASS_DARK, BLACK, WOOD, WOOD_DARK, GLOW, IRON, STRING, RED, GREEN, ORANGE, LEATHER, GOLD = range(16)


def part(frm, to, cell, rot=None):
    u, v = (cell % 4) * 4 + 1, (cell // 4) * 4 + 1
    uv = [u, v, u + 2, v + 2]
    el = {"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": "#p"} for f in ["north", "south", "east", "west", "up", "down"]}}
    if rot:
        el["rotation"] = rot
    return el


def model3d(name, parts, display):
    write(RES / f"assets/{NS}/models/item/{name}_3d.json", {
        "textures": {"p": f"{NS}:item/palette", "particle": f"{NS}:item/{name}"},
        "elements": parts,
        "display": display,
    })


BLADE = [
    part([7, -2, 7], [9, -1, 9], BRASS_DARK),
    part([7.3, -1, 7.3], [8.7, 4, 8.7], BLACK),
    part([7.2, 0, 7.2], [8.8, 0.6, 8.8], LEATHER),
    part([7.2, 2, 7.2], [8.8, 2.6, 8.8], LEATHER),
    part([5, 4, 7.2], [11, 5, 8.8], BRASS),
    part([4.5, 4.2, 7.5], [5.2, 5.6, 8.5], BRASS_DARK),
    part([10.8, 3.4, 7.5], [11.5, 4.8, 8.5], BRASS_DARK),
    part([7, 5, 7.2], [9, 7.5, 8.8], STEEL_DARK),
    part([7.6, 5.5, 6.9], [8.4, 7, 7.2], BRASS),
    part([7.3, 7.5, 7.75], [8.7, 20, 8.25], STEEL),
    part([7.3, 7.5, 7.65], [7.7, 19.5, 8.35], STEEL_MID),
    part([7.5, 20, 7.8], [8.5, 22, 8.2], STEEL),
    part([7.8, 22, 7.85], [8.2, 23.2, 8.15], STEEL),
]

PISTOL = [
    part([7.2, 6, 7.2], [8.8, 18, 8.8], STEEL_DARK),
    part([7, 9, 7], [9, 10, 9], BRASS),
    part([7, 15.5, 7], [9, 16.5, 9], BRASS),
    part([6.9, 18, 6.9], [9.1, 19, 9.1], STEEL_MID),
    part([7.3, 3, 8.6], [8.7, 15, 9.8], WOOD),
    part([6.8, 4, 7.4], [9.2, 7, 9], BRASS),
    part([7.6, 3, 6.3], [8.4, 5.5, 7.4], STEEL_DARK),
    part([7.6, 5.2, 6.1], [8.4, 6, 6.6], STEEL_MID),
    part([7.2, 0, 8.6], [8.8, 4.5, 13.5], WOOD, {"origin": [8, 4, 9], "axis": "x", "angle": -22.5}),
    part([7, -0.6, 12.8], [9, 0.8, 14], BRASS, {"origin": [8, 4, 9], "axis": "x", "angle": -22.5}),
    part([7.6, 5, 9.8], [8.4, 5.6, 11.4], BRASS),
    part([7.6, 7.2, 9.8], [8.4, 7.8, 11.4], BRASS),
    part([7.75, 5.8, 9.8], [8.25, 6.8, 10.6], STEEL_DARK),
]

WRIST_BOW = [
    part([6, 0, 6], [10, 8, 10], LEATHER),
    part([5.8, 1, 5.8], [10.2, 2, 10.2], BRASS),
    part([5.8, 6, 5.8], [10.2, 7, 10.2], BRASS),
    part([7.3, 8, 7.3], [8.7, 14, 8.7], STEEL_DARK),
    part([3.5, 12, 7.5], [12.5, 13, 8.5], BRASS),
    part([1.5, 12.2, 7.6], [3.8, 12.9, 8.4], BRASS_DARK, {"origin": [3.5, 12.5, 8], "axis": "z", "angle": -22.5}),
    part([12.2, 12.2, 7.6], [14.5, 12.9, 8.4], BRASS_DARK, {"origin": [12.5, 12.5, 8], "axis": "z", "angle": 22.5}),
    part([2.2, 10.8, 7.9], [13.8, 11.1, 8.1], STRING),
    part([7.8, 9, 7.8], [8.2, 15.5, 8.2], STEEL_MID),
    part([7.6, 15.5, 7.6], [8.4, 16.3, 8.4], STEEL),
]


def disp(fp_rot, fp_tr, fp_scale, tp_rot, tp_tr, tp_scale):
    return {
        "firstperson_righthand": {"rotation": fp_rot, "translation": fp_tr, "scale": [fp_scale] * 3},
        "firstperson_lefthand": {"rotation": fp_rot, "translation": fp_tr, "scale": [fp_scale] * 3},
        "thirdperson_righthand": {"rotation": tp_rot, "translation": tp_tr, "scale": [tp_scale] * 3},
        "thirdperson_lefthand": {"rotation": tp_rot, "translation": tp_tr, "scale": [tp_scale] * 3},
    }


model3d("folding_blade", BLADE, disp([-50, 180, 0], [0, 2, -3], 0.7, [0, 90, 0], [0, 3, 1], 0.85))
model3d("pistol", PISTOL, disp([-90, 180, 0], [0, 0, -4], 0.75, [-90, 0, 0], [0, 1, 4], 0.8))
model3d("wrist_bow", WRIST_BOW, disp([-90, 180, 0], [0, -2, -2], 0.7, [-90, 0, 0], [0, 0, 2], 0.7))
print("ok")
