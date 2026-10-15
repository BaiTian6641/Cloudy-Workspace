# P0-S2 window probe: code review and build acceptance

**Reviewed:** 2026-10-15. **Scope:** native diagnostic APK version 0.2.0 and report schema 2. This accepts the source increment for device experiments; P0-S2 and CAP-PLATFORM remain open. The containing Git commit identifies the reviewed source.

## Review ownership and corrections

The implementation agent owned Kotlin, resources, tests and the probe README. A separate Android API reviewer inspected the final code and test design; another reviewer checked test boundaries. The primary agent read the implementation, reconciled official-source research, and ran the complete build. Only the primary agent controls the connected device and Git.

- **Resolved P1:** Kotlin predicate `removeAll` on `CopyOnWriteArraySet` invokes unsupported iterator removal and could crash on Activity stop/destruction. The reviewed correction uses the collection's supported `removeIf` through `ListenerRegistry`. Two tests exercise the real helper, including idempotent removal, preserving the other window's subscription and independent snapshots. The API-24 method is covered by minSdk 35. See the [official collection contract](https://developer.android.com/reference/java/util/concurrent/CopyOnWriteArraySet).
- **Resolved contract mismatch:** observations now include actual `Activity.isTaskRoot()` alongside task identity.
- **Resolved lint failure:** capacity text uses separate numeric labels rather than count-dependent English grammar. No new lint suppression was added.

The reviewed implementation separates Activity-local Bundle state from process-scoped diagnostic observations. New document tasks receive no copied identity/state. Recreated generations replace only their own record; stale callbacks cannot overwrite or close the replacement. Stopped Activities retain live status. The eight-record diagnostic policy rejects excess live registration and evicts only closed history, with visible rejection/omission accounting. It is not an Android task limit.

All controls and diagnostics are inside one scrolling viewport. Insets preserve system captions. Observation deduplication and viewport-only layout triggers prevent report text from recursively creating geometry changes. Save status requires bounded byte-for-byte AtomicFile readback. The registry and event history are bounded; no protocol, codec-performance or USB operation is simulated.

## Executed build checks

The pinned toolchain and dependency verification remain unchanged. The primary agent ran `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest` with the documented workspace caches, without verification-generation flags.

- Kotlin compilation and APK assembly passed with warnings as errors.
- Android Lint reported **No issues found**. The existing narrowly scoped Gradle-version advisory exception remains the only exception.
- **17 JVM tests passed, zero failures/errors/skips:** three bounded-history, five window-state, seven ledger and two listener-registry cases.
- Final Gradle result: **BUILD SUCCESSFUL**, 49 actionable tasks (23 executed, 26 up-to-date).

Private build log: `.cache/bootstrap/window-probe-checks.log`. Test XML and lint outputs live under the ignored probe build directory; their host metadata is not committed. Device acceptance and the eventual source/APK identities belong in the separate P0-S2 run record.

## Acceptance boundaries

The tests cover state restoration validation, independent marker state, counter overflow, task collisions, generation replacement, stopped/live capacity, closed eviction, bounded attributed history and immutable observation snapshots. They do not prove delivery of Android Bundles, visible desktop windows, real focus/caption behavior or persistence failure reporting on the device. In particular, the W06 save-failure clause requires its own real fault observation; a quiet revision check alone cannot satisfy it.

Follow the [window contract](../03-platform/googlebook/p0-s2-window-probe-contract.md), [VM investigation](../03-platform/googlebook/p0-s2-linux-vm-boundary.md), and [operation lifetimes](../03-platform/googlebook/p0-s2-operation-lifetimes.md). Source review and compiler success do not close process-death, service/timeout, engagement, physical input, protocol or release gates.

## Subsequent physical-device evidence

The reviewed source was committed as `13dce23dd4052163f22703752b16438ec81c8306`, pushed with matching remote SHA, and passed both hosted workflows. The separate [window run](../../qualification/results/P0-S2/2026-10-15-window-probe.md) records actual W01–W06 bounded observations. A second reviewer independently compared the public conclusions with retained probe reports, app-only UI nodes, permission restoration and exact revision sequences. Registry-capacity/stale-callback stress remains JVM coverage. The same-process window result does not establish process-death restoration or complete P0-S2.

The subsequent [process-restoration experiment](../../qualification/results/P0-S2/2026-10-15-process-restoration.md) independently verified PID absence and a new process before accepting existing-task Bundle restoration. Its initial no-effect kill attempt remains an inconclusive attempt. This additional diagnostic result does not qualify service lifetimes or remote-operation recovery.

Evidence integration review accepted both public manifests and reports: 45 window-run and 27 process-run private hashes matched, and all selected state/event projections were independently checked. The primary agent reviewed the evidence author's output and completed the process report; a separate reviewer then accepted that final report. Public files exclude raw device/process/user identifiers, unrelated UI content and command output. Final repository AST/Ruff, nine planning regressions, documentation/schema and JavaScript checks passed. The Android source remained exactly the already-tested source commit.
