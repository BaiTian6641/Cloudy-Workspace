# Device baseline qualification probe

This is a dedicated **P0-S1 diagnostic Android APK**, not Cloudy Console or Cloudy Desktop. Its controls query real framework APIs. The **Refresh diagnostics** button re-reads the build, input classes and video decoder reports, captures the current window, and schedules a private JSON snapshot. Installing or running it does not establish remote-protocol, USB, input, decoder-performance or desktop-product support.

## Build and check

The initial pinned candidate uses AGP **9.4.0**, its built-in Kotlin compiler, Gradle **9.6.0**, SDK **37.0**, Build Tools **36.0.0**, min SDK **35**, target SDK **37**, JDK **21** to run Gradle, and Java/Kotlin **17** bytecode. There is no Compose, AndroidX, NDK, dependency-injection or network library. JUnit **4.13.2** is a test-only dependency. Toolchain compatibility and device results must be recorded after actual runs; these settings are not build evidence.

From this directory, with the project-local tool paths supplied by the maintainer:

```bash
export ANDROID_HOME="$(git rev-parse --show-toplevel)/.cache/android-sdk"
export ANDROID_SDK_HOME="$(git rev-parse --show-toplevel)/.cache/android-user"
export ANDROID_USER_HOME="$ANDROID_SDK_HOME/.android"
export GRADLE_USER_HOME="$(git rev-parse --show-toplevel)/.cache/gradle-home"
mkdir -p "$ANDROID_SDK_HOME" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Select the intended JDK 21 in the invoking environment. The wrapper/tool distributions are maintained separately from the probe sources. The legacy Android SDK home and Android user home deliberately resolve to the same `.android` location. Kotlin compilation runs in process to avoid an additional user-home daemon cache; it uses the same pinned compiler. Compilation includes Kotlin warnings as errors; Android Lint treats warnings as errors. Do not add blanket suppressions to obtain a passing result. The focused JVM tests verify event retention order, eviction accounting and snapshot independence. They do not exercise Android APIs or constitute device evidence.

The sole initial lint exception is `AndroidGradlePluginVersion` for `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.6.0 is intentionally pinned to AGP 9.4's documented baseline. See [toolchain rationale](../../../docs/03-platform/googlebook/probe-toolchain.md). `lint.xml` preserves version notices elsewhere and all source/resource warnings.

## Install, run and retrieve

Use the authorized target's serial privately in `CLOUDY_DEVICE_SERIAL`; do not paste it into source-controlled records. The maintainer must select the device explicitly. These commands are instructions, not a claim that they have run:

```bash
adb -s "$CLOUDY_DEVICE_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$CLOUDY_DEVICE_SERIAL" shell am start -W \
  -n dev.cloudy.qualification.baseline/.BaselineActivity
```

Wait for **Private report saved**. Resize or move the native window, change focus, and use Refresh diagnostics after changes to connected input devices. Preserve the OS caption controls. The UI shows actual reported values and explicit pending/error state; codec detail lives in the private report.

Export to an ignored private location from the repository root:

```bash
mkdir -p qualification/results/private
adb -s "$CLOUDY_DEVICE_SERIAL" exec-out run-as dev.cloudy.qualification.baseline \
  cat files/baseline.json > qualification/results/private/device-baseline.json
python3 -m json.tool qualification/results/private/device-baseline.json > /dev/null
```

`run-as` requires the installed debuggable build and its package identity. Treat failure as a failure; do not substitute sample JSON. Read `revision`, `collectionInProgress`, section statuses and `lastCollectionAtDeviceUtc` before using an export. A previous file can remain after a save failure; the UI reports that limit. Sanitize and review an export before deriving public evidence, recording the exact APK SHA-256, source commit, build/backend identity, observation time and limitations. Device clock timestamps are reported clock readings, not synchronized measurement evidence.

## Observations and limits

