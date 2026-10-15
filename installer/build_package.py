"""Assemble the DishonoredCraft download: release/DishonoredCraft Setup/ (and a zip of it).

Only our own work and freely licensed tools go in - never any Dishonored files:
  Setup DishonoredCraft.exe   launcher (installer/launcher, Rust)
  runtime/                    Python (PSF) + numpy / scipy (BSD) + Pillow (HPND) + pefile (MIT) + unicorn (GPL-2)
                              + tkinter (Tcl/Tk licence)
  setup/                      setup.pyw, engine.py, version.json, tools/ (our extractors and bakers)
  bin/                        umodel (MIT), vgmstream (ISC-style; its codec DLLs LGPL/BSD), ffmpeg (GPL-3, portable)
  payload/                    DishonoredCraft-base.jar: the mod's code (ours), no Dishonored files
  license/                    all of the above licences + CREDITS

    python installer/build_package.py [--zip]        (build the mod first: ./gradlew build)
"""
import json
import shutil
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OUT = REPO / "release" / "DishonoredCraft Setup"
PY = Path(sys.base_prefix)
SITE = PY / "Lib" / "site-packages"
PACKAGES = ["numpy", "numpy.libs", "scipy", "scipy.libs", "PIL", "unicorn", "ordlookup"]
MODULES = ["pefile.py"]
STDLIB_SKIP = {"site-packages", "test", "idlelib", "ensurepip", "turtledemo", "lib2to3", "pydoc_data", "__pycache__"}
TOOLS = ["assets/import_dishonored.py", "assets/akpk_extract.py", "assets/build_pack.py", "assets/bake_anims.py",
         "assets/bake_world.py", "assets/crop_hud.py", "assets/gltf_mesh.py", "re/anim_pose.py", "re/edge_dump.py",
         "re/edge_emu.py", "re/props.py", "re/ue3_decompress.py", "re/upk.py"]
HOME_TOOLS = Path.home() / "tools"
FFMPEG = next((Path.home() / "AppData/Local/Microsoft/WinGet/Packages").glob("Gyan.FFmpeg*/ffmpeg-*_build"))


def copytree(src, dst, skip=()):
    for p in src.rglob("*"):
        rel = p.relative_to(src)
        if any(part in skip for part in rel.parts):
            continue
        q = dst / rel
        if p.is_dir():
            q.mkdir(parents=True, exist_ok=True)
        else:
            q.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, q)


def runtime():
    rt = OUT / "runtime"
    rt.mkdir(parents=True, exist_ok=True)
    for f in ["python.exe", "pythonw.exe", "python3.dll", "python312.dll", "vcruntime140.dll", "vcruntime140_1.dll",
              "LICENSE.txt"]:
        shutil.copy2(PY / f, rt / f)
    copytree(PY / "DLLs", rt / "DLLs")
    copytree(PY / "tcl", rt / "tcl", skip={"demos"})
    copytree(PY / "Lib", rt / "Lib", skip=STDLIB_SKIP)
    for pkg in PACKAGES:
        copytree(SITE / pkg, rt / "Lib" / "site-packages" / pkg, skip={"__pycache__", "tests"})
    for m in MODULES:
        shutil.copy2(SITE / m, rt / "Lib" / "site-packages" / m)
    for dist in SITE.glob("*.dist-info"):
        if dist.name.split("-")[0].lower() in ("numpy", "scipy", "pillow", "unicorn", "pefile"):
            copytree(dist, rt / "Lib" / "site-packages" / dist.name)


def setup_files():
    s = OUT / "setup"
    s.mkdir(parents=True, exist_ok=True)
    for f in ("setup.pyw", "engine.py", "version.json"):
        shutil.copy2(REPO / "installer" / f, s / f)
    copytree(REPO / "installer" / "data", s / "data")   # CurseForge's Forge loader entry for new profiles
    for t in TOOLS:
        (s / "tools" / t).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / "tools" / t, s / "tools" / t)


def binaries():
    b = OUT / "bin"
    (b / "umodel").mkdir(parents=True, exist_ok=True)
    for f in ("umodel_64.exe", "SDL2_64.dll", "LICENSE.txt", "readme.txt"):
        if (HOME_TOOLS / "umodel" / f).exists():
            shutil.copy2(HOME_TOOLS / "umodel" / f, b / "umodel" / f)
    copytree(HOME_TOOLS / "vgmstream", b / "vgmstream")
    shutil.copy2(FFMPEG / "bin" / "ffmpeg.exe", b / "ffmpeg.exe")


