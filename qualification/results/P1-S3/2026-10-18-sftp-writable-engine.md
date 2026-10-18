# Writable SFTP engine on a maintained virtual-root server

**Date/stage:** 2026-10-18, M4/P2-S7 engine slice. **Outcome:** scoped host PASS. This is not a
product file-action, transfer-journal, staged-edit, publication-consent, integrity or Googlebook
result, and it does not complete C18/Q-C09. The maintained fixture is a construction server; its
SFTP root is private and disposable.

## Implemented boundary

`SshFiles` (renamed from `SshReadOnlyFiles`) keeps the bounded list/read methods and adds:

- `stat(path)` returning kind, size and modification seconds without following symlinks.
- `mkdir(path)` with an explicit `ALREADY_EXISTS` pre-check.
- `writeNew(path, bytes)` using an exclusive-create open (`CREAT|WRITE|EXCL`), a 1 MiB maximum,
  32 KiB chunk writes, zero-wiped internal copy, and best-effort removal of a partially created
  file. A failed cleanup is reported as `CLEANUP_UNCERTAIN` rather than a success or generic
  failure.
- `rename(from, to, overwrite)` with an explicit destination pre-check when `overwrite=false` and
  `RenameFlags.OVERWRITE` when true. This is not an atomic compare-and-replace; races remain and
  publication consent belongs to the product UI.
- `removeFile`/`removeEmptyDirectory` with kind checks and typed `NOT_FOUND`/`INVALID_INPUT`/
  `OPERATION_FAILED` results.

Writes use the new `SshAuthenticatedTransport.withFilesRecoverable` path. A typed per-operation
failure returns the result without stopping the retained transport; the read path still stops the
transport on failure. Close remains the only path that retires the whole transport. No journal,
staging area, resumable transfer, checksum, upload/download picker, editor or product action was
added.

## Fixture construction

The tracked [MINA terminal fixture](../../../qualification/fixtures/ssh-terminal/README.md) now
adds the pinned `sshd-sftp-2.20.0.jar` (size 394207, SHA-256
`70ce2e0e35b1106c4371471fb93313e67e0ceab8f127bc09432e115f67a5089e`) and installs a real
`SftpSubsystemFactory`. Its `VirtualFileSystemFactory` root is `<run>/sftp-root`, created inside
the already-validated 0700 run directory, so SFTP `/` never maps to the host filesystem.

The first run (`run-6D4aUK`, port 46345) exposed a construction defect: the subsystem was added
but the filesystem factory was not actually installed, so the test listed the host `/` and
`mkdir("/cloudy-write")` returned `OPERATION_FAILED` from the host permission denial. No product
mutation occurred outside a private temp path. The tracked source now installs the virtual root
and a fresh fixture (`run-ztiTv6`, port 44711) was used for the passing run. The failure is
preserved here rather than relabelled as a server pass.

## Actual host execution

Command (Gradle offline, JDK 21, explicit fixture root):

```bash
CLOUDY_SSH_KEY_FIXTURE_ROOT="$PWD/qualification/results/private/ssh-terminal-01/run-ztiTv6" \
  ./gradlew :ssh:test --tests "io.github.baitian6641.cloudy.ssh.SshWritableFilesHostTest" \
  --rerun-tasks --offline --no-daemon --console=plain
```

Result: `BUILD SUCCESSFUL`, test report `tests="2" skipped="0" failures="0" errors="0"`.

| Case | Observed result |
| --- | --- |
| Real virtual-root listing and mkdir | Empty SFTP `/`; `mkdir("/cloudy-write")` succeeds; a second mkdir returns `ALREADY_EXISTS`; missing `stat`/`removeFile` return `NOT_FOUND`; `stat("/")` still succeeds |
| Exclusive create and readback | `writeNew` succeeds; exact UTF-8 bytes read back; second `writeNew` on the same path returns `ALREADY_EXISTS` and leaves the original bytes unchanged |
| Rename semantics | `rename` without overwrite succeeds; a non-overwrite collision returns `ALREADY_EXISTS`; explicit `overwrite=true` replaces the destination; bytes reflect the rename |
| Removal semantics | `removeFile` on a directory returns `INVALID_INPUT`; removing a non-empty directory returns recoverable `OPERATION_FAILED`; file and empty-directory removal succeed; transport remains usable |
| Bounds and invalid paths | Empty and oversize writes (1 MiB + 1) return `INVALID_INPUT`; embedded newline and empty mkdir paths return `INVALID_INPUT`; no mutation |
| Retirement | Each case closes with `SshTransportCloseResult.Retired` within 3 s |

Fixture properties, keys and test bytes remain private under the ignored
`qualification/results/private/` root. The fixture process is stopped after the run; its exact
run directory remains for local inspection only.

## Remaining work and next action

The next product task is the session-linked Files UI action set (new folder, rename with typed
overwrite confirmation, delete of file/empty directory, transfer status) plus the shared transfer
journal and staged edit. Upload/download require a URI-grant-aware copy path, not this bounded
one-shot API. Q-C09, D05/D06/D09 consent and real-device presentation remain unqualified; do not
infer them from these host cases.
