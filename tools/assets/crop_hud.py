"""Cut Dishonored's HUD sprites out of the exported Scaleform sheets (UI_HUD_SF) into ~/dishonored-extract/ui/hud.

Sprites are found as connected alpha blobs; the indices below were picked by eye from the labelled sheets.
"""
import glob
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

UI = EXTRACT_DIR / "ui"


def sheet(n):
    return Image.open(glob.glob(str(UI / f"UI_HUD_SF/**/{n}.png"), recursive=True)[0]).convert("RGBA")


def blobs(n):
    a = np.asarray(sheet(n))[:, :, 3] > 8
    lab, _ = ndimage.label(ndimage.binary_dilation(a, iterations=2))
    boxes = [s for s in ndimage.find_objects(lab) if (s[0].stop - s[0].start) * (s[1].stop - s[1].start) > 150]
    return [(s[1].start, s[0].start, s[1].stop, s[0].stop) for s in boxes]


def main():
    B = {n: blobs(n) for n in ["HUD_I46", "HUD_I5C", "HUD_I54"]}
    crops = {
        "bar_red_a": ("HUD_I46", (0, 386, 330, 424)),
        "bar_red_b": ("HUD_I46", B["HUD_I46"][35]),
        "bar_grey": ("HUD_I46", (0, 290, 410, 340)),
        "bar_black": ("HUD_I46", (0, 340, 410, 388)),
        "stroke_black": ("HUD_I46", B["HUD_I46"][17]),
        "heart_red": ("HUD_I46", B["HUD_I46"][24]),
        "ink_backdrop": ("HUD_I5C", B["HUD_I5C"][0]),
        "vial_outline": ("HUD_I5C", B["HUD_I5C"][2]),
        "vial_red": ("HUD_I5C", B["HUD_I5C"][3]),
        "vial_cyan": ("HUD_I5C", B["HUD_I5C"][8]),
        "ring_glow": ("HUD_I54", B["HUD_I54"][12]),
        "ring_red": ("HUD_I54", B["HUD_I54"][34]),
        "ring_blue": ("HUD_I54", B["HUD_I54"][35]),
        "ring_grey": ("HUD_I54", B["HUD_I54"][36]),
        "ink_strip": ("HUD_I54", B["HUD_I54"][38]),
        # Takedown prompts and the stealth silhouette.
        "prompt_kill": ("HUD_I54", (0, 258, 78, 340)),     # backstab: Corvo grabbing from behind
        "prompt_choke": ("HUD_I67", (417, 385, 495, 471)),  # choke hold
        "prompt_drop": ("HUD_I54", (161, 257, 247, 340)),
        # Awareness markers shown above guards' heads: the bracket that fills, its alerted red version and glow.
        "aware_white": ("HUD_I46", B["HUD_I46"][4]),
        "aware_red": ("HUD_I46", B["HUD_I46"][5]),
        "aware_glow": ("HUD_I46", B["HUD_I46"][0]),
        # The guard awareness marker proper: lightning bolts around the head (lit, unlit, glow).
        "aware_bolt": ("HUD_I85", (437, 244, 457, 279)),
        "aware_bolt_dim": ("HUD_I85", (462, 246, 480, 279)),
        "aware_bolt_glow": ("HUD_I5C", (444, 445, 469, 484)),
        "stance_stand": ("HUD_I54", (5, 13, 63, 124)),
        "stance_crouch": ("HUD_I54", (85, 27, 155, 126)),  # crouched, arms out (matches the game's HUD)
    }
    (UI / "hud").mkdir(parents=True, exist_ok=True)
    for name, (n, box) in crops.items():
        im = sheet(n).crop(tuple(box))
        bb = im.getbbox()
        (im.crop(bb) if bb else im).save(UI / "hud" / f"{name}.png")
    # Mana: the game's own blue crystal and its dark empty crystal (HUD_I85).
    for name, box in [("vial_blue", (107, 334, 135, 504)), ("vial_blue_empty", (153, 333, 189, 505))]:
        im = sheet("HUD_I85").crop(box)
        bb = im.getbbox()
        (im.crop(bb) if bb else im).save(UI / "hud" / f"{name}.png")
    # The Mark of the Outsider (power wheel art; drawn there at ~20% opacity): solid white glyph plus a soft
    # glow, for the bone charm button in the inventory.
    from PIL import ImageFilter
    src = Image.open(glob.glob(str(UI / "UI_PowerWheel_SF/**/powerwheel_I8C.png"), recursive=True)[0]).convert("RGBA")
    alpha = np.asarray(src)[:, :, 3].astype(np.float32)
    alpha = np.clip(alpha / max(1.0, alpha.max()) * 255, 0, 255).astype(np.uint8)
    mark = Image.new("RGBA", src.size, (255, 255, 255, 0))
    mark.putalpha(Image.fromarray(alpha))
    mark = mark.crop(mark.getbbox())
    pad = 12
    glow = Image.new("RGBA", (mark.width + pad * 2, mark.height + pad * 2), (255, 255, 255, 0))
    glow.paste(mark, (pad, pad))
    ga = glow.getchannel("A").filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.GaussianBlur(5))
    glow.putalpha(ga)
    mark_padded = Image.new("RGBA", glow.size, (255, 255, 255, 0))
    mark_padded.paste(mark, (pad, pad))
    mark_padded.save(UI / "hud" / "mark_outsider.png")  # same canvas as the glow, so both draw at one rect
    glow.save(UI / "hud" / "mark_outsider_glow.png")
    print(f"{len(crops) + 4} HUD sprites")


if __name__ == "__main__":
    main()
