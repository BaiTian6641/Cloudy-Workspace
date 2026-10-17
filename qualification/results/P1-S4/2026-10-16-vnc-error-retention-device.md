# Desktop 0.1.19 VNC failure retention on Googlebook

Desktop **0.1.19 / code 20** passes one actual model/JNI failure-retention method and a separate ordinary UI connection-refusal case on **HP Googlebook 14, Android 17 / API 37, arm64-v8a, build CL3B.260622.276.R2 / incremental 16560795**. Console remains **0.1.16 / code 17**. The [construction report](2026-10-16-vnc-error-retention-construction.md) separately binds compilation, 27 JVM methods and two zero-issue Lint reports; those counts are not added to this actual-device result.

## Actual model and native ownership

Test04 executes `VncOperationFailureAndroidTest.nativeFailureSurvivesForeignReapAndActiveReplacement`: **one completed method, zero failures and zero skips**, with raw `OK (1 test)` and instrumentation completion code -1. Its exact test APK matches build03. Actual model A connects to an owned app-local listener, detaches to pause polling, and encounters peer EOF. Model B observes terminal A and centrally reaps it, then admits a second real native connection before A reattaches. Assertions verify A's retained failure, no borrowed foreign attempt, no false failure in B and the still-active foreign owner. B then disconnects and reaches terminal-or-reaped state before scope cleanup or app force-stop.

Both accepted sockets send/read zero RFB, TLS or authentication payload. Synthetic test secret/pin bytes are not account credentials. This establishes actual main-looper/model/JNI failure retention across foreign reap and replacement; it does not establish successful authentication, certificate verification, frames, input or rendered UI. The complete before/after baseline matches exactly and the test package is removed.

Version 0.1.18 test01's broad failure, test02's preflight refusal without instrumentation execution, and diagnostic test03's `ASSERT_ORIGINAL_FAILURE` remain preserved. They are not converted into passes by test04. The corrected source ignores the Java initial error sentinel until a meaningful native error or actual failed owner exists.

## Ordinary UI refusal and protected screenshot

The parent separately uses the ordinary VNC Activity and saved-profile form with the exact owned **127.0.0.1:44128** endpoint, unused at this step. Real connection refusal occurs before TLS or authentication; no host fixture or reverse is created. A real owned ephemeral certificate, independently checked using OpenSSL/DER, supplies only the submitted pin input. There is no certificate exchange, successful authentication or wrong-password finding.

The post-operation accessibility hierarchy contains both **“Original native owner retired”** and the fixed safe connection-failure message. The pin, username and password fields are empty and pin comparison is unchecked after submission. No fullscreen session is entered. The parent closes through UI and deletes only the owned temporary profile through the exact confirmation; the subsequent profile screen states deletion completed and no saved profiles remain.

**This is not pixel visual acceptance.** `FLAG_SECURE` remains enabled, and the inspected screenshot has black protected app content. The separately captured accessibility hierarchy establishes actual text/state, not pixel layout, readability or complete accessibility behavior. Security is not weakened to obtain a screenshot.

## Final settlement

Both products are stopped, product test packages are absent and the UI inspector is stopped. The original native owners reached terminal retirement before app stop. No host listener or reverse was created and no question remains active. Final authority is the private settlement snapshot plus its separately bound acceptance. Raw exact equality remains false with only two explained hash differences: Desktop profiles.db after actual UI create/delete, and AndroidX profileInstalled after upgrade/launch. Original logical rows/metadata, trust, resources/replay, preferences, full UIDs and recorded system/boot state remain unchanged; the normal local-network permission is retained.

The [JSON companion](2026-10-16-vnc-error-retention-device.json) records exact private evidence bindings without publishing credential values, private run/profile identities, account inventories or raw transcripts. Principal literal evidence paths are:

- `actualTest04`: `qualification/results/private/foundation/2026-10-16-vnc-error-test-04/result.json` — SHA-256 `c703426e0d76309c7075db65e72d3712392c406d03fd4adbd26ecdbf7aa837d5`.
- `actualTest04Output`: `qualification/results/private/foundation/2026-10-16-vnc-error-test-04/run-io/negative-test.stdout` — SHA-256 `a81ac6eebeced1e9625589700aa218eaa4b5d1132a57a79835daf49075f6b20d`.
- `runtimeReview`: `.cache/vnc-product-session-policy-01/error-visibility-runtime-review-01/result.json` — SHA-256 `c6e3acde10108066e9262861c0fb2847da2bb0a98853125d4c4430fa04c68b46`.
- `uiNegative`: `qualification/results/private/foundation/2026-10-16-vnc-error-ui-01/negative-result019.json` — SHA-256 `6e37d604462038ac6031201be863dfd38836964c93cf5292971e14ce95fa51ce`.
- `uiPostscreen`: `qualification/results/private/foundation/2026-10-16-vnc-error-ui-01/negative-postscreen019.json` — SHA-256 `5a61c74e9586c9d09a91585cf66ba6f1fa7f2a9ff1523292d6c7db94846606ab`.
- `protectedScreenshot`: `qualification/results/private/foundation/2026-10-16-vnc-error-ui-01/negative-visible019.png` — SHA-256 `5d97289426576f9966d9cf2faf15a7efd5efa7a8703184195b061578a2d0e5de`.
- `profileDeleteConfirmation`: `qualification/results/private/foundation/2026-10-16-vnc-error-ui-01/cleanup-confirm019.json` — SHA-256 `d157d4bde3009c038e3559fdbdac9165e4caaefe96d1fd1d3e2baf1281d6fc9f`.
- `profilesEmpty`: `qualification/results/private/foundation/2026-10-16-vnc-error-ui-01/cleanup-empty019.json` — SHA-256 `454f2d99e2269020e2eaf5001568f8ef23ed31d00f6aa3c595fa8629e4c83f9b`.
- `settlementSnapshot`: `qualification/results/private/foundation/2026-10-16-vnc-error-settlement-01/result.json` — SHA-256 `c4d359b1d53a88ef67d369b606f1dfb6f5abcdc80c1f2f019aa802d9559fcc8d`.
- `settlementAcceptance`: `qualification/results/private/foundation/2026-10-16-vnc-error-settlement-01/acceptance-01.json` — SHA-256 `bf150fc4cd0c7967b7a61b76fe750c840feb50923538e977e6ae5066fb49518c`.

No stage or supported capability is promoted. Broader lifecycle, server interoperability, sustained use, accessibility, distribution and release requirements remain open; the [earlier successful user-ended session](2026-10-16-vnc-user-ended-device.md) retains its separate positive-session scope.
