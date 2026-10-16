# Positive handoff test foundation and isolated Android checks

**16 October 2026 — scoped test construction and eight actual Android passes. P1-S3 remains in progress.** This increment adds retained receiver preparation/checkpoint/cleanup helpers and an ordinary-process cross-app controller. Both product APKs remain exactly **0.1.13/code14**; no product source, manifest, dependency or version changes belong to this increment. Positive Open/BAL and fresh receiver authentication/list/read remain unrun.

The [sanitized manifest](2026-10-16-handoff-test-foundation.json) binds the eight changed test sources, frozen builds, executed runner and private evidence. Read the [qualification contract](../../../docs/06-validation/files-handoff-positive-qualification.md) for the eventual operation and its acceptance limits.

## Real test implementation

Receiver PREPARE uses the existing native Files operation to authenticate, list `./nested`, register the completed listing, disconnect and share the exact resource. It then seals an exclusive immutable v2 checkpoint with run/direction, owner/package/UID, version, fixed case, preparation time, original full store state and exact owned rows. VERIFY is read-only. CLEANUP checks the immutable authority and all three current stores before removing the exact resource, pin and profile. Partial cleanup is refused; replay state is never reset. The existing settled fixture paths retain their earlier behavior.

This source is compiled, but the full PREPARE/VERIFY/CLEANUP sequence and coordinated three-store runtime preflight have not run. The three isolated SQLite methods exercise real in-memory databases and the actual exact-deletion helpers; they do not establish the complete phase sequence or production-store cleanup.

The standalone controller launches only the sender's exported launcher, navigates the visible Refresh/select/Open controls once, expects the real receiver, supplies its private fixture password, reads the nested preview, disconnects and follows ordinary Close controls. It contains no direct receiver intent, forged ticket, local Stub or adopted shell permission. Secrets are provisioned privately, never in instrumentation arguments or logged UI trees. Its final status explicitly leaves parent settlement pending.

Independent review found that selected labels also remain visible in the original lists. The corrected controller scopes selected-location and endpoint assertions to their semantic sections. A missing or ambiguous actual accessibility representation must fail and preserve the operation; compilation does not prove the selectors work on the device.

## Source and build evidence

| Frozen candidate | Executed checks and limit |
| --- | --- |
| Receiver build04 | Strict Kotlin/Java compilation and dependency verification; 227 unchanged source/build-input bindings, five frozen APKs, four Android Lint reports with zero issues. Existing JVM tasks were up-to-date. Separately, the exact extracted pure codec/metadata and five assertion bodies passed on the pinned local Kotlin/JDK toolchain. |
| Controller build03 | Strict compilation/dependency verification; 228 unchanged inputs, five frozen APKs, four zero-issue Lint reports. Existing JVM tasks were up-to-date. Both controller methods remain unrun. |
| Independent source reviews | Receiver metadata finding closed at build04; controller section-scoping finding closed at build03. Parent and independent reviewers inspected actual source and evidence. Earlier candidate builds and findings remain preserved. |

The installed product hashes equal both frozen builds. The device run uses build04's Console test APK only. The one later platform controller source is explicitly outside that exercised APK's source set; every existing build04 input and all five frozen artifact hashes were still checked. This is not a claim that the entire later worktree equals build04.

## Eight actual Googlebook methods

Device: **HP Googlebook 14**, Android17/API37, arm64-v8a, build **CL3B.260622.276.R2**, incremental **16560795**. The accepted packet ran from **18:27:45.614 to 18:27:52.600 UTC**. This is packet elapsed time, not protocol performance.

Five parser/metadata methods pass: typed integers/escaped strings; malformed, duplicate, coercion and trailing-input rejection; depth/field bounds; valid fixed preparation metadata; and wrong identity/case/time rejection. Three SQLite methods pass: exact owned-row deletion preserves baseline rows; changed tuples/extra rows refuse without mutation; and partial prior cleanup refuses instead of reconstructing state.

The actual instrumentation stream has eight unique starts, eight matching zero-code terminals, zero skips/failures, one finished runner and empty stderr. No Activity, host connection, authentication, receiver phase or positive Open was requested. All eight cases execute once in Console; duplicated execution of the same shared test sources in Desktop is not required for this isolated boundary.

Only the exact Console test APK was installed and removed. Both products remain installed at their original hashes and stopped; all three test packages are absent. The before/final snapshots match exactly: persistent private files and typed preferences, system settings, boot identity/count, device/build and ADB reverse mappings. No data clear, restoration write, replay reset or product replacement occurred.

## Preserved refusal and runner corrections

The first attempt refused in read-only preflight because both apps contained an unlisted private `app_dxmaker_cache` directory. No install, force-stop or test occurred. The parent also found that this build exposes focus fields in global `dumpsys window`, not the narrower `windows` query; an earlier standalone query failed its assertion and the following guarded runner still reached only read-only preflight. The corrected single runner owns the complete preflight before mutation.

Read-only inspection found both additional directories regular and empty. Runner03 includes that exact directory in the full hash/symlink/special-entry preservation inventory rather than excluding it, uses the observed global window query and refuses focused authentication UI. Earlier independent findings also tightened package-absence stderr handling and rejected unattributed instrumentation statuses. The executed runner's exact SHA is in the manifest. Offline controls and independent review passed before the separate successful attempt; synthetic controls are not counted among the eight device methods.

## Remaining acceptance

Both-direction normal Open still needs a real isolated host, genuine SHARED registration, receiver-owned fresh authentication/list/read and actual same-operation retirement. Source review found two precise prerequisites: the host fixture currently records SFTP opcodes without actual paths, and the controller needs a source-backed continuity/retirement observation before closing the ordinary windows. A separately reviewed fixture-only path observer and bounded continuity proof are the next work. No broader upstream conformance battery or product placeholder is needed.

Retain the pending product-vault question; this run did not replay a system authentication prompt. Full files/terminal/edit/drop/X11, VNC-first product integration, platform acceptance and release gates remain open under the active P0–P6 goal.
