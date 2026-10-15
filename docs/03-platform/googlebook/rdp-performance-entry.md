# RDP presentation performance entry

**Checked:** 2026-10-15. **Scope:** bounded adapter review of diagnostic 0.1.5 and the completed 0.1.6 status-update experiment, not a latency qualification or renderer replacement. P0-S4 remains subject to the [desktop performance procedure](../../06-validation/desktop-performance.md) and [library reuse policy](../../05-delivery/library-reuse-policy.md). The integration leader owns implementation acceptance and subsequent device measurements.

The bounded status-update candidate is implemented and checked in 0.1.6: it avoids repeated unchanged diagnostic controls and paces counter-only text. The [build/review](../../06-validation/2026-10-15-rdp-status-review.md) and [matched run](../../../qualification/results/P0-S4/2026-10-15-rdp-rendering.md) show no useful timing improvement: both workload samples have p95 11 ms and one janky frame; p99 is 14/15 ms for baseline/candidate. Operation reduction is supported by source/tests, not a measured operation trace. Framebuffer copying, software rendering, input order and native protocol behavior remain unchanged.

## Observations and measurement limits

The user's 0.1.4 pre-capture observation was “working but pretty laggy.” The repaired 0.1.5 [cursor result](../../../qualification/results/P0-S4/2026-10-15-rdp-cursor-repair.md) established host trail, cursor movement and release, without a new latency rating. These observations do not establish that 0.1.5 has the same performance or that its cursor repair reduced latency.

The leader retained unchanged-0.1.5 Android `gfxinfo` samples in private `native-rdp-session/2026-10-15-run-12`, against the same isolated GRD software target. The earlier observation labelled `idle` covered approximately 33 seconds and includes warmup and operator screen activity; the synthetic workload used four bounded ADB mouse swipes of three seconds each. They are different workloads, **not an A/B comparison**.

| Android framework summary | Warmup/operator activity (`idle` filename) | Synthetic pointer workload |
| --- | --- | --- |
| Frames rendered | 455 | 1,426 |
| Janky frames | 136 (29.89%) | 1 (0.07%) |
| Frame-time p95 / p99 | 23 / 24 ms | 11 / 14 ms |
| Slow UI thread counter | 136 | 1 |
| Slow issue draw commands counter | 134 | 0 |
| High input latency counter | 11 | 1,089 |

Process/window summaries repeat the same totals in each file; they must not be summed. These are Android rendering diagnostics, not decoded RDP frame counts, physical input-to-photon, network latency or host encode measurements. In particular the high-input-latency counter is not 1,089 measured end-to-end latency samples. The workload result does not identify status rendering as the cause of that earlier jank. Ignored `.cache/rdp-performance-review/gfxinfo-summary.json` records the two file hashes and extracted counters.

The retained run-11 post-physical logcat has 734 lines and no matched skipped-frame, Davey or ANR markers in a bounded keyword scan. The eleven-line GRD log supplies no per-stage codec timings. Absence of those markers does not prove low latency. Hashes and bounded classifications are in ignored `.cache/rdp-performance-review/retained-log-summary.json`; private log contents are not reproduced here.

## Concrete adapter work and hypotheses

Locations below identify the reviewed 0.1.5 source, before any subsequent optimization; line numbers can move.

| Source boundary | Observed work | Consequence and uncertainty |
| --- | --- | --- |
| `RdpActivity.kt:163–182`, `render` / `renderCapture` | Every notification formats status, assigns text and control state, reads LAN permission three times, updates the frame and sets capture labels again. | Redundant control work is established. Its duration and contribution to jank are unknown. Preserve immediate error/capture visibility and native field editing. |
| `RdpSession.kt:118–124,147–151,290` | Notifications coalesce into one pending main runnable, but are not aligned to display frames; the observer also publishes every 100 ms. | A framebuffer/cursor burst can cause repeated control renders. The poll does not impose a 100 ms cursor delay: input/cursor changes already notify immediately. |
| `RdpFrameView.kt:57–60,67–119` | Software layer; every update invalidates; each draw scales the whole bitmap and draws cursor/status overlays, including cursor-only updates. | Potential scaling/draw cost, not timed. Do not substitute hardware rendering without a bitmap lifetime design. |
| `RdpFrameBuffer.kt:36–82` | One monitor protects native dirty-rectangle copy, cursor state and the full scaled Canvas draw. | Draw/copy can block cursor state access. `RdpSession.kt:338–350` acquires the cursor revision before JNI relative admission, so this contention can delay the input owner. Measure wait and hold separately before redesign. |
| `RdpSession.kt:520–536` | The ordered owner serializes attempts and writes the atomic journal periodically and at important edges. | A possible owner stall, not observed timing evidence. Do not weaken evidence durability or move lifecycle writes asynchronously without an explicit ordering design. |

