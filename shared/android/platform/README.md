# Android window platform

**State:** implemented P1-S1 source, Gradle `:platform`; strict compilation and bounded device tests now pass; full stage acceptance remains parent-owned and open. This shared library depends on `:domain` and `:design-system` and is instantiated independently inside each app process.

`CloudyWindowActivity` hosts the native Compose workspace, validates/restores owner-bound local window metadata through `WindowBundles`, commits name changes and updates Activity/task labels. It requests a fresh document task for New Window, copies no local state into launch extras, ignores externally supplied Intent state, and closes only its own window. Framework saved state and the editor's saveable draft are distinct from durable storage or live-operation ownership.

The shared XML extraction resource excludes all nine supported domains from both cloud backup and device transfer. Both apps also disable backup. This is configured policy, not an actual migration-exclusion test or a product vault. System captions, safe drawing and IME insets remain part of the native window boundary.

No shared daemon, credentials, network, remote operation, permission orchestration or general background-session implementation exists in this library. URI grants, protected storage and operation-specific lifecycle adapters remain planned P1-S3 and feature work. Read the [module map](../../../docs/02-architecture/module-boundaries.md) and [current state](../../../docs/05-delivery/execution-state.md).

## Accepted bounded build/device checkpoint

The [native foundation report](../../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 with no dependency-regeneration flags, nine passing JVM tests (domain5/design4), and zero Lint findings in both apps. On HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, five real platform Bundle/Parcel tests and two Activity tests per app pass (nine connected tests total). Each product is independently installed/launched with the other absent. The first Console instrumentation failure exposed Espresso3.5 reflection incompatibility; the test-only3.7 pin corrects it, without changing the frozen product APK hashes.

This qualifies only the tested local-window metadata/editor/restoration boundary. P1-S1 is complete with actual Desktop New/Close and both package replacement/uninstall receipts; later product requirements remain open. Physical keyboard/native IME, process-death restoration, broader geometry/accessibility and remote functionality are not established by Activity recreation or Compose test stimulus.
