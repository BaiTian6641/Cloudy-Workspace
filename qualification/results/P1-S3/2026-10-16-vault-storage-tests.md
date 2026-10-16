# Vault storage tests on Googlebook

**Date:** 2026-10-16. **Scope:** P1-S3 library storage/recovery tests, not actual protected product credentials. **Result:** 18 executed tests pass, zero failures/errors/skips. The test package uses real Android file APIs and test-only software/fault-injection crypto. No production Keystore key or system authentication prompt was exercised.

## Target and artifacts

HP Googlebook14, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`; explicit USB ADB target selected and revalidated. Private identity, pre/postflight, test XML/logs, source hashes and manifest are retained under `qualification/results/private/foundation/2026-10-16-vault-01/`. Private device identifiers and raw logs are excluded from Git.

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Console0.1.2/code3, built only | 31533970 | `e80db122f00f62f09819fea760c1f91e9e2c50659321b93c9131ef43255128f6` |
| Desktop0.1.2/code3, built only | 31533978 | `f4f6f7c4f39c21574f33136aaa9c40fbdba52c06f46bd75b85db0d6c3745808e` |
| Identity Android test APK | 11044735 | `7eb8a810d1a19ca71a4731609191619d5572a96320b6c1cd039e73238aef16ae` |

Actual connected XML SHA256: `460dfccd006c46170e104455daa97a73c1bec22392760960423afa1b00c92506`. The immutable evidence manifest binds this run to its exact source files and APK bytes. A later build must use a new result identity.

## Executed checks

- Twelve storage tests: incomplete creation, pending/completed deletion and retry/purge, residual-key refusal, malformed/orphaned records, capacity bounds, main-thread guard, wrong owner and input closure, cancellation during create/use, authenticated binding mismatch, scoped consumer closure/replay denial, and verified absence across keys and AtomicFile remnants.
- Five profile tests: actual file reload/removal, owner/binding immutability, bounded strict decoding and corruption refusal, capacity, and orphaned partial-file refusal.
- One key-alias derivation test. It does not generate or qualify an Android Keystore key.

The parent invoked only `:identity:connectedDebugAndroidTest` on the explicit target as part of strict build04. Existing product Activity suites were compiled but not rerun in this increment. Gradle's test-package cleanup completed; postflight finds no identity test package. Installed Console/Desktop remain0.1.1/code2, and boot ID/count are unchanged. No fixture/listener or authentication question remains active.

## Acceptance boundary

This accepts the tested Android storage/recovery integration with test crypto. It does not qualify actual AES backing or auth enforcement, product management UI, credential entry/consumption, app-to-app isolation, physical keyboard input, backup/transfer exclusion, remote login or P1-S3 completion. Follow the [source review](../../../docs/06-validation/2026-10-16-product-vault-review.md) and [implementation entry](../../../docs/03-platform/googlebook/product-vault-entry.md) for the next product tests.
