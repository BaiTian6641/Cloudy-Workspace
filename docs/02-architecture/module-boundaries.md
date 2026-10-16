# Module boundaries and implementation graph

P1-S1 established five real Gradle source modules: `:console` (`apps/console`), `:desktop` (`apps/desktop`), `:domain` (`shared/android/domain`), `:platform` (`shared/android/platform`) and `:design-system` (`shared/design-system/android`). Both apps use `:platform`, which uses the pure local-window domain and native Compose design source. Strict build and bounded device tests now pass; P1-S1 local construction acceptance is complete.

The implemented vertical slice is local window naming, fresh document creation, task labels, owner-bound Bundle restoration and separately saved editor drafts. Packages are `io.github.baitian6641.cloudy.console` and `.desktop`, each0.1.0/code1, min35/target37, with native icons and cloud/D2D extraction exclusions. That historical local-window slice contained no remote engines, credential storage or network actions. P1-S3 now adds a sixth real module, `:identity` (`shared/android/identity`), and native management through `:platform`. Candidate0.1.2/code3 is built with14 identity JVM and18 Android storage tests passing; actual product prompt/AES protection is pending. Shared source still implies no other-app process/state dependency. The remaining responsibilities below stay planned unless specifically covered by the [vault review](../06-validation/2026-10-16-product-vault-review.md).

| Module | Owns | Public interface | Verification |
| --- | --- | --- | --- |
| `apps/console` | Console windows, navigation, layout, session coordinator | UI commands and persisted view state | App independence, input, window restoration |
| `apps/desktop` | Workspace views, file/device/activity orchestration | UI commands, backend selection | Missing backend, partial capability, degraded states |
| `shared/android:domain` | Host/environment/resource IDs and error categories | Immutable models and schema conversion | Unknown enum/version handling |
| `shared/android:identity` | App-owned keys, trust records, pairing | Credential handles and signing/wrapping operations | Revocation, backup/restore, enrollment cancel |
| `shared/android:ssh` | Engine adapters, PTY, SFTP, jumps, forwards | Bounded asynchronous streams and typed errors | Trust, algorithm policy, disconnect semantics |
| `shared/android:x11` | Console X11 window broker, native task/surface binding and input adaptation | Generation-scoped X window records and typed engine events | Separate windows, dialogs, trust context, GUI lifecycle |
| `shared/android:remote-desktop` | Native render/input/profile boundary for stream/RDP/VNC | Typed capabilities and lifecycle, native surface handles | Backend-specific input/session/security and bounded state |
| `shared/android:transfer` | Journals, version checks, cache/staging | Transfer jobs and commit outcomes | Resume mismatch, conflict, cancellation cleanup |
| `shared/android:platform` | Window/input/URI/lifecycle wrappers | Capability checks and OS callbacks | API guards, IME, process death |
| `shared/android:ipc` | Explicit app handoff and import | Versioned non-secret envelopes | Sender identity, unknown recipient/version |
| `shared/android:diagnostics` | Redacted bounded logs/counters | Export with schema/version/context | No secret/transcript leakage |
| `shared/design-system` | Cloudy tokens and future native components | Semantic roles and component contracts | Contrast, focus, text scaling, density |
| `shared/terminal-renderer` | Pinned engine assets and viewport | PTY bytes, resize, selection, narrow events | Sequence/IME/accessibility/performance fixtures |
| `host/core` | Pairing/capability/event/job service | Versioned transport-neutral contracts | Expiry, replay gaps, scope, cancellation |
| `host/{windows,macos,linux}` | User-session OS capabilities | Capability flags and typed adapter results | Physical OS/session matrix |
| `adapters/{desktop,usb,agents}` | Upstream version-specific integration | Discover/start/status/stop/reconcile as supported | Unsupported API and backend-version cases |
| `adapters/x11` | Qualified local X server packaging/connection/rendering hooks | Native local channels and X window metadata | License/ABI, authentication, no root/private API dependency |
| `adapters/desktop/{rdp,vnc,stream}` | Independent maintained protocol engines | Native engine callbacks behind shared Desktop contracts | Negotiation, security, display/input recovery, engine updates |

