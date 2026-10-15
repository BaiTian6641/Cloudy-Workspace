# P0 — Qualification and decisions

**Status:** planned. **Active effort:** 15–28 person-days before contingency. **Purpose:** Resolve Googlebook desktop API/input/engine/window/protocol/device boundaries before full product commitment. Early SSH work may proceed independently; the full Console gate includes X11 and integrated files.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Estimates are active effort, not elapsed time or staffing commitments; re-estimate after P0. Stage IDs remain stable; the dependency order can place a later-numbered addition before an existing release gate.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P0-S1 | Inventory and reproducible qualification setup | 1–2 | None |
| P0-S2 | Platform API, lifecycle, and capability probes | 1–2 | P0-S1 |
| P0-S3 | SSH engine and terminal renderer qualification | 2–4 | P0-S2 |
| P0-S4 | Streaming, RDP/VNC, physical input and decoder qualification | 4–6 | P0-S2 |
| P0-S5 | USB provider and real programming workflow | 2–4 | P0-S1 |
| P0-S7 | X11 forwarding and native application-window feasibility | 3–6 | P0-S2, P0-S3 |
| P0-S6 | Network measurements and first system decision gate | 2–4 | P0-S3, P0-S4, P0-S5, P0-S7 |

## P0-S1 — Inventory and reproducible qualification setup

**Owner:** Device/QA owner. **Prerequisites:** None. **Status:** planned.

Work:

1. Record exact Googlebook SKU/build/API/ABI, WebView, input devices, display scaling, network, and available decoders.
2. Map each named host to its installed OS/build/GPU/driver/user session and Linux compositor; list actual boards/probes and reset identities.
3. Pair ADB through the documented model/port path; capture sanitized baseline diagnostics and a run manifest.
4. Select one primary host/project/probe and define a lab-side fallback topology before multiplying combinations.

Deliverables:

- Completed private inventory and sanitized public matrix
- Qualification run template populated with versions/topology

Exit evidence:

- ADB can install/run a minimal development build on the target, or an exact blocker is documented.
- No host OS, decoder, peripheral, or compositor support is inferred from model name.

Record evidence under `qualification/results/P0-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01. Official/upstream source IDs: G04, G01; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S2 — Platform API, lifecycle, and capability probes

**Owner:** Android/platform developer. **Prerequisites:** P0-S1. **Status:** planned.

Work:

1. Inspect the API tracker and pin candidate SDK/AndroidX versions for windows, caption bounds, input, files, and foreground-service experiments.
2. Build minimal resize/multi-instance/caption/focus probes with runtime guards and record experimental annotations.
3. Classify SSH, transfers, USB, and streaming lifecycle strategies independently; force relevant timeout/process-recreation cases.
4. Investigate supported local Linux VM attach/control interfaces; record absence or roadmap status without private-API assumptions.

Deliverables:

- API availability/compile-probe table
- Operation-specific lifecycle decision note and VM boundary record

Exit evidence:

- Every proposed API use has a version guard or fallback.
- System IME/launcher and Linux roadmap limits are reflected in release scope.

Record evidence under `qualification/results/P0-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01, Q-P02. Official/upstream source IDs: G03, G05, G06, G15, G16, G23, G26; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S3 — SSH engine and terminal renderer qualification

**Owner:** Terminal/Android developer. **Prerequisites:** P0-S2. **Status:** planned.

Work:

1. Compare maintained JVM/native SSH candidates for Android ABI, algorithms/trust, PTY/SFTP/jumps/forwards, X11 channel/cookie hooks, footprint, license and update ownership.
2. Embed a pinned local xterm.js experiment with a narrow native boundary and compare a native renderer if needed.
3. Run exact-byte paste, CJK IME/preedit, wide-cell/emoji, TUI, selection, resize and sustained-output cases with physical desktop inputs.
4. Record CPU/memory/input latency and accessible terminal behavior; select the engine/renderer or a bounded remediation experiment.

Deliverables:

- Renderer/SSH ADR with versions and rejected alternatives
- Input/TUI fixture results and local-renderer boundary probe

Exit evidence:

- First-trust/changed-key behavior and input correctness pass on actual hardware.
- Unresolved accessibility/IME failures block renderer adoption, not basic project planning.

