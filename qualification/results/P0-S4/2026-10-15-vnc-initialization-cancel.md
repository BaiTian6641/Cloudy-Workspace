# VNC cancellation during protocol initialization

**Date:** 2026-10-15, America/Chicago (2026-10-16 UTC). **Stage:** P0-S4. **Verdict:** bounded PASS for operator cancellation of the actual native worker while waiting for the genuine server's RFB greeting. This closes the named protocol-initialization cancellation case. TLS/authentication cancellation, held-button release, physical input, performance and CAP-VNC remain unqualified.

## Exact application and topology

The unchanged VNC diagnostic is **0.1.3/code4**, build reference `478b0a46-2d02-4eeb-940b-e25e602f8aa1`, APK SHA256 `97799b6a6495f3c6506df705d2cb5249f1a013d0bfe646b37dd2997a8b8d55f0`. Before and after pulls match. The target is HP Googlebook 14, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Earlier [startup/input](2026-10-15-vnc-startup-timing.md) and [focus/hidden recovery](2026-10-15-vnc-focus-recovery.md) evidence carries forward under D12; no engine or app code changed for this run.

Ubuntu26.04 runs the same TigerVNC1.15.0+dfsg-2build1 and Qt6.9.2 canvas03 on a fresh isolated virtual X display. This is not capture of the workstation's Wayland desktop. The server's normal X509Plain/exact-leaf/PAM configuration is retained, but this attempt never reaches security negotiation or authentication.

A reviewed, single-client loopback gate connects to the **real** TigerVNC server, reads its actual 12-byte `RFB 003.008` greeting and withholds it. It sends no data to either peer. The gate is capped at180 seconds overall and60 seconds holding the greeting. The genuine host has its existing600-second bound and is explicitly stopped after361.242 seconds; these are distinct fixture limits. The Android profile retains a180-second finite session bound. Explicit noncredential sentinels satisfy the diagnostic's form admission; no user's password is collected or transmitted during this attempt.

## Observed result

One new attempt, `76a17618-7ac6-48da-b894-5bf439ef1a97`, starts the native worker. The actual upstream greeting is held before the parent invokes the visible Disconnect control. The gate observes client EOF while still holding the greeting, zero client bytes and zero forwarded bytes. The device's terminal journal records `reason=operator`, native cancellation error1, `closed=true`, `threadStarted=true`, `joined=true` and `freed=true`.

| Device-clock interval or outcome | Recorded value |
| --- | ---: |
| Accepted Connect → stop request | 18,661 ms |
| Native start invocation → stop request | 18,646 ms |
| Stop request → joined worker | **79 ms** |
| Accepted Connect → joined worker | 18,740 ms |
| Final journal elapsed duration | 18,742 ms |
| Initialized connections / frames / input writes | 0 / 0 / 0 |

`CONNECTED`, frame, conversion and draw timestamps remain null. The private accepted-result field `startedToStopMs` refers specifically to **NATIVE_START → STOP**. These intervals use one Android monotonic clock; no host/device clock subtraction or input-latency claim is made. The last running journal still contains native state0 from its initial persistence; actual thread-start, the gate's real accepted socket/greeting and the terminal join/free record establish the tested boundary. A continuously persisted CONNECTING state is not claimed.

## Retained observation failure and launch details

The first journal read caught a transient zero-byte file while it was being created. The observer failed JSON parsing and saved `case-result.json` as `not-exercised`. That empty read and failure are retained unchanged. The parent reread the **same live attempt**, verified native start and the genuine held greeting, then invoked Disconnect and collected `07-final.json` and `07-accepted-result.json`. No restart or second Connect was used to obtain this result. The original observer's result is not erased or counted as a separate device attempt.

The Android CLI launch returned success after reinstalling the exact APK, but its immediate layout did not show VNC. An explicit launch of the already-exported diagnostic Activity was followed by actual UI inspection. CLI exit status alone is not accepted as visible-launch evidence. No export policy or credential policy was weakened.

## Preservation, cleanup and remaining work

There are now **12 closed VNC records**, with all11 predecessors byte-identical before, after and after stopping the app. Four diagnostic slots remain, subject to a fresh preflight before further work. The app is stopped, its temporary profile and owned ADB reverse are removed, the gate's sockets are closed, and the genuine server/canvas exit0 with their listener/X socket/lock absent. Boot ID and boot count match across this run; this observes no recurrence during this case and does not establish the earlier reboot's cause or repair it.

The new [mouse instrumentation build](../../../docs/06-validation/2026-10-15-mouse-input-fixture.md) is a separate test fixture. It did not participate in this result and has not run on the device. Its same-clock inspection prerequisite must be resolved before a held-button test. Require a real host button press, then a host release before the helper's final UP; framework injection acceptance alone is insufficient. TLS/PAM cancellation is outside this greeting-blocked case, not an inferred pass. Broader servers, product credentials, process recovery, physical input/IME and latency remain open; no stage, capability or release gate changes.

The [manifest](2026-10-15-vnc-initialization-cancel.json) binds sanitized observations to exact private files, including the initial failure, real journals, before/after preservation, helper sources, gate and host cleanup. Parent review and an independent direct-Codex evidence review agree on the bounded verdict and timing definitions. Raw UI, device identity, commands and fixture data remain ignored. The gate's seven synthetic helper checks establish only helper behavior; this report's device verdict comes from the actual correlated run.

The protocol greeting boundary follows [RFC6143 §7.1.1](https://www.rfc-editor.org/rfc/rfc6143#section-7.1.1) (R24). Android interval semantics follow [SystemClock](https://developer.android.com/reference/android/os/SystemClock) (G160). Existing native compilation,10 JVM tests, Android Lint and upstream regression evidence carry forward because the VNC application and engine bytes are unchanged.
