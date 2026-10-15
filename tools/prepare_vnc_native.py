#!/usr/bin/env python3
"""Build pinned Android LibVNCClient TLS/JPEG closure, or verify retained bytes offline.

This is a native feasibility entry: no JNI adapter, app, device or network probe.
OpenSSL reuses an exact accepted Android receipt; it is never rebuilt in place.
"""

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
PROBE = ROOT / "qualification/probes/native-vnc"
LOCK = PROBE / "sources.lock.json"
CACHE = ROOT / ".cache/native-vnc"
SHARED = ("tools/build_rdp_native.py", "tools/prepare_rdp_native.py")
LIBRARIES = {"libvncclient.so", "libssl.so", "libcrypto.so"}
SYSTEM = {"libc.so", "libdl.so", "libm.so", "libz.so"}


def safe(path):
    path = path.absolute()
    if any(item.is_symlink() for item in (path, *path.parents)):
        raise ValueError("Symlink path rejected")
    return path


def read(path):
    return safe(path).read_bytes()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def verified(raw, pin):
    if len(raw) != pin["bytes"] or digest(raw) != pin["sha256"]:
        raise ValueError("Pinned input byte count/hash mismatch")
    return raw


def relative(name):
    path = PurePosixPath(name)
    if not name or path.is_absolute() or ".." in path.parts or "\\" in name:
        raise ValueError("Unsafe relative path")
    return path


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def hashes(directory):
    records = {}
    for path in sorted(safe(directory).rglob("*")):
        safe(path)
        if path.is_file():
            records[path.relative_to(directory).as_posix()] = digest(read(path))
        elif not path.is_dir():
            raise ValueError("Unexpected file type")
    return records


def snapshot():
    paths = [LOCK, Path(__file__).resolve(), *(ROOT / name for name in SHARED)]
    paths.extend(sorted((PROBE / "patches").glob("*.patch")))
    contents = {str(path.relative_to(ROOT)): read(path) for path in paths}
    lock = json.loads(contents[str(LOCK.relative_to(ROOT))])
    declared = [item["path"] for item in lock["patches"]]
    actual = {str(path.relative_to(PROBE)) for path in paths if path.suffix == ".patch"}
    if len(set(declared)) != len(declared) or set(declared) != actual:
        raise ValueError("Patch set differs from lock")
    for item in lock["patches"]:
        relative(item["path"])
        verified(contents[str((PROBE / item["path"]).relative_to(ROOT))], item)
    for pin in (lock["source"], lock["jpeg"], lock["openssl"]["source"]):
        if len(relative(pin["archive"]).parts) != 1:
            raise ValueError("Archive must be a filename")
    return lock, {name: digest(raw) for name, raw in contents.items()}, contents


def unchanged(expected):
    if snapshot()[1] != expected:
        raise ValueError("Authored inputs changed; publication rejected")


def acquire(pin):
    directory = safe(CACHE / "downloads")
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / pin["archive"]
    if path.exists():
        return verified(read(path), pin)
    hints = (ROOT / ".cache/vnc-qualification" / pin["archive"],
             ROOT / ".cache/native-rdp/downloads" / pin["archive"])
    raw = None
    for hint in hints:
        if hint.exists():
            raw = verified(read(hint), pin)
            break
    if raw is None:
        if not pin["url"].startswith("https://"):
            raise ValueError("Source download requires HTTPS")
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


def extract(raw, pin, destination):
    """Pinned archive only; do not delegate link/path handling to extractall."""
    verified(raw, pin)
    destination.mkdir(parents=True, exist_ok=False)
    seen = set()
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as bundle:
        for member in bundle:
            path = relative(member.name)
            if path in seen or path.parts[0] != pin["root"]:
                raise ValueError("Archive root/duplicate differs")
            seen.add(path)
            if not (member.isdir() or member.isfile()):
                raise ValueError("Archive link/special file rejected")
            if len(path.parts) == 1:
                continue
            target = safe(destination.joinpath(*path.parts[1:]))
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(member) as stream:
                    with target.open("xb") as output:
                        shutil.copyfileobj(stream, output)


