# Repository map

The root Gradle graph contains seven source modules: `:console`, `:desktop`, `:domain`, `:platform`, `:design-system`, `:identity` and `:ssh`, with paths bound in `settings.gradle.kts`. P1-S1 local construction is complete; later scoped identity/profile/trust and foreground SSH/read-only files/handoff increments have separate evidence. Full remote workflows remain incomplete. `host/core` additionally contains the separately built [HOST-EXP01 Go diagnostic](../03-platform/host-core-entry.md); other host adapters are planned, not implemented by their directories.

Read the [module boundaries](../02-architecture/module-boundaries.md) and [current state](../05-delivery/execution-state.md) before continuing. The full implementation goal continues with Codex GPT6.1-Sol and authorized OMP coworkers; no P1-only stopping rule is inferred from historical plans.


The tree below mixes the implemented Android source modules with reserved future ownership boundaries. Root Gradle configuration exists for the Android modules; host/core has its own explicit go.mod. Other targets are not implied by their directories.

```text
apps/
  console/                 Independent Android app; Console-specific state and UI
  desktop/                 Independent Android app; Desktop-specific state and UI
shared/
  android/                 Profiles, identity, SSH/SFTP, transfer, lifecycle, IPC, diagnostics
    x11/                   Native top-level X11 window broker boundary
    remote-desktop/        Native stream/RDP/VNC surface, input and capability boundary
  design-system/           Canonical tokens and future Compose components
  terminal-renderer/       Isolated terminal assets and narrowly typed native messages
protocol/
  schemas/                 Proposed JSON contracts, separate from transport
  examples/                Synthetic valid examples for review and compatibility tests
host/
  core/                    Pairing, capabilities, scoped requests, resumable events
  windows/                 Logged-in user adapters and narrow helper boundary
  macos/                   Permission-aware session adapters and own-job events
  linux/                   Explicit compositor/session adapters
adapters/
  desktop/                 External performance-stream launch and protocol engine adapters
    rdp/                   Native RDP client engine boundary
    vnc/                   Native RFB client engine boundary
    stream/                High-performance external/native stream boundary
  x11/                     Qualified local X server engine adapter
  usb/                     VirtualHere consumer/provider orchestration and reconciliation
  agents/                  Optional structured job/CLI hooks
qualification/
  results/                 Sanitized run evidence; no invented pass results
docs/
  00-project/              Scope, review, decisions, repository map
  01-product/              Console/Desktop requirements and journeys
  02-architecture/         Boundaries, data paths, lifecycle, network model
  03-platform/             Official Googlebook/Android API and host references
  04-design/               Design handbook and inspectable specimen
  05-delivery/phases/      P0–P6, each split into concrete stages
  06-validation/           Acceptance cases, matrices, evidence templates
  07-reference/            Source registry, review cadence, change tracking
  adr/                     Decision history
  intake/                  Supplied plan preserved with provenance
skills/                    Project-local research workflow
tools/                     Documentation/data checks; no host mutations
```

## Ownership and allowed dependencies

| Area | Accountable role | May depend on | Must not depend on |
| --- | --- | --- | --- |
| Console | Android/terminal developer | shared Android, tokens, renderer, X11 adapter, protocol | Desktop process or host agent for baseline SSH |
| Desktop | Android/desktop developer | shared Android, tokens, adapters, protocol | Console process for its desktop/file basics |
| Shared Android | Platform developer | approved OS/library adapters | App-specific navigation or presentation state |
| Renderer | Terminal developer | pinned terminal engine and local asset loader | Arbitrary network pages, credentials, SSH implementation |
| Host core | Host developer | protocol, OS interfaces | Android UI, media-frame transport |
| OS adapters | Per-OS developer | narrow host-core interface | Assumed universal notification/capture access |
| Design system | Design/Android developer | canonical tokens, platform semantics | SSH/session policy |
| Qualification | QA/device owner | versioned packages and explicit inventory | Unrecorded hardware assumptions |

These are role assignments, not claims that a staffed team exists. One developer may fill several roles. Dependency direction is enforced during P1 build setup; see [module boundaries](../02-architecture/module-boundaries.md).
## Accepted bounded build/device checkpoint

The [native foundation report](../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 with no dependency-regeneration flags, nine passing JVM tests (domain5/design4), and zero Lint findings in both apps. On HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, five real platform Bundle/Parcel tests and two Activity tests per app pass (nine connected tests total). Each product is independently installed/launched with the other absent. The first Console instrumentation failure exposed Espresso3.5 reflection incompatibility; the test-only3.7 pin corrects it, without changing the frozen product APK hashes.

This qualifies only the tested local-window metadata/editor/restoration boundary. P1-S1 is complete with actual Desktop New/Close and both package replacement/uninstall receipts; later product requirements remain open. Physical keyboard/native IME, process-death restoration, broader geometry/accessibility and remote functionality are not established by Activity recreation or Compose test stimulus.
