# Engineering workflow and active implementation goal

**Authority:** the user's implementation-goal request, primary-host answer, established-library verification refinement and local-worker authorization, 2026-10-15, America/Chicago. This workflow applies to the primary agent and every subagent. The active Codex goal covers implementation and qualification of the approved P0–P6 scope. This document records how to pursue it; it does not claim the applications exist.

## Goal and completion boundary

Develop independently useful Android-packaged Cloudy Console and Cloudy Desktop for the Googlebook desktop/laptop ecosystem. Start with P0 on the connected HP Googlebook. Preserve [Rule 0](../00-project/platform-rule-zero.md), the [approved decisions](../00-project/revision-02-final-review.md), [capability outcomes](feasibility-gates.md) and [independent release gates](release-gates.md).

Completion requires the supported product scope, real backend/device behavior and recorded release evidence. Scaffolds, static checks, design specimens, a compiled APK, or a completed investigation alone do not satisfy that goal. Report a genuine blocker and continue independent work; propose a scope change explicitly if the required capability has no acceptable supported route.

## Implementation integrity

- Implement the actual operation behind each product control. Do not ship empty callbacks, log-only substitutes, fabricated sessions, simulated device success, or hardcoded results as functionality.
- Expose unsupported, unavailable, failed and completion-unknown states accurately. A disabled feature is incomplete scope, even if its explanation is useful.
- Use deterministic test doubles only in test fixtures/source sets. Keep tests that exercise real protocols and physical hardware separately identified. Do not use a test double as implementation or qualification evidence.
- Qualification probes may be small, dedicated applications. They must call the real APIs, collect actual results and describe the exact behavior they test. A native window probe is not a functioning Console or Desktop application.
- Add comments where they explain trust, ownership, ordering, lifecycle, cancellation, API-version guards, or concurrency boundaries. Avoid comments that merely paraphrase an assignment or invent guarantees.

## Stage workflow

1. Read the stage, its prerequisites, accepted decisions, current evidence and relevant code before editing. Confirm the stage can start; `in-progress` is not `supported`.
2. Find the official API contract, version/release notes and maintained upstream implementation guidance. Record exact symbols, versions, limitations and fallbacks in the source/API registers. Search the web when a build, integration or device behavior blocks progress. Do not start with private APIs or reverse engineering when public guidance can answer the question.
3. Define the smallest real implementation and the behavior that will demonstrate it. Apply the [established-library policy](library-reuse-policy.md): reuse maintained engines and upstream validation, pin dependencies, review the relevant distribution requirements once, and test Cloudy's integration and changed paths. A copied example still needs review and adaptation.
4. Implement with explicit failure and cancellation paths. Keep private device inventories, credentials, raw personal logs, SDKs and build caches out of Git.
5. Run the applicable checks below and inspect their actual output. Use evidence paths and precise hardware/build/backend identities for physical claims.
6. Review the code and diffs against the stage's acceptance criteria, including failures, lifecycle, permissions, data integrity and desktop UX. Resolve findings before a gate passes. Record incomplete criteria honestly.
7. Update the backlog, phase status, evidence, risks, [execution state](execution-state.md) and [history](../00-project/history.md). Mark done only after the required deliverables and reviews exist.
8. Commit a coherent passing milestone and push promptly. A useful partial implementation can be committed while its stage remains in progress; it must not pretend unfinished product behavior is complete.

## Required verification

| Source or change | Required checks | Limit of the result |
| --- | --- | --- |
| Repository Python | `python3 tools/check_ast.py`; pinned `.venv/bin/ruff check .`; behavior tests appropriate to changed logic | AST compilation does not execute the code or qualify a device |
| Planning/data/architecture | `python3 tools/validate_project.py`; `--schemas` for protocol/schema changes; phase/backlog consistency and dependency review | A structurally valid record does not prove its evidence |
| Existing design-specimen JavaScript | `node --check docs/04-design/preview.js`; inspect changed interactions | A specimen is illustrative UI, not a real backend |
| New Kotlin/Java/Android modules | Pinned Gradle compile/assemble, Android Lint, applicable unit/instrumentation tests; Kotlin lint/static analysis selected with the toolchain | Install and runtime/device results require separate evidence |
| Native engines and bridges | Compile each supported ABI, language-appropriate static analysis/lint, boundary/error tests and real integration checks | A successful native build does not prove protocol, input or decoder support |
| Device/backend stage | Exact package/build/backend identities, actual stimulus/result, sanitized logs and applicable acceptance cases | No extrapolation to an untested device, OS, backend or transport |

