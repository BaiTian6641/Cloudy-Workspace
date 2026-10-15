# Transfer probe 0.4.2: inconclusive completion audits

**Date:** 2026-10-15. **Scope:** diagnostic recovery correctness. **Source review:** accepted by the primary agent and an independent reviewer. **Device status at this source checkpoint:** 0.4.2 is built but not yet installed; prior 0.4.1 observations retain their original source/APK identity.

## Problem and resulting behavior

The 0.4.1 startup path treated every `IOException` during re-verification as a content mismatch. A missing/unreadable file or the cooperative audit deadline therefore replaced a recorded completion with a corruption claim without having measured different bytes.

Version 0.4.2 distinguishes verified content, measured mismatch, and unavailable verification. Metadata reads use the throwing `Files.size` API instead of `File.length`, which can return zero after a failed metadata read. Actual length/hash disagreement still invalidates the recorded completion. Missing paths, read errors and deadline interruption preserve the prior completion and verified digest as **historical evidence**, display `saved-file-audit-unavailable`, and block new work. Explicit Refresh retries initialization after the underlying fault is resolved.

All saved completions are audited before restoration mutates or writes the journal. One unavailable audit therefore also preserves other completed records and an unfinished latest attempt. Startup always attempts to cancel stale scheduler work, including error paths. Stop, failure and queued-persistence callbacks cannot mutate uninitialized history; notification cancellation finishes with an explicit unavailable control result instead of claiming a persisted cancellation. No new dependency, permission, service type or suppression was introduced.

## Review and verification

The implementation author inspected the restart boundary and added five regression tests. The primary review identified the early-return scheduler/callback gap; the author corrected it before freezing the patch. A separate reviewer then inspected all five source/test changes plus `TransferJournal` and `TransferJobService`, including initialization ordering, unavailable-history preservation, Refresh, scheduler cancellation and late notification/job callbacks. Primary and independent review found no remaining actionable source issue.

The normal pinned command completed successfully:

```text
:app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --console=plain
BUILD SUCCESSFUL in 45s
51 actionable tasks: 24 executed, 27 up-to-date
66 tests in 9 JVM suites; 0 failures; 0 errors
```

New tests exercise actual missing-path and directory-read errors; injected I/O/deadline interruption followed by successful verification; actual length/hash mismatch; and unchanged historical completion, verified metadata, events and revision until a conclusive retry. Injected unit faults are software tests, not Android storage/lifecycle qualification. The runtime callback and AtomicFile recovery behavior still needs a real device case.

The [package audit](2026-10-15-transfer-audit-package.json) binds **0.4.2 / code6** to APK SHA-256 `b52ebabeb8a6e84801425b3c0bad813a67d189be902c3dbfa69ebbca9448b77b`, **6,863,589 bytes**. All eleven notice assets match their source bytes. The merged manifest retains the five reviewed permissions, nonexported transfer Activity/receiver/JobService, `BIND_JOB_SERVICE` protection and existing exported launcher. No foreground service was added.

## Next physical case

Preserve the real completed payload and journal, stop only the diagnostic, and temporarily move that completed file to another app-private path. Install the exact reviewed APK and open the transfer experiment. Verify the explicit unavailable state, disabled Start and a byte-identical journal across repeated observation. Restore the same payload, verify its digest, press Refresh, and verify re-audit without a network operation or replacement of the earlier failed/interrupted attempts. Record preparation separately from user-stop/process-loss tests, then bind the actual source commit and APK in a separate run report. This case does not require a running fixture or changes to system storage permissions.
