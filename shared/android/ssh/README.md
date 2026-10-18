# SSH, PTY, SFTP and X11 boundary

**State:** retained SSHJ 0.41.2 candidate with real bounded implementations; adoption remains experimental and CAP-SSH is not promoted. **Delivery stages:** P0-S3/S7, P1-S3/S4, P2-S1/S2/S4/S6/S8.

## Responsibility

Own trusted SSH engine adapters, authentication, retained connection identity, bounded PTY/SFTP channels and forwarding. Keep raw SSHJ objects, credentials, cookies and terminal/file bytes out of general UI bridges. I/O stays off the UI thread with explicit ownership, cancellation and retirement. Each consuming app owns its profiles, credentials, jobs and lifecycle; there is no global credential store or permanently shared daemon.

## Implemented boundaries

- `SshHostKeyObserver` verifies a finite server-key observation through SSHJ and retires its actual socket/readers without authenticating. A saved pin still requires fresh exact verification before authentication.
- `SshAuthenticatedTransport` implements strict password admission, public-key/single-prompt keyboard-interactive admission, retained ownership and verified one-hop jump chains. `SshFiles` provides bounded SFTP list/read plus exclusive-create, bounded write, typed stat, explicit-overwrite rename and file/empty-directory removal. Writes use the recoverable files path so a typed per-operation failure does not retire the transport; the [writable host run](../../../qualification/results/P1-S3/2026-10-18-sftp-writable-engine.md) covers the maintained virtual-root server. `SshFileEditor` adds two-phase staged editing: an exclusive same-directory temp, a re-stat conflict check, explicit-overwrite rename and temp discard. `SshFiles.readTo`/`writeFrom`/`digest` stream one bounded file with cancellation and SHA-256; the platform owns document-grant upload/download flows, the bounded persistent transfer journal and per-save consent. Recursive/multi-file transfer and server-side hash verification remain separate work.
- `openPty` allocates one real `xterm-256color` shell per retained transport. `SshOwnedPty` provides bounded copied input, coalesced resize and backpressured stdout/stderr. PTY close conservatively retires the whole transport and linked SFTP; local queue admission/write/resize is not remote consumption or acknowledgment. See the [real PTY host run](../../../qualification/results/P2-S1/2026-10-17-ssh-pty-backend.md).
- `SshX11Forwarding` implements standard `x11-req`/incoming channels, per-owner fake cookies, strict Xauthority lookup, setup-block substitution, bounded channels and retirement. `SshX11ClientFactory` creates the reviewed narrow-policy client without a quiet-channel read timeout for long-lived X11 traffic; `attach(..., onError)` reports channel failures without affecting termination. The [OpenSSH/Xvfb host run](../../../qualification/results/P0-S7/2026-10-17-x11-forwarding-host.md) covers the protocol leg; the [M2 device run](../../../qualification/results/P0-S7/2026-10-17-x11-broker-device.md) covers a diagnostic two-XID Android presentation with real forwarded xterms. Product rendering, restricted SECURITY mode, popup/IME and owner→window retirement remain unqualified.

The public APIs return typed results and redacted representations. A connected transport does not prove that a remote process survived reconnect, and EOF does not prove command success. Queue ownership and stale-attachment fencing remain mandatory for renderer consumers.

PTY admission accepts one whole input action up to 64 KiB. The total queued plus in-flight byte
capacity remains 64 KiB, and the queued-entry limit remains 64. A full/invalid action is rejected
without partial admission; callers must not split a paste into independently admitted chunks.
The [whole-input result](../../../qualification/results/P2-S1/2026-10-17-pty-whole-input.md) records
eight passing boundary cases (including failing-before evidence) and an exact 20,000-byte native
clipboard action independently observed on a real SSH/kernel PTY. Sustained-load, physical-input,
reconnect and full clipboard UX qualification remain separate.

## Verification

Default JVM tests exercise domain integration, codecs, bounds and finite real TCP behavior. Opt-in fixtures require an owned real server and are skipped without explicit configuration; a skip is never a pass. Run the real PTY fixture through [qualification/fixtures/ssh-pty](../../../qualification/fixtures/ssh-pty/README.md). Device transport, renderer input/IME, physical Googlebook behavior and product UI need their own evidence.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
