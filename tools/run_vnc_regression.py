#!/usr/bin/env python3
"""Build/run three bounded advisory regressions against verified cached upstream.

No downloads, listeners, real server connections, installs or Android claims.
Every invocation requires a fresh output directory and retains failed evidence.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import tarfile
import time


REVISION = "98aaee0560baa0332339b2ef464ea96ed9b9b28c"
ARCHIVE_SHA256 = "e4aba30e25cd7bcc743794ccaf4a9b7c3236d15945b9e8b8bb059b0ae1d2f1bb"
ROOT = Path(__file__).resolve().parent.parent
FLAGS = (
    "-fsanitize=address,undefined -fno-omit-frame-pointer -O1 -g "
    "-DLZO_CFG_NO_UNALIGNED=1"
)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def verify_source(source, archive):
    """Bind every regular/symlink input to the accepted archive, without extracting."""
    if sha256(archive) != ARCHIVE_SHA256:
        raise ValueError("source archive hash mismatch")
    records = {}
    with tarfile.open(archive, "r:gz") as bundle:
        for member in bundle:
            parts = Path(member.name).parts
            if not parts or parts[0] != f"libvncserver-{REVISION}":
                raise ValueError("unexpected archive root")
            relative = Path(*parts[1:])
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("unsafe source archive path")
            path = source / relative
            if member.isdir():
                continue
            if member.issym():
                if not path.is_symlink() or os.readlink(path) != member.linkname:
                    raise ValueError(f"source symlink mismatch: {relative}")
                records[str(relative)] = {"symlink": member.linkname}
            elif member.isfile():
                stream = bundle.extractfile(member)
                if stream is None:
                    raise ValueError("missing archived source bytes")
                expected = hashlib.sha256(stream.read()).hexdigest()
                if path.is_symlink() or not path.is_file() or sha256(path) != expected:
                    raise ValueError(f"source file mismatch: {relative}")
                records[str(relative)] = {"sha256": expected, "bytes": member.size}
            else:
                raise ValueError("unsupported archive entry")
    actual = {str(p.relative_to(source)) for p in source.rglob("*")
              if p.is_symlink() or p.is_file()}
    if actual != set(records):
        raise ValueError("source tree has extra or missing files")
    return records


def run(stage, name, command, timeout, env=None):
    started = time.monotonic()
    with (stage / f"{name}.stdout").open("wb") as out, \
            (stage / f"{name}.stderr").open("wb") as err:
        process = subprocess.Popen(command, stdout=out, stderr=err, env=env,
                                   start_new_session=True)
        try:
            code = process.wait(timeout=timeout)
        except BaseException:
            # These compiler/test processes have no persistent-service purpose.
            # Kill the whole owned group, including descendants, on interruption.
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
            save(stage / f"{name}.json", {"command": command,
                 "exitCode": process.returncode, "interrupted": True})
            raise
    save(stage / f"{name}.json", {"command": command, "exitCode": code,
         "elapsedSeconds": round(time.monotonic() - started, 3)})
    if code:
        raise RuntimeError(f"{name} failed with exit {code}; retained logs in {stage}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    cache = ROOT / ".cache/vnc-qualification"
    parser.add_argument("--source", type=Path, default=cache / f"libvncserver-{REVISION}")
    parser.add_argument("--archive", type=Path, default=cache / f"libvncserver-{REVISION}.tar.gz")
    parser.add_argument("--stage", type=Path, required=True, help="New ignored output directory")
    args = parser.parse_args()
    source, archive, stage = args.source.resolve(), args.archive.resolve(), args.stage.resolve()
    # A fresh descendant of this cache cannot overwrite prior accepted artifacts.
    stage.relative_to(cache / "followup")
    stage.mkdir(parents=True, exist_ok=False)
    inputs = verify_source(source, archive)
    harness = ROOT / "tools/vnc_client_regression.c"
    authored = {str(p.relative_to(ROOT)): sha256(p) for p in (harness, Path(__file__))}
    save(stage / "inputs.json", {"revision": REVISION, "archiveSha256": ARCHIVE_SHA256,
         "source": inputs, "authored": authored})
    build = stage / "build"
    command = ["cmake", "-S", str(source), "-B", str(build), "-G", "Ninja",
               "-DBUILD_SHARED_LIBS=OFF", "-DWITH_LIBVNCSERVER=OFF",
               "-DWITH_EXAMPLES=OFF", "-DWITH_TESTS=OFF", "-DWITH_LZO=OFF",
               "-DWITH_ZLIB=ON", "-DWITH_JPEG=ON", "-DCMAKE_BUILD_TYPE=Debug",
               "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON", f"-DCMAKE_C_FLAGS={FLAGS}"]
    command += [f"-DWITH_{option}=OFF" for option in (
        "PNG", "SDL", "GTK", "QT", "XCB", "LIBSSHTUNNEL", "OPENSSL", "GNUTLS",
        "GCRYPT", "SYSTEMD", "SASL", "FFMPEG")]
    run(stage, "configure", command, 120)
    run(stage, "build", ["cmake", "--build", str(build), "--target", "vncclient",
                         "--parallel", "4"], 180)
    binary = stage / "vnc_client_regression"
    command = ["cc", "-std=c11", "-Wall", "-Wextra", "-Wpedantic", "-Werror",
               *FLAGS.split(), f"-I{source / 'include'}", f"-I{build / 'include'}",
               f"-I{source / 'src/common'}", str(harness), str(build / "libvncclient.a"),
               "-Wl,--wrap=ReadFromRFBServer", "-Wl,--wrap=WriteToRFBServer",
               "-lz", "-ljpeg", "-lpthread", "-o", str(binary)]
    run(stage, "compile", command, 60)
    env = dict(os.environ, ASAN_OPTIONS="detect_leaks=1:abort_on_error=1",
               UBSAN_OPTIONS="halt_on_error=1:print_stacktrace=1")
    save(stage / "sanitizers.json", {key: env[key] for key in ("ASAN_OPTIONS", "UBSAN_OPTIONS")})
    run(stage, "run", [str(binary)], 20, env)
    rows = [json.loads(line) for line in (stage / "run.stdout").read_text().splitlines()]
    summary = rows[-1]
    cases = rows[:-1]
    if (len(cases) != 27 or not all(x["passed"] for x in cases)
            or summary != {"summary": True, "cases": 27, "failures": 0,
                           "tightValidControls": 3, "gradientValidControls": 3,
                           "ultrazipValidControls": 3}):
        raise ValueError("unexpected or incomplete regression verdicts")
    if verify_source(source, archive) != inputs or any(
            sha256(ROOT / name) != digest for name, digest in authored.items()):
        raise ValueError("inputs changed during regression")
    artifacts = {str(p.relative_to(stage)): {"sha256": sha256(p), "bytes": p.stat().st_size}
                 for p in stage.rglob("*") if p.is_file()}
    save(stage / "result.json", {"status": "passed", "scope": "host-only-three-advisory-regression",
         "revision": REVISION, "configuration": FLAGS, "summary": summary, "artifacts": artifacts})
    print(json.dumps({"stage": str(stage), "summary": summary}))


if __name__ == "__main__":
    main()
