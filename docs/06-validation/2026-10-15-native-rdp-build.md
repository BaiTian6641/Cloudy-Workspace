# FreeRDP Android native build result

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4, still in progress. **Result:** the real `freerdp-android` native/JNI target compiled and linked successfully for **arm64-v8a / Android API29**, with upstream **MediaCodec H.264 enabled**. This closes the isolated native-build question. It does not establish Android loading, decoded pixels, a server connection, performance or CAP-RDP support.

The [manifest](2026-10-15-native-rdp-build-manifest.json) binds actual inputs, 40 retained private evidence files, output hashes and the independent review. The [reproduction recipe and source lock](../../qualification/probes/native-rdp/README.md) preserve the selected entry without vendoring third-party implementations or binaries.

## Source and configuration

FreeRDP **3.32.2**, exact revision `97eff6ddfbbfaa8e42a126d362686bd001ff8bdc`, came from its official archive. Archive SHA-256 is `e99ebb8bbdd2608fd54fcc8b15c510f89dbc05b166c48339d1b1edea0b30f3c8`. Comparing all **2,975 regular source files** against the archive found only the reviewed two-line OpenSSL job-limit patch. Generated files and output libraries are bound separately.

The actual toolchain was stable **NDK29.0.14206865**, Android Clang21.0.0, **CMake4.1.2** and Ninja1.12.1. Upstream declares NDK29.0.13113456, an r29 RC1 build; using stable r29 is an explicit substitution. The CMake native entry avoids building upstream's profile/database application. Google documents these toolchain/ABI/platform arguments in its [NDK CMake guide](https://developer.android.com/ndk/guides/cmake).

The selected graph built hash-verified **OpenSSL4.0.1**, **cJSON1.7.19** and **uriparser1.0.2**. MediaCodec is on; FFmpeg, OpenH264, Opus, PNG, WebP and JPEG are off. The inner cache and generated headers confirm `WITH_MEDIACODEC` and `WITH_GFX_H264`; selected dependencies resolve through the isolated Android prefix/NDK. No host-library contamination was observed in the inspected paths.

This intentionally revises the initial codec-disabled entry. The investigated KDE server requires Graphics Pipeline/H.264, so a decoder-free build would not answer its integration question. The [source/API decision](../07-reference/2026-10-15-rdp-android-mediacodec.md) records the exact NDK API levels, CPU-accessible YUV → GDI → Bitmap route, experimental upstream status, output-format risk and initialization-only fallback behavior. No hardware acceleration or zero-copy rendering is inferred.

## Actual attempts and warnings

| Attempt | Observed result | Resolution |
| --- | --- | --- |
| Build01 | Exit1: OpenSSL's generated Makefile split the source path at the workspace space | Retained failure; extracted the same source into a real temporary path without spaces and aliased the SDK |
| Build02 | Exit1: dependencies built, but inner compilation could not write the default ccache directory | Retained failure; set stage-local `CCACHE_DIR` without modifying global configuration |
| Build03 | Exit0, completed at **16:34:03 UTC**, with the actual JNI shared-library link | Accepted compiler/link result; no unchanged rebuild required |

The final build retains **two upstream WinPR `PARITY_NONE` macro-redefinition warnings**. Configuration also reports experimental MediaCodec H.264/AAC support, optional Cairo/systemd findings and CMake development warnings. The result is a successful build, not a warning-free build or an exhaustive upstream static-analysis pass. No Cloudy Kotlin/native adapter code changed, so existing Android application compiler/Lint evidence is carried forward under D12; the new integration will require its own checks.

## Resulting libraries

NDK `llvm-readelf` confirmed ELF64/AArch64, no observed RPATH/RUNPATH and **0x4000 alignment for every PT_LOAD segment**. This is ELF inspection only; APK packaging alignment remains untested.

| Output | Bytes | Packaging role |
| --- | ---: | --- |
| `libfreerdp-android.so` | 490,704 | JNI entry |
| `libfreerdp3.so` | 20,356,072 | Linked core |
| `libfreerdp-client3.so` | 3,552,120 | Linked client |
| `libwinpr3.so` | 5,731,088 | Linked platform runtime |
| `libssl.so` | 1,205,848 | Linked TLS |
| `libcrypto.so` | 6,016,896 | Linked cryptography |
| `libcjson.so` | 134,800 | Linked JSON dependency |
| `liburiparser.so` | 558,008 | Linked URI dependency |
| `legacy.so` | 146,424 | Separate OpenSSL provider; not in the required link closure |

The NDK's `libc++_shared.so` was also inspected and retained, but **no produced library requires it**. Do not package it merely because the CMake STL option names it. The manifest records all exact SHA-256 values and dependency edges. Outputs contain debug data; these are probe artifacts, not final release sizes.

## Review and next integration boundary

A direct **GPT6.1-Sol Codex subagent** independently inspected actual logs, inner options, generated configuration, compiler metadata, ELF dependencies, the patch and selected-file licenses. The parent reviewed that packet and verified source/archive and output bindings. The native build is accepted with these concrete follow-ups:

1. Preserve the upstream JNI class/package/signatures and callbacks. Use Cloudy's deliberate connection arguments; the upstream profile builder's `/cert:ignore` and automatic clipboard configuration are unsuitable defaults.
2. Resolve Android OpenSSL provider/trust-store paths. The compiled temporary `OPENSSLDIR`/`MODULESDIR` are not device paths, and copying `legacy.so` beside JNI files does not prove packaging or discovery.
3. Preserve file-level licenses: five compiled JNI C files and `LibFreeRDP.java` carry MPL-2.0; `android_cliprdr.c`/`android_disp.c` and core files carry Apache-2.0. The complete distribution decision remains P1-S4.
4. Build the smallest real Android framebuffer/trust/input/cancel integration, inspect the APK and exercise actual JNI loading on the Googlebook. Compilation of camera/audio/printer channels does not authorize requesting redirection.
5. Use a deliberately selected KDE existing-session fixture. Current package resolution proposes broad desktop upgrades; no host package/service/listener changes were made for this result. Record actual negotiation, changing pixels, certificate rejection, input/focus release and teardown before claiming a working RDP lane.

No RDP APK was installed, no remote connection made, and no device/session/permission state changed in this native build increment. Existing terminal evidence remains intact. Continue the integration checks above; do not repeat the accepted build or audit unchanged upstream internals without a concrete new trigger.
