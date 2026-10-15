# Feasibility outcomes and implementation readiness

**Status:** adopted for planning following the final review. No capability has been qualified. Stage progress and a capability decision answer different questions: a completed investigation can establish that the requested integration is blocked.

## Decision record

The canonical `capabilityGates` records in [backlog.json](backlog.json) connect a qualification owner to the implementation stages it controls. Each record names its deciding stages, responsible role, exact supported scope, outcome, evidence paths, and any next experiment. The initial outcome is `pending` with no evidence.

| Outcome | Meaning | Permitted next work |
| --- | --- | --- |
| `pending` | Required evidence has not been collected or reviewed | Investigation and unrelated work; dependent product implementation stays planned or blocked |
| `supported` | Recorded evidence supports the selected approach within the named hardware/build/backend/workflow scope | The linked implementation may become ready once its other prerequisites also pass |
| `experiment` | A specific uncertainty remains, with a hypothesis, owner, effort cap, stop condition and expected evidence | That bounded investigation may continue; the dependent product stage stays planned or blocked |
| `blocked` | The requested capability lacks an acceptable demonstrated route | Unrelated work may continue; dependent implementation requires new evidence or an explicit scope decision |

`supported` is permission to use a qualified approach in its stated scope, not a shipping result or universal compatibility claim. New versions/topologies require an applicability review. A failed or inconclusive experiment cannot become supported because its time budget expired.

## Readiness and completion rules

1. Record the experiment's pass/fail/blocked/inconclusive verdict in a [run](../06-validation/templates/run.md), then separately record the capability outcome. Link exact artifacts; a summary of documentation or a URL response is insufficient device evidence.
2. Mark an investigation stage done only when its stated investigation deliverables and decision record are complete. A blocked decision can finish an investigation without satisfying the dependent feature.
3. A product stage can enter `ready`, `in-progress`, `review` or `done` only when its required capability records are supported and its stage prerequisites have completed. Bounded prototype work belongs to its investigation record, not to a prematurely activated product stage.
4. A blocker applies to the named capability and dependent stages. Preserve progress in unrelated Console/Desktop branches. Do not turn optional host adapters into mandatory dependencies through a broad phase-complete flag.
5. Before changing requested scope, record the user-visible requirement that would remain unmet, the tested alternative, the decision authority, and the new estimate. A proposed fallback alone does not authorize reduced scope.
6. Re-estimate implementation after P0 evidence and the foundation's engine/distribution review. Keep the published effort range a budgeting hypothesis until those decisions exist.

The repository checker validates the structure, references and supported-outcome requirement for activated dependent stages. It also checks complete dependency paths against the selected app/stream independence constraints. It cannot establish the truth of physical evidence or approve a scope change.

## Primary capability decisions

| Capability | Deciding investigation | Required evidence |
| --- | --- | --- |
| Desktop Android lifecycle/windows | P0-S2 | Independent tasks, recreation, input and operation-specific background strategy on the target build |
| Shared SSH transport and SFTP | P0-S3 + P1-S4 | Engine build/adoption, server trust/authentication/jumps and actual SFTP probes independent of any terminal renderer; CAP-SSH controls P2-S1/P2-S8 |
| Terminal renderer and committed input | P0-S3 + P1-S4 | Chosen renderer, exact committed bytes, IME/accessibility, bounded output and renderer adoption; CAP-TERMINAL controls P2-S2 |
| Native X11 app windows | P0-S7 + P1-S4 | Two apps, owned dialogs/popups, native window behavior, trust isolation, lifetime and engine adoption |
| USB forwarding and enrolled hotplug | P0-S5 + P1-S4 | Actual programming/verify/reset, permission owner, provider/consumer control, confirmed ownership and backend adoption |
| Native RDP | P0-S4 + P1-S4 | Primary-server trust/input/render/session integration probe and engine/build/distribution decision |
| Native VNC | P0-S4 + P1-S4 | Primary-server security/encoding/input/recovery probe and engine/build/distribution decision |
| Native performance streaming | P0-S4 + P1-S5 | Supported native surface/input path, measurable baseline, dependency/distribution and maintenance decision |

P0 records the experimental verdict even when final engine adoption remains pending. P0-S3 must distinguish SSH/SFTP transport from terminal renderer results. Its investigation can finish with supported transport feasibility and a blocked renderer; CAP-SSH becomes supported only after its own P1-S4 adoption review, independently of CAP-TERMINAL. P2-S8 qualifies shared transfer/publication source without a PTY, terminal renderer or Console UI; Desktop's P3-S3 then integrates it with its own credentials and lifecycle.

P1-S4 completes each relevant engine decision separately: a blocked renderer, VNC or X11 choice does not stop foundation completion or promote that blocked feature. Its capability outcome continues to hold only its own dependent implementation. The eight initial capability outcomes remain pending; splitting a record creates no qualification evidence.

The USB and X11 fallbacks retain their existing product limits: a lab-side USB server does not establish Googlebook-local plug-and-play, and one full X desktop does not establish separate native application windows.
