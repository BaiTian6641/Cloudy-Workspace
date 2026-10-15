# Project history

Keep this file as a concise record of accepted decisions and reviewed milestones. Exact file changes and commit identities remain in Git. Detailed evidence belongs in linked run/review records. Do not include credentials, private equipment inventories or raw transcripts.

## 2026-10-14 — Planning foundation and integrated scope

- `e283cac`: established the project foundation, source/API tracking, delivery stages, design reference and planning checks.
- `57ec64a`: refined the desktop-first Googlebook scope and required X11, SFTP/drop, native RDP/VNC and enrolled USB workflows.
- [Revision 02](revision-02-final-review.md) records the user's every-save overwrite choice, independent app releases and early native-stream decision. Its [validation record](../06-validation/revision-02-planning-review.md) describes the planning checks performed at that snapshot.

## 2026-10-15 — Implementation goal and reviewed execution setup

The user activated phase-by-phase implementation, connected the HP Googlebook by USB, required real implementations, stage code reviews, AST/lint checks, helpful comments, reviewed subagent work, official-reference research and timely Git commits/pushes. These rules are recorded in AGENTS and the [engineering workflow](../05-delivery/engineering-workflow.md).

The primary Linux host is this Ubuntu 26.04/Wayland workstation. Windows remains desired with Parsec accepted temporarily; no Windows test target or USB board/probe was supplied. Initial ADB and host observations are recorded in the [baseline](../../qualification/results/P0-S1/2026-10-15-usb-baseline.md). They do not qualify product features.

Independent planning review found a remaining Desktop dependency on Console terminal/pane/file UI. Shared SSH/SFTP core work was separated from that renderer path, with a separate SSH capability and explicit dependency constraints. Existing effort was redistributed rather than counted twice. The current roadmap/backlog supersede the earlier 39-stage snapshot; historical reports remain unchanged.

Official Android skills were acquired at an immutable upstream revision, inspected and documented with project-specific usage limits. Real Python AST/compile and pinned Ruff lint checks were added to CI. The primary agent reviews the subagent changes and records the completed gate in the [execution setup review](../06-validation/2026-10-15-execution-setup-review.md).

This checkpoint keeps all application capabilities pending. Follow [execution state](../05-delivery/execution-state.md) for the next stage and unresolved qualification prerequisites.

