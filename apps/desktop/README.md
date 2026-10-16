# Cloudy Desktop application

**State:** real P1-S1 source implementation; strict build and bounded device tests now pass; P1-S1 local construction acceptance is complete. Gradle target `:desktop`, package `io.github.baitian6641.cloudy.desktop`, version0.1.1/code2, minSDK35/targetSDK37. This is an Android-packaged Googlebook desktop/laptop app under [Rule0](../../docs/00-project/platform-rule-zero.md).

The current native Compose window edits and commits a validated local name, updates its system task label, opens a fresh document window and closes its own window. Logical owner/UUID/name restore through validated framework Bundle state; the native editor separately saves its unfinished draft/selection. Incoming Intent extras/data are not imported as local state. The bounded Bundle/Activity restoration and invalid-edit cases now pass on the recorded device; Desktop New/Close and both package replacement/uninstall cases also pass in their recorded scopes.

`:desktop` uses `:platform`, which shares `:domain` and `:design-system` source. Each APK runs its own Activity, logical state and app storage; shared code creates no shared process, credential store or other-app dependency. Native launcher icons and explicit cloud/D2D extraction exclusions are included. The application requests no network/storage permissions and contains no credentials or remote engine actions.

Full native RDP/VNC, performance streaming, files and enrolled USB plug-and-play remains planned feature work, with its existing adoption and release gates. Ordinary SSH and Desktop basics retain their independent-app requirement; a local window does not satisfy those features. The user's latest direction continues the full implementation goal with direct Codex GPT6.1-Sol and authorized OMP coworkers; P1 is no longer an automatic stopping point.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [current state](../../docs/05-delivery/execution-state.md) and [validation strategy](../../docs/06-validation/strategy.md). Parent integration owns actual build/install/lifecycle evidence and capability decisions.

## Accepted bounded build/device checkpoint

The [native foundation report](../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 with no dependency-regeneration flags, nine passing JVM tests (domain5/design4), and zero Lint findings in both apps. On HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, five real platform Bundle/Parcel tests and two Activity tests per app pass (nine connected tests total). Each product is independently installed/launched with the other absent. The first Console instrumentation failure exposed Espresso3.5 reflection incompatibility; the test-only3.7 pin corrects it, without changing the frozen product APK hashes.

This qualifies only the tested local-window metadata/editor/restoration boundary. P1-S1 is complete with actual Desktop New/Close and both package replacement/uninstall receipts; later product requirements remain open. Physical keyboard/native IME, process-death restoration, broader geometry/accessibility and remote functionality are not established by Activity recreation or Compose test stimulus.

The [0.1.1 context increment](../../qualification/results/P1-S2/2026-10-16-committed-context.md) keeps the committed local window name visible as a semantic heading while an unapplied draft is edited. Its two updated Activity cases pass on the same Googlebook. Broader P1-S2 interaction remains in progress.
