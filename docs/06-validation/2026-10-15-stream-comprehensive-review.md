# Comprehensive streaming and continuity review — 0.1.9

**Reviewed 2026-10-15, America/Chicago. Outcome: visual playback FAILED; implementation findings remain open.** The user explicitly reports **“no playback”** and requested comprehensive review of the implementation and documents to prevent misunderstandings during a long agent run. This review records source findings and corrects continuity documents; it does not implement the proposed repairs or qualify a working player.

The leader reviewed the owned UI/authentication/catalog/connection/surface/journal flow, its composed upstream integration, the latest device/host evidence and the current planning/reference instructions. Direct Codex GPT6.1-Sol workers supplied disjoint UI/state, engine and context reviews; the leader inspected their findings and evidence. This is a comprehensive review of this streaming increment and its project handoff, not an exhaustive audit of every upstream library or a completed P0/product stage. D12 reuse remains in force.

## Controlling evidence and uncertainty

The [actual result and bound manifest](../../qualification/results/P0-S4/2026-10-15-stream-decoder-session.md) identify stream 0.1.9/code10, build `a9159dd4-2b44-4fc4-9818-bbc5ba1876f5`, on HP Googlebook 14, Android 17/API 37, build `CL3B.260622.276.R2`. The exact APK SHA256 is `813fad6c49f3ffd08afbe5b1ffeabd834cbea0a271f9552db8f4f618c43631da`. The selected Sunshine version/source, host setup and original records remain bound there.

| Observation boundary | Actual result | What it does not establish |
| --- | --- | --- |
| App identity and credential | One PLAY has type-1 device-credential authorization and same TEE key/signature qualification | Which later click the user's “no popup/no changes” reply described |
| Paired catalog | Three new reads completed, each returning one real application; 160/138/88 ms callback-to-completion | Host readiness for an arbitrarily later click, network-only latency or automatic approval to launch |
| Host launch/native engine | Real selected application, native start result 0, 22 stage callbacks | Visible video or product session readiness |
| Decoder/output Surface | H.264 1280 × 720; `c2.qti.avc.decoder.low_latency`; 1,524 decode-unit submissions, 1,517 output releases and 1,517 rendered callbacks | TextureView consumption, current on-screen visibility, controlled content match or human-observed movement |
| User-visible playback | Final user verdict **“no playback”**; earlier no moving-video and no-popup/no-change replies retained | No pass may be inferred from engine counters |
| End of run | Original 30-second authorization deadline; worker/controller returned, matching surface released, journal closed | An operator Stop test, unrestricted reconnect or long-session stability |
| Cleanup | Host/canvas exit 0; seven ports released; permission/profile removed; app stopped; 22 final records and all 15 prior records preserved | Current liveness after future unrelated user actions; verify before reuse |

The two later catalog CLIENT_TLS/IOException records remain failed records. Their timing is consistent with the closed finite fixture, but Android monotonic timestamps and host UTC are not synchronized causal evidence. Each retains local identity qualification; do not call this a key regression or discard it as noise.

The 0.1.8 unsigned-version incompatibility was concrete and its narrow 0.1.9 correction is complete. This attempt passed that former boundary. It does not establish that the visible-player problem is fixed. The raw `started=true`, `failure=NONE` and `cleanupComplete=true` fields describe different native/retirement boundaries; none is a `visiblePlaybackPassed` field.

## Findings and required disposition

### SR-01 — P1: the exhausted playback state is not actionable

[Activity](../../qualification/probes/native-stream/android/app/src/main/java/dev/cloudy/qualification/stream/StreamProbeActivity.java), lines 162–165 and 216–239; [ViewModel](../../qualification/probes/native-stream/android/app/src/main/java/dev/cloudy/qualification/stream/StreamProbeViewModel.java), lines 250–257 and 665–710.

After the allowed native run, the process guard remains consumed, Preview is disabled, and normal cleanup removes its TextureView. The help paragraph mentions restart, but the current screen has no prominent restart-required outcome or adjacent disabled-action reason. A disabled button never reaches the ViewModel's existing rejection message. A later click can therefore produce no prompt or apparent change. This is a source-confirmed usability defect; it is not proof that this was the user's particular click.

