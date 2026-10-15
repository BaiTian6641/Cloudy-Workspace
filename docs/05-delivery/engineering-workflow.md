# Engineering workflow and active implementation goal

**Authority:** the user's implementation-goal request and primary-host answer, 2026-10-15, America/Chicago. This workflow applies to the primary agent and every subagent. The active Codex goal covers implementation and qualification of the approved P0–P6 scope. This document records how to pursue it; it does not claim the applications exist.

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
3. Define the smallest real implementation and the behavior that will demonstrate it. Pin dependencies and inspect distribution requirements before embedding third-party code. A copied example still needs review and adaptation.
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

## Delegation and review

A delegated task must identify its stage, intended outcome, read-first documents, accepted decisions, exact writable files, prohibited concurrent operations, useful sources/skills, required checks and evidence. The primary agent retains architecture and integration responsibility. Avoid assigning two writers to the same files or allowing simultaneous device control.

Each subagent returns a concise result with changed paths, actual commands and outcomes, assumptions, limitations and remaining findings. The primary agent reads the diff and important source, checks the evidence against the claimed behavior, reruns critical validation and resolves conflicts. Assign an independent reviewer for substantial engine, security, lifecycle and transfer changes. A reviewer should not certify their own implementation as independently reviewed.

Read the [skill register](../07-reference/development-skills.md) before using downloaded guidance. All agents inherit the user's desktop scope, no-placeholder rule and stage gates. Skill snippets, suggested dependencies and broad mobile examples cannot silently override them.

## Git, history and context

Use focused commits with descriptive messages on `codex/` branches. Before committing, inspect `git diff`, the staged diff and `git diff --cached --check`; verify no private inventory, tokens, signing keys, downloaded implementation, unrelated changes or generated noise is staged. Preserve upstream history. Push the reviewed commit to the configured origin without force, then compare the remote branch SHA with local HEAD.

Do not let long-running work accumulate without a reviewed checkpoint. Record the current stage, completed behavior, checks, evidence, review findings, unresolved blockers and next action. A failed push is a delivery blocker to record and investigate, not permission to claim the remote is current.

For context recovery, read AGENTS → README → this workflow → execution state → the relevant phase and latest history entry. Treat the registers and actual code as evidence; do not rely on memory of an older stage. Keep handoff notes short enough to use and concrete enough to resume. Never write credentials or raw device identifiers into them.

## Initial qualification scope

The user connected the HP Googlebook by USB and selected the current Ubuntu 26.04/Wayland workstation as the primary Linux host. Windows is desired; Parsec is an accepted temporary access option. Windows compatibility remains unqualified until a real Windows target is available. The user subsequently left the development board/debug probe deliberately undecided because little hardware is available, and retained full general-purpose USB implementation scope. Do not substitute a simulated peripheral for real forwarding or promise universal device compatibility. Continue software/API/provider and failure-state work, record the missing physical lane, and keep actual attach/reset/programming claims pending.

Use a private inventory for transport serials and exact local setup, and a sanitized run record for the model/build/API/ABI and measured results. Begin with real ADB/build/launch evidence, then API/lifecycle/input probes. The Linux host selection does not establish installed SSH, streaming, RDP/VNC or USB backend readiness.
