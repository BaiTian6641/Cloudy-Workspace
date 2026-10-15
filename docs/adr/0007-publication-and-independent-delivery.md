# ADR 0007 — Per-save consent and independent delivery

**Status:** adopted for planning from three user question-box replies. **Date:** 2026-10-14. This refines ADRs 0001, 0003, 0005 and 0006; the full requested product scope is retained.

## File publication

When a backend cannot guarantee conditional replacement, ask before every attempted remote overwrite. Consent belongs to the exact file, destination and staged revision, with save-copy and cancel alternatives; do not remember blanket permission. Retain local autosaves as pending until their remote publication is confirmed. Explain the concurrency limit and preserve ambiguous work. See the [publication contract](../02-architecture/file-publication.md).

The user selected a prompt on every save instead of the suggested automatic save-copy default. Atomic rename and prior version checks do not become a conditional write through user consent. Server-specific guarantees remain subject to API33 qualification.

## Independent releases

R1 Console and R2 Desktop can qualify and release separately after their own functional and app-scoped quality gates. The complete build/upload/handoff/program/inspect journey moves to P3-S9. Remove both the direct Console-gate dependency from R2 and the indirect X11 dependency through the global network gate. Shared implementation may still have source/build prerequisites, but neither app requires the other's installed process or credentials. See [release gates](../05-delivery/release-gates.md).

## Early native-stream adoption

Move the former P5-S1 adoption/distribution work to P1-S5 after P0 feasibility and the initial app build foundation. Native integration at P5-S2 depends on that decision, the native foundation and Desktop profiles. Optional P4 host adapters/events are not prerequisites. DocumentsProvider depends on file/lifecycle readiness instead of the streaming decision. Retain the early external streaming lane and the full native-stream target; a reduced external-only product requires a separate explicit scope decision.

## Consequences

The original Revision 02 backlog had 39 stages. The 2026-10-15 execution review extracts P2-S8 shared transfer work from existing app stages, yielding 40 stages with the same total effort. CAP-SSH and CAP-TERMINAL are separate decisions, so a renderer-only failure cannot hold Desktop files. Existing streaming decision effort moves between phases; the suite journey's original effort is split out rather than counted twice. Independent app release checks add 4–6 active person-days, yielding 129–223 before reserve. Capability outcomes and physical results remain pending; these planning decisions do not select a project license or certify a backend.
