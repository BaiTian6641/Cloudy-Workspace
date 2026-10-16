# Shared foundation tests and manual qualification

This directory is shared source compiled separately into Console and Desktop. It is not a shared process or independently installed product. `src/androidTest` contains normal Activity tests and one explicitly opt-in manual vault case. `src/debug` contains only the non-exported diagnostic Activity; neither source nor its manifest is wired into release.

The [build review](../../docs/06-validation/2026-10-16-vault-harness-review.md) records the reviewed0.1.3 candidate. It is built only; [0.1.2 UI entry](../../qualification/results/P1-S3/2026-10-16-vault-ui-entry.md) does not qualify its new harness or actual AES.

## Parent-owned manual entry

Before any device run, preserve existing receipts, record the device/build and exact app/test APK hashes, and install matching versions. Use the approved Android tooling and an explicitly selected private device serial. Credentials/PIN/password never belong in commands, packets or replies. Do not clear app data or unregister existing keys to make a test pass.

Select the test class `io.github.baitian6641.cloudy.testing.VaultManualQualificationTest`. Without `cloudyVaultManual=enabled`, it skips before Activity launch. A skip is not a pass. With that opt-in, `cloudyVaultAction` accepts only:

| Action | Required observation | Human interaction |
| --- | --- | --- |
| `observe` (default) | Fresh metadata OPEN, Activity recreation, one new metadata-only OPEN and no automatic operation | No authorization prompt |
| `save` | Real approval, store OK, actual key-info fields, consumed-grant replay AUTH_REQUIRED | Fresh screen-lock approval |
| `use` | Real approval, store OK, one exact-byte consumer, replay AUTH_REQUIRED and zero consumers | Fresh screen-lock approval |
| `delete` | Real approval, delete OK, purge OK, verified absence and owned-profile cleanup | Fresh screen-lock approval |
| `cleanup` | Previously authorized deletion cleanup, purge OK and verified absence | No new decrypt or prompt |

Example for the no-prompt observation, after matching APK installation:

```bash
adb -s "$QUALIFIED_DEVICE_SERIAL" shell am instrument -w -r \
  -e class io.github.baitian6641.cloudy.testing.VaultManualQualificationTest \
  -e cloudyVaultManual enabled -e cloudyVaultAction observe \
  io.github.baitian6641.cloudy.console.test/androidx.test.runner.AndroidJUnitRunner
```

Use the Desktop test package for its independent lane. The parent must get current human readiness and identify each attempt before choosing `save`, `use` or `delete`. These actions are never ordinary CI commands. Keep one selected action/app at a time and preserve failed/rejected attempts. Polling expires after150seconds; the Activity separately expires after ten minutes. Its closure cancels real authority without automatically deleting a credential.

Capture `cloudyVaultReceipt` from instrumentation output and retain the owning-app receipt `noBackupFilesDir/cloudy-vault-qualification-v1/receipt.json` privately. It contains bounded metadata and results only. Verify new operation IDs against the specific attempt; never take an older success as a new pass. Source/app/test identity and user observations stay separate from callback, storage, backing and cleanup evidence.

Only one fixture record/account pair is retained per app. Reopening loads metadata without permission to decrypt. Receipt corruption/capacity exhaustion fails closed; a new fixture or archive requires a deliberate reviewed continuation. A profile-only interrupted save uses the product's separately reviewed verified-absence cleanup; the harness does not broaden cleanup to arbitrary keys. `observe` is Activity recreation, not process death. Actual negative prompt, key invalidation, cross-app isolation and production UI tests remain separate cases.
