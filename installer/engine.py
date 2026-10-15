"""DishonoredCraft setup engine: finds Dishonored through Steam, checks it, finds the player's Minecraft Forge 1.20.1
profiles in a modding launcher (or makes a new "DishonoredCraft" one in CurseForge / Prism), and builds DishonoredCraft.jar from the
player's own Dishonored files with the bundled tools (setup GUI: setup.pyw).

Package layout (installer/build_package.py makes it):
    Setup DishonoredCraft.exe   launcher -> runtime\\pythonw.exe setup\\setup.pyw
    runtime\\                   Python 3.12 + numpy / scipy / Pillow / unicorn / capstone / pefile / tkinter
    setup\\                     this engine, the GUI, version.json, tools\\ (our extractors and bakers)
    bin\\                       umodel (MIT), vgmstream (ISC-style), ffmpeg (GPL, portable), licences
    payload\\                   DishonoredCraft-base.jar: the mod's code with no Dishonored files in it

The finished jar contains Arkane's textures, models, animations and sounds from the player's own copy of the game:
it is for that player only and must not be shared. Nothing from Dishonored is in the download.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

DISHONORED_APP = "205100"
NO_WINDOW = 0x08000000
MINECRAFT = "1.20.1"
FORGE = "47.4.10"
# wwiser (Wwise bank reader, by bnnm) has no licence file, so it isn't shipped: the setup downloads this release.
WWISER_URL = "https://github.com/bnnm/wwiser/releases/download/v20260808/wwiser.pyz"
PROFILE_NAME = "DishonoredCraft"
# Dishonored.exe builds the animation decoder is known to work with (the game's own Edge decoder runs in an
# emulator at a fixed address). Steam's current build:
KNOWN_EXES = {"66443f3d68a6c658b0ed943260c3eb2f4cc9e3400d5809a94d6ec41eb725e17e": "Steam"}
# what Dishonored must have
GAME_FILES = ["Binaries/Win32/Dishonored.exe", "DishonoredGame/CookedPCConsole/Startup.upk",
              "DishonoredGame/CookedPCConsole/Engine.upk", "DishonoredGame/CookedPCConsole/DishonoredGame.upk",
              "DishonoredGame/CookedPCConsole/UI_HUD_SF.upk", "DishonoredGame/CookedPCConsole/UI_PowerWheel_SF.upk",
              "DishonoredGame/CookedPCConsole/Bank_Power_Player.pck", "DishonoredGame/CookedPCConsole/Bank_Weapon.pck"]

HERE = Path(__file__).resolve().parent            # <package>/setup  (development: <repo>/installer)
PACKAGE = HERE.parent
TOOLS = HERE / "tools"
BIN = PACKAGE / "bin"
PAYLOAD = PACKAGE / "payload"
# python.exe beside the GUI's pythonw.exe: the tools print progress, which needs a console-type Python
PY = Path(sys.executable).with_name("python.exe") if Path(sys.executable).with_name("python.exe").exists() else Path(sys.executable)
if not TOOLS.exists():                            # development: run from the repo
    TOOLS = PACKAGE / "tools"
    BIN = PACKAGE / "installer" / "dev_bin"
    PAYLOAD = PACKAGE / "build" / "libs"


# ------------------------------------------------------------------------------------- Steam / Dishonored

def steam_root():
    try:
        import winreg
        for hive, key, val in ((winreg.HKEY_CURRENT_USER, r"Software\Valve\Steam", "SteamPath"),
                               (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\WOW6432Node\Valve\Steam", "InstallPath"),
                               (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Valve\Steam", "InstallPath")):
            try:
                with winreg.OpenKey(hive, key) as k:
                    p = Path(winreg.QueryValueEx(k, val)[0])
                    if p.exists():
                        return p
            except OSError:
                pass
    except ImportError:
        pass
    p = Path("C:/Program Files (x86)/Steam")
    return p if p.exists() else None


def steam_libraries():
    root = steam_root()
    libs = []
    if root:
        libs.append(root)
        vdf = root / "steamapps" / "libraryfolders.vdf"
        if vdf.exists():
            for m in re.finditer(r'"path"\s+"([^"]+)"', vdf.read_text(encoding="utf-8", errors="ignore")):
                p = Path(m.group(1).replace("\\\\", "\\"))
                if p not in libs:
                    libs.append(p)
    return [p for p in libs if p.exists()]


def find_dishonored():
    """Dishonored's folder from Steam's manifests, or None."""
    for lib in steam_libraries():
        acf = lib / "steamapps" / f"appmanifest_{DISHONORED_APP}.acf"
        if acf.exists():
            m = re.search(r'"installdir"\s+"([^"]+)"', acf.read_text(encoding="utf-8", errors="ignore"))
            if m:
                p = lib / "steamapps" / "common" / m.group(1)
                if p.exists():
                    return Path(os.path.realpath(p))
    return None


def sha256(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def check_dishonored(path):
    """(ok, message)"""
    if path is None:
        return False, ("Dishonored was not found. DishonoredCraft is built from YOUR copy of Dishonored (the original "
                       "2012 game on Steam): install it, then press Check again - or point to its folder with Browse.")
    p = Path(path)
    missing = [f for f in GAME_FILES if not (p / f).exists()]
    if missing:
        return False, (f"No {missing[0]} in {p}. Is this the Dishonored folder (the one with Binaries and "
                       "DishonoredGame)? If it is, let Steam verify the game files.")
    exe = sha256(p / GAME_FILES[0])
    if exe not in KNOWN_EXES:
        return False, (f"Found Dishonored at {p}, but its Dishonored.exe is a version the setup doesn't know. The setup "
                       "needs the Steam version of the game (it reads the animations with the game's own code). "
                       "If Steam has an update for it, let it update, then press Check again.")
    return True, f"Found: {p}  ({KNOWN_EXES[exe]} version)"


# ------------------------------------------------------------------------------------- Minecraft profiles
# DishonoredCraft only ever goes into a modding launcher's profile (CurseForge, Prism Launcher, MultiMC) - never the
# Minecraft Launcher, which is for vanilla Minecraft.

def appdata():
    return Path(os.environ.get("APPDATA", Path.home() / "AppData/Roaming"))


def curseforge_instances():
    for root in (Path.home() / "curseforge/minecraft/Instances", Path(os.environ.get("USERPROFILE", Path.home())) / "curseforge/minecraft/Instances"):
        if root.exists():
            return root
    return None


def curseforge_installed():
    local = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData/Local"))
    return curseforge_instances() is not None or (local / "Programs" / "CurseForge Windows").exists()


def prism_root():
    for name in ("PrismLauncher", "MultiMC"):
        r = appdata() / name
        if (r / "instances").exists() or (r / "prismlauncher.cfg").exists() or (r / "multimc.cfg").exists():
            return r
    return None


def launchers():
    """The modding launchers on this PC that a new DishonoredCraft profile can be made in."""
    out = []
    if curseforge_installed():
        out.append("CurseForge")
    r = prism_root()
    if r:
        out.append("Prism Launcher" if r.name == "PrismLauncher" else "MultiMC")
    return out


def curseforge_exe():
    local = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData/Local"))
    exe = local / "Programs" / "CurseForge Windows" / "CurseForge.exe"
    return exe if exe.exists() else None


def curseforge_running():
    """CurseForge keeps its profiles in memory and writes them back: it must be closed while the setup changes one."""
    try:
        out = subprocess.run(["tasklist", "/FI", "IMAGENAME eq CurseForge.exe", "/NH"], capture_output=True, text=True,
                             creationflags=NO_WINDOW).stdout
        return "CurseForge.exe" in out
    except OSError:
        return False


def find_profiles():
    """Modding-launcher profiles that run Forge 1.20.1: [{"name", "launcher", "mods": Path, "dir": Path}]."""
    found = []

    def add(name, launcher, inst_dir, game_dir):
        mods = Path(game_dir) / "mods"
        if not any(f["mods"] == mods for f in found):
            found.append({"name": name, "launcher": launcher, "mods": mods, "dir": Path(inst_dir)})

    root = curseforge_instances()
    for inst in sorted(root.glob("*/minecraftinstance.json")) if root else []:
        try:
            d = json.loads(inst.read_text(encoding="utf-8"))
            loader = str((d.get("baseModLoader") or {}).get("name", ""))
            if d.get("gameVersion") == MINECRAFT and loader.startswith("forge-"):
                add(d.get("name") or inst.parent.name, "CurseForge", inst.parent, inst.parent)
        except (OSError, ValueError):
            pass
    r = prism_root()
    for pack in sorted((r / "instances").glob("*/mmc-pack.json")) if r else []:
        try:
            comps = {c.get("uid"): c.get("version") for c in json.loads(pack.read_text(encoding="utf-8")).get("components", [])}
            if comps.get("net.minecraft") == MINECRAFT and "net.minecraftforge" in comps:
                game = pack.parent / ".minecraft"
                if not game.exists() and (pack.parent / "minecraft").exists():
                    game = pack.parent / "minecraft"
                name = pack.parent.name
                cfg = pack.parent / "instance.cfg"
                if cfg.exists():
                    m = re.search(r"^name=(.*)$", cfg.read_text(encoding="utf-8", errors="ignore"), re.M)
                    if m:
                        name = m.group(1).strip()
                add(name, "Prism Launcher" if r.name == "PrismLauncher" else "MultiMC", pack.parent, game)
        except (OSError, ValueError):
            pass
    return found


def dc_profile(launcher):
    """The DishonoredCraft profile this setup made in `launcher` before, or None."""
    for p in find_profiles():
        if p["launcher"] == launcher and p["name"] == PROFILE_NAME:
            return p
    return None


def new_profile_mods(launcher):
    if launcher == "CurseForge":
        root = curseforge_instances() or Path.home() / "curseforge/minecraft/Instances"
        return root / PROFILE_NAME / "mods"
    return (prism_root() or appdata() / "PrismLauncher") / "instances" / PROFILE_NAME / ".minecraft" / "mods"


# ------------------------------------------------------------------------------------- misc

def setup_version():
    v = json.loads((HERE / "version.json").read_text(encoding="utf-8"))
    return {"version": str(v["version"]), "content": int(v.get("content", 1))}


def free_gb(path):
    p = Path(path)
    while not p.exists() and p.parent != p:
        p = p.parent
    return shutil.disk_usage(p).free / 1e9


def start_thread(fn):
    t = threading.Thread(target=fn, daemon=True)
    t.start()
    return t


def jar_name():
    return f"DishonoredCraft-{setup_version()['version']}.jar"


# ------------------------------------------------------------------------------------- install

class Cancelled(Exception):
    pass


class Installer:
    """Runs the steps in a thread; `on_progress(fraction, step text)` and `on_log(line)` are called from that
    thread (the GUI marshals them). `mods` is the mods folder to put the jar in; with `new_profile` (a launcher name:
    "CurseForge", "Prism Launcher" or "MultiMC"), the setup first makes (or updates) the DishonoredCraft profile in
    that launcher and `mods` is that profile's."""

    def __init__(self, game, mods, new_profile, on_progress, on_log):
        self.game = Path(game)
        self.new_profile = new_profile
        self.mods = Path(mods) if mods else new_profile_mods(new_profile)
        self.work = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "DCSetup"
        self.on_progress = on_progress
        self.on_log = on_log
        self.cancelled = False
        self.proc = None
        self.log_file = None
        self._base, self._span = 0.0, 0.0

    # -------------------------------------------------- plumbing
    def log(self, line):
        self.on_log(line)
        if self.log_file:
            self.log_file.write(line + "\n")
            self.log_file.flush()

    def step(self, frac, text):
        self.check()
        self._base = frac
        self.on_progress(frac, text)
        self.log(f"== {text}")

    def check(self):
        if self.cancelled:
            raise Cancelled()

    def cancel(self):
        self.cancelled = True
        if self.proc and self.proc.poll() is None:
            try:
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(self.proc.pid)], creationflags=NO_WINDOW,
                               capture_output=True)
            except OSError:
                pass

    def run(self, cmd, env=None, cwd=None, on_line=None):
        self.check()
        self.proc = subprocess.Popen([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                     encoding="utf-8", errors="replace", env=env, cwd=cwd, creationflags=NO_WINDOW)
        for line in self.proc.stdout:
            line = line.rstrip()
            if line:
                self.log("   " + line)
                if on_line:
                    on_line(line)
            if self.cancelled:
                break
        code = self.proc.wait()
        self.check()
        if code != 0:
            raise RuntimeError(f"{Path(str(cmd[1] if len(cmd) > 1 else cmd[0])).name} stopped with an error (code {code}); "
                               "see the log above")

    def download(self, url, dest, what):
        self.log(f"   downloading {what} from {url}")
        dest.parent.mkdir(parents=True, exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "DishonoredCraft-setup"})
        with urllib.request.urlopen(req, timeout=60) as r, open(dest, "wb") as f:
            total = int(r.headers.get("Content-Length") or 0)
            got = 0
            while True:
                self.check()
                chunk = r.read(1 << 16)
                if not chunk:
                    break
                f.write(chunk)
                got += len(chunk)
            self.log(f"   {got / 1e6:.1f} MB" + (f" of {total / 1e6:.1f} MB" if total else ""))
        return dest

    # -------------------------------------------------- steps
    def install(self):
        self.mods.parent.mkdir(parents=True, exist_ok=True)
        if self.work.exists():
            shutil.rmtree(self.work, ignore_errors=True)
        self.work.mkdir(parents=True, exist_ok=True)
        self.log_file = open(self.work.parent / "DishonoredCraft_setup_log.txt", "w", encoding="utf-8")
        self.log(f"DishonoredCraft setup {setup_version()['version']}  {datetime.now():%Y-%m-%d %H:%M}")
        self.log(f"Dishonored: {self.game}")
        self.log(f"Jar goes to: {self.mods}")
        tools = self.tools()
        if self.new_profile:
            self.make_profile()
        pack = self.extract(tools)
        jar = self.build_jar(pack)
        if self.new_profile == "CurseForge":
            self.profile_icon(pack)
        self.step(0.98, "Cleaning up")
        shutil.rmtree(self.work, ignore_errors=True)
        self.step(1.0, "Done")
        self.log(f"Built {jar}")
        log_path = self.work.parent / "DishonoredCraft_setup_log.txt"
        self.log_file.close()
        self.log_file = None
        try:
            shutil.copy2(log_path, self.mods.parent / "DishonoredCraft_setup_log.txt")
        except OSError:
            pass
        return jar

    def tools(self):
        self.step(0.01, "Getting the tools ready")
        t = {"umodel": BIN / "umodel" / "umodel_64.exe", "vgmstream": BIN / "vgmstream" / "vgmstream-cli.exe",
             "ffmpeg": BIN / "ffmpeg.exe", "wwiser": BIN / "wwiser.pyz"}
        for k in ("umodel", "vgmstream"):
            if not t[k].exists():
                raise RuntimeError(f"{t[k]} is missing - please extract the whole download again.")
        if not t["ffmpeg"].exists():
            w = shutil.which("ffmpeg")
            if not w:
                raise RuntimeError("bin\\ffmpeg.exe is missing - please extract the whole download again.")
            t["ffmpeg"] = Path(w)
        if not t["wwiser"].exists():
            t["wwiser"] = self.download(WWISER_URL, self.work / "wwiser.pyz", "wwiser (Wwise sound bank reader)")
        return t

    def extract(self, t):
        """Run the extract + bake pipeline (tools/assets/import_dishonored.py) into the work folder's pack."""
        pack = self.work / "pack"
        env = dict(os.environ)
        env.update({"DC_EXTRACT": str(self.work / "extract"), "DISHONORED_EXE": str(self.game / GAME_FILES[0]),
                    "DC_VGMSTREAM": str(t["vgmstream"]), "DC_FFMPEG": str(t["ffmpeg"]), "PYTHONUNBUFFERED": "1",
                    "PYTHONIOENCODING": "utf-8"})
        steps = {"1": (0.10, "Reading Dishonored's sound names"), "2": (0.16, "Unpacking the sound banks"),
                 "3": (0.22, "Finding Corvo's sounds"), "4": (0.28, "Converting the sounds"),
                 "5": (0.45, "Exporting Corvo's arms, blade, pistol and crossbow"),
                 "6": (0.55, "Decoding Dishonored's animations"),
                 "7": (0.80, "Making the HUD, icons, rats, elixirs and gadgets")}

        def on_line(line):
            m = re.match(r"^(\d)/7 ", line)
            if m and m.group(1) in steps:
                frac, text = steps[m.group(1)]
                self.on_progress(frac, text)

        self.step(0.05, "Building Dishonored's files for Minecraft")
        self.run([PY, TOOLS / "assets" / "import_dishonored.py", "--game", self.game, "--pack", pack,
                  "--umodel", t["umodel"], "--wwiser", t["wwiser"]], env=env, on_line=on_line)
        if not (pack / "assets" / "dishonored" / "fp" / "anims.json").exists():
            raise RuntimeError("the animations were not built (fp/anims.json is missing); see the log above")
        return pack

    def build_jar(self, pack):
        """The mod's code jar + the built Dishonored files -> <mods>/DishonoredCraft-<version>.jar."""
        self.step(0.92, "Building DishonoredCraft.jar")
        base = sorted(PAYLOAD.glob("DishonoredCraft-*.jar"))
        base = [b for b in base if not b.name.endswith("-sources.jar")]
        if not base:
            raise RuntimeError("payload\\DishonoredCraft-base.jar is missing - please extract the whole download again.")
        self.mods.mkdir(parents=True, exist_ok=True)
        out = self.mods / jar_name()
        tmp = self.work / out.name
        added = set()
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zout:
            assets = pack / "assets"
            for f in sorted(assets.rglob("*")):
                if f.is_file():
                    name = f.relative_to(pack).as_posix()
                    zout.write(f, name)
                    added.add(name)
            with zipfile.ZipFile(base[-1]) as zin:
                for info in zin.infolist():
                    if info.filename not in added:
                        zout.writestr(info, zin.read(info.filename))
        # older DishonoredCraft jars in that folder would load twice
        for old in list(self.mods.glob("DishonoredCraft-*.jar")) + list(self.mods.glob("dishonored-mc-forge-*.jar")):
            if old.name != out.name:
                self.log(f"   removing the older {old.name}")
                try:
                    old.unlink()
                except OSError as e:
                    self.log(f"   (could not remove it: {e.strerror}; please delete it yourself)")
        shutil.move(str(tmp), str(out))
        self.log(f"   {out}  ({out.stat().st_size / 1e6:.1f} MB)")
        return out

    def profile_icon(self, pack):
        """The CurseForge profile's picture: Dishonored's game icon, the Mark of the Outsider, white on Dishonored's dark
        green (made from the player's own files, so it stays on this PC)."""
        try:
            import math
            from PIL import Image
            mark = Image.open(pack / "assets/dishonored/textures/gui/hud/mark_outsider.png").convert("RGBA")
            m = mark.crop(mark.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox())
            k = 230 / max(m.size)
            m = m.resize((round(m.width * k), round(m.height * k)), Image.LANCZOS)
            icon = Image.new("RGBA", (256, 256))
            for y in range(256):
                for x in range(256):
                    c = int(20 + 28 * max(0.0, 1 - math.hypot(x - 128, y - 128) / 181))
                    icon.putpixel((x, y), (c, c + 11, c + 7, 255))
            white = Image.new("RGBA", m.size, (230, 232, 226, 255))
            white.putalpha(m.getchannel("A"))
            icon.alpha_composite(white, ((256 - m.width) // 2, (256 - m.height) // 2))
            inst = self.mods.parent
            icon.save(inst / "dishonoredcraft.png")
            mi = inst / "minecraftinstance.json"
            data = json.loads(mi.read_text(encoding="utf-8"))
            data["profileImagePath"] = str(inst / "dishonoredcraft.png")
            mi.write_text(json.dumps(data, indent=2), encoding="utf-8")
        except Exception as e:  # noqa: BLE001 - only cosmetic
            self.log(f"   (profile picture skipped: {e})")

    def make_profile(self):
        """The DishonoredCraft profile (Forge 1.20.1) in the chosen modding launcher. The launcher downloads Minecraft
        and Forge itself the first time the profile is played. An existing DishonoredCraft profile is kept as it is."""
        if dc_profile(self.new_profile):
            self.mods = dc_profile(self.new_profile)["mods"]
            return
        self.step(0.03, f"Making the DishonoredCraft profile in {self.new_profile}")
        if self.new_profile == "CurseForge":
            root = curseforge_instances() or Path.home() / "curseforge/minecraft/Instances"
            inst = root / PROFILE_NAME
            (inst / "mods").mkdir(parents=True, exist_ok=True)
            loader = json.loads((HERE / "data" / f"curseforge_forge-{MINECRAFT}-{FORGE}.json").read_text(encoding="utf-8"))
            import uuid
            now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f0Z")
            data = {
                "baseModLoader": loader, "isUnlocked": True, "javaArgsOverride": None, "lastPlayed": "0001-01-01T00:00:00",
                "playedCount": 0, "timePlayed": 0, "manifest": None, "fileDate": "0001-01-01T00:00:00", "installedModpack": None,
                "projectID": 0, "fileID": 0, "customAuthor": None, "modpackOverrides": [], "isMemoryOverride": False,
                "allocatedMemory": 4096, "profileImagePath": None, "groupId": None, "isVanilla": False, "guid": str(uuid.uuid4()),
                "gameTypeID": 432, "installPath": str(inst) + "\\", "name": PROFILE_NAME, "cachedScans": [], "isValid": True,
                "lastPreviousMatchUpdate": "0001-01-01T00:00:00", "lastRefreshAttempt": "0001-01-01T00:00:00", "isEnabled": True,
                "gameVersion": MINECRAFT, "gameVersionFlavor": None, "gameVersionTypeId": None, "preferenceAlternateFile": False,
                "preferenceAutoInstallUpdates": False, "preferenceDeleteOrphanedDependencies": False,
                "preferenceDeleteSavedVariables": False, "preferenceReleaseType": 1, "preferenceModdingFolderPath": None,
                "installDate": now, "installedAddons": [], "installedGamePrerequisites": [], "wasNameManuallyChanged": False,
                "wasGameVersionTypeIdManuallyChanged": False}
            (inst / "minecraftinstance.json").write_text(json.dumps(data, indent=2), encoding="utf-8")
            self.mods = inst / "mods"
        else:
            r = prism_root() or appdata() / "PrismLauncher"
            inst = r / "instances" / PROFILE_NAME
            (inst / ".minecraft" / "mods").mkdir(parents=True, exist_ok=True)
            (inst / "instance.cfg").write_text(f"InstanceType=OneSix\nname={PROFILE_NAME}\n", encoding="utf-8")
            pack = {"formatVersion": 1, "components": [
                {"uid": "net.minecraft", "version": MINECRAFT, "important": True},
                {"uid": "net.minecraftforge", "version": FORGE}]}
            (inst / "mmc-pack.json").write_text(json.dumps(pack, indent=2), encoding="utf-8")
            self.mods = inst / ".minecraft" / "mods"
        # DishonoredCraft's key layout (mouse buttons for powers / wheel, C to crouch, toggle crouch ...) for a fresh
        # profile; an options.txt that is already there (the player's own) is left alone. Its first line is the data
        # version: without it Minecraft treats the file as ancient and resets the keys to its defaults.
        options = self.mods.parent / "options.txt"
        if not options.exists():
            shutil.copy2(HERE / "data" / "options_keys.txt", options)
        self.log(f"   profile '{PROFILE_NAME}' -> {self.mods.parent}")