def source_tree(raw, lock, contents, destination):
    extract(raw, lock["source"], destination)
    for patch in lock["patches"]:
        replay_patch(destination, contents[str((PROBE / patch["path"]).relative_to(ROOT))])


def validate_import(lock, receipt_raw, files):
    pin = lock["openssl"]
    verified(receipt_raw, pin["receipt"])
    receipt = json.loads(receipt_raw)
    if receipt["exitCode"] != 0:
        raise ValueError("OpenSSL reuse receipt was not successful")
    expected = {f"lib/{name}": item for name, item in pin["libraries"].items()}
    expected.update({f"include/{name}": item for name, item in pin["headers"].items()})
    if set(files) != set(expected):
        raise ValueError("OpenSSL import closure differs")
    for name, item in expected.items():
        verified(files[name], item)
    for name, item in pin["libraries"].items():
        if receipt["libraries"][name] != item["sha256"]:
            raise ValueError("OpenSSL library differs from accepted receipt")


def import_openssl(lock, prefix, receipt_path, destination):
    pin = lock["openssl"]
    receipt_raw = read(receipt_path)
    names = [f"lib/{name}" for name in pin["libraries"]]
    names += [f"include/{name}" for name in pin["headers"]]
    files = {name: read(prefix / relative(name)) for name in names}
    validate_import(lock, receipt_raw, files)
    destination.mkdir()
    for name, raw in files.items():
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    (destination / "accepted-receipt.json").write_bytes(receipt_raw)


def bounded(command, run, name, limits):
    start = time.monotonic()
    log = run / f"{name}.log"
    with log.open("x") as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True,
                                   env=os.environ | {"MAKEFLAGS": "", "MFLAGS": ""})
        try:
            while process.poll() is None:
                if time.monotonic() - start > limits["timeoutSeconds"]:
                    raise subprocess.TimeoutExpired(command, limits["timeoutSeconds"])
                if log.stat().st_size > limits["maximumLogBytes"]:
                    raise ValueError("Compiler log limit exceeded")
                time.sleep(0.1)
            if log.stat().st_size > limits["maximumLogBytes"]:
                raise ValueError("Compiler log limit exceeded")
            if process.returncode:
                raise subprocess.CalledProcessError(process.returncode, command)
        except BaseException:
            stop_process_group(process)
            save(run / f"{name}.json", {"command": command, "exitCode": process.returncode,
                                      "incomplete": True})
            raise
    save(run / f"{name}.json", {"command": command, "exitCode": 0,
                              "durationSeconds": time.monotonic() - start})


def native_info(files):
    if set(files) != LIBRARIES:
        raise ValueError("Unexpected native library set")
    infos = {name: elf_info(raw) for name, raw in files.items()}
    for name, info in infos.items():
        if info["soname"] != name or not set(info["needed"]) <= SYSTEM | LIBRARIES:
            raise ValueError("Native SONAME/dependency differs")
    if not {"libssl.so", "libcrypto.so", "libz.so"} <= set(infos["libvncclient.so"]["needed"]):
        raise ValueError("Actual VNC native closure lacks required TLS/zlib")
    return infos


def compile_contract(build):
    commands = json.loads(read(build / "compile_commands.json"))
    files = {Path(item["file"]).name for item in commands}
    if not {"tls_openssl.c", "crypto_openssl.c", "minilzo.c", "rfbclient.c"} <= files or "tls_none.c" in files:
        raise ValueError("Actual compilation graph lacks selected TLS/compression paths")
    config = read(build / "include/rfb/rfbconfig.h").decode()
    for macro in ("LIBVNCSERVER_HAVE_LIBSSL", "LIBVNCSERVER_HAVE_LIBZ", "LIBVNCSERVER_HAVE_LIBJPEG"):
        if not re.search(rf"^#define\s+{macro}\s+1\s*$", config, re.MULTILINE):
            raise ValueError(f"Selected feature missing: {macro}")


