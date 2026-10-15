# Native streaming core — source and build review

**2026-10-15, P0-S4.** The real Moonlight Android native core compiled for arm64 and passed offline source/output verification. This establishes a concrete native build input for the next Android adapter increment. It establishes no APK, runtime JNI load, pairing, Surface/MediaCodec output, streaming, latency or physical-device result. The integration leader and independent direct Codex reviewer accepted this bounded source/build result after inspecting the recipe, lock, actual Make log, 617 retained source files, 21 prepared outputs, ELF and JNI exports; no blocking defects remained.

The [source lock](../../qualification/probes/native-stream/sources.lock.json), [recipe](../../tools/prepare_native_stream.py), [probe README](../../qualification/probes/native-stream/README.md) and [adoption entry](../03-platform/googlebook/native-stream-adoption-entry.md) describe the implementation boundary. Official source pins are Moonlight Android **v12.2 / `b48494cb96bff23d8886c4775cc4f39a1075495d`**, common-C `874ac9548f1bd6f095ef2b435c42cdde460e7821`, ENet `aca87840b57f045a1f7f9299e4b1b9b8e2a5e2f1` and nanors `b1e3c22ca0cdc0bb83e3cd6ed1a2fc77869ed99a`. The four exact official archive URLs, byte sizes and SHA256 values are in the lock; no upstream patch is applied. The Android source archive supplies measured static Opus 1.6.1/OpenSSL 4.0.2 libraries, with their exact source/build provenance pinned separately.

The actual recipe invokes upstream NDK `build/core/build-local.mk`, Android.mk and Application.mk using **NDK29.0.14206865**, native Android21, `arm64-v8a`, `PRODUCT_FLAVOR=nonRoot`, release optimization and four jobs. A fresh no-space temporary source stage avoids the existing NDK shell wrapper's workspace-space rejection. The SDK is unchanged. Existing reviewed ELF, patch and process-group cleanup helpers are reused and both transitive helper files are included in the captured input hashes and preparation notices.

| Accepted native candidate | Value |
| --- | --- |
| Run / build reference | `entry-02` / `f336e245-805d-4aad-bc1a-9208df8db318` |
| Library | `libmoonlight-core.so`, **5,110,376 bytes** |
| SHA256 | **`95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`** |
| ELF | AArch64; matching SONAME; minimum load alignment 16KiB; no RPATH/RUNPATH |
| Dependencies | `libc.so`, `libdl.so`, `liblog.so`, `libm.so` only |
| JNI exports | Actual MoonBridge `init`, `startConnection`, `stopConnection`, `interruptConnection`, `sendMouseMove` symbols |
| Integrity | 617 assembled regular source files; 21 prepared files; empty patch set |

Inputs are captured before compilation and checked before publication. Verified archive bytes are the bytes extracted; traversal, links, duplicate members and overwrites are rejected. The build has a 180-second process-group deadline and 8MiB compiler-log limit, including a final bound after fast successful exit. Success publication follows offline reconstruction and verification of retained source, native ELF, prepared outputs and evidence. `--check` needs neither network nor compiler/device access.

The initial **`entry-01`** completed real compilation/linking but preparation failed on a wrong common-C notice filename (`LICENSE` versus actual `LICENSE.txt`). Its private `failure.json`, build log and partial output remain retained; it has no accepted success receipt. Correcting the notice path and completing final-log/publication checks justified `entry-02`; no further native rebuild is required merely to add tests or these records.

Six maintained [helper tests](../../tests/test_prepare_native_stream.py) passed: changed archive bytes, traversal, changed authored inputs before publication, retained symlinks, a fast process over the log bound and a real child-process deadline. These test Cloudy's preparation boundary; they do not emulate Android/JNI or recreate upstream protocol tests. Authored AST (20 files), focused Ruff and project documentation validation passed; the integration leader owns the remaining shared checks.

Exact private evidence is `.cache/native-stream-build/entry-02/{receipt.json,command.json,build.log,exports.txt,source/,prepared/}`; the package input is `.cache/native-stream-build/entry-02/prepared/nativeLibraries/arm64-v8a/libmoonlight-core.so`. Earlier failure evidence is `.cache/native-stream-build/entry-01/failure.json`. The [sanitized result](../../qualification/results/P0-S4/2026-10-15-native-stream-build.json) binds evidence hashes without publishing downloaded implementations or host inventories.

[ADR0010](../adr/0010-desktop-open-source-distribution.md) adopts open-source Desktop with compatible licensing and required source/notices. Private integration may proceed. The preparation notice directory carries exact client/core/submodule archives and identified notices/build inputs; it is **not** a final APK asset/source-distribution layout or a complete corresponding source bundle for bundled Opus/OpenSSL. Exact compatible dependency closure, required source/build delivery and recipient access remain release gates. Next integration must use actual upstream Java/Surface/MediaCodec boundaries and Desktop-owned trust/pairing/key storage, with finite cancellation and process-global engine ownership; no other application's private keys or invented stream results qualify that work.
