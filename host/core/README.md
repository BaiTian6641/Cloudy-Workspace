# Host core

**State:** finite local runtime diagnostic and explicit installation identity implemented; production host core remains incomplete. **Delivery stages:** P4-S1 construction experiment HOST-EXP01, with stage prerequisites retained.

## Responsibility

Pairing/authenticated transport, scoped requests, registered resources, expiry/idempotency policy, capability manifests, job supervisor, bounded event retention/cursors/gaps, and redacted logs.

## Boundary

Do not implement OS capture/notification APIs directly in core or automatically replay shell/flash side effects.

## Local diagnostic

Go1.27.2. The Windows identity adapter additionally pins `golang.org/x/sys/windows` from `golang.org/x/sys v0.49.0`; Linux/macOS execution paths remain standard-library based. See [third-party notices](THIRD_PARTY_NOTICES.txt) and the exact module checksums in `go.sum`. From this directory, using the selected compiler:

```sh
GOTOOLCHAIN=local go run ./cmd/cloudy-host inspect
GOTOOLCHAIN=local go run ./cmd/cloudy-host version
gofmt -l .
GOTOOLCHAIN=local go vet ./...
GOTOOLCHAIN=local go test ./...
```

`inspect` prints actual runtime/target facts and UTC observation time. Unimplemented Cloudy control features report unavailable; external backends remain unknown because they were not inspected. This command does not create identities, inspect accounts/environment, open a listener, install a service or execute a job. It is not the production capability wire manifest. Require successful process exit and complete JSON; failed stdout can contain a partial record.

The [entry/toolchain record](../../docs/03-platform/host-core-entry.md) describes exact local archive verification and build commands. The [evidence](../../qualification/results/P4-S1/2026-10-16-host-baseline.md) distinguishes actual Linux CLI execution from Windows/macOS cross-builds. Remote transport, authenticated pairing, boot/logon identity, persistent events, job ownership and user-session adapters remain implementation work. Do not treat this diagnostic as a supported paired host service.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.


## Explicit installation self-ID

Version0.1.1 adds `identity init --state-dir <existing-directory>` and `identity read --state-dir <existing-directory>`. Use an existing app-owned directory. Init exclusively claims `installation-identity-v1`; an existing child, including incomplete/corrupt state, is never repaired or replaced. Read never creates state. This nonsecret UUIDv4 identifies installation state, not authentication, hardware, boot or logon. See the [frozen identity contract and local check limits](../../docs/03-platform/host-installation-identity.md) before use. Directory sync, output failure and cleanup uncertainty retain state; no reset/recovery command is included. Only ignored owned fixtures were used for local execution.


## Windows permanent-file profile (0.1.2)

Windows construction now uses the same `installation-identity-v1` leaf as an exclusively created permanent regular file. The actual opened trusted root must report local fixed NTFS through handle-based volume information, final GUID-volume path and drive-type queries before claim. Unknown/failed/unsupported queries refuse without claiming. Only the successful claimant generates an ID. No truncate, replacing rename, alternate namespace, migration, reset or Sync fallback exists.

The new bounded disk record has version2, storage profile `windows-ntfs-anchor-file-v1` and its canonical UUIDv4. Empty/partial/malformed anchors block another Init. Content appearance is not atomic; every later failure retains the file, and a complete read cannot certify an earlier uncertain flush. Existing directory anchors use strict read-only legacy-v1 parsing. Windows local identity output is version2 with explicit `recordVersion`/`storageProfile`; successful new Init reports `write-through-file-flush-completed`, while Read remains `not-assessed`.

Linux/macOS retain the v1 directory layout, local output version1 and every required file/directory Sync. Native Windows API/NTFS/flush/close/concurrency/reparse/process-loss cases must execute on Windows; local cross-builds do not qualify them. The supported construction precondition is an existing trusted app-owned directory without hostile same-user mutation. Local fixed NTFS is necessary, not proof for arbitrary cloud/filter paths, ACL privacy, atomic complete-record appearance or power-loss durability. This self-ID is never authentication, pairing, boot/logon identity or a supported host service.
