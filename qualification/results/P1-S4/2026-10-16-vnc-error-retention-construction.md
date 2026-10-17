# Desktop 0.1.19 VNC failure-retention construction

Desktop **0.1.19 / code 20** passes the bounded source, compiler, JVM, Lint and packaging checks for retaining an operation failure separately from native-owner retirement. Console remains **0.1.16 / code 17**. This report records construction only; the subsequent [device result](2026-10-16-vnc-error-retention-device.md) records installation, one actual model/JNI/socket pass, UI state and final settlement separately.

The observed [0.1.17 device case](2026-10-16-vnc-user-ended-device.md) exposed a misleading outcome: native cleanup completed, but the screen showed only retirement after a failed connection. The repair retains the first fixed safe failure for the exact original attempt. Only installing a newly admitted original owner resets that outcome; refresh, rejected admission and cleanup cannot erase it. Lifecycle status remains separate. Categories cover connection failure, timeout, certificate rejection, and security or authentication rejection. They disclose no raw peer strings, credentials or exception text and do not diagnose a wrong password.

Candidate01 review found that another window could reap the terminal record before the original model observed its error. Candidate02 shared the original model's plain outcome holder with the registry and captured the terminal result before unlinking, but its UI could still hide the retained error while a foreign replacement was active. Candidate03 additionally projects the model's own holder in every final snapshot, including that foreign-active branch. Neither correction borrows another window's attempt or outcome. The two rejected candidates and their independent reviews remain preserved; they are not retrospectively accepted.

Actual version 0.1.18 testing exposed an additional mapper defect: the Java engine starts with nativeError `-1`, which the original fallback incorrectly treated as connection failure. The current mapper treats `-1` and native no-error `0` as no result unless the Java owner explicitly reports FAILED. A seventh pure regression covers this sentinel. Test01 remains an actual broad-assertion failure; test02 stopped at preflight on a stopped-app flag and executed no instrumentation; the diagnostic test03 failed at `ASSERT_ORIGINAL_FAILURE`. That diagnostic phase contained four assertions, so it does not identify which assertion failed. The current test names those assertions separately; the earlier diagnostic and test-build02 artifacts remain preserved. Current 0.1.19 construction does not convert these attempts into passes.

The accepted patch changes the model, fixed outcome helper, screen, string resources and seven pure tests. The build additionally binds the Desktop version and the reviewed Android test source. Native engine, protocol, JNI, cancellation, release, join/free, ownership and the previously accepted user-ended policy remain unchanged.

| Actual affected check | Result |
| --- | --- |
| Current source/input bindings | All 263 recorded inputs match; no build-time drift |
| Product JVM task | Executed; 27 methods passed, including seven outcome methods; zero failures/errors/skips |
| Desktop Android Lint | Debug and release: zero issues |
| Gradle construction | 428 tasks: 45 executed, 383 up-to-date; strict dependency verification and offline mode |
| Frozen output bindings | All 13 recorded artifacts match current outputs and frozen copies |
| Android instrumentation | Compiled with the test APK; not executed by this report |

Library JVM tests and unchanged native/upstream batteries were not rerun. Debug and unsigned release APK manifests both identify 0.1.19/code 20, min SDK 35, target SDK 37, and a nonexported VNC Activity. Native libraries and notice/source assets match the accepted [0.1.17 construction](2026-10-16-vnc-user-ended-construction.md) byte for byte. Application-generated release baseline profile changes are outside that native/notice equality claim. APK signature cryptography was not independently reverified.

The debug APK SHA-256 is `d55cb18cb2ddecc9bb82a9665b30eab264ebcaccedf089375e178468564a9488`; the unsigned release APK is `0854691511503348bc47e8c3ef3213c3174c952b4bc6fa5aa00bc573738c89de`. The AndroidTest APK is `fa0378de8ed603abc3ac7cafdc74fc604eb363fdcc27451ef315e9b2909a4746`.

The [JSON companion](2026-10-16-vnc-error-retention-construction.json) binds candidate03 source/patch, the complete build input manifest, all 13 output hashes, actual Gradle log, source reviews, instrumentation entry review and independent artifact review. Principal ignored evidence references are:

- `candidate03Freeze`: `.cache/vnc-product-session-policy-01/error-visibility-proposal-01/freeze-03.json` — SHA-256 `ea7ed8c5ae5b2c78507de018ef2ba8390319a7fbcf62998e4e8b703bf491916e`.
- `acceptedSourceReview`: `.cache/vnc-product-session-policy-01/error-visibility-review-03/review.json` — SHA-256 `3b4ea4803bb5a245c2bf8d7a4d13b241f1e2cca1ffe9db3d123177c200349649`.
- `build`: `.cache/vnc-product-session-policy-01/error-visibility-build-03/result.json` — SHA-256 `0f663ce3269c700e1e1e120ba9c95989858696407e0519f959cd376955d1eac2`.
- `buildInputsBefore`: `.cache/vnc-product-session-policy-01/error-visibility-build-03/inputs-before.json` — SHA-256 `b19a278c6e593d96dc63318ba047cd3a2baff71558d1d834831dc919db7d6d8c`.
- `gradleLog`: `.cache/vnc-product-session-policy-01/error-visibility-build-03/gradle.log` — SHA-256 `8660d4ae9b4bf3d7337d9b94320497ca77457e7fc8fd42a1897ea81be5a19625`.
- `independentBuildReview`: `.cache/vnc-product-session-policy-01/error-visibility-build-review-03/result.json` — SHA-256 `4946f8c0546a8cc0afd304c0ccc52b86f8e43b2dc2086943dfdc06c1d8b96f0e`.
- `deviceSourceEntryReview`: `.cache/vnc-product-session-policy-01/error-visibility-device-review-01/review.json` — SHA-256 `e40e810cc7d311b8cd16e261323b8b94085367bb7332429b033b79a79f19fae0`.

No formal stage, supported capability, broader server/input/lifecycle behavior, or release/distribution gate is promoted. The separate device result supplies actual model/native retention, UI hierarchy state and original-owner settlement. Its secure screenshot supplies no pixel visual acceptance.
