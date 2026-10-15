# P0-S4 — First foreground streaming pairing and client TLS

**Date:** 2026-10-15, America/Chicago. **Result:** the Googlebook diagnostic completed real upstream pairing and a pinned client-certificate HTTPS application-list read using its existing app-owned TEE-backed identity after fresh screen-lock credential approval. One separate request without a client certificate reached TLS 1.3 and then failed with `SSLError`. This establishes the bounded pairing/client-TLS route on the exact fixture below; it does not qualify playback, performance, cancellation, the product vault or CAP-STREAM. The [manifest](2026-10-15-stream-first-pairing.json) binds selected private evidence, preparation/source inputs, test reports and package audit.

## Exact build and fixture

| Boundary | Identity |
| --- | --- |
| Device | HP Googlebook 14, Android 17 / API 37, arm64-v8a |
| Device build | `CL3B.260622.276.R2`, incremental `16560795`, security patch `2026-09-01` |
| Application | `dev.cloudy.qualification.stream`, **0.1.5 / code 6** |
| Build reference | `8673aa5d-f310-48e8-aeba-d3a7169c7c12` |
| APK | **24,710,631 bytes**, SHA256 **`4441cabc3039bce2a97d4fd2bd190bf037d8882ed936e9072dc459683e0fc61b`** |
| SDK boundary | min 35, compile/target 37; identity diagnostic requires API 37 |
| Client source | Moonlight Android v12.2, `b48494cb96bff23d8886c4775cc4f39a1075495d`, with the locked finite pairing overlay |
| Native core | Existing build `f336e245-805d-4aad-bc1a-9208df8db318`; unchanged 5,110,376-byte library, SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98` |
| Host | Official Sunshine `v2026.914.233613`, source `63d35f702ee9e362e43263742981836ec0710384`, x86_64 Ubuntu 26.04 / KDE Wayland |
| Host executable | SHA256 `6946d8ac8bb0e99da1e51b374029c8b24dcabb315265a0e7004d046eb6f143fd`; same accepted release/runtime basis as [runtime-04](2026-10-15-stream-host-runtime.json) |

The finite private fixture retained the reviewed KWin permission-entry quoting and package working-directory procedure. Its permission file/cache procedure did not disable compositor permission checks. The selected route was loopback-only with parent-owned ADB reverse mappings and a public app-private profile containing the approved leaf certificate/pin. Explicit HTTP bootstrap/pairing remained part of the inherited protocol; HTTPS identity failures had no selected silent HTTP fallback. No stream or host application was launched by this pairing operation.

## Actual operation and evidence

The [foreground UI](../../../docs/06-validation/2026-10-15-stream-pairing-ui-review.md) requested `DEVICE_CREDENTIAL` for a fresh attempt on the existing `READY` identity. Actual AndroidX authentication type **1** was recorded at client monotonic time **103,374,262 ms**. The worker qualified that same key, obtained its provider while the attempt remained live, and ran the real pairing controller. Terminal observation was **103,374,480 ms**, a **218 ms** callback-to-observation interval for this one run. It includes qualification and subsequent work, excludes the earlier prompt wait and is not a latency benchmark or a comparison with the host clock.

Attempt **`945e8271-0120-48c3-b486-dd12c48ec5a0`** is closed with `status=completed`, `pairingCompleted=true`, `clientTlsRead=true`, `pairingStage=COMPLETED` and no failure code. The actual HTTPS application-list body was **64 bytes**, parsed by the upstream status/parser logic. Its body is not published. No frames were decoded.

The qualified identity remains `trusted-environment`, RSA 2048, with hardware-enforced authentication reported, unavailable private encoding and successful real sign/verify. Its public certificate SHA256 is **`1746d903cbcfc803874fef80b5386cce453e8df18143816e634d4165f6fc901b`**, matching the accepted [same-key credential result](2026-10-15-stream-credential-control.md). No identity replacement, software-key fallback, provider forcing or automatic authentication retry was used. The earlier same-key biometric failure remains unchanged and unresolved.

Host evidence records a pending client and successful PIN-submission status. A separate application-list control supplied **no client certificate**, reached **TLS 1.3**, then recorded `SSLError` and `denied=true`, without a successful application-list result. The error reason was not retained, so this report does not infer a particular TLS alert or diagnose its exact enforcement point. This control and the actual client result support the fixture-specific client-certificate boundary; they are not exhaustive TLS conformance tests.

## Build, review and preservation

The parent assembled `.cache/native-stream-android/pairing-ui-01/` from **648 staged files**. Actual Android compilation and **15 JVM tests passed**, with zero failures, errors or skips. Scoped Lint had **zero errors and zero owned findings**, retaining **27 inactive upstream baseline errors, 336 warnings and one hint**. Affected baseline entries must still be retired before enabling their paths. The package audit confirms one owned Activity; Internet, local-network and biometric permissions; exact unchanged native bytes; 16 KiB native ZIP alignment; signature; selected notices; and 39 runtime artifacts. It does not complete distribution source/notices obligations. The [source review](../../../docs/06-validation/2026-10-15-stream-pairing-ui-review.md) and [earlier transport build](2026-10-15-stream-pairing-build.md) remain separately scoped evidence.

Selected device evidence is under `qualification/results/private/native-stream/2026-10-15-pairing-01/`. The original and preserved pairing JSON are byte-identical. Cleanup verifies all **nine prior identity records** and the earlier native-load record remained byte-identical; the integration leader observed **ten identity records total and one closed pairing record** after this run. The new record remains unchanged after stop. No PIN, credentials, certificate body, raw UI or command log is published here.

## Closure and remaining limits

The host fixture stopped at **193,276 ms**, exited **0**, and verified **all four observed listeners closed**. Owned desktop permission/cache cleanup exited **0**. The parent confirmed the device app stopped; the selected cleanup record verifies both reverse routes and the public fixture profile were removed. Private host state remains retained with its process closed. No automatic unpair ran, and process/listener cleanup is not a claim that host enrollment was revoked.

Wrong-pin rejection, real background/operator cancellation, expiry/deadline behavior, arbitrary PIN delay and mid-pairing reauthentication remain separate cases. The inherited HTTP pairing phases are not end-to-end encrypted; preserve exact out-of-band leaf trust and the reviewed isolated route. Surface/MediaCodec delivery, changing decoded frames, physical streaming input, 3D behavior, sustained performance and end-to-end latency remain unqualified. P0-S4 stays in progress and no capability is promoted.
