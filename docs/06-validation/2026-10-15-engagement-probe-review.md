# P0-S2 engagement collector: source and dependency review

**Reviewed:** 2026-10-15. **Scope:** diagnostic probe 0.3.0 / version code 3 / report schema 3. This increment calls the published AndroidX engagement API; it does not implement Console or Desktop. P0-S2 and CAP-PLATFORM remain open. The containing source commit identifies the implementation; later device evidence must bind that commit to the installed APK.

## Ownership and accepted design

The implementation agent owned Kotlin, tests and the probe README. A separate Android API reviewer inspected the controller, Android adapter, Activity/recorder integration and tests. Another agent audited runtime dependency licenses and assembled exact notice texts. The primary agent read the source and tests, checked dependency provenance and retained responsibility for build configuration, integration acceptance, device control and Git.

The [engagement contract](../03-platform/googlebook/p0-s2-engagement-probe-contract.md) was refined against the published alpha before implementation. The code uses one process-owned HandlerThread with a prepared Looper, one serial registration/removal queue, and the same tracker/Consumer pair for removal. An Activity's stop immediately invalidates its subscription epoch; delayed delivery snapshots current state and cannot restore an earlier epoch. Stopping the first window leaves the process executor available to another window and to cleanup work.

The controller reserves one of eight slots before queuing work. Failed cleanup retains its slot because the library may have removed its adapter-map entry before backend cleanup throws; a repeated removal cannot prove recovery. Registration failures attempt removal of the exact original pair. Errors retain phase and exception class without publishing exception messages. The report callback captures only the process recorder and immutable identity, avoiding retention of an Activity through a failed backend cleanup.

Per-generation history is limited to 32 events, mode names to 16 entries of 80 characters, and report/UI delivery is coalesced. Duplicate callbacks update bounded counters without producing continuous writes; Refresh samples those counters. Reported values, an empty reported set, pending/no value and failure remain distinct. The adapter copies actual library modes; no result is manufactured and no mode changes density, font scale or desktop controls. Existing per-window state and saved-state behavior remain independent of the diagnostic recorder.

## Dependency and license checks

WindowManager **1.6.0-alpha05** is isolated to the probe. Core **1.8.0** is declared directly because Window exposes its Consumer type in public signatures but publishes Core at runtime scope. The selected toolchain is unchanged. All **350** prior artifact digests remain present; **39** new digests matched separate downloads from canonical Google Maven/Maven Central URLs. An empty dependency cache resolved successfully with checksum verification enforced. The final source compiles against the actual public methods, without reflection or private APIs.

The runtime license audit covers **18 binary artifacts** and five metadata-only coordinates. Debug/release runtime coordinate sets agree. The primary agent independently checked binary hashes against verification metadata, all ten indexed notice-file hashes and six verbatim upstream license/notice downloads. The bundle includes Kotlin stdlib's embedded BSD-3-Clause/Boost terms and upstream coroutines NOTICE, which the Maven POM summaries alone did not capture. See [notice provenance and packaging procedure](../../qualification/probes/device-baseline/THIRD_PARTY_NOTICES.md).

The first compiler/assembly/test run passed but lint rejected the newer-Core version advisory. The explicit Core declaration now carries a local `GradleDependency` suppression for this reviewed diagnostic pin. The pre-existing wrapper-version exception remains scoped to the wrapper properties. Source/resource checks and other dependency advisories remain enabled. This pin is not a product dependency recommendation.

## Acceptance cases and limits

The controller tests execute the production lifecycle policy with test-only queues/backends. They cover stopped-before-registration, delayed accepted callbacks across stop/start, late old callbacks, first-window removal while another stays subscribed, partial registration failure, sticky cleanup failure, blocked-backend admission limits, untracked/destroyed owners, duplicate floods, history overflow and explicit default-method failures. Ledger tests cover stale epochs/generations and historical cleanup without reviving a closed record.

These tests establish policy behavior under controlled ordering. They do not establish actual AndroidX callback delivery, physical input transitions or the absence of all backend leaks. Follow E02–E05 on the real Googlebook, including two-window ownership, recreation, stop/return and settled revisions. Do not substitute test callbacks for device evidence or label a library failure as missing HP hardware capability.

## Build and packaging outcome

The implementation agent ran `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` with normal dependency verification and compiler warnings as errors. The primary agent inspected the final logs, all test XML, lint XML, merged manifest and actual APK package metadata.

- Kotlin compilation and APK assembly passed. **31 JVM tests passed, zero failures/errors/skips** (17 existing tests plus 12 controller and two ledger tests).
- Android Lint reported **zero issues** after the declaration-local version-advisory exception described above.
- The actual debug APK identifies package `dev.cloudy.qualification.baseline`, version **0.3.0 / 3**. SHA-256: `1c6959501df43e712a5a90c227b91577c954f8b67c0124127ac9b94759667b16`.
- All **11** packaged notice assets match their source bytes exactly. The APK and merged manifest add no permissions, services, receivers or providers. Core adds its component factory; Window declares optional extension/sidecar libraries.
- [Packaging audit](2026-10-15-engagement-package-audit.json) binds these checks and notice hashes to the actual APK. The packaged provenance file's pending status describes its earlier source-collection snapshot; this external audit records subsequent binary inspection without mutating the packaged bytes.

Private logs are `.cache/bootstrap/engagement-agent-build.log` (initial lint advisory) and `.cache/bootstrap/engagement-agent-build-final.log` (passing final run). Fresh-cache resolution and canonical checksum audit logs are retained in the same ignored directory. The independent notice reviewer also confirmed the final APK identity, byte equality, manifest, test XML and lint output. Repository Python AST/Ruff, nine planning regressions, documentation/schema checks and specimen JavaScript syntax checks passed. Two file-specific Git whitespace attributes preserve the already-reviewed notice bytes, including their final separator lines; other whitespace checks remain enabled. No device result is implied by build acceptance.
