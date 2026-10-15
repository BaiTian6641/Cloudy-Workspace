# P0 — Qualification and decisions

**Status:** P0-S1 complete; P0-S2 investigation complete with CAP-PLATFORM `experiment`; P0-S3, P0-S4 and P0-S5 investigations in progress. **Active effort:** 15–28 person-days before contingency. **Purpose:** Resolve Googlebook desktop API/input/engine/window/protocol/device boundaries using scoped capability outcomes. Independent branches do not inherit an X11 phase-wide blocker.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

Apply [D12: established libraries and proportionate verification](../library-reuse-policy.md) to every engine/provider investigation. Review adoption once, reuse upstream coverage, and test the actual Cloudy boundary with representative success/failure cases. Carry forward accepted evidence, expand only on a concrete trigger, and close each investigation with a scoped decision. Existing task prerequisites and physical-device exit criteria remain in force; terminal remediation does not prevent P0-S4/P0-S5 investigation on their own prerequisites.

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

**Owner:** Android/platform developer. **Prerequisites:** P0-S1 complete. **Status:** done for the initial investigation and bounded-experiment decision; platform support remains unqualified.

The [platform decision](../../03-platform/googlebook/p0-s2-platform-decision.md) records CAP-PLATFORM as **experiment**, with accepted public evidence, a two-engineer-day native SSH ownership/classification follow-up and explicit stop conditions. The API/compile subset, [operation decisions](../../03-platform/googlebook/p0-s2-operation-lifetimes.md) and [VM boundary](../../03-platform/googlebook/p0-s2-linux-vm-boundary.md) complete the initial investigation deliverables. Q-P01/Q-P02 remain partial; no continuous-session FGS type, policy approval or product readiness is established. P1-S1/P1-S2/P1-S3 remain planned under the existing supported-capability gate.

The [window run](../../../qualification/results/P0-S2/2026-10-15-window-probe.md), [process restoration](../../../qualification/results/P0-S2/2026-10-15-process-restoration.md), [font-scale run](../../../qualification/results/P0-S2/2026-10-15-font-scale.md) and [engagement run](../../../qualification/results/P0-S2/2026-10-15-engagement-probe.md) establish bounded native window/lifecycle observations. Last-window task removal left no persisted final cleanup report. The accepted [transfer preflight](../../../qualification/results/P0-S2/2026-10-15-transfer-preflight.md) and [0.4.1 lifecycle run](../../../qualification/results/P0-S2/2026-10-15-transfer-lifecycle.md) add finite HTTPS completion, permission UI, cancellation, failure/retry and injected-loss recovery. Physical keyboard/mouse/native IME/focus release, full Q-P02 display/fullscreen cases, continuous operation classification and remaining UIDT failure cases are carried forward explicitly. No timed FGS was selected, so accelerated FGS timeout cases are conditional future work rather than fabricated passes.

Work:

1. Inspect the API tracker and pin candidate SDK/AndroidX versions for windows, caption bounds, input, files, direct LAN permission, and foreground-service experiments.
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

