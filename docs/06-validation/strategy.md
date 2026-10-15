# Validation strategy and evidence policy

Validation proceeds from contracts and synthetic fixtures to physical device workflows. The initial repository checker validates planning artifacts; it does not run an Android application.

## Layers

| Layer | Proves | Does not prove |
| --- | --- | --- |
| Documentation/data checks | Internal links, IDs/DAG/effort sums, source references, declared contrast pairs, intake hash | Correctness of upstream claims or real app behavior |
| Contract schemas/fixtures | Proposed envelope shape, required fields, rejection of invalid examples | Identity, authorization, clock expiry, replay safety |
| Unit/integration tests | Chosen engine/state logic and controlled failures | OS permission/capture/input/device behavior |
| Emulator instrumentation | Layout, tasks, process recreation, selected OS APIs | Physical trackpad, USB resets, sustained hardware decode |
| Physical qualification | Exact version/topology workflow | Every device/build or future backend version |
| Dogfood | Sustained real workflows and defect recurrence | Unlimited reliability or universal compatibility |

## Proposed quality budgets

| Measure | Initial target | Measurement |
| --- | --- | --- |
| Local terminal input processing/render | p95 under 20 ms | Exclude network RTT/remote execution; record device/renderer/load |
| Warm layout restore | Within 2 s | Separate from authentication/network readiness |
| Durable session reattach | Normal case within 5 s after usable connectivity returns | Report retry/backoff/outliers; no command replay |
| Long-session memory | Bounded after warmup over 8 h | Track queue/scrollback/cache limits and resident memory |
| Stream | Stable pacing at qualified requested profile | Actual decoded/presented frames/drops, not settings alone |
| Input/data integrity | No silent duplicate/lost committed text or file conflict overwrite in supported tests | Byte/hash/version fixtures and real workflows |

Targets are hypotheses, not benchmarks. Refine them after P0. A missed performance target requires diagnosis and a scope/quality decision; a data-loss, trust, replay, or ownership defect blocks release.

## Required run context

Record run ID/date, tester role, stage/case IDs, commit/package hashes, device/OS/build/ABI, backend/driver/compositor, permission/session state, input device, display/scaling, route/path, stimulus, expected/observed behavior, measurements/units/boundaries, artifacts/hashes, verdict, and limitations. Use the [template](templates/run.md).

Use pass, fail, blocked, or inconclusive; avoid “works” without a case. Store sanitized evidence in `qualification/results/<stage>/<run>/`. Private logs and raw identifiers stay in the ignored private directory or a separately controlled store.

## Release gates

R1 needs a full Console day with Desktop and host agent absent. R2 needs the actual edit/build/program/inspect journey. R3 needs per-OS event capability and offline/gap/expiry evidence. R4 needs native-stream or explicitly re-scoped external flow plus provider/conflict/metered proof. R5 needs fault/migration/update/support checks and ten working days of dogfooding.

Re-run affected gates after meaningful changes, failures, or newly discovered concerns. Do not repeatedly broaden checks after a stable passing result without a reason. Keep upstream/backend versions pinned in every support claim.
