# Host core

**State:** host CLI0.1.3 supplies finite diagnostics, explicit installation identity and Linux context observation; internal owned-job and Linux/macOS peer prerequisites also exist within their recorded scopes. Production host core remains incomplete. **Delivery stages:** P4-S1 construction experiment HOST-EXP01, with stage prerequisites retained.

## Responsibility

Pairing/authenticated transport, scoped requests, registered resources, expiry/idempotency policy, capability manifests, job supervisor, bounded event retention/cursors/gaps, and redacted logs.

## Boundary

Do not implement OS capture/notification APIs directly in core or automatically replay shell/flash side effects.

## Local diagnostic

Go1.27.2. The Windows identity adapter additionally pins `golang.org/x/sys/windows` from `golang.org/x/sys v0.49.0`; The internal Linux jobs library also uses the same module's `unix` wrappers; Linux/macOS identity paths remain standard-library based. See [third-party notices](THIRD_PARTY_NOTICES.txt) and the exact module checksums in `go.sum`. From this directory, using the selected compiler:

```sh
GOTOOLCHAIN=local go run ./cmd/cloudy-host inspect
GOTOOLCHAIN=local go run ./cmd/cloudy-host version
gofmt -l .
GOTOOLCHAIN=local go vet ./...
GOTOOLCHAIN=local go test ./...
```

`inspect` prints actual runtime/target facts and UTC observation time. Unimplemented Cloudy control features report unavailable; external backends remain unknown because they were not inspected. This command does not create identities, inspect accounts/environment, open a listener, install a service or execute a job. It is not the production capability wire manifest. Require successful process exit and complete JSON; failed stdout can contain a partial record.

The [entry/toolchain record](../../docs/03-platform/host-core-entry.md) describes exact local archive verification and build commands. The [evidence](../../qualification/results/P4-S1/2026-10-16-host-baseline.md) distinguishes actual Linux CLI execution from Windows/macOS cross-builds. The new [explicit Linux context observation](../../docs/03-platform/host-context-observation.md) reads existing installation and boot IDs without initializing state. Remote transport, authenticated pairing, logon/session identity, persistent events, remote job admission and user-session adapters remain implementation work. Do not treat this diagnostic as a supported paired host service.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.


## Explicit installation self-ID

Version0.1.1 adds `identity init --state-dir <existing-directory>` and `identity read --state-dir <existing-directory>`. Use an existing app-owned directory. Init exclusively claims `installation-identity-v1`; an existing child, including incomplete/corrupt state, is never repaired or replaced. Read never creates state. This nonsecret UUIDv4 identifies installation state, not authentication, hardware, boot or logon. See the [frozen identity contract and local check limits](../../docs/03-platform/host-installation-identity.md) before use. Directory sync, output failure and cleanup uncertainty retain state; no reset/recovery command is included. Only ignored owned fixtures were used for local execution.


## Windows permanent-file profile (0.1.2)

Windows construction now uses the same `installation-identity-v1` leaf as an exclusively created permanent regular file. The actual opened trusted root must report local fixed NTFS through handle-based volume information, final GUID-volume path and drive-type queries before claim. Unknown/failed/unsupported queries refuse without claiming. Only the successful claimant generates an ID. No truncate, replacing rename, alternate namespace, migration, reset or Sync fallback exists.

The new bounded disk record has version2, storage profile `windows-ntfs-anchor-file-v1` and its canonical UUIDv4. Empty/partial/malformed anchors block another Init. Content appearance is not atomic; every later failure retains the file, and a complete read cannot certify an earlier uncertain flush. Existing directory anchors use strict read-only legacy-v1 parsing. Windows local identity output is version2 with explicit `recordVersion`/`storageProfile`; successful new Init reports `write-through-file-flush-completed`, while Read remains `not-assessed`.

Linux/macOS retain the v1 directory layout, local output version1 and every required file/directory Sync. Native Windows API/NTFS/flush/close/concurrency/reparse/process-loss cases must execute on Windows; local cross-builds do not qualify them. The supported construction precondition is an existing trusted app-owned directory without hostile same-user mutation. Local fixed NTFS is necessary, not proof for arbitrary cloud/filter paths, ACL privacy, atomic complete-record appearance or power-loss durability. This self-ID is never authentication, pairing, boot/logon identity or a supported host service.


## Internal Linux local jobs

The [bounded local-job profile](../../docs/03-platform/host-local-jobs.md) adds `internal/jobs.Start`, an explicit Job owner, observation-only Wait, cancellation and settlement. It performs actual execution in a fresh owned cgroup with bounded I/O and checked retirement; missing placement authority refuses honestly. There is no jobs CLI or service in0.1.3. See the [source and finite Linux result](../../qualification/results/P4-S1/2026-10-16-host-linux-jobs.md) before integration:32 named pure test/subtest passes and seven actual native cases pass, at its exact earlier source checkpoint. The subsequent [Windows jobs construction](../../qualification/results/P4-S1/2026-10-16-host-windows-jobs.md) and [finite native fixture](../../qualification/results/P4-S1/2026-10-16-host-windows-native-fixture.md) have separate evidence; hosted step success is not inspected raw per-case acceptance. Remote admission, crash recovery and macOS managed jobs remain open. Native tests require a reviewed parent-owned finite delegated fixture; do not enable them against an existing session or arbitrary cgroup. Ordinary test runs leave that opt-in native lane skipped, not passed.


## Explicit Linux context observation (0.1.3)

`cloudy-host context read --state-dir <existing-app-owned-directory>` calls installation Read then boot Read and emits localContextVersion1. It never initializes, repairs, generates or caches identity. Actual installation durability passes through unchanged; UTC observedAt follows both reads, and observationConsistency is `sequential-not-atomic`. Default inspect stays identifier-free. Explicit context output contains correlatable nonsecret IDs, so it belongs in its intended local diagnostic context. Require exit0 and complete JSON; output failure may leave partial stdout. Windows/Darwin return fixed unsupported before state access.

The [contract](../../docs/03-platform/host-context-observation.md) and [result](../../qualification/results/P4-S1/2026-10-16-host-context.md) record eleven checks,14 top-level/25 aggregate Linux race passes with zero skips/failures, ten actual owned-fixture CLI commands and independent/parent review. Windows/Darwin only cross-built in this increment. Trusted state/procfs, sequential observation, no authentication/session authority and unqualified reboot/container boundaries remain explicit. No new hosted CI result or P4 completion is claimed.
