# P0-S4 — VNC authentication timeout build

**2026-10-15:** the real native adapter and Android **0.1.1/code 2** build passed compilation, focused tests, Android Lint and package integrity review. Build reference is **`ebfc6f16-0b7a-4ace-8849-23e9bc748492`**. This is a build checkpoint: **0.1.1 has not been installed**. The existing installed VNC 0.1.0 remains stopped. The [manifest](2026-10-15-vnc-auth-timeout-build.json) binds exact authored inputs, native and Android preparation receipts, final APK, audit and retained failure evidence.

The prior two-second read idle timeout could expire during a normal PAM authentication delay. The authored adapter now permits a fixed **30-second authentication phase**, clipped to the original total monotonic session deadline, with a **15-second authentication read idle timeout**. After successful initialization it restores the **two-second session read idle timeout**. The three-second connection timeout, maximum 600-second session and existing cancellation path remain in force. This does not modify the host PAM stack, authentication policy, X509Plain-only selection or exact certificate leaf SHA256 and validity checks.

Source review found an important boundary gap: successful `rfbClientInitialise()` could return after the authentication deadline, after which clearing that deadline would admit the session. The accepted source rechecks `continuing(client)` immediately after initialization and before clearing the phase deadline or reporting connected. The policy tests cover deadline clipping, an already-expired total deadline and overflow-safe arithmetic; they also assert the authentication and session idle budgets. This is source and host-policy evidence, not a live PAM timing pass.

| Final artifact | Bytes | SHA256 |
| --- | ---: | --- |
| APK `dev.cloudy.qualification.vnc` 0.1.1/code 2 | 10,888,191 | `37ed5c92c5a385ce357df0bf2204c4031d6c131e76d2990238875909b878ba11` |
| `libcloudy-vnc-jni.so` | 3,523,672 | `912dc8dd17f41ce458ed5b65a8387dca2875ef49913b0eb67ccbc897dda31f45` |
| Reused `libssl.so` | 1,205,848 | `66288be31a05675f6a319632d6abb137eb38b07f10eea302a8b128bd6ed7d5c0` |
| Reused `libcrypto.so` | 6,020,992 | `46c0eb8c3c601f3c9d8746e051aefe61e7a324bf152a535feac3f97472deb2d8` |

The real native build uses `.cache/native-vnc/adapter/auth-timeout-01/`, shared adapter-family label `vnc-adapter-x509plain-2026-10-15`, and the accepted base `.cache/native-vnc/entry-03/receipt.json`. LibVNCClient remains pinned to `98aaee0560baa0332339b2ef464ea96ed9b9b28c`; the existing security/cancellation upstream patch is unchanged. The adapter is rebuilt against the accepted TLS/JPEG closure. The native label is not a unique payload identifier: the exact run/receipt hash, library hashes and unique app build reference distinguish this rebuild. Its receipt binds the exact C/Java/lock/patch/helper inputs, source preparation, compiler commands, exports and prepared outputs. NDK 29.0.14206865, Clang 21, CMake 4.1.2 and Android API29 native targeting are retained. Authored C passed strict warnings as errors and no-undefined linking; the Java JNI ABI passed `--release 17 -Xlint:all -Werror`.

Three focused adapter tests passed, including **37 actual host C policy assertions** compiled with strict warnings, ASan/UBSan and real host OpenSSL. The tests are authored boundary coverage, not an exhaustive upstream retest or Android sanitizer run. The final Android stage `.cache/native-vnc-android/auth-timeout-02/` passed assemble, Android Lint with warnings as errors, **seven JVM tests** and runtime dependency recording. Python AST checked 28 files and targeted Ruff passed. Native offline verification and final Android offline verification passed; the latter verified 39 prepared files.

The reviewed toolchain remains JDK 21.0.9, AGP 9.4.0 and Gradle 9.6.0. The first Gradle attempt failed because its default global debug signing directory was unwritable. A local-environment retry reached compilation but failed Lint on an available-toolchain-update notice. The sole detector `AndroidGradlePluginVersion` is now disabled with an explicit reviewed-pin comment; source Lint rules and warnings as errors remain enabled. That source change required the fresh `auth-timeout-02` preparation stage. No global installation, wrapper upgrade or keystore replacement was performed.

The initial package audit then rejected signing continuity: Gradle had used a nested Android user directory and a different existing debug certificate. Its audit and APK were retained. Correcting `ANDROID_USER_HOME` to the accepted `.cache/android-user` directory selected the existing accepted key, and a second package build completed successfully. Final certificate SHA256 is `1a86c3953ff2315a5f1c811cd253be21f7581336417f47beda1320e7eadd7852`, matching accepted 0.1.0. This defect was caught and corrected before installation.

Final audit `.cache/native-vnc-android/auth-timeout-audit-02/apk-audit.json` verifies the compiled manifest, valid signature and signing continuity, all three exact prepared arm64 library bytes, 16 KiB ELF and ZIP alignment, and the complete expected notice asset set. The manifest contains only `VncActivity`, with `INTERNET` and `ACCESS_LOCAL_NETWORK`; backup and cleartext traffic remain disabled. No service, provider or receiver is present. Android Lint has zero findings, the seven JVM tests have zero failures/errors, and the recorded Java runtime dependency closure is empty.

Reproduction uses the existing pinned local toolchain and retained source archives, without device or server operations:

```sh
python3 tools/prepare_vnc_adapter.py --build --run auth-timeout-01
python3 tools/prepare_vnc_adapter.py --check --run auth-timeout-01
python3 tools/prepare_vnc_android.py --prepare --stage auth-timeout-02
GRADLE_USER_HOME="$PWD/.cache/gradle-home" \
ANDROID_HOME="$PWD/.cache/android-sdk" \
ANDROID_USER_HOME="$PWD/.cache/android-user" \
.cache/native-vnc-android/auth-timeout-02/gradlew \
  -p .cache/native-vnc-android/auth-timeout-02 --no-daemon \
  assembleDebug lintDebug testDebugUnitTest recordCloudyRuntimeDependencies
python3 .cache/native-vnc-android/audit_auth_timeout_corrected.py
python3 tools/prepare_vnc_android.py --check --stage auth-timeout-02
```

Those names identify immutable completed runs; a new reproduction must select fresh run/stage/audit output names and bind its packaging lock to its own accepted receipt. Exact commands for focused source checks and byte/hash records for build logs, receipts, test XML, final audit and retained failures are in the manifest. Private logs, APKs, downloaded implementation and signing material remain outside tracked public results. The manifest publishes only scoped relative evidence paths, sizes and hashes; it contains no private key or credential content.

The build does not prove successful live authentication, PAM timing, framebuffer delivery, input, cancellation or deadline enforcement on the Googlebook. It introduces no CAP-VNC, latency or performance pass. The next parent-owned controlled installation and protected-server case must qualify those boundaries. Distribution source/notices acceptance remains a separate release gate. No device, server, system configuration or Git operation was part of this build increment.
