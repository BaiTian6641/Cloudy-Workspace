# Native RDP diagnostic

This separate **0.1.5 / code6** Android diagnostic, `dev.cloudy.qualification.rdp`, uses real FreeRDP 3.32.2 JNI and a native framebuffer view. It is an implemented qualification application, not Cloudy Desktop or completed CAP-RDP. Build reference is `a5a5db42-1444-4642-bfff-aa63751a8e4f`; APK **12,746,630 bytes**, SHA256 `565ae8036040e34de98c1c83c1d365c43df83053188b7f65bbd61906578ea1df`. Fresh native compilation, Android assembly/Lint and **45 JVM tests** pass, with zero test failures or skips. Signature, 16 KiB ZIP alignment, eight native libraries and 21 assets pass the package audit. The current source uses the six reviewed patches below.

The [0.1.5 cursor repair](../../results/P0-S4/2026-10-15-rdp-cursor-repair.md) records two framework-confirmed captures and actual releases on HP Googlebook 14 with the isolated GRD 50.2 / Mutter 50.1 fixture. The user confirms **“Host trail and cursor move; release works.”** This bounded result keeps local cursor prediction separate from the host-motion-1 target's actual event trail. Input device subtype/model, authoritative-position/default/hidden runtime cases, broader physical interactions, 3D and performance remain open. The [repair contract](../../../docs/03-platform/googlebook/rdp-cursor-presentation-entry.md) describes the invariants and limits; the product credential vault remains unimplemented.

Historical evidence stays linked: [build03](../../../docs/06-validation/2026-10-15-native-rdp-build.md), [APK/load](../../results/P0-S4/2026-10-15-rdp-apk-load.md), [first trust/NLA sessions](../../results/P0-S4/2026-10-15-rdp-first-sessions.md), [framebuffer resize/display](../../results/P0-S4/2026-10-15-rdp-framebuffer.md), [0.1.3 zero-target input](../../results/P0-S4/2026-10-15-rdp-input-boundary.md), [KRdp portal/relative findings](../../results/P0-S4/2026-10-15-rdp-relative-portal.md), [isolated-host readiness](../../results/P0-S4/2026-10-15-rdp-isolated-host.md) and [0.1.4 physical cursor failure](../../results/P0-S4/2026-10-15-rdp-physical-cursor.md). Prior failures and unmeasured lag are not overwritten by the repaired result. The separate [native CI workflow](../../../.github/workflows/android-native-rdp.yml) builds pinned native sources and the real APK; the [first complete hosted run](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38782707586) passed the earlier `cdc9163` inputs. Existing baseline CI and that historical run do not certify this new increment.

Read the [MediaCodec/API decision](../../../docs/07-reference/2026-10-15-rdp-android-mediacodec.md), [stage entry](../../../docs/03-platform/googlebook/p0-s4-native-desktop-entry.md) and [library-reuse policy](../../../docs/05-delivery/library-reuse-policy.md). Reuse upstream protocol/codec behavior; target Cloudy's authored trust, lifecycle, input and packaging boundaries rather than recreate upstream conformance suites.

[source-lock.json](source-lock.json) records the exact source/archive, dependency archives, tools and options. Apply these patches in order:

1. [bounded-openssl-jobs.patch](patches/bounded-openssl-jobs.patch): bound both nested OpenSSL make invocations to four jobs; no protocol change.
2. [android-session-ownership.patch](patches/android-session-ownership.patch): make the event queue and native thread/instance ownership suitable for bounded cancellation and cleanup.
3. [android-external-certificate.patch](patches/android-external-certificate.patch): delegate the actual PEM certificate to the diagnostic's explicit trust owner.
4. [android-key-validation.patch](patches/android-key-validation.patch): reject unmapped key codes before enqueueing scan-code zero.
5. [android-relative-input.patch](patches/android-relative-input.patch): expose negotiated standard relative mouse capability and a bounded signed-relative event queue route, with consumer-side capability/state checks and no absolute or AINPUT fallback.
6. [android-pointer-position.patch](patches/android-pointer-position.patch): forward the real server SetPosition callback through the instance-scoped Java bridge; this patch changes presentation feedback, not input or wire behavior.

Keep the actual source, build directory and cached SDK/dependencies for subsequent imports and adapter checks. Upstream implementations and generated binaries remain outside Git. A changed patch requires rebuilding the affected native target and a new matching receipt; context changes alone do not require repeating unchanged evidence.

