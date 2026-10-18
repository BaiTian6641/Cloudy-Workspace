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

## Product integration (2026-10-18)

- **Profile model:** `ConnectionProfileDraft`/`ConnectionProfile` carry one optional canonical `jumpProfileId`; the store schema is v3 (`jump_profile_id`), migrates v1/v2 rows with identity/revision preserved, validates that a hop exists in the same catalog, is SSH and has no jump of its own, rejects self/missing/chain references, and refuses deleting a referenced hop. The editor exposes a Direct/eligible-hop selector; the saved-state schema is v3 with a schema-2 restore default; favorite toggles preserve the reference.
- **Session:** Console resolves the target's current jump profile, prepares a separate coordinator handle/pin for each hop, connects the jump first, opens one `direct-tcpip` channel to the target endpoint, then connects the target over it. Both hops require their own current accepted trust pin; the jump is retired after the target on session end; a jump/direct failure never admits the target. The same transient credential is copied for the two hops and the UI states that scope.
- **Product journey:** `ee048e19-7d1f-4cc8-9714-475eca6c8884` (`jumpRequested=true`, Console `52f8e5f1…`, test `131d9198…`) passes the real Home→target Connect→jump UI assertion→password→jump auth→direct-tcpip→target auth→host-witnessed command→20,000-byte host-matched paste→exit/CLOSED→exact restore, including two trusted peer pins and removal of the seeded hop/target. Fixture topology: jump profile `127.0.0.1:18327` (owned device reverse) and target `127.0.0.1:41603` (fixture listener), same fixture host key.
- **Regression:** password journey `6f989544…` passes on the same schema-v3 build; `:platform` tests for profiles, migration, editor state and coordinator ran **58/58**.
- **Remaining:** the two hops use the same transient credentials and the same fixture server on two loopback ports (not two distinct hosts or credential sets); per-hop failure/revocation attribution across two real hosts, Q-C01 and editor interaction coverage remain. C14 stays partial.
