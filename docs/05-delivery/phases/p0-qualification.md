# P0 — Qualification and decisions

**Status:** planned. **Active effort:** 8–16 person-days before contingency. **Purpose:** Resolve hardware/API boundaries before product commitment. Console-only work may advance after P0-S3 while desktop/probe investigations continue.

The canonical stage data is [backlog.json](../backlog.json). Estimates assume one experienced developer using established components; hardware access and upstream blockers can add elapsed time. Owner labels are roles, not staffed assignments.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P0-S1 | Inventory and reproducible qualification setup | 1–2 | None |
| P0-S2 | Platform API, lifecycle, and capability probes | 1–2 | P0-S1 |
| P0-S3 | SSH engine and terminal renderer qualification | 2–4 | P0-S2 |
| P0-S4 | Desktop backend, physical input, and decode qualification | 2–3 | P0-S2 |
| P0-S5 | USB provider and real programming workflow | 1–3 | P0-S1 |
| P0-S6 | Network measurements and first system decision gate | 1–2 | P0-S3, P0-S4, P0-S5 |

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

Record evidence under `qualification/results/P0-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01. Official source IDs: G04, G01; resolve them in the [source register](../../07-reference/source-register.md).

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

Record evidence under `qualification/results/P0-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P01, Q-P02. Official source IDs: G03, G05, G06, G15, G16, G23, G26; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S3 — SSH engine and terminal renderer qualification

**Owner:** Terminal/Android developer. **Prerequisites:** P0-S2. **Status:** planned.

Work:

1. Compare maintained JVM and native SSH candidates for Android ARM64, host-key policy, algorithms, PTY/SFTP/jumps/forwards, footprint, and update ownership.
2. Embed a pinned local xterm.js experiment behind a narrow native boundary and compare a native renderer if needed.
3. Run exact-byte paste, CJK IME/preedit, wide-cell/emoji, alternate-screen TUI, selection, resize, and sustained-output cases.
4. Record CPU/memory/input latency and screen-reader behavior; select engine/renderer or a time-bounded remediation experiment.

Deliverables:

- Renderer/SSH ADR with versions and rejected alternatives
- Input/TUI fixture results and local-renderer boundary probe

Exit evidence:

- First-trust/changed-key behavior and input correctness pass on actual hardware.
- Unresolved accessibility/IME failures block renderer adoption, not basic project planning.

Record evidence under `qualification/results/P0-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-C02, Q-A01. Official source IDs: G09, G10, G17, R09, R10; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S4 — Desktop backend, physical input, and decode qualification

**Owner:** Desktop/device developer. **Prerequisites:** P0-S2. **Status:** planned.

Work:

1. Install/pair qualified Sunshine/Moonlight versions and discover whether a documented external-launch/profile mechanism exists.
2. Exercise real trackpad and external mouse capture/release in the actual CAD application; trace button/modifier clearing on focus loss.
3. Enumerate and measure selected decoder/profile behavior with native surfaces, text readability, frame pacing, and power.
4. Test Windows/macOS/selected Wayland session states separately; record missing permissions, lock/headless limits, and manual-launch fallback.

Deliverables:

- Pinned backend/input/decoder matrix
- Supported external handoff contract or honest manual-launch flow

Exit evidence:

- Required visual workflow passes on the primary host with a released input path.
- Unsupported OS/compositor/trackpad cases remain explicitly excluded or have a qualified fallback.

Record evidence under `qualification/results/P0-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-D02, Q-P02. Official source IDs: G09, G20, R04, R05, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S5 — USB provider and real programming workflow

**Owner:** Embedded/device developer. **Prerequisites:** P0-S1. **Status:** planned.

Work:

1. Verify Android VirtualHere provider and workstation consumer setup/licensing/control interfaces for the actual topology.
2. Program and verify the actual board through the primary probe, including reset/bootloader/DFU identities and composite interfaces.
3. Exercise explicit release, transport loss, reenumeration timeout, owner reconciliation, and protected-operation policy in a safe lab run.
4. If Android forwarding fails, qualify a lab-side Linux provider or vendor hardware-server workflow and record the changed topology.

Deliverables:

- Probe-specific flash/verify evidence and identity set
- Backend ownership/control interface decision and fallback run

Exit evidence:

- The actual target programs/verifies; serial-only success does not certify JTAG/DFU.
- Ownership recovery is established without automatic flash retry or timer-based reassignment.

Record evidence under `qualification/results/P0-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D04. Official source IDs: G14, R07; resolve them in the [source register](../../07-reference/source-register.md).

## P0-S6 — Network measurements and first system decision gate

**Owner:** Network/QA owner. **Prerequisites:** P0-S3, P0-S4, P0-S5. **Status:** planned.

Work:

1. Measure direct/relay routing in both desktop and reverse USB directions, with actual firewall/tailnet policies.
2. Sample terminal, CAD video, files, USB, and idle control traffic independently; preserve payload/wire boundaries.
3. Run a small degraded-link experiment outside active programming and record RTT/loss/reconnect behavior.
4. Approve a tested primary vertical slice or explicitly accept a measured fallback and revise scope/effort/risk records.

Deliverables:

- Measured primary-topology baseline and counter provenance
- P0 go/adjust decision with selected fallbacks

Exit evidence:

- Each critical input/probe/backend risk has evidence or an accepted qualified fallback.
- Console can progress independently even while a Desktop hardware branch remains unresolved.

Record evidence under `qualification/results/P0-S6/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-N01, Q-X03. Official source IDs: R08, R16, G21; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Every stage must satisfy its exit evidence or record an accepted, qualified fallback before this phase is declared complete. Critical desktop/probe failure blocks the affected Desktop release unless an alternative topology is actually qualified. Do not hold an otherwise qualified Console hostage to unrelated hardware.