def check(run, receipt=None):
    if receipt is None:
        receipt = json.loads(read(run / "receipt.json"))
    lock, inputs, contents = snapshot()
    if receipt["exitCode"] != 0 or receipt["inputs"] != inputs or receipt["buildRef"] != lock["buildRef"]:
        raise ValueError("Successful receipt inputs differ")
    prepared = run / "prepared"
    if hashes(prepared) != receipt["prepared"]:
        raise ValueError("Prepared output differs")
    notices = prepared / "nativeNotices"
    with tempfile.TemporaryDirectory(prefix="cloudy-vnc-check-") as temporary:
        source = Path(temporary) / "source"
        source_tree(read(notices / lock["source"]["archive"]), lock, contents, source)
        if hashes(source) != receipt["sourceFiles"] or hashes(run / "source") != receipt["sourceFiles"]:
            raise ValueError("Retained VNC source differs from pinned source/patches")
        jpeg = Path(temporary) / "jpeg"
        extract(read(notices / lock["jpeg"]["archive"]), lock["jpeg"], jpeg)
        if hashes(jpeg) != receipt["jpegSourceFiles"]:
            raise ValueError("JPEG source differs")
    verified(read(notices / lock["openssl"]["source"]["archive"]), lock["openssl"]["source"])
    imported = notices / "openssl-import"
    files = {name: read(imported / name) for name in hashes(imported) if name != "accepted-receipt.json"}
    validate_import(lock, read(imported / "accepted-receipt.json"), files)
    raw = {name: read(prepared / "nativeLibraries/arm64-v8a" / name) for name in LIBRARIES}
    if native_info(raw) != receipt["elf"]:
        raise ValueError("ELF differs from receipt")
    if any(digest(read(run / name)) != value for name, value in receipt["evidence"].items()):
        raise ValueError("Retained compiler evidence differs")
    return receipt


