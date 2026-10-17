#!/usr/bin/env bash
# Bounded private X11-forwarding fixture: an extracted OpenSSH server (never installed) and an
# extracted Xvfb, both scoped to one fresh run directory, loopback-only, key-only, torn down by
# kill signals. No system service, account, route or repository change. See README.md.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
PARENT="$ROOT/qualification/results/private/x11-forward-01"
mkdir -p "$PARENT"
RUN=$(mktemp -d "$PARENT/run-XXXXXX")
chmod 700 "$RUN"

SSHD="$ROOT/.cache/openssh-10.2-fixture/root/usr/sbin/sshd"
OPENSSH_LIB="$ROOT/.cache/openssh-10.2-fixture/root/usr/lib/openssh"
XVFB="$ROOT/.cache/x11-forward-01/xvfb-root/usr/bin/Xvfb"
PORT=18163
DISPLAY_NUM=98
DISPLAY_NUM2=99
USER_NAME=$(id -un)
HOSTNAME=$(hostname)

for binary in "$SSHD" "$XVFB" /usr/bin/xauth /usr/bin/xdpyinfo; do
  [ -x "$binary" ] || { echo "missing fixture binary: $binary" >&2; exit 1; }
done

ssh-keygen -q -t ecdsa -b 256 -N '' -f "$RUN/host_ecdsa" -C cloudy-x11-fixture-host
ssh-keygen -q -t ecdsa -b 256 -N '' -f "$RUN/user_ecdsa" -C cloudy-x11-fixture-user
cp "$RUN/user_ecdsa.pub" "$RUN/authorized_keys"
HOST_FINGERPRINT=$(ssh-keygen -lf "$RUN/host_ecdsa.pub" | awk '{print $2}')

# Clean only this fixture's display numbers when their owning process is gone; a live server
# (pid alive in the lock file) aborts instead of disturbing anyone.
for D in $DISPLAY_NUM $DISPLAY_NUM2; do
  LOCK="/tmp/.X$D-lock"
  if [ -f "$LOCK" ]; then
    LOCKPID=$(cat "$LOCK" 2>/dev/null | tr -d ' ')
    if [ -n "$LOCKPID" ] && kill -0 "$LOCKPID" 2>/dev/null; then
      echo "display :$D is held by live pid $LOCKPID; refusing to disturb it" >&2; exit 1
    fi
    rm -f "$LOCK" "/tmp/.X11-unix/X$D"
  fi
done

# The local X server carries only this fixture's cookie; the remote side starts empty and
# receives sshd's fake cookie, proving which cookie actually crossed the channel.
COOKIE=$(head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
COOKIE2=$(head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
xauth -f "$RUN/xauthority-local" add ":$DISPLAY_NUM" MIT-MAGIC-COOKIE-1 "$COOKIE"
xauth -f "$RUN/xauthority-local" add ":$DISPLAY_NUM2" MIT-MAGIC-COOKIE-1 "$COOKIE2"
: > "$RUN/xauthority-remote"

"$XVFB" ":$DISPLAY_NUM" -auth "$RUN/xauthority-local" -screen 0 1280x800x24 -nolisten tcp \
  >"$RUN/xvfb.log" 2>&1 &
XVFB_PID=$!
"$XVFB" ":$DISPLAY_NUM2" -auth "$RUN/xauthority-local" -screen 0 640x480x24 -nolisten tcp \
  >"$RUN/xvfb2.log" 2>&1 &
XVFB2_PID=$!

cat > "$RUN/sshd_config" <<CFG
ListenAddress 127.0.0.1
Port $PORT
AddressFamily inet
HostKey "$RUN/host_ecdsa"
PidFile "$RUN/sshd.pid"
AuthorizedKeysFile "$RUN/authorized_keys"
SshdSessionPath "$OPENSSH_LIB/sshd-session"
SshdAuthPath "$OPENSSH_LIB/sshd-auth"
AllowUsers $USER_NAME
UsePAM no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
PermitRootLogin no
PermitUserEnvironment no
PermitUserRC no
StrictModes yes
AllowAgentForwarding no
AllowTcpForwarding no
X11Forwarding yes
X11UseLocalhost yes
XAuthLocation /usr/bin/xauth
AcceptEnv XAUTHORITY
PermitTTY no
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
CFG

"$SSHD" -D -e -f "$RUN/sshd_config" >"$RUN/sshd.log" 2>&1 &
SSHD_PID=$!

cleanup() {
  kill "$SSHD_PID" "$XVFB_PID" "$XVFB2_PID" 2>/dev/null || true
  # sshd re-execs per connection; the pid file names the surviving listener.
  [ -f "$RUN/sshd.pid" ] && kill "$(cat "$RUN/sshd.pid")" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 50); do
  [ -S "/tmp/.X11-unix/X$DISPLAY_NUM" ] && [ -S "/tmp/.X11-unix/X$DISPLAY_NUM2" ] && \
    kill -0 "$SSHD_PID" 2>/dev/null && [ -f "$RUN/sshd.pid" ] && break
  sleep 0.2
done
[ -S "/tmp/.X11-unix/X$DISPLAY_NUM" ] && [ -S "/tmp/.X11-unix/X$DISPLAY_NUM2" ] || \
  { echo "Xvfb sockets not ready" >&2; exit 1; }
kill -0 "$SSHD_PID" || { echo "sshd died; see $RUN/sshd.log" >&2; exit 1; }
kill -0 "$XVFB2_PID" || { echo "second Xvfb died; see $RUN/xvfb2.log" >&2; exit 1; }

cat > "$RUN/fixture.properties" <<PROPS
schema=1
run=$RUN
host=127.0.0.1
port=$PORT
user=$USER_NAME
keyPath=$RUN/user_ecdsa
hostKeyFingerprint=$HOST_FINGERPRINT
display=$DISPLAY_NUM
xauthorityLocal=$RUN/xauthority-local
xauthorityRemote=$RUN/xauthority-remote
localSocket=/tmp/.X11-unix/X$DISPLAY_NUM
display2=$DISPLAY_NUM2
localSocket2=/tmp/.X11-unix/X$DISPLAY_NUM2
hostname=$HOSTNAME
sshdPid=$SSHD_PID
xvfbPid=$XVFB_PID
PROPS
echo "$RUN/fixture.properties"
# Keep the fixture up for the caller's qualification run; teardown: kill the two pids.
wait
