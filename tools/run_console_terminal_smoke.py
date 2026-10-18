#!/usr/bin/env python3
"""Run the opt-in Console workflow against an owned local SSH terminal fixture.

Credentials are read from its private properties file and sent over adb stdin,
never command arguments or logs. A host-side proof must be observed before the
controller writes the app-private witness. Neither callbacks nor a nonempty log
count as remote execution. This controller never creates or kills a host server.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.baitian6641.cloudy.console"
PRIVATE = "no_backup/ssh-authenticate-fixture"
RECEIPTS = "no_backup/ssh-authenticate-qualification"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--serial", required=True, help="Explicit owned emulator or device; no implicit target")
    parser.add_argument("--device-port", type=int, default=18327)
    args = parser.parse_args()
    if not 1024 <= args.device_port <= 65535:
        parser.error("--device-port must be an unprivileged TCP port")

    source = args.fixture.absolute()
    info = source.lstat()
    if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != os.getuid():
        raise ValueError("Fixture properties must be an owned private regular file")
    if not 1 <= info.st_size <= 16_384:
        raise ValueError("Fixture properties exceed the supported bound")
    properties = {}
    for line in source.read_text().splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            if key in properties:
                raise ValueError("Duplicate fixture property")
            properties[key] = value.replace("\\:", ":").replace("\\=", "=")
    if properties.get("schema") != "1" or properties.get("host") != "127.0.0.1":
        raise ValueError("Requires the owned local ssh-terminal fixture")
    port = int(properties["port"])
    if not 1024 <= port <= 65535:
        raise ValueError("Invalid fixture listener")

    def adb(*command, data=None, timeout=30, check=True):
        result = subprocess.run(["adb", "-s", args.serial, *command], input=data,
                                capture_output=True, timeout=timeout, check=check)
        return result.stdout

    mapping = f"tcp:{args.device_port}"
    if any(mapping in line.split() for line in adb("reverse", "--list").decode().splitlines()):
        raise ValueError("Device port already has a reverse mapping; preserve its owner")
    run = str(uuid.uuid4())
    output = ROOT / "qualification/results/private/console-terminal-resume" / run
    output.mkdir(parents=True, mode=0o700)
    proof = Path("/tmp") / f"cloudy-terminal-{run}.proof"
    if proof.exists() or proof.is_symlink():
        raise ValueError("Proof path already exists")
    expected = f"CLOUDY_TERMINAL_{run}".encode()
    expected_hash = hashlib.sha256(expected).hexdigest()
    config = dict(schema=1, run=run, owner="console", host="127.0.0.1", port=args.device_port,
                  keyFormat="ecdsa-sha2-nistp256", fingerprint=properties["fingerprint"],
                  password=properties["password"], usernameAccept=properties["user"],
                  usernameDeny=properties["user"], usernameChange=properties["user"],
                  usernameWait=properties["user"])

    published = set()

    def put_private(relative, value):
        # The app observes file existence. Publish only a closed, complete file, never
        # the zero-length/partial destination exposed by a direct shell redirect.
        temporary = adb("shell", "run-as", PACKAGE, "mktemp", f"{relative}.XXXXXX").decode().strip()
        if not temporary.startswith(relative + ".") or not temporary[len(relative) + 1:].isalnum():
            raise RuntimeError("PRIVATE_TEMPORARY_PATH_INVALID")
        try:
            adb("shell", "run-as", PACKAGE, "sh", "-c", f"'cat > {temporary}'", data=value)
            # Rename publishes complete bytes. Android denies hard links here; use
            # no-clobber rename and reject mv's otherwise-successful "skipped" result.
            adb("shell", "run-as", PACKAGE, "sh", "-c",
                f"'mv -n {temporary} {relative} && test ! -e {temporary}'")
            published.add(relative)
        finally:
            adb("shell", "run-as", PACKAGE, "rm", "-f", temporary)

    receipt = dict(schema=1, run=run, proofSha256=expected_hash, remoteEffectObserved=False,
                   surfaceCaptured=False, instrumentationPassed=False, baselineRestored=False,
                   controllerTimedOut=False, artifacts={},
                   controllerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    process = None
    reverse_owned = False
    try:
        for relative in ["apps/console/build/outputs/apk/debug/console-debug.apk",
                         "apps/console/build/outputs/apk/androidTest/debug/console-debug-androidTest.apk"]:
            apk = ROOT / relative
            receipt["artifacts"][relative] = hashlib.sha256(apk.read_bytes()).hexdigest()
            if b"Success" not in adb("install", "-r", str(apk), timeout=90):
                raise RuntimeError("APK_INSTALL_FAILED")
        adb("shell", "run-as", PACKAGE, "mkdir", "-p", PRIVATE)
        adb("shell", "run-as", PACKAGE, "chmod", "700", PRIVATE)
        put_private(f"{PRIVATE}/{run}.json", json.dumps(config).encode())
        adb("reverse", mapping, f"tcp:{port}")
        reverse_owned = True
        with (output / "instrumentation.txt").open("wb") as log:
            process = subprocess.Popen([
                "adb", "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
                "-e", "class", "io.github.baitian6641.cloudy.console.ConsoleTerminalActivityTest",
                "-e", "cloudyTerminalRun", run,
                PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner",
            ], stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 120
            while process.poll() is None:
                if time.monotonic() >= deadline:
                    receipt["controllerTimedOut"] = True
                    raise TimeoutError("PRODUCT_INSTRUMENTATION_TIMEOUT")
                if proof.exists() and not receipt["remoteEffectObserved"]:
                    if proof.is_symlink() or not proof.is_file() or proof.read_bytes() != expected:
                        raise RuntimeError("REMOTE_PROOF_BYTES_DIFFER")
                    put_private(f"{PRIVATE}/{run}-terminal-proof.json",
                                json.dumps(dict(schema=1, run=run, sha256=expected_hash)).encode())
                    receipt["remoteEffectObserved"] = True
                time.sleep(0.05)
        log_text = (output / "instrumentation.txt").read_text(errors="replace")
        receipt["instrumentationPassed"] = process.returncode == 0 and "OK (1 test)" in log_text and "FAILURES!!!" not in log_text
    finally:
        if process is not None and process.poll() is None:
            # Killing an adb client does not prove Android-side retirement. Preserve that
            # uncertainty and the fixture receipt; never reset app data to claim cleanup.
            process.terminate()
            process.wait(timeout=10)
        files = [
            (f"{run}-console-success.json", "product-receipt.json"),
            (f"{run}-terminal-surface.png", "terminal-surface.png"),
            (f"{run}-terminal-failure.json", "terminal-failure.json"),
            (f"{run}-terminal-final-state.json", "terminal-final-state.json"),
        ]
        for remote, local in files:
            body = adb("exec-out", "run-as", PACKAGE, "cat", f"{RECEIPTS}/{remote}", check=False)
            if local.endswith(".png"):
                if not body.startswith(b"\x89PNG\r\n\x1a\n"):
                    continue
                receipt["surfaceCaptured"] = True
            else:
                try:
                    parsed = json.loads(body)
                except (UnicodeDecodeError, ValueError):
                    continue
                if local == "product-receipt.json":
                    receipt["baselineRestored"] = parsed.get("phase") == "EXACT_BASELINE_RESTORED"
                    receipt["fixtureCasePassed"] = parsed.get("caseOutcome") == "PASSED"
            (output / local).write_bytes(body)
        if reverse_owned:
            adb("reverse", "--remove", mapping, check=False)
        for relative in published:
            adb("shell", "run-as", PACKAGE, "rm", "-f", relative, check=False)
        if proof.is_file() and not proof.is_symlink() and proof.read_bytes() == expected:
            proof.unlink()
        (output / "result.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print(json.dumps({"run": run, "output": str(output), "result": receipt}, indent=2))
    return 0 if all(receipt.get(field) for field in ["remoteEffectObserved", "instrumentationPassed",
                                                    "baselineRestored", "fixtureCasePassed", "surfaceCaptured"]) else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError):
        # Subprocess diagnostics can contain private test configuration or assertion text.
        print("Console terminal qualification failed; inspect its private receipt.", file=sys.stderr)
        sys.exit(1)
