# Host core

**State:** host CLI0.1.4 supplies finite diagnostics, explicit installation identity and Linux/macOS context observation; internal owned-job, bounded Linux job-status storage and Linux/macOS peer prerequisites also exist within their recorded scopes. Production host core remains incomplete. **Delivery stages:** P4-S1 construction experiment HOST-EXP01, with stage prerequisites retained.

## Responsibility

Pairing/authenticated transport, scoped requests, registered resources, expiry/idempotency policy, capability manifests, job supervisor, bounded event retention/cursors/gaps, and redacted logs.

## Boundary

Do not implement OS capture/notification APIs directly in core or automatically replay shell/flash side effects.

## Local diagnostic

Go1.27.2. The Windows identity adapter additionally pins `golang.org/x/sys/windows` from `golang.org/x/sys v0.49.0`; The internal Linux jobs library also uses the same module's `unix` wrappers; Linux/macOS identity paths remain standard-library based. The internal Linux job-event store additionally pins MIT-licensed `go.etcd.io/bbolt v1.5.0`. See [third-party notices](THIRD_PARTY_NOTICES.txt) and the exact module checksums in `go.sum`. From this directory, using the selected compiler:

```sh
GOTOOLCHAIN=local go run ./cmd/cloudy-host inspect
GOTOOLCHAIN=local go run ./cmd/cloudy-host version
gofmt -l .
GOTOOLCHAIN=local go vet ./...
GOTOOLCHAIN=local go test ./...
```

`inspect` prints actual runtime/target facts and UTC observation time. Unimplemented Cloudy control features report unavailable; external backends remain unknown because they were not inspected. This command does not create identities, inspect accounts/environment, open a listener, install a service or execute a job. It is not the production capability wire manifest. Require successful process exit and complete JSON; failed stdout can contain a partial record.

The [entry/toolchain record](../../docs/03-platform/host-core-entry.md) describes exact local archive verification and build commands. The [initial evidence](../../qualification/results/P4-S1/2026-10-16-host-baseline.md) distinguishes its Linux execution from other-OS cross-builds; the later [macOS context result](../../qualification/results/P4-S1/2026-10-16-host-macos-context.md) supplies actual scoped Mac execution. [Explicit local context observation](../../docs/03-platform/host-context-observation.md) reads existing installation and boot IDs without initializing state. Remote transport, authenticated pairing, logon/session identity, live event collection/delivery, remote job admission and user-session adapters remain implementation work. Do not treat this diagnostic as a supported paired host service.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.


## Explicit installation self-ID

Version0.1.1 adds `identity init --state-dir <existing-directory>` and `identity read --state-dir <existing-directory>`. Use an existing app-owned directory. Init exclusively claims `installation-identity-v1`; an existing child, including incomplete/corrupt state, is never repaired or replaced. Read never creates state. This nonsecret UUIDv4 identifies installation state, not authentication, hardware, boot or logon. See the [frozen identity contract and local check limits](../../docs/03-platform/host-installation-identity.md) before use. Directory sync, output failure and cleanup uncertainty retain state; no reset/recovery command is included. Only ignored owned fixtures were used for local execution.


## Windows permanent-file profile (0.1.2)

Windows construction now uses the same `installation-identity-v1` leaf as an exclusively created permanent regular file. The actual opened trusted root must report local fixed NTFS through handle-based volume information, final GUID-volume path and drive-type queries before claim. Unknown/failed/unsupported queries refuse without claiming. Only the successful claimant generates an ID. No truncate, replacing rename, alternate namespace, migration, reset or Sync fallback exists.

The new bounded disk record has version2, storage profile `windows-ntfs-anchor-file-v1` and its canonical UUIDv4. Empty/partial/malformed anchors block another Init. Content appearance is not atomic; every later failure retains the file, and a complete read cannot certify an earlier uncertain flush. Existing directory anchors use strict read-only legacy-v1 parsing. Windows local identity output is version2 with explicit `recordVersion`/`storageProfile`; successful new Init reports `write-through-file-flush-completed`, while Read remains `not-assessed`.

Linux/macOS retain the v1 directory layout, local output version1 and every required file/directory Sync. Native Windows API/NTFS/flush/close/concurrency/reparse/process-loss cases must execute on Windows; local cross-builds do not qualify them. The supported construction precondition is an existing trusted app-owned directory without hostile same-user mutation. Local fixed NTFS is necessary, not proof for arbitrary cloud/filter paths, ACL privacy, atomic complete-record appearance or power-loss durability. This self-ID is never authentication, pairing, boot/logon identity or a supported host service.


## Internal Linux local jobs

