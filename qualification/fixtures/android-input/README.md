# Finite Android mouse input fixture

P0-S4 framework input driver, separate from the VNC diagnostic and product apps. This minimal Java `Instrumentation` runs in its own helper package, has no Activity, runtime dependencies, network permission, credentials, server launch or VNC launch. The debug variant is debuggable through AGP's normal debug configuration. Instrumentation targets **its own package**, so starting it does not instrument/restart VNC.

The scoped question is whether Cloudy's VNC adapter releases a genuinely host-observed held primary button when window focus is lost. A successful framework injection alone does not answer that question, establish host delivery, or qualify physical mouse input.

## API and test-stack assessment

The reviewed `testing-setup` skill was assessed with `docs/07-reference/development-skills.md` and the assigned scope: no DI, fakes, app refactor, Hilt, AndroidX test runner, UI Automator library or screenshot framework is needed for this finite public framework driver. This fixture uses only `Instrumentation`, `UiAutomation`, accessibility window inspection and `MotionEvent`. It does not replace existing app tests.

Official API references checked 2026-10-15:

- [`Instrumentation.getUiAutomation(int)`](https://developer.android.com/reference/android/app/Instrumentation#getUiAutomation(int)): available API24; supports cross-app operations; a null connection is handled as unavailable.
- [`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES): available API24; preserves running accessibility services.
- [`UiAutomation.injectInputEvent(InputEvent, boolean)`](https://developer.android.com/reference/android/app/UiAutomation#injectInputEvent(android.view.InputEvent,boolean)): available API18; synchronous injection reports framework acceptance, and the caller owns event recycling.
- [`MotionEvent.obtain(...)`](https://developer.android.com/reference/android/view/MotionEvent) and [`InputDevice.SOURCE_MOUSE`](https://developer.android.com/reference/android/view/InputDevice#SOURCE_MOUSE): a single pointer uses `TOOL_TYPE_MOUSE`, `SOURCE_MOUSE`, `ACTION_DOWN` with `BUTTON_PRIMARY`, then matching `ACTION_UP` with button state zero. Pointer/down time and coordinates remain matched.
- [`AccessibilityWindowInfo`](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo) and [`getRootInActiveWindow()`](https://developer.android.com/reference/android/app/UiAutomation#getRootInActiveWindow()): the active root package, active/focused window, default display and exact inspected root bounds gate the initial press. If accessibility inspection is unavailable the operation fails without a press. MinSDK35 covers these APIs.

No hidden API or privileged product permission is introduced. The shell-launched instrumentation provides the test capability; it is not a product input path. There remains an unavoidable race between the last window snapshot and input dispatch. Perform this on the isolated qualification display with unrelated interactions stopped. Cleanup intentionally sends the matching UP even if focus has moved; it never sends a second press to the new app.

## Build in an isolated directory

Use the existing pinned SDK (compile37/build tools36), Gradle9.6.0, AGP9.4.0, JDK21 and Java17 source target. This is an independent Gradle build; do not add it to the product/native builds. Example from the repository root:

```bash
# mktemp creates an exclusive new directory; preserve it as this candidate's evidence root.
fixture_run=$(mktemp -d "$PWD/.cache/android-input-candidate-XXXXXXXX")
mkdir "$fixture_run/project" "$fixture_run/gradle-home" "$fixture_run/android-user"
cp -R qualification/fixtures/android-input/. "$fixture_run/project/"
ANDROID_USER_HOME="$fixture_run/android-user" \
ANDROID_HOME="$PWD/.cache/android-sdk" JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  .cache/toolchains/gradle-9.6.0/bin/gradle \
  --gradle-user-home "$fixture_run/gradle-home" \
  -p "$fixture_run/project" --no-daemon :app:assembleDebug :app:lintDebug \
  :app:dependencies --configuration debugRuntimeClasspath > "$fixture_run/build-and-closure.log" 2>&1
```

APK: `$fixture_run/project/app/build/outputs/apk/debug/app-debug.apk`. The Java-only module explicitly disables AGP built-in Kotlin and uses `-Xlint:all -Werror`. The actual debug runtime closure must report `No dependencies`. Never copy into a historical candidate directory or replace a cited APK/log/source manifest. Build and Lint are source/toolchain checks, not device or host evidence. The integration leader controls installation, ADB, host fixtures, evidence, acceptance and cleanup. Version0.1.2/code3 adds bounded root/window readiness and primary-failure status; historical candidates remain frozen.

## Inspect, then one finite hold

Missing/unknown `mode` fails before connecting automation and sends no input. `mode=inspect` calls the public window APIs, validates an actual active/focused default-display VNC root, and returns only package/window identity, root bounds and actual Android clocks. It sends no input, collects no UI tree/credentials and performs no network or app launch. After leader-controlled installation, enforce exclusive automation ownership. Finish the fresh framebuffer/layout inspection first. Identify any still-running **owned** Android CLI instrumentation (`com.android.cli.interact.instrumentation`); retire only that identified owned instance and verify it is absent, including its UiAutomation registration. If any automation owner is unknown, stop rather than killing unrelated automation. Then run inspect/hold without an interleaved Android CLI layout/interaction command, which can register a competing UiAutomation. A failure or absent root is not permission to terminate an unknown owner. The parent controls this device preflight; the helper never kills another package.

Inspection command:

```bash
adb -s "$DEVICE_SERIAL" shell am instrument -w -r -e mode inspect \
  dev.cloudy.qualification.input/.HeldMouseInstrumentation
```

Require final `result=inspection_only_no_input`; extract `windowId`, `left`, `top`, `right`, `bottom`, `inspectedUptimeMs` and `inspectedElapsedRealtimeMs` from **that successful invocation's actual result**. No input is emitted during this inspection. Pair it with a current inspected VNC framebuffer and app/version/session/host-target identity. Choose screen coordinates **inside that visible framebuffer**; root containment alone does not prove framebuffer containment. Do not reuse historical values, a failed inspection, or another boot's results.

`inspectedUptimeMs` is obtained from Android [`SystemClock.uptimeMillis()`](https://developer.android.com/reference/android/os/SystemClock#uptimeMillis()), which excludes deep sleep. `inspectedElapsedRealtimeMs` comes from [`elapsedRealtime()`](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime()), which includes deep sleep. Do not substitute `/proc/uptime`, host time or guessed values. Hold mode freshly revalidates the active/focused default-display VNC package and exact root bounds, then requires both ages to be from0 through30,000ms. The elapsed clock prevents suspend from silently keeping an inspection admissible. Window IDs are returned as diagnostic observations only: Android accessibility can remove and recreate an interaction connection as accessibility enablement changes across separate automation registrations, assigning another ID to the same app window. Public `getId()` promises uniqueness, not cross-registration persistence. ID inequality therefore does not bypass or replace the package/focus/display/bounds/point/clock guards. Hold emits its actual current diagnostic ID in `event=hold_admission_snapshot` before checking freshness; this event is not an admitted press or host observation. Perform the fresh framebuffer inspection and mode calls in the same boot with no intervening app/window/session change; the clocks are freshness observations, not an authenticated boot/session token.

Coordinates are integer screen pixels from0 through16,383; right/bottom may be16,384. Hold duration is2,000–30,000ms. No defaults silently choose a mode or target. Hold invocation using values from the actual successful inspect result:

```bash
adb -s "$DEVICE_SERIAL" shell am instrument -w -r -e mode hold \
  -e x "$INSPECTED_FRAMEBUFFER_X" -e y "$INSPECTED_FRAMEBUFFER_Y" \
  -e left "$ROOT_LEFT" -e top "$ROOT_TOP" -e right "$ROOT_RIGHT" -e bottom "$ROOT_BOTTOM" \
  -e inspectedUptimeMs "$INSPECTED_UPTIME_MS" \
  -e inspectedElapsedRealtimeMs "$INSPECTED_ELAPSED_REALTIME_MS" -e holdMs 15000 \
  dev.cloudy.qualification.input/.HeldMouseInstrumentation
```

Both modes retry only a null root or missing root window, with50ms polling and a2second elapsedRealtime readiness budget after automation/service setup. A real non-VNC root, focus/display mismatch or invalid geometry fails immediately without input. Each retry queries the current public API again; no arbitrary app is accepted. Platform queries and sleeps themselves are synchronous and can overrun the polling budget under an OS stall/suspend. Hold checks both inspection ages after readiness; waiting cannot renew old inspection timestamps.

A primary RuntimeException produces bounded `event=primary_failure` status (error class, reason capped at256characters, actual uptime/elapsed clocks) before framework `finish`. This preserves the primary observation when framework disconnect/finish subsequently fails; a real finish failure is not caught, suppressed or labeled clean completion. No hidden connection-state/disconnect API is used.

Run hold instrumentation asynchronously under leader control so it does not prevent independent host observation/notification-shade operation. Save the actual inspect result, full structured hold output and correlated host event journal privately. `event=down` and `event=up` statuses include `injectionAccepted`, device `uptimeMs` and `elapsedMs`. MotionEvent timestamps and down/up elapsed remain on Android's required uptime basis; down elapsed is zero, and UP's timestamp is sampled immediately before final injection. Final `framework_down_and_up_accepted` means only both calls returned true; rejected injections/exceptions return failure. Every attempted DOWN enters cleanup that attempts UP in `finally`, and both events are recycled. No root/focus re-gating blocks cleanup after focus changes.

The requested hold uses `SystemClock.sleep`, measured in uptime. Suspend can extend it in wall time; synchronous injection/OS stalls have no Java-enforced retirement bound. Process death or OS failure can prevent cleanup. Do not kill the helper during the hold or claim that the bounded requested duration guarantees cleanup under an OS stall. No power/system changes are part of this driver.

The leader must observe an actual host button DOWN before opening the notification shade. Then prove actual host UP **before** the fixture's final UP injection; otherwise the result does not establish focus-loss release. If host DOWN is absent, stop that test interpretation: do not treat a final helper UP as a repair or pass. Cross-device timestamps need a correlated observation/ordering method; device uptime cannot be directly compared to a host wall clock. Retain exact app/helper APK identities, operation, injection timestamps and host sequence. The helper does not open the shade, run a network session, capture a UI tree, or report a qualification verdict.

At completion inspect the real host button state, retire the test, stop/uninstall the helper as appropriate and preserve existing app journals. No device action was performed merely by adding/building this fixture.
