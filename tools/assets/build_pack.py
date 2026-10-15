"""Build a private "Dishonored Original Assets" resource pack from the player's own Dishonored install.

Pipeline (all local; nothing here is redistributed):
  1. akpk_extract.py   carve .bnk/.wem out of the game's AKPK .pck packs
  2. umodel -list      harvest Wwise event names from the UE3 packages (-> wwnames.txt)
  3. wwiser -g         resolve event -> wem as named .txtp playlists
  4. vgmstream-cli     render .txtp to .wav, then ffmpeg -> mono .ogg
  5. this script       writes pack.mcmeta + assets/dishonored/sounds.json (replace: true) + oggs

    python tools/assets/build_pack.py --extract ~/dishonored-extract --pack "<gameDir>/resourcepacks/Dishonored Original Assets"
"""
import argparse
import os
import json
import shutil
import subprocess
import tempfile
from pathlib import Path
# Where extracted Dishonored files go (the installer points this at its work folder).
EXTRACT_DIR = __import__("pathlib").Path(__import__("os").environ.get("DC_EXTRACT", str(__import__("pathlib").Path.home() / "dishonored-extract")))

# mod sound event -> (bank folder, txtp name prefix)
SOUNDS = {
    "power.blink": ("Bank_Power_Player", "Snd_Power_P_Blink_Start"),
    "power.blink_arrive": ("Bank_Power_Player", "Snd_Power_P_Blink_Stop"),
    "power.dark_vision_on": ("Bank_Power_Player", "Darkvision_Start"),
    "power.dark_vision_off": ("Bank_Power_Player", "Darkvision_Stop"),
    "power.bend_time": ("Bank_Power_Player", "Snd_Power_P_Bend_Time {"),
    "power.bend_time_end": ("Bank_Power_Player", "Snd_Power_P_Bend_Time_Stop"),
    "power.possession_in": ("Bank_Power_Player", "Snd_Power_Possession_In_Lvl1"),
    "power.possession_out": ("Bank_Power_Player", "Snd_Power_Possession_Out"),
    "power.swarm": ("Bank_Power_Player", "Snd_Power_P_Devouring_Swarm_Spell"),
    "power.swarm_rats": ("Bank_Power_Player", "Snd_Power_P_Devouring_Swarm_Rat_Spawn"),
    "power.windblast": ("Bank_Power_Player", "Snd_Pwr_P_WindBlast_lvl_1 {s}=("),
    "power.shadow_kill": ("Bank_Power_Player_ShadowKill", "Snd_Power_Shadow_Kill"),
    "power.switch": ("Bank_Power_Player", "Snd_Power_Switch"),
    "power.empty": ("Bank_Power_Player", "Snd_Power_Empty."),
    "power.super_jump": ("Bank_Power_Player", "Snd_Power_Super_Jump"),
    "player.mantle": ("Bank_Player", "Snd_P_Mantle_Medium_cue_ak"),
    "player.slide": ("Bank_Player", "Snd_P_Whoosh_Kick_cue_ak"),
    "weapon.pistol_fire": ("Bank_Weapon", "Snd_Weapon_Gun_Corvo_Fire"),
    "weapon.pistol_empty": ("Bank_Weapon", "Snd_Weapon_Gun_Corvo_Empty"),
    "weapon.crossbow_fire": ("Bank_Weapon", "Snd_Crossbow_P_Fire"),
    "weapon.crossbow_reload": ("Bank_Weapon", "Snd_Crossbow_P_Reload"),
    "weapon.grenade_explode": ("Bank_Weapon", "Snd_Grenade_Explosion {"),
    "weapon.grenade_pin": ("Bank_Weapon", "Snd_Grenade_Pin"),
    "weapon.razor_install": ("Bank_Weapon", "Snd_Spring_Razor_Install."),
    "weapon.razor_arming": ("Bank_Weapon", "Snd_Spring_Razor_Arming"),
    "weapon.razor_shot": ("Bank_Weapon", "Snd_Spring_Razor_Shot"),
    "weapon.sword_whoosh": ("Bank_Weapon", "Snd_Weapon_Sword_Whooshe_ak"),
    "weapon.sword_parry": ("Bank_Weapon", "Snd_Weapon_Sword_whooshe_Parry_cue_ak"),
    "weapon.sword_unfold": ("Bank_Weapon_Sword_Corvo", "Snd_Sword_Corvo_Out"),
    "item.elixir_mana": ("Bank_UI_Ingame_Wheel", "Snd_UI_Ingame_Wheel_Mana"),
    "item.elixir_health": ("Bank_UI_Ingame_Wheel", "Snd_UI_Ingame_Wheel_Life"),
}

STEREO = {"power.bend_time", "power.bend_time_end", "power.dark_vision_on", "power.dark_vision_off", "power.switch", "power.empty",
          "item.elixir_mana", "item.elixir_health"}


def find_txtp(bank_dir: Path, prefix: str):
    matches = sorted(p for p in (bank_dir / "txtp").glob("*.txtp") if p.name.startswith(prefix))
    return matches[0] if matches else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--extract", default=str(EXTRACT_DIR))
    ap.add_argument("--pack", required=True)
    ap.add_argument("--vgmstream", default=os.environ.get("DC_VGMSTREAM", str(Path.home() / "tools/vgmstream/vgmstream-cli.exe")))
    ap.add_argument("--ffmpeg", default=os.environ.get("DC_FFMPEG", "ffmpeg"))
    args = ap.parse_args()

    audio = Path(args.extract) / "audio"
    pack = Path(args.pack)
    sounds_dir = pack / "assets/dishonored/sounds"
    if sounds_dir.exists():
        shutil.rmtree(sounds_dir)
    sounds_dir.mkdir(parents=True)

    sounds_json = {}
    tmp = Path(tempfile.mkdtemp())
    for event, (bank, prefix) in SOUNDS.items():
        txtp = find_txtp(audio / bank, prefix)
        if txtp is None:
            print(f"  missing  {event}: {bank}/{prefix}*")
            continue
        wav = tmp / (event + ".wav")
        r = subprocess.run([args.vgmstream, "-o", str(wav), str(txtp)], capture_output=True, text=True)
        if r.returncode != 0 or not wav.exists():
            print(f"  failed   {event}: {r.stdout.strip()[-200:]} {r.stderr.strip()[-200:]}")
            continue
        name = event.replace(".", "/")
        ogg = sounds_dir / f"{name}.ogg"
        ogg.parent.mkdir(parents=True, exist_ok=True)
        channels = "2" if event in STEREO else "1"
        subprocess.run([args.ffmpeg, "-y", "-loglevel", "error", "-i", str(wav), "-ac", channels, "-ar", "44100",
                        "-c:a", "libvorbis", "-q:a", "6", str(ogg)], check=True)
        sounds_json[event] = {"replace": True, "sounds": [{"name": f"dishonored:{name}", "stream": False}]}
        print(f"  ok       {event} <- {txtp.name}")

    (pack / "assets/dishonored").mkdir(parents=True, exist_ok=True)
    (pack / "assets/dishonored/sounds.json").write_text(json.dumps(sounds_json, indent=2), encoding="utf-8")
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 34,
        "description": "Dishonored audio from YOUR install (private, do not share)"}}, indent=2), encoding="utf-8")
    shutil.rmtree(tmp, ignore_errors=True)
    print(f"{len(sounds_json)}/{len(SOUNDS)} sounds -> {pack}")


if __name__ == "__main__":
    main()
