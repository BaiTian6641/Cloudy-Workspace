# Decisions, assumptions, and risks

Statuses: **adopted for planning**, **proposed pending spike**, **unknown**, **qualified**. None of the device-dependent items is qualified yet.

| ID | Decision / risk | Status | Owner role | Resolution stage | Consequence and fallback |
| --- | --- | --- | --- | --- | --- |
| D01 | Two app targets with independent lifecycle | Adopted for planning | Product/platform | P1-S1 | Shared libraries; no shared immortal daemon |
| D02 | Compose native shell; xterm.js isolated surface | Proposed pending spike | Android/terminal | P0-S3 | Swap renderer or revisit shell if IME/accessibility/latency gates fail |
| D03 | External streaming before embedding | Adopted for planning | Desktop | P0-S4 | Manual launch if no documented handoff; expose the transition honestly |
| D04 | Tailnet plus app-level pairing for agent APIs | Adopted for planning | Host/security | P1-S3 | SSH alone remains available |
| D05 | Project license and upstream embedding model | Unknown | Maintainer | P1-S4 / P5-S1 | Keep third-party implementations external until decided |
| R01 | Physical trackpad relative capture/CAD behavior | Unknown, high impact | Device owner | P0-S4 | Absolute mode or qualified external mouse; do not claim trackpad CAD readiness |
| R02 | Primary USB probe reset/reenumeration over Android provider | Unknown, critical | Device/embedded | P0-S5 | Lab-side Linux provider or vendor hardware server |
| R03 | SSH engine compatibility and modern algorithms on ARM64 | Unknown, high | Terminal | P0-S3 | Compare maintained engines; do not implement cryptography |
| R04 | System IME + WebView terminal composition | Unknown, high | Terminal/QA | P0-S3 | Renderer alternative or corrected native input boundary |
| R05 | Background service type and timeout fitness | Unknown, high | Android | P0-S2 / P6-S1 | Persist metadata, host-side durable sessions, explicit reconnect |
| R06 | Linux VM public attach/control API | Unknown; roadmap dependencies | Platform | P0-S2 | No local-VM feature commitment |
| R07 | Wayland capture/input/lock support | Unknown per compositor | Linux | P0-S4 | Qualified matrix and explicit unsupported states |
| R08 | macOS capture permission and notification scope | Unknown per backend | macOS | P0-S4 / P4-S3 | Own-job events and supported user-session integration |
| R09 | Conflict detection and provider write semantics | Proposed | Files | P3-S3 / P5-S3 | Staged writes, source-version checks, conflict copies |
| R10 | Backend API absence/version drift | Unknown | Integration | P0-S4/S5 | Isolate adapters; pin measured versions |
| R11 | Metered data use/relay path | Unmeasured | Network/QA | P0-S6 | Terminal-first, explicit video budget and measurement boundary |
| R12 | Cross-app version skew and signing identity | Proposed | Platform | P1-S3 / P6-S2 | Version negotiation, explicit import, actionable mismatch |
| R13 | Distribution restrictions/Play target requirements | Must refresh before release | Maintainer | P6-S3 | Record selected channel and actual requirements at release time |
| R14 | Eye fatigue/density at actual display scaling | Unmeasured | Design/QA | P1-S2 / P6-S4 | User density/font controls and real-hardware review |

Update this table when evidence changes; link the run, installed version, and affected phase. A fallback is acceptable only when its user-visible limitation is documented and the fallback itself has been tested.
