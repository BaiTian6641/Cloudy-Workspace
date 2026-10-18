# Native terminal viewport repair — 2026-10-17

**Status:** verified rendering/layout slice; product terminal input/SSH workflow still failing at this snapshot. **Requirements:** C04/C05, M3/P2-S2. **Source base:** `31ac4a4` plus the preserved Console integration and the renderer change committed with this record. No full Q-C02, physical input, system IME, TalkBack or R1 claim.

## Reproduced cause

The product WebView measured **2073×1104 native pixels** and reported `innerHeight=679`, but its actual HTML body and `.xterm` were **0 CSS pixels high**. The terminal container had only its eight pixels of padding; the fit result was one row. A protected window PixelCopy showed native controls above an empty dark terminal. JavaScript execution and input callbacks did not establish visible output.

The exact selected implementation explains the mismatch:

1. Compose1.12.1 `AndroidViewHolder` adds the WebView as a native child. With no explicit child layout parameters, the default is `WRAP_CONTENT`.
2. Chromium149.0.7827.5 `AwLayoutSizer.updateLayoutSettings()` sets `forceZeroLayoutHeight` when the **actual WebView layout parameter** is `WRAP_CONTENT`, even if its native measurement is exact and nonzero.
3. Blink zeroes the layout viewport/initial containing-block height. The authored `html, body { height:100%; overflow:hidden }` then collapses and clips the content. The visual viewport used by `innerHeight` remains nonzero.

`TerminalWebViewBridge` now assigns explicit `ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)` at WebView creation, before embedding. No CSS workaround, forced software layer, dependency change, hidden API, security relaxation or secondary native character path was introduced.

Sources: [official Android WebView sizing guidance](https://developer.android.com/develop/ui/views/layout/webapps/best-practices), [exact149 layout sizer](https://github.com/chromium/chromium/blob/149.0.7827.5/android_webview/java/src/org/chromium/android_webview/AwLayoutSizer.java), [exact149 native delegate](https://github.com/chromium/chromium/blob/149.0.7827.5/android_webview/java/src/org/chromium/android_webview/AwContents.java), [selected Compose source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1-sources.jar). Registers: G259/G260/R375/API72. This applies the native viewport guidance, not the guide's mobile-product recommendations; Rule0 is unchanged.

## Actual changed-boundary result

Environment: HP Googlebook14, Android17/API37, build `HP/quartz/quartz:17/CL3B.260622.276.R2/16560795:user/release-keys`; WebView `com.google.android.webview`149.0.7827.5/code782700504. Product minimum35/target37; Compose1.12.1, WebKit1.17.1, xterm6.1.0-beta.315/fit0.12.0-beta.311 unchanged.

Run **`312454a5-c937-41e7-a73d-0a08a9260ccb`**, no DevTools attached, used the actual Console Activity, real password SSH and the maintained MINA2.20.0/util-linux2.41.3 kernel-PTY fixture. App APK SHA-256 **`921b4e36ca403ad8d3992e049e4d141c573645466a14ff1d238366bc4febe425`**; instrumentation APK **`7027cc53e5db8506926a87aa4ac496cbbff29c46e26d4b1b430428ae7db10ce0`**. Private evidence directory: `qualification/results/private/console-terminal-resume/312454a5-c937-41e7-a73d-0a08a9260ccb/`.

| Observation | Before | After |
| --- | --- | --- |
| Native WebView height | 1104 px | 1104 px |
| HTML/body layout height | 0 CSS px | 679.3846 CSS px |
| `.xterm` layout height | 0 CSS px | 642.3846 CSS px |
| Pixel surface | Blank terminal below native controls | Local status strip, real remote shell prompts and cursor visible |

The parent inspected the actual `terminal-surface.png`, not merely its capture status. `first-paint`/`first-contentful-paint` were also observed, but those metrics alone are not pixel proof. `FLAG_SECURE` remained enabled. Native profile/trust baseline restoration remained exact.

**Still failed:** the external command-effect witness was absent, so `ConsoleTerminalActivityTest` failed. The image shows two shell prompts; this is not a command-execution or complete key/IME pass. The next investigation measures the actual committed-text/native-key event ordering. The prior missing-Enter attribution is not automatically applied to this changed build.

## Executed checks and preserved investigations

- Strict offline `:terminal:testDebugUnitTest :terminal:lintDebug` passed after the viewport change (15 renderer JVM tests).
- Strict offline Console app/test assembly, Console Lint and platform JVM tasks passed at their recorded builds. Lint warns that Java-only `:domain`/`:ssh` are treated as external dependencies and are not Android-Lint analyzed.
- The real product runtime above failed its end-to-end witness while proving the corrected layout/pixels. No failure was suppressed or acceptance condition weakened.
- A preceding DevTools observation (`cdp-c88997b4…`, operation `09aa510e…`) unexpectedly enabled command execution/retirement while still initially capturing the blank viewport. Because the observer changed behavior, that run is **not** accepted as an unobserved-product input pass.
- Explicit Compose key forwarding, a native sibling-host experiment and a virtual-test-clock hypothesis did not resolve the earlier failure. Disproved code experiments were removed; exact failed artifacts remain private.
- The independently reviewed DeepSeek Flash source investigation confirmed the layout cause. Its `onUnhandledKeyEvent` finding was corrected to mean a renderer-returned unhandled key, not proof of a Java IME fallback. Source evidence did not prove that viewport height alone caused the key symptom.

## Remaining acceptance

Resolve the actual committed-input/control sequence and independent remote-effect witness without DevTools; then retain a regression, verify final output and real owner retirement, and inspect the native surface again. Complete physical keyboard/pointer, installed IME, clipboard/menus, resize/backpressure, files/layouts and full Q-C02 separately. This rendering repair neither closes M3 nor promotes CAP-TERMINAL/R1.