Checkpoint [ebb8278](https://github.com/BaiTian6641/Cloudy-Workspace/commit/ebb827861b4970b3695a929da4dd8e8674791efd) was committed and pushed to `codex/project-foundation`; local and remote SHA matched. [GitHub Actions run 38685129757](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38685129757) completed successfully, including AST, Ruff, nine regression tests, planning/schema and JavaScript checks. The subsequent documentation checkpoint records this verified delivery result without changing capability status.

## 2026-10-15 — Real Googlebook diagnostic APK and device smoke

Source checkpoint [cf6630b](https://github.com/BaiTian6641/Cloudy-Workspace/commit/cf6630b6ff98ea8131ced1ef7ebaef36a33494af) adds a real native diagnostic APK, verified Gradle/SDK pins, dependency locking/checksums, Android compile/lint/JVM checks and CI. It was independently reviewed, committed and pushed on `codex/p0-device-baseline`; the remote SHA matched. The [review](../06-validation/2026-10-15-device-probe-review.md) records the corrected decoder flags, pending-status ordering and bounded persistence readback.

The [physical-device run](../../qualification/results/P0-S1/2026-10-15-device-probe.md) records actual APK install/launch/report retrieval, working Refresh and ADB HOME/return callbacks. Input/decoder counts and attached-view caption geometry are observations; no feature/performance gate is promoted. The user left USB hardware undecided while preserving full generic forwarding/PnP implementation scope. Linux is the primary host; Windows remains desired and unqualified.

Repository CI for cf6630b passed. The first Android CI run failed after setup. A fresh local checkout/cache reproduced missing transitive metadata checksums; five canonical Maven Central entries were verified and added, and all 49 clean build/lint/test tasks then passed without disabling verification. Correction [3dfd070](https://github.com/BaiTian6641/Cloudy-Workspace/commit/3dfd0705155ef5481e6d40c773ffdbfcb17ade86) was committed/pushed with matching remote SHA. Hosted [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38688126844) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38688126993) both completed successfully for that exact correction.

Official-source review also distinguished Sunshine stable-release macOS guidance from later master changes and recorded KDE Wayland backend prerequisites. Follow the [execution state](../05-delivery/execution-state.md) for remaining P0-S1 route/project inventory and later capability tests.

## 2026-10-15 — P0-S1 inventory and setup complete

The [setup closeout](../../qualification/results/P0-S1/2026-10-15-setup-closeout.md) completes normalized private inventory and the sanitized public matrix. DMI identifies the selected Ubuntu workstation as HP ZBook Ultra G1a 14 inch Mobile Workstation PC. The existing login and an isolated ignored fixture directory are selected for later work. Intake-only hosts, unavailable Windows and deliberately undecided USB hardware are explicit gaps; no support is inferred from their names.

Two Googlebook ADB-shell ICMP packets to the selected host on the local LAN succeeded. Two packets to its tailnet address received no replies; that shell lookup selected WLAN, and official Android/Tailscale guidance confirms per-app VPN policy can affect the later application's route. LAN is selected first, tailnet remains an unresolved candidate, and no VPN setting or host service was changed. These are setup observations, not protocol/performance evidence.

Independent closeout review reconciled the stage deliverables with actual build/device/inventory evidence. P0-S1 is done for setup scope; P0-S2 is ready. All eight feature capability outcomes remain pending, including generic USB forwarding and plug-and-play. The implementation goal remains active.

Closeout commit [2f949b1](https://github.com/BaiTian6641/Cloudy-Workspace/commit/2f949b1b7a67ecdd1f27490dffb91934adb00ad5) was pushed with matching local/remote SHA and a clean working tree. Both hosted [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38689059845) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38689059722) passed. A read-only P0-S2 preparation review identified per-window/task/activity-generation attribution as the prerequisite to adding real multiple-window observations; the execution state carries that next increment. The desktop contract's stale all-experiments-untested wording was corrected to acknowledge partial P0-S1 evidence while retaining unqualified product scope.

## 2026-10-15 — P0-S2 native window source increment

The diagnostic APK now implements real document-window creation, independent Bundle-owned marker/counter state, explicit Activity recreation, per-task/generation observations and bounded retention. An independent code review caught a stop/destruction listener-removal crash; the corrected collection operation has regression coverage. The primary agent reviewed the source and ran Kotlin compilation, APK assembly, Android Lint (no issues) and all 17 JVM tests successfully. Repository AST/Ruff, nine planning regressions, schema/documentation and JavaScript checks also passed. The [stage review](../06-validation/2026-10-15-window-probe-review.md) records acceptance and limits.

Official task/state APIs, the Linux VM boundary and operation-specific background execution were researched separately. The current sources do not establish a third-party Linux VM attach/control API. Service classification, physical device compatibility and product support remain unqualified. P0-S2 is in progress; this source checkpoint permits the next actual Googlebook window experiments and does not close the stage.

Source [13dce23](https://github.com/BaiTian6641/Cloudy-Workspace/commit/13dce23dd4052163f22703752b16438ec81c8306) was pushed on `codex/p0-window-lifecycle` with matching remote SHA. Hosted [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38691345990) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38691346070) passed for that source. The [actual window run](../../qualification/results/P0-S2/2026-10-15-window-probe.md) then exercised two tasks, independent Bundle state, native resize/tiling/caption/focus/close, quiet settling and a reversible private-report write fault/recovery. Device actions were injected through ADB/official CLI; physical input, larger fonts and product capabilities remain unqualified. Raw unrelated desktop content stays private; public evidence contains allowlisted observations and hashes.

A separate [process-restoration run](../../qualification/results/P0-S2/2026-10-15-process-restoration.md) distinguished the initial no-effect kill request from a later verified process death. After the minimized probe reached LAST_ACTIVITY, a package-scoped background kill removed its PID. Opening the existing Recents task restored its logical window, task and C/2 marker state in a new Activity generation and process. This qualifies the bounded Bundle restoration case, not natural low-memory killing, cached-process behavior or protocol/operation durability. The [method and pinned AOSP diagnostic references](../03-platform/googlebook/p0-s2-process-lifecycle-method.md) explain the investigation without treating Android 16 upstream internals as an HP Android 17 contract.

The next engagement diagnostic is documented against published AndroidX WindowManager 1.6.0-alpha05. The primary reviewer checked the release notes, exact public symbols and four artifact hashes independently. API09 now distinguishes the published alpha, library installation floor and runtime behavior; no dependency, permission or density setting was changed. The [candidate contract](../03-platform/googlebook/p0-s2-engagement-probe-contract.md) carries compiler/dependency/device acceptance into the next increment.

A subsequent pre-implementation review found that the alpha engagement backend delegates a null Handler to InputManager, requiring the registering thread's Looper. The contract now specifies a process-owned HandlerThread rather than the existing plain executor. The primary agent checked the exact framework contract and published alpha delegation before accepting this correction; no shipped probe code uses the alpha yet.

## 2026-10-15 — Real engagement collector and dependency audit

The probe's 0.3.0/schema-3 increment uses the actual published WindowManager engagement API, with process-owned Looper dispatch, same-instance cleanup, per-start epochs, bounded admission/history and explicit failure states. An independent reviewer accepted the implementation and tests; the primary agent reviewed the source and dependency/license results. Compilation, Android Lint and all 31 JVM tests passed. All 350 earlier dependency digests were preserved, 39 additions matched canonical downloads, and fresh-cache dependency resolution passed with verification enforced. The actual APK contains all 11 reviewed notice assets byte-for-byte. The [source review and packaging audit](../06-validation/2026-10-15-engagement-probe-review.md) retain exact APK identity, lifecycle invariants and the narrow Core version-advisory exception. This checkpoint authorizes real-device E02–E05 experiments; it does not close P0-S2 or any product capability gate.