Record evidence under `qualification/results/P0-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-C02, Q-A01. Official/upstream source IDs: G09, G10, G17, R09, R10; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S4 — Streaming, RDP/VNC, physical input and decoder qualification

**Owner:** Desktop/device developer. **Prerequisites:** P0-S2. **Status:** planned.

Work:

1. Pin Sunshine/Moonlight and prove external handoff; select buildable RDP/VNC engine candidates and record actual server/encoding/security negotiation.
2. Exercise trackpad and external mouse in real CAD and text/desktop workloads; verify focus-loss release and native IME/keyboard paths.
3. Enumerate codecs and optional API-30 low-latency support; measure native decode/pacing/text quality, power and input-to-photon baseline.
4. Test primary host sessions, lock/headless/permissions, RDP certificate/NLA requirements and protected VNC routes; record unsupported cases and dependencies.

Deliverables:

- Pinned stream/RDP/VNC engine and server feasibility matrix
- Native decoder/input baseline, measured boundaries and external handoff contract

Exit evidence:

- The primary stream workflow passes or has an explicit tested fallback; RDP and VNC each have a build/integration path and blocker list.
- No inference of latency, RemoteApp, server security or trackpad support from a package name; unqualified combinations remain excluded.

Record evidence under `qualification/results/P0-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-D02, Q-D07, Q-D08, Q-N02, Q-P02. Official/upstream source IDs: G02, G09, G20, G29, G30, R04, R06, R21, R23, R24, R25, R27; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S5 — USB provider and real programming workflow

**Owner:** Embedded/device developer. **Prerequisites:** P0-S1. **Status:** planned.

Work:

1. Verify Android provider/workstation consumer licensing, permission ownership and consumer-side API on the actual USB topology.
2. Program/verify the actual board/probe with runtime/bootloader/DFU/composite identities recorded.
3. Probe hotplug, replug, permission denial, auto-use toggle/state semantics, consumer restart, contention and protected ownership reconciliation in a controlled lab.
4. If Android forwarding fails, qualify a lab-side provider/hardware server and report that the local Googlebook plug-and-play requirement remains unmet.

Deliverables:

- Probe-specific flash/verify evidence, enrollment identity set and permission map
- Backend control/auto-use decision and tested fallback topology

Exit evidence:

- The actual target programs/verifies; serial-only success does not certify JTAG/DFU.
- Ownership recovery is established without automatic flash retry or timer-based reassignment.

Record evidence under `qualification/results/P0-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D04, Q-D06. Official/upstream source IDs: G14, R07, R28; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S7 — X11 forwarding and native application-window feasibility

**Owner:** X11/Android/terminal developer. **Prerequisites:** P0-S2, P0-S3. **Status:** planned.

Work:

1. Prove ordinary SSH x11-req/cookie substitution with remote xauth and two isolated host contexts.
2. Build a maintained Android X server candidate and prototype top-level, transient-dialog and popup mapping to Googlebook native windows without root/private APIs.
3. Test physical keyboard/IME, focus, caption/resize/display scaling, clipboard boundaries and disconnect/process-death semantics.
4. Record exact engine/license/build/backend and broker cost; reject a single X desktop as completion of the separate-window requirement.

Deliverables:

- X11 engine/window-broker feasibility ADR and prototype evidence
- Per-application window support/failure matrix and re-estimate

Exit evidence:

- Two remote apps have independent native desktop windows and correct owned dialogs, or the required capability is explicitly blocked with a scope proposal.
- Cookie/trust context isolation and actual GUI connection lifetime are demonstrated; no root/shared-signing/private VM dependency.

Record evidence under `qualification/results/P0-S7/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C07, Q-C08, Q-P02, Q-A01. Official/upstream source IDs: G03, G10, G24, G27, G28, R17, R18, R19, R20, R33; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S6 — Network measurements and first system decision gate

**Owner:** Network/QA owner. **Prerequisites:** P0-S3, P0-S4, P0-S5, P0-S7. **Status:** planned.

Work:

1. Measure direct/relay routing for stream/RDP/VNC/X11/files and reverse USB with actual firewall/tailnet policies.
2. Record independent payload/wire counters plus repeatable Moonlight/Parsec comparisons where supported; label GeForce NOW contextual.
3. Run controlled latency/jitter/loss/route/outage profiles outside protected programming and measure adaptation/recovery/input integrity.
4. Ratify or revise desktop budgets and full workflow estimates; publish explicit blockers, tested fallbacks and support exclusions.

Deliverables:

- Primary topology latency/pacing/traffic/recovery baseline with raw evidence
- P0 go/adjust decision for all revised requirements and desktop-only scope

Exit evidence:

- Each critical input/probe/backend risk has evidence or an accepted qualified fallback.
- Console can progress independently even while a Desktop hardware branch remains unresolved.

Record evidence under `qualification/results/P0-S6/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-N01, Q-N02, Q-N03, Q-X03. Official/upstream source IDs: R08, R16, G21, R29, R30; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

An X11 multiwindow blocker affects the full Console scope; a USB/backend blocker affects the relevant Desktop capability. Preserve useful partial builds, state unmet requirements explicitly, and re-estimate after evidence. Every done stage links actual evidence; a documentation check does not qualify a device.