def payload():
    p = OUT / "payload"
    p.mkdir(parents=True, exist_ok=True)
    version = json.loads((REPO / "installer" / "version.json").read_text(encoding="utf-8"))["version"]
    jar = REPO / "build" / "libs" / f"DishonoredCraft-{version}.jar"
    if not jar.exists():
        raise SystemExit(f"{jar} is missing: set mod_version={version} in gradle.properties and run ./gradlew build")
    # (the code jar must never carry Dishonored files: only our own resources)
    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist() if n.startswith("assets/")]
        bad = [n for n in names if n.endswith(".ogg") or "/fp/" in n or "/textures/world/" in n
               or "/gui/hud/" in n or "/gui/wheel/" in n]
        if bad:
            raise SystemExit(f"the code jar contains files that look like Dishonored assets: {bad[:5]}")
    shutil.copy2(jar, p / f"DishonoredCraft-{version}.jar")


def licences():
    d = OUT / "license"
    d.mkdir(parents=True, exist_ok=True)
    shutil.copy2(REPO / "installer" / "licenses" / "CREDITS.txt", d / "CREDITS.txt")
    shutil.copy2(HOME_TOOLS / "umodel" / "LICENSE.txt", d / "umodel-LICENSE.txt")
    shutil.copy2(HOME_TOOLS / "vgmstream" / "COPYING", d / "vgmstream-COPYING.txt")
    shutil.copy2(PY / "LICENSE.txt", d / "Python-LICENSE.txt")
    for dist, name in (("numpy-*.dist-info", "numpy"), ("scipy-*.dist-info", "scipy"), ("pillow-*.dist-info", "Pillow"),
                       ("unicorn-*.dist-info", "unicorn"), ("pefile-*.dist-info", "pefile")):
        for di in SITE.glob(dist):
            for f in [*di.glob("LICENSE*"), *di.glob("COPYING*"), *(di / "licenses").rglob("*")]:
                if f.is_file():
                    shutil.copy2(f, d / f"{name}-{f.name}{'' if f.suffix else '.txt'}")
    (d / "unicorn-source.txt").write_text(
        "runtime/Lib/site-packages/unicorn is the Unicorn CPU emulator, version 2.1.4, unmodified, from PyPI\n"
        "(https://pypi.org/project/unicorn/2.1.4/). It is GPL-2.0: its complete source is at\n"
        "https://github.com/unicorn-engine/unicorn/tree/2.1.4 and in the PyPI source distribution.\n", encoding="utf-8")
    shutil.copy2(FFMPEG / "LICENSE", d / "ffmpeg-GPL-3.0.txt")
    shutil.copy2(FFMPEG / "README.txt", d / "ffmpeg-build-README.txt")
    (d / "ffmpeg-source.txt").write_text(
        "bin/ffmpeg.exe is FFmpeg, the unmodified static Windows build from www.gyan.dev (GPL v3; build configuration in\n"
        "ffmpeg-build-README.txt). Source: https://ffmpeg.org/download.html; the external libraries' sources are linked\n"
        "from https://www.gyan.dev/ffmpeg/builds/. It is portable: the setup runs it from bin/ while building the sounds -\n"
        "it is not installed on your PC and the mod never uses it.\n", encoding="utf-8")


def launcher():
    exe = REPO / "installer" / "launcher" / "target" / "release" / "dishonoredcraft_setup.exe"
    if exe.exists():
        shutil.copy2(exe, OUT / "Setup DishonoredCraft.exe")
    else:
        print("!! launcher not built (cargo build --release in installer/launcher)")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for child in OUT.iterdir():
        shutil.rmtree(child) if child.is_dir() else child.unlink()
    runtime()
    setup_files()
    binaries()
    payload()
    licences()
    launcher()
    shutil.copy2(REPO / "installer" / "icon" / "dishonoredcraft.ico", OUT / "dishonoredcraft.ico")   # wizard window
    (OUT / "READ ME FIRST.txt").write_text(
        "DishonoredCraft - setup\n\n"
        "Double-click \"Setup DishonoredCraft.exe\". You need Dishonored (the 2012 game, Steam version) installed and\n"
        "Minecraft Java Edition. The setup builds DishonoredCraft.jar from YOUR copy of Dishonored - no Dishonored files\n"
        "are included here - and puts it into a Minecraft Forge 1.20.1 profile (yours, or a new one it makes).\n\n"
        "The jar it builds contains Dishonored's art and sounds from your own game: it is for you only. Please don't\n"
        "share it; share this setup instead.\n\n"
        "Command windows may flash while it runs; that is normal.\n", encoding="utf-8")
    size = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file()) / 1e6
    print(f"{OUT}: {size:.0f} MB")
    if "--zip" in sys.argv:
        version = json.loads((REPO / "installer" / "version.json").read_text(encoding="utf-8"))["version"]
        z = OUT.parent / f"DishonoredCraft Setup {version}.zip"
        with zipfile.ZipFile(z, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
            for f in OUT.rglob("*"):
                if f.is_file():
                    zf.write(f, f.relative_to(OUT.parent))
        print(f"{z}: {z.stat().st_size / 1e6:.0f} MB")


if __name__ == "__main__":
    main()
