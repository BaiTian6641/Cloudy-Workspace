# Cloudy Workspace

An integrated workspace for a Googlebook: **Cloudy Console** for terminals and command-line work, and **Cloudy Desktop** for remote desktops, files, devices, and visual work.

**Rule 0:** desktop/laptop Googlebook applications built on Android's app and package model. Keyboard, mouse/trackpad, native windows and desktop productivity define the design. Phone/tablet optimization is outside scope; narrow tiled desktop windows remain supported. Read the [platform rule](docs/00-project/platform-rule-zero.md).

**Project state:** implementation goal active; **P0-S1 qualification setup is complete; P0-S2 is in progress** on the USB-connected HP Googlebook. A real native diagnostic APK is implemented and installed; Console, Desktop and the host agent are not implemented yet. Research updated **15 October 2026, America/Chicago**. ADB install/launch and bounded diagnostic observations are recorded in the [device run](qualification/results/P0-S1/2026-10-15-device-probe.md); product/hardware compatibility remains unqualified.

Repository: [BaiTian6641/Cloudy-Workspace](https://github.com/BaiTian6641/Cloudy-Workspace) · SSH remote: `git@github.com:BaiTian6641/Cloudy-Workspace.git`

## Start here

| Read | Purpose |
| --- | --- |
| [Documentation index](docs/README.md) | Navigate the whole project |
| [Review of the supplied plan](docs/00-project/plan-review.md) | Retained decisions, corrections, and proposed changes |
| [Revision 01: integrated workflows](docs/00-project/revision-01-integrated-workflows.md) | X11 windows, SFTP/drop, RDP/VNC, USB plug-and-play and desktop scope |
| [Revision 02: final-review decisions](docs/00-project/revision-02-final-review.md) | Per-save overwrite consent, independent app release gates and early native-stream adoption |
| [Engineering workflow](docs/05-delivery/engineering-workflow.md) | Real implementations, stage reviews, AST/lint, delegation, Git and context rules |
| [Execution state](docs/05-delivery/execution-state.md) and [history](docs/00-project/history.md) | Current work, evidence, reviewed milestones and next steps |
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
