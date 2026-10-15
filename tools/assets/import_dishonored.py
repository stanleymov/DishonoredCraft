"""One command: build the private "Dishonored Original Assets" pack from YOUR Dishonored install.

Needs (all local): UE Viewer (umodel), vgmstream-cli, wwiser.pyz, ffmpeg, Python with numpy, scipy, Pillow, unicorn,
capstone and pefile. Animation decoding executes code from the player's own Dishonored.exe inside the Unicorn emulator.
Nothing produced here may be shared: the pack contains Arkane's audio, meshes and textures.

    python tools/assets/import_dishonored.py --game "C:/Program Files (x86)/Steam/steamapps/common/Dishonored" \
        --pack "%APPDATA%/.minecraft-dishonored/resourcepacks/Dishonored Original Assets"
"""
import argparse
import subprocess
import sys
from pathlib import Path
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

HERE = Path(__file__).parent
TOOLS = Path.home() / "tools"
EXTRACT = EXTRACT_DIR

AUDIO_BANKS = ["Bank_Power_Player", "Bank_Power_Player_ShadowKill", "Bank_Weapon_Sword_Corvo", "Bank_Weapon",
               "Bank_Player", "Bank_UI_Ingame_Wheel"]
MESHES = [("Engine.upk", "Skm_Player"), ("Startup.upk", "Wpn_PlySword01"), ("Startup.upk", "CorvoGun_01"),
          ("Startup.upk", "crossbow_01"), ("Startup.upk", "bolt_01"), ("Startup.upk", "Bolt_Sleep"), ("Startup.upk", "Bolt_Flare")]
TEXTURES = [("Engine.upk", "PlayerArmsTatooed_D"), ("Startup.upk", "Wpn_PlySword01_D"), ("Startup.upk", "CorvoGun_01_D"),
            ("Startup.upk", "Crossbow_01_D"), ("Startup.upk", "Bolt_01_S"), ("Startup.upk", "Bolt_Flare_D"),
            ("Startup.upk", "Stealth_02_m")]  # (the stealth screen mask: the HUD's dark screen edges)


# The weapon wheel's own icons (white halftone silhouettes; the wheel movie loads them from Startup.upk).
WHEEL_ICONS = ['ic_item_flareBolt', 'ic_item_regularBolt', 'ic_item_sleepDart', 'ic_item_springRazor', 'ic_item_regularGrenade', 'ic_item_regularBullet', 'ic_pow_Blink', 'ic_pow_bendTime', 'ic_pow_darkVision', 'ic_pow_possession', 'ic_pow_ratSwarm', 'ic_pow_windBlast']
UI_PACKAGES = ["UI_HUD_SF.upk", "UI_PowerWheel_SF.upk", "UI_ItemIcons_Large.upk", "UI_Powers_Large.upk", "UI_HUDFX_SF.upk"]
WORLD_MESHES = [("L_ArtDealer_Block.upk", "Mana"), ("L_Bridge_Part2_Script.upk", "emptyelixir"),
                ("Startup.upk", "SpringRazor"), ("Startup.upk", "Grenade"), ("DishonoredGame.upk", "Npc_SmallRat")]
WORLD_TEXTURES = [("Startup.upk", "SpringRazor_D"), ("Startup.upk", "Grenade_D"), ("DishonoredGame.upk", "SmallRat_D")]


