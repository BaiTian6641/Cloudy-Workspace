# Native stream APK and local JNI review

**Date:** 2026-10-15. **Stage:** P0-S4 remains in progress. **Result:** a real, separately named Android diagnostic compiled, passed its scoped Lint gate, packaged the accepted native core and completed one local JNI loading/metadata observation on Googlebook. No pairing, TLS handshake, host connection, decoded frame or stream is established. See the [device result](../../qualification/results/P0-S4/2026-10-15-native-stream-apk-load.md) and [hashed manifest](../../qualification/results/P0-S4/2026-10-15-native-stream-apk-load.json).

## Owned boundary

`tools/prepare_stream_android.py` reconstructs the actual pinned Moonlight Java/resource closure from the accepted native preparation, then applies the tracked Android overlay. Preparation records 633 staged inputs and rejects changed or extra source/build/native payloads. The Gradle pre-build task verifies the prepared source again. Existing source/JNI class names remain `com.limelight`; the independently installable application ID is `dev.cloudy.qualification.stream`. No upstream discovery, pairing, USB or credential-owning component is registered.

The sole owned Activity supplies native framework Views, inset handling, a visible local load action and Close. It invokes actual `MoonBridge.getLaunchUrlQueryParameters`, allowing upstream class initialization to load/init the real native core once. No fake renderer or metadata response is substituted. A worker thread performs the load and atomically publishes success/failure evidence. The main-thread UI only updates while the Activity remains alive. A single native-library load observation is not a stream lifecycle test or a guarantee that a hung native entry is cancellable. No host operation or key provider is reached.

The final source manifest removes merged `INTERNET` and `androidx.startup.InitializationProvider`. The latter carries **node-local** `tools:ignore="MissingClass"` solely on its `tools:node="remove"` marker: Lint entry04 complained about a class referenced by this removal instruction, although the runtime manifest had no such component. Inspection of the final merged manifest confirms **one owned Activity, zero permissions and zero providers/services/receivers**. The ignore is not application-wide or a suppression on owned executable code; retire/review the marker when enabling a future network dependency initializer. Entry02's unwanted merge additions were caught before device use.

## Scoped Lint and dependency evidence

The baseline contains exactly the 27 errors in entry01's retained Lint XML: **25 MissingPermission and two GestureBackNavigation**, with matching IDs, messages, source text and locations. All locations are in seven pinned `com/limelight` source paths; none is owned `dev/cloudy` code. Those upstream paths are inactive in this local ABI diagnostic. There is no blanket MissingPermission/back suppression or disabled error-abort policy. The existing upstream MissingTranslation exception and old BC-package-specific lint configuration remain explicit inherited exceptions.

Entry05 Android assembly and Lint completed `BUILD SUCCESSFUL`. Final Lint has **zero new errors, 337 warnings and one baseline hint**; it is not warning-free. The 27 inactive upstream errors are excluded by the exact baseline, not fixed or qualified. The hint notes differing baseline/current variant labels; the actual matching error identities were reviewed. Retire affected entries and qualify permissions/back behavior before enabling those paths. Parent-reported five focused Android-staging tests passed; these are helper tests, not JVM engine or device tests. Full shared-check totals belong to the parent's final checkpoint, not this report.

`recordCloudyRuntimeDependencies` hashes the actual resolved debug runtime artifacts. Fourteen artifacts are recorded, including OkHttp Android/Okio, BC, JCodec, JmDNS, ShieldControllerExtensions, Kotlin and AndroidX startup/tracing dependencies. A removed manifest initializer does not remove its classes/artifact from the graph. This is selected runtime closure evidence, not the final dependency/source/license SBOM or a reason to silently replace versions.

## Build and packaging identity

- Diagnostic **0.1.0/code1**, build reference `04cf72ef-ece2-4e15-b072-ef6e73dd203c`; minimum API35, compile SDK37 and inherited **target SDK36**. Target37 behavior has not been substituted or qualified.
- APK **21,713,253 bytes**, SHA256 `b674ab7c55e4775ed3f79941b3ec95fa6acb20cd26e864d6f483f3146ca0958a`.
- Native build reference `f336e245-805d-4aad-bc1a-9208df8db318`. The actual packaged arm64 core is **5,110,376 bytes**, SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`, byte-identical to the [accepted native build](../../qualification/results/P0-S4/2026-10-15-native-stream-build.md). It is stored with 16 KiB APK alignment; existing native ELF alignment/dependency qualification is reused.
- APK signature verification passed. Selected native notice assets match the retained prepared inputs. The APK also contains dependency public-suffix data. Source archives and build materials remain in the private native preparation bundle; the APK's selected notices are not all corresponding source or complete transitive notices.

## Retained failures and acceptance limit

Android entry01 compiled but failed Lint with 27 errors/337 warnings. Entry02 failed because the baseline was generated at the wrong path. Entry03 was prepared only and superseded. Entry04's removal-marker MissingClass finding failed Lint. Entry05 contains the reviewed narrow marker annotation and completed successfully. Keep all five statuses distinct from the earlier native entry01/02 history.

The initial device operator layout predicate expected a nonempty window title and failed; that was an observation-helper failure, not an app failure. The corrected check used the unique visible diagnostic heading plus actual focused component. Success UI was observed, no screenshot was captured, and the app was subsequently closed/stopped.

The local device record establishes only native loading/initialization and the metadata method's return on this build. Next work is the [app-owned key provider](../03-platform/googlebook/native-stream-key-provider-entry.md), real pairing/trust, finite HTTP/native cancellation, then actual decoder/audio/Surface stream and desktop input. D16's [open-source direction](../adr/0010-desktop-open-source-distribution.md) retains exact dependency/notices/corresponding-source/build release gates. No vault, hardware decoding, performance, 3D, host interoperability or CAP-STREAM pass follows here. This documentation task reran no compiler/device check; it inspected retained evidence and actual source.