## Reproduce the accepted configuration

Prerequisites are Linux x86_64, Python with tar extraction filters, Perl, Make, pkg-config, patch, and installed SDK packages **NDK 29.0.14206865** and **CMake 4.1.2**. The SDK CMake package supplies Ninja 1.12.1. Stable NDK r29 deliberately replaces upstream's declared r29 RC1; this is not an exact reproduction of its Gradle toolchain. The upstream Gradle app, database and profile UI are not built here.

From the repository root, use a real source/build directory without spaces and an SDK alias. The first run failed inside OpenSSL's generated Makefile when the source path contained `Cloudy Workspace`; the second needed a writable ccache directory. Both failures are retained in the result. Do not change global SDK or ccache configuration to work around these local issues.

```bash
set -euo pipefail
cloudy_repo="$PWD"
cloudy_stage="$(mktemp -d /tmp/cloudy-rdp-XXXXXXXX)"
cloudy_rev=97eff6ddfbbfaa8e42a126d362686bd001ff8bdc
ln -s "$cloudy_repo/.cache/android-sdk" "$cloudy_stage/sdk"
cloudy_cmake="$cloudy_stage/sdk/cmake/4.1.2/bin/cmake"
cloudy_ndk="$cloudy_stage/sdk/ndk/29.0.14206865"
cloudy_src="$cloudy_stage/FreeRDP-$cloudy_rev"
cloudy_build="$cloudy_stage/build"
cloudy_receipt="$cloudy_repo/.cache/native-rdp/receipt-${cloudy_stage##*/}.json"
```

Fetch the exact archive URL in the lock, verify its SHA-256 **before extraction**, and extract it into `cloudy_stage`. Python `tarfile.extractall(filter="data")` is suitable after the hash check; do not reuse a modified source directory. The accepted archive has 3,411 members, including 2,975 regular files. For the current diagnostic, apply all six patches once to the pristine source:

```bash
for cloudy_patch in bounded-openssl-jobs android-session-ownership android-external-certificate android-key-validation android-relative-input android-pointer-position; do
  patch --batch --forward -d "$cloudy_src" -p1 \
    < "$cloudy_repo/qualification/probes/native-rdp/patches/$cloudy_patch.patch"
done
cloudy_prefix="$cloudy_src/client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a"
export CMAKE_BUILD_PARALLEL_LEVEL=4
export PKG_CONFIG_PATH=""
export PKG_CONFIG_LIBDIR="$cloudy_prefix/lib/pkgconfig"
export CCACHE_DIR="$cloudy_stage/ccache"
"$cloudy_cmake" \
  -S "$cloudy_src/client/Android/Studio/freeRDPCore/src/main/cpp" \
  -B "$cloudy_build" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$cloudy_stage/sdk/cmake/4.1.2/bin/ninja" \
  -DCMAKE_TOOLCHAIN_FILE="$cloudy_ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DFREERDP_DEFAULT_PROJECT_VERSION=3.32.2 \
  -DWITH_OPENSSL=ON -DWITH_CJSON=ON -DWITH_MEDIACODEC=ON \
  -DWITH_FFMPEG=OFF -DWITH_OPENH264=OFF -DWITH_OPUS=OFF \
  -DWITH_PNG=OFF -DWITH_WEBP=OFF -DWITH_JPEG=OFF
python3 "$cloudy_repo/tools/build_rdp_native.py" \
  --source "$cloudy_src" --build "$cloudy_build" \
  --archive "$cloudy_repo/.cache/native-rdp/FreeRDP-$cloudy_rev.tar.gz" \
  --receipt "$cloudy_receipt"
```

Upstream's ExternalProject graph downloads and checks the pinned OpenSSL 4.0.1, cJSON 1.7.19 and uriparser 1.0.2 archive hashes. Preserve configure/build commands, output and exit codes. Verify the inner FreeRDP cache and generated configuration, rather than relying only on outer flags. The successful native target is `cloudy_build/libfreerdp-android.so`; its dependencies are copied into `cloudy_prefix`. No system install is required.

The result manifest binds the actual output bytes. A new build in another directory may differ because it includes debug/build paths; matching source/options alone is not a claim of bit-for-bit reproducibility. Preserve the actual build directory until its outputs and evidence have been retained.

## Prepare and build the separate APK

