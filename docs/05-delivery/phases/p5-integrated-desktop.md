# P5 — Integrated desktop and document access

**Status:** planned. **Active effort:** 17–28 person-days before contingency. **Purpose:** Add native streaming only if justified and distributable, then deepen file/metered workflows.

The canonical stage data is [backlog.json](../backlog.json). Estimates assume one experienced developer using established components; hardware access and upstream blockers can add elapsed time. Owner labels are roles, not staffed assignments.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P5-S1 | Integrated streaming license and maintenance decision | 2–3 | P4-S4 |
| P5-S2 | Optional native streaming and input profiles | 7–12 | P5-S1, P0-S4 |
| P5-S3 | DocumentsProvider and cached editing | 5–8 | P5-S1, P3-S3 |
| P5-S4 | Metered policy, accounting, and integrated desktop gate | 3–5 | P5-S2, P5-S3, P0-S6 |

## P5-S1 — Integrated streaming license and maintenance decision

**Owner:** Maintainer/desktop developer. **Prerequisites:** P4-S4. **Status:** planned.

Work:

1. Review actual upstream license/source obligations and choose external-only or maintained native integration.
2. Assess fork/update ownership, backend/client version compatibility, packaging, decoder/input surfaces, and reproducible builds.
3. Confirm that R2 handoff limitations justify the added integration scope.
4. If external-only is selected, replace P5-S2 implementation with qualified external-flow polish and re-estimate the phase.

Deliverables:

- Approved implementation/distribution ADR
- Pinned upstream/source/maintenance plan

Exit evidence:

- No integrated streaming code is shipped before the distribution model is resolved.
- Scope/effort is revised if the external-only branch is selected.

Record evidence under `qualification/results/P5-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01. Official source IDs: R06, R04, R05; resolve them in the [source register](../../07-reference/source-register.md).

## P5-S2 — Optional native streaming and input profiles

**Owner:** Desktop/media developer. **Prerequisites:** P5-S1, P0-S4. **Status:** planned.

Work:

1. Integrate the selected native media/input backend with explicit pairing, monitor/profile selection, and permission state.
2. Keep frames off general UI bridges, clear remote input on focus/disconnect, and always expose release capture.
3. Implement qualified Desktop/CAD/Presentation/Metered profiles and real encode/decode/drop statistics.
4. Run CAD/text/readability/long-session/resize/display-change and lifecycle cases on each supported topology.

Deliverables:

- Native stream candidate or re-scoped external-only deliverable
- Profile/input/decoder hardware evidence

Exit evidence:

- Actual CAD workflow passes with no stuck capture/modifiers and truthful observed metrics.
- Unsupported codec/compositor/lock/headless cases remain excluded.

Record evidence under `qualification/results/P5-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D02, Q-P02, Q-N01. Official source IDs: G09, G20, R04, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P5-S3 — DocumentsProvider and cached editing

**Owner:** Files/Android developer. **Prerequisites:** P5-S1, P3-S3. **Status:** planned.

Work:

1. Expose selected remote roots with stable document IDs, supported flags, explicit grants, and bounded cache.
2. Implement local staging, source identity/version checks, commit acknowledgements, offline queued edits, and conflict copies.
3. Test provider consumers, random/sequential access needs, canceled writes, revoked URI access, and process death.
4. Document backend semantic limits and cleanup/pinning/eviction settings.

Deliverables:

- Qualified remote document provider
- Consumer/cache/conflict/writeback matrix

Exit evidence:

- No remote file is labeled committed until the backend confirms publication.
- Concurrent/offline edits preserve data and explicit conflict choices.

Record evidence under `qualification/results/P5-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D05. Official source IDs: G12, G13, R15; resolve them in the [source register](../../07-reference/source-register.md).

## P5-S4 — Metered policy, accounting, and integrated desktop gate

**Owner:** Network/desktop QA. **Prerequisites:** P5-S2, P5-S3, P0-S6. **Status:** planned.

Work:

1. Add policy for hidden streams, thumbnails, prefetch, bitrate selection, and explicit large-transfer choices.
2. Label counters by interval/endpoint/direction/payload-or-wire/estimate and path-observability limits.
3. Compare app/stream counters with OS/router measurements without double-counting endpoint copies.
4. Run protected USB operations while changing bulk-transfer priorities and budget thresholds.

Deliverables:

- R4 integrated desktop candidate and measured usage report
- Metered policy/failure behavior evidence

Exit evidence:

- Counter scope is understandable and measured values align within declared boundaries.
- Budget policy never silently interrupts protected programming or fabricates path telemetry.

Record evidence under `qualification/results/P5-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-N01, Q-X03. Official source IDs: G21, R08, R16; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Every stage must satisfy its exit evidence or record an accepted, qualified fallback before this phase is declared complete. External-only streaming remains viable; re-scope and re-estimate P5-S2 rather than claiming an integration that cannot be maintained.
