# P0 — Qualification and decisions

**Status:** P0-S1 complete; P0-S2 in progress. **Active effort:** 15–28 person-days before contingency. **Purpose:** Resolve Googlebook desktop API/input/engine/window/protocol/device boundaries using scoped capability outcomes. Independent branches do not inherit an X11 phase-wide blocker.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P0-S1 | Inventory and reproducible qualification setup | 1–2 | None |
| P0-S2 | Platform API, lifecycle, and capability probes | 1–2 | P0-S1 |
| P0-S3 | SSH engine and terminal renderer qualification | 2–4 | P0-S2 |
| P0-S4 | Streaming, RDP/VNC, physical input and decoder qualification | 4–6 | P0-S2 |
| P0-S5 | USB provider and real programming workflow | 2–4 | P0-S1 |
| P0-S7 | X11 forwarding and native application-window feasibility | 3–6 | P0-S2, P0-S3 |
| P0-S6 | Network measurements and scoped system decisions | 2–4 | P0-S3, P0-S4, P0-S5 |

## P0-S1 — Inventory and reproducible qualification setup

**Owner:** Device/QA owner. **Prerequisites:** None. **Status:** done for setup scope.

Initial [USB baseline](../../../qualification/results/P0-S1/2026-10-15-usb-baseline.md) records authorization and reported build properties. The [real diagnostic APK run](../../../qualification/results/P0-S1/2026-10-15-device-probe.md) now proves build/install/launch and bounded API observations. The [setup closeout](../../../qualification/results/P0-S1/2026-10-15-setup-closeout.md) completes primary route/project inventory and unavailable-hardware classification, with both hosted check workflows passed. Feature qualification stays in its own later stages.

Work:

1. Record exact Googlebook SKU/build/API/ABI, WebView, input devices, display scaling, network, and available decoders.
2. Map each named host to its installed OS/build/GPU/driver/user session and Linux compositor; list actual boards/probes and reset identities.
3. Pair ADB through the documented model/port path; capture sanitized baseline diagnostics and a run manifest.
4. Select one primary host/project and define a lab-side fallback topology. The user left the board/probe undecided; record it as an explicit inventory gap rather than inventing hardware.

Deliverables:

- Completed private inventory and sanitized public matrix
- Qualification run template populated with versions/topology

Exit evidence:

- ADB can install/run a minimal development build on the target, or an exact blocker is documented.
- No host OS, decoder, peripheral, or compositor support is inferred from model name.

Record evidence under `qualification/results/P0-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01. Official/upstream source IDs: G04, G01; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S2 — Platform API, lifecycle, and capability probes

**Owner:** Android/platform developer. **Prerequisites:** P0-S1 complete. **Status:** in-progress.

The [operation-lifetime research](../../03-platform/googlebook/p0-s2-operation-lifetimes.md) records API13/API14 and API39/API40 proposals, runtime/target guards, fallbacks and remaining timeout/process-loss experiments. Supplemental official sources G59–G68 cover UIDT, foreground-service prerequisites, Android 17 audio, actual USB permission and distribution review. No continuous-session service type or policy approval is selected, and no capability gate is promoted by this research.

Work:

1. Inspect the API tracker and pin candidate SDK/AndroidX versions for windows, caption bounds, input, files, and foreground-service experiments.
2. Build minimal resize/multi-instance/caption/focus probes with runtime guards and record experimental annotations.
3. Classify SSH, transfers, USB, and streaming lifecycle strategies independently; force relevant timeout/process-recreation cases.
4. Investigate supported local Linux VM attach/control interfaces; record absence or roadmap status without private-API assumptions.
5. Record CAP-PLATFORM as supported, bounded experiment or blocked, with scope and evidence; investigation completion does not activate dependent implementation.

Deliverables:

- API availability/compile-probe table
- Operation-specific lifecycle decision note and VM boundary record
- CAP-PLATFORM outcome and any bounded follow-up experiment

Exit evidence:

- Every proposed API use has a version guard or fallback.
- System IME/launcher and Linux roadmap limits are reflected in release scope.

Record evidence under `qualification/results/P0-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01, Q-P02. Official/upstream source IDs: G03, G05, G06, G15, G16, G23, G26; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S3 — SSH engine and terminal renderer qualification

