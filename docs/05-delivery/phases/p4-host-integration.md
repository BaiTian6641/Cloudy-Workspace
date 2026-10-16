# P4 — Host adapters and activity

**Status:** planned. **Active effort:** 12–20 person-days before contingency. **Purpose:** Deliver optional host adapters and attributed activity. These features do not gate native streaming or standalone app releases.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P4-S1 | Host agent pairing, capabilities, jobs, and resumable events | 4–6 | P1-S4, P3-S1 |
| P4-S2 | Windows user-session notification adapter | 3–5 | P4-S1 |
| P4-S3 | Linux and macOS scoped event adapters | 3–5 | P4-S1 |
| P4-S4 | Agent hooks, event deduplication, and R3 gate | 2–4 | P4-S2, P4-S3, P3-S9 |

## P4-S1 — Host agent pairing, capabilities, jobs, and resumable events

**Parallel construction entry:** [HOST-EXP01](../../03-platform/host-core-entry.md) now supplies a finite local diagnostic and portable build evidence under the [user mandate](../parallel-execution-contract.md). It does not activate this whole integration stage or waive P1-S4/P3-S1. Windows/Linux/macOS adapters can develop in disjoint bounded packets with explicit session/API limits. The subsequent [HOST-IDENTITY0.1.1](../../../qualification/results/P4-S1/2026-10-16-host-installation-identity.md) adds explicit local installation self-ID with independently reviewed Linux evidence; the subsequent [Windows0.1.2 permanent-file profile](../../../qualification/results/P4-S1/2026-10-16-host-windows-identity.md) resolves the source-level read-only directory-flush construction gap with unchanged Unix semantics. Final local checks and all three exact54e607d hosted host jobs pass; raw per-case Windows/macOS output remains uninspected. The subsequent [Linux local-jobs result](../../../qualification/results/P4-S1/2026-10-16-host-linux-jobs.md) adds an internal real library with32 named pure passes and seven actual finite native cases. The later [Windows jobs construction](../../../qualification/results/P4-S1/2026-10-16-host-windows-jobs.md) and [native CI safety fixture](../../../qualification/results/P4-S1/2026-10-16-host-windows-native-fixture.md) are reviewed and cross-compiled; the exactf2f8c16 hosted native-test/upload step now succeeds. The disjoint [macOS UNIX-peer UID prerequisite](../../../qualification/results/P4-S1/2026-10-16-host-macos-peer.md) also has a successful hosted compile/vet/test step. Raw individual outcomes remain uninspected after an unauthenticated artifact-download refusal. The new [Linux peer UID prerequisite](../../../qualification/results/P4-S1/2026-10-16-host-linux-peer.md) passes six actual finite socket race cases and independent review. These internal peer libraries create no listener and supply no complete local or remote command authority. The [Linux boot-identity prerequisite](../../../qualification/results/P4-S1/2026-10-16-host-linux-boot.md) passes focused checks and two real reads in one boot, with independent review; it does not complete event/capability integration or authenticate the host. The later [host0.1.3 context command](../../../qualification/results/P4-S1/2026-10-16-host-context.md) now composes the actual installation and boot readers on Linux, with11 focused checks and10 finite CLI processes. Other platforms return honest unsupported context results; Windows/Darwin builds are cross-compiled only at this increment. macOS managed jobs, remote jobs admission, recovery and all integration gates remain open.

**Owner:** Host/core developer. **Prerequisites:** P1-S4, P3-S1. **Status:** planned.

Work:

1. Implement separately versioned host core with authenticated pairing, revocation, scopes, and capability negotiation.
2. Add request IDs/expiration/idempotency policy, registered roots, managed jobs, and bounded diagnostics.
3. Implement stream/boot identities, sequence/cursors, retention, gap snapshots, and event persistence.
4. Test unauthorized/stale requests, repeated IDs, disconnects, restart, missing backend, and version skew.

Deliverables:

- Optional host-core service and protocol implementation
- Pairing/replay/gap/expiry fixtures

Exit evidence:

- Ordinary Console SSH still works without the agent.
- A repeated request or event cannot silently repeat a side effect.

Record evidence under `qualification/results/P4-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-S01, Q-X02. Official/upstream source IDs: G18, R08; resolve them in the [source register](../../07-reference/source-register.md).

## P4-S2 — Windows user-session notification adapter

**Owner:** Windows developer. **Prerequisites:** P4-S1. **Status:** planned.

Work:

1. Validate packaging/capability and user-consent requirements for UserNotificationListener.
2. Implement source/text/icon/dismissal capabilities that are actually supported, with bounded icon handling.
3. Handle access denial/revocation, session switching, host restart, and background synchronization explicitly.
4. Test Android permission/channel denial and keep the in-app activity path functional.

Deliverables:

- Qualified Windows notification adapter
- Permission/fidelity capability matrix

Exit evidence:

- Correct host/source attribution and no stale-action promises.
- Revoked access is distinct from an empty healthy inbox.

Record evidence under `qualification/results/P4-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X04. Official/upstream source IDs: R11, G22; resolve them in the [source register](../../07-reference/source-register.md).

