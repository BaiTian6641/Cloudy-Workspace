# Native RDP build probe

This is the reproducible entry for the **actual FreeRDP 3.32.2 Android arm64 native/JNI build** accepted on 2026-10-15. It uses upstream's maintained CMake target, with one build-resource patch. It is not a remote desktop application. Read the [build result](../../../docs/06-validation/2026-10-15-native-rdp-build.md), [MediaCodec/API decision](../../../docs/07-reference/2026-10-15-rdp-android-mediacodec.md) and [stage entry](../../../docs/03-platform/googlebook/p0-s4-native-desktop-entry.md) before integration.

[source-lock.json](source-lock.json) records the exact source/archive, dependency archive hashes, tools and options. The only source change is [bounded-openssl-jobs.patch](patches/bounded-openssl-jobs.patch): both OpenSSL `make -j` invocations become `make -j4`. This prevents its nested build from ignoring the outer four-job limit; it changes no protocol or decoder behavior. Source archives, extracted third-party implementations and binaries remain outside Git.

## Reproduce the accepted configuration

Prerequisites are Linux x86_64, Python with tar extraction filters, Perl, Make, pkg-config, patch, and installed SDK packages **NDK29.0.14206865** and **CMake4.1.2**. The SDK CMake package supplies Ninja1.12.1. Stable NDK r29 deliberately replaces upstream's declared r29 RC1; this is not an exact reproduction of its Gradle toolchain. The upstream Gradle app, database and profile UI are not built here.

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
```

Fetch the exact archive URL in the lock, verify its SHA-256 **before extraction**, and extract it into `cloudy_stage`. Python `tarfile.extractall(filter="data")` is suitable after the hash check; do not reuse a modified source directory. The accepted archive has 3,411 members, including 2,975 regular files. Apply the resource patch once:

```bash
patch --batch --forward -d "$cloudy_src" -p1 \
  < "$cloudy_repo/qualification/probes/native-rdp/patches/bounded-openssl-jobs.patch"
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
"$cloudy_cmake" --build "$cloudy_build" --target freerdp-android --parallel 4
```

Upstream's ExternalProject graph downloads and checks the pinned OpenSSL4.0.1, cJSON1.7.19 and uriparser1.0.2 archive hashes. Preserve configure/build commands, output and exit codes. Verify the inner FreeRDP cache and generated configuration, rather than relying only on outer flags. The successful native target is `cloudy_build/libfreerdp-android.so`; its dependencies are copied into `cloudy_prefix`. No system install is required.

The result manifest binds the actual output bytes. A new build in another directory may differ because it includes debug/build paths; matching source/options alone is not a claim of bit-for-bit reproducibility. Preserve the actual build directory until its outputs and evidence have been retained.

## Packaging handoff

The actual `DT_NEEDED` closure is the JNI library plus `libfreerdp3.so`, `libfreerdp-client3.so`, `libwinpr3.so`, `libssl.so`, `libcrypto.so`, `libcjson.so` and `liburiparser.so`, along with Android system libraries. None of these outputs requires `libc++_shared.so`; setting `ANDROID_STL=c++_shared` alone does not justify adding it. Recheck the closure when inputs change.

`legacy.so` is an OpenSSL provider, outside that link closure. Its lack of a SONAME, filename and compiled provider search directory require an explicit Android packaging/loading decision. The build's `OPENSSLDIR`/`MODULESDIR` point into its temporary prefix; do not use them as a device trust-store configuration. Do not silently enable legacy algorithms or copy every duplicate library directory.

Preserve upstream `com.freerdp.freerdpcore.services.LibFreeRDP` native signatures and callbacks when reusing the JNI target. Its upstream Java bookmark builder enables `/cert:ignore` and clipboard; Cloudy must supply its own deliberate arguments and trust decisions. Several selected JNI C files and the Java bridge carry MPL-2.0 headers, while others and the core use Apache-2.0. Record file-level notices before packaging; product distribution remains P1-S4 work.

All inspected ELF load segments have 16KiB alignment. APK ZIP alignment, JNI class loading, provider behavior, authenticated connections, real decoder output, keyboard/pointer input, cancellation and cleanup remain untested. Build success does not promote CAP-RDP or complete P0-S4.
