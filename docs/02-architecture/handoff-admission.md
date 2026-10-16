# Handoff caller and replay admission

P1-S3 implementation increment, 2026-10-16. Read with the [wire/runtime profile](handoff-runtime-profile.md) and [ADR0004](../adr/0004-resource-handoff-and-trust.md). These primitives enforce identity and request consumption. A reservation is not permission to access a resource, proof that a window appeared, or a working cross-app endpoint.

## Caller identity

`HandoffCaller.verifyIncoming` reads `Binder.getCallingUid()` synchronously inside the incoming transaction, before clearing identity or dispatching work. It selects the exact Console/Desktop counterpart from the receiving app's own package identity. It rejects local/self calls, unsupported receivers, receiver/process mismatch, missing or disabled packages, UID mismatch, shared-UID declarations, unexpected UID membership and signature mismatch. Package visibility failures deny admission.

The eventual explicit bound endpoint must also enforce a receiver-owned signature permission and declare exact counterpart package visibility. A signature alone does not admit unrelated apps signed with the same development key. An incoming payload, Intent extra, WorkSource value or supplied package name never selects the authenticated caller. A background worker must not re-read `Binder.getCallingUid()` after leaving the transaction and treat its own UID as the sender. Package replacement/revocation during queued work remains an integration case, requiring fresh receiver-owned validation before presentation.

## Durable request consumption

The receiving app owns the ledger under its credential-encrypted no-backup directory. It stores only canonical request UUIDs, expiry instants, a last-committed admission clock value and local store metadata. It does not store credentials, commands, hostnames or resource payloads.

The implementation uses Android SQLite, an immutable installation marker and a permanent lock file. Existing storage is opened without a create flag; incomplete initialization, missing established artifacts, malformed metadata and corruption fail closed. The custom corruption handler does not delete the database. There is no automatic reset or repair path that clears previously consumed requests. Checked file and directory synchronization protects initialization; SQLite uses DELETE journaling and EXTRA synchronization for commits. Ordinary process-restart evidence and actual power-loss durability remain different claims.

Admission runs off the main thread. A process lock and nonblocking file lock serialize instances/processes; contention returns a fixed busy result. Within one transaction, the receiver checks its own clock, envelope freshness, duplicate UUID and capacity; prunes expired entries; inserts the reservation; and advances the stored clock value. A reservation is returned only after commit succeeds. A crash after consumption and before navigation may lose that navigation; it must not trigger an automatic replay.

The production ceiling is 4,096 retained request IDs and a 2 MiB database ceiling, excluding rollback-journal/filesystem overhead. Live entries are never evicted to make room. Capacity exhaustion returns a denial. UUID comparisons use the domain model's canonical spelling. Timestamp comparisons preserve seconds and nanoseconds, including the exact expiry boundary.

If the receiver clock falls below the last committed admission value, admission returns clock rollback until the clock catches up. Pruning and clock advancement commit together, so a deleted original envelope cannot become fresh again under an accepted lower clock. The floor advances with successful reservations, not every rejected observation. A mistaken large forward clock adjustment can reduce availability; silently clearing the ledger is not a remedy.

The bounded guarantee is rejection of a consumed original envelope, including after expiry pruning and clock rollback, plus duplicate UUID rejection while its row is retained. After pruning, a sender-created envelope with that UUID and different future timestamps is not covered by an everlasting UUID ban. Counterparts must generate a fresh UUID for every new request. An indefinite global ID ban would require unbounded history or an explicit protocol epoch/sequence design. This distinction does not grant resource authority: the enrolled-resource and action checks apply independently to every request.

Whole-app data clearing/reinstallation, privileged replacement of every store artifact, and rollback of the entire app-data snapshot are outside this ledger's persistence guarantee. The app must not perform these operations as a replay-recovery shortcut. Normal app updates preserve its private data; backup policy and product lifecycle tests remain part of integration.

## Integration order and genuine destinations

The authenticated receiver must validate the bounded envelope, recipient/action compatibility and complete enrolled-resource tuple before reserving it. Immediately before any effect, recheck freshness and resource authorization, with no retry after uncertain delivery. A lookup must never create an enrollment or infer trust from a matching hostname. Keep the window, credential and resource identifiers in distinct namespaces.

Current local windows contain app-owned document metadata; vault profiles contain expected authentication metadata. Neither is an enrolled host/environment/workspace/resource registry. The product needs a separate owning-app profile/catalog and deliberate registrations, with exact tuple matching and revocation. Profile creation must remain possible without saving credentials or installing the sibling app. A saved endpoint does not establish trust, reachability or a connected session.

`open-console`, `open-desktop` and `open-files` retain their intended real destinations. A generic window, credential list, profile-details screen or empty file list must not be reported as a working terminal, remote desktop or file browser. The receiver-owned registry, signed IPC endpoint, missing-sibling behavior, positive/negative cross-app tests and actual destination presentation remain required implementation work.

## Validation boundaries

Caller utility tests use real Android Binder/PackageManager reads and establish local rejection only. They do not establish successful sibling authentication or wrong-signature IPC rejection without an actual endpoint. Replay tests exercise real Android SQLite/file operations with controlled receiver clocks; they do not change the Googlebook's system clock. Reopening an object is not process restart. The separate opt-in process test seeds a reservation, ends that process, then verifies duplicate rejection in a different process before testing pruning/clock rollback.

Keep device identities, APK/source hashes, executed case counts and cleanup evidence in the corresponding qualification result. Hosted compilation does not execute Android tests. No native design, protected credential, remote-engine or suite release gate is promoted by these primitives.

The [first actual result](../../qualification/results/P1-S3/2026-10-16-handoff-admission.md) passes19 policy/storage cases and two distinct-process phases, with the default invocation recorded as a skip. Sources G195–G204/R266 and existing G137 in the [source register](../07-reference/source-register.md) cover Binder/PackageManager identity, SQLite transactions and EXTRA synchronization, nonblocking locks, public descriptor synchronization and storage-context classification. API50/API51 record public availability, the existing minimumSDK boundary, fallback and remaining qualification work.
