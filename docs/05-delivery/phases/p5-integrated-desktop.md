# P5 — Integrated desktop and document access

**Status:** planned. **Active effort:** 18–30 person-days before contingency. **Purpose:** Complete the separately gated native high-performance media path, measured latency/stability, document provider and metered policy.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Estimates are active effort, not elapsed time or staffing commitments; re-estimate after P0. Stage IDs remain stable; the dependency order can place a later-numbered addition before an existing release gate.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P5-S1 | Integrated streaming license and maintenance decision | 2–3 | P4-S4 |
| P5-S2 | Native high-performance stream and latency/stability qualification | 8–14 | P5-S1, P0-S4 |
| P5-S3 | DocumentsProvider and cached editing | 5–8 | P5-S1, P3-S3 |
| P5-S4 | Metered policy, accounting, and integrated desktop gate | 3–5 | P5-S2, P5-S3, P0-S6 |

## P5-S1 — Integrated streaming license and maintenance decision

**Owner:** Maintainer/desktop developer. **Prerequisites:** P4-S4. **Status:** planned.

Work:

1. Review actual upstream license/source obligations and choose external-only or maintained native integration.
2. Assess fork/update ownership, backend/client version compatibility, packaging, decoder/input surfaces, and reproducible builds.
3. Confirm that R2 handoff limitations justify the added integration scope.
4. If external-only is selected, replace P5-S2 implementation with qualified external-flow polish and re-estimate the phase.
5. Treat native high-performance integration as the full-scope target; an external-only decision must explicitly revise user-visible scope, stage estimates and unresolved requirements.

Deliverables:

- Approved implementation/distribution ADR
- Pinned upstream/source/maintenance plan

Exit evidence:

- No integrated streaming code is shipped before the distribution model is resolved.
- Scope/effort is revised if the external-only branch is selected.

Record evidence under `qualification/results/P5-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01. Official/upstream source IDs: R06, R04, R05; resolve them in the [source register](../../07-reference/source-register.md).

## P5-S2 — Native high-performance stream and latency/stability qualification

**Owner:** Desktop/media developer. **Prerequisites:** P5-S1, P0-S4. **Status:** planned.

Work:

1. Integrate the approved maintained native stream engine with direct media/input, native Surface and measured decoder/profile selection.
2. Minimize copies/queue age, use supported low-latency decoder modes, and implement backend-supported adaptation with stable audio/video pacing.
3. Qualify CAD/keyboard/trackpad/mouse, display/scaling/capture release, permissions/session and thermal 8-hour behavior.
4. Compare input-to-photon and quality against qualified standalone Moonlight and Parsec where supported; run impaired links and recovery without false parity claims.

Deliverables:

- Native high-performance Desktop stream path and diagnostics
- Matched comparison/pacing/power/stability results with explicit target decisions

Exit evidence:

- Native media/input path and required real CAD work pass; no video through general UI bridges.
- P0-ratified latency/quality/stability budgets have measured outcomes and no unresolved input/trust/data-integrity blocker; any scope change is explicit.

Record evidence under `qualification/results/P5-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D02, Q-N01, Q-N02, Q-N03, Q-A01. Official/upstream source IDs: G09, G20, G29, G30, R04, R05, R06, R29, R30; resolve them in the [source register](../../07-reference/source-register.md).

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

Record evidence under `qualification/results/P5-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D05. Official/upstream source IDs: G12, G13, R15; resolve them in the [source register](../../07-reference/source-register.md).

## P5-S4 — Metered policy, accounting, and integrated desktop gate

**Owner:** Network/desktop QA. **Prerequisites:** P5-S2, P5-S3, P0-S6. **Status:** planned.

Work:

1. Add policy for hidden streams, thumbnails, prefetch, bitrate selection, and explicit large-transfer choices.
2. Label counters by interval/endpoint/direction/payload-or-wire/estimate and path-observability limits.
3. Compare app/stream counters with OS/router measurements without double-counting endpoint copies.
4. Run protected USB operations while changing bulk-transfer priorities and budget thresholds.
5. Measure file/USB contention and adaptive stream behavior with hysteresis; preserve protocol state and protected operations while prioritizing interactive work.

Deliverables:

- R4 integrated desktop candidate and measured usage report
- Metered policy/failure behavior evidence

Exit evidence:

- Counter scope is understandable and measured values align within declared boundaries.
- Budget policy never silently interrupts protected programming or fabricates path telemetry.

Record evidence under `qualification/results/P5-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-N01, Q-X03, Q-N02, Q-N03. Official/upstream source IDs: G21, R08, R16; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Resolve licensing at P5-S1 before native streaming. An external-only decision requires explicit feature/effort/scope revision. Compare performance with matched conditions and publish limitations. Every done stage links actual evidence; a documentation check does not qualify a device.
