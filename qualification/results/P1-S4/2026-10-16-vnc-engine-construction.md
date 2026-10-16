# VNC engine library construction

**2026-10-16 — bounded construction checks passed; Desktop embedding and runtime qualification remain open.** The new Android library owns a real retained JNI client. It is not registered in the application graph and neither app gains a VNC dependency. This result does not complete P1-S4, P3-S8 or CAP-VNC. The [manifest](2026-10-16-vnc-engine-construction.json) binds exact source, artifacts and review; the frozen build packet is `.cache/vnc-product-engine-01/final-build-01.json`, SHA-256 `928bb2b78e55ab922ff8adaeed5b25e7581d9dac607813a3bb141f905fdf3260`.

## Implemented boundary

`adapters/desktop/vnc/android` contains a single-use retained owner for actual native load/create/start, complete owned RGBA callbacks, pointer/key/release input, cancellation, join and free. Production delegates to the actual JNI ABI. Package-only test seams do not replace that backend in production. No probe Activity, private configuration reader, qualification journal, default credentials or reconnect policy is imported.

Configuration is explicit and immutable: numeric endpoint, port, profile identifier, exact32-byte leaf SHA-256 and10–600second duration. The selected patched LibVNCClient route remains VeNCrypt19/X509Plain262, TLS>=1.2, leaf-pin plus validity, with no plaintext downgrade or ordinary CA/name-trust claim. Credentials transfer as bounded canonical UTF-8 arrays and are wiped on rejection and after native creation; temporary decoder character storage is also wiped. API37 local-network permission is checked before allocation; owning-app permission/trust/authentication UX remains integration work.

A bounded owner queue serializes JNI work off UI/callback threads. Callbacks fence the original attempt and handle; one pending complete RGBA frame is coalesced, with at most32MiB per frame. The consumer must retain at most one presented frame; native scratch/framebuffer/resize allocations are additional. Callback delivery is not visible playback. Stop disables local input immediately, attempts real tracked key/button release for an initialized connected transport and waits at most750ms for its write result or terminal status before cancel/join/free. A write is not a host acknowledgment. Failed join/free preserves the handle and process lease as cleanup uncertainty, with explicit retirement retry and no reconnect.

## Executed checks

The isolated ignored Gradle root references the real library directory without changing root settings, app dependencies, locks or product versions. It used the selected JDK21/Java17 target, Gradle9.6.0, AGP9.4.0, SDK37/build-tools36.0.0, NDK29.0.14206865/CMake4.1.2, API35 and arm64. Gradle ran offline with strict dependency verification.

Four native preparation attempts are retained: one failed before a compiler because the selected145095072-byte compiler exceeded the generic input reader bound; three subsequent successful builds compiled only the authored JNI namespace port. Existing patched client/JPEG static archives and SSL/crypto were reused. No upstream library was rebuilt or retested. The repaired compiler verifier uses a bounded streaming digest of the exact no-follow regular target inside the selected NDK, checks descriptor/path identity before and after, and refuses growth/replacement. The final native receipt is `06bc8873a11f37bbc6faae798a29568d8e0acb11e8ec3a0180a6a76b109dae26`.

Android build01 failed in configuration before Java. Its source-set cast failure was repaired using AGP's public `onVariants` and `Sources` APIs, confirmed against the selected9.4 API JAR and [official extension guidance](https://developer.android.com/build/extend-agp), [Sources](https://developer.android.google.cn/reference/tools/gradle-api/9.4/com/android/build/api/variant/Sources) and [SourceDirectories](https://developer.android.google.cn/reference/tools/gradle-api/9.4/com/android/build/api/variant/SourceDirectories). Build02 then compiled both variants and executed all eight JVM methods below, with zero skips, failures or errors. Both lint reports failed solely on `ChromeOsAbiSupport` at the explicit arm64 filter. The retained inline suppression scopes this sampled arm64 Googlebook construction and makes no ChromeOS/x86 qualification claim. Build03 reran affected assemble/lint tasks successfully:68 tasks,13 executed and55 up-to-date, with **zero issues in both debug and release lint XML reports**. The unchanged Java/JVM tests were not rerun; their exact source hashes bind the executed build02 reports to final source05.

