# Product vault installation and UI entry

**Date:** 2026-10-16. **Outcome:** bounded installation and Console local-vault UI entry observed; human readiness remains pending. This is not an authentication, AES, enrollment or P1-S3 gate pass.

The parent installed the frozen Console and Desktop **0.1.2/code3** APKs through the official Android CLI. Console's normal `WorkspaceActivity` was launched, and its **Saved credentials** action opened the nonexported `CloudyVaultActivity`. The retained second layout shows the owning Cloudy window, empty enrollment fields and **Local storage state refreshed.** No credential was recorded. Desktop was installed but was not launched in this run.

## Exact artifact and device scope

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Console debug APK | 31,533,970 | `e80db122f00f62f09819fea760c1f91e9e2c50659321b93c9131ef43255128f6` |
| Desktop debug APK | 31,533,978 | `f4f6f7c4f39c21574f33136aaa9c40fbdba52c06f46bd75b85db0d6c3745808e` |

The retained APKs are `.cache/vault-entry-01/frozen-0.1.2/{console,desktop}-debug.apk`. Their sizes and hashes were independently checked while writing this report. These development artifacts are not release-signing or publishing qualification.

The actual device is **HP Googlebook14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. The package observation at **2026-10-16T07:16:29.930433Z** confirms both packages at 0.1.2/code3 and unchanged boot ID/count. No device serial or raw machine inventory is published.

## Retained observations

Private evidence is under `qualification/results/private/foundation/2026-10-16-vault-ui-01/`:

- `preflight.json`: exact device/build and initial boot observation.
- `install-console.log`, `install-desktop.log`: official CLI transfer/install receipts. The later package check establishes the observed installed versions; the logs are not treated as a generic success-string assertion.
- `launch-console.log`: explicit normal Console workspace launch.
- `layout-01.json`, `layout-02.json`: parent entry observations; the accepted second layout identifies the Cloudy credential-management UI and refreshed empty local state. Unrelated windows and raw layouts are not reproduced.
- `package-check.json`: final versions and boot-continuity observation at the timestamp above.

The [vault storage tests](2026-10-16-vault-storage-tests.md) and [source/integration review](../../../docs/06-validation/2026-10-16-product-vault-review.md) remain separate evidence. This UI-entry run does not turn their test-only software crypto into production Android Keystore AES evidence.

## Limits and next boundary

No authentication prompt was started, no credential fields were supplied, no AES operation or real enrollment was qualified, and no network operation ran. Human readiness is pending rather than a failed test. No saved-credential use, deletion, cancellation, hardware backing, biometric route or broader accessibility/input/lifecycle claim follows from opening the UI.

The preceding 0.1.1 evidence remains historical and unchanged. The parent's separately prepared 0.1.3 harness is a candidate, not the installed artifact established here. This report does not claim a final force-stop/cleanup state beyond the listed observations. The next finite qualification must correlate the actual installed build, operation and user authorization before making a real prompt/AES claim; no automatic enrollment or retry is authorized by this receipt.
