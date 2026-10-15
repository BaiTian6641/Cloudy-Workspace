# RDP diagnostic status-render review

**Date:** 2026-10-15. **Stage:** P0-S4. **Outcome:** bounded UI change compiled, passed Android Lint and 48 JVM tests, and completed the matched device rendering sample. No useful timing improvement was demonstrated. Read the [performance entry](../03-platform/googlebook/rdp-performance-entry.md), [run report](../../qualification/results/P0-S4/2026-10-15-rdp-rendering.md) and [hashed manifest](../../qualification/results/P0-S4/2026-10-15-rdp-rendering.json).

## Authored change and invariants

`RdpActivity` uses the new pure `RdpStatusPolicy` to format counter-only status at most once per 250 ms. Existing session notifications and the 100 ms observer provide later opportunities; no timer or background owner was added. Attempt, state/detail/error, native version, geometry, busy and connected changes bypass pacing immediately. A new Activity has a fresh policy. The counter deadline advances only when text is selected for rendering, so suppressed bursts cannot postpone it indefinitely.

Each render reads LAN permission once. Resume and permission-result refreshes query the actual permission again. Label resource IDs are cached per Activity; enabled setters compare the actual widget value. Capture controls still refresh immediately through their existing callback. Every existing session notification still calls `frame.update`, including counter-only notifications. No remote input is coalesced, dropped or reordered. Native fields/IME, release/capture handling, framebuffer copying/monitor ownership, software rendering, journal persistence and native input code are unchanged.

Source/test review supports fewer repeated setter calls and bounded counter formatting. There is no retained operation-count trace measuring the reduction on device, and the policy does not promise a four-Hz cap on urgent status changes or framebuffer draws.

## Build and inspection evidence

Candidate **0.1.6/code7**, build reference `27cb59d8-a829-4424-a888-db41259a006c`, is a Java/Kotlin/UI increment over accepted 0.1.5. The parent-owned full Android assembly, Android Lint and JVM test run ended `BUILD SUCCESSFUL`. The ten retained JUnit XML suites total **48 tests, zero failures/errors/skips**. Three status-policy tests cover sustained counter bursts and deadline behavior, final errors/new attempts, immediate geometry/connection changes and fresh policy state. The focused Kotlin 2.2.10/JUnit run passed those same three tests; they are included in the 48, not additional coverage.

An initial focused compiler invocation accidentally included incompatible Gradle scripting plugins and failed with `NoSuchMethodError`; the corrected Kotlin 2.2.10 dependency-only classpath compiled and passed. This tool invocation failure is not a passing check and did not change app code. Parent integration and independent review reported no blocking findings in this bounded change.

APK audit records **12,763,022 bytes**, SHA256 `4af1c2c4d51a90a80e11605805b69d2b6198fd999e70a1e74f2a8557faa63168`. Signature verification and 16 KiB ZIP alignment passed. Direct comparison of the two preserved APKs confirms **eight native libraries and 21 assets unchanged byte-for-byte** from 0.1.5. Their library membership also matches. Existing native qualification is reused; no new native build is asserted or required for this UI-only change.

Evidence: ignored `.cache/native-rdp/status-app-build.log`, `status-apk-audit.json`, the module's `app/build/test-results/testDebugUnitTest/TEST-*.xml`, and both preserved APKs in private `native-rdp-performance/2026-10-15-run-01`. The public manifest binds current authored sources and selected build/device/cleanup artifacts by hash. These local checks do not claim a hosted CI result for a future commit.

## Result and stop condition

The matched four-swipe baseline/candidate samples have p95 11 ms in both conditions, p99 14/15 ms respectively, and one janky frame each. The sample does not demonstrate a useful improvement or isolate status work as the cause of the earlier subjective lag. No input-to-photon, host encode, decoder or wire timing was measured.

The finite status-change experiment is complete. Preserve the passing correctness change and its limited timing result; do not continue attributing lag to status without evidence. Further performance work should measure host, copy/draw and owner/monitor boundaries with bounded instrumentation under the existing performance procedure. No source, input ordering or bitmap lifetime change is implied by this review.
