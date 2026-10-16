# VNC held-primary-button release on focus loss

**Date:** 2026-10-15, America/Chicago (actual host run 2026-10-16 UTC). **Stage:** P0-S4. **Verdict:** bounded **PASS** for a framework-held primary mouse button: the actual host observes release after the notification shade takes focus and before the helper sends its matching UP. Physical mouse/trackpad qualification remains separate. The [manifest](2026-10-15-vnc-held-button.json) binds actual private evidence, exact artifacts and sanitized observations.

## Exact tested route

The unchanged VNC diagnostic **0.1.3/code4**, build `478b0a46-2d02-4eeb-940b-e25e602f8aa1`, runs on HP Googlebook 14, Android 17/API 37, arm64-v8a, build `CL3B.260622.276.R2`, incremental 16560795. Both pulled VNC APKs match SHA-256 `97799b6a6495f3c6506df705d2cb5249f1a013d0bfe646b37dd2997a8b8d55f0`. No VNC product/adapter change or reinstall was required for this case.

The separate framework input fixture **0.1.2/code3, candidate 04** has SHA-256 `d3c3e186dc648608d36063a0a66766d402d78832e430a7d785efa788c451cf26`. Its [admission report](../../../docs/06-validation/2026-10-15-mouse-input-admission.md) records actual build/Lint/dependency checks, installation, no-input inspection and rejection of expired admission. It also preserves candidate 03's failed inspections and the source-guided repair. The helper targets its own package and uses public Instrumentation/UiAutomation/MotionEvent APIs; it does not contain a network or credential path.

Host session 10 uses Ubuntu 26.04, TigerVNC 1.15.0+dfsg-2build1 and Qt 6.9.2 canvas 03 in its own 1280×720 virtual X session. This is **not the workstation's Wayland desktop capture**. The protected route uses X509Plain, a deliberately provisioned exact ephemeral certificate leaf, normal host PAM and a loopback ADB reverse. Password and username are entered only by the user on the Googlebook. The host supervisor has a 600-second limit, canvas 570 seconds and Android profile 180 seconds; this run ends after 156,330 ms with both host processes exiting 0.

## Actual sequence and causal evidence

The user reports “Connected; leaving controls untouched.” The parent observes an initialized connection and the visible framebuffer, obtains its actual center, retires only the identified owned Android CLI instrumentation, verifies that UiAutomation registration is absent, then obtains a fresh helper inspection. No other CLI layout command runs during helper inspect/hold. The helper validates package, focus, display, exact root bounds, point containment and both actual Android clock ages before generating a 25,000 ms primary-button hold.

| Observation | Actual evidence |
| --- | --- |
| Host receives DOWN | Canvas sequence 3, button 1, buttons 1, point 639×360 |
| Parent opens notification shade | Command follows the observed host DOWN; WindowManager confirms NotificationShade focus |
| Host receives early UP | Canvas sequence 6, button 1, buttons 0, same point; observed 386.595871 ms after helper spawning |
| Helper remains active at early UP | Process still running, with no matching-UP completion yet |
| Helper finally sends matching UP | Both framework injections accepted; final DOWN-to-UP duration 25,016 Android uptime ms |

The causal bound establishes the result without comparing unlike clocks: the helper's DOWN cannot precede its spawning; its own matching UP occurs 25,016 uptime ms after DOWN, whereas the host UP is already observed within 387 host-monotonic ms after spawning. The successful final duration and injection result provide the necessary margin; process presence alone would not prove it. The observed host release therefore cannot be explained by the helper's final UP. This is a bounded integration result, **not an input-to-photon latency measurement**.

Actual native attempt `a5b7f694-b6e8-401a-8205-8ed5bb4241ad` initializes one connection, receives 436 native/Java frame callbacks, converts 425 frames and draws 420 unique frames. All five admitted input operations complete and write successfully. Its generic journal field remains `releaseResult=written-not-host-confirmed`; the separate host event journal confirms this specific focus-release case without rewriting that generic field or claiming all writes reached the host.

First draw occurs 20,620 ms after Connect, including initialization/authentication, and 98 ms after CONNECTED. This carries forward the known startup UX cost; it neither explains the historical roughly one-minute delay nor establishes interaction latency. Home then produces `not-visible` retirement, with native thread join/free 199 ms after Stop. Native error 1 is the cancellation terminal result here. The final host canvas has buttons 0 and no held keys; no F8 input is exercised in this case.

## Preserved failures and cleanup

A second newly discovered journal, `c8b82d25-14e5-4c70-9fd6-16c976a48afe`, records a 21 ms **pre-native setup failure** before the successful connection. It is closed with zero initialized connections, frames or input; no native worker started, so join/free are false. Its trigger is **unattributed**. Do not invent a user click, missing-profile cause or helper-input explanation.

The first cleanup observer expected one new row and failed on finding two. That observer failure is retained. The parent exported both new rows and all predecessors, verified closure, then completed teardown. Final state is **14 closed records**, all 12 predecessors byte-identical and all 14 unchanged after stopping. **Two slots remain**, subject to fresh inspection; preserve/archive the batch before more work could exhaust it.

The native VNC APK remains unchanged. VNC and helper processes are stopped; the owned CLI process and UiAutomation registration are absent. The temporary profile and owned reverse are removed. Host and canvas exit 0, with listener, X socket and lock absent. Boot ID/count stay unchanged throughout this run; the earlier kernel-lockup/reboot cause remains unresolved. No fixture, connection or user question is left active.

## Acceptance and next boundary

Independent direct Codex review verifies host DOWN/shade/early UP ordering, the final helper duration, actual shade focus, artifact identity, retained journals and teardown. Strict helper Java compilation, Android Lint and empty runtime closure pass in the separate admission report; accepted VNC compilation, ten JVM tests and zero Lint are carried forward because that code did not change. Documentation/evidence validation is recorded with this checkpoint; no library-wide replay is required under [D12](../../../docs/05-delivery/library-reuse-policy.md).

Together with [protocol-initialization cancellation](2026-10-15-vnc-initialization-cancel.md) and [held-key/hidden-session recovery](2026-10-15-vnc-focus-recovery.md), this closes the two named remaining bounded VNC recovery checks. Next is a scoped engine investigation/adoption packet using the accepted evidence and explicit remaining gates. **P0-S4 and CAP-VNC remain incomplete**: physical input/IME, broader server/host combinations, product credential ownership, process recovery, performance and final P1-S4 distribution/adoption still require their own outcomes. The protocol-cancellation pass does not cover TLS/PAM cancellation. Do not repeat passing framebuffer/build/recovery cases without a changed boundary or concrete failure.
