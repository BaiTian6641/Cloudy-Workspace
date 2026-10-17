# Desktop 0.1.20 VNC startup-failure construction

Desktop **0.1.20 / code 21** passes the bounded Java startup-failure retention construction. It is **not installed**; Desktop **0.1.19** and Console **0.1.16** retain the prior [actual-device authority](2026-10-16-vnc-error-retention-device.md). No actual OS startup failure or new device behavior is claimed.

The public engine queues connection admission before running native startup on its owner thread. Previously, post-allocation start rejection or an exception could enter cleanup before a normal status poll existed. Successful retirement could therefore leave only the initial no-result code. The reviewed helper samples validated native status on that same owner thread and retains an available selected error, or fixed Java startup reason **11**, before the unchanged cleanup authorization. It exposes no peer or exception message.

Classification rechecks explicit stop under the callbacks lock shared with stop publication. A stop that already won does not acquire an invented startup error; an earlier meaningful callback error is retained. A helper that wins before a later callback may retain generic reason 11. This establishes failure retention, not exact-cause attribution or complete stop/start race closure. No new elapsed status-call bound is introduced. Cancel, join/free, uncertain handle/lease retention and explicit retry retain their existing semantics.

The exact two-file engine/test proposal was adopted. Product changes bind version 0.1.20/code 21 and the existing instrumentation version guard; no new instrumentation pass is inferred. Official rationale sources R368/R369 support the startup/scheduling explanation, not exact installed bionic behavior. Four deterministic Backend tests cover native start rejection with selected error, exception/unreadable status and uncertain cleanup retry, explicit stop during rejection, and prior callback error precedence. These are real Java owner executions with test-only native seams, not actual pthread resource-exhaustion events.

| Actual affected check | Result |
| --- | --- |
| Input/output binding review | 263 current inputs, no build-time drift; 18 current/frozen artifacts match |
| Engine JVM task | Executed: 16 methods passed, including four new methods |
| Desktop JVM task | Executed: 27 methods passed |
| Lint | Four library/product debug/release reports, zero issues |
| Gradle | 446 tasks: 75 executed, 371 up-to-date; offline and strict dependency verification |
| Package inspection | Debug/release 0.1.20/code 21, min SDK 35, target SDK 37, private VNC Activity |

Both test tasks actually executed; the 27 product methods are not described as reused XML alone. No failures, errors or skips appear in the bound JVM XML. APK native libraries and notice/source assets match the accepted 0.1.19 closure, excluding generated dexopt profiles. Both library AAR native/assets closures also match their accepted baseline. The Java-only reuse declaration is bound to the existing native receipt; native C/JNI/dependencies were not rebuilt or retested. Release is unsigned; APK signature cryptography was not independently reverified.

The debug APK SHA-256 is `08157c99fa65eed42872355a87b83a56cf6dce22ae80e3ef097d52606cd6affb`; unsigned release is `55b24c0652f4d5ae94ac0e26c7fd18c5988ac180324922d5411134306dc48850`.

The [JSON companion](2026-10-16-vnc-startup-failure-construction.json) records source, artifact, test and review bindings. Principal ignored evidence references are literal paths:

- `build`: `.cache/vnc-product-session-policy-01/startup-error-build-01/result.json` — SHA-256 `867defa2981d15ce8405d81d87debc01883b960414c146b06c8a83b55d8032af`.
- `independentBuildReview`: `.cache/vnc-product-session-policy-01/startup-error-build-review-01/result.json` — SHA-256 `a7b61c7c5f838440e98b54c60aba95dc650800f98c01ccc8235e4d3b4d159b8b`.
- `sourceEntryReview`: `.cache/vnc-product-session-policy-01/engine-startup-error-review-01/review.json` — SHA-256 `cf3aca6a5ba50c43708eb7088c219ff0b04e11e05e1b2397922c027e9da70c15`.
- `nativeReuseDeclaration`: `.cache/vnc-product-engine-01/startup-error-reuse-01.json` — SHA-256 `75d123f3ed6294081ed2049477348c6cb99373342c64e01934f7248a8d97c8ed`.
- `nativeReceipt`: `.cache/vnc-product-engine-01/user-ended-01/receipt.json` — SHA-256 `96ed1ef25c0c1194c5c347f4989ef55b3c82cf56b608d41b89fb9513a7ecebc7`.

Parent source, build and public-evidence review accepts this construction checkpoint. No device, authentication, rendering, physical input, full lifecycle, stage/capability or release/distribution gate is promoted. The full P0–P6 goal remains active.
