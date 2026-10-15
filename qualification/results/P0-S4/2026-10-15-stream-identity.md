# P0-S4 — Streaming identity and authentication boundary

**Date:** 2026-10-15, America/Chicago. **Result:** the real app-owned Keystore implementation builds and generates a pending TEE-backed identity, but authentication-bound signing fails on the tested Googlebook. Enrollment, TLS, pairing and streaming remain unqualified. The [evidence manifest](2026-10-15-stream-identity.json) binds exact packages, source inputs, dependencies and private observations. Read the [implementation review](../../../docs/06-validation/2026-10-15-stream-identity-review.md) and [official/provider research](../../../docs/03-platform/googlebook/native-stream-key-provider-entry.md).

## Exact tested builds

Both packages use `dev.cloudy.qualification.stream`, compileSDK37, targetSDK36 and minSDK35. Identity admission deliberately requires API37 because the inspected `KeyInfo.isUnlockedDeviceRequired()` API was added in36.1. Both ran on **HP Googlebook14, Android17/API37, arm64-v8a, build CL3B.260622.276.R2, incremental16560795, security patch2026-09-01**. The current user/profile is recorded only in private operator evidence.

| Build | Preparation / build reference | APK bytes / SHA256 |
| --- | --- | --- |
| 0.1.1 / code2 | `identity-02` / `c61b9f1b-e1f8-4c6a-bb61-382c64743ef0` | 24,656,602 / `a475a1d7fc1dd4bce80914ce3c41337de9e861bd136581d0454b46fca3aebe2b` |
| 0.1.2 / code3 | `identity-04` / `6b7ee157-b28b-4a3c-936d-bf91c94f6a5a` | 24,672,986 / `1f660391888e0e19ac11eeeedb37506663ae96951ab905ae8472091c4b915881` |

The accepted Moonlight native core is unchanged: 5,110,376 bytes, SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`, native build reference `f336e245-805d-4aad-bc1a-9208df8db318`. Its actual earlier [0.1.0 JNI-load result](2026-10-15-native-stream-apk-load.md) is carried forward; this run did not repeat native loading or decode frames.

## Actual sequence and verdict

1. Initial0.1.1 Refresh reported `ABSENT`. Explicit Create generated a real app-owned RSA2048 key with a30-second strong-biometric-or-device-credential authorization policy and unlocked-device restriction.
2. The user confirmed **“I completed the system authentication prompt”.** The actual successful-authentication callback was recorded at elapsed99739155ms; the CREATE operation failed with `AUTH_REQUIRED` at99739175ms, **20ms later**. That build did not identify the failing cryptographic stage. A subsequent Refresh reported `ENROLLMENT_PENDING` and `trusted-environment` backing.
3. Version0.1.2 added a real provider-neutral SHA256withRSA initialization/sign/verify control before BC certificate construction, plus bounded failure-stage, cause-class, public numeric Keystore-code and authentication-type fields. The same key and all three prior identity records were preserved. Two actual Refresh records in0.1.2 both reported the same pending TEE-backed state; their presence is retained without inferring why another refresh occurred.
4. The user confirmed **“I completed the system prompt”.** The actual AUTHORIZE callback reported `authenticationType=2` (biometric) at elapsed100185871ms. The operation failed at100185877ms, **6ms later**, with `AUTH_REQUIRED`, `failureStage=precertificate-sign-init`, `failureCauseClass=UserNotAuthenticatedException`, and null public numeric Keystore code. No certificate or qualification result was produced.
5. All six identity records and the earlier native-load record remain preserved. The parent explicitly stopped the owned diagnostic after collecting the completed result; `pidof` returned no process. Version0.1.2 remains installed. The pending key was not deleted, regenerated or exported.

This localizes the latest failure to plain JCA signing initialization **before BC certificate construction**. The observed Keystore exception distinguishes it from the app's own cancellation/expiry guard. The6ms interval is within the app's30-second admission window, but neither the user confirmation nor a prompt-success callback proves that Keystore accepted the corresponding authorization token. The available public error field did not identify the underlying rejection. No claim about a Googlebook biometric/HAL defect, numeric Android user ID, root secure-user ID mismatch or installed provider binary is established.

All recorded operations report zero host connections and zero decoded frames. The compiled manifest has only `USE_BIOMETRIC` and `USE_FINGERPRINT`, one owned Activity, disabled backup and no Internet permission, service, receiver or provider. No server, pairing or network workaround was introduced.

## Implemented boundary and verification

The real store implements the pinned `LimelightCryptoProvider`, with opaque Android Keystore handles, a pending-generation record, atomic app-private public certificate metadata and explicit recovery/deletion semantics. Authentication, cancellation and owner checks remain process-local; no saved success flag grants authorization. A retained ViewModel serializes crypto/evidence work away from the main thread, reattaches prompt callbacks after configuration changes and rejects replacement while a worker or prompt is still outstanding. Actual lock/cancel/recreation/expiry acceptance is still pending.

Both accepted packages pass Java compilation, three focused JVM authorization tests and scoped Android Lint. Each Lint report retains339 warnings and one baseline hint, with27 exact inactive-upstream errors baselined and **zero findings in owned source**. This is not a warning-free upstream tree. Entries must be retired before their affected upstream paths become active. No executable owned finding was suppressed. Package audits verify signatures, the exact native bytes,16KiB ZIP alignment, selected notices and39 resolved runtime artifact identities. Each prepared closure binds637 staged files; the final offline check passes against current source. Full dependency/source/notices delivery remains a release gate.

The parent inspected the direct Codex GPT6.1-Sol implementation and independent security review, including key ownership, lifecycle, exception sanitation and the manifest merge. One API-availability defect was corrected before the accepted build by admitting identity operations only on API37. The0.1.2 diagnostic has a small retained localization caveat: its initial/final pre-certificate app guards can inherit a crypto-stage label when throwing `IdentityException`. That did not produce this observed `UserNotAuthenticatedException`; correct the labels in the next changed build.

Private preparation history retains two rejected attempts: `identity-01` was staged before a final worker source change and correctly failed the pre-build input-integrity check; `identity-03` compiled and ran its tests but the package audit rejected an owned redundant API33 Lint warning. Fresh corrected builds were used instead of overwriting those receipts. An initial UI-tree observation omitted the active app; a directly viewed screenshot established the actual visible diagnostic and later scoped layouts worked. Raw layouts/screenshots remain private.

## Next bounded control and remaining gates

The official [AndroidX allowed-authenticator contract](https://developer.android.com/reference/androidx/biometric/BiometricPrompt.PromptInfo.Builder#setAllowedAuthenticators(int)) supports a credential-only prompt on API30+. A future explicit control can select `DEVICE_CREDENTIAL` on the **same existing combined-auth key**, leaving its30-second policy and all signing parameters unchanged. Record requested mode and require actual callback type1 before attributing that control to device credentials. This comparison is proposed, not executed in this checkpoint. No automatic retry, longer authorization window, provider forcing or software-key fallback is justified.

Successful credential signing would establish only an authenticator-dependent difference on this exact device/key; it would not identify the biometric defect or prove client-certificate TLS. Certificate creation/validation, local sign/verify, missing-key recovery, lock/cancel/expiry, real Sunshine TLS/pairing and Surface/MediaCodec playback remain separate gates. P0-S4 stays in progress; no capability is promoted.
