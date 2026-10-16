# Vault metadata-only Activity recreation

**Date:** 2026-10-16. **Outcome:** explicit metadata-only observation passed once per app; default non-opt-in invocation skipped without creating a receipt. No prompt, AES, key generation or credential enrollment/use is qualified by this run. P1-S3 remains open.

## Artifact and execution boundary

The parent installed Console and Desktop **0.1.3/code4**, using the frozen review01 bytes bound by the [harness build manifest](2026-10-16-vault-harness-build.json) and [build report](../../../docs/06-validation/2026-10-16-vault-harness-review.md). Source remains unchanged from `64b8437` for this execution. The historical build-only manifest is preserved as its original checkpoint; this report records subsequent device execution.

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Console app | 31,528,288 | `b9b49be1a90a9ccd81b64f2499144af243a63e3dd206dce44aa6defc03766914` |
| Console androidTest | 2,768,609 | `9d0d29ffac9ac567fc2750e545df5b42b3bca782e50d87918020fb04dd25c046` |
| Desktop app | 31,528,292 | `90b963fae8a95b005fc8fe9bf98d9042ebe7ab8bd7ef1ea9bf34c621d07b170e` |
| Desktop androidTest | 2,768,613 | `b2c05313f9944959ca54dcceb512496a47435ddb55a4c42700eafdbddac0b2a3` |

Actual hardware is HP Googlebook14, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. No device serial or unrelated inventory is published.

## Accepted observe02 result

Private evidence is `qualification/results/private/foundation/2026-10-16-vault-observe-02/`. `result.json`, timestamped **2026-10-16T07:29:18.627087Z**, records both installed versions, unchanged boot ID/count and empty final app PID observations.

For each app, `*-default-skip.log` records assumption status **-4** because explicit manual opt-in was absent. Despite the runner's generic “OK (1 test)” trailer, this is a skipped case, not a passing vault operation. `*-receipt-after-skip.log` and the result record establish that default invocation created no receipt.

Explicit `*-observe.log` then records **one passing test per app**, with status0. Each `*-receipt-after.log` contains two actual `OPEN / RESULT / METADATA_ONLY` events: initial Activity creation and Activity recreation. Both events retain the same per-app process identity. Each receipt has `saveAttempted=false` and `completedDeletion=false`.

| App | Metadata OPEN events | Save attempted | Final `*-receipt-after.log` SHA256 |
| --- | ---: | --- | --- |
| Console | 2 | false | `717263f0ca05cb789b7ec3fa9a5703b0a55c3b43d1307f2ba64718fac92c7d5c` |
| Desktop | 2 | false | `2fccec87aefc4fd32f04bc7b5cea658851278b8510a41afb53a646ccb029a744` |

The per-app install logs, observation logs, receipt-after logs and `result.json` retain the execution trail. Both `*-remove-test.log` files report successful test-package uninstall. The parent force-stopped the apps; `*-pid-post.log` is empty and the result records empty app PIDs. Successful uninstall receipts are the retained test-package cleanup evidence; no additional package-enumeration absence check is claimed.

## Preserved failed preflight

The preceding `2026-10-16-vault-observe-01` attempt failed in the host preflight before invoking tests. `adb exec-out` returned exit0 while reporting a missing receipt file; the operator interpreted that as an existing receipt and stopped. Only the Console app/test package had been installed at that point. This was an operator preflight failure, not a vault or authentication failure. Its artifacts remain preserved.

Observe02 uses `adb shell` exit status together with the missing-file message for the absent-receipt check. The correction does not alter product source, fabricate a receipt or erase the failed attempt.

## Qualification limits

This establishes opt-in admission behavior and metadata-only **Activity recreation**, not process death/restart, saved-secret recovery, credential isolation, Android Keystore backing or authentication. Separate per-app metadata/process observations are not an app-to-app secret-access test. No password, authentication prompt, AES operation, key generation or network operation occurred.

The earlier [UI entry](2026-10-16-vault-ui-entry.md) and [storage tests](2026-10-16-vault-storage-tests.md) retain their separate scopes. The human readiness/authentication question remains pending; this no-prompt run neither answers it nor constitutes a new prompt request. Real prompt/AES enrollment and scoped use require separately correlated execution and user approval. No capability or stage gate is promoted.
