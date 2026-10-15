# P6 — Hardening and supported release

**Status:** planned. **Active effort:** 10–18 person-days before contingency. **Purpose:** Prove resilience, upgrades, support documentation, and sustained daily use.

The canonical stage data is [backlog.json](../backlog.json). Estimates assume one experienced developer using established components; hardware access and upstream blockers can add elapsed time. Owner labels are roles, not staffed assignments.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P6-S1 | Failure injection, long sessions, and power transitions | 3–5 | P5-S4 |
| P6-S2 | Protocol skew, data migrations, updates, and rollback | 2–4 | P6-S1 |
| P6-S3 | Distribution requirements and user documentation | 2–4 | P6-S2 |
| P6-S4 | Ten-working-day dogfood and release decision | 3–5 | P6-S3 |

## P6-S1 — Failure injection, long sessions, and power transitions

**Owner:** QA/platform developer. **Prerequisites:** P5-S4. **Status:** planned.

Work:

1. Exercise client eviction, host restart, network switch, route loss, timeout, permission revoke, sleep/resume, and lid/display changes.
2. Run eight-hour terminal/stream/transfer workloads with memory/cache bounds and battery/thermal observations.
3. Verify independent app operation, no command/approval/flash replay, and authoritative USB/file reconciliation.
4. Fix critical defects and narrow support claims where reliable behavior is not established.

Deliverables:

- Failure matrix and sustained-run evidence
- Resolved critical defect list

Exit evidence:

- No silent data loss, unbounded growth, stuck input, or surprise side-effect replay in supported cases.
- Failures produce actionable bounded diagnostics.

Record evidence under `qualification/results/P6-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X02, Q-X03, Q-C06, Q-D03. Official source IDs: G15, G16, G02; resolve them in the [source register](../../07-reference/source-register.md).

## P6-S2 — Protocol skew, data migrations, updates, and rollback

**Owner:** Build/host/platform developer. **Prerequisites:** P6-S1. **Status:** planned.

Work:

1. Test old/new app pairs, agent/backend skew, unknown optional capabilities, and incompatible major versions.
2. Exercise profile/journal/cache migrations transactionally, with recoverable backups and no secret export.
3. Build signed update/rollback candidates for the selected distribution channel and verify actual downgrade limits.
4. Record release manifest, schema/dependency hashes, SBOM, and provenance of artifacts.

Deliverables:

- Migration/compatibility/update report
- Signed candidate manifests and rollback procedure

Exit evidence:

- An upgrade failure preserves recoverable user state and explains required action.
- Rollback is limited to verified supported paths, not an assumed Android downgrade.

Record evidence under `qualification/results/P6-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-R01, Q-S01. Official source IDs: G02, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P6-S3 — Distribution requirements and user documentation

**Owner:** Maintainer/documentation owner. **Prerequisites:** P6-S2. **Status:** planned.

Work:

1. Refresh current target-SDK/distribution requirements for the chosen channel; verify signing/license/source obligations.
2. Write setup, trust/key recovery, backend installation, permissions, input release, file conflicts, USB recovery, and diagnostics guides.
3. Publish only measured OS/compositor/probe/codec combinations and list explicit deferred capabilities.
4. Optionally qualify phone Continue On on API 37 devices; defer it explicitly without blocking the core release.

Deliverables:

- Release/support documentation and source/notice bundle
- Distribution checklist tied to actual candidate

Exit evidence:

- Users can complete the primary journeys and recover from known failures with the shipped documentation.
- Optional phone continuity or VM roadmap features do not become accidental release blockers/promises.

Record evidence under `qualification/results/P6-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-R01. Official source IDs: G02, G07, G26, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P6-S4 — Ten-working-day dogfood and release decision

**Owner:** Product/QA/maintainer. **Prerequisites:** P6-S3. **Status:** planned.

Work:

1. Use the candidate for at least ten working days across real Console, visual, files, and hardware workflows.
2. Track defects by severity, recurrence, topology/version, data impact, and recovery cost; reserve effort for fixes.
3. Re-run only affected gates after changes and recheck critical complete journeys before promotion.
4. Approve release with evidence/support matrix or retain candidate status with explicit unresolved defects.

Deliverables:

- Dogfood report with ten-day observation window
- R5 release decision and known limitations

Exit evidence:

- No unresolved release-blocking data-loss/trust/replay/ownership/input defect.
- At least ten working days observed; the 3–5 day estimate is active QA effort, not the observation duration.

Record evidence under `qualification/results/P6-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-R01, Q-A01. Official source IDs: G02, D09; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Every stage must satisfy its exit evidence or record an accepted, qualified fallback before this phase is declared complete. Remain a candidate or narrow the support matrix while release-blocking defects remain. A deadline does not waive data/trust/ownership gates.

P6-S4 has a ten-working-day observation minimum. Its active effort estimate does not compress that elapsed test window.
