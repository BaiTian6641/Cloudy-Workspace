# Finite retained SSH/SFTP host fixture

This is host-qualification source, **not a production server, adopted backend or passing test result**. It uses maintained Apache MINA SSHD2.20.0 core/common/**sshd-sftp**, upstream commit `2221febd396cd3daafdfbf2ff982c6d0eb69a203`, and the existing SLF4J API/NOP2.0.20 closure. The parent owns dependency verification, strict compilation, execution and evidence. The password-only fixture remains unchanged. No system SSH daemon, account, PAM setting or personal credential is used.

## Invocation and private filesystem

Invoke the compiled `CloudySshSessionFixture` with three arguments:

```text
CloudySshSessionFixture <fresh-output-directory> <existing-synthetic-root> <case>
```

`case` is exactly `basic`, `same-key`, `changed-key` or `hold-read`. Output and synthetic root must be disjoint (neither may contain the other). The root must be a real existing directory with mode0700; ancestor aliases are rejected by real-path comparison. The fixture checks a maximum128 tree entries, depth4,1MiB per regular file and4MiB total. Symlinks, special files and multiply linked regular files are rejected. The parent must keep this dedicated tree unchanged throughout execution; this fixture is not a sandbox against another process concurrently replacing that tree.

The parent provisions known non-secret bytes, for example `hello.txt` containing exactly UTF-8 `Cloudy retained SFTP\n`, plus `overflowdir/a`, `overflowdir/b` and `overflowdir/c`. The fixture does not create or overwrite root content. Enumeration and read responses come from the actual filesystem through MINA; expected bytes and client bounds must be asserted by the caller. Reading may update filesystem access time; no host metadata-preservation claim is made.

The output leaf is created fresh0700; `fixture.properties`, `events.jsonl`, `path-events.jsonl` and atomic `summary.json`/`path-summary.json` are0600. Properties keys are `port`, `fingerprint`, `replacementFingerprint`, `password`, `user`, `case`. Host address is implicitly127.0.0.1. The host key and independent replacement key are fresh in-memory P256 keys; the password is generated from32 random bytes. Properties contain generated fixture credentials and must remain private. Only `READY port=... fingerprint=...` is printed; generic failures contain no path, credential or exception transcript. Library logging must resolve to NOP or startup refuses. Java immutable password/properties Strings are not securely erased; this is not whole-JVM zeroization.

## Real protocol and read-only boundary

The server binds loopback port0 only. The selected policy remains ECDH nistp256, ECDSA nistp256, AES128-GCM, HMAC-SHA256 and no compression. Each session can submit one password attempt; a change request is refused. `basic` supports repeated independent authenticated sessions (for the parent's bounded cases); `hold-read` also supports repeated sessions. Rekey cases accept only one authentication across their invocation. All modes cap total sessions at16.

`VirtualFileSystemFactory` supplies the rooted filesystem and the actual `SftpSubsystemFactory` handles SFTP. The pre-processing event hook admits only INIT, OPEN, CLOSE, READ, OPENDIR, READDIR, STAT, LSTAT, FSTAT and REALPATH. Every extension and other request is refused. OPEN separately rejects non-READ options and non-regular/symlink targets. Root traversal is handled by the maintained virtual filesystem; the accessor also rejects symlink components on file open. No WRITE, create, remove, rename, link, attribute change, lock or extension operation is admitted. The root is dedicated synthetic content, never the output directory.

Shell and exec factories are absent. Forwarding and global request handlers are rejected. Only ordinary session channels are available, at most four open requests per connection. SFTP handles are capped at16 per subsystem; read data requests at64KiB, directory response data at16KiB, and cumulative requested read data at8MiB per session. The small root separately bounds enumeration. This intentionally narrow fixture does not establish general SFTP interoperability or upstream conformance.

## Parent control

The parent owns stdin and writes one ASCII command followed by LF. Commands are at most32 bytes, with at most16 commands per invocation. Main polls available bytes; there is no permanent blocking control-reader thread. No secrets travel in this protocol. Unsupported/malformed commands fail the fixture rather than fabricate a successful action.

- `rekey-same`: only in `same-key`, after actual authentication. Set the session-local provider to the original key, then call public `reExchangeKeys()` once.
- `rekey-changed`: only in `changed-key`, after actual authentication. Set the session-local provider to the independent replacement key, then call public `reExchangeKeys()` once.
- `release-read`: only in `hold-read`, release the current authenticated session's entered hold. Other connections must not overlap this control; use sequential cases.
- `shutdown`: release holds and close the server and supplied SFTP workers.

A relay may atomically publish/observe its own fixed request file **outside the SFTP root**, then forward the approved command to stdin. The fixture does not read that request file.

The first actual READ per session in `hold-read` enters a real pre-read listener on the owned subsystem worker. It waits for release, actual session closure or10seconds, then either allows the maintained filesystem read or throws a fixed failure. NIO, authentication and KEX processing remain unblocked. Cancel/timeout tests must witness `read_hold_entered` before acting, and require actual peer/session/worker retirement; receiving the command alone establishes none of those results.

## Evidence events and limits

All events contain a fixed event name, integer `session` correlation ID and fixed `mode` (case or `unknown`). Authentication request metadata includes only allowlisted method and password-change Boolean. No username, password, server text, remote/local path, file data, proposals or key payload is recorded. Logs cap at2048 events; overflow or I/O failure makes the fixture unsuccessful.

Relevant names:

- `session_created`, `session_authenticated`, `session_closed`, `auth_request`, `auth_accepted`, `auth_denied`, `change_handler`, `channel_open`.
- `server_initial_kexinit_written`/`server_initial_kexinit_write_failed`, `server_rekey_kexinit_written`/`server_rekey_kexinit_write_failed`.
- `client_initial_kexinit_negotiation_start`, `client_rekey_kexinit_negotiation_start`, `key_established`.
- `same_key_rekey_requested`, `changed_key_rekey_requested`, `rekey_future_completed`, `rekey_future_failed`.
- `sftp_<operation>_request`, `sftp_initialized`, `sftp_destroying`, `sftp_mutation_denied`, `sftp_extension_denied`, `read_completed`, `read_failed`.
- `read_hold_entered`, `read_hold_released`, `read_hold_retired`, `release_requested`, `shutdown_requested`.

KEX request receipt, actual outgoing write completion, parsed client negotiation and exchange completion remain distinct. The public reserved-message hook copies the already encoded plaintext payload before maintained `writePacket`: MINA computes its original exchange-hash input after the hook, whereas writing mutates the supplied buffer. It returns the same actual `IoWriteFuture`, preventing duplicate fallback delivery. Write completion is not proof of peer reception. Negotiation callback counts only parsed proposals reaching negotiation, not raw/malformed ingress. `rekey_future_completed` means a completed future without a reported exception; require `key_established` and subsequent real I/O for a successful same-key test. A changed-key rejection must be demonstrated by the actual client verifier/result and closure, not inferred from fixture control or absence of a completion event.

The main lifetime is at most175seconds before closure, with a180second absolute hard watchdog. Normal/shutdown-hook closure releases holds, awaits the actual server close future and explicitly shuts down/awaits every supplied subsystem executor within a shared four-second budget. Final summary records `serverClosed`, `workersRetired`, `ioFailed`, `overflow` and counts. Hard stop exits124 and cannot certify cleanup; parent must verify process/listener termination and preserve uncertainty. Immediate process kill, a quiet interval or server close alone is not proof of client retirement or idle security.

## Pinned upstream basis

- [SFTP event listener](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/SftpEventListener.java): `received` precedes processing; `reading` precedes actual file read.
- [SFTP accessor](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/SftpFileSystemAccessor.java), [factory](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/SftpSubsystemFactory.java) and [subsystem](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-sftp/src/main/java/org/apache/sshd/sftp/server/SftpSubsystem.java): maintained file/channel/protocol operations and explicit executor provider.
- [Virtual filesystem](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-common/src/main/java/org/apache/sshd/common/file/virtualfs/VirtualFileSystemFactory.java).
- [AbstractServerSession](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-core/src/main/java/org/apache/sshd/server/session/AbstractServerSession.java): public session-local `setKeyPairProvider`; `getHostKey` reloads the effective provider. [DHGServer](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-core/src/main/java/org/apache/sshd/server/kex/DHGServer.java) requests that key while handling each real exchange. Source inspection supports this changed-key construction; execution must still prove the client sees it.
- [Reserved KEX hook](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-core/src/main/java/org/apache/sshd/common/session/ReservedSessionMessagesHandler.java) and [session events](https://github.com/apache/mina-sshd/blob/2221febd396cd3daafdfbf2ff982c6d0eb69a203/sshd-core/src/main/java/org/apache/sshd/common/session/SessionListener.java).

No build, listener, host test or Android device result is claimed by this source entry. These controlled maintained-server tests will remain separate from OpenSSH, installed-product, LAN/hostname, physical-input, saved-vault and complete SSH/SFTP product qualification.

The [2026-10-16 qualification](../../results/P1-S3/2026-10-16-ssh-retained-session.md) now records actual host and lower-level Android execution separately from this fixture contract.

## Path observer compilation and bounded evidence

Compile the helper alongside main; old main-only recipes are insufficient. With the five verified locked JARs already available in `$FIXTURE_JARS`, use JDK21:

```bash
fixture_cp="$FIXTURE_JARS/sshd-core-2.20.0.jar:$FIXTURE_JARS/sshd-common-2.20.0.jar:$FIXTURE_JARS/sshd-sftp-2.20.0.jar:$FIXTURE_JARS/slf4j-api-2.0.20.jar:$FIXTURE_JARS/slf4j-nop-2.0.20.jar"
javac -Xlint:all -Werror -cp "$fixture_cp" -d "$FIXTURE_CLASSES" \
  qualification/fixtures/ssh-session/src/CloudySftpPathObservation.java \
  qualification/fixtures/ssh-session/src/CloudySshSessionFixture.java \
  qualification/fixtures/ssh-session/src/CloudySftpPathObservationTest.java
java -cp "$FIXTURE_CLASSES:$fixture_cp" CloudySftpPathObservationTest
```

`FIXTURE_CLASSES` is a parent-owned fresh build directory. The pure main runs14 mapping/outcome assertions; it starts no fixture. Exact source/build hashes and the executed compile result are in the [2026-10-16 observer result](../../results/P1-S3/2026-10-16-handoff-path-observer.md). This compile result does not qualify listener/authentication/callback/runtime behavior.

The separate append-only `path-events.jsonl` journal caps at512 records and shares existing session correlation IDs. It emits only schema1, monotonic sequence, session and finite operation (`directory-list`/`file-read`), pathClass (`nested-directory`/`nested-file`/`other`) and completion literals. Absolute rooted paths must match the trusted synthetic root; exact normalized virtual `/nested` and `/nested/nested.txt` are classified, while other paths remain `other`. No raw path, entry name, handle, file bytes, credentials or exception text is logged. This classification does not change access authorization.

Public directory callbacks record requested/produced pages; file callbacks record requested reads and completed/empty/eof/failed outcomes. Completion establishes subsystem production, not client reception or full listing. Forced journal writes and atomic private `path-summary.json` count/overflow/ioFailed accompany the unchanged original event journal/summary. Overflow or I/O failure also fails overall fixture status. Parent acceptance must demand both clean final summaries, contiguous sequence/count agreement, expected measured-session classes/completions and corresponding original authentication/closure evidence; missing or inconsistent evidence is not success. Actual callback paths, journal faults/overflow and host/client handoff remain separately qualified runtime work.