The actual `DT_NEEDED` closure is the JNI library plus `libfreerdp3.so`, `libfreerdp-client3.so`, `libwinpr3.so`, `libssl.so`, `libcrypto.so`, `libcjson.so` and `liburiparser.so`, along with Android system libraries. None of these outputs requires `libc++_shared.so`; setting `ANDROID_STL=c++_shared` alone does not justify adding it. Recheck the closure when inputs change.

`legacy.so` is an OpenSSL provider, outside that link closure. Its lack of a SONAME, filename and compiled provider search directory require an explicit Android packaging/loading decision. The build's `OPENSSLDIR`/`MODULESDIR` point into its temporary prefix; do not use them as a device trust-store configuration. Do not silently enable legacy algorithms or copy every duplicate library directory.

Preserve upstream `com.freerdp.freerdpcore.services.LibFreeRDP` native signatures and callbacks when reusing the JNI target. Its upstream Java bookmark builder enables `/cert:ignore` and clipboard; Cloudy must supply its own deliberate arguments and trust decisions. Several selected JNI C files and the Java bridge carry MPL-2.0 headers, while others and the core use Apache-2.0. Record file-level notices before packaging; product distribution remains P1-S4 work.

The historical build03 inspected ELF load segments had 16 KiB alignment. That record did not test APK ZIP alignment, JNI class loading, provider behavior, authenticated connections, decoder output, input or cleanup. The current diagnostic adds real adapter code and local JVM checks; actual APK/device observations must be recorded separately. Build success does not promote CAP-RDP or complete P0-S4.

The build helper issues a fresh receipt only after the actual native target exits successfully and its source and eight output identities pass. It refuses to overwrite a prior receipt/log. The importer performs no download or build and has no dummy or fallback libraries. Supply the real modified source, completed build, pinned pristine archive, canonical MPL text and matching private build receipt:

```bash
python3 tools/prepare_rdp_native.py \
  --source "$cloudy_src" --build "$cloudy_build" \
  --archive ".cache/native-rdp/FreeRDP-$cloudy_rev.tar.gz" \
  --mpl-license qualification/probes/native-rdp/licenses/MPL-2.0.txt \
  --receipt "$cloudy_receipt"
python3 tools/prepare_rdp_native.py --check
./qualification/probes/device-baseline/gradlew \
  -p qualification/probes/native-rdp \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Run from the repository root using the existing qualified JDK/SDK and project-local Gradle cache. This reuses the shared checked-in wrapper without introducing a second Gradle distribution; Gradle `-p` selects the separate native project. Missing prepared inputs fail preBuild rather than generating a fabricated session. The importer verifies the pristine archive plus reviewed patches, receipt, ELF ABI/alignment/dependency closure and exact eight-library output set. Its offline `--check` rejects missing/extra prepared libraries, changed notices/hashes or changed lock/patch inputs. Generated libraries are under `app/build/generated/nativeLibraries/arm64-v8a`; notices, manifest and actual corresponding native bridge source archive are under `app/build/generated/nativeNotices` and bundled as APK assets. Notice text alone does not replace corresponding MPL source; the archive contains the modified bridge files/headers and patch recipe.

## Fresh setup and hosted compiler coverage

The [setup helper](../../../tools/setup_rdp_native.py) implements verified acquisition and the configure-only part of the recipe above. It requires an installed selected SDK and a fresh stage path without whitespace. It verifies each cached/downloaded archive, applies the six exact patches, checks all original source files, seeds upstream ExternalProject download locations with the verified dependency archives and checks the resulting outer configuration. It does not compile or install anything:

```bash
cloudy_stage=/tmp/cloudy-rdp-fresh-stage # Must not already exist.
python3 tools/setup_rdp_native.py --sdk "$ANDROID_HOME" --stage "$cloudy_stage"
cloudy_rev=97eff6ddfbbfaa8e42a126d362686bd001ff8bdc
cloudy_src="$cloudy_stage/FreeRDP-$cloudy_rev"
cloudy_build="$cloudy_stage/build"
cloudy_receipt="$PWD/.cache/native-rdp/fresh-receipt.json" # Must be a fresh name.
python3 tools/build_rdp_native.py --source "$cloudy_src" --build "$cloudy_build" \
  --archive ".cache/native-rdp/downloads/FreeRDP-$cloudy_rev.tar.gz" --receipt "$cloudy_receipt"