**Open repair:** render the actual admission state and reason beside Preview: identity/permission/catalog/surface missing, operation active, cleanup pending, evidence blocked, or native allowance used. Show explicit retired/restart-required state after cleanup. Preserve the one-run guard and do not fake, automatically restart or replay a stream. An eventual fresh-process workflow needs actual implementation and review.

### SR-02 — P1: preview, status and Stop share a long scrollable form

Activity lines 76–143 and 177–192 place all identity, pairing, catalog, preview, status and cancellation controls in one ScrollView. Native prompt focus changes and form updates are not accompanied by a stable preview viewport. Before the prompt, the retained TextureView was fully visible: 2007 × 390 pixels inside the observed viewport. Its aspect ratio was about 5.15:1 for 16:9 content. No active-session geometry was retained.

**Open repair:** create an adaptive preview pane outside the controls ScrollView from the start, preserving video aspect ratio and keeping compact phase/countdown/Stop visible. Support narrow desktop windows, native captions/insets and font scaling. Do not reparent or replace a live texture to achieve this. Initial zero size/off-screen placement is disproved for this attempt; later clipping remains a hypothesis, not a diagnosed cause.

### SR-03 — P1 investigation boundary: presentation is unobserved after decoder output

Activity lines 235–236 discard size and TextureView-update callbacks. [Surface ownership](../../qualification/probes/native-stream/android/app/src/main/java/dev/cloudy/qualification/stream/PlaybackSurfaceOwner.java) checks producer-target validity, while the ViewModel stores codec-side counters. There is no retained live update count, clipped geometry, attachment or hardware-window observation. The final absent TextureView is expected cleanup and cannot locate the failure during playback.

