# Moonlight native core and local Android loading probe

**Current checkpoint:** 0.1.10/code11 is installed, tested and stopped. The [finite visible-preview result](../../results/P0-S4/2026-10-15-stream-visible-preview.md) records the user's explicit confirmation: “Yes, the video is visible and moving.” This follows real catalog completion, actual ID selection and fresh device-credential authorization. The correlated PLAY records native start 0, H.264 1280×720, 1,533 decode submissions, 1,518 codec-render callbacks and 1,361 actual TextureView updates with attached/shown/hardware 951×534 geometry. These measurements remain separate from the visual verdict. The original authorization deadline closes worker/controller/surface ownership. All 30 records are preserved, including all 22 prior records byte-identically; all network records are closed. Host media-03 exits 0 with seven ports released, the temporary profile is deleted and the app is stopped. Preserve the [0.1.9 visual failure](../../results/P0-S4/2026-10-15-stream-decoder-session.md), [comprehensive review](../../../docs/06-validation/2026-10-15-stream-comprehensive-review.md) and [current execution state](../../../docs/05-delivery/execution-state.md). Audio, input, performance, sustained operation, broader lifecycle, product readiness and CAP-STREAM remain unqualified. The recipes and chronology below are historical; do not repeat their “next” work or create new keys. The stream overlay uses checksum-pinned Gradle **9.7.1**; the separate baseline app uses 9.6.0. The accepted native library is unchanged.

Current source staging applies base Android overlay plus pairing, playback, catalog and compatibility overlays in their reviewed order. Use a new stage only for changed inputs; preserve old APKs/stages/receipts. Current build evidence is [0.1.10 presentation/retirement](../../results/P0-S4/2026-10-15-stream-presentation-build.md): 681 staged files, 60 JVM tests, zero owned Lint, same 39 runtime artifacts. It is built/reviewed and now has the finite device result above. Use final stage `presentation-03`; the first `presentation-01` has a preserved unused-resource warning/audit correction. Read the [repair review](../../../docs/06-validation/2026-10-15-stream-presentation-review.md) for the source and ownership invariants. Earlier source counts/version names below apply only to the named historical checkpoint. One native lifetime per process and original 30-second authorization are diagnostic restrictions, not completed product reconnect behavior.
The diagnostic UI does not ingest external user acceptance and still contains historical wording that visual playback has not passed. Treat the correlated result above as current evidence; a neutral wording follow-up is pending. This does not justify a new runtime claim or build. Preserve/archive the 15 identity records before further fresh-process runs; the 16-record bound is unchanged.


## Historical native loading and identity development

This directory contains a **source lock, native preparation recipe and real Android local ABI diagnostic**. The actual separately named APK has loaded the native core on Googlebook; pairing, a stream and Surface output remain unimplemented/unqualified. It advances the [native streaming entry](../../../docs/03-platform/googlebook/native-stream-adoption-entry.md), using actual maintained Moonlight code under the [established-library policy](../../../docs/05-delivery/library-reuse-policy.md).

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

## Historical first-load boundary and continuing licensing obligations

The [APK loading result](../../results/P0-S4/2026-10-15-native-stream-apk-load.md) establishes real Android assembly, scoped Lint, selected notices/signature/alignment and one local JNI call. MediaCodec output, pairing, streaming and hardware performance remain unqualified. Next use the reviewed [app-owned key provider](../../../docs/03-platform/googlebook/native-stream-key-provider-entry.md), `NvConnection`, `MediaCodecDecoderRenderer`/Surface and parent-controlled host/device evidence. Preserve process-global engine ownership and finite HTTP/native cancellation; no placeholder decoder, fake session or another app's private keys may stand in for that integration.

Moonlight Android and common-C carry GPLv3 license text; ENet/nanors and the bundled static dependencies retain their own terms. The user selected open-source Desktop with compatible licensing and required source/notices in [ADR0010](../../../docs/adr/0010-desktop-open-source-distribution.md). This permits continued private integration; it does not complete the exact dependency/license/source closure required before distribution. The preparation notice directory carries the four source archives and identified license/build materials for private feasibility. It is neither the final APK asset/source-distribution layout nor a complete corresponding source bundle for the bundled Opus/OpenSSL static libraries; their exact source provenance is pinned, but their complete source/build delivery remains a release gate. Ordinary SSH and native RDP/VNC retain independent delivery and qualification gates; this build introduces no custom Cloudy host adapter.


## Android staging, build and actual local loading

The owned overlay is `android/`; actual pinned upstream Java/resources are staged privately, not copied into tracked source. With accepted native `entry-02` already available, run from repository root with the existing JDK21/SDK37 and checksum-pinned Gradle9.7.1 wrapper:

```bash
python3 tools/prepare_stream_android.py --prepare --stage entry-05 --native-run entry-02
python3 tools/prepare_stream_android.py --check --stage entry-05
repository_root="$(pwd)"
cd .cache/native-stream-android/entry-05
./gradlew --no-daemon -PcloudyRepositoryRoot="$repository_root" \
  :app:assembleDebug :app:lintDebug :app:recordCloudyRuntimeDependencies
```