**Owner:** Terminal/Android developer. **Prerequisites:** P0-S2. **Status:** planned.

Work:

1. Compare maintained JVM/native SSH candidates for Android ABI, algorithms/trust, PTY/SFTP/jumps/forwards, X11 channel/cookie hooks, footprint, license and update ownership; probe SSH authentication and SFTP operations independently of any terminal renderer.
2. Embed a pinned local xterm.js experiment with a narrow native boundary and compare a native renderer if needed.
3. Run exact-byte paste, CJK IME/preedit, wide-cell/emoji, TUI, selection, resize and sustained-output cases with physical desktop inputs.
4. Record CPU/memory/input latency and accessible terminal behavior; select the engine/renderer or a bounded remediation experiment.
5. Record separate CAP-SSH and CAP-TERMINAL feasibility verdicts with scope and evidence. Final supported readiness also requires the relevant P1-S4 adoption decision; renderer-only IME/accessibility failure must not block a qualified SSH/SFTP route or be hidden by its success.

Deliverables:

- Renderer/SSH ADR with versions and rejected alternatives
- Input/TUI fixture results and local-renderer boundary probe
- Separate CAP-SSH and CAP-TERMINAL outcomes with any bounded follow-up experiments

Exit evidence:

- First-trust/changed-key, SSH/SFTP and terminal-input probes have separate actual-hardware verdicts; an unsuccessful route is blocked or a bounded experiment and cannot inherit another route's pass.
- Unresolved accessibility/IME failures block terminal renderer adoption, not a separately supported SSH/SFTP approach or completion of the investigation record.

Record evidence under `qualification/results/P0-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-C02, Q-A01, Q-F02. Official/upstream source IDs: G09, G10, G17, R09, R10; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S4 — Streaming, RDP/VNC, physical input and decoder qualification

**Owner:** Desktop/device developer. **Prerequisites:** P0-S2. **Status:** planned.

Work:

1. Pin Sunshine/Moonlight and prove external handoff; select buildable RDP/VNC engine candidates and record actual server/encoding/security negotiation.
2. Exercise trackpad and external mouse in real CAD and text/desktop workloads; verify focus-loss release and native IME/keyboard paths.
3. Enumerate codecs and optional API-30 low-latency support; measure native decode/pacing/text quality, power and input-to-photon baseline.
4. Test primary host sessions, lock/headless/permissions, RDP certificate/NLA requirements and protected VNC routes; record unsupported cases and dependencies.
5. Record separate CAP-RDP, CAP-VNC and stream-feasibility outcomes; document native surface/input and integration blockers independently of external launch success.
6. Ratify provisional native-stream and protocol-specific responsiveness budgets from the primary-lane probes; missing USB or X11 evidence does not block this decision.

Deliverables:

- Pinned stream/RDP/VNC engine and server feasibility matrix
- Native decoder/input baseline, measured boundaries and external handoff contract

Exit evidence:

- The primary stream workflow and RDP/VNC integration probes have scoped verdicts, tested fallbacks or explicit blockers; blocked lanes do not inherit another lane's pass.
- No inference of latency, RemoteApp, server security or trackpad support from a package name; unqualified combinations remain excluded.
- RDP/VNC supported outcomes require actual engine integration probes; a candidate name or build alone cannot activate its product stage.

Record evidence under `qualification/results/P0-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-D02, Q-D07, Q-D08, Q-N02, Q-P02. Official/upstream source IDs: G02, G09, G20, G29, G30, R04, R06, R21, R23, R24, R25, R27; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S5 — USB provider and real programming workflow

**User decision, 2026-10-15:** no board/probe is available for selection yet. Full general-purpose USB implementation remains required. Continue API/provider/licensing and real software-state work; physical forwarding, reset and programming results remain pending. General scope is not a universal hardware-support claim, and synthetic fixtures cannot satisfy CAP-USB.

**Owner:** Embedded/device developer. **Prerequisites:** P0-S1. **Status:** planned.

Work:

1. Verify Android provider/workstation consumer licensing, permission ownership and consumer-side API on the actual USB topology.
2. Program/verify the actual board/probe with runtime/bootloader/DFU/composite identities recorded.
3. Probe hotplug, replug, permission denial, auto-use toggle/state semantics, consumer restart, contention and protected ownership reconciliation in a controlled lab.
4. If Android forwarding fails, qualify a lab-side provider/hardware server and report that the local Googlebook plug-and-play requirement remains unmet.
5. Record CAP-USB feasibility verdict with scope and evidence; its final supported readiness also requires the relevant P1-S4 engine/distribution decision, and investigation completion alone does not activate implementation.

