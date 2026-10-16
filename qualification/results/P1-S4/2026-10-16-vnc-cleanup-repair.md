# VNC owner cleanup repair: affected Java checks and artifact acceptance

On 2026-10-16, parent and independent reviews accept the narrow Java owner repair within its isolated construction scope. Ten actual JVM methods pass, including two new deterministic regressions; strict Java compilation, debug/release assembly and both zero-issue Android Lint variants complete in one Gradle run. The [matching manifest](2026-10-16-vnc-cleanup-repair.json) binds source, raw failures, build/artifacts, corrected inspection and three independent reviews. No device/JNI runtime or complete stage is accepted.

## Resulting behavior

The [source finding](../../../docs/06-validation/2026-10-16-vnc-cleanup-race-review.md) exposed an automatic retry: a running poll could attempt cleanup directly while Stop queued another attempt. An uncertain first attempt could then be repeated without a deliberate retry. Repeated Stop could also replace the uncertainty state.

Every automatic path now requests the same first cleanup authorization. The serialized owner consumes that authorization once, making queued duplicates stale. Only explicit `retryCleanup()` after observed uncertainty captures the next attempt; pending requests coalesce and cannot consume a later uncertainty. First-stop admission and state publication share the callbacks lock with cleanup consumption, closing the additional parent-reviewed publication race. Native release-once/cancel/join/free ordering and retained handle/lease on uncertainty stay intact. Comments explain those invariants and the package-only deterministic executor seam.

Two new JVM cases pause the owner poll and fence the queued Stop, then check exact backend call counts and retained uncertainty. A second case checks repeated Stop, duplicate explicit retry and preservation of the next uncertain attempt. The ten-method class executes freshly with zero failure/error/skip. These are test-only backend/executor seams; they do not claim actual forced native cleanup failure.

## Actual checks and preserved failures

The accepted compiler recipe03 runs offline with strict existing dependency verification, selected JDK21/Java17/SDK37.0, and reviewed process/log bounds. Actual Gradle exits0 in26.235seconds:72 actionable tasks, all72 executed. Raw JUnit records all ten named methods; debug and release Lint each contain zero issues. No unchanged upstream/native engine battery or device case is repeated.

The original build aggregate remains **passed=false**, SHA `b9a682071475dc1d2414d214895b7fb13c3467f5e48d08b14ccf3d9dd227d4e1`. Its post-build inspector counted ZIP directory entries, producing five JNI entries where it required three files. The exact predicate is established from the frozen source and actual archive maps; the original result retained only RuntimeError, not its message. It is not a compiler or native-build failure.

A separate offline inspector first refuses its own128MiB member cap before comparing the inherited139847680-byte OpenSSL source archive. That inspection failure is also preserved. Corrected inspection02 binds the four whole AARs and exact old metadata before decompression, streams payload hashes with exact length/CRC checks, distinguishes files from empty directory records and preserves duplicate/directory-set rejection. Three synthetic ZIP boundary controls, Python3.10 AST and full project Ruff pass. This is an evidence correction, with no Gradle/native rerun or historical result edit.

Final offline inspection passes, SHA `5cb4f68734d8e2a8d1e8257641038af803b2e99c92f84181ebffa88298363587`; independent evidence review is `58906e6d3fa1a9ba3dfbb157ec69bfab5113a112e1e09fa69d70fcd671500328`. Both fresh AARs retain exactly three native files and17 source/notice asset files, all byte-identical to the accepted earlier artifacts, with the same empty directory sets. The12 class names are unchanged. Only `VncEngine.class` and `VncEngine$2.class` differ; bridge bytes and all other outer files remain unchanged.

## Adoption boundary

This build uses a reviewed ignored source-set projection of the actual module Java/manifest/test sources and exact accepted native/assets. The canonical module's broad `verifyNative` source guard still refuses the changed Java against historical receipt06bc. The successful projection does not repair that command or make root adoption ready. An explicit portable native-reuse route is a separate reviewed task, and future runtime qualification must bind the newly accepted repaired AAR.

Historical native receipts/AARs and all failed preparation/build/inspection results remain immutable. No JNI/upstream native rebuild, root registration, Desktop embedding, product APK update, permission/device operation, visible session, authentication, input, latency or final license/source closure follows. Installed products remain0.1.15/code16 at the separate reverse01 preserved-state authority. Existing P1-S4 prerequisites and all phase/capability/release gates remain unchanged.
