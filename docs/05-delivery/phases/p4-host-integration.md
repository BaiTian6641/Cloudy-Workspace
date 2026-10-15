# P4 — Host adapters and activity

**Status:** planned. **Active effort:** 12–20 person-days before contingency. **Purpose:** Add optional host capabilities and attributed activity without making standard SSH/RDP/VNC depend on the custom agent.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Estimates are active effort, not elapsed time or staffing commitments; re-estimate after P0. Stage IDs remain stable; the dependency order can place a later-numbered addition before an existing release gate.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P4-S1 | Host agent pairing, capabilities, jobs, and resumable events | 4–6 | P1-S4, P3-S1 |
| P4-S2 | Windows user-session notification adapter | 3–5 | P4-S1 |
| P4-S3 | Linux and macOS scoped event adapters | 3–5 | P4-S1 |
| P4-S4 | Agent hooks, event deduplication, and R3 gate | 2–4 | P4-S2, P4-S3, P2-S5, P3-S5 |

## P4-S1 — Host agent pairing, capabilities, jobs, and resumable events

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

**Owner:** Integration/product QA. **Prerequisites:** P4-S2, P4-S3, P2-S5, P3-S5. **Status:** planned.

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

Keep source fidelity and permissions explicit. Host-agent failure must not invalidate ordinary SSH/RDP/VNC or manufacture event history. Every done stage links actual evidence; a documentation check does not qualify a device.
