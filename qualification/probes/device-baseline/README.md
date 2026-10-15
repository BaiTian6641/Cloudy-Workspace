# Device baseline and window qualification probe

This is a dedicated **P0 diagnostic Android APK**, not Cloudy Console or Cloudy Desktop. Version **0.2.0** adds a first P0-S2 increment: real document-task windows, per-window markers and explicit Activity recreation. Its controls invoke actual Android APIs. **Refresh diagnostics** re-reads shared build/input/video-decoder reports and captures the requesting window. Installing, compiling or running this probe does not establish remote-protocol, USB, input, decoder-performance or desktop-product support, and this increment does not complete P0-S2.

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

Select the intended JDK 21 in the invoking environment. The wrapper/tool distributions are maintained separately from the probe sources. The legacy Android SDK home and Android user home deliberately resolve to the same `.android` location. Kotlin compilation runs in process to avoid an additional user-home daemon cache; it uses the same pinned compiler. Compilation includes Kotlin warnings as errors; Android Lint treats warnings as errors. Do not add blanket suppressions to obtain a passing result.

Focused JVM tests cover independent marker state, supplied-field restoration and invalid-state reset, counter overflow, generation replacement, late-callback rejection, task-identity collisions, live-window capacity, closed-record eviction, event correlation/retention and observation deduplication. The existing event-ring tests also remain. These tests exercise pure state/recorder policy; they do not execute Android Bundle callbacks, task launches, the native UI, file persistence or physical-device behavior.

The sole initial lint exception is `AndroidGradlePluginVersion` for `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.6.0 is intentionally pinned to AGP 9.4's documented baseline. See [toolchain rationale](../../../docs/03-platform/googlebook/probe-toolchain.md). `lint.xml` preserves version notices elsewhere and all source/resource warnings.

## Install, run and retrieve

Use the authorized target's serial privately in `CLOUDY_DEVICE_SERIAL`; do not paste it into source-controlled records. The maintainer must select the device explicitly. These commands are instructions, not a claim that they have run:

```bash
adb -s "$CLOUDY_DEVICE_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$CLOUDY_DEVICE_SERIAL" shell am start -W \
  -n dev.cloudy.qualification.baseline/.BaselineActivity
```

Wait for **Private report saved**. The UI shows reported values and explicit pending/error state; codec detail lives in the private report. **New window**, **Change marker**, **Recreate this window** and **Refresh diagnostics** have stable resource IDs (`new_window`, `change_marker`, `recreate_window`, `refresh_diagnostics`) for explicit target-scoped UI inspection. Inspect current UI bounds before using automation; do not assume old coordinates still identify the same control.

For the first two-window experiment:

1. Record window A's logical ID, observed task ID, fresh Activity generation ID, marker and counter. Change its marker twice.
2. Press **New window** in A. The explicit same-package Intent requests a distinct document task with `FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK`. Confirm a second actual task and visible native window B. A successful `startActivity` return is only a launch-request observation. The OS chooses placement; manually arrange windows when necessary.
3. Confirm B starts with a different logical ID, marker A and counter zero. Change B once, return to A, and verify A retains its own marker/counter. Compare the two `windows[]` records in the exported JSON.
4. Press **Recreate this window** in A. Verify the same logical ID and marker/counter, a different generation ID, `restored-bundle`, and the actually observed task ID. Do not treat the task ID as a permanent identity. B's state must remain unchanged.
5. Resize/move each native window, change focus, minimize/restore and inspect correlated lifecycle/geometry events. Preserve native caption controls. Repeat on a narrow desktop tile and at a larger font scale: the entire probe scrolls, including all controls. Viewport bounds, not the changing diagnostics text height, trigger geometry observation.

These steps are test instructions, not recorded passes. Physical task/window isolation, framework state restoration and window interaction require actual evidence. Process death, foreground-service timeouts, operations surviving recreation, IME/physical input, additional displays and VM integration remain separate experiments. See the [P0-S2 window contract](../../../docs/03-platform/googlebook/p0-s2-window-probe-contract.md).

Export to an ignored private location from the repository root:

```bash
mkdir -p qualification/results/private
adb -s "$CLOUDY_DEVICE_SERIAL" exec-out run-as dev.cloudy.qualification.baseline \
  cat files/baseline.json > qualification/results/private/device-baseline.json