| Executed JVM method | Authored boundary checked |
| --- | --- |
| `createRejectionReleasesLeaseAndDisposesSecrets` | Failed allocation releases Java admission and disposes transferred arrays. |
| `deliberateStopReleasesBeforeCancelJoinFreeOffCallingThread` | Tracked release precedes actual cleanup calls on the owner worker. |
| `releaseTimeoutIsFiniteAndRetainedCleanupDoesNotReleaseTwice` | Bounded release timeout and explicit uncertain-retirement retry. |
| `stopDuringCreateRetainsThenJoinsAndWipesCredentials` | Stop during allocation retains the returned owner through cleanup. |
| `joinTimeoutAndFreeRefusalNeverReleaseOwnerEarly` | Join/free refusal preserves the lease and nonzero owner. |
| `staleAndStoppedCallbacksCannotPublishOrRetainFrames` | Handle fencing, completed-frame coalescing and stopped callback rejection. |
| `invalidCredentialsAreWipedWithoutNativeAllocation` | Malformed input is rejected and transferred arrays wiped. |
| `releaseWriteFailureStillCancelsAndDoesNotClaimAck` | A failed release write remains explicit while cleanup proceeds. |

Twelve separate pure Python controls, scoped Python3.10 AST checking and Ruff passed. These exercise authored preparation/import/publication/tool-bound/process-group glue; JVM backend seams and Python fixtures are not native-server evidence.

## Actual packaged artifacts

| Variant | AAR SHA-256 | Bytes |
| --- | --- | ---: |
| Debug | `906790ac138659551f378dbfa77b58f51a710db2a560580516a2fd3805932776` |62692971 |
| Release | `03b8bebcb8e9b568bbc0e903313b6de9bfe2c384c2b76ea95dcb03fada04e471` |62692347 |

Both AARs were independently inspected: exactly three arm64 JNI libraries matching the prepared payload, nine correct product-namespace Java exports,16KiB ELF load alignment,12 engine classfiles and17 verified assets. There is no qualification Activity, test class, private config reader or journal in the runtime classes. The native libraries are JNI `702667cae3c54f550528a9d48c04e58619f868c702fc3931f9f44e406f254729`, SSL `66288be31a05675f6a319632d6abb137eb38b07f10eea302a8b128bd6ed7d5c0` and crypto `46c0eb8c3c601f3c9d8746e051aefe61e7a324bf152a535feac3f97472deb2d8`. Gradle reported that it could not strip those libraries and packaged their verified bytes unchanged; this is not a warning-free-build claim.

Three retained `.tar.gz` source assets appear as `.tar` in each AAR. Each packaged tar was matched byte-for-byte against the exact accepted input after gzip decoding; the other14 assets match directly. The manifest records the precise input/output names and digests. An initial local inspection expecting unchanged gzip filenames refused this difference; its disposition is retained. No product source changed to hide it.

The asset `cloudy-vnc-notices/accepted-receipt.json` is **historical inherited RDP native closure metadata**, supporting provenance of the reused SSL/crypto pair only. Its larger FreeRDP library list is not the VNC closure and it is not the current VNC build receipt. The actual VNC closure is bound by the separate `06bc…` receipt above. The17 construction assets include notices, retained archives and authored inputs; they are not certified as a minimized or complete final VNC release corresponding-source package.

## Acceptance limits

Independent source/artifact review accepted this construction scope without unresolved findings; review JSON SHA-256 `264fad4ccb6d5decefae11c1fa308c12abf8eabb44ed7260b327ad46403465b1`. Failed attempts and original native receipts remain unchanged. Native/Gradle build ownership was released after terminal checks.

No device/native loader, protected server, visible frame, input effect, cancellation against a real server, APK zip alignment, other ABI or physical-device case ran in this increment. AAR construction does not establish Android runtime permission behavior, app lifecycle/process-loss policy, embedding, persistent sessions or a supported VNC capability. D16 permits compatible open-source Desktop work; exact release licensing, final source/notices minimization/completeness and combined app closure remain parent review. Root registration and Desktop adoption require their separate accepted integration step.
