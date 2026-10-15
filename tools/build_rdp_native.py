#!/usr/bin/env python3
"""Build the configured pinned RDP target and record its actual successful outputs.

Configuration/source acquisition follows the probe README. No SDK downloads or
system installation occur here. Receipt/log destinations stay in ignored cache.
"""

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys

import prepare_rdp_native as native


def stop_process_group(process):
    """Stop descendants even when the CMake leader exits before its workers."""
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        pass
    # Group lifetime differs from leader lifetime; escalate independently of wait().
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "build", "archive", "receipt"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    args = parser.parse_args()
    try:
        source = native.no_symlinks(args.source).resolve(strict=True)
        build = native.no_symlinks(args.build).resolve(strict=True)
        receipt = native.no_symlinks(args.receipt).resolve()
        if not receipt.is_relative_to(native.ROOT / ".cache"):
            raise ValueError("Receipt must be inside this checkout's ignored .cache")
        if receipt.exists() or receipt.with_suffix(".log").exists():
            raise ValueError("Preserve previous receipts/logs; choose a fresh receipt name")
        lock, inputs = native.current_inputs()
        native.verify_source(source, args.archive, lock)
        native.build_identity(build, source, lock, require_inner=False)
        values = {}
        for line in (build / "CMakeCache.txt").read_text().splitlines():
            if ":" in line and "=" in line and not line.startswith(("#", "//")):
                key, value = line.split("=", 1)
                values[key.split(":", 1)[0]] = value
        cmake = Path(values["CMAKE_COMMAND"]).resolve(strict=True)
        prefix = source / "client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a"
        env = os.environ | {"CMAKE_BUILD_PARALLEL_LEVEL": "4", "PKG_CONFIG_PATH": "",
                            "PKG_CONFIG_LIBDIR": str(prefix / "lib/pkgconfig"),
                            "CCACHE_DIR": str(build.parent / "ccache")}
        receipt.parent.mkdir(parents=True, exist_ok=True)
        with receipt.with_suffix(".log").open("x") as log:
            process = subprocess.Popen([str(cmake), "--build", str(build), "--target",
                                        lock["nativeTarget"], "--parallel", "4"],
                                       env=env, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            try:
                process.wait(timeout=1800)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                stop_process_group(process)
                raise ValueError("Native build interrupted; process group stopped; no receipt issued") from None
        result = process
        if result.returncode:
            raise ValueError(f"Native build failed with exit {result.returncode}; no receipt issued")
        # Recheck source after compilation; never attest an edited-during-build tree.
        native.verify_source(source, args.archive, lock)
        native.build_identity(build, source, lock)
        if native.current_inputs()[1] != inputs:
            raise ValueError("Tracked inputs changed during build; no receipt issued")
        hashes = {}
        for name in sorted(native.LIBS):
            path = build / name if name == "libfreerdp-android.so" else prefix / name
            data = native.read_file(path)
            if native.elf_info(data)["soname"] != name:
                raise ValueError("Build output SONAME differs from selected library")
            if name == "libfreerdp-android.so":
                native.jni_boundary_exports(data)
            hashes[name] = native.digest(data)
        record = {"schemaVersion": 1, "exitCode": result.returncode,
                  "inputs": inputs, "libraries": hashes}
        with receipt.open("x") as output:
            output.write(json.dumps(record, indent=2) + "\n")
        print("Actual native target succeeded; eight output hashes recorded")
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        print(f"Native build receipt failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
