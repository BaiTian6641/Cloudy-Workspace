# Native USB access boundary diagnostic

This is a separate **P0-S5 qualification APK**, `dev.cloudy.qualification.usb` **0.1.0/code1**. It is not Console, Desktop, a USB exporter or a completed provider. It loads actual libusb **1.0.30**, initializes/exits an explicit no-discovery context, enumerates through public Android `UsbManager`, and honestly reports zero devices. No library enumeration, network export, interface claim, driver detach, reset, configuration change, endpoint transfer or programming action is implemented.

A user must select an identified peripheral before requesting Android access and inspecting a cached native device descriptor. Hubs and ADB interfaces are excluded. The Java connection owns the borrowed FD until native handle/context cleanup finishes. Worker operations are serialized; selection/cancel/detach/hidden-window invalidation rejects stale opens/replies. Native access expires after 15seconds; Android permission requests expire after30seconds. Creator-owned immutable PendingIntent extras correlate a per-request nonce, generation and selected device; the receiver checks correlation before consuming pending state and re-queries actual device presence/eligibility/permission rather than depending on system fill-in extras. Cancellation reports requested cleanup until the owner executor finishes; no unsafe concurrent close interrupts a blocking kernel operation. Exiting/minimizing closes access; there is no service. Android's grant can outlive this access lease until unplug, and Cloudy does not claim to revoke that OS grant.

A successful no-device run proves only loading, initialization and the actual enumeration result. It does not qualify granted FD access, physical cancellation/hotplug, USB forwarding, reset, arbitrary classes, programming, VirtualHere control or CAP-USB. **Actual device follow-up:** the [Googlebook preflight](../../results/P0-S5/2026-10-15-usb-native-preflight.md) loads libusb and enumerates two unselected devices. No zero-device or granted-access result is claimed. With no selected peripheral, only loading/enumeration preflight is authorized; do not open the development/debug USB link or unrelated devices to manufacture evidence.

## Build and inspect

Use the existing project-local SDK37, Build Tools36.0.0, NDK29.0.14206865, AGP9.4.0, shared Gradle9.6.0 wrapper and JDK21. Java/Kotlin target17, minSDK35 and arm64-v8a match the existing finite native diagnostic baseline. No global package or CLI installation is required. USB host, touchscreen and faketouch feature declarations are optional; desktop keyboard/pointer use must not create a touch-device installation filter. From repository root:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$PWD/.cache/android-sdk"
export ANDROID_SDK_HOME="$PWD/.cache/android-user"
export ANDROID_USER_HOME="$ANDROID_SDK_HOME/.android"
export GRADLE_USER_HOME="$PWD/.cache/gradle-home"
python3 tools/prepare_usb_native.py --build
qualification/probes/device-baseline/gradlew -p qualification/probes/native-usb \
  :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon
python3 tools/prepare_usb_native.py --check
python3 -m unittest discover -s qualification/probes/native-usb/tests -p 'test_*.py'
```

`prepare_usb_native.py --build` explicitly downloads the pinned official release archive if missing, verifies its exact SHA256/size before extraction and rechecks every cache hit. Archives, logs and build identity receipt stay under ignored `.cache/native-usb/`; generated libraries/notices stay under ignored `app/build/`. Gradle only invokes offline `--check`; it never starts a native build/download. Input changes invalidate the receipt. Build inputs are snapshotted before compilation and checked again before publication; the receipt always binds the captured source. The isolated build process group is terminated on timeout/interruption, including descendants after the Make leader exits. Preparation hashes only its owned generated native directories, leaving AGP-generated outputs intact.

The script reuses upstream `android/jni/libusb.mk`, excludes upstream sample/test executables, and builds the tiny JNI file with strict C compiler diagnostics. The NDK shell entry rejects spaces in this workspace's SDK path; the script invokes the same upstream `build/core/build-local.mk` through Make with a temporary SDK alias. No SDK files are changed. The helper checks AArch64 ELF type,16KiB load alignment, required JNI exports and allowlisted dynamic dependencies. Both shared libraries are packaged rather than hiding libusb in a static app binary. Gradle dependency verification metadata is inherited from the already selected native-RDP build; `app/gradle.lockfile` pins this probe's resolved graph. Retained unused verification entries do not add runtime dependencies.

The identical locked Kotlin2.2.10/annotations13.0 runtime notice bundle is reused from the reviewed native-RDP diagnostic, with USB-specific explanatory headings and corresponding hash updates; this introduces no runtime dependency.

Generated APK assets include the full unmodified libusb release source archive, upstream LGPL2.1-or-later license text, pinned source lock, JNI corresponding source, reproducible Make recipe, helper source and native manifest. The recipe uses `LIBUSB_SOURCE` for the extracted release and `CLOUDY_SOURCE` for the JNI source directory. Keep these materials with redistributed diagnostic packages. Product license/distribution adoption remains a separate P1-S4 review.

Result locations:

- APK: `qualification/probes/native-usb/app/build/outputs/apk/debug/app-debug.apk`.
- Compiler/Lint/JVM output: Gradle output; XML test results and lint reports under `app/build/`.
- Native log/receipt: `.cache/native-usb/build.log`, `.cache/native-usb/receipt.json`.
- APK identity must be measured after the final reviewed build; source `BuildConfig.QUALIFICATION_BUILD_REF` and `source-lock.json` share the build identity.

Twenty focused JVM tests exercise actual Cloudy lease code with test-only native/Android adapters: owner cleanup order, idempotent close, stale generation, cancellation during open/wrap/description, absent device, invalid FD, replacement and exceptions. These fixtures do not represent physical USB results. Three cleanup-admission regressions cover a replacement lease before delayed UI completion, cleanup failure and executor rejection. Seven permission-correlation cases cover stale other-device/same-device replies, missing creator identity, exact one-shot acceptance without system fill-in extras, cancellation and selection/nonce mismatch. Six helper tests cover pinned-byte tamper rejection, invalid ELF identity/alignment, source-change publication refusal and real timeout cleanup of a child process that ignores TERM; unchanged upstream USB internals are not retested.

Source investigation and deferred provider/import requirements: [P0-S5 provider entry](../../../docs/03-platform/googlebook/p0-s5-usb-provider-entry.md). The Android permissions remain owned by this diagnostic; any external VirtualHere app needs its own actual provider grant and lifecycle qualification.
