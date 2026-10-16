# Handoff caller checks and durable request consumption

**Date:** 2026-10-16. **Outcome:** strict compilation/Lint, nineteen actual Android caller/ledger cases and two explicitly separated process phases pass. One default process-fixture invocation skips by design and is not a pass. No signed cross-app endpoint, enrolled-resource registry or successful navigation is implemented by this increment. P1-S3 remains in progress.

## Implemented boundary and source review

The [admission contract](../../../docs/02-architecture/handoff-admission.md) documents the exact caller and replay behavior. `HandoffCaller` obtains the Binder UID synchronously and checks the exact receiver/counterpart packages, their enabled state, exclusive UID membership, shared-UID declarations and signatures. `HandoffReplayLedger` uses the receiving app's credential-encrypted no-backup directory, checked immutable initialization markers and real Android SQLite transactions. It commits request consumption before returning a reservation, with bounded capacity, nonblocking lock admission, nanosecond expiry and a persisted clock floor. The original envelope cannot be resurrected by pruning followed by clock rollback. Indefinite uniqueness of a UUID reused with different timestamps is explicitly outside bounded retention.

The parent reviewed both direct Codex GPT6.1-Sol authors' actual source, not just their completion messages. A separate direct reviewer checked the caller utility and actual APIs. A second review of the ledger/process test confirmed the supplied device-protected context could lose its storage flag through `applicationContext`; the parent corrected this before device execution. The relevant case subsequently passes. The worker's completion report did not itself establish that repair. Local-window and vault-profile records remain distinct from genuine resource registration.

A final independent direct Codex GPT6.1-Sol review found no actionable acceptance issues. It checked all five source hashes, all three APK hashes, the actual successful build and zero-issue Lint reports, nineteen regular test completions, the separate default skip, both process-phase passes with the intervening no-PID observation, and cleanup/unchanged-boot evidence. It also confirmed the public contract retains the unqualified endpoint, resource-navigation, simultaneous separate-process contention and power-loss boundaries.

## Compiler and Lint

The [build manifest](2026-10-16-handoff-admission-build.json) binds five source/test files and three APKs against source base `931cd27680bc534f8a1c8227abb58a221a4681c6` plus this increment. Frozen artifacts, reports and failures are preserved under `.cache/handoff-admission-01/`.

Build01 fails because `OsConstants.O_DIRECTORY` is absent from the public SDK. The parent confirms the actual selected SDK surface and replaces it with public `Os.open(O_RDONLY)`, descriptor `fstat`/`S_ISDIR`, checked directory `fsync` and `close`. Build02 fails strict Kotlin warnings on heterogeneous SQL bind-array inference. Build03 fixes one array but retains two of those warnings; build04 explicitly types all three arrays without suppressing warnings.

**Build04 succeeds in25seconds,243 tasks,44 executed.** It builds both product APKs and the standalone platform instrumentation APK and runs Console, Desktop and platform Lint. All three Lint XML reports have zero issues. Dependencies, versions, verification hashes, manifests and permissions are unchanged. Existing domain/codec/identity/design tests are not claimed rerun; this change uses the earlier accepted unchanged results under D12.

## Actual Googlebook execution

Hardware: **HP Googlebook14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. The standalone test package and instrumentation target are both `io.github.baitian6641.cloudy.platform.test`. The new product APKs are built only; installed Console/Desktop remain the preserved0.1.3/code4 manual-vault artifacts and are stopped before/after this run.

Test APK:32,323,803bytes, SHA256 `b34baef1223505688b8d6dfb6c61108b79a41527adc23fb17169e2019dba05db`. The private evidence is `qualification/results/private/foundation/2026-10-16-handoff-admission-01/`; the finite parent runner is `.cache/handoff-admission-01/run_device_01.py`.

| Actual execution | Result and scope |
| --- | --- |
| `HandoffCallerTest`,5 cases | Pass. Actual local Binder/PackageManager reads, self-call rejection, unsupported receiver and spoofed context rejection. No positive counterpart or wrong-signature cross-app result is claimed. |
| `HandoffReplayLedgerTest`,14 cases | Pass. Real SQLite/filesystem, concurrent instances, duplicate/reopen, expiry/capacity, pruning/rollback, changed payload with retained UUID, finite UUID reuse, missing/corrupt artifacts, interrupted bootstrap, main-thread/lock contention, metadata types and device-protected storage rejection. |
| `HandoffReplayProcessTest`, default invocation | Assumption skip before fixture creation; kept separate from passes. |
| Process phase `seed` | Pass. Real reservation and a checked test receipt are persisted. The parent then force-stops the standalone package and independently observes no remaining package PID. |
| Process phase `verify` | Pass in a different process. The saved UUID is duplicate; a later reservation prunes the expired original; the original is expired; returning the controlled receiver clock below the committed floor yields clock rollback. The test-owned fixture is removed only after all assertions pass. |

The regular run records19 successful test completions with `OK (19 tests)`. Each explicit process phase records its own successful one-test completion and terminal instrumentation code−1. Actual process identities are kept in the private result; different PIDs and the independent seed-stop observation support the process-restart claim. The clock is a test-source injection into the real ledger: no system date/time is modified and no remote effect is simulated. Concurrent threads/instances and same-process file-lock contention are tested; simultaneous separate-process contention remains an integration case.

The result closes at **2026-10-16T08:14:30.860865Z**. Test-package uninstall succeeds and a separate package-path check confirms absence. Both product PIDs remain absent. Boot ID and boot count are unchanged. The tests launch no product Activity, authentication prompt, credential operation, remote server or network connection.

## OMP review disposition

The authorized finite `zai/glm-5.3` review completes in90.904seconds, exit0, with actual provider/model matching and zero tool events. Its packet contains selected source only. Its snapshot predates the parent's compiler and storage-context corrections; the direct review and actual final build/device evidence cover those changes. Raw packet/events and the explicit leader disposition remain under `.cache/omp-workers/2026-10-16-handoff-admission/security-01/ADMISSION-REVIEW-001/`.

The leader rejects all four proposed findings after checking the code and primary references:

1. Reinitializing when the anchor survives but directory/marker are missing would erase replay history after established-store loss. This condition is intentionally indistinguishable from interrupted bootstrap and fails closed.
2. `PackageManager.checkSignatures(int,int)` is a real publicAPI5 overload; the suggested alternative API is not the selected public interface.
3. The pinned AOSP generator assigns `sharedUserId` without field-specific retrieval flags. Broadening lookup to uninstalled packages/cross-user results is unwarranted.
4. `SELF_CALL` is a denial, not an acceptance path; only `VERIFIED` denotes caller verification.

These corrections are retained for context continuity. A completed worker run is neither an accepted patch nor executed-test evidence.

## Remaining integration

Expose the explicit signature-permission endpoint only with actual receiver-owned resource resolution and action/lifecycle policy. Test real same-signed and wrong callers, delegated handles, missing sibling, package replacement, cancellation, expiry immediately before an effect, revocation and honest destination presentation. Never turn local window or credential IDs into implied host authority. Register real Console/Desktop/files destinations as their product backends become usable; no generic page or empty list completes those actions.

Physical power-cut persistence, privileged snapshot rollback and whole-app-data reset are outside this run. Actual product credential authorization remains a separate pending human-readiness check. No P1 stage, platform capability or suite release gate is closed here. All seven hosted workflows pass for the preceding exact commits `b2966b9` and `931cd27`; this new source increment requires its own exact-commit CI after push.
