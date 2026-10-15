# P0-S4 — First protected VNC framebuffer and pointer sessions

**2026-10-15:** VNC diagnostic **0.1.2/code3** completes real X509Plain authentication and displays the isolated 1280×720 canvas on the HP Googlebook14. Physical pointer movement/clicks and explicit disconnect have app and host evidence. The user also reports keyboard operation, but the host canvas records **zero key events**, so the F8/host-keyboard check remains unconfirmed. The user reports roughly one minute without a framebuffer at startup; that delay is **open**, not a performance pass. The [manifest](2026-10-15-vnc-first-session.json) binds the exact records and cleanup.

The device is Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental16560795, patch2026-09-01. Installed build reference is `f1b74f98-3691-4399-8169-ffc4fb36ba99`; APK10,888,191bytes, SHA256 `7a59f68429a89ece878d1d17ec9cc089f7a2672aa51a8893817a13d9e1ebec83`. The [build result](2026-10-15-vnc-auth-idle-build.md) records strict native compilation,37 focused C assertions, seven JVM tests, zero Android Lint findings, package audit and signing continuity. The app is stopped after this run.

## Authentication correction and retained failures

The first fresh fixture used accepted0.1.1. Two user-started attempts, `eb3520d2-1ba4-42de-9f48-a706c3c23951` and `c3341b8d-b527-4300-b05b-66a75003cea4`, stopped after15,129ms and15,091ms with generic initialization/security error10, zero initialized connections and no frames. Both workers joined/freed and records closed. A separate user-triggered load check also passed; it is not a session result.

The server subsequently emitted its authentication-success message at about20seconds. Official TigerVNC1.15.0 source places that message in [`VNCSConnectionST::authSuccess`](https://github.com/TigerVNC/tigervnc/blob/v1.15.0/common/rfb/VNCSConnectionST.cxx#L392-L410), reached after [`approveConnection(true)` sends the successful security result](https://github.com/TigerVNC/tigervnc/blob/v1.15.0/common/rfb/SConnection.cxx#L426-L448). R244/R245 and retained source hashes document this mapping. The native error alone did not mean the password was wrong.

The reviewed0.1.2 delta lets the read wait use the existing30-second authentication phase. The absolute phase remains30seconds, clipped to the original session deadline; cancellation and the post-initialization deadline check remain active, and ordinary session read idle returns to2seconds. No host PAM configuration, certificate policy or credential fallback was changed. The password was entered only by the user in the native Googlebook field; no password field inspection or credential capture was performed.

## Real session results

Both fresh fixtures use privately extracted TigerVNC1.15.0+dfsg-2build1, a private X display and Xauthority, X TCP disabled, loopback RFB through one scoped USB ADB reverse, and an ephemeral approved leaf certificate. Only VeNCrypt19/X509Plain262 is offered. Normal PAM service `other` and the current-user allowlist remain in force; clipboard exchange is disabled. The reviewed real Qt6.9.2/Xcb canvas runs on the isolated display, without capturing unrelated desktop content.

| Observation | First0.1.2 session | User-started reconnect |
| --- | --- | --- |
| Attempt | `7ac4ad88-4ced-4dd6-afe9-97bd8f80231d` | `6986e1e0-f035-4c52-b0f0-5d03a54a55af` |
| Duration | 60,046ms | 61,951ms |
| Initialized connections | 1 | 1 |
| Native/Java frame callbacks | 558/558 | 235/235 |
| Converted frames / unique draws | 526/480 | 223/220 |
| Admitted/completed/written inputs | 328/328/328 | 0/0/0 |
| Terminal reason | User disconnect; cancellation code1 | Owned fixture stopped by parent; I/O code3 |
| Native ownership | Joined, freed, journal closed | Joined, freed, journal closed |

The host independently records **254 motion events and four button events**, with no held buttons/marker keys at canvas completion. The client release result is `written-not-host-confirmed`: successful RFB writes are not per-event host acknowledgements. The keyboard discrepancy and unspecified physical input subtype remain visible; absolute VNC pointer delivery does not establish relative3D support.

Server authentication takes about20seconds on both0.1.2 connections, measured only to one-second log resolution. The diagnostic did not record first-frame or first-draw monotonic timestamps. Therefore the user's approximately one-minute blank interval cannot be fully apportioned to authentication, framebuffer setup or presentation from this run. The next changed-path diagnostic should separate those phases and show useful waiting state; no host authentication weakening or unsupported low-latency claim is justified.

The first fixture stops cleanly after179,048ms; the corrected-build fixture stops after174,067ms. Both server/canvas processes exit0, RFB listeners close, and owned X sockets/locks disappear. Seven app records remain byte-identical after final stop, including the previous five across installation. The public fixture profile and exact ADB reverse are removed, and no diagnostic process or test prompt remains. The earlier [wrong-leaf rejection](2026-10-15-vnc-first-trust.md) is retained without repeating unchanged trust tests.

This is bounded VNC integration evidence. Startup latency, host keyboard marker, input subtype, direct-LAN permission enforcement, lifecycle/recovery, product credential storage, broader server coverage and distribution closure remain open. CAP-VNC and P0-S4 are not promoted to supported/completed.
