# P0-S2 transfer implementation review

**Date:** 2026-10-15. **Scope:** finite HTTPS host fixture and Android UIDT diagnostic extension. **Status:** source accepted for bounded device qualification; compile/lint/JVM checks passed. P0-S2 and all feature capability gates remain open.

## Ownership and review

One delegated author implemented the host fixture and its tests, then the thirty new pure Kotlin tests. A second author implemented Android transfer state, I/O, journal, runtime, service and receiver. An independent reviewer checked public API contracts and both source boundaries. The primary agent implemented the native transfer screen and owned integration, source inspection, compilation, actual device operation, evidence review and Git delivery. No subagent success report alone changes a gate.

The official Android CLI/testing guidance and repository research workflow were read with the [project overrides](../07-reference/development-skills.md). No new Android dependency or test framework was selected. Sources G82–G94 extend API39/API41 without claiming device support.

## Host fixture checks completed during review

- The primary agent inspected generated-content routing, CA/server generation, bounded request workers, shutdown and private configuration handling.
- Thirteen real loopback HTTPS tests exercise independent expected bytes/hash, exact path/method/body/range restrictions, wrong CA/hostname rejection, file modes, config drift, overwrite refusal, throttling, interruption and stalled-peer cleanup.
- Python AST checked seven authored files against Python 3.10 grammar; Ruff and specimen JavaScript syntax passed. The full repository suite passed **22 tests**, including nine existing planning regressions.
- Independent review found that `BaseException` cleanup around worker start could return an admission slot twice if Ctrl-C arrived after the worker started. Rollback now catches `Exception`; normal teardown owns process interruption. A regression starts a real worker, injects `KeyboardInterrupt`, verifies tracked ownership and joined cleanup, and captures thread exceptions. The independent reviewer inspected the correction and all thirteen tests with no remaining actionable host-fixture finding. The primary agent reran the complete suite successfully after this correction.

The primary agent prepared an ignored, private 64 MiB fixture with a two-day certificate and a 512 KiB/s throttle. Preparation started no listener. Its expected generated SHA-256 is `5302fb4146dff35d2ed3784bc3c8f005e2d89c19c2c722387a1f265a8236ee1f`. This is setup evidence, not a Googlebook download result.

## Android review and verification

The primary and independent reviewer inspected the [transfer contract](../03-platform/googlebook/p0-s2-transfer-probe-contract.md) against implementation: typed configuration, scoped TLS/normal hostname validation, durable attempt ownership, recreation, process-loss reconciliation, cancellation fencing, assigned network and stop handling, stale notification actions, main-thread responsiveness and explicit error reporting. The delegated runtime author separately checked the primary-authored screen's weak-reference/visibility gate, native insets, scrolling and permission controls.

Review corrections:

- Reject string-coerced JSON numbers and invalid journal field types; catch checked `JSONException` and URI failures so malformed input becomes visible failure instead of leaving an IO worker stopped and initialization pending. Journal encoding is within the persistence failure boundary; refresh resets its queued flag in `finally`.
- Signal cancellation before queued disk/socket cleanup. A notification receiver now uses at most eight `goAsync` receipts with eight-second deadlines. Readback acknowledges the cancellation-request revision or exposes failure; it does not claim final worker exit or terminal cancellation. Cold-process receipt handling waits for startup reconciliation without replaying work.
- Recheck LAN permission before actual network work, snapshot the assigned network, update real notification/byte progress, and handle `jobFinished` errors. Stop callbacks invalidate later completion of the same execution.
- The first compiler run rejected a Java static constant accessed through a Kotlin subclass; the reference now names the framework `JobService` directly. Android Lint then flagged the static context holder. Runtime ownership moved into `ProbeApplication`'s existing lazy-owner pattern, with no added suppression.

The final pinned Gradle command `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --console=plain` passed: **49 tasks, 16 executed**, **61 JVM tests / zero failures or errors / nine suites**. The thirty new tests cover state/cancel/recovery policy, strict endpoint bounds, real temporary-file copying/reread, short/oversized input, retained existing parts, corruption and mutation during verification. They do not run Android JSON, AtomicFile, JobScheduler, notification or networking integrations.

The [package audit](2026-10-15-transfer-package-audit.json) records version **0.4.0 / code4**, APK SHA-256 `134cb6bc0d54eb69a3ba9956d6820c07913344022dd164977cf8453837683686`, and **7,113,925 bytes**. All eleven packaged dependency-notice assets match their source bytes. No dependency, lock or verification metadata change is needed. The merged manifest contains only the five intended network/UIDT/LAN/notification permissions, a nonexported transfer Activity/receiver, the nonexported `BIND_JOB_SERVICE` service, and the existing exported launcher. There is no foreground-service declaration or new provider.

The independent reviewer accepted this source for bounded qualification with no remaining blocking finding. The earlier 0.3.0 APK remains installed until a new deployment is recorded. T01–T06 physical cases are pending. Host/JVM tests do not prove Android TLS, LAN permission, UIDT scheduling, cancellation, file durability or product protocol behavior.