Deliverables:

- Probe-specific flash/verify evidence, enrollment identity set and permission map
- Backend control/auto-use decision and tested fallback topology
- CAP-USB outcome and any bounded follow-up experiment

Exit evidence:

- The actual target's program/verify attempt has evidence and a scoped verdict; serial-only success never certifies JTAG/DFU, and failure leaves that capability blocked.
- Ownership recovery is demonstrated without automatic flash retry or timer-based reassignment, or its exact blocker and bounded next experiment are recorded.

Record evidence under `qualification/results/P0-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D04, Q-D06. Official/upstream source IDs: G14, R07, R28; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S7 — X11 forwarding and native application-window feasibility

**Owner:** X11/Android/terminal developer. **Prerequisites:** P0-S2, P0-S3. **Status:** planned.

Work:

1. Prove ordinary SSH x11-req/cookie substitution with remote xauth and two isolated host contexts.
2. Build a maintained Android X server candidate and prototype top-level, transient-dialog and popup mapping to Googlebook native windows without root/private APIs.
3. Test physical keyboard/IME, focus, caption/resize/display scaling, clipboard boundaries and disconnect/process-death semantics.
4. Record exact engine/license/build/backend and broker cost; reject a single X desktop as completion of the separate-window requirement.
5. Record CAP-X11 feasibility verdict with scope and evidence; its final supported readiness also requires the relevant P1-S4 engine/distribution decision, and investigation completion alone does not activate implementation.
6. Record X11 network/redraw and lifetime behavior for the full Console gate; attach the result to the scoped system decision after it exists.

Deliverables:

- X11 engine/window-broker feasibility ADR and prototype evidence
- Per-application window support/failure matrix and re-estimate
- CAP-X11 outcome and any bounded follow-up experiment

Exit evidence:

- Two remote apps have independent native desktop windows and correct owned dialogs, or the required capability is explicitly blocked with a scope proposal.
- Cookie/trust isolation and actual GUI lifetime have recorded verdicts; a supported route cannot rely on root/shared-signing/private VM behavior, and failed cases remain blocked.

Record evidence under `qualification/results/P0-S7/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C07, Q-C08, Q-P02, Q-A01. Official/upstream source IDs: G03, G10, G24, G27, G28, R17, R18, R19, R20, R33; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S6 — Network measurements and scoped system decisions

**Owner:** Network/QA owner. **Prerequisites:** P0-S3, P0-S4, P0-S5. **Status:** planned.

Work:

1. Measure direct/relay routing for available stream/RDP/VNC/files and reverse USB branches; add X11 evidence from P0-S7 when available without blocking unrelated lanes.
2. Record independent payload/wire counters plus repeatable Moonlight/Parsec comparisons where supported; label GeForce NOW contextual.
3. Run controlled latency/jitter/loss/route/outage profiles outside protected programming and measure adaptation/recovery/input integrity.
4. Ratify or revise per-lane desktop budgets and estimates; record explicit blockers, tested fallbacks and support exclusions for each branch.
5. Reconcile capability outcomes and stage readiness; publish scoped go/experiment/blocked decisions and re-estimate after evidence without treating completed research as feature support.

Deliverables:

- Primary topology latency/pacing/traffic/recovery baseline with raw evidence
- Scoped go/experiment/blocked decisions; X11 investigation remains separately required for full Console

Exit evidence:

- Each measured lane has evidence, limits and a capability decision; pending X11 work is named and cannot be counted as a passing result.
- Neither app waits for an unrelated capability decision; its own required gates remain mandatory.

Record evidence under `qualification/results/P0-S6/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-N01, Q-N02, Q-N03, Q-X03. Official/upstream source IDs: R08, R16, G21, R29, R30; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

An investigation may complete with a blocked capability record. Only a scoped supported outcome activates its dependent product work. Full Console still needs X11; Desktop can qualify independently. Re-estimate after evidence. Every done stage links actual evidence; a documentation check does not qualify a device.
