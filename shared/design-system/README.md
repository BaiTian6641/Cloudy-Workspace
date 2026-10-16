# Shared design system

[tokens.json](tokens.json) is the canonical Cloudy palette, typography, spacing, shape, motion, density and terminal color data. Read the [design handbook](../../docs/04-design/README.md) for component roles.

`python3 tools/generate_design_tokens.py` generates specimen CSS; `python3 tools/generate_android_tokens.py` generates the checked-in Android constants. Both offer `--check` drift detection. Real Gradle `:design-system` source lives in [android](android/): token-backed Compose theme/typography/shapes and the local window workspace editor/actions. Text uses system fonts and scalable sp; the scrollable layout unions safe-drawing/IME insets and wraps action buttons. Native editing retains a bounded draft and selection; final naming validation belongs to the domain model.

This is P1-S1 source implementation, with strict Compose build and bounded Activity tests passing; desktop physical input/geometry/font/accessibility acceptance remains separate. It is not a complete P1-S2 component catalog, terminal renderer or video surface. Further native components and engine integration retain their feature gates. Shared UI runs in each owning application; it does not share app state or processes.

## Accepted bounded build/device checkpoint

The [native foundation report](../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 with no dependency-regeneration flags, nine passing JVM tests (domain5/design4), and zero Lint findings in both apps. On HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, five real platform Bundle/Parcel tests and two Activity tests per app pass (nine connected tests total). Each product is independently installed/launched with the other absent. The first Console instrumentation failure exposed Espresso3.5 reflection incompatibility; the test-only3.7 pin corrects it, without changing the frozen product APK hashes.

This qualifies only the tested local-window metadata/editor/restoration boundary. P1-S1 is complete with actual Desktop New/Close and both package replacement/uninstall receipts; later product requirements remain open. Physical keyboard/native IME, process-death restoration, broader geometry/accessibility and remote functionality are not established by Activity recreation or Compose test stimulus.
