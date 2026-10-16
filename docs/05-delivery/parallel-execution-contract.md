# Parallel client and host execution

**Controlling user revision: 2026-10-16.** Continue the full P0–P6 implementation goal. Develop the Googlebook clients and the Windows/Linux/macOS **host-side software** concurrently. The user's clarification explicitly excludes a new Windows/Linux/macOS client-port workstream. Rule 0, the two Android applications, independent releases and the existing 40-stage baseline remain intact. P1 is an integration milestone, not a stopping point.

The active goal already includes P4 host development. This record refines its execution and recovery instructions; it does not replace that goal with a smaller diagnostic objective. Completion still requires the approved feature and release evidence. A model's familiarity with an established OS helps implementation but does not establish its permission, session or hardware behavior.

**Latest recovery reminder:** Parsec/TeamViewer logic applies only to the connected RDP/VNC/streaming experience; discovery, device lists and pre-connection flows remain Cloudy's own. Keep Googlebook probe and product foundations evidence-backed while host construction advances. Develop both lanes concurrently and commit/push each coherent reviewed milestone as it becomes ready; do not hold a passing host change for unrelated client work or vice versa. The parent serializes shared Git operations, so parallel delivery does not mean conflicting worker pushes.

## Work allocation

| Lane | Current work and next bounded increment | Ownership and acceptance |
| --- | --- | --- |
| Googlebook client foundation | P1-S2 accessibility outcome semantics; P1-S3 positive protected file opening, fresh receiver authentication and lifecycle; VNC-first product adoption work on its prerequisites | Direct Codex GPT-6.1 Sol workers; parent owns actual ADB/fixtures/authentication correlation and final integration |
| Shared host core | HOST-EXP01 finite local runtime inspection; next identity/session observation, then local job ownership and event persistence under separate reviewed profiles | Direct Sol coordinator assigns Kimi general implementation proposals and Flash bounded tasks; parent selects interfaces, toolchain and transport/security design |
| Windows adapter | Interactive-user helper, notification packaging/access/revocation, native process-tree/job ownership, service/session boundaries | Disjoint `host/windows/`; build and OS execution evidence remain distinct |
| Linux adapter | Actual user-session identity, supported desktop/backend observation, process retirement and own-job events | Disjoint `host/linux/`; current Ubuntu workstation is available, not an automatic platform pass |
| macOS adapter | Own-job/app events, user agent lifecycle, supported permission/backend status and process ownership | Disjoint `host/macos/`; no universal notification observer or physical Mac qualification inferred |
| Design and integration review | Canonical Cloudy tokens, exact screen contracts, rendered review, native input/accessibility and honest connection states | Bounded OMP screenshot findings, direct Sol review and parent acceptance; no autonomous retheme |

The parent remains the integration leader. **GPT-6.1 Sol runs directly in Codex.** OMP uses **`kimi-code/k3`**, **`zai/glm-5.3-flash`**, and security assignments on **`zai/glm-5.3`**. Never silently fall back or route GPT through OMP. The coordinator can prepare packets, dispatch within authorization, review outputs and return proposed patches. It cannot approve itself, expand payload permissions, bypass an approval rejection, commit/push, operate ADB or acquire shared host resources without explicit bounded assignment.

## Construction versus integrated acceptance

P4-S1 retains prerequisites **P1-S4 and P3-S1**; P4-S2/S3 retain P4-S1, and P4-S4 retains P3-S9 as well as the adapters. Host-only probes, library construction, build infrastructure and official-API research can proceed in explicitly bounded experiments before those integration prerequisites. Record hypothesis, owner, scope, stop condition, result and remaining gate. Do not label the whole P4 stage ready/done because a local executable compiles.

[HOST-EXP01](../03-platform/host-core-entry.md) starts with no listener, pairing, service installation, privilege escalation, remote jobs or protocol-manifest claim. It can run alongside client work without touching the Googlebook or host fixtures. Later core/job/session packets need their own real behavior and failure cases. A runnable local diagnostic is not a remotely usable host service.

