# Console desktop home and responsive state — 2026-10-17

**Status:** verified bounded emulator UX/state slices, not full P1-S2, C01/C05 or Googlebook acceptance. **Source:** `31ac4a4` plus the reviewed integration committed with this record. The parent reviewed the exact DeepSeek Flash proposals and inspected actual native captures; source-only worker output was not visual acceptance.

## Implemented surface

`WorkspaceHomeScreen.kt` uses the canonical Cloudy theme/metrics for a compact toolbar, host navigation, search/list and selected-host detail. Wide windows use side navigation and a detail pane; narrow or large-text windows reflow into labeled, scrollable sections rather than phone bottom navigation. Connection identity, trust and credential metadata remain explicit. Unsupported routes are labeled, not simulated.

Parent integration wired Favorites to the real store, removed unused/default no-op actions, retained Add host with a populated list, and corrected the timestamp label to **Last connection attempt**. Window-name text, selection and refusal state now live above conditional layouts, with the existing window-ID-bound `WindowDraftCodec`; only explicit Apply changes committed window identity.

The real Home → Connect → SSH command/output/retirement route is recorded separately in [the terminal workflow](../P2-S2/2026-10-17-console-terminal-emulator.md). These home controls do not imply tabs, writable SFTP, transfers, X11 or complete authentication support.

## Environment and artifacts

- Owned API37/Android17 x86_64 emulator; Android Emulator37.2.12/build16428233, Google APIs37.0 revision6; 2880×1800 at240dpi.
- Fingerprint: `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
- Console0.1.18/code19; final app APK SHA-256 **`124075190d772f5522ea33b27904f21f766392307a5392b8f730985db075eeca`**.
- Window state/display test APK SHA-256 **`a7bcf9e5258333e99af60e47d93a0d6c26fdcfa1567277e7a85c91a499b0f8fa`**. This is later than the terminal workflow's separately frozen test APK.
- The emulator uses controlled legacy freeform settings and does not advertise Googlebook PC/caption parity. No emulator-only system setting enters product code; minSdk35 was not lowered for the official API34 desktop image.

The shared platform now depends on the renderer, so the parent also resolved Desktop's existing
dependency configurations and updated its generated lock entries without changing selected versions.
Desktop debug/app-test assembly and Lint passed (275 tasks, 116 executed) using the already reviewed,
explicitly hash-bound VNC native reuse declaration; no verification gate was excluded. Packaging
reported unstripped native libraries, and Java-only domain/SSH modules remain outside Android Lint.
No Desktop installation, VNC runtime or new native-engine qualification was performed.

## Executed native workflows

| Check | Actual result | Evidence / boundary |
| --- | --- | --- |
| Profile create and metadata | Created an owned emulator-only profile through the real editor; tags and endpoint persisted | `.cache/console-emulator-2026-10-17/manual-ui-results.json`; no connection to that profile was claimed |
| Favorites | Real toggle persisted; Favorites contains the profile and removal action becomes available | Same manual record and native captures |
| Search | No-match state observed; Clear search restored the row | Same manual record; not a mock list |
| Selected host | Endpoint, trust/credential state and Connect remained identifiable | Wide light/dark selected-host captures |
| Theme/density transitions | `ConsoleWindowDisplayTest`: one method, six light/dark × density configurations; window identity and unsaved text/selection preserved | `files/display-review/f1e04bd0-1327-4db2-928c-5ed07437a8f4` in the app; copied/validated under `.cache/console-emulator-2026-10-17/display-f1e04bd0/` |
| Window lifetime | `ConsoleWindowActivityTest`: three methods pass for committed identity plus draft/selection recreation, invalid-name refusal and host-key-screen return | Public accessibility action activates Apply; assertions retain window/task/generation/title ownership. No private production method is invoked |
| Native width/font reflow | One owned window changed 2400px → 900px → 2400px wide, then font scale1.0 → 2.0. The unsaved native draft survived and remained reachable; committed heading stayed unchanged | `.cache/console-emulator-2026-10-17/reflow-dd1b080c-4275-4558-8f54-6590f6fb99ac/`; actual ADB/native input and OS task resizing, not a composable preview |
| Narrow keyboard reachability | Fourteen injected Tab actions brought Connect into view at200% text | `keyboard-reachability.json`; not physical keyboard qualification |

The parent inspected all six theme/density captures, the full compact-dark capture, narrow reflow and200% text pixels. Controls wrap and the local draft stays visible when scrolled into view. App-only captures exclude the separate system IME surface; blank inset space is not evidence about keyboard rendering. Ordinary profile/search fields retain native editing rather than a terminal key mapper.

The preference test restored exact **DARK / COMFORTABLE** state. The reflow run restored system font scale **1.0**, closed only its own window through the native Close control and removed its owned XML dump. Synthetic profile data remains only in the owned development emulator, not product defaults or repository fixtures.

## Failures and limits retained

Earlier injected pointer/Tab→Enter attempts did not activate Apply in the state test; their failures remain preserved. The state contract now uses the public semantic click while retaining all committed/draft/selection/identity assertions. A separate native pointer interaction renamed the window; this does not qualify all physical keyboard, focus or IME routes.

This run observed an unnecessary discard prompt after saving an unchanged profile. The later [clean-close repair](2026-10-17-profile-clean-close.md) passes its own source-bound Android and native-pointer checks while retaining dirty/stale/uncertain-write guards. The original observation is preserved, not relabeled.

The full opt-in Googlebook geometry matrix, actual hardware keyboard/trackpad, system IME candidate/preedit, TalkBack, long-label/failure breadth and final product visual acceptance remain open. The earlier physical-device results retain their own source boundaries. No stage, capability or release status is promoted by this record.

## Reproduce the scoped checks

```bash
./gradlew :console:assembleDebug :console:assembleDebugAndroidTest :console:lintDebug \
  :platform:testDebugUnitTest :terminal:testDebugUnitTest :terminal:lintDebug \
  --offline --dependency-verification=strict --no-daemon --console=plain
adb -s emulator-5580 install -r apps/console/build/outputs/apk/debug/console-debug.apk
adb -s emulator-5580 install -r apps/console/build/outputs/apk/androidTest/debug/console-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -r \
  -e class io.github.baitian6641.cloudy.console.ConsoleWindowActivityTest,io.github.baitian6641.cloudy.console.ConsoleWindowDisplayTest \
  io.github.baitian6641.cloudy.console.test/androidx.test.runner.AndroidJUnitRunner
```

Use the documented project-local SDK/JDK/Gradle environment and an explicitly owned target. Instrumentation's shell exit status alone is insufficient; inspect the actual JUnit result and restoration receipts. The commands above combine the two classes for convenience; the recorded runs executed them separately.
