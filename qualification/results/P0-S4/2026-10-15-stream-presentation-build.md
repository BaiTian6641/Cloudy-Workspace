# Streaming presentation/retirement build — 0.1.10

**Build and source review passed; not installed or executed. Visual playback remains FAILED from 0.1.9.** This is the reviewed follow-up to [SR-01 through SR-06](../../../docs/06-validation/2026-10-15-stream-comprehensive-review.md), with [detailed repair disposition](../../../docs/06-validation/2026-10-15-stream-presentation-review.md). It does not establish that missing video is fixed. The [machine-readable manifest](2026-10-15-stream-presentation-build.json) binds source, build, tests, audit and review evidence.

## Exact artifact and scope

- Package `dev.cloudy.qualification.stream`, version **0.1.10/code11**, build `8262bedf-19ea-41f0-baff-04a2f4d2ff8e`; minSdk35, target/compile37. Identity creation/inspection remains API37-only.
- Final stage `presentation-03`; APK **24,851,299 bytes**, SHA256 `d02eebced99b3d7db9d8c62f33d4fdf93dba13052d93af98a9d8ee6379abbfed`.
- Gradle **9.7.1** with the existing checked wrapper checksum, JDK21, SDK37 revision2, build-tools36.0.0. The separate baseline app's Gradle9.6.0 does not apply here.
- Native build reference `f336e245-805d-4aad-bc1a-9208df8db318`; native bytes, four composed engine overlays and all **39 runtime artifacts** remain unchanged. No native rebuild was needed.

The source adds a fixed aspect-preserving preview, visible Stop/phase/attempt/deadline and actionable admission reasons; bounded actual TextureView count/timestamps/geometry; and real prompt/worker/terminal-write retirement for PAIR/CATALOG. No live Surface rebind, credential-policy change, automatic replay or native lifetime reset was added. Preserved evidence failures explicitly require a fresh process and local reconciliation after verified cleanup.

## Executed checks

The final real Gradle invocation completed in **44,522 ms** with `assembleDebug`, `lintDebug`, `testDebugUnitTest` and `recordCloudyRuntimeDependencies` passing. **60 JVM tests** pass with zero failures, errors or skips. Tests cover the actual production retirement coordinator, prompt/worker ordering, duplicate callbacks, write failure/retry, presentation bounds/immutable snapshots, update timestamps, late callbacks and blocker priority. They do not substitute for Android recreation or visible-video observations.

Android Lint reports **zero owned findings and zero new errors**. The accepted **27 inactive upstream baseline errors**, **338 upstream warnings** and one hint remain explicitly scoped; this is not a claim of an empty upstream Lint report. The final audit includes owned resources as well as Java. All **681 staged files** match the preparation receipt and current source inputs.

Actual compiled and merged manifests expose the single owned Activity, with the existing four permissions, backup disabled and the existing loopback-only cleartext exception. APK signature continuity, native 16 KiB ZIP alignment, exact native bytes, selected notice assets and unchanged runtime closure pass. The source was reviewed independently and by the leader before integration.

The first `presentation-01` build also compiled and passed 60 tests, but retained one owned unused-string warning. Its initial Java-path-only audit incorrectly reported zero owned findings. The parent caught this by comparing Lint reports, preserved the artifact and correction receipt, removed the unused string and ran the fresh final stage above. Do not use the first artifact as the accepted zero-owned-Lint build. The intermediate presentation-02 also passed 60 tests/zero owned Lint; final review then corrected the normal Stop recovery message. Its artifact remains preserved and is superseded by presentation-03.

## Runtime and continuation

No 0.1.10 install, system prompt, catalog request, host launch or playback occurred for this build checkpoint. Installed 0.1.9 remains stopped, with the previous 22 records preserved and no active streaming fixture. Its 1,517 decoder-render callbacks and the user's **no playback** result remain unchanged.

The next action is a separately identified device/layout and finite-playback attempt after verifying actual package/process/evidence state. A visible animation result, actual texture observations and user feedback must be correlated to that attempt. Audio, input, performance, sustained behavior, broader host compatibility, product vault/reconnect, D16 distribution closure and CAP-STREAM remain unqualified. The P0–P6 goal remains active.
