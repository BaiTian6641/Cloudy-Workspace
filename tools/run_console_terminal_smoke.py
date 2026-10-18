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
import shlex
import signal
import stat
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.baitian6641.cloudy.console"
PRIVATE = "no_backup/ssh-authenticate-fixture"
RECEIPTS = "no_backup/ssh-authenticate-qualification"
IME_PRIVATE = "no_backup/ime-qualification"
IME_COMPONENT = PACKAGE + "/io.github.baitian6641.cloudy.testing.ime.QualificationImeService"
IME_SETTINGS = ("default_input_method", "enabled_input_methods", "selected_input_method_subtype")
IME_SETTING_LIMIT = 8_192


def _parse_ime_setting(body):
    """Preserve null/empty/whitespace exactly; reject truncated or multiline command output."""
    if len(body) > IME_SETTING_LIMIT or b"\x00" in body:
        raise ValueError("IME_SETTING_BOUNDS")
    if body.endswith(b"\r\n"):
        body = body[:-2]
    elif body.endswith(b"\n"):
        body = body[:-1]
    else:
        raise ValueError("IME_SETTING_INCOMPLETE")
    if b"\r" in body or b"\n" in body:
        raise ValueError("IME_SETTING_MULTILINE")
    value = body.decode("utf-8")
    return None if value == "null" else value


def _ime_record_paths(record, run):
    """Accept only this run's bounded immutable request IDs before constructing audit paths."""
    if (not isinstance(record, dict) or type(record.get("schema")) is not int or
            record["schema"] != 1 or record.get("run") != run or
            record.get("component") != IME_COMPONENT):
        raise ValueError("IME_RESULT_IDENTITY")
    requests = record.get("requests")
    if not isinstance(requests, list) or len(requests) > 64:
        raise ValueError("IME_REQUEST_IDS")
    seen = set()
    paths = [f"{IME_PRIVATE}/{name}" for name in (
        "lease.json", "lease.json.new", "lease.lock", "state.json.new", "session-result.json.new")]
    for request in requests:
        if not isinstance(request, str):
            raise ValueError("IME_REQUEST_ID")
        parsed = uuid.UUID(request)
        if parsed.version != 4 or str(parsed) != request or request in seen:
            raise ValueError("IME_REQUEST_ID")
        seen.add(request)
        for kind in ("request", "result"):
            path = f"{IME_PRIVATE}/{run}-{request}.{kind}.json"
            paths.extend((path, path + ".new"))
    return paths


def _read_owned_regular(path, maximum, *, private=False):
    """Bound the read on the opened inode, not a racy path stat or an unbounded read_bytes."""
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC | os.O_NONBLOCK)
    with os.fdopen(descriptor, "rb") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid():
            raise ValueError("Evidence is not an owned regular file")
        if private and stat.S_IMODE(info.st_mode) != 0o600:
            raise ValueError("Private evidence mode differs")
        if info.st_size > maximum:
            raise ValueError("Evidence exceeds its bound")
        body = stream.read(maximum + 1)
        if len(body) > maximum:
            raise ValueError("Evidence grew beyond its bound")
        return body


