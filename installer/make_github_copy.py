"""Make a clean, code-only copy of DishonoredCraft for GitHub: dishonored-mc-forge/github/DishonoredCraft (its own git
repo). No Dishonored files or anything derived from them (no extracted art / sounds, no disassembly notes), no test
recordings, no dev journal, no personal automation scripts."""
import shutil
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OUT = REPO / "github" / "DishonoredCraft"
SKIP = {
    "MODLOG.md", "env.sh", "README.md",              # dev journal, this PC's paths, the old Fabric readme
    "tools/mc.sh", "tools/mc_drive.py",              # personal test automation
    "tools/re/f_ar.txt", "tools/re/f_const.txt", "tools/re/f_cr.txt",   # disassembly of Dishonored.exe: never shared
    "tools/re/exedis.py", "tools/re/anim_preview.py", "tools/assets/pose_try.py", "tools/assets/pose_arms.py",
    "tools/assets/poses.py", "tools/assets/upk_strings.py",
}


def tracked():
    r = subprocess.run(["git", "-C", str(REPO), "ls-files"], capture_output=True, text=True, check=True)
    return [f for f in r.stdout.splitlines() if f not in SKIP]


def main():
    keep_git = OUT / ".git"
    if OUT.exists():
        for child in OUT.iterdir():
            if child != keep_git:
                shutil.rmtree(child) if child.is_dir() else child.unlink()
    OUT.mkdir(parents=True, exist_ok=True)
    n = 0
    for f in tracked():
        d = OUT / f
        d.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / f, d)
        n += 1
    shutil.copy2(REPO / "installer" / "README_github.md", OUT / "README.md")
    gi = (OUT / ".gitignore").read_text(encoding="utf-8")
    if "github/" not in gi:
        (OUT / ".gitignore").write_text(gi.rstrip() + "\ngithub/\n", encoding="utf-8")
    print("copied", n, "files to", OUT)


if __name__ == "__main__":
    main()
