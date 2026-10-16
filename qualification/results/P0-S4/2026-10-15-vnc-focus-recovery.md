# P0-S4 — VNC held-key focus release and hidden-session retirement

**2026-10-15, America/Chicago:** the unchanged **0.1.3/code4** diagnostic passes a bounded framework-injected F8 focus-release check on the HP Googlebook. The real host receives F8 up after the system notification shade takes window focus, before the ten-second injected press ends. A later normal Home action hides the diagnostic and its native worker joins/frees with a closed `not-visible` record. **The held-mouse-button case was not exercised:** the chosen injection produced no host button-down. Initialization cancellation also remains untested.

The [result manifest](2026-10-15-vnc-focus-recovery.json) binds 36 evidence files. It preserves all eleven final records and confirms the prior nine are byte-identical. This extends the [previous startup/input result](2026-10-15-vnc-startup-timing.md); it does not establish physical keyboard/mouse coverage, complete recovery behavior, CAP-VNC support or product readiness.

## Exact unchanged build and finite setup

APK SHA256 `97799b6a6495f3c6506df705d2cb5249f1a013d0bfe646b37dd2997a8b8d55f0`, build reference `478b0a46-2d02-4eeb-940b-e25e602f8aa1`, is pulled back from the device and remains byte-identical to the accepted timing APK. Existing compilation, ten JVM tests, zero Android Lint and package/native evidence carry forward under D12; no source or APK rebuild is required.

Device: HP Googlebook14, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental16560795. Host: Ubuntu26.04 with the same extracted TigerVNC1.15.0+dfsg-2build1 and reviewed Qt6.9.2 canvas03. A fresh fixture08 uses a private virtual X display, loopback-only RFB through an owned USB ADB reverse, fresh exact-leaf TLS trust, RFB3.8/VeNCrypt19/X509Plain262 and normal PAM `other`. It is not capture of the workstation's Wayland desktop. Server logs retain Tight and Tight-JPEG encoding observations; no broad encoding matrix is inferred. Host600s, canvas570s and client180s limits remain unchanged. Credentials are entered by the user on the Googlebook.

## Attempts and observed ordering

The first new attempt, `39a6112a-3124-49f0-b89f-97de7c6631bb`, records1,025 frame callbacks,860 unique draws and907 input writes. The user reports “Canvas appears; connection left open,” but the record is already closed with reason `operator` when inspected. It has no F8 activity and cannot satisfy the intended held-key experiment. The report does not attribute the close to a particular user gesture.

The parent arms a finite watcher before the next connection. Attempt `4e3d23ab-2edd-4907-94c5-197703599707` reaches the actual server pixel-format stage, and a current window check confirms the VNC Activity owns focus. The user reports “Reconnected and left open.” The parent sends the public shell command `input keyevent --duration 10000 KEYCODE_F8`, requires a real non-repeating host F8 down, then opens the system notification shade. The host records:

| Event | Host canvas sequence | Evidence boundary |
| --- | ---: | --- |
| F8 down | 737 | Non-repeating marker; remote canvas focused and active |
| F8 up | 738 | Observed while the injected key command is still running, following the shade focus transition |
| Injection completes | Separate orchestration record | Approximately9.86s after the parent already observed host key-up; the shade remains open until completion |

Only ordering is used. Canvas, parent monotonic and Android elapsed clocks are kept separate; no cross-clock latency is calculated. The private window dump confirms `NotificationShade` owns focus. The client records one down queued, no owned up queued, and two subsequently unowned up observations. The per-key diagnostic is not a transport acknowledgement; the independent host event supplies the actual delivery evidence.

After the shade closes, a bounded `input mouse motionevent DOWN` attempt targets the inspected framebuffer center. No host button-down is observed within the three-second observation bound, so no focus transition is triggered for that mouse case; a matching UP is sent for local cleanup. The current ownership policy requires a real button-state bit in addition to a press action. The shell stimulus does not establish that prerequisite. **This is a not-exercised mouse test, not a mouse-release pass and not an established app defect.** A valid physical or framework stimulus remains required.

The subsequent normal Home action triggers the diagnostic's visible-only stop path. The final second-attempt record closes after113,916ms with reason `not-visible`, native cancellation code1, joined/freed ownership,525 frame callbacks,503 unique draws and44 completed/written inputs. STOP→JOINED is212ms on the Android clock. Its first draw is20,643ms after admission, including20,572ms to initialized connection. These are setup/cleanup observations, not input-to-photon performance. The native cancellation label is retained as recorded rather than presented as a fresh authentication failure.

## Cleanup and scoped next work

All eleven device journals are closed. The original nine remain unchanged before and after force-stop, and the exact installed APK is verified. Only this fixture's device profile and reverse route are removed. Server and canvas exit0; the listener, X socket and lock are absent. Host completion reports no held marker keys or buttons. No test, credential prompt or question remains active.

The accepted evidence now covers this framework held-key/window-focus boundary and normal hidden-session retirement. Carry it forward without repeating engine, trust or unchanged framebuffer checks. One bounded follow-up should address a valid held-primary-button stimulus and cancellation after native start but before CONNECTED. If connection initialization wins that race, classify the result as connected cancellation. Process loss/recovery, physical input types, native IME, broader hosts, startup distribution, responsiveness and P1-S4 source/distribution adoption remain separate open work. The VNC route is viable within the tested fixture; full P0-S4 and product admission remain incomplete.