def build(args, run):
    lock, inputs, contents = snapshot()
    limits = lock["toolchain"]
    run.mkdir(parents=True, exist_ok=False)
    try:
        save(run / "inputs.json", inputs)
        for name, data in contents.items():
            target = run / "input-snapshot" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        pins = (lock["source"], lock["jpeg"], lock["openssl"]["source"])
        archives = {pin["archive"]: acquire(pin) for pin in pins}
        ndk = args.sdk.resolve(strict=True) / "ndk" / limits["ndk"]
        if f"Pkg.Revision = {limits['ndk']}" not in read(ndk / "source.properties").decode():
            raise ValueError("NDK version mismatch")
        cmake_dir = args.sdk.resolve(strict=True) / "cmake" / limits["cmake"] / "bin"
        cmake = str(cmake_dir / "cmake")
        toolchain = ndk / "build/cmake/android.toolchain.cmake"
        sysroot = ndk / "toolchains/llvm/prebuilt/linux-x86_64/sysroot"
        tools = {"ndk.properties": digest(read(ndk / "source.properties")),
                 "cmake": digest(read(cmake_dir / "cmake")), "ninja": digest(read(cmake_dir / "ninja")),
                 "clang": digest(read((ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/clang").resolve(strict=True))),
                 "zlib.h": digest(read(sysroot / "usr/include/zlib.h")),
                 "zconf.h": digest(read(sysroot / "usr/include/zconf.h")),
                 "libz.so": digest(read(sysroot / f"usr/lib/aarch64-linux-android/{limits['androidPlatform']}/libz.so"))}
        with tempfile.TemporaryDirectory(prefix="cloudy-vnc-android-") as temporary:
            stage = Path(temporary)
            source, jpeg, ssl = stage / "source", stage / "jpeg", stage / "ssl"
            source_tree(archives[lock["source"]["archive"]], lock, contents, source)
            extract(archives[lock["jpeg"]["archive"]], lock["jpeg"], jpeg)
            source_hashes, jpeg_hashes = hashes(source), hashes(jpeg)
            import_openssl(lock, args.openssl_prefix, args.openssl_receipt, ssl)
            common = ["-G", "Ninja", f"-DCMAKE_MAKE_PROGRAM={cmake_dir / 'ninja'}",
                      f"-DCMAKE_TOOLCHAIN_FILE={toolchain}", "-DANDROID_ABI=arm64-v8a",
                      "-DANDROID_PLATFORM=android-29", "-DCMAKE_BUILD_TYPE=RelWithDebInfo",
                      "-DCMAKE_POSITION_INDEPENDENT_CODE=ON", "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON"]
            jb, vb = stage / "jpeg-build", stage / "vnc-build"
            bounded([cmake, "-S", str(jpeg), "-B", str(jb), *common, "-DENABLE_SHARED=OFF",
                     "-DENABLE_STATIC=ON", "-DWITH_TURBOJPEG=OFF", "-DWITH_SIMD=ON"], run, "jpeg-configure", limits)
            bounded([cmake, "--build", str(jb), "--target", "jpeg-static", "--parallel", "4"], run, "jpeg-build", limits)
            # Generated jconfig.h and source headers form one private include tree.
            include = stage / "jpeg-include"
            include.mkdir()
            for name in ("jpeglib.h", "jmorecfg.h", "jerror.h"):
                shutil.copyfile(jpeg / "src" / name, include / name)
            shutil.copyfile(jb / "jconfig.h", include / "jconfig.h")
            options = ["-DBUILD_SHARED_LIBS=ON", "-DWITH_LIBVNCCLIENT=ON", "-DWITH_LIBVNCSERVER=OFF",
                       "-DWITH_OPENSSL=ON", "-DWITH_ZLIB=ON", "-DWITH_JPEG=ON", "-DWITH_LZO=OFF",
                       f"-DOPENSSL_INCLUDE_DIR={ssl / 'include'}", f"-DOPENSSL_SSL_LIBRARY={ssl / 'lib/libssl.so'}",
                       f"-DOPENSSL_CRYPTO_LIBRARY={ssl / 'lib/libcrypto.so'}", f"-DJPEG_INCLUDE_DIR={include}",
                       f"-DJPEG_LIBRARY={jb / 'libjpeg.a'}", "-DCMAKE_C_FLAGS=-DLZO_CFG_NO_UNALIGNED=1",
                       "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"]
            options += [f"-DWITH_{name}=OFF" for name in ("PNG", "SDL", "GTK", "QT", "XCB", "LIBSSHTUNNEL",
                        "GNUTLS", "GCRYPT", "SYSTEMD", "SASL", "FFMPEG", "EXAMPLES", "TESTS", "WEBSOCKETS", "TIGHTVNC_FILETRANSFER")]
            bounded([cmake, "-S", str(source), "-B", str(vb), *common, *options], run, "vnc-configure", limits)
            compile_contract(vb)
            bounded([cmake, "--build", str(vb), "--target", "vncclient", "--parallel", "4"], run, "vnc-build", limits)
            unchanged(inputs)
            if hashes(source) != source_hashes or hashes(jpeg) != jpeg_hashes:
                raise ValueError("Compiler changed pinned sources")
            raw = {"libvncclient.so": read(vb / "libvncclient.so")}
            raw.update({name: read(ssl / "lib" / name) for name in lock["openssl"]["libraries"]})
            infos = native_info(raw)
            prepared = run / "prepared"
            libs, notices = prepared / "nativeLibraries/arm64-v8a", prepared / "nativeNotices"
            libs.mkdir(parents=True)
            notices.mkdir()
            for name, data in raw.items():
                (libs / name).write_bytes(data)
            for name, data in archives.items():
                (notices / name).write_bytes(data)
            shutil.copytree(ssl, notices / "openssl-import")
            for name, data in contents.items():
                path = notices / "build-inputs" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            shutil.copyfile(source / "COPYING", notices / "LibVNC-GPL2.txt")
            for name in ("LICENSE.md", "README.ijg"):
                shutil.copyfile(jpeg / name, notices / f"libjpeg-turbo-{name}")
            shutil.copyfile(jb / "libjpeg.a", notices / "libjpeg-android-pic.a")
            shutil.copyfile(vb / "include/rfb/rfbconfig.h", notices / "vnc-rfbconfig.h")
            shutil.copyfile(vb / "compile_commands.json", run / "vnc-compile-commands.json")
            with tarfile.open(fileobj=io.BytesIO(archives[lock["openssl"]["source"]["archive"]])) as bundle:
                (notices / "OpenSSL-LICENSE.txt").write_bytes(bundle.extractfile("openssl-4.0.1/LICENSE.txt").read())
            (notices / "NOTICE.txt").write_text(
                "Native Android build entry only; no APK/JNI runtime, TLS trust, real-server or distribution approval.\n"
                "LibVNCClient GPL-2.0-or-later; bundled miniLZO GPL-2.0-or-later. Full pinned source retained.\n"
                "JPEG3.2.0 static PIC source/licenses, OpenSSL4.0.1 source/license and exact reused binary/header receipt retained.\n"
                "Android OS-owned libz.so is dynamically used; NDK stub/header hashes are build inputs, not device zlib identity.\n"
                "Run tools/prepare_vnc_native.py with the pinned NDK/CMake and exact accepted OpenSSL import.\n"
                "TLS code is compiled; the future adapter must restrict security negotiation and validate server identity fail-closed.\n")
            shutil.copytree(source, run / "source")
            evidence_names = [f"{name}.{suffix}" for name in ("jpeg-configure", "jpeg-build", "vnc-configure", "vnc-build")
                              for suffix in ("json", "log")] + ["vnc-compile-commands.json"]
            evidence_names += ["inputs.json"]
            receipt = {"schemaVersion": 1, "exitCode": 0, "scope": "Android-native-TLS-JPEG-build-only",
                       "buildRef": lock["buildRef"], "inputs": inputs, "toolchain": tools,
                       "sourceFiles": source_hashes, "jpegSourceFiles": jpeg_hashes,
                       "prepared": hashes(prepared), "elf": infos,
                       "libraries": {name: {"sha256": digest(data), "bytes": len(data)} for name, data in raw.items()},
                       "evidence": {name: digest(read(run / name)) for name in evidence_names}}
            check(run, receipt)
            unchanged(inputs)
            save(run / "receipt.json", receipt)
        return receipt
    except BaseException as failure:
        save(run / "failure.json", {"scope": "incomplete; no accepted receipt", "class": type(failure).__name__, "detail": str(failure)})
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--build", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--run", required=True)
    parser.add_argument("--sdk", type=Path, default=ROOT / ".cache/android-sdk")
    parser.add_argument("--openssl-prefix", type=Path)
    parser.add_argument("--openssl-receipt", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", args.run):
        parser.error("Run must be a bounded lowercase filename")
    if args.build and (args.openssl_prefix is None or args.openssl_receipt is None):
        parser.error("Build requires exact --openssl-prefix and --openssl-receipt")
    run = safe(CACHE / args.run)
    try:
        receipt = build(args, run) if args.build else check(run)
        print(json.dumps({"status": "passed", "scope": receipt["scope"], "libraries": receipt["libraries"],
                          "elf": receipt["elf"], "receipt": str(run / "receipt.json")}, indent=2))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError, tarfile.TarError) as error:
        parser.exit(1, f"VNC native preparation failed: {error}\n")


if __name__ == "__main__":
    main()