Record evidence under `qualification/results/P0-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01, Q-P02, both partial at this investigation closeout. Official/upstream source IDs: G03, G05, G06, G15, G16, G23, G26, G72, G78–G81, G98–G99; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S3 — SSH engine and terminal renderer qualification

**Owner:** Terminal/Android developer. **Prerequisites:** P0-S2 investigation decision complete. **Status:** in-progress for engine/renderer qualification.

Begin with real native SSH trust/authentication/SFTP qualification independently of the renderer, then retain the full physical input/IME/accessibility and renderer work below. The [CAP-PLATFORM follow-up](../../03-platform/googlebook/p0-s2-platform-decision.md) evaluates connection ownership and any justified background route alongside this investigation. CAP-SSH/CAP-TERMINAL remain pending, and neither this stage activation nor the bounded platform experiment activates a dependent product stage.

Work:

1. Compare maintained JVM/native SSH candidates for Android ABI, algorithms/trust, PTY/SFTP/jumps/forwards, X11 channel/cookie hooks, footprint, license and update ownership; probe SSH authentication and SFTP operations independently of any terminal renderer.
2. Embed a pinned local xterm.js experiment with a narrow native boundary and compare a native renderer if needed.
3. Run exact-byte paste, CJK IME/preedit, wide-cell/emoji, TUI, selection, resize and sustained-output cases with physical desktop inputs. Apply the [desktop interaction matrix](../../04-design/desktop-terminal-interaction-contract.md), including shortcuts, direct pointer selection, context menus and native field editing; mobile component defaults do not waive these requirements.
4. Record CPU/memory/input latency and accessible terminal behavior; select the engine/renderer or a bounded remediation experiment.
5. Record separate CAP-SSH and CAP-TERMINAL feasibility verdicts with scope and evidence. Final supported readiness also requires the relevant P1-S4 adoption decision; renderer-only IME/accessibility failure must not block a qualified SSH/SFTP route or be hidden by its success.

Deliverables:

- Renderer/SSH ADR with versions and rejected alternatives
- Input/TUI fixture results and local-renderer boundary probe
- Separate CAP-SSH and CAP-TERMINAL outcomes with any bounded follow-up experiments

Exit evidence:

- First-trust/changed-key, SSH/SFTP and terminal-input probes have separate actual-hardware verdicts; an unsuccessful route is blocked or a bounded experiment and cannot inherit another route's pass.
- Unresolved accessibility/IME failures block terminal renderer adoption, not a separately supported SSH/SFTP approach or completion of the investigation record.

The [0.5.0 SSH/SFTP run](../../../qualification/results/P0-S3/2026-10-15-ssh-sftp-probe.md) supplies the first real Android/backend trust, key-authentication and exclusive-create integrity evidence. It retains five attempts and qualifies only the finite diagnostic route. The subsequent [0.6.0 exec/PTY run](../../../qualification/results/P0-S3/2026-10-15-ssh-pty-probe.md) adds actual exec/PTY bytes and resize, active recreation/two-observer ownership, interruption without replay and explicit cancellation/helper cleanup. All eight attempts remain retained in byte-matched archives after an [actual restoration drill](../../../qualification/results/P0-S3/2026-10-15-ssh-batch-rotation.md). A separate [four-attempt lifecycle run](../../../qualification/results/P0-S3/2026-10-15-ssh-lifecycle.md) adds last-observer minimize cancellation, sole-task and permission-change process-loss reconciliation, and explicit post-grant completion without automatic replay. The [real terminal increment](../../03-platform/googlebook/p0-s3-terminal-renderer-increment.md) now has a reviewed, compiled 0.7.0 ordinary-shell/renderer [source and package candidate](../../06-validation/2026-10-15-terminal-implementation-review.md), with 198 JVM tests plus bounded byte-flow tests. The [eight-attempt ordinary-shell run](../../../qualification/results/P0-S3/2026-10-15-terminal-probe.md) now records actual WebView/SSH output, PTY resize, curses navigation, exact controlled input, explicit disconnect and interruption/recovery. The tested 0.7.1 correction transfers native focus on Connect; rapid ADB input still duplicates received bytes. The [terminal archive/restore drill](../../../qualification/results/P0-S3/2026-10-15-terminal-batch-rotation.md) now preserves all eight attempts. A [new one-attempt trace run](../../../qualification/results/P0-S3/2026-10-15-terminal-input-trace.md) observes four DOM insertions but ten host bytes for rapid input, versus four paced bytes; an uninstrumented repeat also duplicates. The [upstream candidate and exact-source comparison](../../07-reference/2026-10-15-xterm-input-fix-candidate.md) led to the reviewed [0.7.2 official core/fit candidate](../../06-validation/2026-10-15-terminal-upstream-review.md). Actual-library rapid/repeated text is corrected in Chrome, while two synthetic editing limits remain. The exact APK is installed: the [bounded device follow-up](../../../qualification/results/P0-S3/2026-10-15-terminal-upstream-device.md) receives four correct bytes for both rapid and paced input, verifies actual host PTY resize 146×23 → 205×32 → 146×23, and closes cleanly. This closes the named rapid-injection regression; full input acceptance remains open under [D12](../library-reuse-policy.md). Keep physical input/IME/accessibility acceptance separate. Continuous/background classification, renderer/IME/accessibility and physical input remain pending. CAP-SSH/CAP-TERMINAL remain pending.

Record evidence under `qualification/results/P0-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-C02, Q-A01, Q-F02. Official/upstream source IDs: G09, G10, G17, R09, R10, R121, R130–R131, R138; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S4 — Streaming, RDP/VNC, physical input and decoder qualification