Standard SSH, RDP, VNC, streaming and direct USB/file data retain their independent backends. The optional host control plane must not become a prerequisite for ordinary connections. Separate per-app pairing/scopes remain required when remote control is introduced; neither a tailnet route nor Moonlight pairing supplies that authority.

## Task packet and collision controls

Each assignment records the current base commit, stage/experiment, exact writable paths, upstream references and pins, accepted decisions, supplied content hashes, prohibited shared operations, finite deadline, checks, stop condition and output format. Put local raw prompts/results in ignored cache; publish only sanitized evidence and useful source. Tool-free OMP may propose complete code, but the parent/direct coordinator applies only reviewed files. Tool-enabled editing needs its own bounded tool/ownership setup; the current successful runs do not establish it.

Give workers enough approved context to implement a small complete operation. A missing API, layout or authority decision is a question to return, not permission to invent a stub. Keep shared schemas, dependency versions, Android/host integration contracts and Git under one owner. Disjoint paths may run concurrently; overlapping source uses isolated checkouts and sequential patch integration. Freeze source during artifact qualification.

Client and host compilers may run concurrently with distinct caches and recorded ownership. ADB, interactive authentication, host test servers, ports and system settings have one operator. Unfinished work, user changes and unrelated files must not enter another worker's checkpoint.

## UX and worker visual checks

Read the [worker visual contract](../04-design/worker-visual-acceptance.md) before any UI assignment. Current tokens and accepted external-feedback disposition control the design; the original Word feedback is not a replacement specification. The [remote-session presentation contract](../04-design/remote-session-presentation.md) controls fullscreen and local overlay behavior.

Both Kimi and Flash have now received and described a single synthetic 1440×1000 Cloudy specimen through the exact OMP routes. This establishes bounded image input and useful visual observations. Flash overstated footer clipping; parent review rejects that claim. Neither model's confidence establishes a layout defect or a visual pass. Static HTML images do not qualify native app behavior. The reviewed route and evidence are recorded in the [worker checkpoint](../06-validation/2026-10-16-parallel-host-workers.md).

## Googlebook foundation gate

Use [foundation readiness](foundation-readiness.md), the official source/API registers and exact accepted probe evidence before assigning client integration. A solid foundation means known behavior and explicit remaining limits, not relabeling every experiment as supported. Carry forward unchanged accepted results; run focused checks for changed boundaries and unresolved consequential risks. Require source/build/engine/device/attempt attribution, compiler/Lint and relevant behavior evidence, with parent review and retained failures. A callback or counter alone does not establish visible playback, physical input or successful authorization.

Keep native window/focus/lifecycle, physical keyboard/pointer release, app-owned credentials, actual render geometry, engine retirement and permission behavior in each relevant acceptance packet. The current pending positive vault use, normal cross-app Open/authenticated effect, accessibility/IME, fullscreen request and sustained performance gates remain explicit in the current state. Host compilation or service progress cannot close them. Missing hardware affects only its applicable lane; no unnecessary repeat of settled upstream or device checks is required.

## Checkpoints and context recovery

Before committing: inspect actual diff and worker results, run relevant compiler/AST/lint and meaningful changed-boundary tests, review staged files, run `git diff --cached --check`, commit a coherent milestone, push normally and compare remote branch SHA. Reuse settled unchanged upstream/device evidence. Never report skipped or merely compiled tests as executed.

On recovery read **AGENTS → execution-state → this record → relevant phase/experiment → latest evidence**. Then inspect actual Git/worktree, active worker and resource state. Resume the latest unresolved step, not a historical next-step recipe. Keep the current summary short; append reviewed milestones in history. Preserve model roles, host-only clarification, failed evidence, pending user questions, raw-data exclusions and the absence of a P1 stop boundary. Leave the full goal active until its actual exit is met.
