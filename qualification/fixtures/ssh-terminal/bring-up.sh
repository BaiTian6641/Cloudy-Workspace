#!/usr/bin/env bash
# Compiles and runs the bounded loopback MINA SSH terminal fixture. See README.md.
# No host account, system service/configuration, namespace, public listener or download is used.
set -euo pipefail
umask 077

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)
SOURCE="$ROOT/qualification/fixtures/ssh-terminal/src/CloudySshTerminalFixture.java"
DEPENDENCIES="$ROOT/qualification/fixtures/ssh-session/dependencies.json"
JARS="$ROOT/.cache/research-sshd-2.20.0"
BUILD_PARENT="$ROOT/.cache/ssh-terminal-fixture"
PRIVATE_PARENT="$ROOT/qualification/results/private/ssh-terminal-01"

fail() {
    printf 'ssh-terminal fixture: %s\n' "$*" >&2
    exit 1
}

for tool in javac java python3; do
    command -v "$tool" >/dev/null 2>&1 || fail "missing required tool: $tool"
done
[ -x /usr/bin/script ] && [ -x /bin/sh ] || fail "missing util-linux script or /bin/sh"
[ -f "$SOURCE" ] || fail "missing fixture source"
[ -f "$DEPENDENCIES" ] || fail "missing dependency pin record"

# Verify the four selected jars against the reviewed pin record before compiling or launching.
# Only fixed basenames are printed; no credential or private material is involved in this check.
python3 - "$DEPENDENCIES" "$JARS" <<'PY' || exit 1
import hashlib
import json
import os
import stat
import sys

dependencies, jars_dir = sys.argv[1], sys.argv[2]
selected = (
    "sshd-core-2.20.0.jar",
    "sshd-common-2.20.0.jar",
    "slf4j-api-2.0.20.jar",
    "slf4j-nop-2.0.20.jar",
)

try:
    with open(dependencies, "r", encoding="utf-8") as handle:
        record = json.load(handle)
    artifacts = {entry["file"]: entry for entry in record["artifacts"]}
except (OSError, ValueError, KeyError, TypeError):
    print("ssh-terminal fixture: dependency pin record is unreadable", file=sys.stderr)
    raise SystemExit(1)

for name in selected:
    entry = artifacts.get(name)
    if entry is None:
        print(f"ssh-terminal fixture: missing pin for {name}", file=sys.stderr)
        raise SystemExit(1)
    path = os.path.join(jars_dir, name)
    try:
        info = os.lstat(path)
    except OSError:
        print(f"ssh-terminal fixture: missing pinned jar {name}", file=sys.stderr)
        raise SystemExit(1)
    if not stat.S_ISREG(info.st_mode):
        print(f"ssh-terminal fixture: pinned jar is not a regular file {name}", file=sys.stderr)
        raise SystemExit(1)
    if info.st_size != entry.get("bytes"):
        print(f"ssh-terminal fixture: size mismatch for {name}", file=sys.stderr)
        raise SystemExit(1)
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    if digest.hexdigest() != entry.get("sha256"):
        print(f"ssh-terminal fixture: sha256 mismatch for {name}", file=sys.stderr)
        raise SystemExit(1)

print(f"verified {len(selected)} pinned jars", file=sys.stderr)
PY

mkdir -p "$BUILD_PARENT" "$PRIVATE_PARENT"
[ ! -L "$BUILD_PARENT" ] || fail "build root must not be a symlink"
[ ! -L "$PRIVATE_PARENT" ] || fail "private root must not be a symlink"
chmod 700 "$BUILD_PARENT" "$PRIVATE_PARENT"

# Fresh parent-owned directories; never delete or reuse prior build/run evidence.
BUILD=$(mktemp -d "$BUILD_PARENT/build-XXXXXX")
RUN=$(mktemp -d "$PRIVATE_PARENT/run-XXXXXX")
chmod 700 "$BUILD" "$RUN"

CP="$JARS/sshd-core-2.20.0.jar:$JARS/sshd-common-2.20.0.jar:$JARS/slf4j-api-2.0.20.jar:$JARS/slf4j-nop-2.0.20.jar"

javac -Xlint:all -Werror -cp "$CP" -d "$BUILD" "$SOURCE"

# The fixture prints the non-secret fixture.properties path plus READY port/fingerprint, writes
# credentials privately into the fresh run directory, and exits on its own deadline/stop file.
exec java -cp "$BUILD:$CP" CloudySshTerminalFixture "$RUN"
