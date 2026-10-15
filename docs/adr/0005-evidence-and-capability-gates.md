# ADR 0005 — Evidence and capability gates

**Status:** adopted for planning. **Date:** 2026-10-14.

A documented capability, a compiling API, and a passing hardware workflow are separate facts. Track official sources and API versions, then record qualified combinations with exact device/build/backend/session/topology identities.

Capability discovery includes unknown/unavailable/permission-required states. Do not infer OS support from hardware names, physical trackpad behavior from touch emulation, programming compatibility from serial success, or persistence from an open window. Moving upstream documentation is a discovery source; qualified releases use pinned versions.

Performance budgets and bandwidth arithmetic remain proposed until measured. CI and synthetic contract/specimen checks do not establish capture permissions, decoding, USB, or power behavior. A failed critical gate narrows scope or requires a tested fallback.

Consequences: more explicit P0 work, a maintained support matrix, evidence-linked release claims, and bounded drift review. **Stages:** all; first gate P0-S6, release gate P6-S4.

**Final-review refinement:** use [explicit capability outcomes](../05-delivery/feasibility-gates.md) alongside stage status. `pending`, `experiment` and `blocked` do not authorize their linked product stages. `supported` needs scoped evidence and completed deciding work; completing a failed investigation cannot silently satisfy a feature prerequisite.
