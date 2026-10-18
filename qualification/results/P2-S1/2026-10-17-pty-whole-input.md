# Whole-action PTY input admission

**Date:** 2026-10-17, America/Chicago. **Scope:** C04/C05, bounded P2-S1/P2-S2 adapter repair; not full terminal or R1 acceptance. **Base:** `0206588c77611fb928bc736e87698007623af34c` on `codex/console-r1-implementation`, with the reviewed SSH changes below. The surrounding native interaction work remains a separate integration milestone.

## Problem and reviewed change

The renderer admits a whole transformed input action up to 64 KiB, but `SshPtyMailbox.offer` previously rejected every action above 16 KiB. Splitting a paste into independent admissions would allow partial delivery and is not an acceptable repair.

`SshOwnedPty.kt` now admits one whole action up to 64 KiB. The total byte charge still includes queued **and in-flight** data and remains 64 KiB; the queued-entry limit remains 64. Full/invalid actions are rejected before copying or enqueueing. Accepted caller bytes are copied once into owned storage. Writer retirement, resize coalescing, output chunks/queue and transport ownership are unchanged.

Parent review checked admission ordering, exact capacity release, caller-buffer ownership and the absence of partial enqueue. No dependency, public API shape, host trust or replay policy changed.

| Source | SHA-256 |
| --- | --- |
| `shared/android/ssh/src/main/kotlin/io/github/baitian6641/cloudy/ssh/SshOwnedPty.kt` | `a882d7b955741675ebead82decd6487011c0f2d6bdeb8fd8eab309b30cd69b15` |
| `shared/android/ssh/src/test/kotlin/io/github/baitian6641/cloudy/ssh/SshPtyBoundsTest.kt` | `00d8fcb8de21e078de40e76c7dae26579f8bbbe6327dc302f67d2485054b5a4b` |

## Executed verification

1. **Failing before:** `wholeLargeActionsShareTheExistingCapacityWithoutPartialAdmission` failed against the old 16 KiB limit: a valid large action was classified invalid instead of being refused only for occupied capacity.
2. **Passing after:** all eight `SshPtyBoundsTest` methods passed, zero failures/errors/skips. They cover whole-action refusal without partial admission, a full 64 KiB action and in-flight charge, caller ownership, close/wakeup, resize fairness and output boundaries. Kotlin source/test compilation passed in that run. The observed XML SHA was `9a7fcc41618b6a0853129e8d25a833250e3ad5e0ccd70f35ed1cef5af287fa93`; the subsequent full SSH suite passed 48 methods with 26 explicitly skipped opt-in cases and zero failures/errors.
3. **Actual changed path:** native Console interaction run `d94e3ce1-0748-4ba1-995f-7242b7b5adb3` delivered exactly 20,000 clipboard bytes through the real app/bridge/SSHJ/Apache MINA2.20.0/util-linux2.41.3 kernel-PTY path. An independent host capture compared the complete bytes/hash and recorded no extra menu/cancelled-preview bytes. The run restored exact logical profile/trust state and its owned clipboard. Later native runs retained the same exact-byte result while exposing unrelated selection/UX failures; those failures are not reclassified as full passes.

The retained module command is:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
GRADLE_USER_HOME="$PWD/.cache/gradle-home" \
./gradlew :ssh:test --tests io.github.baitian6641.cloudy.ssh.SshPtyBoundsTest \
  --offline --dependency-verification=strict --no-daemon --console=plain
```

The real-app receipt is under `qualification/results/private/console-terminal-resume/d94e3ce1-0748-4ba1-995f-7242b7b5adb3/`; frozen app/test/source identities are under `.cache/console-interaction-2026-10-17/candidate04-functional/`. Its app SHA is `9d23ea52af83ed3f4e38ef05f534c8819313106685e1718ef91ee5703d9f749b`, test SHA `45c5bb39755979e4676fa6a8eb8775fdcd6c33fe8041a28308e391369c02a9ef`, and controller SHA `8245adbe0a12264b6cbc48971a82b77b95e7a7a8d1505cfebab09302aba52c93`. That test-driver/UI extension is separate from this backend-only checkpoint.

## Boundary and remaining work

The runtime target was the owned Android17/API37 x86_64 emulator, Google APIs37.0 revision6, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`, WebView145.0.7632.218. It is not physical Googlebook qualification. The MINA fixture's real PTY does not implement remote window-change propagation; the earlier [OpenSSH resize evidence](2026-10-17-ssh-pty-backend.md) keeps its distinct source/run scope.

Local admission is not remote consumption acknowledgement. No sustained load, arbitrary network interruption, physical IME/input, complete clipboard UX or R1 claim follows. Continue the native interaction repair and its actual app workflow in [execution-state](../../../docs/05-delivery/execution-state.md), then the remaining M3–M7 requirements.
