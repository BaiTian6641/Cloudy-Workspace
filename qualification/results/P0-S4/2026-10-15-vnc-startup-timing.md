# P0-S4 — VNC startup timing and controlled keyboard follow-up

**2026-10-15, America/Chicago:** installed VNC **0.1.3/code4** completes a protected 1280×720 session on the HP Googlebook. The user confirms **“ok, worked.”** The client records frames, two F8 down/up pairs and explicit Disconnect; the isolated host independently records pointer/button/wheel input and both F8 pairs. First draw occurs **20,639 ms after Connect**, with **20,578 ms to completed connection initialization** and **61 ms from initialization to first draw**. The earlier approximately one-minute blank-start report is not fully explained by this sample. The measured initialization delay remains a UX issue.

The [manifest](2026-10-15-vnc-startup-timing.json) binds 38 private evidence files, including all nine final device records, exact APKs, host results and retained failures. This is bounded P0 integration evidence, not CAP-VNC support, product readiness or a latency benchmark.

## Exact build and setup

The accepted [timing build](2026-10-15-vnc-timing-build.md) is unchanged: build reference `478b0a46-2d02-4eeb-940b-e25e602f8aa1`, APK 10,888,183 bytes, SHA256 `97799b6a6495f3c6506df705d2cb5249f1a013d0bfe646b37dd2997a8b8d55f0`. Its existing Android compilation, 10 JVM tests, zero Lint findings and exact native/package audit carry forward under D12. Installation preserves all seven earlier closed records; the pulled installed APK is byte-identical. Android CLI subsequently launches that same accepted APK. No client/native source or authentication policy changes in this follow-up.

Device: HP Googlebook14, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental16560795, patch2026-09-01. Host: Ubuntu26.04 with extracted TigerVNC1.15.0+dfsg-2build1 and the reviewed Qt6.9.2 canvas03. The server uses a private Xcb display, Xauthority, loopback-only RFB through one scoped USB ADB reverse, VeNCrypt19/X509Plain262, an approved ephemeral leaf certificate and normal PAM service `other`. Clipboard exchange remains disabled. Password entry occurs only in the Googlebook field; no password value is read or captured.

## Retained setup and authentication failures

Host fixtures04/05 fail to create the canvas journal before the 30-second startup bound. The supervised canvas is terminated, server exits0, and listeners/X socket/lock are removed. A bounded own-process trace observes KDE platform-theme initialization and a GUI child, without establishing its exact cause. Fixture06 selects `QT_QPA_PLATFORMTHEME=generic` only for this process and reaches readiness in205ms. This uses Qt's documented [platform-theme environment selection](https://doc.qt.io/qt-6/qguiapplication.html#QGuiApplication); global desktop settings and the canvas binary are unchanged. It is a fixture correction, not an explanation of the historical client delay.

Attempt `3e424a1f-d11b-4862-aa28-7cbdba43a4bb` then fails after12,243ms with native security error10, zero initialized connections and zero frames. The user reports “ok, Native-error-10.” Its worker joins/frees and the record closes. The server records authentication failure; host audit records show AppArmor access denials for `unix_chkpwd` during this sandboxed-host attempt. These observations do not establish a wrong password. Fixture06 closes cleanly.

The parent runs fresh fixture07 in the normal host command environment under existing host-test authorization. PAM configuration, password policy, certificate checks and protocol remain unchanged. That attempt succeeds. The failed attempt remains preserved; no authentication fallback or security-policy weakening is introduced.

## Successful attempt and measured boundary

Attempt `6b34c12f-1022-4530-91a1-fbe97c638903` uses one Android `SystemClock.elapsedRealtime` timeline:

| Interval | Observed duration |
| --- | ---: |
| Connect accepted → worker | 0 ms |
| Connect accepted → native start invocation | 13 ms |
| Native start → initialized connection | 20,565 ms |
| Connect accepted → initialized connection | 20,578 ms |
| Initialized connection → first frame callback | 9 ms |
| First frame → conversion | 16 ms |
| Conversion → first Canvas draw | 36 ms |
| Connect accepted → first Canvas draw | **20,639 ms** |
| Explicit stop → joined native worker | 135 ms |

Initialization includes TLS, server authentication and protocol setup; it is not authentication-only timing. Host logs independently reach the normal pixel-format stage after approximately21seconds, at one-second log resolution. No subtraction across host and Android clocks is used. Canvas draw records an actual drawing call, not photon delivery, and none of these values measures input-to-photon latency.

The session closes after59,170ms with operator cancellation, native code1, joined/freed ownership and a terminal journal. It records1,787 native/Java frame callbacks,1,701 converted frames,1,469 unique draws and1,618 admitted/completed/written input events. The F8 frame-view counters record two downs and two ups, all queued, with no focus/window/connection/Meta rejection. Queue/write counts still do not provide per-key remote acknowledgements.

The controlled host separately records1,292 motion events,24 button events,one wheel event and four keyboard events: two non-repeating F8 down/up pairs (`Qt::Key_F8`,16777271), with focus and active-window true. Completion reports no held marker keys or buttons. The user's successful observation, client counters and host events are distinct evidence. Physical input-device subtype was not specified; wider layouts, IME and relative3D behavior are not inferred.

## Preservation, cleanup and next work

All nine device records are closed. All seven earlier records and the new failed authentication attempt remain byte-identical after the final force-stop. The temporary device profile and exact owned ADB reverse are removed. Fixture07 canvas/server exit0; its RFB listener and X socket/lock are gone. No question, prompt or live VNC fixture remains.

The controlled F8 gap from the [first session](2026-10-15-vnc-first-session.md) is now resolved for this fixture. The current first-draw delay is localized mainly to initialization; further work should examine the actual host authentication path and clear waiting-state UX before changing framebuffer/rendering code. The earlier one-minute report, wider startup distribution, input-device coverage, direct-LAN permission behavior, lifecycle/recovery, product credential storage, broader server compatibility and distribution closure remain open. Reuse unchanged engine and trust evidence rather than repeating it without a concrete trigger.
