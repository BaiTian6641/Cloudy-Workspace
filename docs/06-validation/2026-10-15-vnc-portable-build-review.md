# VNC portable build and hosted CI review

**Scope:** fresh-runner build infrastructure for the existing VNC diagnostic. This changes Cloudy's preparation/provenance boundary; it does not change the accepted VNC security policy, native adapter, application behavior or device verdict. The [20.639-second startup and host-observed F8 result](../../qualification/results/P0-S4/2026-10-15-vnc-startup-timing.md) remains the physical evidence. No new device or host session is required for this infrastructure change.

## Problem and implementation

The historical build helpers deliberately import exact previously accepted local receipts and library bytes. A fresh hosted runner does not have those private artifacts, and rebuilding debug-bearing native libraries in another path need not reproduce their hashes. Changing historical pins to accommodate each runner would confuse new builds with accepted device evidence.

The new [orchestrator](../../tools/build_vnc_ci.py) provides an explicit `portable-source-build` route with `hosted-build-only-no-device` scope. It builds only the OpenSSL target through the existing pinned RDP source/configuration recipe, then the pinned LibVNC/JPEG base, reviewed JNI adapter and real Android diagnostic. Each stage binds its actual outputs to its successful predecessor. Offline verification recursively checks the chain, retained source/patch bytes, prepared files and compiler records. The existing default exact-import mode remains strict. Historical locks and frozen evidence are not rewritten.

The OpenSSL receipt describes an OpenSSL-only build; it cannot stand in for a full RDP build receipt. It includes only the required OpenSSL headers and two libraries. Native compilation remains bounded to four jobs with failure records and process cleanup. Source downloads are verified on every use. The new [workflow](../../.github/workflows/android-native-vnc.yml) caches downloads only and uses pinned actions, verified command-line tools/Gradle wrapper and checked SDK revisions.

After source preparation, the real Gradle build runs compilation, Lint, JVM tests and runtime dependency recording. The APK audit checks the exact JNI/SSL/crypto library closure, imported notices, uncompressed 16 KiB ZIP alignment, merged package/activity, empty Java runtime dependency closure and passing reports. `apksigner` separately verifies the actual APK. CI retains bounded diagnostics and hashes; APK publication and final source-distribution review remain separate.

## Reproduction

Use a fresh run name and fresh path without spaces for the OpenSSL build stage. Existing runs and receipts are preserved.

```sh
python3 tools/build_vnc_ci.py --build --run ci \
  --sdk "$ANDROID_HOME" --rdp-stage "$RUNNER_TEMP/cloudy-vnc-openssl"
.cache/native-vnc-android/ci/gradlew -p .cache/native-vnc-android/ci \
  --no-daemon --console=plain --max-workers=4 \
  assembleDebug lintDebug testDebugUnitTest recordCloudyRuntimeDependencies
python3 tools/build_vnc_ci.py --check --run ci \
  --apk .cache/native-vnc-android/ci/app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose \
  .cache/native-vnc-android/ci/app/build/outputs/apk/debug/app-debug.apk
```

The workflow supplies Java21, API37 revision2, build-tools36.0.0, NDK29.0.14206865 and CMake4.1.2. The actual cache paths for this command are `native-vnc-ci/ci`, `native-vnc-ci/openssl-ci`, `native-vnc/ci-ci`, `native-vnc/adapter/ci-ci` and `native-vnc-android/ci`, under ignored `.cache/`. The top receipt is `native-vnc-ci/ci/receipt.json`; the Android receipt is `cloudy-preparation.json`, and the final package audit is `native-vnc-ci/ci/apk-audit.json`.

## Review and validation state

Direct Codex GPT6.1-Sol owns the build-helper implementation. A separate direct Codex worker reviews its final diff and checks. Kimi K3 supplied a context-only workflow proposal through actual `kimi-code/k3`, normal stop and zero tools; the parent applied it with corrections. Parent and independent workflow review corrected test discovery, source-lock spelling, wrapper verification, runner environment context and actual artifact paths, and removed APK publication. The worker ran no tools or checks and did not edit the repository. This is useful reviewed code generation, not a tool-enabled OMP editing qualification.

The [local build record](../../qualification/results/P0-S4/2026-10-15-vnc-portable-ci-build.json) binds 32 actual source/evidence files. Fresh `local-ci-02` passes the complete OpenSSL/native/JNI preparation chain and actual Android compilation, **10 JVM tests**, **zero Lint findings**, empty Java runtime closure, native/asset/merged-manifest audit and APK signature verification. APK SHA256 is `0440e31613d984701d9fb2d40a76c8b51f73ecde2da0747e2b65cdb2ca5ebf3f`, size 10,888,223 bytes. It was not installed. This newly built APK is distinct from the historical device-qualified APK, even though application version/source are unchanged.

Parent checks pass **30 authored Python AST files**, full Ruff and **114 repository tests**; the VNC subset is 30 tests, including 12 new provenance/package-boundary tests. Workflow YAML and shell syntax pass; this does not replace GitHub Actions schema/execution validation. The independent source reviewer found and accepted two repairs: binding the executing native helper in the OpenSSL recipe and enforcing exact assets plus merged permission/component/security policy. The package audit explicitly checks merged XML, not decoded binary APK manifest; signature verification is a separate actual SDK-tool pass.

The earlier `local-ci-01` attempt is preserved as a stale-input failure: source changed during the integration build, the native receipt was rejected and no accepted top-level receipt was issued. Its completed OpenSSL target is not promoted to a successful complete chain. The corrected source was frozen and reviewed before the fresh second run. Clean hosted execution remains pending. The previous commit `2c77febda37b6a5d618103da6f14614ff2cf06b4` has five successful hosted lanes; those passes do not establish success of this new lane. No capability or phase prerequisite is promoted.


## First hosted result and workflow correction

The [first VNC hosted run](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38839325183) at `594c799c11ee058f46e7ef6e08424cdff6a0cc1f` passed preparation tests, the fresh native chain, Gradle compilation/Lint/JVM tasks and package audit, then failed signature verification. Inspection of that exact committed workflow establishes the error: the parent publication-scope edit removed the APK operand from `apksigner verify` while leaving it in the upload list. The same missing-operand command exits1 locally. This is an invocation defect, not evidence of an invalid signature; the accepted local APK's separate signature result remains valid.

The first hosted upload consequently included the debug APK in artifact `12020846433` (`native-vnc-build-diagnostics`). Earlier statements that this workflow excluded APK publication were incorrect. This does not alter the fact that the distinct local `local-ci-02` APK was not installed or published. The original32-file local build manifest remains unchanged, including its historical workflow hash; it is not silently repinned to the repair.

The corrected workflow passes an explicit APK argument, preserves the real exit status, retains signature output and exposes an escaped bounded failure annotation. Its artifact allowlist now excludes APKs. A separate push-only job, without source checkout or third-party actions, receives only Actions write permission to remove that exact unintended artifact after checking its ID/name/run/full commit. It requires a204 deletion response and a subsequent404. Withdrawal and the repaired hosted verification remain pending until their actual run succeeds. The built-in token is used only with GitHub's official artifact API and is never printed. R253–R255 document the command/API boundaries.

The parent executes the corrected signature step locally against the accepted local APK, substituting only the run paths; it passes. YAML/shell syntax and embedded Python AST checks pass, and independent direct Codex review confirms the operand, APK exclusion and narrowly scoped cleanup authority. No native source, engine, APK or device change is required for this workflow correction. Retained evidence is under `.cache/native-vnc-ci/workflow-repair-01/` and the preceding `review-02/` job/annotation/artifact receipts.
