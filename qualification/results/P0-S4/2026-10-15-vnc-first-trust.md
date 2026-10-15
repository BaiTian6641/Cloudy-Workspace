# P0-S4 — First real VNC trust rejection

**2026-10-15:** VNC diagnostic **0.1.1/code2** is installed on the HP Googlebook14 and stopped after a real wrong-certificate attempt. The selected finite TigerVNC server and isolated canvas have exited cleanly. The negative trust boundary passed; **accepted-pin authentication, frames and input remain pending**. The user deferred the hands-on login test until returning. The [manifest](2026-10-15-vnc-first-trust.json) binds the exact package, device journal, host result, source/build receipts and private operator evidence.

The Googlebook reports Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental16560795, security patch2026-09-01. Installed build reference is `ebfc6f16-0b7a-4ace-8849-23e9bc748492`; APK10,888,191bytes, SHA256 `37ed5c92c5a385ce357df0bf2204c4031d6c131e76d2990238875909b878ba11`. The [accepted build review](2026-10-15-vnc-auth-timeout-build.md) supplies native/compiler/Lint/test/signing evidence without repeating unchanged library checks.

The host is the selected Ubuntu26.04 workstation, using privately extracted TigerVNC1.15.0+dfsg-2build1. Its `Xtigervnc` binary SHA256 is `e59b12d25e9a9341db3b54b2da365c0964faeb52b5b720fb50674631d3fcac84`. This fixture owns a distinct X display and private Xauthority cookie, disables X TCP, accepts RFB only on loopback through one scoped ADB reverse, and disables clipboard exchange. It exposes X509Plain only, with its own ephemeral certificate/key, existing normal PAM service `other`, and current-user allowlist. No account, PAM policy, system installation, root server or unrelated session was changed. The [official Xvnc options](https://tigervnc.org/doc/Xvnc.html) and registered R234/R235 authentication source define these boundaries.

The actual [Qt/Xcb canvas](../../../docs/06-validation/2026-10-15-vnc-host-canvas-review.md) started on that isolated display. It paints color bars and an alternating square, with actual input counters separate from paint/timer activity. Its source and binary match the reviewed canvas02 hashes. The fixture became ready in304ms and ended after570,875ms when the canvas reached its570-second limit, inside the supervisor's600-second bound.

| Observation | Actual result | Boundary |
| --- | --- | --- |
| Wrong certificate leaf | Attempt `1b452388-235c-4c56-bab6-20bba0435c87`, terminal `native-error-4`,46ms | One changed hex digit in the approved leaf pin; known-invalid dummy credential values only |
| Native lifecycle | Worker started, joined and freed; journal closed | Actual JNI/network path, not a simulated policy result |
| Initialized connections | 0 | No authenticated VNC session |
| Frame/input counters | All0; geometry0×0 | No rendering or input pass |
| Host negotiation | RFB3.8, VeNCrypt19, X509Plain262, then TLS termination | Consistent with rejected TLS; no inference of a successful PAM operation |
| Accepted-pin login | No new attempt before fixture expiry | User action pending; not an authentication failure |
| Host canvas | 2,282 paint events,2,281 timer ticks; all input counters0 | Host-only animation is not client frame delivery |

The approved certificate profile and current host username were prepared for the separate login case, with the password left for user entry on the device. No password was obtained through chat or diagnostic inspection. Only owned sanitized journals were read while credential entry could have been pending. This fixture was allowed to expire without extending its lifetime or submitting credentials automatically.

Final cleanup verifies no diagnostic process, removal of the public fixture profile and exact reverse route, server/canvas exit0, closed RFB listener, and absent owned X socket/lock. The earlier load record `52109479-3fd7-4f1e-bb27-fd51430f7015` and new negative record are byte-identical after stop. Private fixture keys/cookies and raw inventories stay ignored; retained evidence is not a running service.

Next start a fresh finite fixture when the user is available, let the user enter the normal host password directly in the Googlebook native field, then qualify actual authenticated frames, controlled input and cancellation. Absolute RFB pointer delivery does not establish RDP-style relative input for3D. The nearby-device permission grant and USB loopback route do not establish denied direct-LAN enforcement. CAP-VNC and P0-S4 remain incomplete; no latency or distribution readiness is inferred.