## Dependency rules

App UI depends on shared models and services. Shared modules never depend on either app. Protocol schemas describe serialization without importing platform UI types. Host adapters depend on host-core interfaces; host core does not import OS-specific APIs directly. Design tokens have no product-state dependency.

The [connection-profile increment](../../qualification/results/P1-S3/2026-10-16-connection-profiles.md) adds `:identity → :domain` so vault bindings and local endpoint profiles use the same unchanged `EndpointHost` normalization. Domain remains pure Kotlin/JVM with no reverse identity/platform dependency. `:platform` owns each app's actual profile SQLite store; the domain model owns no persistence, credential or trust authority. The native profile editor is implemented with scoped qualification; registered-resource integration remains pending.

SSH/SFTP engine adapters must be replaceable without redesigning navigation. Stream launch and integrated stream adapters expose different capability levels rather than pretending external-client lifecycle is controllable. USB ownership state is shared domain logic with backend-confirmed facts, not a boolean derived from a button.

Console and Desktop reuse file/transfer source while owning credentials and journals separately. Console alone supplies its session browser and drop export staging; Desktop's later remote DocumentsProvider is not a dependency. X11 broker surfaces do not use the terminal renderer. Native RDP/VNC are separate modules from external stream launch and do not require the custom host agent.

## Versioning and release units

P2-S1 implements reusable SSH transport/authentication under CAP-SSH. P2-S8 implements transfer, journal, staging and publication primitives; both apps integrate those primitives in their own stages. CAP-TERMINAL controls Console's renderer/input route, not Desktop's SSH/SFTP readiness. Shared modules cannot import Console UI or require its installed process. Q-F02 checks this boundary with real SSH/SFTP behavior.

Apps, host agent, protocol, and external adapters have separate versions. Build-time shared code can move together without forcing runtime lockstep. Negotiate compatible protocol ranges and named capabilities. A newer app receiving unknown optional capabilities ignores them; unknown required major versions fail with an actionable upgrade path.

Release manifests record app and agent versions, protocol range, backend versions, schema hashes, and supported qualification runs. P1 selects dependency locks and a reproducible build environment; P6 qualifies upgrade/rollback and stored-data migrations.

## Accepted bounded build/device checkpoint

The [native foundation report](../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 with no dependency-regeneration flags, nine passing JVM tests (domain5/design4), and zero Lint findings in both apps. On HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, five real platform Bundle/Parcel tests and two Activity tests per app pass (nine connected tests total). Each product is independently installed/launched with the other absent. The first Console instrumentation failure exposed Espresso3.5 reflection incompatibility; the test-only3.7 pin corrects it, without changing the frozen product APK hashes.

This qualifies only the tested local-window metadata/editor/restoration boundary. P1-S1 is complete with actual Desktop New/Close and both package replacement/uninstall receipts; later product requirements remain open. Physical keyboard/native IME, process-death restoration, broader geometry/accessibility and remote functionality are not established by Activity recreation or Compose test stimulus.


## Current credential-free SSH boundary

P1-S3 adds a seventh real production module, pure JVM `:ssh` at `shared/android/ssh`. The dependency direction is `:console/:desktop → :platform → :ssh → :domain`, alongside the existing identity/design modules. The engine owns bounded numeric-address transport observation and actual thread/socket retirement, with no Android context, DNS, credential store, trust persistence or UI dependency. Platform owns resolver/permission/lifecycle presentation and its app-private trust CAS. No process or credential data is shared between products.

The [observation contract](ssh-key-observation.md) and [0.1.8 result](../../qualification/results/P1-S3/2026-10-16-ssh-key-observation.md) cover only that real finite route. PTY, authentication, jumps, SFTP, forwarding and full P2-S1 acceptance remain required. Existing diagnostic implementations do not become product features merely by sharing an engine pin.
