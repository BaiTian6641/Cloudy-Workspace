# RDP cursor presentation repair — source and build review

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4, in progress. **Candidate:** `dev.cloudy.qualification.rdp` **0.1.5/code6**, build reference **`a5a5db42-1444-4642-bfff-aa63751a8e4f`**. The parent and direct GPT6.1-Sol workers retain integration/review ownership; OMP remains Kimi-only. This note records the accepted source/build review and the parent-owned scoped physical result: the user confirms host trail and cursor movement with working release. This is a cursor/host-target/release result on the exact fixture below; P0-S4 and CAP-RDP remain incomplete.

## Problem and retained failed evidence

The [repair entry](../03-platform/googlebook/rdp-cursor-presentation-entry.md) identifies an incomplete client presentation path: relative submissions did not advance the custom cursor, default notifications had no software-arrow path during Android capture, and the native Android position callback discarded server coordinates. Shape handling already existed; this change does not replace the engine or input protocol.

The [0.1.4 physical exercise](../../qualification/results/P0-S4/2026-10-15-rdp-physical-cursor.md) remains a **visible captured-motion failure with working local release**. Its 873 relative JNI submissions and 504 later host-target moves cannot establish captured-only delivery because the physical release edge was not timestamped. The earlier KRdp missing-relative-callback failure is a separate server case. Source repair, predicted cursor movement and compiler success cannot erase either observation or certify latency, relative wire delivery, 3D or performance.

## Reviewed change and boundaries

| Source | Reviewed behavior |
| --- | --- |
| [Sixth native patch](../../qualification/probes/native-rdp/patches/android-pointer-position.patch), [Java ABI](../../qualification/probes/native-rdp/app/src/main/java/com/freerdp/freerdpcore/services/LibFreeRDP.java) | `SetPosition` forwards authoritative instance/x/y through exact `(JII)V` static JNI. Native validates context/instance and rejects UINT32 values above `INT32_MAX`; the session validates current framebuffer bounds and active instance. Delivery is synchronous, with conditional thread detach and explicit failure/exception containment. Java retains the callback's original sink for failure reporting. |
| [Cursor policy](../../qualification/probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpCursorState.kt), [framebuffer](../../qualification/probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpFrameBuffer.kt) | Default, custom and server-hidden states are distinct. A software default arrow is drawn during capture; custom shape/hotspot bounds and framebuffer clipping remain enforced. A hidden cursor stays hidden. All framebuffer/cursor replacement and drawing share the existing synchronization. |
| [Session](../../qualification/probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpSession.kt) | Only successfully admitted relative motion advances explicitly labeled prediction. It cannot send input, warp/recenter the host or prove delivery. The position revision sampled before JNI admission must still match afterward, so a crossing server correction, geometry or capture change wins over stale prediction. Position source remains distinguishable as initial estimate, absolute input, server position or prediction. |
| [Capture journal](../../qualification/probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpCaptureJournal.kt), [frame view](../../qualification/probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpFrameView.kt) | Actual request/confirmation/release/loss/timeout callbacks capture attempt identity, client monotonic time and submission counts before the owner queue can race cleanup. The journal retains at most 32 edges and counts omissions. Later owner observations are separately labeled; they are not simultaneous UI snapshots or host-clock alignment. |
| [Host target](../../tools/rdp_input_target.cpp) | `host-motion-1` paints a crosshair, a bounded trail of 64 points and actual canvas mouse-event count using received own-window Qt events only. It has no fake initial point, client delta feed, global interception or transport attribution. Existing colors/grid/editor, security limits and finite completion remain. |

Upstream's exact [pointer cache](https://github.com/FreeRDP/FreeRDP/blob/97eff6ddfbbfaa8e42a126d362686bd001ff8bdc/libfreerdp/cache/pointer.c) suppresses `SetPosition` dispatch unless `FreeRDP_GrabMouse` is true. Setting it in Android post-connect is therefore a callback-dispatch prerequisite; it does not request Android pointer capture or emit input. The inspected GRD cursor path has not established position feedback for this server, so prediction remains necessary presentation feedback with explicitly limited meaning. The maintained signed relative JNI/queue/core route and negotiated capability checks remain intact; no absolute fallback was introduced.

Two independent cross-reviews found no blocking defects in their scoped native/ABI and Kotlin/capture ownership changes. Review addressed primitive coordinate narrowing, callback lifetime/failure containment, hidden/default semantics, the prediction revision race and bounded evidence ownership. The subsequent parent-owned physical result below closes only its explicitly exercised cursor/host-target/release scope.

## Verified builds and exact evidence

The [source lock](../../qualification/probes/native-rdp/source-lock.json) retains FreeRDP **3.32.2**, revision `97eff6ddfbbfaa8e42a126d362686bd001ff8bdc`, archive SHA256 `e99ebb8bbdd2608fd54fcc8b15c510f89dbc05b166c48339d1b1edea0b30f3c8`. The six-patch order is registered in [the preparation helper](../../tools/prepare_rdp_native.py). New patch SHA256 is **`63ed1b864d2782e19c5e9c4245a6b6a157a1c58b468186952899374c0fdf910e`**; current source-lock SHA256 is `e02a9264efbba9c57d0a8d40b7031ee54cafe7d0f2b4b63c4faf415c429187f5`.

