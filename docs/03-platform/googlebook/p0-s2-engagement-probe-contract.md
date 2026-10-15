# P0-S2 engagement probe contract

**Owner:** Android/platform developer. **Stage:** P0-S2 / API09. **Checked:** 2026-10-15, America/Chicago. **Status:** researched proposal for isolated diagnostic adoption; dependency resolution, compilation and device acceptance remain pending. This note selects no product dependency and promotes no capability gate.

Read the [desktop application contract](desktop-application-contract.md), [P0 stage](../../05-delivery/phases/p0-qualification.md), [API tracker](api-tracker.json) and [window probe contract](p0-s2-window-probe-contract.md). The intended observation is an actual platform-library engagement callback. It does not qualify physical keyboard/trackpad behavior, perform remote operations, or change Cloudy's desktop scope or density.

## Published API and reproducible provenance

The [official WindowManager release notes](https://developer.android.com/jetpack/androidx/releases/window) list stable **1.5.1** and alpha **1.6.0-alpha05**; the latter was released **2026-06-17**. The alpha contains `WindowInfoTracker.windowEngagementInfo()` and `WindowEngagementInfo.EngagementMode.PRECISE_POINTER`; inspected stable 1.5.1 sources lack the engagement methods. This is a published alpha artifact, beyond an announcement, but not a stable API adoption. Gradle module metadata's `release` publication status does not change alpha maturity.

G03's [desktop-windowing guide](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing), updated 2026-08-04, demonstrates the Flow API without establishing an artifact pin. G01's [Googlebook overview](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview), updated 2026-10-03, supplies desktop context. Existing G42/G49 cover the underlying input-device APIs. The release and artifact URLs below are registered as G73–G77, with API09 marked published-alpha and unqualified.

Candidate coordinate: **`androidx.window:window:1.6.0-alpha05`**, published by AndroidX through Google's Maven repository. Its POM and source headers identify Apache License 2.0. The following bytes were independently fetched and inspected in memory; no implementation was vendored, dependency installed, or build executed during this research. Re-fetch these exact URLs and compare SHA-256 before accepting a later dependency change.

| Artifact | SHA-256 |
| --- | --- |
| [Source JAR](https://dl.google.com/dl/android/maven2/androidx/window/window/1.6.0-alpha05/window-1.6.0-alpha05-sources.jar) | `c9a13a30939e93f6c99e69fba22c37a5718f1a42e5c3d23fb21694337eb1dfc9` |
| [AAR](https://dl.google.com/dl/android/maven2/androidx/window/window/1.6.0-alpha05/window-1.6.0-alpha05.aar) | `13cb300f56c1f047c3114df8eb5ce7f65c41576af265e895f5e495254107fb9d` |
| [POM](https://dl.google.com/dl/android/maven2/androidx/window/window/1.6.0-alpha05/window-1.6.0-alpha05.pom) | `7a7e7eff28bc02137c05ba4eba856b5ec0cb5cb5cc948075a40718d208d127f1` |
| [Gradle module metadata](https://dl.google.com/dl/android/maven2/androidx/window/window/1.6.0-alpha05/window-1.6.0-alpha05.module) | `2506d3b2880ad9f12704e59896ff2e55cebfcd83906defe1246948454accdebe` |

Hashes bind these fetched bytes, not publisher-signature authentication or runtime qualification. Inspect the source files `WindowInfoTracker.kt`, `WindowInfoTrackerImpl.kt`, `WindowEngagementInfo.kt`, `adapter/EngagementModeBackendApi0.kt`, `adapter/InputDeviceTracker.kt`, `util/InputHelper.kt` and `util/EngagementModeHelper.kt` under `androidx/window/layout/` to reproduce the findings below.

## Availability and meaning

| Boundary | Verified artifact contract and proposed handling |
| --- | --- |
| Library installation floor | AAR manifest minSdk **23**. AAR metadata declares minCompileSdk **34**, minCompileSdkExtension **0** and minAGP **8.1.1**. These are this artifact's floors, not a selected product minimum or proof that all transitive dependencies fit the current build. |
| Meaningful precise-pointer calculation | In this version, API **30+** computes an input/display hint. Below API 30, the compatibility backend returns `TOUCH`; that fallback is not a physical touch observation. The existing minSdk-35 diagnostic is above this implementation boundary. |
| Public entry points | `WindowInfoTracker.getOrCreate(context)`; `windowEngagementInfo(@UiContext Context)`; `registerWindowEngagementInfoListener(@UiContext Context, Executor, Consumer<WindowEngagementInfo>)`; matching `unregisterWindowEngagementInfoListener(Consumer<WindowEngagementInfo>)`; `hasEngagementMode(PRECISE_POINTER)`. Use the live Activity as the UI context. |
| Extension/annotation guard | These engagement methods have no `RequiresWindowSdkExtension` requirement or experimental opt-in annotation in the inspected source. Requirements on unrelated posture/layout APIs do not apply here. Alpha compatibility still requires review. |
| Dependencies | POM includes same-version `window-core`, AndroidX core 1.8.0, collection 1.4.2, annotation 1.8.1, JSpecify 1.0.0, coroutines-android 1.9.0 and Kotlin stdlib 2.1.20. Resolve the actual graph, conflicts and verification metadata before accepting it; a direct POM list is not a resolved dependency lock. |
| Fallback | If collection is unavailable, fails or has not produced a value, expose that state honestly. Preserve explicit user density preference and usable bounds-based layout. Do not synthesize a successful engagement result. |

The alpha's compatibility implementation combines an enabled nonvirtual alphabetic keyboard, an enabled mouse-source device excluding stylus sources, and a display diagonal of at least 11 inches calculated from maximum window metrics and reported physical DPI. These are **version-specific library internals**, not a heuristic to copy into Cloudy. `PRECISE_POINTER` and `TOUCH` describe the library's layout hint; neither proves actual physical input quality. Record all returned modes, but do not infer audio playback, audio permission or visual-rendering qualification from `AUDIO_ON`/`VISUALS_ON` values.

No artificial density adjustment belongs in this experiment. A `TOUCH` result must not hide desktop functions, adopt phone layouts, change font scaling, or override the user's density choice. Cloudy's window arrangement still follows actual usable bounds.

## Smallest real diagnostic increment

Propose the pinned alpha only within the qualification probe. The direct callback API permits the existing platform-Views Activity to remain in place; it does not require adding Compose, ComponentActivity or a Lifecycle dependency. Preserve the baseline toolchain unless the resolved dependency/compiler checks establish a concrete incompatibility.

Register while the Activity is started and unregister when it stops. Use a managed background executor for input scanning; the public source recommends background registration and delivery because initial enumeration may perform slow IPC. Serialize registration/removal, marshal UI changes to the UI thread, and reject late callbacks from a stopped subscription or superseded Activity generation. Preserve the recorder's logical-window/task/generation attribution and bounded storage. Do not retain a destroyed Activity through the tracker subscription.

Report the artifact version, subscription state, observation time, actual mode set and errors. The initial input scan is asynchronous and may produce an initial `TOUCH` value before a discovered precise-pointer mode; preserve the sequence and bounded settling evidence instead of treating the first callback as final. Do not add service declarations, permissions, reflection or private APIs for engagement.

| Case | Required evidence |
| --- | --- |
| E01 — Artifact/compiler | Exact resolved dependencies and verification metadata, successful compilation of the public methods, Android Lint and meaningful listener lifecycle tests. Test doubles remain test-only. |
| E02 — Actual observation | On the identified HP build, collect real callback values or report the exact failure/no-observation outcome. Bind results to source revision, APK hash, task, logical window and Activity generation. |
| E03 — Window ownership | Open two probe windows. Changes, stop/start and callbacks remain attributed to their own subscription/generation; no observation from an obsolete owner overwrites a replacement. |
| E04 — Lifecycle/quietness | Background/return and recreate an Activity, demonstrate fresh subscription and old-listener cleanup, then verify revisions settle without continuing report-driven churn. Model tests cover registration/removal races; device evidence covers actual framework/library delivery. |
| E05 — Honest fallback | Pending/error observations remain distinguishable from reported `TOUCH`. Desktop controls, user density and font scaling remain unchanged by this diagnostic. |

Physical keyboard/mouse/trackpad transitions, external-display moves and hotplug require separate recorded actions; ADB-injected clicks cannot satisfy them. A successful single-device callback is a partial API observation, not full API09 qualification or CAP-PLATFORM support.

## Next experiment

Review the proposed dependency addition and its resolved graph, implement the bounded callback collector, run compiler/lint/tests, then record E02–E05 on the real device. Reconcile API09 with the eventual compiler/device evidence; its registered candidate is not a selected product dependency. Preserve an explicit pending outcome if the alpha fails build or runtime acceptance. Larger-font layout checks and the operation-specific service/timeout work remain separate P0-S2 deliverables; this experiment cannot close them.
