# Development skills: provenance and project boundaries

**Reviewed:** 2026-10-15, America/Chicago. The user authorized acquiring development skills for the parent agent and subagents. Three official Android skills are installed in the local Codex skill directory. This records guidance acquisition, not an Android toolchain installation, application implementation, or device qualification.

Read [AGENTS.md](../../AGENTS.md), [Rule 0](../00-project/platform-rule-zero.md), the assigned phase, and the [engineering workflow](../05-delivery/engineering-workflow.md) before applying a skill. The current user request and these project decisions control scope. Downloaded examples are reference material that requires review.

## Pinned origin and integrity

The publisher is Google LLC. The upstream is [android/skills](https://github.com/android/skills/tree/5bda39f154fb859a08d45e2392f3568e049f83fb), revision `5bda39f154fb859a08d45e2392f3568e049f83fb`, committed 2026-10-14. The repository uses [Apache-2.0](https://github.com/android/skills/blob/5bda39f154fb859a08d45e2392f3568e049f83fb/LICENSE.txt). Google's [skills overview](https://developer.android.com/tools/agents/android-skills) identifies the official repository and explains the workflow format. Source records: R36, R37 and G33 in the [register](source-register.md).

| Skill | Pinned upstream directory | Intended use | Skill metadata date |
| --- | --- | --- | --- |
| `android-cli` | [devtools/android-cli](https://github.com/android/skills/tree/5bda39f154fb859a08d45e2392f3568e049f83fb/devtools/android-cli) | Official documentation lookup, selected-device inspection/deployment, and explicit journey evidence | 2026-09-12 |
| `testing-setup` | [testing/testing-setup](https://github.com/android/skills/tree/5bda39f154fb859a08d45e2392f3568e049f83fb/testing/testing-setup) | Review test layers, state restoration, UI behavior, screenshot and physical-device evidence | 2026-09-30 |
| `edge-to-edge` | [system/edge-to-edge](https://github.com/android/skills/tree/5bda39f154fb859a08d45e2392f3568e049f83fb/system/edge-to-edge) | Compose caption/system/IME inset handling after SDK/library selection | 2026-08-24 |

The installed `SKILL.md` files were checked against these SHA-256 digests:

| File | SHA-256 |
| --- | --- |
| `android-cli/SKILL.md` | `93bef1a066f4cc04ee6e02c7770f1b741aa43b2ca250b77e5626f0fb26cc75a0` |
| `testing-setup/SKILL.md` | `56d85c76b6771c7b1fdabdf0090953f4e0b4d2b332a3690c59985fb2e7a1e2ed` |
| `edge-to-edge/SKILL.md` | `4ca04cb6a5330b96e51410bc815f878b4ca642fba46fe5e1ad724c94e04d6eb8` |
| Upstream `LICENSE.txt` retained beside each skill | `58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd` |

Each installed directory also contains `cloudy-provenance.json` with the upstream revision, skill/license hashes and project-guidance reminder. The installer copied the selected directory; the upstream root license was added separately because a directory-only install does not include it. Upstream skill bodies are unchanged. The original selected directories contain Markdown instructions/references and no executable scripts. This review does not certify arbitrary commands embedded in Markdown or future upstream revisions. Third-party material stays outside this repository.

## Required project overrides

1. **Use the desktop contract.** Do not add phone/tablet optimization, phone continuity, mobile navigation, or phone-only screenshot requirements because a general Android skill suggests them. Exercise desktop windows, narrow tiles, physical keyboard/mouse, native IME, accessibility and font scaling as defined by Cloudy.
2. **Preserve dependency decisions.** `testing-setup` suggests Hilt when no dependency-injection framework exists and discusses an alpha AGP screenshot workflow. These defaults do not select Cloudy's architecture or authorize Hilt, an alpha toolchain, new test frameworks, or a migration. Use the agreed build/dependency decision and the smallest test stack that verifies the stage.
3. **Implement real behavior.** No placeholder production implementations, fake successful operations, empty backend methods, or invented protocol results. Test doubles may exist in test-only source sets to drive specific failure/concurrency states. Keep their results distinct from real engine, server, device, and release evidence. A real qualification probe may implement only its stated experiment; it must not masquerade as a completed product feature.
4. **Keep tools explicit.** Installing the `android-cli` skill does not install the executable. Do not follow its automatic `curl | bash`, `latest`, environment initialization, SDK update, or shell-profile changes as an implicit next step. Review the required tool/version, official installer or release artifact, checksum and local impact first. Existing SDK/platform tools remain usable. Search official documentation through available web tools when the CLI is unavailable.
5. **Inspect current command help.** The pinned CLI interaction reference recommends `layout --diff`, while its embedded command help calls that flag deprecated/no-op. Check the installed executable's help/version. Choose the intended ADB device explicitly; do not interact with unrelated devices or collect unrelated personal app content.
6. **Verify exact APIs.** `edge-to-edge` assumes Compose and target SDK 35 or later; it also shows newer ruler APIs. Check the project's actual AndroidX versions, runtime guards and system caption behavior before copying a pattern. A successful compilation does not establish physical IME/window behavior.
7. **Keep reviews and evidence independent.** Skills do not replace syntax/AST checks, compilation/type checking, lint, meaningful tests, stage review, or parent review of subagent changes. Record commands, outcomes, limitations and the exact code revision in the stage record before changing capability status.

The [Android CLI overview](https://developer.android.com/tools/agents/android-cli) documents usage telemetry and the `--no-metrics` option. Apply the project’s chosen configuration deliberately if the executable is later installed. Skill acquisition did not run the CLI installer or enable a new service.

## Parent and subagent loading

Resolve the installed paths using the [development setup](../03-platform/googlebook/development-setup.md#load-reviewed-skills). After reading project instructions, explicitly read the relevant installed `SKILL.md` and any reference needed for the assigned task. Fresh turns may discover these skills through their current catalog; discovery is not evidence that the body or this project's overrides were read. A missing catalog entry does not prevent an agent with filesystem access from reading the reviewed installed path.

Delegate with the stage ID, owned files, relevant source/API IDs, exact skill path, these overrides, required checks, and the intended return evidence. The parent reviews the resulting diff and check output before integration. Subagents do not inherit authorization to alter the toolchain, public scope, capability outcomes, credentials, or device ownership merely because a skill mentions those actions.

The repository-local [cloudy-reference-research workflow](../../skills/cloudy-reference-research/SKILL.md) remains required for official API research. Start from the source/API trackers, inspect official signatures and versioned documentation, then use focused upstream source inspection when an unresolved question requires it. Search official guidance and known issues before attempting to reconstruct undocumented behavior.

## Reviewed but deferred candidates

| Candidate at the same upstream revision | Reason for deferral |
| --- | --- |
| [adaptive](https://github.com/android/skills/blob/5bda39f154fb859a08d45e2392f3568e049f83fb/jetpack-compose/adaptive/SKILL.md) | Assumes Compose/Navigation 3 and broad form-factor migration; some Grid/FlexBox/MediaQuery paths are experimental. Read specific references only after the stack decision; do not import phone scope or unapproved migrations. |
| [jetpack-compose-performance](https://github.com/android/skills/blob/5bda39f154fb859a08d45e2392f3568e049f83fb/jetpack-compose/jetpack-compose-performance/SKILL.md) | Needs implemented code and measurements; some guidance depends on Compose 1.12/1.13. UI micro-optimizations cannot establish terminal/video latency. |
| [android-intent-security](https://github.com/android/skills/blob/5bda39f154fb859a08d45e2392f3568e049f83fb/security/android-intent-security/SKILL.md) | Useful audit areas, but examples include presence-only payload validation, sample certificate constants and differing API assumptions. Production IPC requires the real trust contract and adversarial tests. |
| [android-permissions-security](https://github.com/android/skills/blob/5bda39f154fb859a08d45e2392f3568e049f83fb/security/android-permissions-security/SKILL.md) | Useful least-privilege topics, but illustrative methods include logging-only/empty behavior. Verify Binder identity context, permission lifetime and URI grant semantics against exact official APIs. |

## Updates and reproducibility

Do not update every skill to moving `main` during a stage. For a required update, review the diff, referenced files, license, executable instructions and project conflicts; pin the new revision; verify hashes; update this record and local provenance; then rerun affected checks. Google's skills overview notes that `android skills add` can update an existing skill to latest, so it is not the revision-pinning procedure for this project. A changed skill is a reviewed tooling change, not an automatic change to product requirements.

## Local oh-my-pi delegation workflow

The user initially chose Kimi K3 first while configuring GLM, then reported GLM access configured. Current authorized OMP routes are **`zai/glm-5.3-flash`** for bounded fast tasks, **`zai/glm-5.3`** for security tasks and **`kimi-code/k3`** for general work. Both GLM selectors are present in the unchanged 18.7.0 catalog; their finite no-tool trials are now accepted for bounded context-only work with leader corrections. The [GLM trial checkpoint](../05-delivery/engineering-workflow.md#glm-context-only-trial-checkpoint) records actual provider/model, zero tool calls, timings, evidence and rejected suggestions; this does not qualify tool-enabled editing or broad security/device behavior. GPT6.1-Sol (`gpt-6.1-sol`) remains direct Codex collaboration only, never OMP, and the parent Codex agent retains leadership across context compression. The repository-local [cloudy-parallel-workers skill](../../skills/cloudy-parallel-workers/SKILL.md) was authored using the installed skill-creator guidance and official OMP 18.7.0 CLI/discovery references. It is project guidance, not a downloaded third-party skill or globally installed package. It defines model selection, minimal context packets, file ownership, leader review, result limits and bounded failure recovery. Resolve actual project/config/plugin and ancestor roots before a run: a fresh temporary cwd does not guarantee an empty discovery root, as the GLM preflight demonstrated before model execution. `omp skill list --json` reported no installed registry skills; no registry skill was installed for this workflow. The [engineering workflow](../05-delivery/engineering-workflow.md#local-oh-my-pi-workers--user-authorized) carries the user's authority and current routing state.

## Host and visual worker workflows

**Added2026-10-16 under the user's parallel host/client and design-review mandate.** Two original repository-local skills are acquired for both direct Codex workers and explicit OMP context packets:

| Workflow | Use and ownership |
| --- | --- |
| [cloudy-host-integration](../../skills/cloudy-host-integration/SKILL.md) | Existing P4 Windows/Linux/macOS host core/session adapters, truthful diagnostic/capability state, jobs/events and actual OS qualification |
| [cloudy-design-implementation](../../skills/cloudy-design-implementation/SKILL.md) | Canonical tokens/components, current design decisions, exact rendered evidence and reviewed native interactions |

These are project-authored guidance using the installed skill-creator workflow and reviewed official references R295–R306; they are not copied upstream implementations or globally installed plugins. Existing Android skills and their overrides remain applicable to clients. No Qt/Compose-desktop/native-client skill or extra client-platform workstream was adopted: the user corrected that interpretation to host-side software before any external model request.

A bounded upstream host-skill search found no suitable authoritative general-purpose package. Exact checked trees: Tokio `cdd9dae3aa5056e23dc4d5bc4f852ab8fbf27aff`, windows-rs `722f202100c3bd428627941d3834d87ad6679d1c` and systemd `91f997293d334422cbf462d32c2ee49e35dcf2d6` were complete and contained no SKILL.md. Rust `bf9448e42622eaba63ebc171bb4584cea22210b4` was truncated, so no global absence is inferred. Search evidence is ignored `.cache/host-01/official-skill-tree-search.json`. Those search pins are not adopted runtime dependencies. No unrelated community skill bundle, downloaded script or global configuration was installed.

Supply a relevant skill body/reference subset explicitly to an OMP packet because current no-skill discovery controls do not auto-load it. The packet ledger names exactly what was supplied and its hash. Reading a skill locally is not evidence that a remote worker received it. Tool and payload permissions remain task-specific. Direct Codex GPT-6.1 Sol coordinates; parent owns integration and shared resources.

Both Kimi and GLM5.3-Flash completed a finite single-image task with matching routes and zero tools. Parent-reviewed [image evidence](../06-validation/2026-10-16-parallel-host-workers.md) supports bounded visual observations, retaining Flash's rejected clipping claim. It does not qualify autonomous UI control or native behavior. Use the new visual contract for future screens; do not repeat unchanged routing trials or call text-only output a visual pass.
