# Cloudy Workspace

An integrated workspace for a Googlebook: **Cloudy Console** for terminals and command-line work, and **Cloudy Desktop** for remote desktops, files, devices, and visual work.

**Project state:** researched development plan and repository foundation. No Android application or host agent is implemented yet. Research snapshot: **14 October 2026, America/Chicago**. Hardware compatibility remains unqualified.

Repository: [BaiTian6641/Cloudy-Workspace](https://github.com/BaiTian6641/Cloudy-Workspace) · SSH remote: `git@github.com:BaiTian6641/Cloudy-Workspace.git`

## Start here

| Read | Purpose |
| --- | --- |
| [Documentation index](docs/README.md) | Navigate the whole project |
| [Review of the supplied plan](docs/00-project/plan-review.md) | Retained decisions, corrections, and proposed changes |
| [Product scope](docs/00-project/vision-scope.md) | Console/Desktop boundaries and release scope |
| [Repository map](docs/00-project/repository-map.md) | Code ownership, dependencies, and planned modules |
| [Phase and stage roadmap](docs/05-delivery/roadmap.md) | Sequenced work, effort assumptions, dependencies, and gates |
| [Googlebook development reference](docs/03-platform/googlebook/overview.md) | Official documentation, APIs, restrictions, and unresolved questions |
| [Design handbook](docs/04-design/README.md) | Carbon × Material Design 3 synthesis |
| [Interactive design specimen](docs/04-design/preview.html) | Console/Desktop, light/dark, density, and connection states |
| [Validation strategy](docs/06-validation/strategy.md) | How proposed capabilities become verified claims |

## Two independently useful applications

**Console** owns SSH, PTYs, tabs and panes, durable remote sessions, jump hosts, tunnels, small SFTP attachment transfers, and interaction with arbitrary agent CLIs. It works with an ordinary SSH server and without Desktop or the custom host agent.

**Desktop** owns host workspaces, remote desktop orchestration, the full file browser, transfer jobs, USB ownership, host activity, and visual applications. The first release hands streaming to an existing client; integrated streaming is a separately gated stage.

Both applications share source modules and a versioned resource handoff contract. Each owns its process, storage, credentials, and permission lifecycle. The proposed native shell uses Kotlin/Compose with Cloudy design tokens; a bundled terminal renderer is evaluated separately. See [ADR 0002](docs/adr/0002-native-shell-and-terminal-surface.md).

## Inspect and validate

The documentation checker uses Python's standard library:

```bash
python3 tools/validate_project.py
python3 -m http.server 8765 --bind 127.0.0.1
```

Open `http://127.0.0.1:8765/docs/04-design/preview.html` to inspect the specimen. Its session data is illustrative and its controls make no network connections. It is a design reference, not a functioning terminal or remote desktop client.

Optional schema validation is documented in [tools](tools/README.md). The initial CI checks documentation and planning data; Android builds and device tests enter during P1.

## Planning conventions

- **Documented:** supported by a named official reference; still subject to runtime/version requirements.
- **Proposed:** Cloudy's intended behavior or architecture; not implemented.
- **Qualified:** passed a recorded experiment on an identified device/build/backend.
- **Roadmap / unknown:** excluded from release claims until evidence changes.

The original supplied plan is preserved byte for byte in [docs/intake](docs/intake/README.md). Its instructions are source material. The user's current request controls the product split, repository setup, detailed planning, official-reference tracking, and revised visual language.

Begin implementation at [P0 qualification](docs/05-delivery/phases/p0-qualification.md). Each stage names its deliverables and the evidence required to exit.
