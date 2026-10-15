# P0-S4 — Same-key screen-lock credential control

**Date:** 2026-10-15, America/Chicago. **Result:** the explicit screen-lock credential control passes local signing, certificate enrollment and a fresh-process metadata reload on the same Googlebook key that failed the earlier biometric test. This establishes a working local credential-authenticated route on the exact tested build. TLS, pairing, playback and the product credential vault remain unqualified. The [manifest](2026-10-15-stream-credential-control.json) binds source, packages, tests and actual private observations; the earlier [failed biometric/control results](2026-10-15-stream-identity.md) remain unchanged.

## Exact package and device

| Item | Actual identity |
| --- | --- |
| Package | `dev.cloudy.qualification.stream`, **0.1.3/code4** |
| Build reference | `470ba056-289a-4404-bee6-73695aa8798b` |
| APK | **24,673,986 bytes**, SHA256 `7922f6b87b3ec3e7673dea42ad52e92c1b1d60065aeb99e3a94d6bcb30143f37` |
| Preparation | `.cache/native-stream-android/identity-05`; SDK37, target36, min35, identity admission37 |
| Device | HP Googlebook14, Android17/API37, arm64-v8a |
| OS build | `CL3B.260622.276.R2`, incremental16560795, security patch2026-09-01 |
| Native engine | Same accepted5,110,376-byte Moonlight core, SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98` |

## Reviewed change

The separate **Authorize with screen-lock credential** action requires an existing ready or pending identity. It requests only `DEVICE_CREDENTIAL` through both AndroidX availability checking and `PromptInfo`, without a CryptoObject or negative-button override. The key still permits strong biometric or device credential for30 seconds and requires an unlocked device; its alias, RSA settings and opaque provider are unchanged. The control does not generate or delete a key.

An immutable per-attempt mode survives Activity recreation and is recorded separately from the actual callback type. Credential-only mode accepts only type1 before any cryptographic continuation; another/unknown type becomes a sanitized `AUTH_TYPE_MISMATCH`. The original combined action remains available with types1/2. Existing cancellation, foreground, deadline and owner-retirement checks remain. App guards in the pre-certificate control now report the `guard` stage rather than inheriting a signing-stage label.

This follows the public [AndroidX allowed-authenticator contract](https://developer.android.com/reference/androidx/biometric/BiometricPrompt.PromptInfo.Builder#setAllowedAuthenticators(int)) and exact1.1.0 source review, tracked as G149/R227 in the [provider entry](../../../docs/03-platform/googlebook/native-stream-key-provider-entry.md). API support is distinct from the observed runtime result below. Parent and independent direct Codex GPT6.1-Sol source reviews found no blocking defect in this bounded delta.

## Actual observation

All six previous identity records and the earlier native-load record were byte-matched before upgrade. The first0.1.3 Refresh still reported `ENROLLMENT_PENDING` / `trusted-environment`. The initial UI-tree query omitted the owned window despite Android reporting its resumed Activity and live process; a fresh scoped query then returned the expected native controls. The parent selected the credential-only action, and the user confirmed **“I completed the screen-lock prompt”.**

Actual record `bf3c4941-d2cb-45f4-99f8-c98a3a6ac5d9.json` reports requested mode `DEVICE_CREDENTIAL` and callback type **1**, authenticated at elapsed100925962ms. It completes at100926023ms, **61ms later**, with `READY` and no failure. The real store performed its plain-JCA signing/verification control, created and validated the public certificate, and passed the final random-challenge signature verification. Reported qualification is:

- RSA2048, `trusted-environment` backing and hardware-enforced authentication reported by actual `KeyInfo`.
- Null private encoding; no encoded private key was persisted or returned.
- `signVerified=true`, public certificate715 bytes, signature256 bytes.
- Public certificate SHA256 `1746d903cbcfc803874fef80b5386cce453e8df18143816e634d4165f6fc901b`.

The parent stopped the owned process, confirmed it absent, and explicitly reopened the diagnostic once. A fresh-process read-only Refresh reported `READY` / `trusted-environment`, requested mode `NONE`, without another authentication or signing result. This verifies persisted enrollment reload; it does not restore authorization or qualify expiry/lock rejection. All **nine** records remain preserved, with the prior six unchanged. Final explicit stop again confirmed no owned process. Version0.1.3 remains installed; no prompt is pending.

The earlier same-key0.1.2 biometric callback type2 followed by `UserNotAuthenticatedException` remains a failure. This credential pass establishes an authenticator-dependent difference on this device/key. It does not identify a HAL/token/provider root cause, fix biometrics, justify weakening policy or establish compatibility on another Googlebook build. No automatic retry, provider forcing or key replacement was used.

## Checks and remaining boundary

The actual Gradle run passed assembly, Java compilation, **five JVM tests across two suites**, scoped Android Lint and runtime-dependency capture. Lint retains27 inactive-upstream baseline errors,339 warnings and one hint, with zero new errors and zero owned findings. The two added tests cover authenticator-mode admission; they do not stand in for the real system-prompt/signature result. APK signature/native bytes/16KiB ZIP alignment/selected notices/compiled manifest audits pass. All39 runtime artifact identities match0.1.2. Final input/offline verification passes.

The package still has only biometric permissions, one owned Activity, disabled backup and no Internet permission or networking component. Every journal row reports zero host connections and decoded frames. The next streaming boundary is finite authenticated HTTP/TLS/pairing with the same opaque provider and explicit foreground reauthorization where needed, followed by real Surface/MediaCodec integration. Actual lock/cancel/expiry/missing-key handling, complete product-vault policy and release source/notices obligations remain separate work. Carry forward this credential route without repeating unchanged upstream validation; retain the biometric limitation explicitly. P0-S4 remains in progress and no capability is promoted.