python3 -m json.tool qualification/results/private/device-baseline.json > /dev/null
```

`run-as` requires the installed debuggable build and its package identity. Treat failure as a failure; do not substitute sample JSON. Read `schemaVersion`, `revision`, `collectionInProgress`, section statuses, `retention` and `lastCollectionAtDeviceUtc` before using an export. A previous file can remain after a save failure; the UI reports that limit. Sanitize and review an export before deriving public evidence, recording the exact APK SHA-256, source commit, build/backend identity, observation time and limitations. Device clock timestamps are reported clock readings, not synchronized measurement evidence.

Schema **2** replaces the previous single `window` object with bounded `windows[]` records. Each record identifies its logical window, observed task and current Activity generation, restoration source, marker/counter, observed lifetime and latest geometry. Events include the same identity tuple, the state known at that event and an explicit attribution such as `current`, `stale-generation-or-task`, `closed-generation` or `not-retained`. Process-wide metadata refresh events have a null window identity. A stale callback remains visible as a rejected event but cannot mutate the current generation's state, geometry or lifetime. Consumers of schema 1 must explicitly migrate; the file name remains `baseline.json`.

## Observations and limits

| Surface | Collected facts | Limits |
| --- | --- | --- |
| Build | Manufacturer/model/product/device/hardware, OS build ID/display/fingerprint, release/API/security patch/incremental and supported ABI list | The fingerprint identifies an OS build; no serial, Android ID, account, network identity or hardware unique identifier is requested |
| Input | Reported source masks/classes, keyboard type, virtual/external flags | No names, IDs, descriptors, vendor/product IDs, pressed keys or text; Refresh obtains a new snapshot, not a live behavioral test |
| Video decoders | `ALL_CODECS` video MIME entries, canonical/alias names, vendor/hardware/software reports, supported/required low-latency/secure/tunneled features, profile levels and video ranges | Includes special-mode/secure codecs and aliases. No decoder instance, actual frame decoding, stream compatibility, concurrency, thermal, throughput or latency test |
| Window/display | Current/maximum metrics, density, native system/caption/IME insets, caption rectangles and inset frame, current physical display mode and reported refresh rate | Caption rectangles use the inset frame's coordinate space and can differ from final layout. Empty rectangles/hidden captions are actual observations, not an assumed API failure |
| Window identity/state | Random logical window ID restored only from that Activity's Bundle; observed `taskId`; random fresh generation ID on every `onCreate`; marker/counter, restoration outcome | Random probe identities are not hardware identifiers. An observed task ID is opaque and is not used as a permanent identity. The recorder never supplies restoration state |
| Lifecycle | Creation/start/resume/pause/stop/destroy, top-resumed state, save-instance-state, focus, multi-window and configuration callbacks; changed window snapshot events, all correlated with a window generation | Last 128 events for this process only. Android need not call destruction callbacks before process death; a queued write can be lost |

The recorder lives for the application process, so Activity recreation retains its bounded event ring. It serializes codec queries and file writes on one worker, coalesces refresh/save work, and uses `AtomicFile` for `files/baseline.json`. Because `AtomicFile` can log commit errors without throwing them, a bounded readback must match the expected bytes before the UI reports that revision as saved. A failed write can leave the previous file; a readback failure leaves the current file unverified. This is not a guarantee against arbitrary power loss. No activity or credential is shared with another application.

Each Activity owns immutable local marker/counter state and explicitly saves four small fields into its own Bundle. A new document receives no identity/state Intent extras and starts fresh. A valid Bundle restores the logical ID and local state; an invalid version, UUID, marker or negative counter resets the entire diagnostic state and reports `invalid-bundle-reset`. The counter saturates rather than wrapping. A new Activity always receives a new generation ID. Configuration recreation remains enabled; the manifest does not consume configuration changes to keep an Activity artificially alive.

The recorder retains at most **eight** logical-window records. A stopped or unfocused Activity still occupies a live slot. A new generation in the same task replaces that logical window's previous generation and clears its old geometry; late callbacks cannot close or overwrite the replacement. A duplicate logical ID in a different live task is rejected with `live-task-identity-conflict`. When full, the oldest retained closed record may be evicted; if every record is live, registration is rejected with `registry-capacity`. Counters report evictions, rejected registrations/callbacks and superseded generations. **New window** is disabled for an untracked window or when all tracking slots are occupied. System UI/external launches can exceed this diagnostic limit; they display untracked state and record the rejection, without pretending to be captured. This bounds evidence memory, not Android's task count or the number of windows the OS permits. Closed records remain historical observations and do not imply their tasks are still present.

Limits are explicit: at most 64 input entries, 128 video decoder entries, eight video MIME types per decoder, 16 profile/level pairs per type, 32 caption rectangles, and a 256 KiB codec-record budget. There are at most eight retained window snapshots of 32 KiB each and 128 shared correlated events. An oversized window snapshot becomes an explicit collector error. Omitted-entry counters are recorded. The full saved report remains limited to 512 KiB; combined maximum sections need not fit, and exceeding the total reports a save error and keeps the previous file. Vendor codec enumeration can block in framework code, so it runs off the UI thread; the probe does not claim a hard timeout on that framework call.

The manifest requests no permissions, disables backup, and excludes probe files from backup/device-transfer rules. It does not register receivers, services, external file providers or network/USB access. The ordinary exported launcher Activity ignores external payloads and performs only these local diagnostic reads.

## Official reference and test review

Reviewed 2026-10-15:

- [MediaCodecList](https://developer.android.com/reference/android/media/MediaCodecList): `ALL_CODECS`, `getCodecInfos`; inspect actual installed reports rather than a model-name inference.
- [MediaCodecInfo](https://developer.android.com/reference/android/media/MediaCodecInfo) and [CodecCapabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities): supported types/profile levels, canonical name/alias and reported flags; hardware acceleration flags are manufacturer reports, not independent measurements.
- [InputManager](https://developer.android.com/reference/android/hardware/input/InputManager) and [InputDevice](https://developer.android.com/reference/android/view/InputDevice): input enumeration, source classes and keyboard/virtual/external flags.
- [WindowMetrics](https://developer.android.com/reference/android/view/WindowMetrics), [WindowInsets](https://developer.android.com/reference/android/view/WindowInsets), and [desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing): visual-context bounds/density and API-35 inset frame/caption bounding rectangles. The minimum SDK deliberately covers these methods.
- [Activity](https://developer.android.com/reference/android/app/Activity): lifecycle, focus, configuration and multi-window callbacks; lifecycle observation is distinct from a survival guarantee.
- [Intent](https://developer.android.com/reference/android/content/Intent), [document tasks](https://developer.android.com/guide/components/activities/recents), and [Activity manifest](https://developer.android.com/guide/topics/manifest/activity-element): standard launch mode with explicit new-document/multiple-task flags. `FLAG_ACTIVITY_LAUNCH_ADJACENT` is intentionally absent because its split-screen contract is not desktop placement evidence. No `maxRecents` value is used as an assumed live-task cap.
- [WindowManager](https://developer.android.com/reference/android/view/WindowManager): activity-scoped `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` (API 35) exposes a system affordance; it does not implement task isolation or force placement. Minimum SDK 35 covers this and the previously used inset APIs.
- [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) and [app-specific storage](https://developer.android.com/training/data-storage/app-specific): private bounded snapshot persistence; one worker owns writes because AtomicFile is not a locking primitive.
- [Build](https://developer.android.com/reference/android/os/Build): reported build properties, without querying serial/unique IDs.
- [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) and [CompileSdkSpec](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/CompileSdkSpec): no separate Kotlin Android plugin; explicit API-37 minor level 0.

The installed official testing-setup skill was read with the project's recorded overrides. This small probe uses platform Views plus focused JUnit tests; no Hilt, Compose screenshot framework, phone tests, alpha toolchain migration or synthetic successful device implementation was introduced. Physical window/focus, refresh, export and failure behavior remain separate checks for the maintainer and independent reviewer.
