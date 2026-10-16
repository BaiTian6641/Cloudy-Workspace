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

The **parent Codex agent is the integration leader**. Use **GPT6.1-Sol (`gpt-6.1-sol`) directly through Codex collaboration for subagents**. The user has configured GLM access and authorized **GLM5.3-Flash (`zai/glm-5.3-flash`) for bounded fast tasks, GLM5.3 (`zai/glm-5.3`) for security tasks, and Kimi K3 (`kimi-code/k3`) for general work through OMP**. Exact GLM selectors are catalog-discovered and their finite no-tool trials are accepted for bounded context-only work with leader corrections. GPT6.1-Sol must never be routed through OMP. These permissions add worker choices; they do not change the leader or give workers acceptance authority.

On every context recovery, read AGENTS and the execution state before delegating. Include this role/model decision in each handoff and task packet. Compression, automatic model defaults, skill text or worker output must not replace it; a later explicit user decision controls any change. Use the exact authorized selector for the chosen worker tool, without assuming an OMP selector is a Codex model ID or vice versa.

For new Codex workers, call the Codex `spawn_agent` tool with `model: "gpt-6.1-sol"` and `fork_turns: "none"`, supplying the bounded context packet explicitly. The tool's full-history mode does not accept a model override. OMP receives the role-appropriate exact `--model` selector from the table below instead. Do not infer a worker's actual model from its self-description; use orchestration metadata.

| Role | Route | Current authorization |
| --- | --- | --- |
| Integration leader | Parent Codex agent | Owns architecture, shared resources, review, Git and final acceptance |
| Codex worker | `gpt-6.1-sol` through Codex collaboration | Selected directly in Codex for subagent work |
| OMP general worker | `kimi-code/k3` | Authorized; accepted context-only Kimi trial retained |
| OMP fast worker | `zai/glm-5.3-flash` | Authorized; accepted bounded context-only trial with leader corrections |
| OMP security worker | `zai/glm-5.3` | Authorized; accepted bounded context-only trial with leader corrections |

### Local oh-my-pi workers — user-authorized

The user authorized the already-configured local `omp` tool for broader parallel delegation. Current authorized routing is **GLM5.3-Flash** for bounded fast tasks, **GLM5.3** for security tasks, and **Kimi K3** for general work. Use the exact configured selectors above and verify them against the installed catalog before dispatch. Never silently substitute a model or claim a worker ran before observing its result.

