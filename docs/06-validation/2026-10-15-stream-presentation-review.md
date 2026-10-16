# Streaming presentation and retirement repair review — 0.1.10

**Reviewed 2026-10-15. Source repair accepted; visible playback remains FAILED from the 0.1.9 run.** This increment addresses the [comprehensive review](2026-10-15-stream-comprehensive-review.md). It does not establish the cause of the missing video or turn decoder activity into a visual pass. The [current execution state](../05-delivery/execution-state.md) remains the continuation authority.

The parent reviewed the integrated diff and actual evidence. Direct Codex GPT6.1-Sol workers owned separate presentation and lifecycle files, with a separate read-only reviewer. Parent integration found and resolved additional error paths before accepting the source. OMP remains Kimi K3 only. No new native-engine implementation or exhaustive upstream revalidation was required under D12.

## Finding disposition

| Finding | Implemented correction | Remaining acceptance boundary |
| --- | --- | --- |
| SR-01: exhausted or disabled preview lacks an actionable reason | Adjacent typed reason, actual phase/operation ID and explicit fresh-process requirement. Evidence/cleanup/owner failures take precedence over ordinary prerequisites | Actual user interaction with the revised diagnostic; product reconnect remains future work |
| SR-02: video and Stop inside the long form; distorted aspect | Preview constructed outside the controls ScrollView, 16:9 containment, Stop above the preview, native system/IME insets and bounded pane sizing | Actual narrow-window/font-scale usability and visible moving content |
| SR-03: decoder output does not establish texture presentation | Count actual TextureView updates and monotonic first/last update times. Retain immutable clipped geometry, attachment, shown and hardware-window flags, at most 16 ordinary samples plus dedicated milestones | A fresh identified run and user observation. Neither these flags nor texture callbacks prove visible pixels or lack of occlusion |
| SR-04: unclear phase and deadline | Original authentication/prompt deadline countdown, neutral `AUTHORIZATION_DEADLINE` for new records; no reset after setup | Real prompt/timeout interaction. Old reason codes remain historical evidence |
| SR-05: cleared catalog owner can lose its final journal rewrite | Shared PAIR/CATALOG retirement coordinator retains executor and process lease until actual prompt and worker completion, then verified terminal persistence. Local cleanup retry performs no network work | Actual Android callback/recreation/failure qualification remains separate from production-coordinator JVM tests |
| SR-06: generic or hidden evidence blockers | Separate capacity, unavailable, malformed, current/prior-process and owner states; explicit recovery text, with no deletion or slot expansion | Device behavior of the failure paths remains unqualified |
| SR-07: contradictory long-run context | Canonical current state, historical-result labels, one correlated test question and preserved negative visual verdict | Keep these records current at every accepted milestone |

## Additional findings resolved during independent review

1. Extending the normal retirement assertion to PAIR exposed a missing PAIR guard in completion/cancellation paths. Both PAIR and CATALOG now retain ownership when terminal readback is absent instead of throwing on the main thread.
2. The first UI revision masked an evidence failure with missing catalog selection or permission. The production-used admission policy now preserves evidence, cleanup and process-owner reasons; focused tests cover precedence and unknown states.
3. A successful local terminal-write retry could release ownership while preserved error provenance still blocked networking, without explaining recovery. Active PAIR/CATALOG now expose local cleanup, and successful durable retirement with a recorded evidence failure explicitly requires a fresh process and local exact-byte reconciliation. No automatic replay or error-history erasure occurs. Local notifications refresh a recreated visible window after the old owner's barrier completes.
4. Each PAIR/CATALOG persistence attempt resets its terminal acknowledgement before writing. Only that write's verified closed record and marker removal establish acknowledgement. This is invariant hardening; review did not demonstrate a prior reachable bypass or connect it to the missing video.
5. Final Lint comparison found an owned unused string left by the new Stop control. The first audit's Java-only ownership detector missed the resource path. The parent preserved that first artifact and a correction receipt, removed the unused resource, expanded the audit to owned resources, and required a fresh final build. The first artifact must not be described as zero owned findings.

6. Final review confirmed that normal Stop after a persistence failure can succeed at local terminal retirement outside the coordinator hook. Central normal PAIR/CATALOG retirement now exposes the same fresh-process reconciliation message. The intermediate `presentation-02` build remains preserved; final `presentation-03` validates the exact corrected source.

## Ownership and observation invariants

Each TextureView callback retains its exact owner. An old, destroyed or retired owner cannot add observations to a new attempt. The actual callback clock supplies first/last update times; no synthetic frames, per-frame allocations/posts/journal writes or pixel reads are added. Worker persistence receives immutable snapshots and never accesses a View.

Native retirement, actual TextureView destruction, Surface release, terminal journal readback and process-lease release remain distinct steps. There is no live reparenting, decoder rebinding, native allowance reset, implicit key generation, authorization extension or automatic session restart. The original 30-second combined authorization/run bound and one-native-run-per-process restriction remain diagnostic limitations, not product session/reconnect requirements.

The geometry follows the official [View visible-rectangle contract](https://developer.android.com/reference/android/view/View#getGlobalVisibleRect(android.graphics.Rect)): clipping within the root view, with absent rectangles explicitly zeroed. It does not measure other-window occlusion. Actual update callbacks follow the [TextureView listener contract](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener); this is a separate observation boundary from codec render callbacks. G25/G151/G154 and API47 track availability; existing minSdk 35 covers these public symbols.

## Evidence and next action

Use the separately bound [0.1.10 build report](../../qualification/results/P0-S4/2026-10-15-stream-presentation-build.md) for exact compilation, JVM, Lint, source and APK evidence. Focused JVM tests exercise the real production coordinator and presentation policies; they are not an Android lifecycle or visual playback pass.

Before a device follow-up, verify installed version, process, preserved records and fixture state. Inspect the fixed pane and controls before a fresh finite host run. Keep one clearly identified question outstanding, retain the original authorization deadline, and correlate live presentation evidence with the user's actual observation. Do not repeat an unchanged 0.1.9 test or infer success from counters. Wrong-pin/cancellation/expiry cases, physical input, audio, performance, sustained behavior, D16 release closure and CAP-STREAM remain open.
