# Cloudy Workspace

An integrated workspace for a Googlebook: **Cloudy Console** for terminals and command-line work, and **Cloudy Desktop** for remote desktops, files, devices, and visual work.

**Rule 0:** desktop/laptop Googlebook applications built on Android's app and package model. Keyboard, mouse/trackpad, native windows and desktop productivity define the design. Phone/tablet optimization is outside scope; narrow tiled desktop windows remain supported. Read the [platform rule](docs/00-project/platform-rule-zero.md).

**Project state:** implementation goal active; **P0-S1 setup and P0-S2 investigation are complete; P0-S3 SSH/terminal qualification, P0-S4 native-engine integration and P0-S5 USB provider investigation are in progress** on the USB-connected HP Googlebook. CAP-PLATFORM remains a [bounded experiment](docs/03-platform/googlebook/p0-s2-platform-decision.md), and dependent product stages remain gated. A real native diagnostic APK is implemented and installed, with [finite SSH/SFTP](qualification/results/P0-S3/2026-10-15-ssh-sftp-probe.md), [exec/PTY ownership](qualification/results/P0-S3/2026-10-15-ssh-pty-probe.md) and [lifecycle/recovery](qualification/results/P0-S3/2026-10-15-ssh-lifecycle.md) device evidence. The [ordinary terminal run](qualification/results/P0-S3/2026-10-15-terminal-probe.md) adds real shell/TUI/resize and recovery observations, a tested **0.7.1 native-focus fix**, and a retained rapid-input duplication failure. The [archive/restore drill](qualification/results/P0-S3/2026-10-15-terminal-batch-rotation.md) preserves all earlier attempts, and [scoped DOM/host tracing](qualification/results/P0-S3/2026-10-15-terminal-input-trace.md) narrows the rapid-input defect without claiming a repair. The [0.7.2 upstream candidate](docs/06-validation/2026-10-15-terminal-upstream-review.md) is installed and [passes the bounded rapid/paced host-byte comparison and native resize](qualification/results/P0-S3/2026-10-15-terminal-upstream-device.md), with two synthetic editing limits retained. The [desktop interaction contract](docs/04-design/desktop-terminal-interaction-contract.md) makes keyboard, pointer, menus and clipboard behavior explicit. Physical keyboard/IME/accessibility and renderer acceptance remain pending. The [FreeRDP native/JNI build](docs/06-validation/2026-10-15-native-rdp-build.md) is integrated into a separate **0.1.6 RDP diagnostic APK**. Its [loading](qualification/results/P0-S4/2026-10-15-rdp-apk-load.md) and [first trust/NLA sessions](qualification/results/P0-S4/2026-10-15-rdp-first-sessions.md) are followed by a [measured resize diagnosis and real changing 2880 × 1800 desktop](qualification/results/P0-S4/2026-10-15-rdp-framebuffer.md), with explicit native join/free cleanup. The [controlled input attempt](qualification/results/P0-S4/2026-10-15-rdp-input-boundary.md) retains seven closed attempts: 5,331 frame updates and 79 accepted JNI input submissions, but zero host-target input events. The subsequent [consented portal/relative run](qualification/results/P0-S4/2026-10-15-rdp-relative-portal.md) delivers controlled keyboard input and implements standard relative capture/release. The user confirms release works, but captured remote movement fails on this KRdp server; full input/3D remains unqualified. The [isolated GNOME follow-up](qualification/results/P0-S4/2026-10-15-rdp-isolated-host.md) displays the controlled target and delivers absolute pointer/keyboard input; the subsequent [physical test](qualification/results/P0-S4/2026-10-15-rdp-physical-cursor.md) reports failed captured movement with working release. The [0.1.5 cursor repair](qualification/results/P0-S4/2026-10-15-rdp-cursor-repair.md) now receives the user confirmation “Host trail and cursor move; release works,” with actual host-painted motion and two recorded capture/release intervals. Thirteen attempts are closed with the first twelve preserved. Input-device coverage, actual3D and measured latency remain pending. Sustained resource use and performance remain pending. The [standard RDP desktop contract](docs/04-design/desktop-rdp-interaction-contract.md) requires relative input for qualified 3D workflows and Linux/Windows/macOS host lanes; the [security contract](docs/02-architecture/rdp-authentication-security.md) defines protected on-device credentials. Console, Desktop and the host agent are not implemented yet. Research updated **15 October 2026, America/Chicago**. ADB install/launch and bounded diagnostic observations are recorded in the [device run](qualification/results/P0-S1/2026-10-15-device-probe.md); product/hardware compatibility remains unqualified.

The [native USB diagnostic review](docs/06-validation/2026-10-15-native-usb-review.md) adds a separate real libusb 1.0.30 access probe with 20 JVM ownership/permission tests and a native build/CI lane. Its [Googlebook preflight](qualification/results/P0-S5/2026-10-15-usb-native-preflight.md) loads the library and enumerates two unselected devices without opening either. Device access, export and workstation import remain separate; full USB forwarding and programming are unqualified.

