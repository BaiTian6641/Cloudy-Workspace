# P1-S1 — Independent native application foundation

**2026-10-16, America/Chicago. Verdict: pass for P1-S1 construction scope.** Console and Desktop are real, independently installed local-window applications. Each launches with the other absent. Window editing/restoration, separate document creation and scoped closure have actual Android evidence. This completes P1-S1; full design/input, protected credentials/IPC, remote features and distribution retain their later gates.

## Identity and reproducible inputs

Source base is `0b794107bcf0c1868be9b0ee219bbce13cac1b38` plus the reviewed increment bound by the [source manifest](2026-10-16-foundation-source.json). The [evidence manifest](2026-10-16-native-foundation.json) binds actual private build, package and device receipts. The [stage review](../../../docs/06-validation/2026-10-16-native-foundation-review.md) records independent review and corrections; [product toolchain](../../../docs/03-platform/googlebook/product-toolchain.md) documents exact versions, ABI and distribution assumptions.

| Artifact | Identity |
| --- | --- |
| Console | `io.github.baitian6641.cloudy.console`, 0.1.0/code1; SHA256 `dc0d8bef6cf54addd921023a0294ec7a141235879b7d11bbb6ad0ffc5dce1785`; 29,023,736 bytes |
| Desktop | `io.github.baitian6641.cloudy.desktop`, 0.1.0/code1; SHA256 `406a7ac8de2fea9e65217bd5f9c37d4a12038abecdd19505c2a5038ef464b56e`; 29,023,746 bytes |
| Local development certificate | Both APK signatures verify; certificate SHA256 `ce641362dd8500eed3c179a029899e1a24dfd5249e29c816ca9e637978de2807`. This is debug signing, not a release identity decision. |
| Client | HP Googlebook14; Android17/API37; build `CL3B.260622.276.R2`, incremental16560795; arm64-v8a |
| Selected build | Root Gradle strict build12, JDK21, Gradle9.6.0/AGP9.4.0, SDK37.0 revision2, BuildTools36.0.0, app min35/target37; exact dependency locks/checksums |
| Stimulus and topology | USB ADB and actual Android instrumentation; local native Compose UI, no host/backend/network session or WebView; no physical typing, IME or input-latency measurement |

Runtime packages contain the upstream AndroidX graphics-path native library in four ABIs; only arm64-v8a is exercised here. Shared source compiles into each app's own process. The apps have separate package/data namespaces and no cross-app dependency, credentials, remote commands or general UI transport bridge.

## Experiment and observations

The parent first confirmed neither new product package existed. Existing qualification apps and records were preserved. The hypothesis was that the two actual packages could independently run real local window operations and restore bounded metadata without importing launcher data or depending on another app.

| Case | Actual observation | Scope |
| --- | --- | --- |
| Console alone | Install succeeds, Desktop absent, cold Activity launch 387ms/Wait389ms; native name/actions visible in layout and viewed screenshot | Launch timing is Android's single launch observation, not a performance benchmark |
| Desktop alone | Console uninstall succeeds and both products are absent; Desktop install succeeds, cold launch333ms/Wait334ms and rendered native controls; Console then reinstalled | Independent package launch; no custom host/daemon |
| Platform instrumentation | Five Bundle/Parcel cases pass in0.025s | Real Android serialization, malformed field/type, owner/schema/identity/name rejection and saved-key whitelist |
| Console Activity instrumentation | Two corrected cases pass in2.075s | Committed window identity/title/task label and unsaved draft/selection survive ActivityScenario recreation; invalid edit leaves committed state unchanged |
| Desktop Activity instrumentation | Same two cases pass in1.789s | Independent actual Activity implementation, new ephemeral generation after recreation |
| Desktop New Window | Observed native button creates a second freeform task; both original and new app windows are present at distinct bounds | Actual UI action and task separation; logical UUID generation separately covered by source/JVM cases |
| Desktop Close Window | Observed Close on the new window removes that task only; original task/window returns focused | Scoped task-root closure; Console shares the reviewed handler, but no separate Console New/Close gesture is inferred |
| Package replacement | `adb install -r` succeeds for each exact frozen APK; each replacement launches successfully | Same-version/same-signature package replacement, not a newer schema/version migration |
| Uninstall and cleanup | Both products have successful uninstall receipts; Desktop restored after its case, both final product processes stopped, all three temporary test packages removed | Old diagnostic packages/evidence unchanged; boot ID/count remain unchanged |

The visible Googlebook system caption uses the app name. Activity title/accessibility window title and task-description metadata include the local window name; pixel-level caption substitution is not claimed. P1-S2 must retain visible in-app context rather than assume an OEM caption will display the supplied task label. The sampled normal desktop window shows native content below the system caption; narrow/short/scaled windows and full accessibility review remain P1-S2 work.

## Retained failure and correction

The first Console instrumentation run fails before the Activity assertions because transitive Espresso3.5.0 reflects `InputManager.getInstance`, unavailable on this target. Both failed cases remain in private window-01 evidence. Exact upstream issue2507 and fix `fddcda4adc8eaba515fbf2d790cf8dfe5c1b722d` support the test-only Espresso core/idling-resource3.7.0 pin (R262/R263/G185/G186). The corrected window-02 cases pass. Product runtime dependency selection and frozen APK hashes remain unchanged; this repairs the test framework, not a product input defect.

The earlier bootstrap compile/Lint/source-discovery failures are preserved in build-01 through build-08. Build09/11 deliberately generate dependency verification/locks and are not the accepted normal-build artifacts. Build10 and final build12 use strict verification with no regeneration flags and produce the same product APK hashes. Final build12 runs nine JVM tests with no failures/skips and zero findings in both app Lint reports; all three instrumentation APKs compile. No missing test discovery is counted as success.

## Acceptance and continuation

P1-S1's four work items, deliverables and two exits are satisfied in the bounded local construction scope. Q-X01 independent installation/source separation and Q-P03 local window restoration receive this partial evidence; their complete security, lifecycle and desktop-quality obligations remain open in later stages. API48 receives partial runtime evidence. CAP-FOUNDATION remains supported for construction; CAP-PLATFORM and feature capability outcomes are unchanged.

Activity recreation is not process death, durable workspace recovery, migration or automatic reconnect. This result does not qualify native composition, physical shortcuts, clipboard/context menus, every input device, broader display geometry, accessibility, continuous background sessions, credential protection, inter-app IPC, remote engines or publication. APK installation and the AAB publishing distinction are documented; no AAB or release APK was published. P1-S2/S3 may now proceed on their existing prerequisites; P1-S4/S5 retain all engine, build, licensing and security decisions. The user removed the earlier P1 stopping point; the full implementation goal continues with a rolling, reviewed coworker packet.
