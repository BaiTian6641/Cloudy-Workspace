#!/usr/bin/env python3
"""Import verified native RDP artifacts and corresponding notices; never build/download."""

import argparse
import hashlib
import io
import json
import re
import shutil
import struct
import sys
import tarfile
import tempfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "qualification/probes/native-rdp"
LOCK = PROBE / "source-lock.json"
GENERATED = PROBE / "app/build/generated"
LIBRARIES = GENERATED / "nativeLibraries/arm64-v8a"
NOTICES = GENERATED / "nativeNotices"
MANIFEST = NOTICES / "native-manifest.json"
CPP = "client/Android/Studio/freeRDPCore/src/main/cpp"
LIBS = {
    "libfreerdp-android.so", "libfreerdp3.so", "libfreerdp-client3.so",
    "libwinpr3.so", "libssl.so", "libcrypto.so", "libcjson.so", "liburiparser.so",
}
SYSTEM = {
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so",
    "libjnigraphics.so", "libmediandk.so", "libz.so", "libOpenSLES.so",
    "libcamera2ndk.so", "libaaudio.so",
}
MPL_SHA256 = "3f3d9e0024b1921b067d6f7f88deb4a60cbe7a78e76c64e3f1d7fc3b779b9d04"
JNI_BOUNDARY_EXPORTS = {
    "Java_com_freerdp_freerdpcore_services_LibFreeRDP_freerdp_1is_1relative_1mouse_1supported",
    "Java_com_freerdp_freerdpcore_services_LibFreeRDP_freerdp_1send_1relative_1cursor_1event",
    "Java_com_freerdp_freerdpcore_services_LibFreeRDP_freerdp_1join",
    "Java_com_freerdp_freerdpcore_services_LibFreeRDP_freerdp_1set_1external_1certificate_1management",
    "Java_com_freerdp_freerdpcore_services_LibFreeRDP_freerdp_1send_1key_1event",
}
PATCH_ORDER = (
    "bounded-openssl-jobs.patch", "android-session-ownership.patch",
    "android-external-certificate.patch", "android-key-validation.patch",
    "android-relative-input.patch",
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def no_symlinks(path):
    """Never follow symlinks in an input or generated destination path."""
    path = path.absolute()
    for item in (path, *path.parents):
        if item.is_symlink():
            raise ValueError(f"Symlink path rejected: {item}")
    return path


def read_file(path):
    path = no_symlinks(Path(path))
    if not path.is_file():
        raise ValueError(f"Missing regular file: {path}")
    return path.read_bytes()


def safe_relative(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError("Unsafe archive or manifest path")
    return path


def current_inputs():
    data = read_file(LOCK)
    lock = json.loads(data)
    patches = PROBE / "patches"
    found = {p.name for p in patches.glob("*.patch")}
    if found != set(PATCH_ORDER):
        raise ValueError("Patch directory differs from reviewed four-patch set")
    records = []
    for name in PATCH_ORDER:
        raw = read_file(patches / name)
        records.append({"path": f"patches/{name}", "bytes": len(raw), "sha256": digest(raw)})
    # Support the original single-patch lock as well as the leader's full patch array.
    declared = lock.get("patches", [lock["patch"]] if "patch" in lock else [])
    for item in declared:
        actual = next((p for p in records if p["path"] == item["path"]), None)
        if actual is None or any(actual.get(k) != item[k] for k in ("sha256", "bytes") if k in item):
            raise ValueError("Tracked patch does not match source lock")
    return lock, {"sourceLockSha256": digest(data), "patches": records}


def replay_patch(root, raw):
    """Apply only exact unified hunks, without shell execution or fuzz matching."""
    lines = raw.decode("utf-8").splitlines(keepends=True)
    index = 0
    while index < len(lines):
        if not lines[index].startswith("--- a/"):
            index += 1
            continue
        old = lines[index][6:].rstrip("\n").split("\t", 1)[0]
        index += 1
        if index >= len(lines) or not lines[index].startswith("+++ b/"):
            raise ValueError("Unsupported patch file header")
        new = lines[index][6:].rstrip("\n").split("\t", 1)[0]
        if old != new:
            raise ValueError("Only modifications to original files are supported")
        target = root.joinpath(*safe_relative(old).parts)
        original = read_file(target).decode("utf-8").splitlines(keepends=True)
        output, cursor = [], 0
        index += 1
        while index < len(lines) and not lines[index].startswith("--- a/"):
            match = re.match(r"@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@", lines[index])
            if not match:
                if lines[index].startswith(("diff ", "index ")):
                    index += 1
                    continue
                raise ValueError("Unsupported patch hunk")
            start = int(match[1]) - 1
            old_count = int(match[2] or 1)
            new_count = int(match[4] or 1)
            if start < cursor:
                raise ValueError("Overlapping patch hunks")
            output.extend(original[cursor:start])
            cursor = start
            removed = added = 0
            index += 1
            while index < len(lines) and not lines[index].startswith(("@@", "--- a/", "diff ")):
                line = lines[index]
                # git-generated project patches omit the prefix on empty context lines.
                if line == "\n":
                    line = " \n"
                if line.startswith("\\ No newline"):
                    raise ValueError("No-newline patch markers are unsupported")
                if line[0] not in " +-":
                    raise ValueError("Invalid unified patch line")
                if line[0] in " -":
                    if cursor >= len(original) or original[cursor] != line[1:]:
                        raise ValueError(f"Patch context mismatch: {old}")
                    cursor += 1
                    removed += 1
                if line[0] in " +":
                    output.append(line[1:])
                    added += 1
                index += 1
            if (removed, added) != (old_count, new_count):
                raise ValueError("Patch line counts differ from hunk header")
        output.extend(original[cursor:])
        target.write_text("".join(output), encoding="utf-8", newline="")


def verify_source(source, archive, lock):
    raw = read_file(archive)
    pin = lock["source"]
    if len(raw) != pin["bytes"] or digest(raw) != pin["sha256"]:
        raise ValueError("Source archive differs from tracked pin")
    originals = []
    with tempfile.TemporaryDirectory(prefix="cloudy-rdp-verify-") as temp:
        pristine = Path(temp)
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:*") as bundle:
            members = bundle.getmembers()
            if "members" in pin and len(members) != pin["members"]:
                raise ValueError("Archive member count differs from source lock")
            prefixes = {safe_relative(m.name).parts[0] for m in members}
            if len(prefixes) != 1:
                raise ValueError("Expected one source archive root")
            for member in members:
                parts = safe_relative(member.name).parts[1:]
                if not parts:
                    continue
                if member.isdir() or member.issym():
                    # Upstream symlink entries are never extracted or followed.
                    continue
                if not member.isfile():
                    raise ValueError("Unsupported archive member type")
                relative = Path(*parts)
                target = pristine / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(member) as handle:
                    target.write_bytes(handle.read())
                originals.append(relative)
        for name in PATCH_ORDER:
            replay_patch(pristine, read_file(PROBE / "patches" / name))
        for relative in originals:
            if read_file(source / relative) != read_file(pristine / relative):
                raise ValueError(f"Unreviewed source change: {relative}")
    return len(originals)


def elf_info(raw):
    """Read ELF64 program/dynamic headers without invoking a host binary loader."""
    if raw[:7] != b"\x7fELF\x02\x01\x01" or len(raw) < 64:
        raise ValueError("Expected little-endian ELF64")
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", raw)
    if header[1] != 3 or header[2] != 183:
        raise ValueError("Expected AArch64 shared library")
    offset, entry_size, count = header[5], header[9], header[10]
    if entry_size != 56 or not count or count > 1024:
        raise ValueError("Invalid ELF program header table")
    loads, dynamic = [], None
    for index in range(count):
        entry = struct.unpack_from("<IIQQQQQQ", raw, offset + index * entry_size)
        kind, _, file_offset, address, _, size, _, alignment = entry
        if file_offset + size > len(raw):
            raise ValueError("ELF segment extends beyond file")
        if kind == 1:
            if alignment < 16384 or alignment & (alignment - 1) or (address - file_offset) % 16384:
                raise ValueError("PT_LOAD does not support 16 KiB pages")
            loads.append((address, file_offset, size))
        if kind == 2:
            dynamic = (file_offset, size)
    if not loads or dynamic is None:
        raise ValueError("Missing ELF load or dynamic segments")
    values = {}
    for position in range(dynamic[0], dynamic[0] + dynamic[1], 16):
        tag, value = struct.unpack_from("<qQ", raw, position)
        if tag == 0:
            break
        values.setdefault(tag, []).append(value)
    if 15 in values or 29 in values:
        raise ValueError("ELF RPATH/RUNPATH rejected")
    string_address = values[5][0]
    string_size = values[10][0]
    strings = None
    for address, file_offset, size in loads:
        if address <= string_address and string_address + string_size <= address + size:
            start = file_offset + string_address - address
            strings = raw[start:start + string_size]
    if strings is None:
        raise ValueError("Invalid ELF dynamic string table")

    def name_at(index):
        end = strings.find(b"\0", index)
        if index >= len(strings) or end < 0:
            raise ValueError("Invalid ELF library name")
        name = strings[index:end].decode("ascii")
        if "/" in name:
            raise ValueError("ELF dependency contains path")
        return name

    needed = sorted(name_at(value) for value in values.get(1, []))
    unknown = set(needed) - LIBS - SYSTEM
    if unknown:
        raise ValueError(f"Unapproved ELF dependencies: {', '.join(sorted(unknown))}")
    sonames = values.get(14, [])
    return {"needed": needed, "soname": name_at(sonames[0]) if sonames else None,
            "machine": "AArch64", "minimumLoadAlignment": min(
                struct.unpack_from("<IIQQQQQQ", raw, offset + i * entry_size)[7]
                for i in range(count)
                if struct.unpack_from("<I", raw, offset + i * entry_size)[0] == 1)}


def jni_boundary_exports(raw):
    """Require the owned JNI boundary in the actual exported dynamic symbols."""
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", raw)
    offset, entry_size, count = header[6], header[11], header[12]
    if entry_size != 64 or not count or offset + entry_size * count > len(raw):
        raise ValueError("Invalid ELF section header table for JNI exports")
    sections = [struct.unpack_from("<IIQQQQIIQQ", raw, offset + i * entry_size)
                for i in range(count)]
    found = set()
    for section in sections:
        if section[1] != 11:  # SHT_DYNSYM, not the non-exported debug symbol table.
            continue
        symbol_offset, symbol_size, link, symbol_entry_size = section[4], section[5], section[6], section[9]
        if symbol_entry_size != 24 or symbol_size % 24 or symbol_offset + symbol_size > len(raw):
            raise ValueError("Invalid ELF dynamic symbol table")
        if link >= count:
            raise ValueError("Invalid ELF dynamic symbol string table link")
        strings_section = sections[link]
        start, size = strings_section[4], strings_section[5]
        if strings_section[1] != 3 or start + size > len(raw):
            raise ValueError("Invalid ELF dynamic symbol strings")
        strings = raw[start:start + size]
        for position in range(symbol_offset, symbol_offset + symbol_size, 24):
            name, info, visibility, defined, _, _ = struct.unpack_from("<IBBHQQ", raw, position)
            if defined == 0 or info >> 4 not in (1, 2) or visibility & 3 not in (0, 3):
                continue
            end = strings.find(b"\0", name)
            if name >= len(strings) or end < 0:
                raise ValueError("Invalid ELF exported symbol name")
            symbol = strings[name:end].decode("ascii")
            if symbol in JNI_BOUNDARY_EXPORTS:
                found.add(symbol)
    if found != JNI_BOUNDARY_EXPORTS:
        raise ValueError("Missing JNI boundary exports: " + ", ".join(sorted(JNI_BOUNDARY_EXPORTS - found)))
    return sorted(found)


def validate_receipt(raw, inputs, libraries):
    """An explicit leader-produced successful build receipt binds initial import.

    Keep this asset limited to sanitized identities; local build paths or command
    lines belong to private evidence, not this APK asset.
    """
    receipt = json.loads(raw)
    if not isinstance(receipt, dict) or set(receipt) - {"schemaVersion", "exitCode", "inputs", "libraries"}:
        raise ValueError("Build receipt must contain only sanitized identity fields")
    if receipt.get("schemaVersion", 1) != 1:
        raise ValueError("Unsupported build receipt schema")
    if type(receipt.get("exitCode")) is not int or receipt["exitCode"] != 0:
        raise ValueError("Build receipt does not record a successful build")
    if receipt.get("inputs") != inputs:
        raise ValueError("Build receipt inputs differ from current source lock/patches")
    if receipt.get("libraries") != libraries:
        raise ValueError("Build receipt hashes differ from actual eight libraries")
    return receipt


def notices(source, mpl):
    external = source / CPP / "external"
    inputs = {
        "FreeRDP-Apache-2.0.txt": source / "LICENSE",
        "MPL-2.0.txt": mpl,
        "OpenSSL-Apache-2.0.txt": external / "openssl/LICENSE.txt",
        "cJSON-MIT.txt": external / "cjson/LICENSE",
        "uriparser-library-BSD-3-Clause.txt": external / "uriparser/src/COPYING",
        "uriparser-headers-BSD-3-Clause.txt": external / "uriparser/include/uriparser/COPYING",
        "Android-cpufeatures-NOTICE.txt": source / "winpr/libwinpr/sysinfo/cpufeatures/NOTICE",
    }
    result = {name: read_file(path) for name, path in inputs.items()}
    if digest(result["MPL-2.0.txt"]) != MPL_SHA256:
        raise ValueError("Supply complete canonical MPL 2.0 text, not a stub or HTML page")
    if b"<html" in result["MPL-2.0.txt"].lower():
        raise ValueError("MPL license must be plain text")
    for algorithm in ("md4", "md5"):
        data = read_file(source / f"winpr/libwinpr/crypto/{algorithm}.c")
        end = data.index(b"*/") + 2
        result[f"WinPR-{algorithm}-NOTICE.txt"] = data[:end] + b"\n"
    return result


def source_bundle(source, lock, inputs):
    files = {}
    for path in sorted((source / CPP).glob("android*")):
        if path.suffix in {".c", ".h"}:
            files[str(path.relative_to(source))] = read_file(path)
    if not files:
        raise ValueError("No corresponding bridge source found")
    files["LICENSE-FreeRDP.txt"] = read_file(source / "LICENSE")
    files["source-lock.json"] = read_file(LOCK)
    for item in inputs["patches"]:
        files[item["path"]] = read_file(PROBE / item["path"])
    files["SOURCE-RECIPE.txt"] = (
        f"FreeRDP source {lock['source']['url']}\nArchive SHA256 {lock['source']['sha256']}\n"
        "Apply patches in this order:\n" + "\n".join(PATCH_ORDER)
        + "\nIncluded bridge files are the actual patched corresponding source.\n"
        "Keep per-file MPL-2.0/Apache-2.0 headers; MPL text accompanies this archive in APK assets.\n"
    ).encode()
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode="w", format=tarfile.USTAR_FORMAT) as bundle:
        for name, data in sorted(files.items()):
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime = len(data), 0o644, 0
            bundle.addfile(info, io.BytesIO(data))
    return stream.getvalue()


def tree_hashes(root):
    no_symlinks(root)
    records = {}
    for path in sorted(root.rglob("*")):
        no_symlinks(path)
        if path.is_dir():
            continue
        if path == MANIFEST:
            continue
        records[str(path.relative_to(root))] = digest(read_file(path))
    return records


def check_prepared(inputs):
    manifest = json.loads(read_file(MANIFEST))
    if manifest["inputs"] != inputs:
        raise ValueError("Prepared inputs differ from current source lock/patches")
    names = {p.name for p in LIBRARIES.iterdir() if p.is_file()}
    if names != LIBS:
        raise ValueError("Prepared native library set differs from exact eight libraries")
    actual = tree_hashes(LIBRARIES)
    if actual != manifest["libraries"] or tree_hashes(NOTICES) != manifest["notices"]:
        raise ValueError("Prepared native file set or hashes changed")
    receipt = read_file(NOTICES / "native-build-receipt.json")
    if digest(receipt) != manifest["buildReceiptSha256"]:
        raise ValueError("Prepared build receipt hash changed")
    validate_receipt(receipt, inputs, actual)
    exports = jni_boundary_exports(read_file(LIBRARIES / "libfreerdp-android.so"))
    if exports != manifest["jniBoundaryExports"]:
        raise ValueError("Prepared JNI boundary exports changed")
    for name in sorted(LIBS):
        if elf_info(read_file(LIBRARIES / name)) != manifest["elf"][name]:
            raise ValueError("Prepared ELF metadata changed")
    print("Native RDP prepared inputs verified: eight arm64 libraries, notices and corresponding source")


def build_identity(build, source, lock, *, require_inner=True):
    """Record actual build-cache/compiler identity without executing the toolchain."""
    records = {}
    caches = {}
    for label, path in (
        ("outer", build / "CMakeCache.txt"),
        ("inner", build / "freerdp-prefix/src/freerdp-build/CMakeCache.txt"),
    ):
        if label == "inner" and not require_inner:
            continue
        raw = read_file(path)
        records[label + "CacheSha256"] = digest(raw)
        values = {}
        for line in raw.decode().splitlines():
            match = re.match(r"([^:#/][^:]*):[^=]+=(.*)", line)
            if match:
                values[match[1]] = match[2]
        caches[label] = values
    for label, expected_source in (("outer", source / CPP), ("inner", source)):
        if label not in caches:
            continue
        actual_source = Path(caches[label]["CMAKE_HOME_DIRECTORY"]).resolve(strict=True)
        if actual_source != expected_source.resolve(strict=True):
            raise ValueError(f"{label.capitalize()} build source directory differs from supplied source")
    for option in lock["cmakeOptions"]:
        key, value = option.removeprefix("-D").split("=", 1)
        actual = caches["outer"].get(key)
        if actual != value:
            raise ValueError(f"Build cache differs from lock: {key}")
    if require_inner:
        inner = caches["inner"]
        if inner.get("WITH_MEDIACODEC") != "ON" or inner.get("WITH_OPENH264") != "OFF":
            raise ValueError("Inner build decoder options differ from selected configuration")
    toolchain = Path(caches["outer"]["CMAKE_TOOLCHAIN_FILE"]).resolve(strict=True)
    ndk = toolchain.parent.parent.parent
    properties = read_file(ndk / "source.properties")
    if lock["toolchain"]["ndk"].encode() not in properties:
        raise ValueError("Actual NDK version differs from source lock")
    records["ndkPropertiesSha256"] = digest(properties)
    records["toolchainFileSha256"] = digest(read_file(toolchain))
    compiler_files = sorted((build / "CMakeFiles").glob("*/CMakeCCompiler.cmake"))
    if len(compiler_files) != 1:
        raise ValueError("Expected exactly one configured C compiler identity")
    compiler = read_file(compiler_files[0])
    expected = lock["toolchain"]["clang"]
    if f'set(CMAKE_C_COMPILER_VERSION "{expected}")'.encode() not in compiler:
        raise ValueError("Configured Clang version differs from lock")
    records["compilerIdentitySha256"] = digest(compiler)
    cmake_version = ".".join(caches["outer"]["CMAKE_CACHE_" + part + "_VERSION"]
                             for part in ("MAJOR", "MINOR", "PATCH"))
    if cmake_version != lock["toolchain"]["cmake"]:
        raise ValueError("Actual CMake cache version differs from lock")
    records["cmakeVersion"] = cmake_version
    for label, key in (("ninja", "CMAKE_MAKE_PROGRAM"), ("cmake", "CMAKE_COMMAND")):
        executable = Path(caches["outer"][key]).resolve(strict=True)
        records[label + "ExecutableSha256"] = digest(read_file(executable))
    records["declaredVersions"] = lock["toolchain"]
    return records


def prepare(args, lock, inputs):
    source = no_symlinks(args.source)
    build = no_symlinks(args.build)
    original_count = verify_source(source, args.archive, lock)
    build_record = build_identity(build, source, lock)
    stage = source / "client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a"
    binaries = {name: read_file(build / name if name == "libfreerdp-android.so" else stage / name)
                for name in sorted(LIBS)}
    library_hashes = {name: digest(data) for name, data in binaries.items()}
    receipt = read_file(args.receipt)
    validate_receipt(receipt, inputs, library_hashes)
    elf = {name: elf_info(data) for name, data in binaries.items()}
    exports = jni_boundary_exports(binaries["libfreerdp-android.so"])
    for name, entry in elf.items():
        if entry["soname"] != name:
            raise ValueError(f"Unexpected ELF SONAME: {name}")
    assets = notices(source, args.mpl_license)
    assets["native-build-receipt.json"] = receipt
    assets["corresponding-bridge-source.tar"] = source_bundle(source, lock, inputs)
    assets["NATIVE-NOTICES.txt"] = (
        "Cloudy diagnostic native RDP components\n"
        "FreeRDP 3.32.2 / WinPR: Apache-2.0; selected Android bridge files: MPL-2.0.\n"
        "OpenSSL 4.0.1: Apache-2.0; cJSON 1.7.19: MIT; uriparser 1.0.2 library: BSD-3-Clause.\n"
        "Android cpufeatures and WinPR MD4/MD5 embedded notices accompany this file.\n"
        "Corresponding actual modified bridge source, headers, root license and patch recipe:\n"
        "corresponding-bridge-source.tar in these APK assets. Full MPL text: MPL-2.0.txt.\n"
        "No OpenH264, FFmpeg or GPL component is selected by this native import.\n"
    ).encode()
    # Stage completely before touching outputs; only these two generated directories are owned.
    for root in (LIBRARIES.parent, NOTICES):
        no_symlinks(root)
        if root.exists():
            tree_hashes(root)
    with tempfile.TemporaryDirectory(prefix="cloudy-rdp-import-") as temporary:
        prepared = Path(temporary)
        for directory, items in (("libraries", binaries), ("notices", assets)):
            target = prepared / directory
            target.mkdir()
            for name, data in items.items():
                (target / name).write_bytes(data)
        manifest = {
            "schemaVersion": 1, "inputs": inputs, "source": lock["source"],
            "toolchain": lock["toolchain"], "cmakeOptions": lock["cmakeOptions"],
            "verifiedOriginalRegularFiles": original_count, "buildIdentity": build_record, "elf": elf,
            "libraries": library_hashes,
            "buildReceiptSha256": digest(receipt), "jniBoundaryExports": exports,
            "notices": {name: digest(data) for name, data in assets.items()},
        }
        (prepared / "notices/native-manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        for destination, staged in ((LIBRARIES.parent, prepared / "libraries"),
                                    (NOTICES, prepared / "notices")):
            if destination.exists():
                shutil.rmtree(destination)
            if destination == LIBRARIES.parent:
                destination.mkdir(parents=True)
                shutil.copytree(staged, LIBRARIES)
            else:
                shutil.copytree(staged, destination)
    check_prepared(inputs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Read-only prepared-file verification")
    for name in ("source", "build", "archive", "mpl-license", "receipt"):
        parser.add_argument(f"--{name}", type=Path)
    args = parser.parse_args()
    try:
        lock, inputs = current_inputs()
        if args.check:
            if any((args.source, args.build, args.archive, args.mpl_license, args.receipt)):
                raise ValueError("--check takes no import inputs")
            check_prepared(inputs)
        else:
            if not all((args.source, args.build, args.archive, args.mpl_license, args.receipt)):
                raise ValueError("Import requires --source, --build, --archive, --mpl-license and --receipt")
            prepare(args, lock, inputs)
    except (OSError, ValueError, KeyError, IndexError, struct.error, tarfile.TarError) as exc:
        print(f"Native RDP preparation failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