Preparation requires a fresh stage; use another name when `entry-05` exists. Offline check verifies retained sources without rebuilding. Pre-build verification reconstructs 633 actual source/overlay/native inputs from accepted archives and rejects changed or extra build/source payloads. Android native import reuses the exact accepted library; Gradle does not invoke another native build. The resolved debug-runtime dependency task records fourteen actual artifacts and hashes for this accepted stage; it is not a final source/license SBOM.

Application ID `dev.cloudy.qualification.stream`, namespace `com.limelight`, **0.1.0/code1**, build reference `04cf72ef-ece2-4e15-b072-ef6e73dd203c`: minimum35, compile37, **target36 inherited from upstream**. Target37 is a later behavior review. Entry05 APK is **21,713,253 bytes**, SHA256 `b674ab7c55e4775ed3f79941b3ec95fa6acb20cd26e864d6f483f3146ca0958a`; actual packaged core bytes match native entry02. Signature and 16 KiB native ZIP alignment pass. The final manifest has one owned Activity and zero permissions/providers/services/receivers. Explicit merger removals prevent dependency-added INTERNET and AndroidX initialization from becoming runtime capability.

The owned native Views screen performs real `MoonBridge` class/native initialization and `getLaunchUrlQueryParameters` on a worker, then atomically records bounded evidence. It does not construct upstream discovery/pairing/key/session objects. On the identified Googlebook, the sole local record reports `native-load-and-metadata-passed`, metadata `&corever=1`, zero host connections and zero decoded frames. Its 2,813,594ns duration is one first local observation, not a latency benchmark. Success UI was observed, no screenshot captured; the app is now stopped with its APK retained. Read the [result/manifest](../../results/P0-S4/2026-10-15-native-stream-apk-load.md) and [build/review](../../../docs/06-validation/2026-10-15-native-stream-apk-load.md).

The final Lint gate has zero new errors, 337 warnings and one baseline hint. Exactly 27 original errors in inactive pinned upstream paths are baselined, with no owned code included. Retire affected entries before enabling their network/controller/Activity paths. Existing upstream translation exceptions remain separate. The node-local MissingClass ignore applies only to the removed initializer marker and was independently reviewed against the actual runtime manifest; it does not hide a missing runtime component. Five focused Android-preparation tests pass; no stream test is implied.

Retain Android entry01 (compiled, Lint27errors), entry02 (baseline path failure), entry03 (prepared-only/superseded) and entry04 (removal-marker Lint false positive). Entry05 passed with the reviewed narrow marker annotation. The initially failing device layout predicate expected a nonempty window title; the corrected unique-heading/focused-component check required no app repair. These Android attempts are distinct from native entry01/02 history.

Selected native notice text is packaged, plus dependency public-suffix data; full source archives/build materials stay in the native evidence bundle. Complete transitive notices, source/build delivery and the final compatible license expression remain required under D16/ADR0010 before distribution. Next implement real non-exportable app-owned identity and pairing authorization/cancellation, then actual decoder/audio/Surface streaming. There is no placeholder stream button or borrowed Moonlight/Console key, and no CAP-STREAM, vault, hardware decode, latency or 3D pass.


## Historical local identity diagnostic (0.1.2–0.1.3)

Version0.1.2/code3 adds a real app-owned Android Keystore provider and retained foreground authentication UI. The [result](../../results/P0-S4/2026-10-15-stream-identity.md) records actual pending TEE-key generation, both user-confirmed prompts, failed plain-JCA signing before certificate creation, compiler/three-test/Lint and package audit evidence. It has biometric permissions but no network permission. Identity requiresAPI37; the unchanged native-load path remains min35. Pairing/TLS/playback are unavailable.

The final frozen stage is `.cache/native-stream-android/identity-04`; run `python3 tools/prepare_stream_android.py --check --stage identity-04` while inputs still match. New source builds require a fresh stage and `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:recordCloudyRuntimeDependencies`. Preserve historical stages and their captured inputs. Existing upstream Lint baseline entries apply only to inactive paths; zero owned findings is required. The next planned credential-only control must reuse the pending key and preserve authentication/cancellation policy.


The [0.1.3 credential follow-up](../../results/P0-S4/2026-10-15-stream-credential-control.md) adds an explicit screen-lock-only control and fixes app-guard stage labels. Five JVM tests/compiler/scoped Lint pass; the actual same-key credential signature/certificate enrollment and fresh-process READY reload pass on Googlebook. That checkpoint's frozen stage is `identity-05`; its offline check and build reference are recorded in that result. Historical0.1.2 inputs remain in their own receipt/commit; do not verify an old stage against changed current source and overwrite its history. Biometrics remain unqualified; TLS/pairing/playback remain unavailable.