The software layer currently makes pixel consumption synchronous with the framebuffer monitor. Android hardware display lists can outlive the Java draw call. Removing that layer alone would invalidate the current safety reasoning for mutable bitmap pixels; it is not an accepted performance fix. Avoid forced bitmap recycling for the same lifetime reason.

## Implemented candidate and bounded follow-up

Implemented in 0.1.6: cache effective control values on the main thread and apply setters only when values change; read LAN permission once per render, refreshing on resume and permission results. Separate the status identity (attempt, state/detail, native version, geometry and connection state) from counters. Render identity changes immediately, including terminal errors; format counter-only updates at most four times per second, using existing notifications and the 100 ms poll rather than a new timer. Keep `frame.update` on every existing notification. Capture request/confirmation/release controls remain immediate. No key, pointer, Unicode, queue, native, pixel-copy or drawing change belongs in this candidate.

If needed to select the next change, add bounded instrumentation with fixed allowlisted trace sections around controls rendering, frame copy/draw and journal persistence. Record adapter input enqueue-to-owner and owner-to-JNI-admission durations using one monotonic clock, such as `SystemClock.elapsedRealtimeNanos`; do not subtract MotionEvent uptime timestamps from elapsed-realtime timestamps. Measure framebuffer monitor wait at the call site and hold inside the synchronized body. Use aggregate counters/fixed histograms, no per-input disk writes, secrets, text, endpoint identities, coordinates or pixels. Native queue admission remains distinct from wire delivery and host consumption.

For window timing, `FrameMetrics` can expose draw, layout/measure, total duration and deadline; unavailable values remain unavailable and overlapping durations must not be added. These still stop before visible photons. A future one-shot `Choreographer` callback could bound notification work per display frame if measured necessary, but is not required for this first candidate.

The finite matched baseline/candidate run has met this experiment’s stop condition, using the same target, resolution, route and scripted workload with the UI/version increment. The sample and its limits are recorded in the linked report; it demonstrates no useful timing improvement. The source/tests establish the bounded control/status policy, while the workload has no capture exercise and no operation-count trace. Stop attributing lag to status and investigate a measured draw/monitor or host-stage cost; claim an improvement only for measured metrics. This entry does not authorize a general engine audit, renderer rewrite, product performance claim, 3D pass or parity benchmark.

## Official guidance and availability

Checked 2026-10-15; diagnostic minimum API 35. The official [custom-view optimization guidance](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view) recommends reducing allocations and unnecessary layout/invalidation. [Android hardware acceleration documentation](https://developer.android.com/develop/ui/views/graphics/hardware-accel) explains layers and display-list behavior; `View.setLayerType` is available since API 11. [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics) is available from API 24, with `DEADLINE` from API 31; unavailable metrics require honest handling. [Trace](https://developer.android.com/reference/android/os/Trace) section APIs are available from API 18 and require matching same-thread sections. [Choreographer.postFrameCallback/removeFrameCallback](https://developer.android.com/reference/android/view/Choreographer) are available from API 16.

The official [AOSP TextView source](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/widget/TextView.java) explains how text assignment can rebuild layout and request layout/invalidation. The inspected `main` source is explanatory, not a claim to match the exact device framework binary; its hash and inspected lines are retained in ignored `.cache/rdp-performance-review/sources.json`. Platform fallback is the existing software/ordered-input path. P0-S4/P3 performance qualification still requires actual measured boundaries under the desktop procedure.
