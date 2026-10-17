# P5 — Integrated desktop and document access

**Status:** planned. **Purpose:** Integrate native performance streaming, document access and contention policy using their actual dependencies; the adoption decision now belongs to P1-S5.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Progress is measured by verified functionality, linked evidence and completed acceptance gates; delivery estimates are withdrawn. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Prerequisites |
| --- | --- | --- |
| P5-S2 | Native high-performance stream and latency/stability qualification | P1-S5, P0-S4, P1-S4, P3-S1 |
| P5-S3 | DocumentsProvider and cached editing | P1-S3, P3-S3 |
| P5-S4 | Metered policy, accounting, and integrated desktop gate | P5-S2, P5-S3, P0-S6, P3-S4, P3-S6 |

## P5-S2 — Native high-performance stream and latency/stability qualification

**Owner:** Desktop/media developer. **Prerequisites:** P1-S5, P0-S4, P1-S4, P3-S1. **Status:** planned.

**Required supported capabilities:** CAP-STREAM.

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

**Owner:** Files/Android developer. **Prerequisites:** P1-S3, P3-S3. **Status:** planned.

Work:

1. Expose selected remote roots with stable document IDs, supported flags, explicit grants, and bounded cache.
2. Implement local staging, source identity/version checks, commit acknowledgements, offline queued edits, and conflict copies.
3. Test provider consumers, random/sequential access needs, canceled writes, revoked URI access, and process death.
4. Document backend semantic limits and cleanup/pinning/eviction settings.
5. Implement the selected file-publication policy: on a backend without conditional replacement, ask before every remote overwrite; offer save-copy/cancel, never remember blanket consent, and preserve pending staging.
6. Inject another writer between the last version check and publication, plus exclusive-name collision and lost-acknowledgement cases; never claim stat-plus-rename prevents concurrent lost updates.

Deliverables:

- Qualified remote document provider
- Consumer/cache/conflict/writeback matrix

Exit evidence:

- No remote file is labeled committed until the backend confirms publication.
- Concurrent/offline edits preserve data and explicit conflict choices.
- Every non-conditional remote overwrite has consent for that file and save generation; known conflicts and ambiguous completion preserve staged work and do not trigger an automatic overwrite retry.

Record evidence under `qualification/results/P5-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D05, Q-F01. Official/upstream source IDs: G12, G13, R15, R34, R35; resolve them in the [source register](../../07-reference/source-register.md).

## P5-S4 — Metered policy, accounting, and integrated desktop gate

**Owner:** Network/desktop QA. **Prerequisites:** P5-S2, P5-S3, P0-S6, P3-S4, P3-S6. **Status:** planned.

**Required supported capabilities:** CAP-PLATFORM. CAP-FOUNDATION construction admission cannot satisfy this product acceptance gate; see [ADR0012](../../adr/0012-foundation-construction-and-platform-acceptance.md).

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

Native streaming requires supported CAP-STREAM and the early P1-S5 adoption decision. DocumentsProvider depends on files/lifecycle rather than the streaming decision. External-only scope reduction still requires an explicit decision. Every done stage links actual evidence; a documentation check does not qualify a device.

## Shared fullscreen and overlay acceptance

The user requires remote desktop ready-time fullscreen with compact local controls. The [presentation contract](../../04-design/remote-session-presentation.md) applies to P1-S2 design, P3-S7/S8 native RDP/VNC and P5-S2 native streaming. Check eligible foreground entry, denial/windowed fallback, overlay/input separation, safe escape, actual geometry and retirement. This refines existing session UX; protocol, security and physical gates remain unchanged. Sources G24/R304–R306; [worker visual checks](../../04-design/worker-visual-acceptance.md) accompany implementation.
