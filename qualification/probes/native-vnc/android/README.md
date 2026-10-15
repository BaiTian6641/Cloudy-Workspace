# VNC Android diagnostic packaging

This is the authored packaging boundary for a separate, finite Android VNC diagnostic. The Java session/UI owner is compiled with the unchanged accepted JNI bridge; the bridge is imported from the retained adapter evidence rather than maintained as a second implementation here. A prepared source tree is not an APK or a successful connection.

The packaging lock selects adapter `entry-02`, its exact successful receipt, and only `libcloudy-vnc-jni.so`, `libssl.so`, and `libcrypto.so`. The patched LibVNCClient and JPEG objects are already statically embedded in the JNI library. The original shared `libvncclient.so` must never enter this APK. Selected exact native license/notice texts accompany the libraries; complete corresponding-source delivery and final distribution license acceptance remain separate gates.

From the repository root, using the existing SDK and reviewed dependency cache:

```sh
python3 tools/prepare_vnc_android.py --prepare --stage entry-01 --sdk .cache/android-sdk
python3 tools/prepare_vnc_android.py --check --stage entry-01
```

The preparer requires the real Activity and strings, verifies the native adapter offline, preserves a fresh stage under `.cache/native-vnc-android/`, and binds authored inputs and staged files in `cloudy-preparation.json`. It downloads, compiles, installs and launches nothing. Failed preparations retain a failure-class record. The Gradle `preBuild` hook reruns the same offline verification and rejects source, generated-payload or build-logic additions outside the recorded closure. Gradle outputs are allowed outside the protected generated import directory.

The integration leader runs the existing checksum-verified Gradle wrapper from the prepared directory with the established writable Gradle cache:

```sh
./gradlew --offline --no-daemon --max-workers=4 :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:recordCloudyRuntimeDependencies
```

Use the existing `GRADLE_USER_HOME`; do not install global tools or bypass checksum verification to make resolution succeed. The Java-only module explicitly disables AGP's automatic Kotlin runtime. `recordCloudyRuntimeDependencies` writes the actual resolved debug runtime artifact receipt and fails on any Java runtime dependency. JUnit/Hamcrest are test-only. Dependency locking is enabled; a generated lockfile must be reviewed after actual resolution rather than fabricated before the first build.

Compiler, Lint, JVM tests, APK contents/signature/alignment, installation, framework lifecycle, protected-server connection, pin rejection and real input/render behavior each require their own actual evidence. No device, server, plaintext fallback, credential persistence or forwarding result is implied by import verification.
