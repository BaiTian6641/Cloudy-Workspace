# Profile recreation and installed-app isolation

**Date:** 2026-10-16. **Outcome:** the unchanged Console/Desktop0.1.4/code5 products pass controlled pending-save Activity recreation and fixture-backed profile isolation. Across the preserved initial run and its continuation, 22 JUnit cases pass; two default invocations skip. One initial filesystem assertion fails and remains recorded. A separate host-preflight failure executes no instrumentation. P1-S2/P1-S3 remain in progress.

## Implementation and source review

This increment changes instrumentation and its cleanup, not production behavior, dependencies or permissions. Two direct Codex GPT6.1-Sol workers supplied disjoint recreation/isolation tests; another coworker reviewed isolation, and the leader inspected the actual source, corrected a misleading file-access comment, built and executed the tests. The leader and author then researched and reviewed the revised filesystem assertion against official AOSP source after the real failure. Worker reports alone are not acceptance evidence.

`ProfileRecreationContract` uses test-only reflection of Cloudy's private app-owned `operations` executor. It verifies the actual single-worker, bounded 16 queue and submits an ordinary latch task with a 15-second deadline. It does not replace the executor, call Android hidden APIs, fake storage, remove work or shut down product infrastructure. The two cases admit an actual UI Create or Update behind that gate, recreate the real Activity, check that the replacement queues its load afterward, and release the gate in `finally`. The real store then commits once. The restored draft remains explicitly uncertain with Save disabled. Refresh and another recreation preserve exactly one created row at revision 1 or exactly one update to revision 2, without replay.

Cleanup captures the owning application context before teardown. It closes the Activity, releases gates and awaits bounded worker/main fences before inspecting and deleting only this test's newly created IDs/prefix. It no longer depends on scrolling through a working UI. Unproved closure or settlement fails and preserves fixtures. Passing cleanup in these cases does not qualify every failure path or actual process death.

## Exact build and device

The [build record](2026-10-16-profile-recovery-build.json) binds both test revisions and APKs. Frozen build01 passes strict compilation and Console/Desktop/platform Lint in 15 seconds, 303 tasks/17 executed; build02 passes in 14 seconds, 303 tasks/15 executed. All three Lint reports contain zero issues. Product APKs are byte-identical to the [accepted editor build](2026-10-16-profile-editor-build.json); the parent reads and hashes the installed APKs before/after each packet. Only the two app instrumentation APKs are installed, then removed. The standalone platform test APK is built but not installed or rerun. Existing unchanged domain/state/library results carry forward under D12.

Device: **HP Googlebook14, Android17/API37, arm64-v8a, build CL3B.260622.276.R2, incremental16560795**. Private records are under `qualification/results/private/foundation/`:

| Packet | UTC interval | Actual outcome |
| --- | --- | --- |
| `2026-10-16-profile-recovery-01/` | 09:14:59.554871–09:15:35.671854 | Six existing profile cases plus two recreation cases pass per app; both default isolation invocations skip; both isolation seeds pass; Console verify fails `PEER_DENIAL_UNCONFIRMED`. Both READY receipts/rows retained. |
| `2026-10-16-profile-recovery-02/` | 09:19:13.759331–09:19:15.303235 | Host preflight finds multiple user-specific data directories and refuses ambiguous selection. No verify/cleanup instrumentation executes. Fixtures remain intact. |
| `2026-10-16-profile-recovery-03/` | 09:20:14.340828–09:20:18.607266 | Both revised verify phases and both exact cleanup phases pass, using the original run/READY records without reseeding. |

The accepted 22 cases comprise 12 existing profile UI cases, 4 admitted-save recreation cases, 2 seed phases, 2 revised verify phases and 2 cleanup phases. Two assumption skips are not passes. Instrumentation metadata status messages are not extra JUnit cases. The six accepted isolation phases use distinct processes and two stable, different product UIDs. This establishes later-process receipt/store use, not a live simultaneous-writer or killed-Activity recovery test.

## Filesystem failure and bounded correction

Initial verification accepted only `EACCES`/`EPERM`. It failed on the Googlebook; that version did not record the actual errno, so its cause is not retroactively asserted. Both owner-side database snapshots showed exactly one added fixture each and unchanged preexisting rows. No data was cleared to obtain a new result.

The pinned [AOSP Zygote implementation](https://android.googlesource.com/platform/frameworks/base/+/bba5ed7765e4b843877513cd9e518f85da0e43cc/core/jni/com_android_internal_os_Zygote.cpp) uses a private mount namespace, overlays app-data roots and binds selected related packages back into view (G210). Thus a known existing sibling file can be absent from another app's view. This explains a possible mechanism; it does not identify the installed framework or prove the cause of either attempt. Public `Os.open`/`fstat` are documented in G201; no private platform API is used.

The revised verify contract requires `cloudyProfileIsolationPeerPathSha256`. Before **each** verify, the parent obtains the actual owning `run-as` working directory, matches it to the package's reported data directories, reads the exact absolute database path as its owner and verifies the original READY run and fixture row. The pathname's SHA256 binds that witness to the sibling probe; no raw pathname is emitted by the test. In each product, an own-database `Os.open`/`fstat` positive control confirms a nonempty regular file before the peer attempt.

Both actual peer attempts return **errno 2 / `NOT_VISIBLE`**, with path digests matching the independent owner-side reads. Both catalogs contain their exact own fixture and no peer fixture. `PERMISSION_DENIED` remains a distinct possible result for errno 1/13. Other errors or any successful peer open fail. An arbitrary nonexistent path, or a digest without the external read receipts, cannot satisfy this qualification. Before/after owner-side database and receipt hashes remain unchanged during both verification phases.

## Preservation and continuation

Both cleanup phases delete only their receipt-bound generation/ID/revision and then remove their own receipt files. The parent verifies that all original profile rows and catalog metadata/generations match the pre-run baseline exactly. Both test APKs are uninstalled with absence checked; both product apps are stopped. Installed product bytes, boot ID/count and both preexisting manual-vault receipt hashes are unchanged across all three packets. No authentication, network session, host fixture, app-data clear or reboot is initiated.

This qualifies sampled owning-product catalog separation and inability to open the witnessed sibling databases, not credential protection, every filesystem alias, IPC authorization, simultaneous writers, physical power-loss durability, or a specific sandbox mechanism. Actual process-death editor recovery, unavailable-store exit UI, protected-vault operations, native input/IME, themes/font scaling/accessibility and real session/handoff integration remain open. The new [SSH trust construction entry](../../../docs/02-architecture/ssh-host-trust.md) defines the next domain/storage boundary; it adopts no engine and implements no connection. Carry the passing cases forward rather than repeat them without a changed boundary.

Repository checks pass: Python syntax for32 authored files, full Ruff, generated Android token consistency and planning validation (121 JSON files,296 Markdown files,2,251 local links,493 sources and53 API records). These checks do not enlarge the device acceptance scope above.
