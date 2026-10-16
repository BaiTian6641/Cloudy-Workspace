# Shared foundation tests and manual qualification

This directory is shared source compiled separately into Console and Desktop. It is not a shared process or independently installed product. `src/androidTest` contains ordinary Activity cases, controlled profile/trust-recreation cases and explicitly opt-in vault/profile-isolation qualification. `src/debug` contains only the non-exported diagnostic Activity; neither source nor its manifest is wired into release.

The [build review](../../docs/06-validation/2026-10-16-vault-harness-review.md) records the reviewed0.1.3 vault harness. Its subsequent [metadata-only run](../../qualification/results/P1-S3/2026-10-16-vault-metadata-recreation.md) passes one explicit observe case per app; actual protected save/use/delete remain unqualified. Current installed product artifacts are0.1.6/code7 from the [native display increment](../../qualification/results/P1-S2/2026-10-16-native-display-settings.md). Preserve previous receipts and correlate any future protected operation with that run's exact current APKs; do not reinstall an older product merely to repeat completed cases.

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

## Local profile Activity tests

`ConsoleProfileActivityTest` and `DesktopProfileActivityTest` inherit six actual production-store cases from `ProfileActivityContract`: CRUD/confirmation, unsaved text/selection recreation, conflict/review/reapply, a further change after review, deleted-row rejection and cancelled Delete. Run the exact app class with its own test package after installing the matching app/test APKs. These tests need no host, password, Keystore prompt or manual opt-in. The separate platform `ProfileEditorStateTest` supplies10 actual Bundle/Parcel cases. The [accepted0.1.4 result](../../qualification/results/P1-S3/2026-10-16-profile-editor.md) binds26 device passes including existing window regressions.

Tests create uniquely named local fixtures and exclude all preexisting IDs from cleanup. Preserve the initial catalog and fixture prefix privately. Cleanup captures the owning application context before closing the ActivityScenario, releases test queue gates, then waits for bounded worker/main callback fences. It uses the real store only after settlement is proved; it does not require successful UI scrolling or semantics. If Scenario closure or queue/callback settlement is unproved, cleanup fails and preserves that run's exact fixture for reviewed reconciliation. Never clear application data or remove unrelated profiles. A passing result requires cleanup assertions; a failed attempt does not imply cleanup succeeded.

## Admitted profile writes and Activity recreation

`ConsoleProfileRecreationTest` and `DesktopProfileRecreationTest` inherit two cases from `ProfileRecreationContract`, one Create and one Update. Test-only `ProfileTestQueue` inspects the app-owned private operation executor and submits a bounded gate ahead of the actual UI Save. The tests confirm the real mutation was admitted, recreate the actual Activity while the gate still prevents its execution/callback, then release the gate in `finally`. The queued production-store write and the replacement Activity's subsequent read run normally.

Assertions require retained draft text, restored pending uncertainty with Save disabled, exactly one created row or exactly one revision increment, and no repeated mutation after Refresh and another recreation. Gate expiry fails the case. The helper does not replace the store, fabricate results, remove queued work or change production APIs. This is white-box scheduler-controlled Activity recreation with real persistence. It does not establish natural race frequency, process-death recovery, physical input or remote connectivity.

The [profile recovery record](../../qualification/results/P1-S3/2026-10-16-profile-recovery.md) binds the accepted continuation and all preserved failures. Run01 records eight profile Activity cases per app passing, both isolation seeds passing and an initial Console verification failure `PEER_DENIAL_UNCONFIRMED`; both original fixtures were retained. Run02 stops in host preflight before instrumentation because package output contains multiple user-specific `dataDir` values. Run03 selects the actual owning `run-as` working directory and matches it to reported package paths rather than assuming the first candidate.

Run03, 09:20:14.340828–09:20:18.607266 UTC, verifies both original fixtures without reseeding. Both directions pass the own-database positive control and exact externally witnessed peer-path digest check, reporting `NOT_VISIBLE` / errno2. Both exact cleanup phases then pass. Original profile rows and catalog generations match the pre-run baseline; product bytes, existing vault receipts and boot identity/count are unchanged. Both app test packages are removed and both products are stopped. The accepted scope is sampled owning-product catalog separation and inability to open the witnessed peer databases. It does not establish a sandbox cause, credential isolation or process-death editor recovery.

## Opt-in installed-product profile isolation

`io.github.baitian6641.cloudy.testing.ProfileIsolationTest` runs against each installed product's actual `ConnectionProfiles` store. Without an explicit isolation run/phase it skips before accessing a Context path, receipt or store; this default skip is not a pass. Use the same canonical lowercase version-4 UUID in `cloudyProfileIsolationRun` for the paired run. Supply `cloudyProfileIsolationPhase` as `seed`, `verify` or `cleanup`; these are separate per-app invocations, not a reset operation.

| Paired phase | Required protocol |
| --- | --- |
| Seed both apps | Run `seed` once in Console and once in Desktop. Each app writes intent before its only Create, then retains a `READY` receipt bound to its exact generation, ID, revision and fixture fields. Existing receipts/fixtures refuse reseeding. Retain both results and receipts before verification. |
| Verify both apps | Immediately before **each** app's `verify`, obtain the peer's actual owning `run-as` working directory and match it to its reported package data paths, accounting for multiple users. Successfully read its exact absolute `no_backup/connection-profiles-v1/profiles.db` pathname in the peer's owning UID context. Preserve that successful read plus its current `READY` receipt/row privately. Supply the SHA-256 of that exact pathname as `cloudyProfileIsolationPeerPathSha256`. Repeat the witness in the opposite direction for the other app. |
| Cleanup both apps | After reviewed completion or an explicitly reviewed aborted run, run `cleanup` separately in each owning app. It uses the exact `READY` generation/ID/revision token and checks fixture fields before deletion, verifies that unrelated profiles remain unchanged, and only then removes that app's receipt. A stale, changed, missing or incomplete fixture fails closed. |

