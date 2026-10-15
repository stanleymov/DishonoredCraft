//! Embeds the version info (dishonoredcraft.rc) into the exe. Built with the GNU toolchain
//! (cargo +stable-x86_64-pc-windows-gnu build --release): llvm-windres turns the .rc into a COFF object the
//! MinGW linker accepts.
use std::{env, path::PathBuf, process::Command};

fn main() {
    println!("cargo:rerun-if-changed=dishonoredcraft.rc");
    println!("cargo:rerun-if-changed=../icon/dishonoredcraft.ico");
    let out = PathBuf::from(env::var("OUT_DIR").unwrap()).join("dishonoredcraft_res.o");
    let windres = env::var("LLVM_WINDRES").unwrap_or_else(|_| {
        let home = env::var("USERPROFILE").unwrap_or_default();
        format!("{home}/modtools/llvm/bin/llvm-windres.exe")
    });
    let ok = Command::new(&windres)
        .args(["--target=x86_64-w64-mingw32", "-O", "coff", "-i", "dishonoredcraft.rc", "-o"])
        .arg(&out)
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    if ok {
        println!("cargo:rustc-link-arg-bins={}", out.display());
    } else {
        println!("cargo:warning=llvm-windres not found - building without the version info");
    }
}
