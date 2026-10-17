# Desktop 0.1.17 user-ended VNC construction

The reviewed Desktop **0.1.17 / code 18** build replaces the ordinary foreground session's private 60-second ceiling with an explicit user-ended policy. This report accepts source, native wrapper compilation, JVM tests, Lint and packaged artifacts. Console remains unchanged at **0.1.16**. The subsequent [Googlebook result](2026-10-16-vnc-user-ended-device.md) separately records the duration/Disconnect case and three actual admission tests; these are not inferred from construction.

The real path is `VncActivity` → `VncSessionModel.connectUserEnded` → `VncEngine.Config.userEnded` → JNI. The explicit factory supplies duration zero; the product generator applies two guarded substitutions to the selected adapter, mapping that value to `UINT64_MAX` for the overall deadline. Existing finite constructors still require 10–600 seconds. Catalog generation, exact endpoint/revision, foreground authority, permission, pin validation, credential transfer/wiping and single-owner admission remain in the shared path. The original diagnostic sources remain unchanged.

Authentication phase/read-idle, in-progress session read-idle, cancellation, socket shutdown, join and retained ownership on uncertain cleanup remain. Focus loss releases input; it does not by itself promise transport retirement. Background sessions remain unsupported. Authenticated TLS writes are explicitly cancellable but have **no independent elapsed write-stall bound** in user-ended mode. This is not a claim that every I/O operation completes autonomously within a fixed time.

Actual affected checks are:

| Check | Actual result |
|---|---|
| Product preparation helper | 19 methods passed |
| Authored C policy and helper controls | 3 unittest methods passed; 39 C assertions using real OpenSSL and ASan/UBSan |
| Library JVM owner/config tests | 12 methods passed; task executed |
| Product JVM tests | 20 methods passed; task executed |
| Android Lint | Four debug/release reports, zero issues |
| Gradle | 446 tasks: 114 executed, 332 up-to-date |

The JNI wrapper was freshly compiled against the verified reused native closure. This was not a complete rebuild or exhaustive retest of LibVNCClient, OpenSSL and JPEG. Strict dependency locks remained unchanged. The new Android admission refusal method compiled with the test APK; this build did not execute instrumentation. JVM seams do not establish a native server or physical-device result.

Both Desktop APK manifests identify version 0.1.17/code 18, min SDK 35 and target SDK 37, with a nonexported VNC Activity. The debug APK SHA-256 is `832701fa5a5067a994ef74789d423b1312d18ec9f647989a90a74001131194b2`; the unsigned release APK is `c76855489121ff8fab3c47cf4d3ab194af091207d9f9852300ac2f2e0a0c71a2`. Both APKs contain the exact three VNC native libraries. Both library AARs match those native bytes and 17 packaged assets, including three AGP gzip inflations. Nine JNI exports were checked. This does not complete release source/notices/licensing closure, and the artifact review adds no fresh cryptographic signature-verification claim.

Two failed aggregates remain preserved. The first helper run rejected an outdated synthetic stage fixture lacking the newly required source anchors; the fixture was corrected without loosening production checks. The first C-policy aggregate expected 37 assertions although the actual harness completed 39 without assertion failures. The expectation was corrected and the affected three-method suite passed. Source01's overly broad I/O comment was narrowed in source02 to the actual cancellation contract. The later expected-count-only test change is separately bound in the JSON companion.

The [JSON companion](2026-10-16-vnc-user-ended-construction.json) binds source02, current source hashes, the actual build receipt `ad22e0d169391a57aaf6b3c10833536afdfced8384f4b80cce0942520d04b2ba`, native receipt `96ed1ef25c0c1194c5c347f4989ef55b3c82cf56b608d41b89fb9513a7ecebc7`, independent source/entry/build reviews and preserved failures. The proposed error-visibility repair remains unimplemented in this version. No capability or phase is promoted by this construction report.
