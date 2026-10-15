#!/usr/bin/env python3
"""Build/verify the bounded JNI adapter against the exact accepted VNC input.

All upstream compilation is isolated. The accepted base receipt/libraries/source
are read-only; static patched VNC is embedded so its extended ABI cannot mix with
the original shared client. No network, SDK install, app or device operation.
"""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

import prepare_vnc_native as base
from prepare_rdp_native import elf_info, replay_patch


ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / "qualification/probes/native-vnc/adapter"
CACHE = ROOT / ".cache/native-vnc/adapter"
LIBRARY = "libcloudy-vnc-jni.so"
EXPORTS = {f"Java_dev_cloudy_qualification_vnc_VncBridge_{name}" for name in
           ("create", "start", "cancel", "join", "free", "status", "pointer", "key", "releaseInputs")}


def snapshot():
    paths = [Path(__file__).resolve(), ROOT / "tools/prepare_vnc_native.py",
             *(ROOT / name for name in base.SHARED)]
    paths += sorted(path for path in ADAPTER.iterdir() if path.suffix in (".c", ".h", ".java", ".json", ".patch")
                    or path.name == "CMakeLists.txt")
    contents = {str(path.relative_to(ROOT)): base.read(path) for path in paths}
    return {name: base.digest(raw) for name, raw in contents.items()}, contents


def unchanged(inputs):
    if snapshot()[0] != inputs:
        raise ValueError("Adapter inputs changed; publication rejected")


def patched_source(base_run, target, lock):
    base.verified(base.read(base_run / "receipt.json"), lock["baseReceipt"])
    receipt = base.check(base_run)
    shutil.copytree(base_run / "source", target)
    patch = base.verified(base.read(ADAPTER / lock["patch"]["path"]), lock["patch"])
    replay_patch(target, patch)
    return receipt


def check(run, receipt=None):
    if receipt is None:
        receipt = json.loads(base.read(run / "receipt.json"))
    inputs, _ = snapshot()
    if receipt["inputs"] != inputs or receipt["exitCode"] != 0:
        raise ValueError("Adapter receipt stale or failed")
    prepared = run / "prepared"
    if base.hashes(prepared) != receipt["prepared"]:
        raise ValueError("Adapter prepared outputs changed")
    if any(base.digest(base.read(run / name)) != digest for name, digest in receipt["evidence"].items()):
        raise ValueError("Adapter compiler evidence changed")
    notices = prepared / "nativeNotices"
    base_lock = json.loads(base.read(notices / "build-inputs/qualification/probes/native-vnc/sources.lock.json"))
    with tempfile.TemporaryDirectory(prefix="cloudy-vnc-adapter-check-") as temporary:
        source = Path(temporary) / "source"
        base.extract(base.read(notices / base_lock["source"]["archive"]), base_lock["source"], source)
        replay_patch(source, base.read(ADAPTER / "security-and-cancel.patch"))
        if base.hashes(source) != receipt["sourceFiles"] or base.hashes(notices / "patched-vnc-source") != receipt["sourceFiles"]:
            raise ValueError("Patched source not exact base plus reviewed patch")
    for name, item in receipt["libraries"].items():
        raw = base.verified(base.read(prepared / "nativeLibraries/arm64-v8a" / name), item)
        if elf_info(raw) != item["elf"]:
            raise ValueError("Adapter ELF changed")
    return receipt