The pathname digest is lowercase 64-character SHA-256 over the exact UTF-8 pathname, without a trailing newline. It is not a database-content hash and does not itself prove the required owner-context read occurred. Keep the external witness alongside the phase result. A guessed path, `exists()` check, absent file or digest reused from an earlier attempt is insufficient. If the test's peer-path digest differs from the externally witnessed actual pathname, verification must fail rather than accept an inferred storage layout.

The revised probe first opens its own real database with `Os.open` and confirms a nonempty regular file as a positive control. Peer observations are reported separately:

| Observed peer result | Interpretation |
| --- | --- |
| `EACCES` / `EPERM` | `PERMISSION_DENIED`, with the observed errno retained |
| `ENOENT` | `NOT_VISIBLE`, eligible only with the matching successful exact-path owner witness above; this is not an arbitrary missing-path pass |
| Open succeeds | `READABLE`, a failed isolation check |
| Another error or failed own control | Verification fails; no isolation conclusion |

The result records accessibility from the probing app's context. It does not establish which Android mechanism caused the observed errno. Verification also checks the owning catalog's exact fixture and absence of the peer's run label. Retain `cloudyProfileIsolationOwnPathSha256`, `cloudyProfileIsolationPeerPathSha256`, own-access, peer-access/errno and phase status with the app/build/run identity. A per-phase `PASSED` status is not completion of the paired protocol.

Receipts live privately at `noBackupFilesDir/profile-isolation-qualification/receipt.bin`. The accepted run03 removes both original fixtures and their live receipts only after successful verification and exact cleanup; the initial verification failure, host-preflight failure and retained run evidence remain historical. For any future interrupted or failed run, preserve its exact fixtures/receipts for reviewed continuation; do not rerun `seed`, delete a receipt or reset app data to obtain a clean result. Keep vault authentication and credential isolation separate: these phases contain only non-secret endpoint metadata and require no password, key reset or authorization prompt.


## Native SSH trust-manager Activity cases

`ConsoleSshTrustActivityTest` and `DesktopSshTrustActivityTest` inherit four actual own-app ledger cases from `SshTrustActivityContract`: exact inspection/Cancel/confirmed revocation, stale confirmation after a real replacement, unconfirmed-dialog recreation, and a real admitted revocation across controlled Activity recreation. Run only the chosen exact class in its owning product with matching app/test APKs. `SshTrustTestQueue` provides a bounded scheduler gate around the production executor; it does not simulate outcomes. The standalone `SshTrustUiStateTest` tests the custom Bundle/Parcel schema/owner/pending record. The [accepted result](../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager.md) binds17 actual cases:3 platform plus4 trust/3 workspace per app.

Each trust case writes a private receipt before mutation at `noBackupFilesDir/ssh-trust-ui-qualification/<runUUID>.json`, with exact owner/package/UID/generation, baseline digest and intended/independently confirmed public fixture state. The case owns unique `.invalid` peers. Cleanup closes the Activity, releases gates and proves worker/main settlement before locking the existing permanent anchor and opening SQLite. It validates exact schema/metadata and the complete baseline plus confirmed fixtures, transactionally deletes only matching eight-field fixture rows, verifies the baseline before commit and checks it again through the production store. It retains permanent anchors and metadata; this is test-only disposal, not a product reset/delete API.

If settlement, catalog comparison or cleanup fails, preserve the run's exact fixture and receipt for leader review. Do not reseed, clear application data or delete anchors to make the next attempt pass. Successful cases remove receipts only after restoration is established. Custom status0 cleanup reports are evidence events, not additional JUnit completions; count successful class/test completions separately. The accepted run has eight exact cleanup reports, preserved original profiles/vault receipts/boot identity, three test packages removed and both products stopped. No password, authentication prompt or remote host is required. Clipboard, physical input, process death, trust-file isolation and engine/authentication remain separate qualification.

## App appearance and density

`ConsoleDisplaySettingsActivityTest`/`DesktopDisplaySettingsActivityTest` use the real owning-app preferences and UI to select each choice and verify Activity recreation. `ConsoleWindowDisplayTest`/`DesktopWindowDisplayTest` assert live Light/Dark × three-density changes preserve window/task/Activity identity and native draft/selection. Console additionally captures six actual root viewports and clipped control bounds in a fresh private `files/display-review/<UUID>/`; Desktop has behavior assertions only. These ordinary cases require no authentication or backend.

Each case reports the exact effective baseline before mutation and restores it through explicit production commits in `finally`. `EXACT_BASELINE_CONFIRMED` is a value receipt, not byte-identical XML or initial key absence: the accepted run creates new XML with explicit System/Comfortable defaults. Never delete production preferences to regain absence. If persistence/cleanup is uncertain, preserve the run and inspect before retrying. Parent copies/hashes each exact capture file before removing only that owned capture directory. Custom status0 receipts are not extra JUnit cases. The [accepted result](../../qualification/results/P1-S2/2026-10-16-native-display-settings.md) records12 cases, including four standalone storage cases and four selected profile/trust regressions.
