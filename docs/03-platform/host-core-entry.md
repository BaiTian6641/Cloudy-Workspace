# Host core construction entry — HOST-EXP01

**2026-10-16; bounded construction experiment for P4-S1.** The user requested parallel Windows/Linux/macOS host software while Googlebook client work continues. The initial experiment tested whether a small, separately versioned Go core builds portably and exposes truthful local diagnostics without choosing remote transport prematurely. Parent owns acceptance; direct Codex GPT-6.1 Sol owns source; OMP Kimi/Flash supply reviewed proposals. The initial scope cap excluded persistence; the separately reviewed [HOST-IDENTITY increment](host-installation-identity.md) now defines that operation. Listeners, jobs, services and OS permissions retain separate profiles and acceptance.

## Implemented local profile

`host/core` contains a separately versioned Go module and `cloudy-host` executable. The initial standard-library-only diagnostic was **0.1.0**, followed by installation identity **0.1.1**. Version **0.1.2** added the [Windows permanent-file identity profile](host-windows-identity-profile.md) and the exact `golang.org/x/sys v0.49.0` dependency, initially used by Windows identity and subsequently by the internal Linux jobs profile. Current **0.1.3** adds the separately invoked [Linux context observation](host-context-observation.md). `inspect` reads the actual running binary's OS/architecture/toolchain and current UTC time. It emits `localDiagnosticVersion: 1`, `agentVersion`, `observedAt`, `runtime`, `features` and `backends`. `version` prints the executable version. Invalid or extra arguments return exit2; output failure returns exit1 with fixed safe text. A successful exit and complete JSON are both required; stdout is not an atomic publication mechanism.

The CLI's unimplemented pairing, remote control, remotely admitted jobs, events and session adapters report **unavailable / not-implemented**. External desktop, USB and notification backends report **unknown / not-inspected**. The `inspect` command neither probes those services nor claims they are absent. It does not collect hostname, account, environment, paths, credentials, machine inventory, installation/boot/session identifiers or backend output.

This is a **local diagnostic profile**, not `protocol/schemas/capabilities.schema.json`. That draft requires authoritative installation, boot and session identities and authenticated admission. The explicit local context command observes existing installation/boot IDs but supplies neither session identity nor that authority. Do not fill them with arbitrary UUIDs or map process startup to OS boot. Authenticated capabilities/version negotiation, registered roots, request authorization, idempotency, persistent events and cursor gaps remain P4-S1 work.

## Toolchain and checks