The [bounded local-job profile](../../docs/03-platform/host-local-jobs.md) adds `internal/jobs.Start`, an explicit Job owner, observation-only Wait, cancellation and settlement. It performs actual execution in a fresh owned cgroup with bounded I/O and checked retirement; missing placement authority refuses honestly. There is no jobs CLI or service in0.1.3. See the [source and finite Linux result](../../qualification/results/P4-S1/2026-10-16-host-linux-jobs.md) before integration:32 named pure test/subtest passes and seven actual native cases pass, at its exact earlier source checkpoint. The subsequent [Windows jobs construction](../../qualification/results/P4-S1/2026-10-16-host-windows-jobs.md) and [finite native fixture](../../qualification/results/P4-S1/2026-10-16-host-windows-native-fixture.md) have separate evidence; hosted step success is not inspected raw per-case acceptance. Remote admission, crash recovery and macOS managed jobs remain open. Native tests require a reviewed parent-owned finite delegated fixture; do not enable them against an existing session or arbitrary cgroup. Ordinary test runs leave that opt-in native lane skipped, not passed.


## Explicit Linux/macOS context observation (0.1.4)

`cloudy-host context read --state-dir <existing-app-owned-directory>` calls installation Read then boot Read and emits localContextVersion1. It never initializes, repairs, generates or caches identity. Actual installation durability passes through unchanged; UTC observedAt follows both reads, and observationConsistency is `sequential-not-atomic`. Default inspect stays identifier-free. Explicit context output contains correlatable nonsecret IDs, so it belongs in its intended local diagnostic context. Require exit0 and complete JSON; output failure may leave partial stdout. Windows and other unsupported targets return fixed unsupported before state access. Darwin shares the same composition in0.1.4 and uses its qualified native boot reader.

The [contract](../../docs/03-platform/host-context-observation.md) and [historical0.1.3 result](../../qualification/results/P4-S1/2026-10-16-host-context.md) retain their original Linux scope. The separate [0.1.4 Mac result](../../qualification/results/P4-S1/2026-10-16-host-macos-context.md) records affected compiler checks, five Linux race methods and an actual Mac run with nine top-level tests/two subcases plus nine CLI cases, followed by verified archive and exact temporary cleanup. Trusted state/platform boot source, sequential observation, no authentication/session authority and unqualified reboot/container boundaries remain explicit. No P4 completion follows.


## Internal Linux job-status event store

`internal/jobevents` now performs explicit Init/Open, atomic bounded status append, latest-state snapshots, cursor resume, lookup and checked Close through bbolt v1.5.0. It retains 256 events and at most 64 jobs in a database capped at 16 MiB. Missing or incomplete established state is never initialized or repaired. Commit uncertainty poisons further mutation; exact retries and lookup never invent a new durability assurance. The finite retained/latest identity horizon requires caller lifetime-unique event IDs beyond it.

The [contract](../../docs/02-architecture/host-job-event-store.md) and [result](../../qualification/results/P4-S1/2026-10-16-host-job-event-store.md) bind parent/independent accepted source02, five checks, 11 top-level / 18 aggregate Linux race passes and two actual finite helper processes. Snapshots in storage tests are synthetic observations; no managed jobs are launched by this increment. Injected uncertainty checks are not power-loss or real OS sync-failure proof. Plaintext IDs/status remain correlatable; trusted-state and read-only-preflight/writable-reopen limitations apply. There is no jobs recorder CLI, live collector, remote event transport, consumer-ack store, service or Windows/Darwin event-store backend. Earlier hosted CI results do not certify this new source.


## Internal Linux job observation recorder

`internal/jobrecorder` now captures one actual borrowed Job.Wait or explicit Job.Settle result with one UTC observation timestamp, then appends the immutable whitelisted status and caller identity through the real event store. CaptureSettle may continue cleanup; observation context cancellation does not cancel the Job. Caller retains Job ownership until settlement and closes Store separately. Exact AppendTo retries do not reobserve, retimestamp, regenerate IDs or replay execution. Outputs and raw failure pointers are never retained.

The [contract](../../docs/02-architecture/host-job-recorder.md) and [result](../../qualification/results/P4-S1/2026-10-16-host-job-recorder.md) bind five passing race-test methods using synthetic observer seams with real temporary databases, plus parent/independent review and exact Flash code provenance. The later [native recorder result](../../qualification/results/P4-S1/2026-10-16-host-job-recorder-native.md) passes two actual public Wait/Settle managed-job cases plus the top harness, with source/binary/gate correlation, real append/exact retry/history and observed child/outer-unit retirement. This count is separate from the five earlier seam tests. Native database reopen is not claimed. Pure uncertainty-flag classification remains distinct from OS fault evidence. No recorder CLI, service, live collector, remote admission or wire integration is provided; the recorder increment itself retained CLI0.1.3; current CLI0.1.4 context support is recorded above.
