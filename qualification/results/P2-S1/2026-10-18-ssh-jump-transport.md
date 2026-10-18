# Nested SSH jump transport - engine slice

**Work:** 2026-10-18. **Branch:** `codex/console-r1-implementation`. **Scope:** a verified one-hop nested transport over an authenticated direct-tcpip channel. This is not the profile jump model, editor, per-hop product credentials or the complete C14 journey.

## Implemented boundary

- `SshAuthenticatedTransport.openDirect(host, port, timeoutMillis)` opens one bounded `DirectConnection` on an established retained jump transport. It uses the same owner operation slot, deadline and `SshTransportCode` classification as PTY/SFTP; a second concurrent operation is `BUSY`.
- `SshAuthenticatedTransportFactory.connectPassword/connectPublicKey/connectKeyboardInteractive` accept an optional `DirectConnection`. The target client uses `SSHClient.connectVia(channel)` instead of a raw socket, skips raw-socket timeout preparation, records the external channel for retirement, and closes it with the target owner (the jump also closes it if the jump retires first).
- Through a direct channel, host-key verification compares the configured target `peer.host`/`peer.port` with the channel's reported remote identity, not a locally resolved numeric address.
- The tracked MINA fixture permits exactly one kind of forwarding: `direct-tcpip` to `127.0.0.1`; agent, X11, remote-listen and non-loopback connects remain refused.

## Real-server qualification

Fixture run `run-jiwXee`, loopback port `40131`, host key `SHA256:CqCWdCDa57DDPma54ulFUnnbWM+4vO2RjRJuKvV3wrg`.

```bash
CLOUDY_SSH_KEY_FIXTURE_ROOT="$PWD/qualification/results/private/ssh-terminal-01/run-jiwXee" \
  ./gradlew :ssh:test --tests io.github.baitian6641.cloudy.ssh.SshJumpTransportHostTest \
  --rerun-tasks --offline --no-daemon --console=plain
```

Executed result: **3/3 passed**.

1. The retained jump authenticates, opens one loopback direct channel, the target authenticates through it, opens a PTY, and both owners retire exactly once; the target credential retires before publication.
2. A refused jump establishment never publishes a transport and disposes its credential.
3. A wrong target credential over the jump fails as `AUTHENTICATION_FAILED`; the dependent channel closes and the jump remains closable and retires.

An earlier target cleanup returned `CLEANUP_UNCERTAIN` because the external direct channel was closed only by the target client's helper; the state now closes it explicitly during target retirement, and the rerun passes.

## Limits / next packet

- The test server reaches itself over loopback; it does not yet prove two distinct hosts, per-hop distinct credentials, or per-hop trust-store attribution.
- The profile jump reference (schema v3/editor), the Console two-hop coordinator/session, the target profile selection and a product Home→jump→target→host-witness journey remain unimplemented. C14 stays partial.
