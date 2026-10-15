# P0-S2: engagement callback and lifecycle observation

**Run:** 2026-10-15, approximately 07:21–07:26 UTC. **Verdict:** bounded callback and window-ownership observations passed; lifecycle and fallback acceptance remain partial. Closing the last window terminated the process before any final lifecycle or cleanup result was persisted. **P0-S2 remains in progress; API09 is partially observed and CAP-PLATFORM remains pending.**

## Source, dependency and device

The installed debug APK was `dev.cloudy.qualification.baseline`, version **0.3.0 / 3**, report schema **3**, from [11c94be17e3d6b53a8125bfa0a56e0148ea748d2](https://github.com/BaiTian6641/Cloudy-Workspace/commit/11c94be17e3d6b53a8125bfa0a56e0148ea748d2). APK SHA-256: `1c6959501df43e712a5a90c227b91577c954f8b67c0124127ac9b94759667b16`. The isolated probe uses **`androidx.window:window:1.6.0-alpha05`**, a published alpha; this run selects no product dependency.

Target: **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**. All retained reports share process-start timestamp `2026-10-15T07:21:32.410297Z`.

The [source review at this commit](https://github.com/BaiTian6641/Cloudy-Workspace/blob/11c94be17e3d6b53a8125bfa0a56e0148ea748d2/docs/06-validation/2026-10-15-engagement-probe-review.md) records successful compilation, APK assembly, **31 JVM tests**, and Android Lint with zero reported issues after the documented narrow version-pin advisory exceptions. The [package audit](../../../docs/06-validation/2026-10-15-engagement-package-audit.json) binds the dependency/license review and **11 packaged notice assets** to the APK. The primary agent verified successful hosted runs [38698200577](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38698200577) and [38698200639](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38698200639).

The [JSON manifest](2026-10-15-engagement-probe.json) contains **10 report projections**, **four engagement histories with 21 entries**, **38 selected lifecycle events**, and **96 private artifact hashes**. It omits screenshots, raw UI trees, command output, transport identifiers, process IDs and unrelated desktop content. Host and device timestamps remain separate; their difference is not a latency measurement.

## Outcomes against the probe contract

The [engagement contract](../../../docs/03-platform/googlebook/p0-s2-engagement-probe-contract.md) defines these cases. Test doubles and device observations retain separate scopes.

| Case | Outcome and evidence |
| --- | --- |
| E01 — Artifact/compiler | **Pass for this isolated source/APK.** Public methods compile; source, dependency verification, packaging, tests and lint have reviewed evidence. |
| E02 — Actual observation | **Pass for bounded actual callbacks.** A reported `TOUCH`, then `PRECISE_POINTER`; both included `AUDIO_ON` and `VISUALS_ON`. B also reported `PRECISE_POINTER`. These are library hints, not physical-input, audio or rendering measurements. |
| E03 — Window ownership | **Pass for the observed sequence.** Distinct task/logical identities and independent markers persisted. A's cleanup completed while B remained active. An obsolete B-generation cleanup delivery was rejected after recreation. |
| E04 — Lifecycle/quietness | **Partial.** First-window close, remaining-window stop/return, recreation and bounded settling have actual evidence. Final last-window cleanup was not persisted; broader races and long-running stability remain unqualified. |
| E05 — Honest fallback | **Partial.** Real pending transitions and initial `TOUCH` remain distinguishable in history. Font scale and density stayed unchanged. Persistent no-value, registration-error, cleanup-error and unavailable-library paths were not induced on hardware; their current evidence is source/JVM coverage. |

## Actual callback and ownership sequence

Window letters identify roles in this run, not marker text. **A** used task **1000150**, logical ID `8fbdb166-a607-456e-8486-7a55b30b2e67`, generation `c4c53406-1526-425e-8c57-1a10f0bf3200`. **B** used task **1000152**, logical ID `033f5ecc-4ab9-4b27-9e68-83a753ccc80f`, initially generation `06788a08-1ad6-4de6-aa4e-fcc01560b0c7`. These identities belong to this new run; the reused task number does not link A to earlier 0.2.0 state.

A's revision **9** history records pending registration, registration, `AUDIO_ON / TOUCH / VISUALS_ON`, then `AUDIO_ON / PRECISE_POINTER / VISUALS_ON`. Both actual mode callbacks are retained. No readiness duration is inferred from their close timestamps.

The early file named `engagement-two-windows.json` was still revision **10** with only A. The later revision **22** establishes two live windows. Revision **24** records A's independent **B / 1** marker and B's **C / 2** marker. After A's native caption close, revision **34** records A destroyed with `finishing=true`, its epoch 1 inactive and `cleanup-complete`, while B remains active. A's retained geometry and configuration thereafter are historical.

Minimizing B produced revision **41**, with epoch 1 stopped and cleanup complete. Returning through its existing Recents task produced revision **47**: the same Activity generation started **epoch 2**, registered and received a callback. Explicit recreation then produced revision **60**, preserving B's logical/task identity and **C / 2** state in new generation `238a280b-9ba5-4402-9f8a-ccfe149f1e99`, with a new epoch 1.

Event **58** rejected delivery of the old generation's epoch-2 cleanup snapshot; event **59** accepted the replacement's reported engagement state. This was a stale cleanup-state delivery, not an observed late mode callback. The ledger rejection count became 1; sampled per-controller late-callback counts remained 0.

After Refresh, the first four exports were **60, 63, 63, 63** across **4.138242 host seconds**, so that series was not stable. A separate later series was **63, 63, 63, 63** across **4.144702 seconds**. This is bounded settling evidence, not a soak test or proof that sampled counters could never change.

## Last-window close and evidence limit

The primary agent selected B's freshly inspected native close control at approximately **07:24:56 UTC**. The initial export and **six bounded follow-up exports** all remained revision **63**, byte-identical to the pre-close report. Its retained B-alive/subscribed fields describe the last saved state, not a still-running window.

A subsequent package-process query returned exit code 1 with empty output. The latest package exit diagnostic reported reason **10 / USER REQUESTED**, subreason **22 / REMOVE TASK**. The public API defines `ApplicationExitInfo.REASON_USER_REQUESTED` from API 30 with value 10; the subreason is retained only as observed shell diagnostic classification, not a selected SDK contract. [Official ApplicationExitInfo reference](https://developer.android.com/reference/android/app/ApplicationExitInfo#REASON_USER_REQUESTED)

The primary agent inspected the after-close screenshot and observed the probe window absent. Together these observations support window/process termination. They do **not** establish successful final unregister, a persisted `onDestroy`, or a final cleanup failure: the asynchronous final observation/save was not retained before process teardown. No missing callback is reconstructed as evidence.

## Scope and review boundary

The primary agent inspected the private two-window, final pre-close and after-close screenshots. The evidence author checked report projections, action-to-own-control matching, histories and hashes, but did not independently inspect pixels. All UI actions were injected. Font scale **1.0** and density **1.625 / 260 dpi** stayed unchanged in the retained reports; the separate [enlarged-font experiment](2026-10-15-font-scale.md) used 0.2.0 and is not a font qualification of this APK.

A separate reviewer independently accepted all 96 private hashes, ten report projections, four full histories, 38 selected events, action matching and the final process-exit/persistence boundary. The primary agent read the complete report and independently matched the same hashes, projections, histories and selected events. E04/E05 remain partial after review.

Physical keyboard/trackpad/touchscreen behavior, hotplug, external displays, audio, renderer performance, leak freedom, process-death restoration, remote-operation survival and production Console/Desktop behavior remain outside this run. Neither successful callbacks nor the current absence of recorded errors closes those gates.