Parent-owned full native compilation and prepared import/offline checks **passed** with NDK 29.0.14206865, CMake 4.1.2, Android 29/arm64-v8a and the retained dependency graph. The unstripped native-build `libfreerdp-android.so` hash is **`387be5a06fefea78fdabb5de9fe0c85d9b6608068fb6b991f7ef6d720414f741`**; the receipt binds all eight libraries and six patches. This is native build evidence, not APK/JNI runtime evidence. Focused changed-object compilation with `-Werror`, exact six-patch replay and the actual authored Java callback test also passed.

Focused Kotlin 2.2.10 compilation with `-Werror`/JVM17 and **15 JUnit tests** passed for cursor state, capture journal and first-failure ownership. Tests use authored policy code; they do not simulate an RDP server or qualify Android rendering. The host target compiled with GCC 15.2.0, Qt 6.9.2 and `-std=c++17 -fPIC -Wall -Wextra -Wpedantic -Werror`; its old accepted binary was preserved. New source SHA256: `a497eeecf2bb42dddc0300d42d87d8d6acb25bcd4c51f1a73b5341b5d1f07c90`. New binary: 259,112 bytes, SHA256 `f240061c2c538ffa102976e2fdd978759bafb99cfa9f48c6e9548933f3bcfbfa`. The worker did not launch it; the parent subsequently used this exact target in the physical exercise below.

Private/ignored evidence, relative to repository root:

- `.cache/native-rdp/cursor-native-receipt.json` and `cursor-native-receipt.log`: full native source/patch/output binding and compiler result; `cursor-configure.log` retains configuration.
- `.cache/native-rdp/pointer-position-review/checks.json`, `compile.json`, `compile.log`: focused native/compiler, replay and actual Java callback checks.
- `.cache/rdp-cursor-presentation-policy/receipt.json`, `compiler.log`, `tests.log`: exact Kotlin/test source hashes, commands, successful compilation and 15-test result.
- `.cache/rdp-input-qualification/host-target/build-cursor.json`, `build-cursor.stdout`, `build-cursor.stderr`, `cursor-target.diff`: host target command/source/binary binding, compiler output and exact delta.
- `.cache/native-rdp/cursor-app-build.log`: successful parent full Android build; `.cache/native-rdp/cursor-apk-audit.json` binds the final APK and package checks.
- `qualification/results/private/native-rdp-cursor/2026-10-15-run-01/`: frozen APK, Android/native build records, Lint/JVM results, policy/Java review records and exact host target.
- `qualification/results/private/native-rdp-session/2026-10-15-run-11/final-journal-raw.json`: final attempt 13 journal, capture edges, submission counters and native cleanup. That same private run retains the actual layouts/screens and operator cleanup records.

## Final Android/package result and scoped physical outcome

The parent full Android build succeeded in **42 seconds**: APK assembly, Kotlin/Java compilation, strict Android Lint with warnings as errors, and **45 JVM tests with zero failures or skips**. The focused 15-test policy run above is a subset of those 45, not an additional total. Python AST 17-file and Ruff checks passed; the corrected root test discovery ran **67 tests successfully**. An initial mistaken `unittest -s tools/tests` command failed because that directory does not exist; only the subsequent `-s tests` run is a pass.

Final APK: **12,746,630 bytes**, SHA256 **`565ae8036040e34de98c1c83c1d365c43df83053188b7f65bbd61906578ea1df`**. The package audit verifies **eight native libraries and 21 assets**, expected native dependency closure, signature and 16KiB ELF/APK alignment. Packaged stripped native hashes are recorded separately from unstripped build hashes in the native receipt; these are different artifact representations.

The [parent physical report](../../qualification/results/P0-S4/2026-10-15-rdp-cursor-repair.md) records **attempt 13**, `2868d0af-58c1-40d0-9b99-887b723bafea`, on the same **HP Googlebook 14 / CL3B.260622.276.R2 / API 37 / arm64-v8a** and selected isolated GRD fixture. The input-device subtype was not identified. The user's observation is **“Host trail and cursor move; release works.”** The final client journal records 565 frame updates, 1,678 JNI input submissions and 1,037 relative submissions at 1280×720, with eight capture edges, zero omitted edges and two confirmed capture/release intervals:

| Interval | Client confirmation → release request (elapsed ms) | Relative submission difference | Released callback (elapsed ms) |
| --- | --- | ---: | ---: |
| First |20,149 →27,332 |619 |27,339 |
| Second |40,412 →46,970 |418 |46,985 |

The host target records **824 actual mouse moves**, finishes with exit 0/operator-close and no held keys/buttons. Native thread join and instance free complete. The retained `native-failure` event carries the native status that the user logged off the server session; it is not relabeled as a client crash or silently removed. Fixture cleanup is verified as target-completed.

This is a scoped physical pass for reported visible cursor, real host trail and local release on this combination. Client JNI counts remain admissions, and the 824 host moves are not a one-to-one mapping to those submissions or proof of every captured wire event. Client and host clocks remain separate; no latency metric follows. Raw server-position callback execution and each default/custom/hidden cursor branch were not separately proven live. Keep the earlier 0.1.4 failure, other servers/OS lanes, actual 3D behavior and performance distinct. **CAP-RDP remains pending.**

The accepted USB 0.1.0 APK and its accepted evidence remain unchanged. Its historical native receipt binds the earlier shared `prepare_rdp_native.py`; consequently running that old USB `--check` against this changed checkout is stale. This does not invalidate the already accepted USB artifact or authorize an unchanged USB rebuild/device rerun for this RDP increment.
