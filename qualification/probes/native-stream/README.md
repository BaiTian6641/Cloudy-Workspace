# Moonlight native core build feasibility

This directory contains a **source lock and native preparation recipe**, not an Android app scaffold. No Activity, Gradle package, stream, Surface or device result is implemented here. It is the first concrete build increment from the [native streaming entry](../../../docs/03-platform/googlebook/native-stream-adoption-entry.md), using actual maintained Moonlight code under the [established-library policy](../../../docs/05-delivery/library-reuse-policy.md).

The selected baseline is Moonlight Android **v12.2**, commit `b48494cb96bff23d8886c4775cc4f39a1075495d`, plus exact common-C, ENet and nanors submodules. [sources.lock.json](sources.lock.json) records all four official archive URLs, sizes, SHA256 values and insertion paths; the native patch set is currently empty. The real arm64 static inputs are upstream-bundled Opus 1.6.1/OpenSSL 4.0.2, with their measured hashes and exact source/build provenance recorded separately. No implementation was copied into tracked project source.

## Build and offline check

From repository root with the existing SDK/NDK installed locally:

```bash
python3 tools/prepare_native_stream.py --build --run entry-02
python3 tools/prepare_native_stream.py --check --run entry-02
```

A build requires a **fresh run name** and refuses to overwrite an existing run. Use another descriptive name for a changed-input build. The commands above reproduce the accepted run only in a checkout where that run does not yet exist; otherwise use its offline check. `--sdk PATH` selects a local SDK; the default is `.cache/android-sdk`. No Gradle, SDK installer, listener, root operation, app launch or device access is invoked.

The explicit build operation downloads pinned HTTPS sources/notices only when needed, or reuses the already reviewed research cache after byte/hash checks. Downloads stay in `.cache/native-stream-build/downloads/`. Extraction accepts only regular files/directories under the expected archive root; links, traversal, duplicate archive members and source insertion overwrites are rejected. Source bytes are verified before extraction and used from that verified in-memory archive snapshot.

The build uses **NDK29.0.14206865**, native **Android21/arm64-v8a**, `PRODUCT_FLAVOR=nonRoot`, `APP_MODULES=moonlight-core`, release optimization and four jobs. It retains the upstream Android.mk/Application.mk and NDK cpufeatures module; no protocol or codec implementation is replaced. A fresh `/tmp/cloudy-native-stream-*` source stage and temporary NDK alias support the existing whitespace-containing workspace: Make directly invokes the same upstream `build/core/build-local.mk`, rather than the NDK shell entry that resolves/rejects the real path's spaces. This does not modify/copy/install the NDK.

The process group has a 180-second deadline and an 8MiB compiler-log limit; timeout/interruption/failure uses the accepted TERM/then-KILL descendant cleanup. Authored lock/patch/helper inputs are captured before compilation and checked again before publication. Both imported RDP helper files are hash-bound and included in build notices because they supply existing patch/ELF/process-cleanup behavior. No successful receipt is published until the reconstructed source, actual retained source, native ELF, outputs and evidence verify offline.

A successful run provides:

- `.cache/native-stream-build/RUN/prepared/nativeLibraries/arm64-v8a/libmoonlight-core.so`: the actual stripped library ready for a later JNI-libraries import.
- `prepared/nativeNotices/`: exact client/core/submodule source archives, license text, NDK cpufeatures source/notices and captured authored build inputs/recipe. These are preparation outputs, not an APK or a product-license approval.
- `source/`: retained exact assembled source tree, compared against the pinned archives plus declared patches by offline check.
- `receipt.json`: build reference, captured input hashes, 617-file source binding, compiler identity, native hash/ELF metadata and prepared/evidence hashes.
- `command.json`, `build.log`, `exports.txt`: actual Make argument vector, compiler/link output and exported symbols. Failed preparation retains `failure.json` and does not receive a successful receipt.

`--check` makes no network request and launches no compiler: it verifies current lock/patch/helper inputs, hashes actual prepared files, reconstructs source from the retained verified archives, compares retained source, validates actual AArch64/16KiB ELF/dependencies, and checks evidence integrity. It requires no current device, listener or running stream. Later changes to a bound helper make the historical receipt stale; they do not mutate an already accepted library.

## Actual result and retained failure

**2026-10-15:** `entry-01` compiled and linked the real native engine successfully, then preparation failed because the initial helper used `LICENSE` instead of common-C's actual `LICENSE.txt`. Its compiler log, actual partial library and `failure.json` are preserved. No completed-input/output or notice pass is claimed for that attempt.

Corrected **`entry-02`** completed the actual build and full offline verification with no upstream source patch:

| Identity/check | Result |
| --- | --- |
| Build reference | `f336e245-805d-4aad-bc1a-9208df8db318` |
| Native library | **5,110,376 bytes** |
| SHA256 | **`95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`** |
| ELF | AArch64, SONAME `libmoonlight-core.so`, minimum load alignment 16KiB, no RPATH/RUNPATH |
| Dynamic dependencies | `libc.so`, `libdl.so`, `liblog.so`, `libm.so` only |
| JNI boundary | Actual exports include MoonBridge `init`, `startConnection`, `stopConnection`, `interruptConnection`, `sendMouseMove` |
| Source | 617 regular files reconstructed from the four exact archives; declared patch set empty |

Strict authored Python AST and Ruff checks passed. Six focused preparation checks passed: archive-byte mismatch, archive traversal, changed authored inputs before publication, retained symlink rejection, a fast process exceeding the final log bound, and a real child-process deadline. The maintained tests are [tests/test_prepare_native_stream.py](../../../tests/test_prepare_native_stream.py); the initial private run is retained at `.cache/native-stream-build/helper-checks.log`. These checks are not Android/JNI/protocol fixtures or a hosted CI result. The existing shared process/ELF algorithms were reused instead of creating an upstream conformance suite.

## Next boundary and licensing

No Java ABI loading, MediaCodec output, Android compilation/Lint, APK notice/signature/alignment result, pairing, stream or hardware performance is established. Those require a separate uniquely named qualification APK using actual upstream Java/resource dependencies, an owned `LimelightCryptoProvider`, `NvConnection`, `MediaCodecDecoderRenderer`/Surface and parent-controlled host/device evidence. Preserve process-global engine ownership and finite HTTP/native cancellation; no placeholder decoder, fake session or another app's private keys may stand in for that integration.

Moonlight Android and common-C carry GPLv3 license text; ENet/nanors and the bundled static dependencies retain their own terms. The user selected open-source Desktop with compatible licensing and required source/notices in [ADR0010](../../../docs/adr/0010-desktop-open-source-distribution.md). This permits continued private integration; it does not complete the exact dependency/license/source closure required before distribution. The preparation notice directory carries the four source archives and identified license/build materials for private feasibility. It is neither the final APK asset/source-distribution layout nor a complete corresponding source bundle for the bundled Opus/OpenSSL static libraries; their exact source provenance is pinned, but their complete source/build delivery remains a release gate. Ordinary SSH and native RDP/VNC retain independent delivery and qualification gates; this build introduces no custom Cloudy host adapter.