**Owner:** Desktop/device developer. **Prerequisites:** P0-S2. **Status:** in-progress; FreeRDP native/JNI/APK, trust/NLA and bounded real framebuffer/explicit cleanup observed; input/performance and other engine lanes remain pending.

The [FreeRDP3.32.2 native/JNI build](../../06-validation/2026-10-15-native-rdp-build.md) now compiles for arm64/API29 with MediaCodec. Carry forward its exact inputs and static ELF evidence; the [current device result](../../../qualification/results/P0-S4/2026-10-15-rdp-framebuffer.md) now observes real changing pixels and explicit cleanup. The next boundary is controlled input/focus/cancel and sustained resource/performance qualification. The [API decision](../../07-reference/2026-10-15-rdp-android-mediacodec.md) records why H.264 is enabled, experimental output-layout/fallback limits and the current host package-upgrade constraint. No CAP-RDP promotion follows from a compile; VNC, streaming and physical-input branches keep their own prerequisites.

The [native desktop entry brief](../../03-platform/googlebook/p0-s4-native-desktop-entry.md) records the accepted FreeRDP 3.32.2 Android build and the [exact LibVNC patched-source candidate](../../07-reference/2026-10-15-vnc-patched-source-selection.md), with focused advisory regression and Android build/integration checks remaining before a network probe. Platform/library availability, compilation and actual server/device support remain separate. CAP-RDP/CAP-VNC remain pending.

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

