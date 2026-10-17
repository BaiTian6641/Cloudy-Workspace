# Real OpenSSH PTY + SFTP fixture

**Purpose:** execute `SshPtyTransportHostTest` against a real OpenSSH password-authenticated PTY shell and SFTP subsystem on one retained client connection. The fixture does not emulate SSH, a shell, output, resize, SFTP or retirement.

## Prerequisites and pinned server

The runner needs Linux user/mount namespaces, `unshare`, `mount`, `cc`, `openssl`, `ssh-keygen`, `ss`, and the extracted OpenSSH server already reviewed in [P0-S3](../../results/P0-S3/2026-10-15-ssh-host-preflight.md): Ubuntu `openssh-server 1:10.2p1-2ubuntu3.8` amd64, package SHA-256 `d14910ed045bd4a2a4c9a4eeb6b3edf08d527fb4cdee5cf527fe66b8bb364c31`.

Expected ignored paths:

```text
.cache/openssh-10.2-fixture/openssh-server_10.2p1-2ubuntu3.8_amd64.deb
.cache/openssh-10.2-fixture/root/usr/sbin/sshd
.cache/openssh-10.2-fixture/root/usr/lib/openssh/sshd-session
.cache/openssh-10.2-fixture/root/usr/lib/openssh/sshd-auth
```

If the reviewed package is present but not extracted, run `dpkg-deb -x` from that exact `.deb` into `.cache/openssh-10.2-fixture/root`; do not install it or start the system service. Verify package bytes against the hash above before use.

## Run

1. In one shell, run `qualification/fixtures/ssh-pty/bring-up.sh`. It prints a fresh ignored `fixture.properties` path and waits.
2. In another shell, set `CLOUDY_SSH_PTY_FIXTURE_ROOT` to the printed **directory**, not the properties filename, then run:

   ```bash
   ./gradlew :ssh:test \
     --tests 'io.github.baitian6641.cloudy.ssh.SshPtyTransportHostTest' \
     --offline --dependency-verification=strict --no-daemon --console=plain
   ```

   Use the same workspace `ANDROID_HOME`, `ANDROID_USER_HOME`, `GRADLE_USER_HOME`, and JDK environment as the reproducible root build when the cache is offline.
3. Stop the fixture with Ctrl+C and verify its loopback port 18164 closes. Keep run logs/properties under ignored `qualification/results/private/ssh-pty-01/`; never publish the generated password, keys or private inventory.

Each run generates a random password and fresh ECDSA host key, creates a namespace-private `/etc`, `/home`, and `/run/sshd`, and uses loopback only. It does not modify host accounts or the host `/etc`.

## Qualification boundary

The outer execution environment denies `setgroups`, and bubblewrap rejects `chroot` here. `noop-priv.c` therefore shims only `chroot`, `setgroups`, and `initgroups` inside the private namespace. This is a disclosed test fixture compatibility boundary, **not** evidence for OpenSSH privilege separation, account isolation, chroot, supplementary-group behavior, or a production server configuration. The extracted OpenSSH binary still performs the actual password verification, SSH channels, `forkpty` shell, window resize, SFTP and connection lifetime. Product/Android behavior requires separate device evidence.

Without this environment limitation, prefer an unmodified ordinary OpenSSH server with an owned disposable account and no shim; record that distinct run instead of broadening this fixture's claims.
