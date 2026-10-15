# Native USB access diagnostic — source and build review

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S5, in progress. **Decision:** the integration leader and an independent direct GPT6.1-Sol Codex reviewer accepted the source/build candidate below for the next bounded device preflight. This is compiler, authored-code test and package evidence. No device installation or runtime observation is claimed here; CAP-USB remains pending.

The separate **`dev.cloudy.qualification.usb` 0.1.0/code1** implements real Android USB enumeration, explicit peripheral selection/permission, native descriptor inspection and close/cancel. It loads actual libusb, initializes an explicit no-discovery context and reports actual results, including zero devices. It performs no interface claim, driver detach, reset, endpoint transfer, network export or programming. Full general-purpose USB and enrolled plug-and-play remain required under the [provider entry](../03-platform/googlebook/p0-s5-usb-provider-entry.md); this finite probe does not implement that provider.

## Pinned inputs and candidate

The [source lock](../../qualification/probes/native-usb/source-lock.json) pins the official [libusb1.0.30 release archive](https://github.com/libusb/libusb/releases/download/v1.0.30/libusb-1.0.30.tar.bz2): **656,112 bytes**, SHA256 **`fea36f34f9156400209595e300840767ab1a385ede1dc7ee893015aea9c6dbaf`**. The Android grant-owned FD route follows [`UsbDeviceConnection.getFileDescriptor()`](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#getFileDescriptor()) and upstream [`libusb_wrap_sys_device`](https://libusb.sourceforge.io/api-1.0/group__libusb__dev.html). libusb does not own/close the borrowed Android FD.

The actual build uses NDK **29.0.14206865**, arm64-v8a / native Android35, Android compile/target37, minSDK35, Build Tools36.0.0, AGP9.4.0, shared Gradle9.6.0 wrapper and JDK21. The preparation helper reuses upstream [android/jni/libusb.mk](https://github.com/libusb/libusb/blob/v1.0.30/android/jni/libusb.mk) without upstream sample/test executables. Because the NDK shell wrapper rejects workspace spaces, Make invokes the same upstream `build/core/build-local.mk` through a temporary NDK alias; SDK contents remain unchanged. JNI C is compiled with strict warnings as errors.

| Identity | Accepted value |
| --- | --- |
| Build reference | `d207f066-517d-4a2f-8897-f347998696da` |
| APK | `qualification/probes/native-usb/app/build/outputs/apk/debug/app-debug.apk` |
| APK bytes | **3,399,686** |
| APK SHA256 | **`90f8b8f581a6b3e901f02446ffd019cd7e8401a16d81b884dead97531a158a90`** |
| `libcloudyusb.so` | 7,304 bytes; `257c0b08a78829f052ca11e0f67b29a0412d5dc3aa30919a0bdb9dd96c55c952` |
| `libusb-1.0.so` | 99,608 bytes; `132c28689889e2db98efd01fe4a7b9261e8aa5bd6257fee8c98ada96699cc2a6` |

Earlier local candidate hashes are superseded by this exact APK. The [probe README](../../qualification/probes/native-usb/README.md) gives the reproduction commands and boundaries.

## Reviewed findings and fixes

- **Permission correlation:** a request code is not returned broadcast data, and an immutable PendingIntent need not receive system fill-in USB extras. Creator-owned generation/device ID/random nonce now match the pending request and current selection before consumption. Stale replies cannot clear a newer request. The receiver re-queries actual presence, eligibility and `hasPermission()` using the retained expected identity.
- **Resource ownership:** one worker serializes open/wrap/describe/close. Native handle/context cleanup precedes Android connection close. Generation invalidation rejects work after cancellation, detach or hidden-window transition. Cleanup status distinguishes requested from completed; no unsafe concurrent close interrupts a blocking kernel call.
- **Close admission:** resetting a coalescing flag through a delayed UI callback could suppress cleanup after a replacement handle opened. The production admission gate now resets atomically on the worker, with focused regression coverage.
- **Build identity:** inputs are snapshotted before compilation; JNI/notices use captured bytes. Changed inputs refuse publication, and receipts bind captured records. Both shared RDP helper files in the Python import closure are hash-bound and included as corresponding build sources.
- **Bounded build cleanup:** the Make build has its own process group. Timeout/interruption/nonzero exit uses the accepted TERM/then-KILL cleanup, including descendants after the leader exits. A real TERM-ignoring child regression checks this boundary.
- **Packaging:** actual libusb source/license/build materials and the already reviewed Kotlin2.2.10/annotations13.0 runtime notices are included. Effective manifest inspection found Android's implicit touch filter; USB host, touchscreen and faketouch are now explicitly optional. No Android permissions or service are declared. Initial missing-icon/plural-resource Lint failures were fixed without suppressions.

## Executed checks and retained evidence

Native compilation and offline input integrity passed. Required JNI exports and allowlisted dynamic dependencies passed; both libraries are ELF64/AArch64 with16KiB load alignment. APK assembly, Kotlin compilation, strict Android Lint, **20 JVM tests in three suites** and **six Python helper tests** passed with zero test failures. AST, Ruff and documentation validation passed. The focused tests cover Cloudy's ownership/correlation/admission and build-integrity boundaries; no exhaustive upstream USB conformance suite was recreated.

Exact package comparison matched **20 files: two native libraries and18 source/notice assets**. Only the expected two native libraries are packaged. The effective manifest confirms optional hardware features and no requested permissions. APKv2 signature verification and16KiB `zipalign` passed. No instrumentation or physical-device test was executed, and no hosted CI result is asserted by this review.

Private/ignored evidence paths, relative to repository root:

| Path | Evidence |
| --- | --- |
| `.cache/native-usb/receipt.json` | Captured source/config/helper hashes, actual ELF/output hashes and generated-file binding |
| `.cache/native-usb/build.log` | Final real upstream/JNI native compiler/link output |
| `.cache/native-usb/gradle.log` | Final APK compiler/Lint/test task result |
| `.cache/native-usb/package-audit.json` | Exact APK identity,20 file comparisons and three JVM-suite results |
| `.cache/native-usb/downloads/libusb-1.0.30.tar.bz2` | Hash-verified selected upstream source archive |
| `qualification/probes/native-usb/app/build/test-results/testDebugUnitTest/` |20 JVM test results; machine-local XML is not publication evidence |
| `qualification/probes/native-usb/app/build/reports/lint-results-debug.html` | Final Android Lint report |
| `qualification/probes/native-usb/app/build/generated/nativeNotices/native-manifest.json` | Packaged native source/build identity manifest |

The primary Ubuntu workstation supplied build/test execution only. The peripheral/probe remains deliberately unspecified. The next parent-owned preflight may establish actual APK loading, no-discovery initialization and the real zero-device observation if no eligible peripheral exists. Granted FD execution, physical denial/cancel/detach/replug races, arbitrary-class forwarding, re-enumeration, reset and program/verify remain pending. No development/debug transport or unrelated peripheral was opened to manufacture evidence. Product distribution/license adoption and full provider/consumer qualification remain separate gates.