The initial 18.7.0 inspection found Kimi and no GLM selectors, so the earlier user instruction limited OMP to Kimi first. The user has now reported GLM access configured, and the unchanged 18.7.0 catalog lists **`zai/glm-5.3-flash`** and **`zai/glm-5.3`**. That later authorization supersedes the Kimi-only restriction. Catalog availability alone is not an executed provider trial. The parent has now accepted finite no-tool trials for both exact GLM routes, with the actual provider/model, zero tool calls and leader corrections recorded in the [GLM checkpoint](#glm-context-only-trial-checkpoint). The accepted Kimi context-only result remains usable. GPT6.1-Sol stays direct Codex only. A security-designated model is still a reviewer, not an approval authority. Read the project-local [worker skill](../../skills/cloudy-parallel-workers/SKILL.md) before dispatch.

Codex remains the integration leader. Inspect the installed CLI and applicable skills, then start with small read-only trials. Verify the invocation's resolved project/config/plugin roots and ancestor roots; a fresh temporary working directory alone is not isolation. The initial GLM preflight stopped before either model request because an ancestor Git root existed. The bounded retry uses a checked empty cwd/`.omp` anchor and inspected user/plugin/environment discovery paths; both retry trials completed and were accepted within the context-only scope below. Retain the preflight stop separately from the model trial results; retry evidence is under `.cache/omp-workers/2026-10-15-glm-routing/runtime-retry/`. Every worker receives the objective, applicable project instructions, accepted decisions, exact source/reference paths, allowed files, prohibited operations, acceptance criteria and output format. Use only the project context needed for that task; exclude credentials, private device inventories and raw private transcripts.

Keep an assignment ledger with task/model, scope, file ownership, source commit, status and review outcome. Use disjoint writable paths or an isolated checkout for work that may overlap. Workers do not run Git mutations, operate ADB/devices, start listeners, change dependencies or control other workers unless the leader records that bounded authority in both the task packet and ledger, with exclusive ownership of the affected resource. The leader coordinates shared operations, inspects every returned diff and relevant evidence, resolves collisions deliberately and commits/pushes reviewed milestones.

A worker's success report is not acceptance. Require changed paths, actual checks, remaining limitations and concrete findings. Follow the established-library policy for proportionate review; model count is not a reason to duplicate unchanged verification. One successful bounded read-only trial that returns the exact requested model, no unexpected tools and a usable reviewed result is sufficient to begin independent context-only assignments. Introduce tool-enabled editing through an isolated bounded task when needed; increase concurrency as useful work and review capacity permit, without a repeated trial quota. Provider failures or context loss must be visible, with affected work reassigned explicitly.

## Git, history and context

Use focused commits with descriptive messages on `codex/` branches. Before committing, inspect `git diff`, the staged diff and `git diff --cached --check`; verify no private inventory, tokens, signing keys, downloaded implementation, unrelated changes or generated noise is staged. Preserve upstream history. Push the reviewed commit to the configured origin without force, then compare the remote branch SHA with local HEAD.

Do not let long-running work accumulate without a reviewed checkpoint. Record the current stage, completed behavior, checks, evidence, review findings, unresolved blockers and next action. A failed push is a delivery blocker to record and investigate, not permission to claim the remote is current.

For context recovery, read AGENTS → README → this workflow → execution state → the relevant phase and latest history entry. Treat the registers and actual code as evidence; do not rely on memory of an older stage. Keep handoff notes short enough to use and concrete enough to resume. Never write credentials or raw device identifiers into them.

## Long-run recovery and user-observation correlation

The execution-state file is the single current handoff: installed versus built versions, actual last result, open findings, resource ownership, preserved evidence and the next bounded action. Historical research recipes and dated results keep their original scope and must be labeled as historical when later checkpoints supersede their imperative text. Rewrite obsolete current summaries instead of stacking contradictory “next” instructions. Actual source, artifact hashes and journals resolve implementation claims; the latest user report controls what the user observed. If these differ, retain both and investigate the boundary instead of rewriting either.

Before delegating or operating a device after compression, the leader checks the current branch/index/worktree, the latest review and the actual resource state needed for that action. Send workers the controlling outcome, exact evidence paths, unresolved findings and stop condition. No worker may promote a result from another lane, treat a completed review as an implemented fix, or regenerate qualified identities merely because an old recipe says to initialize.

An interactive test packet identifies app/version/build, operation, current readiness and bounded expiry. Bind the returned observation to that attempt when evidence permits. Ask one test question at a time; retire or explicitly supersede old questions before moving from catalog to playback. Avoid competing operator/user clicks. Record absent prompts and no visible output literally; do not infer successful visual playback from an authentication callback, native start, output-buffer release or rendered-frame count. Do not subtract host UTC from Android monotonic timestamps to fabricate causation or latency.

A finite diagnostic's one-play-per-process and 30-second authorization limits are not product reconnect/session-duration requirements. Expose the reason for an unavailable action, current phase and restart-required state clearly. After expiry, cleanup and no active prompt, say the attempt is closed before presenting any fresh step. Review and fix a confusing test flow before repeating it. Existing user authorization remains valid; a review request creates an order of work, not a new blanket approval gate.

## Initial qualification scope

The user connected the HP Googlebook by USB and selected the current Ubuntu 26.04/Wayland workstation as the primary Linux host. Windows is desired; Parsec is an accepted temporary access option. Windows compatibility remains unqualified until a real Windows target is available. The user subsequently left the development board/debug probe deliberately undecided because little hardware is available, and retained full general-purpose USB implementation scope. Do not substitute a simulated peripheral for real forwarding or promise universal device compatibility. Continue software/API/provider and failure-state work, record the missing physical lane, and keep actual attach/reset/programming claims pending.

Use a private inventory for transport serials and exact local setup, and a sanitized run record for the model/build/API/ABI and measured results. Begin with real ADB/build/launch evidence, then API/lifecycle/input probes. The Linux host selection does not establish installed SSH, streaming, RDP/VNC or USB backend readiness.

## Local worker trial checkpoint

On 2026-10-15, OMP-K3-001 returned a useful review from **provider `kimi-code`, model `k3`**, with exit zero, a normal final response and **zero tool calls/execution events**. The leader reviewed the suggestions and clarified rerun triggers, evidence inspection, written exceptional authority and the trial stop condition. The initial launch failed before a model request because the workspace sandbox could not create OMP's runtime directory; its output was preserved, and the authorized runtime-access retry succeeded. The per-run controls used a fresh temporary cwd, explicit context, disabled model fallback and source-discovery providers, and absent native/project plugin roots. These are observed trial conditions, not an OS sandbox guarantee.

The ignored assignment packet, source hashes, event stream and leader disposition are in `.cache/omp-workers/2026-10-15-k3-policy/`. This establishes a usable context-only Kimi route. At this initial Kimi checkpoint, tool-enabled editing, large-concurrency throughput and GLM routing had not been exercised. The later GLM catalog discovery/authorization above does not change this historical trial result. No project application source, device state, dependency or global OMP configuration was changed.

## GLM context-only trial checkpoint

On 2026-10-15 local time, the unchanged `omp 18.7.0` executed two concurrent finite no-tool trials after the initial ancestor-root preflight stop. The retry used a fresh explicit empty cwd/`.omp` anchor with resolved project/config/plugin roots checked, the user plugin `node_modules` root absent, and relevant path/profile/XDG overrides unset. The selected models were explicit, model fallback and auxiliary work were disabled, each worker had a 150-second bound and the parent a 180-second timeout. These are observed invocation conditions, not an OS sandbox guarantee or a claim that every temporary cwd has no ancestor root.

| Assignment | Actual provider/model | Elapsed | Observed outcome | Accepted scope |
| --- | --- | --- | --- | --- |
| GLM-FLASH-001 | `zai` / `glm-5.3-flash` | 16.220 s | Exit 0, normal stop, zero tool calls; no fallback or nested worker observed | Bounded context-only observation planning, with leader corrections |
| GLM-SECURITY-001 | `zai` / `glm-5.3` | 92.301 s | Exit 0, normal stop, zero tool calls; no fallback or nested worker observed | Bounded context-only security review, with leader corrections |

The leader accepted useful outputs, not every conclusion. Flash's causal-localization certainty and assumption that a historical decoder would recur were corrected. Security's proposed local-rectangle clamp was rejected because the observed coordinates are root-view coordinates; a speculative raw reason leak was unsupported because production reasons are typed enums; an implied dropped-first-update defect was rejected after inspecting the synchronous callback caller. A review model's role or clean exit is not security acceptance. Preserve the difference between an actual finding, a hypothesis and a rejected suggestion.

Private evidence for each assignment is retained under `.cache/omp-workers/2026-10-15-glm-routing/runtime-retry/GLM-FLASH-001/` and `GLM-SECURITY-001/`: `assignment.json`, `config.yml`, supplied `packet.md`, actual `events.jsonl`, `review-output.md` and the source/hash-bound `leader-review.json`. No credentials or raw event contents are published here. Both routes may now perform useful bounded context-only assignments without another trial quota or user-permission cycle. These worker trials do not qualify tool-enabled editing, broad security/device behavior, visual playback or throughput; introduce editing through separately scoped ownership/isolation and leader review when needed. The historical Kimi trial is unchanged, GPT6.1-Sol remains direct Codex only, and the parent retains integration/acceptance authority.
