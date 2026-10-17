# P6 — Hardening and supported release

**Status:** planned. **Purpose:** Qualify the complete suite, reusing applicable standalone evidence and validating combined failure, migration, distribution and sustained-use behavior.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Progress is measured by verified functionality, linked evidence and completed acceptance gates; delivery estimates are withdrawn. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Prerequisites |
| --- | --- | --- |
| P6-S1 | Failure injection, long sessions, and power transitions | P5-S4, P4-S4, P3-S9 |
| P6-S2 | Protocol skew, data migrations, updates, and rollback | P6-S1 |
| P6-S3 | Distribution requirements and user documentation | P6-S2 |
| P6-S4 | Ten-working-day dogfood and release decision | P6-S3 |

## P6-S1 — Failure injection, long sessions, and power transitions

**Owner:** QA/platform developer. **Prerequisites:** P5-S4, P4-S4, P3-S9. **Status:** planned.

Work:

1. Exercise client eviction, host restart, network switch, route loss, timeout, permission revoke, sleep/resume, and lid/display changes.
2. Run eight-hour terminal/stream/transfer workloads with memory/cache bounds and battery/thermal observations.
3. Verify independent app operation, no command/approval/flash replay, and authoritative USB/file reconciliation.
4. Fix critical defects and narrow support claims where reliable behavior is not established.
5. Repeat required X11 window, SFTP grant/export/edit, RDP/VNC session and USB enrolled hotplug failure cases across suspend/resume and actual Googlebook process eviction.
6. Reuse app-scoped release evidence only for unchanged versions and applicable cases; run full-suite regressions without retroactively coupling standalone releases.

Deliverables:

- Failure matrix and sustained-run evidence
- Resolved critical defect list

Exit evidence:

- No silent data loss, unbounded growth, stuck input, or surprise side-effect replay in supported cases.
- Failures produce actionable bounded diagnostics.

Record evidence under `qualification/results/P6-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X02, Q-X03, Q-C06, Q-D03, Q-C07, Q-C08, Q-C09, Q-C10, Q-D06, Q-D07, Q-D08, Q-N03, Q-P03, Q-F01, Q-R02. Official/upstream source IDs: G15, G16, G02; resolve them in the [source register](../../07-reference/source-register.md).

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

Record evidence under `qualification/results/P6-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-R01, Q-S01. Official/upstream source IDs: G02, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P6-S3 — Distribution requirements and user documentation

**Owner:** Maintainer/documentation owner. **Prerequisites:** P6-S2. **Status:** planned.

Work:

1. Refresh current target-SDK/distribution requirements for the chosen channel; verify signing/license/source obligations.
2. Write setup, trust/key recovery, backend installation, permissions, input release, file conflicts, USB recovery, and diagnostics guides.
3. Publish only measured OS/compositor/probe/codec combinations and list explicit deferred capabilities.
4. Publish the desktop-only applicability ledger; keep phone/tablet optimization and phone continuity outside required release work.
5. Verify the actual Android distribution/device-catalog targeting and separate APK/AAB signing/install/update behavior; publish Googlebook desktop support only, with phone/tablet explicitly outside scope.

Deliverables:

- Release/support documentation and source/notice bundle
- Distribution checklist tied to actual candidate

Exit evidence:

- Users can complete the primary journeys and recover from known failures with the shipped documentation.
- Phone continuity outside scope and VM roadmap features do not become accidental release blockers/promises.

Record evidence under `qualification/results/P6-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-R01, Q-P03. Official/upstream source IDs: G02, G07, G26, R06, G01, G31, G32; resolve them in the [source register](../../07-reference/source-register.md).

## P6-S4 — Ten-working-day dogfood and release decision

**Owner:** Product/QA/maintainer. **Prerequisites:** P6-S3. **Status:** planned.

Work:

1. Use the candidate for at least ten working days across real Console, visual, files, and hardware workflows.
2. Track defects by severity, recurrence, topology/version, data impact, and recovery cost; resolve release-blocking defects.
3. Re-run only affected gates after changes and recheck critical complete journeys before promotion.
4. Approve release with evidence/support matrix or retain candidate status with explicit unresolved defects.
5. Dogfood the complete revised desktop ecosystem workflows, collect applicable official desktop-quality evidence, and label every unsupported backend/device capability.
6. Record the full-suite observation separately from each standalone app candidate; overlapping days count only where the exact build and required workflows were actually observed.

Deliverables:

- Dogfood report with ten-day observation window
- R5 release decision and known limitations

Exit evidence:

- No unresolved release-blocking data-loss/trust/replay/ownership/input defect.
- At least ten working days observed; this required observation period is an acceptance criterion, not a delivery forecast.

Record evidence under `qualification/results/P6-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-R01, Q-A01. Official/upstream source IDs: G02, D09; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Full-suite promotion requires all applicable app, integration and host-feature evidence. Standalone releases retain their own gates. No unresolved trust/data/input replay/ownership defect may pass. Every done stage links actual evidence; a documentation check does not qualify a device.
