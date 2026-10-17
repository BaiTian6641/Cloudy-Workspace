#!/usr/bin/env bash
# Real OpenSSH password/PTY/shell/SFTP fixture in a private user+mount namespace.
# No host account, system service, repository secret or public listener is created.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
PARENT="$ROOT/qualification/results/private/ssh-pty-01"
mkdir -p "$PARENT"
RUN=$(mktemp -d "$PARENT/run-XXXXXX")
chmod 700 "$RUN"

OPENSSH_ROOT="$ROOT/.cache/openssh-10.2-fixture/root"
SSHD="$OPENSSH_ROOT/usr/sbin/sshd"
PORT=18164
FIXTURE_USER=cloudypty
# Generated per run and written only to the ignored private fixture properties. Never echo it.
FIXTURE_PASSWORD=$(head -c24 /dev/urandom | base64 | tr -d '\n')
PASSWORD_SALT=$(head -c12 /dev/urandom | od -An -tx1 | tr -d ' \n')
PASSWORD_HASH=$(printf '%s\n' "$FIXTURE_PASSWORD" | openssl passwd -6 -salt "$PASSWORD_SALT" -stdin)

for tool in unshare mount cc openssl ssh-keygen ss; do
  command -v "$tool" >/dev/null || { echo "missing fixture tool: $tool" >&2; exit 1; }
done
[ -x "$SSHD" ] || { echo "missing extracted OpenSSH: $SSHD; see README.md" >&2; exit 1; }
if ss -ltn | grep -q "127.0.0.1:$PORT"; then
  echo "fixture port $PORT is already occupied; stop the owned prior fixture first" >&2
  exit 1
fi

mkdir -p "$RUN/etc" "$RUN/home-root/$FIXTURE_USER" "$RUN/empty"
# OpenSSH StrictModes rejects the workspace's normal group-writable umask.
chmod 700 "$RUN/empty" "$RUN/home-root" "$RUN/home-root/$FIXTURE_USER"
ssh-keygen -q -t ecdsa -b 256 -N '' -f "$RUN/host_ecdsa" -C cloudy-pty-fixture-host
HOST_FINGERPRINT=$(ssh-keygen -lf "$RUN/host_ecdsa.pub" | awk '{print $2}')
printf 'cloudy-pty-fixture-proof\n' > "$RUN/home-root/$FIXTURE_USER/proof.txt"
cc -shared -fPIC -O2 -Wall -Wextra -Werror \
  -o "$RUN/noop-priv.so" "$ROOT/qualification/fixtures/ssh-pty/noop-priv.c"

# Namespace-private accounts. Namespace uid 0 maps to the invoking unprivileged user outside;
# the host /etc and /home are never edited. All namespace users use uid 0 for this finite fixture.
printf 'root:x:0:0:root:/root:/bin/bash\nsshd:x:0:0:sshd:/var/empty:/usr/sbin/nologin\n%s:x:0:0:Cloudy PTY fixture:/home/%s:/bin/bash\n' \
  "$FIXTURE_USER" "$FIXTURE_USER" > "$RUN/etc/passwd"
printf 'root:!:20200:0:99999:7:::\nsshd:!:20200:0:99999:7:::\n%s:%s:20200:0:99999:7:::\n' \
  "$FIXTURE_USER" "$PASSWORD_HASH" > "$RUN/etc/shadow"
chmod 600 "$RUN/etc/shadow"
printf 'root:x:0:\nsshd:x:0:\n%s:x:0:\n' "$FIXTURE_USER" > "$RUN/etc/group"

NS_RUN="/mnt${RUN#$ROOT}"
NS_OPENSSH=/mnt/.cache/openssh-10.2-fixture/root
cat > "$RUN/sshd_config" <<CFG
ListenAddress 127.0.0.1
Port $PORT
AddressFamily inet
HostKey "$NS_RUN/host_ecdsa"
PidFile "$NS_RUN/sshd.pid"
UsePAM no
PasswordAuthentication yes
PubkeyAuthentication no
KbdInteractiveAuthentication no
AuthenticationMethods password
AllowUsers $FIXTURE_USER
PermitRootLogin yes
PermitTTY yes
StrictModes yes
SshdSessionPath "$NS_OPENSSH/usr/lib/openssh/sshd-session"
SshdAuthPath "$NS_OPENSSH/usr/lib/openssh/sshd-auth"
AllowAgentForwarding no
AllowTcpForwarding no
X11Forwarding no
MaxAuthTries 3
MaxStartups 3
MaxSessions 4
LoginGraceTime 15
LogLevel VERBOSE
KexAlgorithms ecdh-sha2-nistp256
HostKeyAlgorithms ecdsa-sha2-nistp256
PubkeyAcceptedAlgorithms ecdsa-sha2-nistp256
Ciphers aes128-gcm@openssh.com
MACs hmac-sha2-256
Subsystem sftp internal-sftp
CFG

# Only user/mount namespaces are unshared; loopback networking stays reachable by the parent test.
# /mnt preserves access to the extracted binaries after the namespace-private /home is mounted.
unshare -Urm --propagation private sh -c '
  set -eu
  mount --bind "$1" /mnt
  mount --bind "$2/etc" /etc
  mount --bind "$2/empty" /run/sshd
  mount --bind "$2/home-root" /home
  export HOME="/home/$3"
  # Test-only OS privilege shim; it replaces no SSH, authentication, PTY or SFTP behavior.
  export LD_PRELOAD="$6"
  exec "$4/usr/sbin/sshd" -D -e -f "$5/sshd_config"
' sh "$ROOT" "$RUN" "$FIXTURE_USER" "$NS_OPENSSH" "$NS_RUN" \
  "$NS_RUN/noop-priv.so" >"$RUN/sshd.log" 2>&1 &
SSHD_PID=$!

cleanup() {
  kill "$SSHD_PID" 2>/dev/null || true
  if [ -f "$RUN/sshd.pid" ]; then
    OWNER_PID=$(cat "$RUN/sshd.pid" 2>/dev/null || true)
    [ -z "$OWNER_PID" ] || kill "$OWNER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

for _ in $(seq 1 50); do
  [ -f "$RUN/sshd.pid" ] && kill -0 "$SSHD_PID" 2>/dev/null && break
  sleep 0.2
done
kill -0 "$SSHD_PID" || { echo "sshd died; see $RUN/sshd.log" >&2; exit 1; }
ss -ltn | grep -q "127.0.0.1:$PORT" || { echo "listener not up; see $RUN/sshd.log" >&2; exit 1; }

cat > "$RUN/fixture.properties" <<PROPS
schema=1
run=$RUN
port=$PORT
user=$FIXTURE_USER
password=$FIXTURE_PASSWORD
fingerprint=$HOST_FINGERPRINT
homeOnRemote=/home/$FIXTURE_USER
sshdPid=$SSHD_PID
PROPS
chmod 600 "$RUN/fixture.properties"
echo "$RUN/fixture.properties"
wait
