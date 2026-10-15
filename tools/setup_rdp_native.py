#!/usr/bin/env python3
"""Acquire verified native RDP sources and configure a fresh isolated Android build."""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request

import prepare_rdp_native as native
from build_rdp_native import stop_process_group


def acquire(directory, name, record):
    """Cache source archives only; every cache hit is checked before use."""
    target = native.no_symlinks(directory / name)
    if not target.exists():
        with tempfile.NamedTemporaryFile(dir=directory, prefix="download-", delete=False) as output:
            temporary = Path(output.name)
            try:
                with urllib.request.urlopen(record["url"], timeout=90) as response:
                    shutil.copyfileobj(response, output)
                output.close()
                raw = native.read_file(temporary)
                if len(raw) != record["bytes"] or native.digest(raw) != record["sha256"]:
                    raise ValueError(f"Archive integrity failed: {name}")
                temporary.replace(target)
            finally:
                temporary.unlink(missing_ok=True)
    raw = native.read_file(target)
    if len(raw) != record["bytes"] or native.digest(raw) != record["sha256"]:
        raise ValueError(f"Cached archive differs from source lock: {name}")
    return target


def properties(path):
    return dict(tuple(part.strip() for part in line.split("=", 1))
                for line in native.read_file(path).decode().splitlines()
                if "=" in line and not line.startswith("#"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--stage", type=Path, required=True, help="Fresh directory without spaces")
    parser.add_argument("--archives", type=Path, default=native.ROOT / ".cache/native-rdp/downloads")
    args = parser.parse_args()
    try:
        lock, inputs = native.current_inputs()
        stage = native.no_symlinks(args.stage)
        if stage.exists() or any(character.isspace() for character in str(stage)):
            raise ValueError("Stage must be a fresh path without whitespace")
        archives = native.no_symlinks(args.archives)
        archives.mkdir(parents=True, exist_ok=True)
        # SDK aliases are permitted, but resolve them before validating tool identities.
        sdk = args.sdk.resolve(strict=True)
        ndk = sdk / "ndk" / lock["toolchain"]["ndk"]
        cmake_root = sdk / "cmake" / lock["toolchain"]["cmake"]
        if properties(ndk / "source.properties").get("Pkg.Revision") != lock["toolchain"]["ndk"]:
            raise ValueError("SDK NDK revision differs from lock")
        if properties(cmake_root / "source.properties").get("Pkg.Revision") != lock["toolchain"]["cmake"]:
            raise ValueError("SDK CMake revision differs from lock")
        for tool in ("perl", "make", "pkg-config"):
            if shutil.which(tool) is None:
                raise ValueError(f"Missing host build prerequisite: {tool}")
        source_record = lock["source"]
        archive = acquire(archives, f"FreeRDP-{source_record['sourceCommit']}.tar.gz", source_record)
        dependencies = [(record, acquire(archives, record["archive"], record))
                        for record in lock["dependencyArchives"]]
        stage.mkdir(parents=True)
        # Keep SDK tool paths free of whitespace too, matching the accepted local recipe.
        (stage / "sdk").symlink_to(sdk, target_is_directory=True)
        ndk = stage / "sdk/ndk" / lock["toolchain"]["ndk"]
        cmake_root = stage / "sdk/cmake" / lock["toolchain"]["cmake"]
        with tarfile.open(archive, "r:gz") as bundle:
            # Hash verification above precedes extraction; the data filter rejects unsafe paths.
            bundle.extractall(stage, filter="data")
        source = stage / f"FreeRDP-{source_record['sourceCommit']}"
        for patch in inputs["patches"]:
            native.replay_patch(source, native.read_file(native.PROBE / patch["path"]))
        native.verify_source(source, archive, lock)
        build = stage / "build"
        targets = {"openssl-4.0.1.tar.gz": "openssl", "v1.7.19.tar.gz": "cjson",
                   "uriparser-1.0.2.tar.xz": "uriparser"}
        # ExternalProject rechecks these same URL_HASH values before extracting them.
        for record, cached in dependencies:
            destination = build / f"{targets[record['archive']]}-prefix/src" / record["archive"]
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(cached, destination)
        prefix = source / "client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a"
        environment = os.environ | {"CMAKE_BUILD_PARALLEL_LEVEL": "4", "PKG_CONFIG_PATH": "",
                                    "PKG_CONFIG_LIBDIR": str(prefix / "lib/pkgconfig"),
                                    "CCACHE_DIR": str(stage / "ccache")}
        command = [str(cmake_root / "bin/cmake"), "-S", str(source / native.CPP),
                   "-B", str(build), "-G", "Ninja",
                   f"-DCMAKE_MAKE_PROGRAM={cmake_root / 'bin/ninja'}",
                   f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
                   *lock["cmakeOptions"]]
        with (stage / "configure.log").open("x") as output:
            process = subprocess.Popen(command, env=environment, stdout=output,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                process.wait(timeout=180)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                stop_process_group(process)
                raise ValueError("Native configuration interrupted; process group stopped") from None
        if process.returncode:
            raise ValueError(f"Native configure failed with exit {process.returncode}; see stage/configure.log")
        native.build_identity(build, source, lock, require_inner=False)
        if native.current_inputs()[1] != inputs:
            raise ValueError("Tracked inputs changed during setup")
        print(json.dumps({"source": str(source), "build": str(build), "archive": str(archive)}, indent=2))
    except (OSError, ValueError, KeyError, tarfile.TarError, urllib.error.URLError) as error:
        print(f"Native RDP setup failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