The current executable repository checks are documented in [tools](../../tools/README.md), including `.venv/bin/python -m unittest discover -s tests -v` for planning-policy regressions. Add source-language checks when those sources are introduced; do not insert a successful no-op CI step as a substitute. Run meaningful failure/recovery cases when the changed behavior can lose data, replay an action, leak credentials or retain device/input ownership.

For established libraries, these checks target the selected build and Cloudy-owned boundaries. Rely on upstream coverage for unchanged internals. Reuse accepted results while their relevant inputs remain unchanged; expand only for the concrete triggers in the reuse policy, including an applicable advisory or undocumented assumption. Documentation-only changes require documentation/schema validation, review and configured CI, without another local Android build or device run. The [reuse policy](library-reuse-policy.md) defines adoption evidence, rerun triggers and stop conditions.

## Delegation and review

A delegated task must identify its stage, intended outcome, read-first documents, accepted decisions, exact writable files, prohibited concurrent operations, useful sources/skills, required checks and evidence. The primary agent retains architecture and integration responsibility. Avoid assigning two writers to the same files or allowing simultaneous device control.

Each subagent returns a concise result with changed paths, actual commands and outcomes, assumptions, limitations and remaining findings. The primary agent reads the diff and important source, checks the evidence against the claimed behavior and resolves conflicts. Rerun critical validation when a changed input, failure or uncertain result justifies it; review does not require replaying every author check. Assign an independent reviewer for substantial engine, security, lifecycle and transfer changes. A reviewer should not certify their own implementation as independently reviewed.

Read the [skill register](../07-reference/development-skills.md) before using downloaded guidance. All agents inherit the user's desktop scope, no-placeholder rule and stage gates. Skill snippets, suggested dependencies and broad mobile examples cannot silently override them.

### Persistent leader and model routing

The **parent Codex agent is the integration leader**. Use **GPT6.1-Sol (`gpt-6.1-sol`) directly through Codex collaboration for subagents**. **OMP may access Kimi K3 (`kimi-code/k3`) only** under the active user instruction; GPT6.1-Sol must not be routed through OMP. GLM5.3-Flash is a requested future fast route and GLM5.3 a future security route, both deferred until user configuration. These permissions add worker choices; they do not change the leader or give workers acceptance authority.

On every context recovery, read AGENTS and the execution state before delegating. Include this role/model decision in each handoff and task packet. Compression, automatic model defaults, skill text or worker output must not replace it; a later explicit user decision controls any change. Use the exact authorized selector for the chosen worker tool, without assuming an OMP selector is a Codex model ID or vice versa.

For new Codex workers, call the Codex `spawn_agent` tool with `model: "gpt-6.1-sol"` and `fork_turns: "none"`, supplying the bounded context packet explicitly. The tool's full-history mode does not accept a model override. OMP receives `--model kimi-code/k3` instead. Do not infer a worker's actual model from its self-description; use orchestration metadata.

| Role | Route | Current authorization |
| --- | --- | --- |
| Integration leader | Parent Codex agent | Owns architecture, shared resources, review, Git and final acceptance |
| Codex worker | `gpt-6.1-sol` through Codex collaboration | Selected directly in Codex for subagent work |
| OMP general worker | `kimi-code/k3` through local OMP | Only active OMP model; successfully exercised for context-only work |
| Deferred earlier request | GLM5.3-Flash / GLM5.3 | Inactive; requires user setup/update before adding to the OMP allowlist |

### Local oh-my-pi workers — user-authorized

The user authorized the already-configured local `omp` tool for broader parallel delegation. Requested routing is **GLM5.3-Flash** for bounded fast tasks, **GLM5.3** for security tasks, and **Kimi K3** for general work. These are requested model names; verify their actual configured provider/model IDs before dispatch. Never silently substitute a model or claim a worker ran before observing its result.