Record evidence under `qualification/results/P0-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-D02, Q-D07, Q-D08, Q-N02, Q-P02. Official/upstream source IDs: G02, G09, G20, G29, G30, R04, R06, R21, R23, R24, R25, R27, R133–R137, R140; resolve them in the [source register](../../07-reference/source-register.md).

Current bounded increment: the [real RDP diagnostic APK/load result](../../../qualification/results/P0-S4/2026-10-15-rdp-apk-load.md) passes local compiler/lint/14 JVM tests, actual patched queue checks and Googlebook native loading. It records zero RDP connections; server trust/NLA/pixels/input/cancellation and performance remain unqualified. This does not change stage dependencies or capability readiness.

The [first native RDP sessions](../../../qualification/results/P0-S4/2026-10-15-rdp-first-sessions.md) now establish actual pin rejection, NLA connection and bounded join/free or process-interruption reconciliation. No frame/input result passes: source-guided host setup reaches capture, then the Android adapter fails before delivery. The [framebuffer follow-up](../../../qualification/results/P0-S4/2026-10-15-rdp-framebuffer.md) preserves this failed case, measures the resize cause and validates the bounded correction with changing pixels and explicit cleanup. Input/performance remain pending; P0-S4 and CAP-RDP remain incomplete.

## P0-S5 — USB provider and real programming workflow

**User decision, 2026-10-15:** no board/probe is available for selection yet. Full general-purpose USB implementation remains required. Continue API/provider/licensing and real software-state work; physical forwarding, reset and programming results remain pending. General scope is not a universal hardware-support claim, and synthetic fixtures cannot satisfy CAP-USB.

**Owner:** Embedded/device developer. **Prerequisites:** P0-S1. **Status:** in-progress for provider and native-access investigation.

The [USB provider entry](../../03-platform/googlebook/p0-s5-usb-provider-entry.md) separates Android grant-owned libusb access, network export and workstation import. The [real native-access diagnostic](../../06-validation/2026-10-15-native-usb-review.md) now builds without a physical peripheral; missing hardware leaves granted-device and forwarding/programming results pending. External provider setup remains a partial milestone.

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

## D15 RDP input and host clarification

The [0.1.3 input result](../../../qualification/results/P0-S4/2026-10-15-rdp-input-boundary.md) is a retained failed host-input case with continued video and correct explicit native cleanup. The [portal/relative follow-up](../../../qualification/results/P0-S4/2026-10-15-rdp-relative-portal.md) resolves the consented keyboard-delivery boundary and adds standard relative capture/release; captured remote movement fails on the exact KRdp server. The earlier isolated GNOME physical failure remains recorded; the [0.1.5 cursor repair](../../../qualification/results/P0-S4/2026-10-15-rdp-cursor-repair.md) now has user-confirmed host trail/cursor movement and release. Input subtype and actual3D remain pending. The [0.1.6 rendering sample](../../../qualification/results/P0-S4/2026-10-15-rdp-rendering.md) shows no useful timing improvement from reduced status/control work. The [27-case LibVNC host regression result](../../../qualification/results/P0-S4/2026-10-15-vnc-regressions.md) closes three named advisory checks; Android/TLS/server integration remains pending.

P0-S4 now explicitly applies [D15](../../04-design/desktop-rdp-interaction-contract.md): standard RDP server compatibility across target Linux/Windows/macOS rows, desktop mouse/keyboard/IME and actual negotiated relative mouse for 3D. Q-D09 qualifies capture, protocol and application consumption separately. P1-S3 owns the [Googlebook credential vault](../../02-architecture/rdp-authentication-security.md), with Q-S02 integrated at P3-S7. Missing host hardware/server capability is recorded per row; it neither certifies that lane nor prevents Linux work. Stage dependencies and capability gates remain unchanged.

The [native-stream source entry](../../03-platform/googlebook/native-stream-adoption-entry.md) pins Moonlightv12.2 and its actual decoder/pairing/connection seams. [ADR0010](../../adr/0010-desktop-open-source-distribution.md) selects open-source Desktop distribution; actual corresponding-source/notices/build and compatibility work remains P1-S4/P1-S5. Stage dependencies and capability gates are unchanged.

The [VNC Android TLS/JPEG build](../../../qualification/results/P0-S4/2026-10-15-vnc-native-build.md) closes its native compilation increment. JNI/ownership, protected security negotiation and Android/server behavior remain pending. The [streaming0.1.0 APK/load result](../../../qualification/results/P0-S4/2026-10-15-native-stream-apk-load.md) establishes real MoonBridge initialization/metadata on Googlebook with no connection or decoding. Next implement the [owned Keystore identity and finite pairing/HTTP boundary](../../03-platform/googlebook/native-stream-key-provider-entry.md), then real Surface/MediaCodec streaming. These bounded results carry accepted upstream evidence forward under D12; P0-S4 remains in progress and no downstream capability gate is promoted.


The [VNC JNI/security adapter build](../../../qualification/results/P0-S4/2026-10-15-vnc-adapter-build.md) now implements the bounded native ownership and X509Plain/exact-leaf policy; APK/runtime and real protected-server tests remain pending. The [stream identity diagnostic](../../../qualification/results/P0-S4/2026-10-15-stream-identity.md) generates a pending TEE-backed key but fails plain-JCA initialization6ms after a real biometric callback on0.1.2. Preserve that key and compare an explicit credential-only prompt before changing crypto/provider policy. The [Sunshine preflight](../../03-platform/googlebook/native-stream-host-entry.md) establishes an extracted AppImage dependency candidate, without host execution/capture/input evidence. These are separate integration increments; P0-S4 stays in progress with no dependency or capability promotion.


The [0.1.3 credential control](../../../qualification/results/P0-S4/2026-10-15-stream-credential-control.md) now passes real signing, certificate enrollment and fresh-process metadata reload on that same TEE key, after a device-credential type1 callback. The earlier biometric failure remains unresolved. Use this explicitly qualified local credential route for the next finite TLS/pairing boundary, without widening key policy or inferring a stream/vault pass. Phase dependencies and capability gates remain unchanged.
