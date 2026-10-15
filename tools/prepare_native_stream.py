#!/usr/bin/env python3
"""Prepare/build pinned Moonlight native core, or verify one retained run offline."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.request

from build_rdp_native import stop_process_group
from prepare_rdp_native import elf_info, replay_patch

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "qualification/probes/native-stream"
LOCK = PROBE / "sources.lock.json"
CACHE = ROOT / ".cache/native-stream-build"
SHARED = ("tools/build_rdp_native.py", "tools/prepare_rdp_native.py")
LIBRARY = "libmoonlight-core.so"
EXPECTED_JNI = {f"Java_com_limelight_nvstream_jni_MoonBridge_{name}"
                for name in ("init", "startConnection", "stopConnection", "interruptConnection", "sendMouseMove")}


def no_symlinks(path):
    path = path.absolute()
    for item in (path, *path.parents):
        if item.is_symlink():
            raise ValueError(f"Symlink path rejected: {item}")
    return path


def read(path):
    return no_symlinks(path).read_bytes()


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def relative(name, allow_empty=False):
    if allow_empty and name == "":
        return PurePosixPath(".")
    path = PurePosixPath(name)
    if not name or path.is_absolute() or ".." in path.parts or "\\" in name:
        raise ValueError("Unsafe relative path")
    return path


def tracked_snapshot():
    paths = [LOCK, Path(__file__).resolve(), *(ROOT / name for name in SHARED)]
    paths.extend(sorted((PROBE / "patches").glob("*.patch")))
    contents = {str(path.relative_to(ROOT)): read(path) for path in paths}
    return {name: sha(raw) for name, raw in contents.items()}, contents


def unchanged(expected):
    if tracked_snapshot()[0] != expected:
        raise ValueError("Build inputs changed; refusing output publication")


def read_lock(contents):
    lock = json.loads(contents[str(LOCK.relative_to(ROOT))])
    declared = {item["path"] for item in lock["patches"]}
    actual = {str(path.relative_to(PROBE)) for path in (PROBE / "patches").glob("*.patch")}
    if declared != actual or len(declared) != len(lock["patches"]):
        raise ValueError("Patch set differs from ordered lock")
    for item in lock["patches"]:
        relative(item["path"])
        verified(contents[str((PROBE / item["path"]).relative_to(ROOT))], item)
    if len(lock["sources"]) != 4 or [item["name"] for item in lock["sources"]] != ["moonlight-android", "moonlight-common-c", "enet", "nanors"]:
        raise ValueError("Expected the reviewed four-source insertion order")
    for item in lock["sources"]:
        if len(PurePosixPath(item["archive"]).parts) != 1:
            raise ValueError("Archive must be a filename")
        relative(item["archive"])
        relative(item["destination"], allow_empty=True)
    return lock


def verified(raw, pin):
    if len(raw) != pin["bytes"] or sha(raw) != pin["sha256"]:
        raise ValueError("Pinned input byte count or SHA256 differs")
    return raw


def acquire(pin, name):
    directory = no_symlinks(CACHE / "downloads")
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / name
    if path.exists():
        return verified(read(path), pin)
    # Reuse the already inspected research cache without trusting cache presence.
    hints = [ROOT / ".cache/native-stream-entry" / name]
    if name == "OpenSSL-LICENSE.txt":
        hints.append(ROOT / ".cache/native-stream-entry/openssl-LICENSE.txt")
    if name == "Opus-COPYING.txt":
        hints.append(ROOT / ".cache/native-stream-entry/opus-COPYING")
    raw = None
    for hint in hints:
        if hint.exists():
            raw = verified(read(hint), pin)
            break
    if raw is None:
        if not pin["url"].startswith("https://"):
            raise ValueError("Pinned download requires HTTPS")
        with urllib.request.urlopen(pin["url"], timeout=90) as response:
            raw = verified(response.read(pin["bytes"] + 1), pin)
    with tempfile.NamedTemporaryFile(dir=directory, delete=False) as stream:
        temporary = Path(stream.name)
        try:
            stream.write(raw)
            stream.close()
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)
    return raw


def extract_source(raw, pin, destination):
    destination.mkdir(parents=True, exist_ok=True)
    seen = set()
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as bundle:
        for member in bundle.getmembers():
            path = relative(member.name)
            if path.parts[0] != pin["root"] or path in seen:
                raise ValueError("Archive root or duplicate member differs")
            seen.add(path)
            if not (member.isfile() or member.isdir()):
                raise ValueError("Archive links/special files are not supported")
            parts = path.parts[1:]
            if not parts:
                continue
            target = no_symlinks(destination.joinpath(*parts))
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                if target.exists():
                    raise ValueError("Source insertion would overwrite a file")
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(member) as stream:
                    target.write_bytes(stream.read())


def source_tree(source, lock, archives, contents):
    for pin in lock["sources"]:
        extract_source(verified(archives[pin["archive"]], pin), pin,
                       source / relative(pin["destination"], allow_empty=True))
    for patch in lock["patches"]:
        replay_patch(source, contents[str((PROBE / patch["path"]).relative_to(ROOT))])
    for pin in lock["staticInputs"]:
        verified(read(source / relative(pin["path"])), pin)


def hashes(directory):
    records = {}
    for path in sorted(no_symlinks(directory).rglob("*")):
        no_symlinks(path)
        if path.is_file():
            records[path.relative_to(directory).as_posix()] = sha(read(path))
        elif not path.is_dir():
            raise ValueError("Unexpected output file type")
    return records


def bounded_build(command, log, limits):
    start = time.monotonic()
    with log.open("x") as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, start_new_session=True,
                                   env=os.environ | {"MAKEFLAGS": "", "MFLAGS": ""})
        try:
            while process.poll() is None:
                if time.monotonic() - start > limits["timeoutSeconds"]:
                    raise subprocess.TimeoutExpired(command, limits["timeoutSeconds"])
                if log.stat().st_size > limits["maximumLogBytes"]:
                    raise ValueError("Native compiler log limit exceeded")
                time.sleep(0.1)
            if log.stat().st_size > limits["maximumLogBytes"]:
                raise ValueError("Native compiler log limit exceeded")
            if process.returncode:
                raise subprocess.CalledProcessError(process.returncode, command)
        except BaseException:
            stop_process_group(process)
            raise
    return time.monotonic() - start


def ndk_record(ndk):
    properties = read(ndk / "source.properties")
    clang = (ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/clang").resolve(strict=True)
    files = {"source.properties": sha(properties), "clang": sha(read(clang))}
    for path in sorted((ndk / "sources/android/cpufeatures").glob("*")):
        if path.is_file():
            files[path.relative_to(ndk).as_posix()] = sha(read(path))
    return {"files": files, "clangVersion": subprocess.check_output([str(clang), "--version"], text=True, timeout=15).splitlines()[0]}


def build(sdk, run):
    inputs, contents = tracked_snapshot()
    lock = read_lock(contents)
    limits = lock["toolchain"]
    run.mkdir(parents=True, exist_ok=False)
    try:
        archives = {pin["archive"]: acquire(pin, pin["archive"]) for pin in lock["sources"]}
        notices = {pin["file"]: acquire(pin, pin["file"]) for pin in lock["additionalNotices"]}
        ndk = sdk.resolve(strict=True) / "ndk" / limits["ndk"]
        properties = read(ndk / "source.properties").decode()
        if f"Pkg.Revision = {limits['ndk']}" not in properties:
            raise ValueError("NDK revision differs from locked build input")
        toolchain = ndk_record(ndk)
        with tempfile.TemporaryDirectory(prefix="cloudy-native-stream-") as temporary:
            stage = Path(temporary)
            source = stage / "source"
            source_tree(source, lock, archives, contents)
            pristine = hashes(source)
            (stage / "ndk").symlink_to(ndk, target_is_directory=True)
            command = ["make", "-O", "-f", str(stage / "ndk/build/core/build-local.mk"),
                       f"NDK_PROJECT_PATH={stage}", f"APP_BUILD_SCRIPT={source}/app/src/main/jni/Android.mk",
                       f"NDK_APPLICATION_MK={source}/app/src/main/jni/Application.mk", "APP_MODULES=moonlight-core",
                       "APP_ABI=arm64-v8a", "PRODUCT_FLAVOR=nonRoot", "APP_OPTIM=release", "NDK_DEBUG=0",
                       f"-j{limits['jobs']}"]
            (run / "command.json").write_text(json.dumps(command, indent=2) + "\n")
            duration = bounded_build(command, run / "build.log", limits)
            unchanged(inputs)
            if hashes(source) != pristine:
                raise ValueError("Compiler changed the verified source tree")
            library = stage / "libs/arm64-v8a" / LIBRARY
            raw = read(library)
            elf = elf_info(raw)
            if elf["soname"] != LIBRARY or not set(elf["needed"]) <= {"libc.so", "libm.so", "libdl.so", "liblog.so"}:
                raise ValueError("Unexpected native dependency or SONAME")
            nm = stage / "ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
            symbols = subprocess.check_output([str(nm), "-D", "--defined-only", str(library)], text=True, timeout=30)
            (run / "exports.txt").write_text(symbols)
            exports = {line.split()[-1] for line in symbols.splitlines() if line.strip()}
            if not EXPECTED_JNI <= exports:
                raise ValueError("Required MoonBridge JNI exports missing")
            prepared = run / "prepared"
            libs = prepared / "nativeLibraries/arm64-v8a"
            licenses = prepared / "nativeNotices"
            libs.mkdir(parents=True)
            licenses.mkdir(parents=True)
            (libs / LIBRARY).write_bytes(raw)
            for name, data in archives.items():
                (licenses / name).write_bytes(data)
            for name, data in notices.items():
                (licenses / name).write_bytes(data)
            for target, path in {
                "Moonlight-Android-GPL3.txt": "LICENSE.txt",
                "Moonlight-Common-GPL3.txt": "app/src/main/jni/moonlight-core/moonlight-common-c/LICENSE.txt",
                "ENet-LICENSE.txt": "app/src/main/jni/moonlight-core/moonlight-common-c/enet/LICENSE",
                "nanors-LICENSE.txt": "app/src/main/jni/moonlight-core/moonlight-common-c/nanors/LICENSE",
            }.items():
                (licenses / target).write_bytes(read(source / path))
            for path in (ndk / "sources/android/cpufeatures").glob("*"):
                if path.is_file():
                    (licenses / ("NDK-cpufeatures-" + path.name)).write_bytes(read(path))
            for name, data in contents.items():
                target = licenses / "build-inputs" / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            recipe = [argument.replace(str(stage / "ndk"), "<NDK_ALIAS>").replace(str(stage), "<STAGE>") for argument in command]
            (licenses / "NOTICE.txt").write_text(
                "Private Moonlight native build feasibility only; no APK, runtime or product distribution approval.\n"
                "Moonlight Android and common-C carry GPLv3; ENet/nanors carry MIT text; Opus/OpenSSL/NDK terms are included.\n"
                "Full selected client/core/submodule archives and authored build inputs are retained here.\n"
                "Bundled static dependency source revisions are declared in sources.lock.json; no local reproducible-build claim.\n"
                "Reconstruct the four archives at their locked insertion points. Use an isolated no-space stage and NDK alias.\n"
                + json.dumps(recipe, indent=2) + "\n")
            shutil.copytree(source, run / "source")
            unchanged(inputs)
            receipt = {"schemaVersion": 1, "scope": "native-build-and-ELF-only; not APK/JNI runtime/streaming",
                       "buildRef": lock["buildRef"], "exitCode": 0, "durationSeconds": duration, "inputs": inputs,
                       "toolchain": toolchain, "sourceFiles": pristine, "prepared": hashes(prepared),
                       "library": {"bytes": len(raw), "sha256": sha(raw), "elf": elf, "requiredJniExports": sorted(EXPECTED_JNI)},
                       "evidence": {name: sha(read(run / name)) for name in ("build.log", "command.json", "exports.txt")}}
            check(run, receipt)
            unchanged(inputs)
            (run / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print_summary(run, receipt)
    except BaseException as failure:
        (run / "failure.json").write_text(json.dumps({"scope": "incomplete build; no successful receipt accepted",
                                                      "failureClass": type(failure).__name__, "detail": str(failure)}, indent=2) + "\n")
        raise


def check(run, receipt=None):
    if receipt is None:
        receipt = json.loads(read(run / "receipt.json"))
    inputs, contents = tracked_snapshot()
    if receipt["inputs"] != inputs:
        raise ValueError("Retained native build inputs differ; receipt is stale")
    lock = read_lock(contents)
    prepared = run / "prepared"
    if receipt["prepared"] != hashes(prepared):
        raise ValueError("Prepared library/notices differ from build receipt")
    archives = {pin["archive"]: verified(read(prepared / "nativeNotices" / pin["archive"]), pin) for pin in lock["sources"]}
    with tempfile.TemporaryDirectory(prefix="cloudy-stream-check-") as temporary:
        source = Path(temporary) / "source"
        source_tree(source, lock, archives, contents)
        if hashes(source) != receipt["sourceFiles"] or hashes(run / "source") != receipt["sourceFiles"]:
            raise ValueError("Retained source differs from exact archives plus reviewed patches")
    raw = read(prepared / "nativeLibraries/arm64-v8a" / LIBRARY)
    if sha(raw) != receipt["library"]["sha256"] or elf_info(raw) != receipt["library"]["elf"]:
        raise ValueError("Native ELF differs from receipt")
    if any(sha(read(run / name)) != value for name, value in receipt["evidence"].items()):
        raise ValueError("Retained compiler evidence differs from receipt")
    return receipt


def print_summary(run, receipt):
    print(json.dumps({"status": "passed", "scope": receipt["scope"], "buildRef": receipt["buildRef"],
                      "sourceFiles": len(receipt["sourceFiles"]), "library": receipt["library"], "receipt": str(run / "receipt.json")}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--build", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--run", required=True, help="Fresh cache run name for build; existing name for offline check")
    parser.add_argument("--sdk", type=Path, default=ROOT / ".cache/android-sdk")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", args.run):
        parser.error("Run name must be 1-64 lowercase letters/digits/hyphens")
    run = no_symlinks(CACHE / args.run)
    try:
        if args.build:
            build(args.sdk, run)
        else:
            print_summary(run, check(run))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError, tarfile.TarError) as failure:
        parser.exit(1, f"Native stream preparation failed: {failure}\n")


if __name__ == "__main__":
    main()