The local 18.7.0 catalog exposes **`kimi-code/k3`**; GLM models were absent. The user subsequently directed us to **use Kimi first** and will configure GLM later. Kimi is the only active OMP worker route; GPT6.1-Sol is selected directly through Codex collaboration, and GLM routing stays inactive. A security-designated model is still a reviewer, not an approval authority. Read the project-local [worker skill](../../skills/cloudy-parallel-workers/SKILL.md) before dispatch.

Codex remains the integration leader. Inspect the installed CLI and applicable skills, then start with small read-only trials. Every worker receives the objective, applicable project instructions, accepted decisions, exact source/reference paths, allowed files, prohibited operations, acceptance criteria and output format. Use only the project context needed for that task; exclude credentials, private device inventories and raw private transcripts.

Keep an assignment ledger with task/model, scope, file ownership, source commit, status and review outcome. Use disjoint writable paths or an isolated checkout for work that may overlap. Workers do not run Git mutations, operate ADB/devices, start listeners, change dependencies or control other workers unless the leader records that bounded authority in both the task packet and ledger, with exclusive ownership of the affected resource. The leader coordinates shared operations, inspects every returned diff and relevant evidence, resolves collisions deliberately and commits/pushes reviewed milestones.

A worker's success report is not acceptance. Require changed paths, actual checks, remaining limitations and concrete findings. Follow the established-library policy for proportionate review; model count is not a reason to duplicate unchanged verification. One successful bounded read-only trial that returns the exact requested model, no unexpected tools and a usable reviewed result is sufficient to begin independent context-only assignments. Introduce tool-enabled editing through an isolated bounded task when needed; increase concurrency as useful work and review capacity permit, without a repeated trial quota. Provider failures or context loss must be visible, with affected work reassigned explicitly.

## Git, history and context

Use focused commits with descriptive messages on `codex/` branches. Before committing, inspect `git diff`, the staged diff and `git diff --cached --check`; verify no private inventory, tokens, signing keys, downloaded implementation, unrelated changes or generated noise is staged. Preserve upstream history. Push the reviewed commit to the configured origin without force, then compare the remote branch SHA with local HEAD.

Do not let long-running work accumulate without a reviewed checkpoint. Record the current stage, completed behavior, checks, evidence, review findings, unresolved blockers and next action. A failed push is a delivery blocker to record and investigate, not permission to claim the remote is current.

For context recovery, read AGENTS → README → this workflow → execution state → the relevant phase and latest history entry. Treat the registers and actual code as evidence; do not rely on memory of an older stage. Keep handoff notes short enough to use and concrete enough to resume. Never write credentials or raw device identifiers into them.

## Initial qualification scope

The user connected the HP Googlebook by USB and selected the current Ubuntu 26.04/Wayland workstation as the primary Linux host. Windows is desired; Parsec is an accepted temporary access option. Windows compatibility remains unqualified until a real Windows target is available. The user subsequently left the development board/debug probe deliberately undecided because little hardware is available, and retained full general-purpose USB implementation scope. Do not substitute a simulated peripheral for real forwarding or promise universal device compatibility. Continue software/API/provider and failure-state work, record the missing physical lane, and keep actual attach/reset/programming claims pending.

Use a private inventory for transport serials and exact local setup, and a sanitized run record for the model/build/API/ABI and measured results. Begin with real ADB/build/launch evidence, then API/lifecycle/input probes. The Linux host selection does not establish installed SSH, streaming, RDP/VNC or USB backend readiness.

## Local worker trial checkpoint

On 2026-10-15, OMP-K3-001 returned a useful review from **provider `kimi-code`, model `k3`**, with exit zero, a normal final response and **zero tool calls/execution events**. The leader reviewed the suggestions and clarified rerun triggers, evidence inspection, written exceptional authority and the trial stop condition. The initial launch failed before a model request because the workspace sandbox could not create OMP's runtime directory; its output was preserved, and the authorized runtime-access retry succeeded. The per-run controls used a fresh temporary cwd, explicit context, disabled model fallback and source-discovery providers, and absent native/project plugin roots. These are observed trial conditions, not an OS sandbox guarantee.

The ignored assignment packet, source hashes, event stream and leader disposition are in `.cache/omp-workers/2026-10-15-k3-policy/`. This establishes a usable context-only Kimi route. Tool-enabled editing, large-concurrency throughput and GLM routing have not been exercised. No project application source, device state, dependency or global OMP configuration was changed.