```

Then run the import/Gradle commands above using the `downloads/` archive path. The checked-in [canonical MPL text](licenses/MPL-2.0.txt) matches the importer's required hash, removing the previous implicit local license-file prerequisite.

The separate hosted workflow selects Ubuntu24.04/Temurin21, verifies the same wrapper and SDK package revisions, builds the actual locked native graph, issues a successful receipt, imports all eight real libraries and runs assembly, Android Lint and JVM tests. Only source/dependency downloads are cached; every cache hit is rehashed. Native outputs are built for each run, with no fallback artifact or skipped integrity gate. Configure/build logs, receipt and test/Lint reports are retained for seven days. The [first complete hosted run](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38782707586) passed for the earlier four-patch `cdc9163` configuration; it does not qualify the later relative/position patches or 0.1.5 APK. See the [CI review](../../../docs/06-validation/2026-10-15-native-rdp-ci-review.md) for its exact evidence boundary. Device queue execution, JNI loading, certificate callbacks, authenticated desktops, input and lifecycle remain separate evidence.

## Private fixture configuration and finite operation

The operator provisions `files/rdp-config.json` in this application's private storage. Do not commit or publish it. It must contain a JSON object no larger than 16 KiB with these fields; no credential-bearing example is supplied:

| Field | Requirement |
| --- | --- |
| `schemaVersion` | Integer 1 |
| `host` | 1–253 ASCII letters/digits/dots/hyphens; cannot begin with a hyphen; no IPv6 literal support in this parser |
| `port` | Integer 1–65535 |
| `username` | 1–256 characters, no NUL |
| `domain` | Optional, defaults empty; at most 256 characters, no NUL |
| `password` | 1–4096 characters, no NUL; private fixture credential |
| `certificateSha256` | Exactly 64 hexadecimal digits: independently approved SHA-256 of the DER leaf certificate for this host/port |
| `width`, `height` | Optional 1280×720 defaults; width 640–2560, height 480–1600, total at most 4,096,000 pixels |

NLA is mandatory. The external certificate callback enforces the endpoint-bound leaf pin, rather than generic trust-on-first-use or a CA-store claim. Obtain the pin from the selected host certificate through the operator's deliberate fixture preparation; the diagnostic does not silently accept an unknown certificate. Native authentication receives the private configured credential. Missing/invalid configuration, unavailable native library, rejected pin or journal failure produces an honest failure state.

Connect/cancel/disconnect operate on the real native session. One session owner retains the native handle until actual thread termination/join and instance cleanup; a recreated Activity cannot take it over while cleanup remains pending. Activity stop/destroy requests shutdown, and focus loss releases held input. The finite session deadline is ten minutes; there is no automatic reconnect/background-session feature.

The bounded atomic journal, `files/rdp-journal.json`, stores at most 16 attempts and 262,144 bytes, with up to 80 native events per attempt, up to 32 separate capture edges and bounded counters. It records intent before native allocation and retains interrupted attempts; it does not contain the endpoint or credentials. **Preserve/export the journal and matching private evidence before rotating it**. At 16 attempts, further connections refuse to start; do not clear app data or overwrite earlier evidence merely to continue. Use a private operator-owned export; a journal outcome alone does not replace observation of server disconnect/resource cleanup or actual changing pixels.

The native view presents the software GDI RGBX framebuffer through Bitmap copies, with experimental MediaCodec byte-buffer decode and native YUV conversion. This is a qualification path, not zero-copy rendering or measured high-quality/low-latency output. Keyboard handling is a bounded US virtual-key path; the separate native IME/text composer sends committed Unicode text. Neither path establishes arbitrary layouts, full physical keyboard/IME/accessibility acceptance or general terminal semantics. Clipboard, audio, gateway, RAIL/remote-app and device redirection are outside this diagnostic's enabled connection scope. No VNC, streaming, product Desktop or broader server support claim follows from local compilation or the first finite connection.

## Standard relative pointer qualification path

Version 0.1.4 adds the real JNI APIs `freerdp_is_relative_mouse_supported(long)` and `freerdp_send_relative_cursor_event(long, int dx, int dy, int flags)`. The capability getter reads an atomic connected/negotiated snapshot; cancellation closes admission permanently for that native instance. The producer validates signed-16-bit deltas and allowed move/button flags before allocation. The native consumer rechecks capability and active input state before calling FreeRDP's standard `freerdp_input_send_rel_mouse_event`. A successful JNI submission proves queue acceptance only, not wire or host-application delivery.

The visible Capture action requires a connected session with negotiated support, a focused window and the remote frame's focus. The framework capture callback confirms the actual capture state; a request without confirmation times out after three seconds. An unsupported server is shown explicitly and cannot activate capture. Capture is never restored automatically. The Release action, input menu and local **Ctrl+Alt+Shift+R** chord release ownership; focus/capture loss, disconnect and Activity lifecycle also release held input. Native composer-field shortcuts remain framework-owned, and the existing explicit Send Unicode path is unchanged.

Captured MOVE/HOVER_MOVE events consume historical and current `getX/getY` deltas with fractional remainders, bounded signed-16-bit splitting, and finite/overflow guards. They use no framebuffer scale or absolute clamp. Button and wheel callbacks do not replay movement deltas. Only explicit button presses acquire ownership; movement or a late release cannot rearm a button held across ownership loss. Button releases follow the route on which they were pressed and precede modifier key releases. Wheels retain their ordinary wheel-only route without an absolute MOVE fallback. The journal retains attempt-scoped negotiated support and relative enqueue counts through close, without rewriting earlier closed attempts.

The historical 0.1.4 compiler/assembly, Android Lint and 36-test JVM gate passed for that source; current 0.1.5 has 45 passing JVM tests. The five added relative-policy tests cover fractional accumulation/reset, signed bounds/splitting, history order, atomic finite/batch rejection and modifier-prefix ownership. The [device report](../../results/P0-S4/2026-10-15-rdp-relative-portal.md) keeps the observed native queue, negotiation and capture results separate from the failed captured movement, user-confirmed local release and pending broader physical/3D cases. These observations do not qualify other host implementations, arbitrary keyboard layouts, the product vault or sustained performance.

## Cursor presentation and capture evidence

Version 0.1.5 forwards `OnPointerSetPosition(long, int, int)` to the instance-checked session and separates default/custom/server-hidden cursor state. Confirmed capture draws a software default arrow when requested; server-hidden remains hidden. Successful relative JNI admission advances only an explicitly labelled, framebuffer-clamped local prediction. Real server feedback reconciles it; a position revision crossing admission wins over the predicted delta. Geometry, connection and capture-ownership changes reset the estimate. Absolute and relative cursor-only movement use the existing coalesced redraw. No prediction emits absolute input, recenters the host or changes signed wire deltas.

Capture records distinguish request, framework confirmation, release request and actual released/lost callback, with bounded reasons and session identity. UI-edge monotonic time/counters and later owner observations are separate. At most 32 edges are retained outside the 80 native events; omission is counted. Retired attempts reject stale callbacks, and final persistence can drain already captured evidence even when native reap wins the queued task.

Attempt 13 records **565 frames, 1,678 input submissions and 1,037 relative submissions** across two confirmed capture/release intervals (**619 + 418**). The host target records 824 actual mouse moves, and the user confirms visible host trail/cursor movement and release. Prior twelve attempts remain preserved. Target operator-close exits 0 with zero held input; native join/free succeeds while the client retains its **native-failure/server-logoff** category. See the [actual result](../../results/P0-S4/2026-10-15-rdp-cursor-repair.md) for exact evidence. These observations do not separately prove live authoritative callbacks, default/hidden runtime cases, latency or 3D behavior.

## First-failure diagnostics and current bitmap boundary

Version 0.1.1 adds an immutable, attempt-bound `firstFailure` journal field: authored operation, bounded exception class, relevant integer slots and relative elapsed time. It excludes exception messages, stacks, endpoint/credential/certificate data and pixels. The owner persists it before stop and after native join even if a queued failure notification loses the race to the observer. Earlier closed attempt records remain unchanged.

Version 0.1.2 accepts the observed 2880×1800 server resize within a 20 MiB budget for one ARGB8888 bitmap and the existing 4096-per-edge limit. It checks both nominal geometry with Long arithmetic and actual `allocationByteCount` before publication. The initial requested size remains capped at 4,096,000 pixels; a later server resize is independent. No forced bitmap recycling or decoder/native change was added. Old/new overlap, GDI, codec and GPU allocations are additional memory; the [device result](../../results/P0-S4/2026-10-15-rdp-framebuffer.md) retains observed resource costs and pending sustained-use qualification.

The [portal/relative result](../../results/P0-S4/2026-10-15-rdp-relative-portal.md) now records the physical exercise: local release worked, but captured remote cursor movement failed on KRdp6.4.5. The server omits the relative callback despite advertised capability. This is not a 3D pass; separate mouse/trackpad models and broader release cases remain open.