| Surface | Collected facts | Limits |
| --- | --- | --- |
| Build | Manufacturer/model/product/device/hardware, OS build ID/display/fingerprint, release/API/security patch/incremental and supported ABI list | The fingerprint identifies an OS build; no serial, Android ID, account, network identity or hardware unique identifier is requested |
| Input | Reported source masks/classes, keyboard type, virtual/external flags | No names, IDs, descriptors, vendor/product IDs, pressed keys or text; Refresh obtains a new snapshot, not a live behavioral test |
| Video decoders | `ALL_CODECS` video MIME entries, canonical/alias names, vendor/hardware/software reports, supported/required low-latency/secure/tunneled features, profile levels and video ranges | Includes special-mode/secure codecs and aliases. No decoder instance, actual frame decoding, stream compatibility, concurrency, thermal, throughput or latency test |
| Window/display | Current/maximum metrics, density, native system/caption/IME insets, caption rectangles and inset frame, current physical display mode and reported refresh rate | Caption rectangles use the inset frame's coordinate space and can differ from final layout. Empty rectangles/hidden captions are actual observations, not an assumed API failure |
| Lifecycle | Creation/start/resume/pause/stop/destroy, top-resumed state, save-instance-state, focus, multi-window and configuration callbacks; changed window snapshot events | Last 128 events for this process only. Android need not call destruction callbacks before process death; a queued write can be lost |

The recorder lives for the application process, so Activity recreation retains its bounded event ring. It serializes codec queries and file writes on one worker, coalesces refresh/save work, and uses `AtomicFile` for `files/baseline.json`. Because `AtomicFile` can log commit errors without throwing them, a bounded readback must match the expected bytes before the UI reports that revision as saved. A failed write can leave the previous file; a readback failure leaves the current file unverified. This is not a guarantee against arbitrary power loss. No activity or credential is shared with another application.

Limits are explicit: at most 64 input entries, 128 video decoder entries, eight video MIME types per decoder, 16 profile/level pairs per type, 32 caption rectangles, and a 256 KiB codec-record budget. Omitted-entry counters are recorded. The full saved report is limited to 512 KiB; exceeding it reports a save error and keeps the previous file. Vendor codec enumeration can block in framework code, so it runs off the UI thread; the probe does not claim a hard timeout on that framework call.

The manifest requests no permissions, disables backup, and excludes probe files from backup/device-transfer rules. It does not register receivers, services, external file providers or network/USB access. The ordinary exported launcher Activity ignores external payloads and performs only these local diagnostic reads.

## Official reference and test review

Reviewed 2026-10-15:

- [MediaCodecList](https://developer.android.com/reference/android/media/MediaCodecList): `ALL_CODECS`, `getCodecInfos`; inspect actual installed reports rather than a model-name inference.
- [MediaCodecInfo](https://developer.android.com/reference/android/media/MediaCodecInfo) and [CodecCapabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities): supported types/profile levels, canonical name/alias and reported flags; hardware acceleration flags are manufacturer reports, not independent measurements.
- [InputManager](https://developer.android.com/reference/android/hardware/input/InputManager) and [InputDevice](https://developer.android.com/reference/android/view/InputDevice): input enumeration, source classes and keyboard/virtual/external flags.
- [WindowMetrics](https://developer.android.com/reference/android/view/WindowMetrics), [WindowInsets](https://developer.android.com/reference/android/view/WindowInsets), and [desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing): visual-context bounds/density and API-35 inset frame/caption bounding rectangles. The minimum SDK deliberately covers these methods.
- [Activity](https://developer.android.com/reference/android/app/Activity): lifecycle, focus, configuration and multi-window callbacks; lifecycle observation is distinct from a survival guarantee.
- [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) and [app-specific storage](https://developer.android.com/training/data-storage/app-specific): private bounded snapshot persistence; one worker owns writes because AtomicFile is not a locking primitive.
- [Build](https://developer.android.com/reference/android/os/Build): reported build properties, without querying serial/unique IDs.
- [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) and [CompileSdkSpec](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/CompileSdkSpec): no separate Kotlin Android plugin; explicit API-37 minor level 0.

The installed official testing-setup skill was read with the project's recorded overrides. This small probe uses platform Views plus focused JUnit tests; no Hilt, Compose screenshot framework, phone tests, alpha toolchain migration or synthetic successful device implementation was introduced. Physical window/focus, refresh, export and failure behavior remain separate checks for the maintainer and independent reviewer.
