# Console R1 implementation baseline — 2026-10-17

**Stage:** P2-S1 construction baseline only; no stage/capability promotion. **Source:** `a6592be4d08ca7a8ee030076708f50c7a0664992` plus documentation-only R1 plan/gate edits. **Branch:** `codex/console-r1-implementation`. **Host:** Ubuntu 26.04, JDK 21.0.9, Gradle wrapper 9.6.0, AGP 9.4.0, SDK Android 37.0 / Build Tools 36.0.0.

## Review and planning

The required product, Rule 0, interaction, X11, files/publication, release-gate and Q-C01–Q-C10 documents are present. The reviewed [execution plan](../../../docs/05-delivery/console-r1-implementation-plan.md) and [C01–C20 matrix](../../../docs/05-delivery/console-r1-requirements.md) record actual partial boundaries and the ordered P2-S5 final release gate. `python3 tools/validate_project.py` passed after those edits: 414 Markdown files, 3,141 local links, 200 JSON files, 40 stages and 37 acceptance cases; no errors.

## Reproducible local build

The workspace-local toolchain/cache selection is:

```bash
export ANDROID_HOME="$PWD/.cache/android-sdk"
export ANDROID_USER_HOME="$PWD/.cache/android-user"
export GRADLE_USER_HOME="$PWD/.cache/gradle-home"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :ssh:test :console:assembleDebug :console:lintDebug \
  --offline --dependency-verification=strict --no-daemon --console=plain
```

Result: **BUILD SUCCESSFUL**, 168 actionable tasks (28 executed, 140 up-to-date). Strict dependency verification and offline resolution succeeded. Console Lint reported `No issues found.` The SSH XML results contain 14 suites / 65 recorded tests: **45 executed successfully, 20 explicitly skipped, 0 failures, 0 errors**. Skipped opt-in host fixtures are not counted as passes.

Artifact: `apps/console/build/outputs/apk/debug/console-debug.apk`; SHA-256 `fcfc185095fe20f10cc692ceb9c1091f972b65e7da0cee6a3631c53898b207cb`. This is a baseline debug APK, not a new feature/release candidate and was not installed by this run.

Three environment setup failures preceded the successful invocation and are not hidden: the sandbox-default Gradle home was read-only; a fresh workspace Gradle home lacked the offline plugin cache; and the first cached run lacked `ANDROID_HOME`. The final command above is the corrected reproducible route. A nonfatal AGP metrics warning referenced the sandbox-default `/home/polar/.android`; compiler, tests, packaging and Lint completed successfully.

## Device identity for later acceptance

The already authorized USB-connected HP Googlebook 14 is visible through ADB. Android 17, API 37, build fingerprint `HP/quartz/quartz:17/CL3B.260622.276.R2/16560795:user/release-keys`, ABI `arm64-v8a`. Inventory only: no install, launch, input or feature acceptance was performed in this baseline.

## Next gates

M1 adopts/compiles/tests the reviewed PTY backend and requires a real POSIX OpenSSH PTY+SFTP fixture. M2 independently executes the two-XID native-window proof. Neither this build nor the existing X11 SSH host fixture closes C04/C05/C16/C17 or P2-S1/P2-S2/P0-S7.
