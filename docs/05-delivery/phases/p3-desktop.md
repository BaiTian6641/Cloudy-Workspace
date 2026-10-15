# P3 — Desktop companion release

**Status:** planned. **Active effort:** 13–21 person-days before contingency. **Purpose:** Join saved workspaces, external streaming, files, and explicit device ownership.

The canonical stage data is [backlog.json](../backlog.json). Estimates assume one experienced developer using established components; hardware access and upstream blockers can add elapsed time. Owner labels are roles, not staffed assignments.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P3-S1 | Desktop hosts, workspaces, and capability presentation | 2–3 | P1-S4 |
| P3-S2 | External desktop handoff and backend setup | 2–4 | P3-S1, P0-S4 |
| P3-S3 | File browser, transfer journal, and conflict-safe writes | 4–6 | P3-S1, P2-S4 |
| P3-S4 | USB ownership and protected operation orchestration | 3–5 | P3-S1, P0-S5 |
| P3-S5 | Console/Desktop vertical slice and companion gate | 2–3 | P3-S2, P3-S3, P3-S4, P2-S5, P0-S6 |

## P3-S1 — Desktop hosts, workspaces, and capability presentation

**Owner:** Desktop developer. **Prerequisites:** P1-S4. **Status:** planned.

Work:

1. Implement manual host/workspace profiles with explicit environment/project roots and non-secret import.
2. Present installed/paired/reachable/permission-required/unknown states without conflation.
3. Add desktop/backend/display/input preference records without claiming an active session.
4. Map S06/S09 navigation and details to typed models with last-observed timestamps.

Deliverables:

- Desktop workspace/profile workflow
- Capability-state UI review

Exit evidence:

- Host, environment, project, and backend identities remain explicit.
- Missing agent/backend produces a supported setup path rather than a false connected state.

Record evidence under `qualification/results/P3-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01. Official source IDs: G02, R04; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S2 — External desktop handoff and backend setup

**Owner:** Desktop/integration developer. **Prerequisites:** P3-S1, P0-S4. **Status:** planned.

Work:

1. Implement only the supported launch/profile route proved in P0; validate target package/backend version.
2. Provide clear external transition, pairing/setup instructions, and manual-launch fallback when control is unavailable.
3. Persist per-host profile preferences and visible return-to-workspace actions.
4. Test unavailable client, unavailable host, permission denial, cancellation, and stream/client exit.

Deliverables:

- R2 stream launch/return workflow
- Backend setup/error diagnostics

Exit evidence:

- Primary visual application is reachable with documented handoff behavior.
- No unsupported upstream control API is invented or hidden behind a fake live view.

Record evidence under `qualification/results/P3-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-D02. Official source IDs: R04, R05, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S3 — File browser, transfer journal, and conflict-safe writes

**Owner:** Files developer. **Prerequisites:** P3-S1, P2-S4. **Status:** planned.

Work:

1. Implement distinct local/SFTP/system-Drive-provider roots with supported-operation metadata.
2. Build list/table, selection, details, staged copy, journal, resumable transfer, and commit states.
3. Bind resume to source version, validate destination, preserve conflict copies, and bound caches.
4. Exercise huge directories, symlinks, case/reserved names, permission failures, source change, cancellation, and interrupted commit.

Deliverables:

- Full file/transfer workflow with state journal
- Conflict/resume/cancel evidence

Exit evidence:

- No silent overwrite or false remote-commit success.
- Unsupported backend semantics are visible and do not silently fall back to destructive behavior.

Record evidence under `qualification/results/P3-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D03. Official source IDs: G13, R14, R15; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S4 — USB ownership and protected operation orchestration

**Owner:** Device/integration developer. **Prerequisites:** P3-S1, P0-S5. **Status:** planned.

Work:

1. Wrap the qualified provider/consumer control or manual workflow with version-aware capability discovery.
2. Implement backend-confirmed ownership states, explicit attach/release, reset identity set, and protection UI.
3. Reconcile after transport loss, app restart, consumer failure, and conflicting host requests.
4. Expose logs/errors and a deliberate recovery path; never automatically retry programming or assign an unknown device.

Deliverables:

- Device ownership view and reconciliation adapter
- Programming protection/failure evidence

Exit evidence:

- Only a confirmed released device can be reassigned.
- The primary actual probe passes the integrated program/verify journey.

Record evidence under `qualification/results/P3-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D04, Q-X03. Official source IDs: G14, R07; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S5 — Console/Desktop vertical slice and companion gate

**Owner:** Product/QA owner. **Prerequisites:** P3-S2, P3-S3, P3-S4, P2-S5, P0-S6. **Status:** planned.

Work:

1. Connect/build in Console, upload an artifact, open the same registered workspace in Desktop, and start visual work.
2. Program/verify the attached target, inspect output, and preserve exact host/project/device context.
3. Interrupt/restore connectivity without replaying commands or losing ownership accounting.
4. Export a measured usage report and publish R2 limitations/dependency installations.

Deliverables:

- First complete real-project vertical-slice report
- R2 companion release candidate/support table

Exit evidence:

- The end-to-end workflow passes on the primary qualified topology.
- Every fallback and extra installed dependency is visible in release documentation.

Record evidence under `qualification/results/P3-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X02, Q-X03, Q-N01. Official source IDs: G02, R08; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Every stage must satisfy its exit evidence or record an accepted, qualified fallback before this phase is declared complete. Keep manual external-client launch or a lab-side USB provider when that is the qualified topology; publish the limitation.