**Open repair:** collect bounded, attempt/surface-bound presentation observations at admission, first update, meaningful visibility changes and retirement. Count actual TextureView updates separately from codec callbacks. Retain dimensions, visible rectangle and relevant view flags without pixels, per-frame file writes or unbounded posts. A texture update still does not establish human-visible output; preserve an independent user verdict. Android documents the hardware-window requirement and ordinary clipping for [TextureView](https://developer.android.com/reference/android/view/TextureView), and its [update callback](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener#onSurfaceTextureUpdated(android.graphics.SurfaceTexture)) observes `updateTexImage`. These sources support the observation boundary, not a proven root cause.

No source-supported new codec/native transport defect was found in this bounded review. Do not replace the engine, disable secure-window protection or change hardware settings solely to chase this symptom.

### SR-04 — P2: deadline, operation and phase messages are ambiguous

ViewModel lines 471–476 use `PAIRING_AUTH_DEADLINE` for all network operations, including PLAY and CATALOG. The prompt may wait up to 120 seconds, but the whole authorized operation has only 30 seconds from the original callback, including setup. The UI shows no clear remaining time or separation between preparing, engine running, visible delivery and retirement.

**Open repair:** display the current phase and remaining time from the original timestamp; use operation-neutral or typed expiry reasons for new records while retaining old evidence values. The observed terminal record was 121 ms after the original expiry, including cleanup; no authorization extension or spontaneous network failure is inferred. Thirty seconds is the current finite diagnostic policy, not a product session-duration requirement. Keep the original deadline intact unless a separately justified design change is reviewed.

### SR-05 — P2: owner-cleared catalog prompt can strand process admission

ViewModel lines 397, 453–457, 550–554, 764–774 and 838–874 contain a concrete cancellation interleaving. If the owner clears while a catalog prompt remains outstanding, its queued final write retains `closed=false`. Terminal evidence is therefore absent, the process owner remains held, and the executor shuts down. If the prompt then retires, the cleared callback only releases when `finalRetired` was already true; it does not schedule the missing terminal write. The operation can stay blocked for that process even after real prompt retirement.

**Open repair:** keep a cancellation-retirement path available until actual prompt/worker completion and verified terminal persistence, then release ownership. Test callback-before/after-write ordering and close/reopen behavior. Keep malformed/unwritten evidence fail-closed; unconditional release, false closure and callback-triggered network restart are unacceptable fixes. This is a source-reviewed liveness defect, not a device-reproduced explanation for the current no-playback result.

### SR-06 — P2: evidence blockers are difficult to distinguish or recover from

ViewModel lines 332–389 and 720–725, plus [NetworkEvidenceStore](../../qualification/probes/native-stream/android/app/src/main/java/dev/cloudy/qualification/stream/NetworkEvidenceStore.java), collapse capacity, malformed evidence, unfinished current-process work and prior-process reconciliation requirements into broad errors. Reconciliation correctly cannot acknowledge malformed/current-process records, but the UI gives little guidance. Per-directory reservation is limited to 16; streaming identity already has 12 records and automatic refreshes consume slots. The network reader's 64-file safety bound is not the admission capacity.

**Open repair:** show distinct bounded reasons and available remedies. Preserve/verify archives before any planned maintenance; never automatically delete old records, expand limits to hide the problem, regenerate an identity or report manual reconciliation as remote cleanup. Do not perform more repeated probes just to consume remaining capacity.

### SR-07 — P1 continuity: contradictory current instructions could replay completed work

The prior execution-state file mixed current status with obsolete “next/current/uninstalled” checkpoints. API summaries and key/host/adoption/probe entries still described signing, pairing, catalog or VNC behavior as unavailable after later runs established bounded results. Historical research commands could be mistaken for today's work queue. Multiple outstanding catalog/playback questions also made click-to-attempt correlation ambiguous.

**Documentation disposition: corrected in this review.** The compact [execution state](../05-delivery/execution-state.md) is the current handoff. README, API47/API catalog, P0 summaries, streaming entries and probe README now distinguish historical recipes from current results. [AGENTS](../../AGENTS.md) and the [workflow](../05-delivery/engineering-workflow.md) require one outstanding test question, explicit expiry, actual resource verification and a bound user observation. Parent leadership, direct Codex GPT6.1-Sol workers and OMP Kimi K3 only remain explicit.

The leader rejected one worker's toolchain inference during integration: the baseline app uses Gradle 9.6.0, but the actual stream-specific wrapper properties and 0.1.9 staged artifact use checksum-pinned 9.7.1. Documentation records the verified stream version. Review output is not accepted without checking its evidence.

## Safeguards retained and review limits

The inspected flow claims prompts once, binds authentication type/token/attempt, reattaches callbacks on recreation without replay, clears stale catalog selection and freezes a real returned application ID. Pair/catalog/playback share durable admission and cancellation. Surface release follows actual controller retirement and matching destruction; native allowance is never reset. Exact trust, same non-exportable key and original authorization remain constraints. No new trust bypass or automatic pairing/launch was found in this bounded review; this is not certification of a product vault, full lifecycle, every host or all security cases.

The composed compatibility targets remain matched to their locks, including all five relevant final patched targets inspected by the engine reviewer. Historical 0.1.8 files and the current 0.1.9 stage remain separate. Existing 42 stream JVM tests/compiler/Lint and 102 repository tests are build evidence; they did not cover every UX/owner-cleared path above and do not establish visible playback. No code fixes, extra APK build or new device/host test were performed as part of this review.

## Bounded continuation contract

1. Resolve SR-01/SR-02/SR-04 as a cohesive UI-state/presentation increment and SR-05 through a focused retirement fix. Add SR-03 observations and SR-06 reason categories where they directly support those changes. Keep source changes separate from frozen 0.1.9 artifacts.
2. Review the diff independently; run compiler/Lint and meaningful changed-path tests, including the catalog callback ordering. Carry unchanged native/library evidence forward under D12.
3. Verify a fresh fixture and app process, identity, journal capacity, actual selected app and current viewport. Issue one clear operation-specific test question, with readiness/expiry and no competing operator/user clicks. Retire old questions before moving to the next operation. No unchanged replay while the UI still obscures the test.
4. Correlate action acceptance or explicit rejection, credential prompt outcome, live presentation evidence, actual user-visible moving content, deadline/Stop and cleanup. Only a new identified successful visual run can change the current failed playback acceptance; preserve this failed attempt permanently. Audio, input, latency and sustained performance remain separate gates.
5. Review precedes this follow-up under the user's latest request. Existing authorization for implementation and finite qualification remains; this report creates no extra permission cycle. The full implementation goal stays active, with P0-S4 incomplete and no capability promotion.

Private reviewer packets are under `.cache/native-stream-android/comprehensive-review-01/`. Public results expose sanitized facts and exact artifact bindings; raw device/UI/host material remains ignored. Documentation validation and the exact reviewed Git checkpoint belong in project history.
