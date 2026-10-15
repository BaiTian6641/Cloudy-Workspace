#!/usr/bin/env python3
"""Stage the pinned Moonlight Java closure with an owned local native/identity probe."""

import argparse
import hashlib
import json
from pathlib import Path
import re

import prepare_native_stream as native

ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "qualification/probes/native-stream/android"
CACHE = ROOT / ".cache/native-stream-android"
WRAPPER = ROOT / "qualification/probes/device-baseline"
REQUIRED_OVERLAYS = {
    "build.gradle", "settings.gradle", "gradle.properties", "gradle-wrapper.properties",
    "app/build.gradle", "app/lint-baseline.xml", "app/src/main/AndroidManifest.xml",
    "app/src/main/java/dev/cloudy/qualification/stream/StreamProbeActivity.java",
    "app/src/main/java/dev/cloudy/qualification/stream/StreamProbeViewModel.java",
    "app/src/main/java/dev/cloudy/qualification/stream/identity/StreamIdentityStore.java",
    "app/src/main/java/dev/cloudy/qualification/stream/identity/IdentityAuthorization.java",
    "app/src/main/res/values/cloudy_strings.xml",
    "app/src/main/res/drawable/cloudy_stream_icon.xml",
    "app/src/main/res/xml/cloudy_backup.xml", "app/src/main/res/xml/cloudy_extraction.xml",
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def file_record(raw):
    return {"bytes": len(raw), "sha256": digest(raw)}


def inputs():
    if not REQUIRED_OVERLAYS <= {path.relative_to(OVERLAY).as_posix()
                                 for path in OVERLAY.rglob("*") if path.is_file()}:
        raise ValueError("Required owned Android overlay missing; upstream launcher cannot be used")
    paths = [Path(__file__).resolve(), WRAPPER / "gradlew",
             WRAPPER / "gradle/wrapper/gradle-wrapper.jar"]
    paths.extend(path for path in sorted(OVERLAY.rglob("*")) if path.is_file())
    return {str(path.relative_to(ROOT)): native.read(path) for path in paths}


def expected_files(native_run, source_inputs):
    # The native verifier reconstructs these source bytes from the four exact
    # archives and checks its source/build inputs before Java packaging can start.
    receipt = native.check(native_run)
    files = {}
    for name, expected in receipt["sourceFiles"].items():
        raw = native.read(native_run / "source" / name)
        if digest(raw) != expected:
            raise ValueError("Native source changed while staging Android inputs")
        files[name] = raw
    prefix = str(OVERLAY.relative_to(ROOT)) + "/"
    for name, raw in source_inputs.items():
        if name.startswith(prefix):
            target = name[len(prefix):]
            if target == "gradle-wrapper.properties":
                target = "gradle/wrapper/gradle-wrapper.properties"
            files[target] = raw
    for name in ("gradlew", "gradle/wrapper/gradle-wrapper.jar"):
        files[name] = source_inputs[str(WRAPPER.relative_to(ROOT)) + "/" + name]
    for name in receipt["prepared"]:
        path = Path(name)
        if name.startswith("nativeLibraries/"):
            files["app/build/generated/cloudy/" + name] = native.read(native_run / "prepared" / name)
        elif path.parent.as_posix() == "nativeNotices" and (
                path.suffix == ".txt" or path.name == "NDK-cpufeatures-NOTICE"):
            files["app/build/generated/cloudy/" + name] = native.read(native_run / "prepared" / name)
    return files, receipt


def prepare(stage, native_run, sdk):
    frozen = inputs()
    files, native_receipt = expected_files(native_run, frozen)
    # Source archives stay in the native evidence bundle. Only selected notice
    # text is packaged here; this is not the final corresponding-source delivery.
    sdk = native.no_symlinks(sdk.absolute())
    if not (sdk / "platforms/android-37.0/android.jar").is_file():
        raise ValueError("Existing SDK37 is required; no installer is invoked")
    property_value = str(sdk).replace("\\", "\\\\").replace(":", "\\:")
    files["local.properties"] = ("sdk.dir=" + property_value + "\n").encode()
    stage.mkdir(parents=True, exist_ok=False)
    try:
        for name, raw in files.items():
            target = stage / native.relative(name)
            native.no_symlinks(target)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        (stage / "gradlew").chmod(0o755)
        if frozen != inputs():
            raise ValueError("Authored Android inputs changed during preparation")
        receipt = {
            "schemaVersion": 1,
            "scope": "real upstream Java/resource closure and local native/identity diagnostic; not a stream",
            "nativeRun": native_run.name,
            "nativeReceipt": file_record(native.read(native_run / "receipt.json")),
            "nativeBuildRef": native_receipt["buildRef"],
            "inputs": {name: file_record(raw) for name, raw in frozen.items()},
            "stagedFiles": {name: file_record(raw) for name, raw in files.items()},
        }
        (stage / "cloudy-preparation.json").write_text(json.dumps(receipt, indent=2) + "\n")
        check(stage)
    except BaseException as error:
        (stage / "cloudy-failure.json").write_text(json.dumps({"failureClass": type(error).__name__}) + "\n")
        raise
    print(json.dumps({"status": "prepared", "files": len(files), "stage": str(stage)}))


def check(stage):
    receipt = json.loads(native.read(stage / "cloudy-preparation.json"))
    current = inputs()
    if receipt["inputs"] != {name: file_record(raw) for name, raw in current.items()}:
        raise ValueError("Authored Android inputs differ from preparation receipt")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", receipt["nativeRun"]):
        raise ValueError("Invalid retained native run name")
    native_run = native.CACHE / receipt["nativeRun"]
    if file_record(native.read(native_run / "receipt.json")) != receipt["nativeReceipt"]:
        raise ValueError("Native preparation receipt changed")
    files, _ = expected_files(native_run, current)
    for name, raw in files.items():
        if receipt["stagedFiles"].get(name) != file_record(raw):
            raise ValueError("Staged input is not reconstructed from reviewed source")
    for name, expected in receipt["stagedFiles"].items():
        if file_record(native.read(stage / native.relative(name))) != expected:
            raise ValueError("Retained Android source/native input changed: " + name)
    # Additional Java/resource/manifest/native source could enter the compiler
    # without changing a recorded file. Reject it, while allowing Gradle outputs.
    for path in stage.rglob("*"):
        name = path.relative_to(stage).as_posix()
        if (name.startswith((".gradle/", "build/", "app/build/"))
                and not name.startswith("app/build/generated/cloudy/")):
            continue
        native.no_symlinks(path)
        if path.is_file() and name not in receipt["stagedFiles"] and name not in {
                "cloudy-preparation.json", "cloudy-failure.json"}:
            raise ValueError("Unreviewed Android source, build logic or prepared payload")
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--prepare", action="store_true")
    modes.add_argument("--check", action="store_true")
    parser.add_argument("--stage", required=True, help="Fresh name beneath .cache/native-stream-android")
    parser.add_argument("--native-run", default="entry-02")
    parser.add_argument("--sdk", type=Path, default=ROOT / ".cache/android-sdk")
    args = parser.parse_args()
    if any(not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", name) for name in (args.stage, args.native_run)):
        parser.error("Stage and native run must be simple lowercase names")
    stage = native.no_symlinks(CACHE / args.stage)
    try:
        if args.prepare:
            prepare(stage, native.CACHE / args.native_run, args.sdk)
        else:
            receipt = check(stage)
            print(json.dumps({"status": "verified", "files": len(receipt["stagedFiles"]), "stage": str(stage)}))
    except (OSError, ValueError, KeyError) as error:
        parser.exit(1, f"Stream Android preparation failed: {error}\n")


if __name__ == "__main__":
    main()
