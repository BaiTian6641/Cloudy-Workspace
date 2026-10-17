# Terminal committed-input repair — actual Googlebook WebView

**Scope:** C04/C05, M3/P2-S2 construction, not full Q-C02 or R1. **Base:** `3f9ce70` plus the reviewed renderer repair and its Android test source. The inherited Console terminal draft remains a separate integration increment.

## Cause and decision

Cloudy's page forced `screenReaderMode: true` for every terminal. In the exact pinned xterm 6.1.0-beta.315 archive, `CoreBrowserTerminal._inputEvent` explicitly excludes `insertText` while that option is enabled. The installed WebView inserted committed text into the textarea but no `onData` reached native admission. This is an adapter configuration defect, not evidence that physical keys were the only possible test route.

Use actual Android accessibility state: enable xterm's mode for an enabled spoken-feedback service or touch exploration, and listen for service-state changes. No Android accessibility service is disabled. Focus uses xterm's public `focus()` API; no native character mapper, textarea-diff forwarding or upstream emulator patch is introduced.

Sources: [pinned input handler](https://github.com/xtermjs/xterm.js/blob/991857a1b1cacf06f9531ba74a6e927a7a6398bb/src/browser/CoreBrowserTerminal.ts), [pinned option contract](https://github.com/xtermjs/xterm.js/blob/991857a1b1cacf06f9531ba74a6e927a7a6398bb/typings/xterm.d.ts), [Android AccessibilityManager](https://developer.android.com/reference/android/view/accessibility/AccessibilityManager). Register entries: R374, G258, API71. Query/touch-exploration APIs are available from API14; the service-change listener is API33, below product min35.

## Reproduction and executed checks

The test-only Activity hosts the actual packaged xterm/fit, authored page, WebViewAssetLoader and native binary listener. Public `InputConnection` methods execute on the connection's declared IME handler, not the UI thread. The test host and runner are not in either product APK.

Before the repair, `plainCommittedTextReachesNativeAdmission` accepted `commitText("Cloudy", 1)` but received zero native input frames. The Unicode test also received zero bytes. A temporary passive test observer confirmed the WebView's `insertText` event contained the committed text; that diagnostic wrapper was removed after locating the guard. Earlier test-harness failures (wrong thread, asynchronous connection readiness) were fixture errors, not product results.

After the repair, this actual target-device invocation passes **3/3**:

```bash
adb -s <owned-googlebook> install -r \
  shared/android/terminal/build/outputs/apk/androidTest/debug/terminal-debug-androidTest.apk
adb -s <owned-googlebook> shell am instrument -w -r \
  -e class io.github.baitian6641.cloudy.terminal.TerminalRendererInputTest \
  io.github.baitian6641.cloudy.terminal.test/androidx.test.runner.AndroidJUnitRunner
```

| Actual case | Observed invariant |
| --- | --- |
| Plain committed input | Exact UTF-8 `Cloudy`, once |
| Unicode/LF after canceled preedit | Exact CJK, combining accent, emoji and multiline bytes; preedit sends nothing; closed attachment sends nothing and does not replay |
| Enter and Ctrl+C | Exactly `0d 03`, not copy or duplicate terminal input |

The accepted composition case retains one public input connection through its lifetime. A separate diagnostic that recreated that connection between cancel and commit lost the subsequent input; connection restart during composition therefore remains unqualified, not silently passed by this test. A CR supplied inside `commitText` is normalized to LF by the HTML textarea; this is recorded, not claimed as raw CR preservation. Text commit is tested with LF; terminal Enter is separately tested as the CR control key. Actual IME-specific newline/paste policies remain open.

## Exact environment

- HP Googlebook 14, Android 17/API37, build `HP/quartz/quartz:17/CL3B.260622.276.R2/16560795:user/release-keys`.
- Installed provider `com.google.android.webview` **149.0.7827.5**, version code **782700504**.
- Test APK SHA-256 **`63051a1d829d31988ee056d767b4f9a66bde608987162ff972cb93cea3503dc4`**.
- Pinned xterm **6.1.0-beta.315**, fit **0.12.0-beta.311**, WebKit **1.17.1**; upstream bytes unchanged.
- Strict offline Android test assembly and `:terminal:lintDebug` passed; product JS syntax check passed. The 15 existing renderer JVM tests passed in the integration build. No synthetic server or SSH authentication was used by these three renderer tests.

## Remaining gates

This proves the Android committed-input and renderer/control-byte boundary. It does **not** qualify a physical keyboard/trackpad, an installed system IME's candidate/preedit behavior, TalkBack speech/input, clipboard/context menus, sustained output, SSH, remote resize, tabs, files or separate X11 product windows. In particular, the upstream screen-reader/input interaction still requires a real spoken-service qualification; the conditional mode selection is not an accessibility pass. CAP-TERMINAL remains experimental and M3 remains in progress.
