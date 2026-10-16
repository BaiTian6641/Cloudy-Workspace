# Receiver diagnostic privacy qualification

**2026-10-16; three actual diagnostic methods pass, with the protected baseline preserved.** This finite result covers a test-only receiver failure diagnostic, strict test-artifact build, exact test-package refresh and the three sanitizer methods listed below. It does not include a new receiver PREPARE/VERIFY/CLEANUP, normal Open or positive handoff attempt. [The manifest](2026-10-16-handoff-receiver-diagnostics.json) binds reviewed source/build/helper hashes and sanitized private-evidence digests.

The actual device is **HP Googlebook14**, Android17/API37, build **CL3B.260622.276.R2**, incremental16560795, arm64-v8a. Both product APKs remain **0.1.14/code15**, byte-identical to the accepted observer build02; neither product was installed by this refresh. No credentials, private inventory, account identifiers or device serial appear in this result.

## Changed source and build

Exactly three instrumentation-source files changed: `SshFilesActivityContract.kt`, `FilesHandoffReceiverCheckpoint.kt` and `FilesHandoffReceiverCheckpointCodecTest.kt` under `testing/foundation/src/androidTest`. The new failure assertion emits a fixed phase, allowlisted stage/category/source and bounded line number. It copies no original message, cause, suppressed throwable, arbitrary class/file, path, SQL or values. The handoff branch preserves its first safe failure through outer wrapping/finally. Checkpoint lock release still attempts all existing releases, preserves a primary failure and throws a cleanup-only failure. Store assertions, cleanup authority and replay behavior remain unchanged.

Frozen diagnostic build01 binds **231 inputs and five APKs**. Raw Gradle output says **BUILD SUCCESSFUL in29s**; both owning Console/Desktop instrumentation consumers compiled. The unchanged platform instrumentation compile was **UP-TO-DATE**, with its exact APK hash unchanged. All four archived debug Lint XMLs contain **zero issues**. Source inputs remained unchanged. No new JVM/release suite was run, and compilation alone did not execute the new methods. Independent source and helper reviews are hash-bound in the manifest.

## Actual refresh and failure preservation

The first refresh entry refused before mutation with `PACKAGE_USER_STOPPED`: the earlier naturally ended receiver instrumentation had left Desktop's PackageManager stopped flag false. Its failed receipt has an empty mutation list and remains preserved. Parent separately verified the full baseline, absent product processes/instrumentation/services and safe focus, then restored only the exact owning Desktop package's stopped flag. Baseline data remained unchanged. That metadata restoration is **not** evidence of old-operation or transport retirement; it clears no app data.

The next refresh used the independently reviewed contract with only a new output directory. It replaced exactly **three fixed test packages**, verified their installed byte hashes and retained both unchanged product APKs. Its terminal phase is `TEST_ARTIFACT_REFRESH_PRESERVED`, `setupPassed=true`, `passed=false`: setup success does not mean handoff success. Full before/after protected baseline equals the accepted setup. Partial failures have no rollback, broad cleanup or automatic retry.

## Actual three-method result

The Console owning test runner executed exactly these `FilesHandoffReceiverCheckpointCodecTest` methods:

- `receiverDiagnosticExcludesHostileMessageCauseStageAndFrames`: synthetic hostile messages, cause/suppressed data, unknown phase/stage and invalid source frames cannot escape the fixed diagnostic.
- `receiverDiagnosticUsesFirstExactProjectFrameOnly`: a deceptive class prefix is rejected and the first exact allowed nested project frame supplies the fixed source/line.
- `previouslySanitizedPrimarySurvivesOuterCleanupClassification`: an already safe primary keeps object identity and its original diagnostic through outer classification, with no attached cause/suppressed throwable.

Raw instrumentation has **three starts and three successful completions, zero skips/failures**, empty stderr, `OK (3 tests)` and terminal instrumentation code **-1**. The parent terminal receipt is `THREE_PRIVACY_TESTS_PASSED_BASELINE_PRESERVED` with `passed=true`. This is a real sanitizer execution result using synthetic control inputs; it is not a fabricated product response or actual receiver SQLite/lifecycle case.

Raw before/after/final snapshots preserve both apps' protected file hashes, three stores' rows/metadata/generations, replay state, typed preferences and UIDs. Boot/system settings and existing reverse mappings match. Installed product/test hashes match the frozen build. After the tests were terminal and product processes already absent, parent restored only Console's test-owner stopped flag and confirmed the final baseline/installed identities. Final checks find no product PID, active instrumentation, product service or product/auth focus. Stopped flags remain separate from operation-retirement proof.

These three methods do not identify the cause of the earlier PREPARE failure, exercise actual SQLite release failure, run the passive observer or establish original Activity/call/model/session continuity. There is no host authentication, nested list/read, ordinary Open, receiver cleanup, screenshot or physical speech/render pass in this snapshot. Future measured attempts require their own explicit parent result and source/artifact binding. No capability or stage is promoted.