def build(args, run):
    inputs, contents = snapshot()
    lock = json.loads(contents[str((ADAPTER / "build.lock.json").relative_to(ROOT))])
    run.mkdir(parents=True, exist_ok=False)
    try:
        base.save(run / "inputs.json", inputs)
        for name, raw in contents.items():
            path = run / "input-snapshot" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        limits = {"jobs": 4, "timeoutSeconds": 240, "maximumLogBytes": 8388608}
        with tempfile.TemporaryDirectory(prefix="cloudy-vnc-adapter-") as temporary:
            stage = Path(temporary)
            source = stage / "source"
            accepted = patched_source(args.base_run, source, lock)
            source_files = base.hashes(source)
            base_prepared = args.base_run / "prepared"
            notices = base_prepared / "nativeNotices"
            shutil.copytree(notices / "openssl-import", stage / "ssl")
            base_lock = json.loads(base.read(notices / "build-inputs/qualification/probes/native-vnc/sources.lock.json"))
            base.extract(base.read(notices / base_lock["jpeg"]["archive"]), base_lock["jpeg"], stage / "jpeg")
            sdk = args.sdk.resolve(strict=True)
            cmake_dir = sdk / "cmake/4.1.2/bin"
            cmake = str(cmake_dir / "cmake")
            ndk = sdk / "ndk/29.0.14206865"
            # Reuse hash-bound base CMake recipes, rebasing only the private stage
            # and SDK root. Never copy a mutable configured CMake build graph.
            original = json.loads(base.read(args.base_run / "vnc-configure.json"))["command"]
            old_stage = str(Path(original[original.index("-S") + 1]).parent)
            old_sdk = str(Path(original[0]).parents[3])
            def rebase(command):
                return [arg.replace(old_stage, str(stage)).replace(old_sdk, str(sdk)) for arg in command]
            jpeg_command = rebase(json.loads(base.read(args.base_run / "jpeg-configure.json"))["command"])
            base.bounded(jpeg_command, run, "jpeg-configure", limits)
            # Configure only to reproduce jconfig.h; reuse the accepted actual PIC archive.
            shutil.copyfile(notices / "libjpeg-android-pic.a", stage / "jpeg-build/libjpeg.a")
            include = stage / "jpeg-include"
            include.mkdir()
            for name in ("jpeglib.h", "jmorecfg.h", "jerror.h"):
                shutil.copyfile(stage / "jpeg/src" / name, include / name)
            shutil.copyfile(stage / "jpeg-build/jconfig.h", include / "jconfig.h")
            command = [arg.replace("-DBUILD_SHARED_LIBS=ON", "-DBUILD_SHARED_LIBS=OFF") for arg in rebase(original)]
            base.bounded(command, run, "vnc-configure", limits)
            base.compile_contract(stage / "vnc-build")
            base.bounded([cmake, "--build", str(stage / "vnc-build"), "--target", "vncclient", "--parallel", "4"],
                         run, "vnc-build", limits)
            adapter_source = stage / "adapter"
            adapter_source.mkdir()
            for name, raw in contents.items():
                if str(Path(name).parent) == str(ADAPTER.relative_to(ROOT)):
                    (adapter_source / Path(name).name).write_bytes(raw)
            command = [cmake, "-S", str(adapter_source), "-B", str(stage / "adapter-build"), "-G", "Ninja",
                       f"-DCMAKE_MAKE_PROGRAM={cmake_dir / 'ninja'}", f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
                       "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-29", "-DCMAKE_BUILD_TYPE=RelWithDebInfo",
                       f"-DVNC_SOURCE={source}", f"-DVNC_BUILD={stage / 'vnc-build'}", f"-DSSL_PREFIX={stage / 'ssl'}",
                       f"-DJPEG_ARCHIVE={stage / 'jpeg-build/libjpeg.a'}"]
            base.bounded(command, run, "jni-configure", limits)
            base.bounded([cmake, "--build", str(stage / "adapter-build"), "--target", "cloudy-vnc-jni", "--parallel", "4"],
                         run, "jni-build", limits)
            library = stage / "adapter-build" / LIBRARY
            nm = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
            exports = subprocess.check_output([str(nm), "-D", "--defined-only", str(library)], text=True, timeout=15)
            (run / "exports.txt").write_text(exports)
            if not EXPORTS <= {line.split()[-1] for line in exports.splitlines() if line.strip()}:
                raise ValueError("Actual adapter JNI export missing")
            javac = shutil.which("javac")
            if not javac:
                raise ValueError("Existing javac unavailable; no Java ABI pass")
            base.bounded([javac, "--release", "17", "-Xlint:all", "-Werror", "-d", str(stage / "java-classes"),
                          "-h", str(stage / "jni-headers"), str(adapter_source / "VncBridge.java")], run, "java-compile", limits)
            unchanged(inputs)
            if base.hashes(source) != source_files:
                raise ValueError("Compiler changed patched source")
            prepared = run / "prepared"
            target_notices = prepared / "nativeNotices"
            shutil.copytree(notices, target_notices)
            (target_notices / "NOTICE.txt").rename(target_notices / "BASE-NOTICE.txt")
            (target_notices / "NOTICE.txt").write_text(
                "Private JNI adapter build only; no Android runtime/server/device result or distribution approval.\n"
                "Patched GPL-2.0-or-later LibVNCClient is statically embedded in authored JNI; do not load the original client SO.\n"
                "Exact base archives/notices, patched client source/headers/static archive and authored inputs retained.\n"
                "Only VeNCrypt X509Plain with unconditional leaf SHA256+validity; TLS1.2+, no renegotiation.\n"
                "No plaintext/anonymous/None route, clipboard/drive/agent implementation or relative emulation.\n")
            shutil.copytree(source, target_notices / "patched-vnc-source")
            shutil.copyfile(stage / "vnc-build/libvncclient.a", target_notices / "patched-libvncclient.a")
            shutil.copytree(source / "include", prepared / "nativeIncludes")
            shutil.copyfile(stage / "vnc-build/include/rfb/rfbconfig.h", prepared / "nativeIncludes/rfb/rfbconfig.h")
            for name, raw in contents.items():
                path = target_notices / "adapter-inputs" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(raw)
            libs = prepared / "nativeLibraries/arm64-v8a"
            libs.mkdir(parents=True)
            files = {LIBRARY: base.read(library)}
            files.update({name: base.read(base_prepared / "nativeLibraries/arm64-v8a" / name) for name in ("libssl.so", "libcrypto.so")})
            actual = {}
            for name, raw in files.items():
                info = elf_info(raw)
                if info["soname"] != name or not set(info["needed"]) <= {"libssl.so", "libcrypto.so", "libc.so", "libdl.so", "libm.so", "libz.so"}:
                    raise ValueError("Unexpected adapter ELF closure")
                (libs / name).write_bytes(raw)
                actual[name] = {"bytes": len(raw), "sha256": base.digest(raw), "elf": info}
            evidence_names = [name.name for name in run.iterdir() if name.is_file()]
            receipt = {"schemaVersion": 1, "exitCode": 0, "scope": "native-adapter-build-only-no-runtime",
                       "buildRef": lock["buildRef"], "inputs": inputs, "baseReceiptSha256": lock["baseReceipt"]["sha256"],
                       "baseToolchain": accepted["toolchain"], "sourceFiles": source_files,
                       "libraries": actual, "prepared": base.hashes(prepared),
                       "requiredJniExports": sorted(EXPORTS),
                       "evidence": {name: base.digest(base.read(run / name)) for name in evidence_names}}
            check(run, receipt)
            unchanged(inputs)
            base.save(run / "receipt.json", receipt)
            return receipt
    except BaseException as error:
        base.save(run / "failure.json", {"status": "incomplete-no-accepted-receipt", "class": type(error).__name__, "detail": str(error)})
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--build", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--run", required=True)
    parser.add_argument("--base-run", type=Path, default=ROOT / ".cache/native-vnc/entry-03")
    parser.add_argument("--sdk", type=Path, default=ROOT / ".cache/android-sdk")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", args.run):
        parser.error("Run must be a bounded lowercase filename")
    run = base.safe(CACHE / args.run)
    try:
        receipt = build(args, run) if args.build else check(run)
        print(json.dumps({"status": "passed", "scope": receipt["scope"], "libraries": receipt["libraries"],
                          "receipt": str(run / "receipt.json")}, indent=2))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        parser.exit(1, f"VNC adapter preparation failed: {error}\n")


if __name__ == "__main__":
    main()
