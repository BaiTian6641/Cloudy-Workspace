# Foundation testing strategy

**Scope:** the actual P1-S1 root graph `:console`, `:desktop`, `:domain`, `:platform`, `:design-system`. Strict build12 and the bounded connected cases below now pass; P1-S1 local construction acceptance is complete. Follow [D12](../05-delivery/library-reuse-policy.md) and the [reviewed skill overrides](../07-reference/development-skills.md).

## Test boundaries

| Layer | Actual cases | What they establish |
| --- | --- | --- |
| `:domain` JUnit4 | Five tests | Fresh local IDs, immutable rename, owner/schema/ID restoration rejection and Unicode/name bounds, including line/paragraph separators |
| `:design-system` JUnit4 | Four draft-codec tests | Bounded native-editor snapshot decoding and rejection; no Compose rendering or Android framework execution |
| `:platform` Android instrumentation | Five tests | Real Bundle/Parcel restoration, malformed field/type rejection, owner/schema validation and invalid serialized ID/name handling |
| Each app Android instrumentation | Two shared Activity cases per app | Committed context/system labels plus unsaved draft survive actual Activity recreation; invalid edit leaves committed identity/labels unchanged |

The shared Activity source is actually `testing/foundation/src/androidTest/kotlin`, wired into both app targets through `AndroidSourceSet.kotlin.directories`. It is not a sixth production Gradle module or shared runtime process. With AGP built-in Kotlin, adding a Kotlin directory to the Java source set is insufficient; the [official migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin) describes the Kotlin source-set configuration. Keep source discovery verified rather than interpreting an empty test APK as a pass.

Use the upstream `AndroidJUnitRunner`, runner/core1.7.0, ext JUnit1.3.0 and explicit test-only Espresso3.7.0, retaining JUnit4.13.2. Explicit test-only Espresso core/idling-resource3.7.0 overrides Compose's3.5.0 floor: the old floor failed on the actual Android17 target before either Activity assertion. The [official issue2507](https://github.com/android/android-test/issues/2507) and fixed upstream release support this narrow correction; no product runtime dependency changed. Compose UI testing is BOM2026.09.00 / ui-test-junit4 1.12.1, using `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` and explicit idle synchronization. The selected artifact deprecates the v1 factory; see [G184 migration](https://developer.android.com/develop/ui/compose/testing/migrate-v2). ActivityScenario drives the real Activity and Compose semantics drive the native editor. No custom runner, Hilt, Robolectric, mocking, screenshot plugin or coverage expansion is required by this slice. Introduce another tool only for a concrete uncovered requirement. Framework stubs and pure codec tests cannot count as actual Android Bundle, Activity or input evidence.

## Build and discovery

From the repository root, using the parent-selected existing JDK/SDK, isolated Gradle user directory and accepted Android signing directory:

```bash
./gradlew --dependency-verification=strict --no-daemon --console=plain --max-workers=4 \
  :console:assembleDebug :desktop:assembleDebug \
  :console:lintDebug :desktop:lintDebug \
  :domain:test :design-system:testDebugUnitTest \
  :platform:assembleDebugAndroidTest \
  :console:assembleDebugAndroidTest :desktop:assembleDebugAndroidTest
```

Record actual compiler, Lint and JUnit reports and test APK discovery from that build. Test compilation/package success is not execution. An absent/skipped test suite is not passing. Build12 executes all nine JVM tests; the recorded device run executes all nine instrumentation cases. These are distinct suites, not18 Android tests.

## Connected execution and independence

The parent first performs the independent-package install/launch cases: Console with Desktop absent and Desktop with Console absent, preserving existing diagnostic/evidence data. Connected test tooling may install the module's own test dependencies; do not use that to infer independent launch. Select the actual device explicitly and run only these modules:

```bash
ANDROID_SERIAL="$QUALIFIED_DEVICE_SERIAL" ./gradlew \
  --dependency-verification=strict --no-daemon --console=plain --max-workers=4 \
  :platform:connectedDebugAndroidTest \
  :console:connectedDebugAndroidTest :desktop:connectedDebugAndroidTest
```

Console package is `io.github.baitian6641.cloudy.console`; Desktop is `io.github.baitian6641.cloudy.desktop`. Neither product requests network/storage permissions or runs a remote engine in this slice. Shared source does not share app state, credentials or another app's process. Keep the actual product/test APK identities and app/window context bound to each result.

ActivityScenario recreation is not process death, a cold-launch persistence guarantee or a physical keyboard/native IME pass. Compose text entry is framework test stimulus. These tests do not qualify accessibility, system caption geometry, physical shortcut behavior, continuous sessions or remote features. Parent-owned device observations and later P1/product exits retain those distinct boundaries. Preserve failed builds and test results; do not rebuild unchanged upstream components or rerun accepted device cases just to document this strategy.

## Actual bounded execution checkpoint

The [native foundation report](../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 without dependency-regeneration flags: domain5/design4 JVM cases pass, with zero findings in both app Lint runs. On HP Googlebook14 Android17/API37 build `CL3B.260622.276.R2`, platform5 real Bundle/Parcel cases, Console2 and Desktop2 Activity cases pass (nine connected cases). Each product installs/launches with the counterpart absent.

The first Console connected run fails in Espresso3.5 reflection. The explicit test-only Espresso3.7 pin repairs that test-framework compatibility boundary; final product APK hashes remain frozen and unchanged. Preserve that failed run separately from final passing reports. Desktop New/Close, both same-version package replacements and independent uninstall receipts now pass; P1-S1 is complete in this local construction scope. These results do not qualify physical input/native IME, process death or remote features.

## P1-S3 storage increment

The foundation command/CI now also runs `:identity:testDebugUnitTest :identity:lintDebug :identity:assembleDebugAndroidTest`. Strict build04 passes with14 identity JVM cases, adding to the existing domain5/design4 reports. The parent separately executed only `:identity:connectedDebugAndroidTest` for18 passing tests on the explicit Googlebook. The [result](../../qualification/results/P1-S3/2026-10-16-vault-storage-tests.md) uses real Android file APIs but test-only software/fault-injection crypto; alias derivation is not key generation. It does not qualify actual product authorization/AES or rerun the earlier product Activity suites. The [source review](2026-10-16-product-vault-review.md) defines the next product checks. Both installed apps remain0.1.1/code2;0.1.2/code3 is built only.