def _interrupt_for_cleanup(_signal, _frame):
    """Route a finite controller stop through owned cleanup; SIGKILL cannot be recovered here."""
    raise KeyboardInterrupt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--serial", required=True, help="Explicit owned emulator or device; no implicit target")
    parser.add_argument("--device-port", type=int, default=18327)
    parser.add_argument("--interactions", action="store_true",
                        help="Also verify local menu/cancel/find and real >16KiB clipboard input")
    parser.add_argument("--allow-device-ime-change", action="store_true",
                        help="Explicitly authorize the Android fixture to change/restore a physical device IME")
    parser.add_argument("--auth", choices=("password", "keyboard-interactive", "private-key"),
                        default="password",
                        help="Product authentication method exercised by the fixture journey")
    parser.add_argument("--jump", action="store_true",
                        help="Seed one same-peer jump profile and target reference for the journey")
    parser.add_argument("--remember-key", action="store_true",
                        help="Persist the picked key's document reference and verify the reload path")
    args = parser.parse_args()
    if not 1024 <= args.device_port <= 65535:
        parser.error("--device-port must be an unprivileged TCP port")

    source = args.fixture.absolute()
    raw_properties = _read_owned_regular(source, 16_384, private=True)
    if not raw_properties:
        raise ValueError("Fixture properties are empty")
    properties = {}
    for line in raw_properties.decode().splitlines():
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

    def device_script(script, *, as_app=False):
        # Shell v2 carries the remote exit status. run-as exec preserves argv quoting
        # differently from adbd's outer shell, so pass the finite script over stdin.
        command = ["shell", "-T"]
        if as_app:
            command.extend(("run-as", PACKAGE))
        return adb(*command, "sh", data=(script + "\n").encode(), timeout=5)

    def ime_snapshot():
        settings = {}
        for name in IME_SETTINGS:
            script = (f"set -o pipefail; cmd settings get secure {shlex.quote(name)} | "
                      f"dd bs=4096 iflag=count_bytes count={IME_SETTING_LIMIT + 1} status=none")
            body = device_script(script)
            settings[name] = _parse_ime_setting(body)
        return settings

    mapping = f"tcp:{args.device_port}"
    if any(mapping in line.split() for line in adb("reverse", "--list").decode().splitlines()):
        raise ValueError("Device port already has a reverse mapping; preserve its owner")
    run = str(uuid.uuid4())
    output = ROOT / "qualification/results/private/console-terminal-resume" / run
    output.mkdir(parents=True, mode=0o700)
    proof = output / "remote-effect.proof"
    if proof.exists() or proof.is_symlink():
        raise ValueError("Proof path already exists")
    expected = f"CLOUDY_TERMINAL_{run}".encode()
    expected_hash = hashlib.sha256(expected).hexdigest()
    config = dict(schema=1, run=run, owner="console", host="127.0.0.1", port=args.device_port,
                  keyFormat="ecdsa-sha2-nistp256", fingerprint=properties["fingerprint"], jump=args.jump,
                  targetPort=int(properties["port"]) if args.jump else args.device_port,
                  password=properties["password"], usernameAccept=properties["user"],
                  usernameDeny=properties["user"], usernameChange=properties["user"],
                  usernameWait=properties["user"])
    interaction_config = None
    capture_record = None
    paste_bytes = b"B" * 20_000
    if args.interactions:
        capture_run = str(uuid.uuid4())
        capture_dir = output / "pty-capture"
        capture_dir.mkdir(mode=0o700)
        capture_record = capture_dir / f"exercise-{capture_run}.json"
        command = shlex.join(["python3", "-I", str(ROOT / "qualification/fixtures/ssh-terminal/exercise.py"),
                              "--workdir", str(capture_dir), "--mode", "capture", "--timeout", "15",
                              "--max-bytes", "65536", "--run", capture_run])
        interaction_config = dict(schema=1, run=run, captureRun=capture_run,
                                  captureCommand=command, pasteText=paste_bytes.decode("ascii"))

    published = set()
    temporaries = set()
    uncertain_publications = set()

    def put_private(relative, value):
        # The app observes file existence. Publish only a closed, complete file, never
        # the zero-length/partial destination exposed by a direct shell redirect.
        try:
            temporary = adb("shell", "run-as", PACKAGE, "mktemp", f"{relative}.XXXXXX").decode().strip()
        except (OSError, ValueError, subprocess.SubprocessError):
            receipt["cleanupErrors"].append("PRIVATE_TEMPORARY_CREATION_UNCONFIRMED")
            raise
        if not temporary.startswith(relative + ".") or not temporary[len(relative) + 1:].isalnum():
            receipt["cleanupErrors"].append("PRIVATE_TEMPORARY_PATH_INVALID")
            raise RuntimeError("PRIVATE_TEMPORARY_PATH_INVALID")
        temporaries.add(temporary)
        try:
            adb("shell", "run-as", PACKAGE, "sh", "-c", f"'cat > {temporary}'", data=value)
            # Rename publishes complete bytes. Android denies hard links here; use
            # no-clobber rename and reject mv's otherwise-successful "skipped" result.
            uncertain_publications.add(relative)
            adb("shell", "run-as", PACKAGE, "sh", "-c",
                f"'mv -n {temporary} {relative} && test ! -e {temporary}'")
            published.add(relative)
            uncertain_publications.discard(relative)
        finally:
            adb("shell", "run-as", PACKAGE, "rm", "-f", temporary)
            temporaries.discard(temporary)

    receipt = dict(schema=1, run=run, proofSha256=expected_hash, remoteEffectObserved=False,
                   surfaceCaptured=False, instrumentationPassed=False, baselineRestored=False,
                   controllerTimedOut=False, instrumentationRetired=False,
                   cleanupConfirmed=False, cleanupErrors=[], artifacts={},
                   controllerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    receipt["interactionsRequested"] = args.interactions
    receipt["authMethod"] = args.auth
    receipt["jumpRequested"] = args.jump
    receipt["rememberKeyRequested"] = args.remember_key
    receipt["keyFileRemoved"] = True
    key_name = f"cloudy-key-{run}"
    receipt.update(inputMethodRestored=False, imeCleanupConfirmed=False, imeCleanupErrors=[])
    ime_before = None
    interaction_published = False
    process = None
    log_text = ""
    reverse_owned = False
    previous_sigterm = signal.signal(signal.SIGTERM, _interrupt_for_cleanup)
    try:
        if adb("shell", "getprop", "ro.kernel.qemu", timeout=5).strip() != b"1" and not args.allow_device_ime_change:
            raise ValueError("IME_PHYSICAL_CHANGE_NOT_AUTHORIZED")
        ime_before = ime_snapshot()
        with (output / "ime-baseline.json").open("x") as baseline:
            os.fchmod(baseline.fileno(), 0o600)
            json.dump(ime_before, baseline)
        selected = ime_before["default_input_method"]
        if selected is None or selected.endswith("/io.github.baitian6641.cloudy.testing.ime.QualificationImeService"):
            raise ValueError("IME_BASELINE_NOT_AVAILABLE")
        for relative in ["apps/console/build/outputs/apk/debug/console-debug.apk",
                         "apps/console/build/outputs/apk/androidTest/debug/console-debug-androidTest.apk"]:
            apk = ROOT / relative
            receipt["artifacts"][relative] = hashlib.sha256(apk.read_bytes()).hexdigest()
            if b"Success" not in adb("install", "-r", str(apk), timeout=90):
                raise RuntimeError("APK_INSTALL_FAILED")
        # An older ownership record is not ours to retire or overwrite.
        lease_paths = [f"{IME_PRIVATE}/{name}" for name in ("lease.json", "lease.json.new", "lease.lock")]
        script = (f"test ! -L {IME_PRIVATE} && "
                  f"for p in {shlex.join(lease_paths)}; do test ! -e \"$p\" && test ! -L \"$p\" || exit 1; done")
        device_script(script, as_app=True)
        adb("shell", "run-as", PACKAGE, "mkdir", "-p", PRIVATE)
        adb("shell", "run-as", PACKAGE, "chmod", "700", PRIVATE)
        put_private(f"{PRIVATE}/{run}.json", json.dumps(config).encode())
        if interaction_config is not None:
            put_private(f"{PRIVATE}/{run}-interactions.json", json.dumps(interaction_config).encode())
        if args.auth == "private-key":
            # The tracked fixture generates this key inside its private run directory; publish a
            # copy only to the emulator's Downloads for the real document-picker journey.
            key_source = source.parent / "client_key"
            info = os.lstat(key_source)
            if (not stat.S_ISREG(info.st_mode) or info.st_size > 65536 or
                    stat.S_IMODE(info.st_mode) != 0o600):
                raise ValueError("KEY_FIXTURE_INVALID")
            adb("push", str(key_source), f"/sdcard/Download/{key_name}", timeout=30)
            receipt["keyFileName"] = key_name
        adb("reverse", "--no-rebind", mapping, f"tcp:{port}")
        reverse_owned = True
        with (output / "instrumentation.txt").open("wb") as log:
            os.fchmod(log.fileno(), 0o600)
            process = subprocess.Popen([
                "adb", "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
                "-e", "class", "io.github.baitian6641.cloudy.console.ConsoleTerminalActivityTest",
                "-e", "cloudyTerminalRun", run,
                "-e", "cloudyTerminalProofPath", shlex.quote(str(proof)),
                "-e", "cloudyTerminalInteractions", str(args.interactions).lower(),
                "-e", "cloudyTerminalAuth", args.auth,
                "-e", "cloudyTerminalJump", str(args.jump).lower(),
                "-e", "cloudyTerminalRememberKey", str(args.remember_key).lower(),
                "-e", "cloudyTerminalKeyName", key_name,
                "-e", "cloudyAllowDeviceImeChange", str(args.allow_device_ime_change).lower(),
                PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner",
            ], stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 120
            while process.poll() is None:
                if time.monotonic() >= deadline:
                    receipt["controllerTimedOut"] = True
                    raise TimeoutError("PRODUCT_INSTRUMENTATION_TIMEOUT")
                if (proof.exists() or proof.is_symlink()) and not receipt["remoteEffectObserved"]:
                    observed = _read_owned_regular(proof, len(expected), private=True)
                    if observed == expected:
                        put_private(f"{PRIVATE}/{run}-terminal-proof.json",
                                    json.dumps(dict(schema=1, run=run, sha256=expected_hash)).encode())
                        receipt["remoteEffectObserved"] = True
                    elif not expected.startswith(observed):
                        raise RuntimeError("REMOTE_PROOF_BYTES_DIFFER")
                if capture_record is not None and capture_record.exists() and not interaction_published:
                    try:
                        captured = json.loads(_read_owned_regular(capture_record, 262_144, private=True))
                    except (UnicodeDecodeError, json.JSONDecodeError):
                        captured = None  # The append-only writer may not have finished publication.
                    if captured is not None:
                        actual_hex = captured.get("capturedHex", "")
                        if not isinstance(actual_hex, str) or len(actual_hex) > 131_072:
                            raise RuntimeError("CAPTURE_BYTES_INVALID")
                        actual = bytes.fromhex(actual_hex)
                        observed_hash = hashlib.sha256(actual).hexdigest()
                        matched = (captured.get("schemaVersion") == 1 and
                                   captured.get("runId") == interaction_config["captureRun"] and
                                   captured.get("mode") == "capture" and actual == paste_bytes and
                                   captured.get("capturedBytes") == len(actual) and
                                   captured.get("capturedSha256") == observed_hash and
                                   captured.get("pendingInputDiscardedOnRestore") is True and
                                   captured.get("intendedExitCode") == 0)
                        receipt["hostCaptureMatched"] = matched
                        receipt["interactionBytes"] = len(actual)
                        put_private(f"{PRIVATE}/{run}-interaction-proof.json", json.dumps(dict(
                            schema=1, run=run, captureRun=interaction_config["captureRun"],
                            matched=matched, bytes=len(actual), sha256=observed_hash)).encode())
                        receipt["interactionVerified"] = matched
                        interaction_published = True
                time.sleep(0.05)
        log_text = _read_owned_regular(output / "instrumentation.txt", 262_144, private=True).decode(errors="replace")
        receipt["instrumentationPassed"] = process.returncode == 0 and "OK (1 test)" in log_text and "FAILURES!!!" not in log_text
    finally:
        # A stop requests Android-side unwinding; killing only the adb client is not retirement.
        previous_sigint = signal.signal(signal.SIGINT, signal.SIG_IGN)
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        try:
            if process is not None and process.poll() is None:
                cancel = f"{PRIVATE}/{run}-cancel"
                receipt["cancellationRequested"] = False
                try:
                    adb("shell", "run-as", PACKAGE, "sh", "-c",
                        f"'umask 077; set -C; : > {cancel}'", timeout=5)
                    published.add(cancel)
                    receipt["cancellationRequested"] = True
                except (OSError, subprocess.SubprocessError) as failure:
                    receipt["cleanupErrors"].append(f"cancel:{type(failure).__name__}")
                try:
                    process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
            if process is None:
                receipt["instrumentationRetired"] = True  # No Android actor was started.
            else:
                if not log_text:
                    try:
                        log_text = _read_owned_regular(
                            output / "instrumentation.txt", 262_144, private=True).decode(errors="replace")
                    except (OSError, ValueError) as failure:
                        receipt["cleanupErrors"].append(f"log:{type(failure).__name__}")
                receipt["instrumentationRetired"] = "INSTRUMENTATION_CODE:" in log_text
            files = [
                (f"{run}-console-success.json", "product-receipt.json"),
                (f"{run}-terminal-surface.png", "terminal-surface.png"),
                (f"{run}-terminal-failure.json", "terminal-failure.json"),
                (f"{run}-terminal-final-state.json", "terminal-final-state.json"),
                (f"{run}-terminal-interactions.json", "terminal-interactions.json"),
                (f"{run}-terminal-paste-preview.png", "terminal-paste-preview.png"),
                (f"{run}-terminal-search.png", "terminal-search.png"),
                (f"{run}-terminal-interaction-failure.png", "terminal-interaction-failure.png"),
            ]
            receipt["artifactReadFailures"] = []
            for remote, local in files:
                maximum = 16 * 1024 * 1024 if local.endswith(".png") else 4 * 1024 * 1024
                try:
                    # Toybox count_bytes bounds stdout even if the app-side file grows mid-read.
                    body = adb("exec-out", "run-as", PACKAGE, "dd", f"if={RECEIPTS}/{remote}",
                               "bs=65536", "iflag=count_bytes", f"count={maximum + 1}",
                               "status=none", timeout=5)
                    if len(body) > maximum:
                        raise ValueError("ARTIFACT_TOO_LARGE")
                    parsed = None
                    if local.endswith(".png"):
                        if not body.startswith(b"\x89PNG\r\n\x1a\n"):
                            raise ValueError("ARTIFACT_NOT_PNG")
                    else:
                        parsed = json.loads(body)
                        if not isinstance(parsed, dict):
                            raise ValueError("ARTIFACT_NOT_OBJECT")
                    with (output / local).open("xb") as artifact:
                        os.fchmod(artifact.fileno(), 0o600)
                        artifact.write(body)
                    if local == "terminal-surface.png":
                        receipt["surfaceCaptured"] = True
                    elif local == "product-receipt.json":
                        receipt["baselineRestored"] = parsed.get("phase") == "EXACT_BASELINE_RESTORED"
                        receipt["fixtureCasePassed"] = parsed.get("caseOutcome") == "PASSED"
                except (OSError, ValueError, subprocess.SubprocessError) as failure:
                    # Failure-only/late-stage artifacts may legitimately not exist.
                    receipt["artifactReadFailures"].append(f"{local}:{type(failure).__name__}")
            receipt["ownedReverseReleased"] = not reverse_owned
            receipt["ownedPrivateFilesRemoved"] = not (published or temporaries)
            if receipt["instrumentationRetired"]:
                # Read-only independent audit. Never repair settings or release an uncertain
                # Android lease: preserve it for explicit owner recovery, without changing failure.
                try:
                    after = ime_snapshot()
                    with (output / "ime-after.json").open("x") as snapshot:
                        os.fchmod(snapshot.fileno(), 0o600)
                        json.dump(after, snapshot)
                    receipt["inputMethodRestored"] = ime_before is not None and after == ime_before
                    if not receipt["inputMethodRestored"]:
                        receipt["imeCleanupErrors"].append("IME_SETTINGS_DIFFER")
                    if process is None:
                        receipt["imeCleanupConfirmed"] = receipt["inputMethodRestored"]
                    else:
                        remote = f"{IME_PRIVATE}/session-result.json"
                        script = (f"test ! -L {IME_PRIVATE} && test -f {remote} && test ! -L {remote} && "
                                  f"dd if={remote} bs=4096 iflag=count_bytes count=65537 status=none")
                        body = device_script(script, as_app=True)
                        if len(body) > 65_536:
                            raise ValueError("IME_RESULT_TOO_LARGE")
                        record = json.loads(body)
                        paths = _ime_record_paths(record, run)
                        with (output / "ime-session-result.json").open("xb") as result_file:
                            os.fchmod(result_file.fileno(), 0o600)
                            result_file.write(body)
                        script = (f"test ! -L {IME_PRIVATE} && for p in {shlex.join(paths)}; "
                                  "do test ! -e \"$p\" && test ! -L \"$p\" || exit 1; done")
                        device_script(script, as_app=True)
                        receipt["imeCleanupConfirmed"] = all(record.get(key) is True for key in (
                            "restored", "requestsRetired", "leaseRetired", "lockRetired",
                            "shellRetired", "cleanupConfirmed"))
                        if not receipt["imeCleanupConfirmed"]:
                            receipt["imeCleanupErrors"].append("IME_NATIVE_RETIREMENT_UNCONFIRMED")
                except (OSError, ValueError, subprocess.SubprocessError) as failure:
                    receipt["imeCleanupErrors"].append(f"ime-audit:{type(failure).__name__}")
                if reverse_owned:
                    try:
                        rows = [line.split() for line in adb("reverse", "--list", timeout=5).decode().splitlines()]
                        matches = [row for row in rows if len(row) >= 3 and row[-2] == mapping]
                        if matches:
                            if len(matches) != 1 or matches[0][-1] != f"tcp:{port}":
                                raise RuntimeError("REVERSE_OWNER_CHANGED_PRESERVED")
                            adb("reverse", "--remove", mapping, timeout=5)
                        remaining = adb("reverse", "--list", timeout=5).decode().splitlines()
                        if any(mapping in line.split() for line in remaining):
                            raise RuntimeError("REVERSE_RELEASE_UNCONFIRMED")
                        receipt["ownedReverseReleased"] = True
                    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as failure:
                        receipt["cleanupErrors"].append(f"reverse:{type(failure).__name__}")
                if args.auth == "private-key":
                    try:
                        adb("shell", "rm", "-f", f"/sdcard/Download/{key_name}", timeout=5)
                        present = subprocess.run(
                            ["adb", "-s", args.serial, "shell", "test", "-e",
                             f"/sdcard/Download/{key_name}"], capture_output=True, timeout=5)
                        receipt["keyFileRemoved"] = present.returncode != 0
                        if not receipt["keyFileRemoved"]:
                            receipt["cleanupErrors"].append("KEY_FILE_NOT_REMOVED")
                    except (OSError, subprocess.SubprocessError) as failure:
                        receipt["keyFileRemoved"] = False
                        receipt["cleanupErrors"].append(f"key-file:{type(failure).__name__}")
                private_files = sorted(published | temporaries)
                if private_files:
                    try:
                        adb("shell", "run-as", PACKAGE, "rm", "-f", *private_files, timeout=5)
                        checks = " && ".join(f"test ! -e {path} && test ! -L {path}" for path in private_files)
                        adb("shell", "run-as", PACKAGE, "sh", "-c", f"'{checks}'", timeout=5)
                        receipt["ownedPrivateFilesRemoved"] = True
                    except (OSError, subprocess.SubprocessError) as failure:
                        receipt["cleanupErrors"].append(f"private-files:{type(failure).__name__}")
            else:
                # Keep credentials/forward alive for an unretired test rather than removing
                # dependencies from under it. This uncertainty can never count as a pass.
                receipt["cleanupDeferred"] = True
                receipt["cleanupErrors"].append("ANDROID_INSTRUMENTATION_UNCERTAIN")
                receipt["imeCleanupErrors"].append("IME_AUDIT_DEFERRED_UNRETIRED_INSTRUMENTATION")
            receipt["uncertainPublications"] = sorted(uncertain_publications)
            receipt["cleanupConfirmed"] = (receipt["instrumentationRetired"] and
                receipt["ownedReverseReleased"] and receipt["ownedPrivateFilesRemoved"] and
                receipt["inputMethodRestored"] and receipt["imeCleanupConfirmed"] and
                receipt["keyFileRemoved"] and
                not receipt["imeCleanupErrors"] and not receipt["cleanupErrors"] and not uncertain_publications)
        finally:
            signal.signal(signal.SIGTERM, previous_sigterm)
            signal.signal(signal.SIGINT, previous_sigint)
            with (output / "result.json").open("x") as result_file:
                os.fchmod(result_file.fileno(), 0o600)
                result_file.write(json.dumps(receipt, indent=2) + "\n")
            print(json.dumps({"run": run, "output": str(output), "result": receipt}, indent=2))
    passed = all(receipt.get(field) for field in ["remoteEffectObserved", "instrumentationPassed",
                                                "baselineRestored", "fixtureCasePassed", "surfaceCaptured",
                                                "cleanupConfirmed"])
    return 0 if passed and (not args.interactions or receipt.get("interactionVerified")) else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, KeyboardInterrupt):
        # Subprocess diagnostics can contain private test configuration or assertion text.
        print("Console terminal qualification failed; inspect its private receipt.", file=sys.stderr)
        sys.exit(1)
