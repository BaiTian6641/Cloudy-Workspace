# Bounded handoff codec and domain validation

**Date:** 2026-10-16. **Outcome:** source review, strict compilation, Lint, eight new domain JVM cases and thirteen actual Android codec cases pass. This is a data-validation increment. No authenticated IPC endpoint, request ledger, resource registry or cross-app navigation is implemented or qualified. P1-S3 remains in progress.

## Source and build

The [runtime profile](../../../docs/02-architecture/handoff-runtime-profile.md) preserves the existing nine-field v1 schema and defines message, timestamp and lifetime bounds. The private-constructor domain model normalizes UUID/time values and separates freshness from authorization. The Android codec uses public `JsonReader`/`JsonWriter`, explicit token checks, strict UTF-8, exact decimal version comparison and fixed error codes. A narrow JSON string guard addresses reviewed AOSP raw-control/escape acceptance; Android retains structural parsing. No new library, manifest permission, API floor or dependency lock/checksum is introduced.

The [build manifest](2026-10-16-handoff-codec-build.json) binds all four new source/test files and three APKs against base `88d2c27926f68c7d47479b73b6f4be8657d61c6f` plus the then-uncommitted increment. Frozen artifacts and reports are under `.cache/handoff-01/build-01/`. The parent reviewed both workers' source and tests before execution.

The strict Gradle command builds both product APKs, runs both app Lint tasks plus platform Lint, domain tests and platform instrumentation compilation. It succeeds in30seconds with245 tasks,51 executed. Kotlin warnings are errors; all three Lint XML reports contain zero issues. Domain results are **eight handoff tests** and **five existing window tests**, zero failures/errors/skips. Unchanged identity/storage/design-system results retain their prior evidence rather than being claimed rerun here.

## Actual Android codec execution

Hardware: **HP Googlebook14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Only the standalone platform test APK is installed and executed. Its package and instrumentation target are both `io.github.baitian6641.cloudy.platform.test`; its actual merged manifest uses the existing AndroidJUnitRunner. Product APKs built in this increment are **not installed**; the previously qualified0.1.3/code4 app bytes remain installed.

Test APK:32,323,803bytes, SHA256 `f6ff8c2dc5ce1a9fcd11b089b5190373684f3876924a5638dbcbfe1405c1e673`. The explicit test class is `io.github.baitian6641.cloudy.platform.HandoffJsonTest`.

The private run `qualification/results/private/foundation/2026-10-16-handoff-01/` records13 starts and13 successful completions, `OK (13 tests)` and terminal instrumentation code−1. The worker's initial statement of14 authored tests was a counting error; source and actual execution both establish13. Cases cover canonical output, offsets/UUID normalization, escaped names, duplicate/unknown/missing fields, coercion/nesting rejection, numeric precision, UTF-8/BOM/size bounds, malformed/trailing documents, fixed errors and string-guard behavior. Fixtures are synthetic protocol data passed through the actual device parser; no remote server or successful resource handoff is simulated.

The result closes at **2026-10-16T07:47:02.452347Z**. Test-package uninstall reports success, a separate package-path check confirms absence, and boot ID/count match the preflight. No auth prompt, credential operation, remote connection, host fixture or product Activity is started by these tests.

## Independent review and delegation

Two direct Codex GPT6.1-Sol workers implemented disjoint domain and codec files. A separate direct coworker reviewed the source and actual evidence; the parent inspected the diffs, selected Android/AOSP/RFC references, compiler/Lint output and device results. No consequential finding remains within this bounded increment.

The prior OMP Kimi proposal did not supply code: its first launch failed before a request on a daemon-path write, and its explicit retry reached actual `kimi-code/k3` but exceeded the150second deadline with no answer. Those terminal attempts remain preserved. Implementation was explicitly reassigned to direct Codex workers.

A narrower authorized OMP `zai/glm-5.3` security review completed in75.093seconds with no tools and no blocking code finding. The leader rejected two explanatory inaccuracies: the review reversed offset signs in its year-boundary examples, and its wording about accepted raw-string quirks did not describe Cloudy's rejecting guard. The implementation and tests already used the correct behavior. OMP output is source-review input, not executed-test evidence. Packets, routes, source hashes and leader dispositions remain under ignored `.cache/omp-workers/2026-10-16-handoff/`; no credentials or private device transcripts were sent.

## Remaining acceptance

Implement receiver-owned signature permission and caller validation, actual enrolled-resource resolution, recipient/action policy, persisted replay admission and clock/lifecycle handling before exposing a working handoff action. Then exercise real signed counterpart navigation and denial cases. Schema/codec success is not a security or suite gate. Product authentication readiness remains a separate pending human question; this no-prompt test does not answer it.
