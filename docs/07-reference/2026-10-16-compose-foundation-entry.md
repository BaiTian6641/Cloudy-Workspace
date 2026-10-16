# Compose foundation entry

Checked: 2026-10-16. Status: official documentation and published artifact review; project build and device qualification pending. This entry proposes the minimal Compose dependency selection for P1-S1. It does not change the accepted diagnostic toolchain or its evidence.

The recommended stable stack is Compose BOM `2026.09.00`, Activity Compose `1.13.0`, and Compose compiler plugin `2.2.10`, retaining AGP `9.4.0`, Gradle `9.6.0`, JDK 21, Java/Kotlin bytecode 17 and SDK 37.0. These are candidate product foundation pins, not an already implemented application stack.

## Published pins and official sources

| Component | Exact pin | Evidence |
| --- | --- | --- |
| Compose BOM | `androidx.compose:compose-bom:2026.09.00` | Latest stable in the inspected [Google Maven metadata](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml); actual [BOM POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom) parsed directly. |
| Runtime, runtime-saveable, UI and foundation | `1.12.1`, through BOM | All mappings inspected in the BOM POM; [runtime release documentation](https://developer.android.com/jetpack/androidx/releases/compose-runtime) identifies 1.12.1 as stable. |
| Material3 | `1.4.0`, through BOM | BOM mapping, published metadata and [Material3 release documentation](https://developer.android.com/jetpack/androidx/releases/compose-material3) agree. Newer Material3 branches were prerelease. |
| Activity Compose | `androidx.activity:activity-compose:1.13.0` | Independently versioned, outside the BOM; actual [metadata](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml) and [release documentation](https://developer.android.com/jetpack/androidx/releases/activity) inspected. |
| Compose compiler plugin | `org.jetbrains.kotlin.plugin.compose:2.2.10` | [Published plugin](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.plugin.compose/2.2.10); Kotlin's [migration guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html) requires matching Kotlin/compiler versions and explains AGP 9 built-in Kotlin. |
| Existing AGP and Kotlin | AGP `9.4.0`, Kotlin `2.2.10` | Actual [AGP POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.0/gradle-9.4.0.pom) declares Kotlin Gradle plugin and stdlib 2.2.10. |

Apply `org.jetbrains.kotlin.plugin.compose` in each Compose module and enable `buildFeatures.compose`. Do not add the redundant `org.jetbrains.kotlin.android` plugin with AGP built-in Kotlin. Preserve compiler version 2.2.10 rather than copying a newer Kotlin version from documentation examples. The [Compose setup guide](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) and [BOM guide](https://developer.android.com/develop/ui/compose/bom) distinguish the compiler, library alignment and separately versioned dependencies. The BOM adds no libraries by itself.

## Artifact compatibility inspection

The review read published Gradle module metadata, AAR metadata and selected manifests in memory, without installing dependencies or running Gradle.

| Actual artifact inspected | Minimum compile SDK | Minimum AGP | Relevant additional findings |
| --- | --- | --- | --- |
| `runtime-android:1.12.1` | 34 | 8.1.1 | Kotlin stdlib 2.1.20; coroutines 1.9.0. |
| `ui-android:1.12.1` | 37.0 | 9.1.0 | Manifest minSDK 23; Kotlin stdlib 2.1.20. |
| `foundation-android:1.12.1` | 37.0 | 9.1.0 | Manifest minSDK 23; Kotlin stdlib 2.1.20. |
| `material3-android:1.4.0` | 35 | 8.6.0 | Older Compose dependency floors are aligned upward by the BOM. |
| `activity-compose:1.13.0` | 36 | 8.9.1 | Activity 1.13.0, Core KTX 1.18.0, lifecycle 2.9.4, navigationevent-compose 1.0.0; Kotlin stdlib 2.1.20. |
| Transitive `core:1.18.0` | 36 | 8.9.1 | Fits the retained SDK/AGP selection. |

Reproducible representative inputs are the [UI module metadata](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1.module), [UI AAR](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1.aar) and [Activity Compose module metadata](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.13.0/activity-compose-1.13.0.module). AAR requirements came from `META-INF/com/android/build/gradle/aar-metadata.properties`. These inspected floors fit SDK 37.0 and AGP 9.4; they do not establish the resolved whole dependency closure or an actual compiler pass.

The official [AGP 9.4 compatibility table](https://developer.android.com/build/releases/agp-9-4-0-release-notes) specifies Gradle 9.6.0, JDK 17 minimum and Build Tools 36.0.0. Retain the existing JDK 21 runtime and bytecode 17. No SDK 37.1, WindowManager alpha, diagnostic Core 1.8.0 pin or unrelated dependency upgrade is required by this proposal. Activity's real transitive Core 1.18.0 requirement must be recorded in the new closure rather than confused with the diagnostic pin.

## Smallest real P1-S1 increment

Implement two independently installable Compose applications in the existing `apps/console` and `apps/desktop` boundaries. Each can provide genuinely functional local window naming, actual Activity caption title updates, New Window with a distinct logical window/document task context, bounded saved-state metadata restoration and real lifecycle state. Do not expose fake SSH, RDP, VNC or host controls. Metadata restoration must not replay remote work or authorization.

A minimal module graph can use two application modules plus meaningful shared domain and platform modules: immutable bounded local window context/validation in domain, and Android window launch, saved-state and lifecycle adapters in platform. Keep application state and installation independent; sharing source does not require a shared process, credential store or daemon. Use Cloudy design tokens while preserving native caption controls, native text editing/IME and font scaling. P1-S2 full design and P1-S3 identity remain separate work.

Declare only directly used dependencies, initially Activity Compose, UI, foundation and Material3 under the BOM; add runtime-saveable explicitly when used. No navigation framework, adaptive-navigation prerelease, Hilt, KSP, native engine or NDK is needed for this local window increment. The existing diagnostic native Views evidence does not qualify this new Compose boundary.

## Bounded acceptance checks and stop condition

1. Resolve the actual two-app dependency graph once. Retain locks, SHA verification, resolved Kotlin/compiler versions, runtime closure and license/notices inputs; detect any unexpected KGP upgrade beyond 2.2.10.
2. Compile both apps and shared modules, run Android Lint without blanket suppression, meaningful local window identity/validation/restore tests, and package/signature/manifest audits.
3. Install and launch each app independently, including with the other absent. Verify distinct New Window contexts, isolated names, recreation and bounded metadata restore, native caption controls, physical keyboard/native IME, font scaling and narrow/short desktop windows.
4. Stop at the real local foundation boundary. Successful Compose rendering and lifecycle behavior do not qualify SSH, Desktop engines, audio, input transport, latency or a product capability.

## Research execution limits

The android-cli and repository reference-research workflows were used. An initial CLI help invocation without explicit Android user environment failed because the default home cache was read-only. The existing CLI then ran with the explicit existing cache environment; its documentation search automatically refreshed a missing local knowledge-base cache. No SDK, dependency or CLI installation, repository source change, build, device interaction or Git operation was performed for this research. No new network research was performed while persisting this entry.

## Registered sources and independent sample

The source register maps the material URLs above as follows: **G161** (Google Maven metadata), **G162** (BOM POM), **G163** (runtime release documentation), **G164** (Material3 release documentation), **G165** (Activity Compose release metadata), **G166** (Activity release documentation), **G167** (Published plugin), **G168** (migration guide), **G46** (AGP POM), **G169** (Compose setup guide), **G170** (BOM guide), **G171** (UI module metadata), **G172** (UI AAR), **G173** (Activity Compose module metadata), **G35** (AGP 9.4 compatibility table). Existing G35/G46 retain their original individual checked dates; this entry and the new source records are checked 2026-10-16.

The integration leader independently inspected a sample retained at `.cache/foundation-entry-01/research/parent-check.json` (checked 2026-10-16 UTC). It binds the exact BOM POM, AGP POM, UI AAR and Activity Compose AAR with byte counts and SHA-256 digests. It confirms BOM runtime/runtime-saveable/UI/foundation 1.12.1 and Material3 1.4.0, AGP Kotlin/stdlib 2.2.10, UI minimum compile SDK 37.0 / AGP 9.1.0 and Activity minimum compile SDK 36 / AGP 8.9.1. The ignored receipt was read without alteration; this independent metadata sample does not establish build or device compatibility.