Repository: [BaiTian6641/Cloudy-Workspace](https://github.com/BaiTian6641/Cloudy-Workspace) · SSH remote: `git@github.com:BaiTian6641/Cloudy-Workspace.git`

## Start here

| Read | Purpose |
| --- | --- |
| [Documentation index](docs/README.md) | Navigate the whole project |
| [Review of the supplied plan](docs/00-project/plan-review.md) | Retained decisions, corrections, and proposed changes |
| [Revision 01: integrated workflows](docs/00-project/revision-01-integrated-workflows.md) | X11 windows, SFTP/drop, RDP/VNC, USB plug-and-play and desktop scope |
| [Revision 02: final-review decisions](docs/00-project/revision-02-final-review.md) | Per-save overwrite consent, independent app release gates and early native-stream adoption |
| [Engineering workflow](docs/05-delivery/engineering-workflow.md) | Real implementations, stage reviews, AST/lint, delegation, Git and context rules |
| [Execution state](docs/05-delivery/execution-state.md) and [history](docs/00-project/history.md) | General progress, current work, evidence, reviewed milestones and next steps |
| [Established-library policy](docs/05-delivery/library-reuse-policy.md) | Reuse upstream engines and validation; focus checks on Cloudy integration and concrete risks |
| [Reviewed development skills](docs/07-reference/development-skills.md) | Official skill pins, provenance and project-specific boundaries |
| [Product scope](docs/00-project/vision-scope.md) | Console/Desktop boundaries and release scope |
| [Repository map](docs/00-project/repository-map.md) | Code ownership, dependencies, and planned modules |
| [Phase and stage roadmap](docs/05-delivery/roadmap.md) | Sequenced work, effort assumptions, dependencies, and gates |
| [Googlebook development reference](docs/03-platform/googlebook/overview.md) | Official documentation, APIs, restrictions, and unresolved questions |
| [Design handbook](docs/04-design/README.md) | Carbon × Material Design 3 synthesis |
| [Interactive design specimen](docs/04-design/preview.html) | Console/Desktop, light/dark, density, and connection states |
| [Validation strategy](docs/06-validation/strategy.md) | How proposed capabilities become verified claims |

## Two independently useful applications

**Console** owns SSH, PTYs, tabs and panes, durable remote terminal sessions, jump hosts, tunnels, separate SSH-forwarded X11 application windows, an adjacent SFTP browser with upload/download/edit and drag/drop, and interaction with arbitrary agent CLIs. It works with an ordinary SSH/SFTP server and without Desktop or the custom host agent; X11 needs the separately qualified local server/broker and remote X11 prerequisites.

**Desktop** owns host workspaces, native RDP and VNC client lanes, high-performance streaming, cross-root files/transfers, enrolled USB plug-and-play, host activity, and visual applications. High-performance streaming starts through a qualified existing client; native adoption/distribution is decided in P0/P1 and native integration proceeds on its own prerequisites, independently of optional host adapters. Native RDP/VNC are explicit companion-release requirements.

Both applications share source modules and a versioned resource handoff contract. Each owns its process, storage, credentials, and permission lifecycle. The proposed native shell uses Kotlin/Compose with Cloudy design tokens; a bundled terminal renderer is evaluated separately. See [ADR 0002](docs/adr/0002-native-shell-and-terminal-surface.md).

## Inspect and validate

The documentation checker uses Python's standard library:

```bash
python3 tools/validate_project.py
python3 -m http.server 8765 --bind 127.0.0.1
```

Open `http://127.0.0.1:8765/docs/04-design/preview.html` to inspect the specimen. Its session data is illustrative and its controls make no network connections. It is a design reference, not a functioning terminal or remote desktop client.

Optional schema validation is documented in [tools](tools/README.md). CI checks repository AST/lint/planning data and the real P0 diagnostic APK with Kotlin compilation, Android Lint and JVM tests. Actual Googlebook evidence is recorded separately; hosted CI has no physical target.

## Planning conventions

- **Documented:** supported by a named official reference; still subject to runtime/version requirements.
- **Proposed:** Cloudy's intended behavior or architecture; not implemented.
- **Qualified:** passed a recorded experiment on an identified device/build/backend.
- **Roadmap / unknown:** excluded from release claims until evidence changes.

The original supplied plan is preserved byte for byte in [docs/intake](docs/intake/README.md). Its instructions are source material. The user's current request controls the product split, repository setup, detailed planning, official-reference tracking, and revised visual language.

The revised plan contains **7 phases / 40 stages**, with **129–223 person-days** of active effort before reserve. These are budgeting hypotheses, not device results or elapsed dates. Begin at [P0 qualification](docs/05-delivery/phases/p0-qualification.md), using [capability outcomes](docs/05-delivery/feasibility-gates.md) to control implementation readiness. Console and Desktop have [independent release gates](docs/05-delivery/release-gates.md) and a separate suite milestone. Each stage names deliverables and exit evidence.

The [matched rendering sample](qualification/results/P0-S4/2026-10-15-rdp-rendering.md) carries the physical 0.1.5 result forward and tests 0.1.6 diagnostic control pacing: all 48 JVM tests and Android Lint pass, all 15 attempts are closed, and the four-gesture framework p95 remains 11 ms. No useful timing improvement or end-to-end latency result is claimed. The [27-case VNC regressions](qualification/results/P0-S4/2026-10-15-vnc-regressions.md) pass under full sanitizers for the selected portable host build; Android/TLS/server work remains. The [native-stream entry](docs/03-platform/googlebook/native-stream-adoption-entry.md) and [actual native build](qualification/results/P0-S4/2026-10-15-native-stream-build.md) establish the pinned Moonlight route, with APK/pairing/playback still pending. The user selected [open-source Desktop distribution](docs/adr/0010-desktop-open-source-distribution.md), with compatible terms and required source/notices/build materials.
