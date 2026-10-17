# SSH, PTY, SFTP and X11 boundary

**State:** retained SSHJ 0.41.2 candidate with real bounded implementations; adoption remains experimental and CAP-SSH is not promoted. **Delivery stages:** P0-S3/S7, P1-S3/S4, P2-S1/S2/S4/S6/S8.

## Responsibility

Own trusted SSH engine adapters, authentication, retained connection identity, bounded PTY/SFTP channels and forwarding. Keep raw SSHJ objects, credentials, cookies and terminal/file bytes out of general UI bridges. I/O stays off the UI thread with explicit ownership, cancellation and retirement. Each consuming app owns its profiles, credentials, jobs and lifecycle; there is no global credential store or permanently shared daemon.

## Implemented boundaries

- `SshHostKeyObserver` verifies a finite server-key observation through SSHJ and retires its actual socket/readers without authenticating. A saved pin still requires fresh exact verification before authentication.
- `SshAuthenticatedTransport` implements strict password admission and retained ownership. `SshReadOnlyFiles` provides bounded read-only SFTP list/read operations; writable transfer/publication, key and keyboard-interactive authentication, jumps and managed TCP forwards remain separate work.
- `openPty` allocates one real `xterm-256color` shell per retained transport. `SshOwnedPty` provides bounded copied input, coalesced resize and backpressured stdout/stderr. PTY close conservatively retires the whole transport and linked SFTP; local queue admission/write/resize is not remote consumption or acknowledgment. See the [real PTY host run](../../../qualification/results/P2-S1/2026-10-17-ssh-pty-backend.md).
- `SshX11Forwarding` implements standard `x11-req`/incoming channels, per-owner fake cookies, strict Xauthority lookup, setup-block substitution, bounded channels and retirement. The [OpenSSH/Xvfb host run](../../../qualification/results/P0-S7/2026-10-17-x11-forwarding-host.md) covers this protocol leg only; Android rendering, per-XID native windows and restricted SECURITY mode remain unqualified.

The public APIs return typed results and redacted representations. A connected transport does not prove that a remote process survived reconnect, and EOF does not prove command success. Queue ownership and stale-attachment fencing remain mandatory for renderer consumers.

## Verification

Default JVM tests exercise domain integration, codecs, bounds and finite real TCP behavior. Opt-in fixtures require an owned real server and are skipped without explicit configuration; a skip is never a pass. Run the real PTY fixture through [qualification/fixtures/ssh-pty](../../../qualification/fixtures/ssh-pty/README.md). Device transport, renderer input/IME, physical Googlebook behavior and product UI need their own evidence.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
