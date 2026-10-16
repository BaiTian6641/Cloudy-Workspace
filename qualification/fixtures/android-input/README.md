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
mkdir -p .cache/android-input-fixture-01/project .cache/android-input-fixture-01/gradle-home .cache/android-input-fixture-01/android-user
cp -R qualification/fixtures/android-input/. .cache/android-input-fixture-01/project/
ANDROID_USER_HOME="$PWD/.cache/android-input-fixture-01/android-user" \
ANDROID_HOME="$PWD/.cache/android-sdk" JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  .cache/toolchains/gradle-9.6.0/bin/gradle \
  --gradle-user-home "$PWD/.cache/android-input-fixture-01/gradle-home" \
  -p .cache/android-input-fixture-01/project --no-daemon :app:assembleDebug :app:lintDebug
```

APK: `.cache/android-input-fixture-01/project/app/build/outputs/apk/debug/app-debug.apk`. The Java-only module explicitly disables AGP built-in Kotlin and uses `-Xlint:all -Werror`; inspect `:app:dependencies --configuration debugRuntimeClasspath` to confirm the actual runtime closure. Build and Lint are source/toolchain checks, not device or host evidence. The integration leader controls installation, ADB, host fixtures, evidence, acceptance and cleanup.

## One finite operation

Before invoking, inspect the actual VNC app/version/build, current session, visible framebuffer and host target. Obtain fresh screen coordinates **inside the inspected framebuffer**, the active root's screen bounds and the device's uptime in milliseconds from the same boot. Never reuse historical coordinates or infer them from a prior screenshot. The fixture requires the expected root bounds to match and an inspection age from zero through30,000ms at admission. Coordinates are integer screen pixels from0 through16,383; right/bottom may be16,384. Hold duration is2,000–30,000ms. No defaults silently choose a target.

After leader-controlled installation, the invocation template is:

```bash
adb -s "$DEVICE_SERIAL" shell am instrument -w -r \
  -e x "$INSPECTED_FRAMEBUFFER_X" -e y "$INSPECTED_FRAMEBUFFER_Y" \
  -e left "$ROOT_LEFT" -e top "$ROOT_TOP" -e right "$ROOT_RIGHT" -e bottom "$ROOT_BOTTOM" \
  -e inspectedUptimeMs "$DEVICE_INSPECTION_UPTIME_MS" -e holdMs 15000 \
  dev.cloudy.qualification.input/.HeldMouseInstrumentation
```

Run instrumentation asynchronously under leader control so its hold does not prevent the independent observation/notification-shade operation. Save full structured instrumentation output and the correlated host event journal privately. `event=down` and `event=up` statuses include `injectionAccepted`, device `uptimeMs` and `elapsedMs`. Down's elapsed is zero. Up's timestamp is sampled immediately before its final framework injection. Final `framework_down_and_up_accepted` means only both calls returned true; rejected injections/exceptions return failure. Every attempted DOWN is followed by an UP attempt in `finally`, and both events are recycled. Process death or OS failure can prevent Java cleanup; do not kill the helper during the hold.

The leader must observe an actual host button DOWN before opening the notification shade. Then prove actual host UP **before** the fixture's final UP injection; otherwise the result does not establish focus-loss release. If host DOWN is absent, stop that test interpretation: do not treat a final helper UP as a repair or pass. Cross-device timestamps need a correlated observation/ordering method; device uptime cannot be directly compared to a host wall clock. Retain exact app/helper APK identities, operation, injection timestamps and host sequence. The helper does not open the shade, run a network session, capture a UI tree, or report a qualification verdict.

At completion inspect the real host button state, retire the test, stop/uninstall the helper as appropriate and preserve existing app journals. No device action was performed merely by adding/building this fixture.
