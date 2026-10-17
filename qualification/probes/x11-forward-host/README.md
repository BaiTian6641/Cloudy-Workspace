# x11-forward-host — bounded private X11 forwarding fixture

**Purpose:** qualify Cloudy's `SshX11Forwarding` (shared/android/ssh) against a real OpenSSH
server and a real X server on the primary Ubuntu host, with no system installation or service
change.

**What `bring-up.sh` does:** creates a fresh run directory under
`qualification/results/private/x11-forward-01/` (gitignored private evidence), generates fixture
ECDSA host/user keys, starts the extracted Xvfb on displays `:98` (1280x800) and `:99` (640x480)
with a fixture-only cookie file, starts the extracted OpenSSH 10.2 server loopback-only on port
18163 with X11Forwarding on and `AcceptEnv XAUTHORITY`, then prints the fixture properties path
and waits. Teardown (Ctrl+C) kills both servers and the pid-file listener.

**Run the qualification:** `CLOUDY_X11_FIXTURE=<printed path> ./gradlew :ssh:test --tests
"io.github.baitian6641.cloudy.ssh.SshX11ForwardingFixtureTest" --rerun-tasks`

**Boundaries:** never installs packages, never touches the system sshd/`~/.Xauthority`, never
binds beyond 127.0.0.1, refuses to disturb a live X server holding its display locks. The fixture
proves protocol behavior only; it is not product evidence of Android rendering.
