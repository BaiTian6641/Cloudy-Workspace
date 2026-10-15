# Revision 02 — Final review and decision record

**Date:** 2026-10-14, America/Chicago. **Authority:** the user requested document updates after the final review and question boxes for decisions. **State:** planning revision; applications and hardware experiments remain unimplemented/unqualified.

## Engineering refinements

This is the original 39-stage snapshot. The subsequent [execution setup](history.md) extracts reusable SSH/SFTP work into P2-S8, yielding 40 stages with the same total effort. Read the current [roadmap](../05-delivery/roadmap.md) and [execution state](../05-delivery/execution-state.md) for active work; the three user decisions below remain in force.

- [File publication](../02-architecture/file-publication.md) distinguishes a version check, atomic rename, conditional replacement and confirmed completion. It adds a concurrent-writer test and preserves unresolved work after ambiguous results.
- [Capability gates](../05-delivery/feasibility-gates.md) separate investigation completion from supported implementation readiness, with bounded experiments and explicit blocked outcomes.
- [Native workspace review](../04-design/native-workspace-review.md) validates the revised S13–S17 desktop interactions before component geometry and feature behavior are frozen.
- The S06 specification distinguishes native RDP/VNC from the early external stream-launch path.

## User decisions

| ID | Decision | State | Selected policy / consequence |
| --- | --- | --- | --- |
| D09 | Default save policy without backend conditional replacement | User selected | Ask whether to overwrite on every remote save; also offer save-copy/cancel, with no remembered blanket overwrite consent |
| D10 | Independent app release gates | User selected | Console and Desktop may qualify and release independently; P3-S9 separately tests the complete suite journey |
| D11 | Native high-performance streaming sequence | User selected | Resolve feasibility/distribution in P0/P1; native integration proceeds on its own prerequisites, independently of optional host adapters |

All three selections were supplied through question-box replies in this conversation. They are recorded in [ADR 0007](../adr/0007-publication-and-independent-delivery.md) and the canonical backlog. The overwrite choice applies to backends without guaranteed conditional replacement; consent does not make an ordinary rename race-free. No project license, distribution channel, hardware support claim, reduced feature scope or shipping date is chosen by this document.

## Delivery changes

- The former P5-S1 decision becomes **P1-S5**, keeping its 2–3 person-day estimate while moving it into the foundation. P1-S4 and unrelated app work do not wait for a blocked streaming decision.
- P5-S2 depends on early adoption, native foundation and Desktop profiles; P4 host adapters/events are removed from its path. P5-S3 depends on file/lifecycle work, independently of streaming adoption.
- P3-S5 becomes the standalone Desktop release gate. P3-S9 holds the complete cross-app journey. The global network decision no longer waits for X11; X11-specific evidence remains mandatory for full Console.
- Each app gate includes its own signed installation/update/recovery, dependency/support documentation and sustained-use evidence. Full-suite P6 retains combined regression and distribution work.

There are now **7 phases / 39 stages**, with **129–223 active person-days** before reserve. Separating the existing integration journey adds one stage without adding its effort twice; app-specific release checks add **4–6 active person-days**. The streaming decision moves effort from P5 to P1. With the illustrative 25% reserve, the range is **161.25–278.75 person-days** or **32.25–55.75 person-weeks**. Observation days are separate from active QA effort and may overlap only for matching builds and recorded workflows.

## Estimate and evidence discipline

All capability outcomes start pending. Completing a research report does not make an unsupported feature ready. The existing effort range remains a budgeting hypothesis, subject to re-estimation after physical feasibility and engine/distribution review. Moving a decision earlier is sequencing work; it does not establish feasibility or grant upstream distribution rights.

Historical Revision 01 reports remain intact. The [Revision 02 validation record](../06-validation/revision-02-planning-review.md) identifies the current checks and their limits.