## P4-S3 — Linux and macOS scoped event adapters

**Owner:** Linux/macOS developer. **Prerequisites:** P4-S1. **Status:** planned.

Work:

1. Evaluate supported Linux desktop notification integration against the actual daemon/compositor.
2. Implement macOS own-job/app-specific events and permission-aware status; keep arbitrary notification capture uncommitted.
3. Separate user-session helper lifecycle from the background host core.
4. Qualify source attribution, capability absence, permission changes, lock/logout/restart, and adapter errors.

Deliverables:

- Per-OS adapter capabilities and supported limitations
- Physical-session event runs

Exit evidence:

- No universal notification observer claim without a supported interface.
- Own-job events remain useful when system-wide forwarding is unavailable.

Record evidence under `qualification/results/P4-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X04. Official/upstream source IDs: R12, R13, R05; resolve them in the [source register](../../07-reference/source-register.md).

## P4-S4 — Agent hooks, event deduplication, and R3 gate

**Owner:** Integration/product QA. **Prerequisites:** P4-S2, P4-S3, P3-S9. **Status:** planned.

Work:

1. Add version-pinned supported hooks for selected managed jobs/agent tools; keep generic PTY operation independent.
2. Bind running/waiting/completed/failed states to current job/request/session identities.
3. Deduplicate events across both apps, suppress loops, respect expiry, and avoid offline reconnect alert storms.
4. Run event retention/offline/gap/permission scenarios and publish exact per-OS fidelity.

Deliverables:

- R3 integrated activity release candidate
- Hook/version/fidelity and offline replay report

Exit evidence:

- No screen-text inference or replayed approval operation.
- Expired history remains history rather than newly actionable notifications.

Record evidence under `qualification/results/P4-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X02, Q-X04. Official/upstream source IDs: G22, R11, R13; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Keep ordinary SSH/RDP/VNC and native streaming independent of the optional host agent. Unsupported OS event capabilities stay explicit; a suite integration failure does not fabricate a standalone app defect. Every done stage links actual evidence; a documentation check does not qualify a device.

HOST-EXP01 source/toolchain and service-boundary references: R295–R303. They support the bounded entry and future adapter research; no unsupported capability or completed P4 stage follows.

**Actual macOS target update (2026-10-16):** the user made a Mac Studio available over SSH. The [finite native primitive result](../../../qualification/results/P4-S1/2026-10-16-host-macos-native.md) passes13 selected top-level tests/28 aggregate results and three CLI checks on macOS26.6.2/arm64, with exact temporary archive/cleanup. Installation and local-peer claims remain sampled; managed jobs, session/capture permissions, pairing and full integration are still open. Connection details remain private, and historical CI raw-artifact limitations remain historical.

**Bounded event persistence construction (2026-10-16):** the [Linux job-status store](../../../qualification/results/P4-S1/2026-10-16-host-job-event-store.md) implements real bbolt-backed append, latest state, finite retention and cursor gaps. Reviewed source02 passes five focused checks and 11 top-level / 18 aggregate Linux race cases, including two finite process fixtures. This internal library does not yet collect live jobs, deliver authenticated events or persist consumer acknowledgements; P4-S1 prerequisites and status remain unchanged.

**Retained local-run construction (2026-10-16):** the [managed-run owner](../../../qualification/results/P4-S1/2026-10-16-host-managed-run.md) now holds one actual Linux Job with a borrowed single-writer Store, immutable cached observations and explicit append/settlement retries. Source review closes settlement-before-Wait ordering and uncertain native-fixture preservation findings. Accepted pure checks cover seven distinct methods plus sixteen admission subtests across the preserved failed first run and successful affected-only test correction; this is not a fresh seven-method run. The subsequent [finite native composition case](../../../qualification/results/P4-S1/2026-10-16-host-managed-run-native.md) passes one actual owned child, immutable pending/terminal retries, cancellation, real persistence and confirmed retirement. Durable admission, CLI/service lifetime and authenticated remote control remain unqualified. This bounded construction does not change P4 prerequisites or stage status.


**Darwin boot prerequisite (2026-10-16):** the [macOS boot reader](../../../qualification/results/P4-S1/2026-10-16-host-macos-boot.md) uses the actual fixed kernel node through unchanged maintained x/sys. Formatting, Darwin-arm64 vet/cross-compile and a separate real Mac26.6.2/build25G83 run pass: six methods plus18 malformed subcases/24 aggregate/zero skip or failure, including two valid equal actual observations without outputting identifiers. All11 evidence files are archived and verified before explicit exact-owned cleanup. Source support does not establish a stable named-node SDK/minimum-version or reboot/authentication guarantee; missing/restricted queries fail honestly. Host0.1.3 and Linux behavior remain unchanged. Darwin CLI context, macOS jobs/event storage, remote admission and P4 integration remain open. No dependency or formal stage/capability status changes.
