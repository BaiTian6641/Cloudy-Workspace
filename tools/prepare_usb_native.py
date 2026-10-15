#!/usr/bin/env python3
"""Build pinned upstream libusb plus descriptor-only JNI, or verify offline inputs."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tarfile
import tempfile
import urllib.request

from build_rdp_native import stop_process_group

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "qualification/probes/native-usb"
CACHE = ROOT / ".cache/native-usb"
GENERATED = PROBE / "app/build/generated"
LOCK = PROBE / "source-lock.json"
LIBRARIES = {"libusb-1.0.so", "libcloudyusb.so"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def regular(path):
    for part in (path, *path.parents):
        if part.is_symlink():
            raise ValueError(f"Symlink input rejected: {part}")
    if not path.is_file():
        raise ValueError(f"Missing regular file: {path}")
    return path


def inputs():
    paths = [LOCK, Path(__file__).resolve(), ROOT / "tools/build_rdp_native.py", ROOT / "tools/prepare_rdp_native.py"]
    for directory in (PROBE / "native", PROBE / "app/src/main"):
        paths.extend(path for path in directory.rglob("*") if path.is_file())
    paths.extend(PROBE / name for name in ("app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties"))
    return {str(path.relative_to(ROOT)): digest(regular(path)) for path in sorted(paths)}


def snapshot_inputs():
    records = inputs()
    contents = {name: regular(ROOT / name).read_bytes() for name in records}
    if any(hashlib.sha256(raw).hexdigest() != records[name] for name, raw in contents.items()):
        raise ValueError("Inputs changed while taking build snapshot")
    return records, contents


def require_unchanged(records):
    if inputs() != records:
        raise ValueError("Inputs changed during native build; refusing publication")


def run_build(command, log_path, timeout=180):
    # Isolate descendants; a killed Make leader does not imply stopped compilers.
    with log_path.open("w") as output:
        process = subprocess.Popen(command, text=True, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            result = process.wait(timeout=timeout)
        except BaseException:
            stop_process_group(process)
            raise
        if result:
            stop_process_group(process)
            raise ValueError(f"ndk-build failed ({result}); see {log_path}")


def verify_archive(path, pin):
    regular(path)
    if path.stat().st_size != pin["bytes"] or digest(path) != pin["sha256"]:
        raise ValueError("libusb archive differs from tracked source pin")


def acquire(pin):
    directory = CACHE / "downloads"
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / pin["archive"]
    if not destination.exists():
        with tempfile.NamedTemporaryFile(dir=directory, delete=False) as stream:
            temporary = Path(stream.name)
            try:
                with urllib.request.urlopen(pin["url"], timeout=90) as response:
                    raw = response.read(pin["bytes"] + 1)
                    stream.write(raw)
                stream.close()
                verify_archive(temporary, pin)
                temporary.replace(destination)
            finally:
                temporary.unlink(missing_ok=True)
    verify_archive(destination, pin)
    return destination


def elf_check(path):
    raw = regular(path).read_bytes()
    if raw[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError("Expected little-endian ELF64")
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", raw)
    if header[1] != 3 or header[2] != 183 or header[9] != 56:
        raise ValueError("Expected AArch64 shared library")
    load = [struct.unpack_from("<IIQQQQQQ", raw, header[5] + i * 56)
            for i in range(header[10])]
    if not any(item[0] == 1 for item in load):
        raise ValueError("No ELF load segments")
    if any(item[7] < 16384 or (item[2] - item[3]) % 16384 for item in load if item[0] == 1):
        raise ValueError("ELF is not aligned for 16KiB pages")
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest(), "machine": "AArch64", "loadAlignment": 16384}


def generated_paths():
    return [path for name in ("nativeLibraries", "nativeNotices")
            for path in (GENERATED / name).rglob("*") if path.is_file()]


def check():
    receipt = json.loads(regular(CACHE / "receipt.json").read_text())
    if receipt["inputs"] != inputs():
        raise ValueError("Native build receipt inputs changed; rebuild required")
    generated = {str(path.relative_to(GENERATED)): digest(regular(path))
                 for path in generated_paths()}
    if generated != receipt["generated"]:
        raise ValueError("Generated native libraries/notices differ from receipt")
    for name in LIBRARIES:
        elf_check(GENERATED / "nativeLibraries/arm64-v8a" / name)
    print(json.dumps({"status": "passed", "scope": "offline-native-input-integrity", "receipt": str(CACHE / "receipt.json")}))


def build(sdk):
    records, contents = snapshot_inputs()
    lock = json.loads(contents[str(LOCK.relative_to(ROOT))])
    archive = acquire(lock["source"])
    # ndk-build cannot handle project paths containing spaces. A temporary alias
    # is confined to this build; SDK contents and user configuration stay unchanged.
    sdk = sdk.resolve(strict=True)
    ndk = sdk / "ndk" / lock["toolchain"]["ndk"]
    properties = regular(ndk / "source.properties").read_text()
    if f"Pkg.Revision = {lock['toolchain']['ndk']}" not in properties:
        raise ValueError("NDK identity differs from lock")
    with tempfile.TemporaryDirectory(prefix="cloudy-usb-") as temporary:
        stage = Path(temporary)
        with tarfile.open(archive, "r:bz2") as bundle:
            for member in bundle.getmembers():
                parts = Path(member.name).parts
                if member.name.startswith("/") or ".." in parts or member.issym() or member.islnk():
                    raise ValueError("Unsafe source archive member")
            bundle.extractall(stage, filter="data")
        source = stage / "libusb-1.0.30"
        (stage / "ndk").symlink_to(ndk, target_is_directory=True)
        (stage / "usb_probe.c").write_bytes(contents[str((PROBE / "native/usb_probe.c").relative_to(ROOT))])
        wrapper = stage / "Android.mk"
        wrapper.write_text(f'''include {source}/android/jni/libusb.mk
LOCAL_PATH := {stage}
include $(CLEAR_VARS)
LOCAL_MODULE := cloudyusb
LOCAL_SRC_FILES := usb_probe.c
LOCAL_C_INCLUDES := {source}/libusb
LOCAL_SHARED_LIBRARIES := usb-1.0
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -Werror -Wconversion -Wformat=2 -Wstrict-prototypes -Wmissing-prototypes -fvisibility=hidden
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
''')
        command = ["make", "-O", "-f", str(stage / "ndk/build/core/build-local.mk"), f"NDK_PROJECT_PATH={stage}", f"APP_BUILD_SCRIPT={wrapper}",
                   "APP_ABI=arm64-v8a", "APP_PLATFORM=android-35", "APP_OPTIM=release", "USE_PC_NAME=1",
                   "APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true", "APP_LDFLAGS=-Wl,-z,max-page-size=16384", "-j4"]
        CACHE.mkdir(parents=True, exist_ok=True)
        run_build(command, CACHE / "build.log")
        require_unchanged(records)
        generated = stage / "generated"
        libraries = generated / "nativeLibraries/arm64-v8a"
        notices = generated / "nativeNotices"
        libraries.mkdir(parents=True)
        notices.mkdir(parents=True)
        metadata = {}
        tool = stage / "ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
        for name in sorted(LIBRARIES):
            path = stage / "libs/arm64-v8a" / name
            metadata[name] = elf_check(path)
            dynamic = subprocess.run([str(tool / "llvm-readelf"), "-d", str(path)], capture_output=True, text=True, check=True).stdout
            needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", dynamic)
            if not set(needed) <= {"libc.so", "libdl.so", "liblog.so", "libm.so", "libusb-1.0.so"}:
                raise ValueError(f"Unexpected native dependency: {needed}")
            metadata[name]["needed"] = needed
            shutil.copyfile(path, libraries / name)
        symbols = subprocess.run([str(tool / "llvm-nm"), "-D", "--defined-only", str(libraries / "libcloudyusb.so")], capture_output=True, text=True, check=True).stdout
        expected = {f"Java_dev_cloudy_qualification_usb_NativeUsb_{name}" for name in ("version", "open", "describe", "close")}
        exported = {line.split()[-1] for line in symbols.splitlines() if line.strip()}
        if not expected <= exported:
            raise ValueError("JNI boundary symbols missing")
        shutil.copyfile(archive, notices / lock["source"]["archive"])
        shutil.copyfile(source / "COPYING", notices / "libusb-COPYING.txt")
        (notices / "cloudy-usb-probe.c").write_bytes(contents[str((PROBE / "native/usb_probe.c").relative_to(ROOT))])
        (notices / "Android-build-recipe.mk").write_text(wrapper.read_text().replace(str(source), "$(LIBUSB_SOURCE)").replace(str(stage), "$(CLOUDY_SOURCE)"))
        (notices / "prepare_usb_native.py").write_bytes(contents[str(Path(__file__).resolve().relative_to(ROOT))])
        (notices / "build_rdp_native.py").write_bytes(contents["tools/build_rdp_native.py"])
        (notices / "prepare_rdp_native.py").write_bytes(contents["tools/prepare_rdp_native.py"])
        (notices / "source-lock.json").write_bytes(contents[str(LOCK.relative_to(ROOT))])
        (notices / "NOTICE.txt").write_text("libusb 1.0.30: LGPL-2.1-or-later. Copyright contributors, including Johannes Erdfelt and libusb authors. Full unmodified corresponding source archive and COPYING are included alongside Cloudy's JNI source and build recipe. Cloudy descriptor diagnostic does not claim USB forwarding or hardware qualification.\n")
        (notices / "native-manifest.json").write_text(json.dumps({"buildRef": lock["buildRef"], "libraries": metadata, "inputs": records}, indent=2) + "\n")
        require_unchanged(records)
        GENERATED.mkdir(parents=True, exist_ok=True)
        for name in ("nativeLibraries", "nativeNotices"):
            target = GENERATED / name
            if target.exists():
                shutil.rmtree(target)
            shutil.copytree(generated / name, target)
        receipt = {"schemaVersion": 1, "buildRef": lock["buildRef"], "inputs": records, "libraries": metadata,
                   "generated": {str(path.relative_to(GENERATED)): digest(path) for path in generated_paths()}}
        (CACHE / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    check()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--build", action="store_true")
    parser.add_argument("--sdk", type=Path, default=ROOT / ".cache/android-sdk")
    args = parser.parse_args()
    try:
        if args.check:
            check()
        else:
            build(args.sdk)
    except (OSError, ValueError, subprocess.SubprocessError, tarfile.TarError) as error:
        parser.exit(1, f"USB native preparation failed: {error}\n")


if __name__ == "__main__":
    main()
