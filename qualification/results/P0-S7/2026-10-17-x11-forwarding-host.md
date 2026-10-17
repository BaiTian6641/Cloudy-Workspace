# P0-S7 host qualification — SSH X11 forwarding with cookie substitution

**Date:** 2026-10-17, America/Chicago. **Lane:** P0-S7 experiment packet ([broker experiment](../../../docs/03-platform/googlebook/p0-s7-x11-broker-experiment.md)), user-requested X11-forwarding research + implementation. **Scope of this run:** the SSH protocol leg only — `x11-req`, incoming `x11` channels, fake-cookie substitution, isolation and retirement — on the primary Ubuntu 26.04 host. No Android/X-server/broker claim.

## What was built

`shared/android/ssh` gains `SshX11Cookie` (RFC 4254 §6.3 fake-cookie generation/hex), `SshXauthorityCodec` (strict Xauthority parsing, local/wildcard cookie lookup), `SshX11Handshake` (X11 client setup block parse and in-place cookie substitution) and `SshX11Forwarding` (SSHJ wiring: `registerX11Forwarder` + `Session.reqX11Forwarding`, bounded channels, watchdog, confirm-then-close retirement).

## Verified upstream findings behind the implementation

- SSHJ 0.41.2's `X11Forwarder` delivers **no channel bytes before `Channel.Forwarded.confirm()`**; the listener owns confirmation (upstream `SocketForwardingConnectListener` pattern). Cloudy confirms after local connect.
- SSHJ 0.41.2's `sendOpenFailure` emits CHANNEL_OPEN_FAILURE **without the RFC 4254 §5.1 language field**; the fixture's OpenSSH 10.2 server disconnects the whole transport (`Invalid open failure message`) when such a packet arrives. Cloudy therefore never rejects x11 channels: denial is confirm-then-close. Recorded for an upstream report.
- The X11 setup request stores name length at bytes 6–7 and data length at 8–9 (bytes 10–11 unused). An earlier self-consistent-but-wrong offset pair was caught by fixture failure and locked down with hand-built reference vectors.

## Fixture (bounded, private)

`qualification/probes/x11-forward-host/bring-up.sh`: extracted OpenSSH `10.2p1-2ubuntu3.8` (the P0-S3 package, SHA-256 `d14910ed…64c31` per that record) and extracted Xvfb `2:21.1.22-1ubuntu1.2`, loopback 127.0.0.1:18163, ECDSA-only pinned algorithms matching the module policy, pubkey-only, fixture-owned Xauthority files, displays :98 (1280x800) and :99 (640x480). Run directory: `qualification/results/private/x11-forward-01/run-Snr77C/` (logs, keys, properties retained as private evidence). No system install, service or account change.

## Results

`SshX11ForwardingFixtureTest`, JVM, against the live fixture — **5/5 pass**:

| Case | Result |
| --- | --- |
| `forwardedX11ReachesTheLocalServerWithSubstitutedCookie` | Real `xdpyinfo` exit 0 through the proxy; setup cookie substituted; the fake cookie stored by the server proven equal to the owner's issued cookie |
| `twoForwardingOwnersStayIsolatedOnTheirOwnDisplays` | Two SSH connections forwarded to :98/:99; each `xdpyinfo` reported only its own geometry (1280x800 / 640x480) |
| `wrongCookieIsRejectedByTheLocalServer` | A tampered real cookie is rejected by the X server; no display opened |
| `missingAuthorityClosesTheChannelBeforeAnyLocalContact` | No local cookie → channel closed with zero local server connects |
| `stoppingTheOwnerEndsTheForwardedSession` | Owner `stop()` retires a live `xterm` channel; remote client ends with failure; counter drains |

Cross-check: the reference OpenSSH 10.2 client (`ssh -X`) against the same fixture succeeds. Full `:ssh:test` suite: **65 tests, 0 failures** (20 skips are unrelated gated fixtures).

## Same-session engine compile (packet step 1)

The pinned Termux:X11 tree (fa3a8b43, xserver 65d790bd) compiled to `libXlorie.so` for arm64-v8a under NDK r29/CMake 4.1.2/Ninja; artifact SHA-256 `632a8b933290197a2eb1228a1a60f7f2928255fe3d242d41d58016df43807a63` in ignored storage. Compile-only evidence; no load, window, or device behavior.

## Boundaries

Host-loopback evidence only. Android-side X server, per-XID window broker, input mapping, clipboard, GPU/GLX, restricted (SECURITY) forwarding and device runs remain unqualified and gate CAP-X11 (`pending`). The confirm-ordering and reject-defect findings are recorded in the [investigation report](../../../docs/03-platform/googlebook/x11-forwarding-investigation-2026-10-17.md).