The bounded core selects **Go1.27.2**. Initial0.1.0/0.1.1 had no external modules;0.1.2 admits only the reviewed Windows API wrapper above, with authenticated checksums, actual license/notices and readonly builds. The `go` directive defines a minimum requirement; validation additionally checks the actual executable version and uses `GOTOOLCHAIN=local`, `GOENV=off`, `GOPROXY=off` and `GOTELEMETRY=off`. Only the exact dependency acquisition step enables the public module proxy/checksum database; subsequent verification/build commands keep module network access off. No automatic toolchain substitution or global Go installation was performed. The parent downloaded the official archive into ignored workspace cache and verified its exact size and SHA256 against the [official download metadata](https://go.dev/dl/?mode=json) (R295).

| Artifact | Value |
| --- | --- |
| Linux build tool | `go1.27.2.linux-amd64.tar.gz` |
| Size | 70,590,635 bytes |
| SHA256 | `ecbadb99091a3f46e31f5f934b068b1864eafa7995211b39eaddf76996045fe5` |
| Local build targets | linux/amd64, windows/amd64, darwin/arm64 |
| Actual local executable run | linux/amd64 only |

The [Go release history](https://go.dev/doc/devel/release#go1.27.2) records the selected patch release (R296). [Go command documentation](https://pkg.go.dev/cmd/go) defines the build/test/vet operations (R297). Go's standard library/runtime is reused under its upstream terms; future packaged artifacts need their actual source/notices and dependency closure. No repository-wide or Console license grant is implied.

Local checks: formatting, `go vet`, compiled tests with Linux race instrumentation, three `CGO_ENABLED=0` builds, and actual Linux inspect/version/invalid-argument processes. Cross-compilation proves buildability only. The pinned CI workflow additionally runs on actual hosted Linux/Windows/macOS runners; even a hosted CLI pass will not establish interactive sessions, installers, permissions or physical peripherals. See the [build evidence](../../qualification/results/P4-S1/2026-10-16-host-baseline.md).

## Next disjoint host work

| Packet | Owned surface | Required outcome before integration |
| --- | --- | --- |
| HOST-IDENTITY | [Explicit installation self-ID](host-installation-identity.md); later platform identity adapters remain separate | Init/read uses a permanent claim anchor and refuses silent repair/regeneration; Linux boot observation is implemented separately; user logon/session and process identity remain unimplemented |
| HOST-JOBS | Transport-neutral job ownership after its local execution profile review | Explicit executable/argv/cwd, bounded/redacted I/O, timeout/cancel, actual descendant ownership and waited retirement; no automatic replay |
| HOST-EVENTS | Bounded persistence/cursor store after event profile review | Stable IDs, sequence/stream reset, retention gaps, snapshot reconciliation and crash windows; own-job events first |
| HOST-WINDOWS | `host/windows` | Interactive helper and notification consent/packaging/API evidence; default service/session0 is not a user desktop |
| HOST-LINUX | `host/linux` | Actual session and supported compositor/backend observations; environment variables are hints and a notification protocol is not an observer grant |
| HOST-MACOS | `host/macos` | User-agent lifecycle and own-app/job events; precise permission state; arbitrary notification capture stays unestablished |
| HOST-PAIRING | Parent-reviewed protocol/security contract, then scoped implementation | Exact transport, per-app identity/scopes, revocation, freshness/replay and local IPC peer policy before exposing commands |

These are bounded packets inside the existing P4 plan, not extra completed stages. Core and OS interfaces are reviewed before concurrent writers consume them. Pairing and Googlebook profile integration still require P1-S4/P3-S1. No default daemon/service registration, autostart, system configuration, installed backend control or programming operation belongs to this experiment.

## Official OS reference routes

| Surface / exact entry point | Availability and reviewed limit | Qualification / fallback |
| --- | --- | --- |
| Windows service/user-session split; named-pipe security descriptor and logon SID | Microsoft official Win32 guidance reviewed; no selected Windows helper implementation or minimum build yet (R299–R300) | P4-S1/S2 peer/session tests; unavailable adapter until implemented; no interactive-service workaround |
| `UserNotificationListener`, `GetAccessStatus` | Existing R11 guide; packaging/capability, user access and read result differ | P4-S2 denial/revocation and fidelity; own-job events remain useful |
| macOS LaunchAgent versus LaunchDaemon | Archived official launchd guide reviewed as background, not current installer proof (R301) | P4-S3 exact macOS/service API investigation and logged-in-session tests |
| Linux `systemd.service`, user/graphical-session lifetime | Current web reference located but fetch failed; inspect pinned or installed manual before writing units (R302) | P4-S3 actual session lifecycle; no automatic root service or linger |

Checked 2026-10-16. These references guide future adapters; HOST-EXP01 invokes none of those APIs. No P4 stage, backend or OS support claim is promoted.


## Internal local-job follow-through

[HOST-JOBS01](host-local-jobs.md) now implements a bounded Linux library using the same selected Go/x/sys versions, at its earlier source checkpoint without claiming a remote jobs command. [Source and finite native evidence](../../qualification/results/P4-S1/2026-10-16-host-linux-jobs.md) cover ownership, placement, bounded output and actual retirement on the selected Linux fixture. This is independent parallel construction under the existing user mandate; remote pairing/admission and full P4-S1 prerequisites remain unchanged. Subsequent Windows owned-job construction/native-fixture evidence is separate; macOS managed jobs remain unimplemented.


## Explicit context integration (0.1.3)

[HOST-CONTEXT01](host-context-observation.md) connects existing installation Read and Linux boot Read through `context read --state-dir <existing-app-owned-directory>`. The local version1 record reports actual runtime, IDs and reader durability with timestamp after both reads and `sequential-not-atomic` consistency. It never initializes or repairs state, does not alter default inspect, and adds no capability/session/authentication claim. Other OS targets return fixed unsupported before observation rather than fabricate boot identity.

The [accepted result](../../qualification/results/P4-S1/2026-10-16-host-context.md) binds all six changed diagnostic files, eleven checks,14 top-level/25 aggregate Linux race passes and ten finite owned-fixture CLI commands. All three product targets build; Windows/Darwin diagnostic tests only cross-compile. Parent/independent review accepts this bounded source and retained Linux runtime; prior immutable reader evidence is reused without unchanged suite repetition. No new hosted CI pass, other-OS native command, service/pairing or P4 promotion follows.
