---
name: cloudy-real-implementation
description: Prepare, implement and review bounded Cloudy worker tasks so production actions have real effects and evidence is reported honestly. Use with external OMP coding, repair, review and rotation packets, and their direct Codex coordinators.
---

# Cloudy real implementation

Use this skill for implementation acceptance, together with the [parallel-worker skill](../cloudy-parallel-workers/SKILL.md) for dispatch and isolation. The parent Codex agent remains integration leader. Direct Codex workers use `gpt-6.1-sol`; external OMP routes are `kimi-code/k3`, `zai/glm-5.3-flash` and security-preferred `zai/glm-5.3`. Explicit recorded rotation on quota/unavailability is allowed. No silent fallback, acceptance by worker self-report, or expansion of file/payload/device authority follows.

Read the current task packet, [execution state](../../docs/05-delivery/execution-state.md), applicable stage and [library reuse policy](../../docs/05-delivery/library-reuse-policy.md). For a context-only external worker, the coordinator must supply the relevant approved contents explicitly. A repository path or URL is not proof the worker can read it. Current OMP discovery is disabled; naming this skill in a prompt alone does not load it.

## Select the actual work mode

| Mode | Permitted result |
| --- | --- |
| Context-only proposal | Complete proposed patch/text from supplied inputs; all unexecuted checks labeled unrun. No claim that files were changed, source was compiled, tests passed or a device was observed. |
| Bounded tool-enabled implementation | Changes only in the assigned isolated paths with explicit tool/resource authority; actual commands, exit codes and evidence returned. Current no-tool trials do not grant this mode automatically. |
| Review | Findings against exact supplied source/diff/evidence; separate demonstrated defects, hypotheses and missing evidence. A clean process exit is not approval. |
| Repair or rotation | Resume from accepted current source and the retained previous result; carry unresolved findings and ownership forward without silently applying rejected code. |

Use the relevant [prompt template](references/worker-prompts.md), not every template at once. Before dispatch resolve every task-specific field, identify the real operation and its acceptance boundary, and remove irrelevant sections. Missing critical inputs must produce a bounded request for those inputs; never invite the worker to invent a repository API, backend, secret or device result. The coordinator can supply public official references and existing adapter examples without authorizing a new dependency.

## Require a real operation

For every requested action identify: entry point → actual library/API/transport → observable result or committed state → failure/cancellation → retirement or recovery. Completion requires all parts relevant to that assignment. A button, interface or class name alone is not integration. A happy-path callback, decoder counter or accepted command alone is not proof of visible output, delivered input or durable effects.

Production code must perform the requested operation or expose a typed, truthful unavailable/unsupported state with a reason and usable recovery path. The latter is a valid state implementation but does **not** complete the missing feature. Do not ship fake sessions, canned remote data, arbitrary sleeps as readiness, logging-only actions, empty success handlers, unconditional trust/authentication, invented identifiers or methods to be filled in later. Do not submit illustrative pseudocode as a compiling patch.

A worker's missing source/context is not a product capability observation. Do not replace a requested integration with an unavailable branch merely because the packet omits its adapter. Request the exact missing input. Any offered product recovery action must also be supported by the actual contract; do not invent setup, permission or reinstall instructions. Define transport-established and visibly-presenting states separately when the task needs both.

Use [acceptance rules and examples](references/acceptance-rules.md) when implementing/reviewing adapters, UI actions, ownership or failure paths. Keep test doubles in test-only sources; name what they establish and what requires the real backend or device. Reuse established libraries and their unchanged upstream evidence. Expand checks for a concrete changed boundary, not to reconstruct the library's entire test suite.

## Worker exit and parent acceptance

Return the exact mode, base/source binding, owned changes, operation-to-source-to-check map, actual executed checks, unrun checks, limitations and unresolved findings. Include useful invariant/lifecycle comments in code. Partial work must remain labeled partial, with a precise next step; do not hide a blocker behind an empty implementation or a claim of success. A worker task blocked on input does not change the status of the full project goal.

The coordinator/parent reads actual code and results, checks the externally visible route and exceptional paths, and compares claimed checks to the source/artifact that ran. Require language-appropriate compiler/AST, lint and meaningful changed-behavior checks; distinguish compile-only, mocked, real-process, hosted and physical-device evidence. Visual work additionally follows the [Cloudy design workflow](../cloudy-design-implementation/SKILL.md). A proposed image description is not a performed visual check.

Reject or return incomplete patches without importing their stubs into product paths. Preserve failed attempts and review dispositions. The parent alone integrates shared changes, runs shared device/host operations and commits/pushes reviewed passing milestones under the existing policy. This skill adds no blanket permission cycle, new release gate, global plugin installation or automatic source upload.
