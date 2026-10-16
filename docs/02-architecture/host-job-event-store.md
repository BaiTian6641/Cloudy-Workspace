# Local Linux job-status event store

HOST-EVENTSTORE01 is an implemented, bounded internal Linux storage library in `host/core/internal/jobevents`. Parent source review and independent review accepted repaired source02. It records caller-supplied observations through real bbolt transactions; it does not collect running jobs or expose a service, CLI, transport or adopted wire protocol. Host CLI version remains 0.1.3. P4 dependencies and capability gates remain unchanged.

## Ownership and persistent state

`Init(dir, installationID)` exclusively claims the fixed `job-events-v1` child of an existing trusted app-owned directory. `Open` opens established state only. Existing incomplete, empty or corrupt anchors are never repaired, removed or initialized. A failed first creation retains its claim. Parent and child directory sync failures remain explicit uncertainty. Any nonnil returned Store remains owned by the caller, including when accompanied by an error, and requires `Close`.

The fixed `events.db` file requests mode 0600. Root confinement, observed/opened file identity checks and no-follow/nonblocking opens reject observed symlinks, nonregular files, missing established files, empty established files and files larger than 16 MiB before mapping. The caller trusts the parent directory and excludes hostile same-user mutation; this is not a security boundary against that actor.

Existing Open validates through a read-only engine first, closes it, then reacquires writable ownership and revalidates. Writable bbolt Open can convert freelist metadata before application revalidation. Therefore a mismatch after that transition is not promised physically read-only. No engine-sync weakening or automatic reset is provided. The 250 ms lock timeout bounds lock acquisition, not every transaction or filesystem operation.

## Append and bounded identity

`AppendJobStatus` accepts explicit event/job UUIDs, typed boot ID, increasing observation revision, UTC-capable occurrence time and a jobs Snapshot. One transaction assigns sequence and updates event history, ID index, latest-job catalog, metadata and pruning. The stream UUID persists across reopen and boot observations. The store binds its installation ID; neither identity is authentication or proof of a global host/container identity.

The profile retains 256 events and at most 64 tracked jobs; a 65th job refuses rather than silently evicting its latest state. Sequences and revisions are bounded by 2^53−1. Records are canonical bounded JSON, with a maximum 4,096-byte stored record and 512-byte metadata. The physical database cap is 16 MiB; logical pruning does not promise indefinite capacity or automatic compaction.

An exact repeat of the latest observation returns its original sequence with `AlreadyObserved` and durability `not-reassessed`, without committing another event. Canonical comparison includes original observation fields; callers must preserve the event identity, boot and time when retrying. Older revisions refuse without inventing a receipt. Retained IDs and every latest row detect conflicting reuse. Beyond that finite horizon, lifetime-unique event IDs remain a caller obligation. `Lookup` absence cannot prove an uncertain transaction never committed.

A fresh receipt reports `sync-completed` only after the requested engine sync path succeeds; this is not a power-loss guarantee. A known prevalidation/transaction callback rejection does not advance sequence. An engine mutation error returns fixed `commit-unknown`, no receipt, and poisons further mutation on that handle. Reads or a later lookup do not erase the original uncertainty. No automatic retry or process replay occurs.

## Resume and safe observation

`ReadAfter` returns copied records in batches of 1 through 64. Wrong stream, an ahead cursor or a cursor older than the retained floor returns an explicit reset/gap reason, latest catalog snapshot and head cursor from one read transaction. The snapshot describes this recorder's bounded catalog, not all host processes. It does not manufacture a `history.gap` event. Consumers must persist accepted data or snapshot before advancing their cursor; consumer acknowledgement persistence and wire delivery are future integration.

Stored payloads contain only observation revision, outcome, start resolution, started/reaped flags, retirement and I/O completion, with exit code/signal only when reaped. Output bytes, arguments, working directory, environment, input, credentials and raw failure strings are excluded. IDs and status remain plaintext correlatable local metadata; the database is neither an encrypted vault nor an authorization mechanism. Outcome alone never proves reaping, retirement or complete I/O.

Operations and Close serialize per handle. Close attempts all owned engine/root closures; its first failure remains sticky on repeated Close. Public failures use fixed safe text without raw-error unwrapping. No operation may silently relabel uncertainty as successful closure or durability.

## Reuse and evidence

The selected dependency is MIT-licensed [bbolt v1.5.0](https://github.com/etcd-io/bbolt/releases/tag/v1.5.0), pinned to commit `e7a8b2dd498494a3766ba24dd94d3509e5588485`, alongside existing Go 1.27.2 and x/sys v0.49.0. Its release publication on June 21, 2026 is distinct from documentation publication dates. The [pinned Open implementation](https://github.com/etcd-io/bbolt/blob/e7a8b2dd498494a3766ba24dd94d3509e5588485/db.go) and [transaction implementation](https://github.com/etcd-io/bbolt/blob/e7a8b2dd498494a3766ba24dd94d3509e5588485/tx.go) control the engine semantics. The selected default logger is upstream's discard logger; operational errors still propagate through safe local failures. Exact checksums and full notice are in the host module and notices.

The [qualification result](../../qualification/results/P4-S1/2026-10-16-host-job-event-store.md) binds repaired source, five checks, 11 top-level / 18 aggregate race-test passes, two actual finite helper processes and independent review. Windows/Darwin storage, live job collection, remote admission, service lifetime, authenticated pairing and wire integration remain unimplemented by this increment.