def run(cmd, **kw):
    print("  $", " ".join(str(c) for c in cmd)[:160])
    return subprocess.run([str(c) for c in cmd], check=True, **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--game", required=True)
    ap.add_argument("--pack", required=True)
    ap.add_argument("--umodel", default=str(TOOLS / "umodel/umodel_64.exe"))
    ap.add_argument("--wwiser", default=str(TOOLS / "wwiser.pyz"))
    args = ap.parse_args()
    cooked = Path(args.game) / "DishonoredGame/CookedPCConsole"
    umodel = [args.umodel, "-game=dis", f"-path={cooked}"]

    print("1/7 Wwise event names from the UE3 packages")
    names = set()
    for upk in sorted(cooked.glob("*.upk")):
        out = subprocess.run(umodel + ["-list", upk.name], capture_output=True, text=True, errors="ignore").stdout
        names.update(line.split()[-1] for line in out.splitlines() if " AkEvent " in line or " AkBank " in line)
    (EXTRACT / "audio").mkdir(parents=True, exist_ok=True)
    (EXTRACT / "wwnames.txt").write_text("\n".join(sorted(names)))
    print(f"   {len(names)} names")

    print("2/7 Carve banks and streamed sounds")
    run([sys.executable, HERE / "akpk_extract.py", *[cooked / f"{b}.pck" for b in AUDIO_BANKS], "--out", EXTRACT / "audio"])

    print("3/7 Resolve events with wwiser")
    for b in AUDIO_BANKS:
        d = EXTRACT / "audio" / b
        (d / "wwnames.txt").write_text((EXTRACT / "wwnames.txt").read_text())
        run([sys.executable, args.wwiser, "-d", "none", "-g", "-go", "txtp", "-gw", "../wem", *sorted(p.name for p in d.glob("*.bnk"))],
            cwd=d, capture_output=True)

    print("4/7 Render sounds into the pack")
    run([sys.executable, HERE / "build_pack.py", "--extract", EXTRACT, "--pack", args.pack])

    print("5/7 Export meshes and textures")
    for upk, obj in MESHES:
        run(umodel + ["-export", "-gltf", f"-out={EXTRACT / 'models'}", upk, obj], capture_output=True)
    for upk, obj in TEXTURES:
        run(umodel + ["-export", "-png", f"-out={EXTRACT / 'textures'}", upk, obj], capture_output=True)

    print("6/7 Decode Dishonored's original animations (the game's own Edge decoder, run in an emulator) and bake the rig")
    raw = EXTRACT / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    run([sys.executable, HERE.parent / "re" / "ue3_decompress.py", cooked / "Startup.upk", raw / "Startup.upk"])
    run([sys.executable, HERE / "bake_anims.py", "--pack", args.pack])
    print("7/7 UI art, item/power icons, world meshes (rat, elixirs, grenade, spring razor) and rat animations")
    run([sys.executable, HERE.parent / "re" / "ue3_decompress.py", cooked / "DishonoredGame.upk", raw / "DishonoredGame.upk"])
    for upk in UI_PACKAGES:
        run(umodel + ["-export", "-png", f"-out={EXTRACT / 'ui'}", upk], capture_output=True)
    for name in WHEEL_ICONS + ["CorvoSword_Large"]:
        run(umodel + ["-export", "-png", f"-out={EXTRACT / 'ui'}", "Startup.upk" if name.startswith("ic_") else "UI_ItemIcons_Large.upk", name],
            capture_output=True)
    for upk, obj in WORLD_MESHES:
        run(umodel + ["-export", "-gltf", "-png", f"-out={EXTRACT / 'models'}", upk, obj], capture_output=True)
    for upk, obj in WORLD_TEXTURES:
        run(umodel + ["-export", "-png", f"-out={EXTRACT / 'textures'}", upk, obj], capture_output=True)
    listing = subprocess.run(umodel + ["-list", "L_ArtDealer_Block.upk"], capture_output=True, text=True, errors="ignore").stdout
    for line in listing.splitlines():
        if " Texture2D " in line and any(k in line.lower() for k in ("mana", "elixir")):
            run(umodel + ["-export", "-png", f"-out={EXTRACT / 'textures'}", "L_ArtDealer_Block.upk", line.split()[-1]], capture_output=True)
    run([sys.executable, HERE / "crop_hud.py"])
    run([sys.executable, HERE / "bake_world.py", "--pack", args.pack])
    print("Done. Enable 'Dishonored Original Assets' under Options > Resource Packs.")


if __name__ == "__main__":
    main()
