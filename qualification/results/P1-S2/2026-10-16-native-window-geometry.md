# Native workspace window geometry and system text scale

**Date:** 2026-10-16. **Outcome:** eight actual Googlebook JUnit cases pass across Console/Desktop at 130%/200% system text scale and three actual OS window sizes. All eight effective preference restorations and final settlement pass. The leader viewed all16 native PNGs and accepted their bounded light-theme viewport content. This advances P1-S2 workspace qualification; it does not complete the native component or accessibility review.

Both installed products remain **0.1.6/code7**, byte-identical to the preceding [display-settings checkpoint](2026-10-16-native-display-settings.md). This increment adds test sources only. The runner installs two test APKs, never installs/upgrades a product, and never clears app data. The [build and measurement manifest](2026-10-16-native-window-geometry-build.json) binds the selected implementation/test sources, five APKs, logs,16 PNGs and eight raw reports with sanitized measurements. Serial, task/window identities, generation values and absolute private paths are excluded from that public manifest.

## Public APIs and bounded test method

The opt-in `WindowGeometryContract` uses an empty Compose test rule and explicitly owned `ActivityScenario` to launch the actual owning-app workspace. `ActivityOptions.setLaunchBounds(Rect)` supplies screen-space pixel bounds; the OS may ignore or clamp them. Every case therefore checks actual Activity `WindowManager.currentWindowMetrics`, decor dimensions and Activity font configuration, rather than forging a Compose context or assuming the request succeeded. The Activity's actual width/height must be positive and at most the requested dimensions plus96px. The measured results below meet the requested dimensions exactly, with an11px vertical position adjustment.

The host runner changes the real current user's `font_scale` setting. The test APK never writes global settings. Before mutation, the runner persists the original literal and exact restoration command, including delete semantics for an originally absent value. Scale is verified in each actual Activity. Android's nonlinear text scaling means130%/200% are selected system configurations, not requirements that every text dimension multiply linearly.

Each case preserves the initial window snapshot, task, title and Activity generation across the app density change. It enters an uncommitted native field draft with selection, scrolls each of seven actions into view, checks it displayed/enabled and preserves draft/context. It does not invoke Apply, New, Close, vault, profiles, trust or settings actions. The seven actions are checked at **separate scroll positions**, not required to fit simultaneously.

## Build progression and preserved failures

Source base `90a8c00abf65e1f7ac6f3621b83f074bb5022369` plus the three new geometry test files. Frozen builds and original sources/logs remain under `.cache/window-geometry-01/`.

| Attempt | Outcome and interpretation |
| --- | --- |
| Build01 | Compilation fails because `performTextReplacement()` returns Unit; chaining selection is invalid. The original source is preserved. No device run used this artifact. |
| Build02 / device01 | Compilation passes. The first Console case fails the raw `hasVisualOverflow` assertion on Saved credentials. Execution stops immediately; failure evidence and its partial app capture directory remain. Global/app state is restored. |
| Build03 / device02 | Additional diagnostic metrics compile. The same first-case assertion fails, now with a measured239×44px layout, reconstructed520×44px paragraph, raw width overflow=true and height overflow=false. The single complete line spans0–238.3125px, with no ellipsis. This is not a small fractional rounding discrepancy. Failure evidence and its partial app capture directory remain. |
| Build04 / device03 | The test distinguishes reconstructed container overflow from actual line/node geometry while retaining all raw flags. Eight real cases and settlement pass. No product implementation changes. |

Build04 runs **10:47:43.994557Z–10:48:02.958641Z**, reports `BUILD SUCCESSFUL in18s`,313 tasks with16 executed/297 up-to-date, strict dependency verification and unchanged sources during the build. Console, Desktop, platform and design-system Lint reports each contain zero issues. Domain38 and design-system4 passing JVM reports carry forward through up-to-date tasks; these are not newly executed JVM tests. Selected dependencies remain unchanged.

The selected `ui-text1.12.1` bytecode defines width overflow as `size.width.toFloat() < multiParagraph.width`, and height overflow as exceeded maximum lines or `size.height.toFloat() < multiParagraph.height`. Simple-string `GetTextLayoutResult` semantics reconstruct a new MultiParagraph at prior constraints and pair it with the cached integer layout size; drawing uses the cached Paragraph. Exact AAR hashes are recorded in the manifest, with extracted bytecode review at `.cache/window-geometry-01/source-review/`.

All56 successful action measurements retain **raw `hasVisualOverflow=true`**. Those flags are neither hidden nor asserted false. The final checks instead require exact label text, complete contiguous character coverage, no ellipsis/max-line loss, every line inside measured layout size, layout size matching the actual node, clipped/unclipped node agreement and full label containment in the actual viewport. Geometric comparison tolerance is1px. The checks qualify text-layout/viewport bounds, not font-ink or interactive hit-target extents.

## Actual Googlebook run and measurements

Target: **HP Googlebook14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Successful run03: **10:48:10.376919Z–10:48:35.981979Z**. Raw private evidence remains in `qualification/results/private/foundation/2026-10-16-window-geometry-03/`; failed packets01/02 are retained alongside it.

Each row below runs once in each app, for eight actual JUnit cases. Each case produces one `MEASURED_PASS` and one effective-baseline restoration receipt; these statuses are not additional tests.

| Configuration | System text scale | App density | Actual OS window | Actual captured root |
| --- | ---: | --- | ---: | ---: |
| Narrow/tall | 1.3 | Comfortable | 650×900px | 650×835px |
| Wide/short | 1.3 | Standard pointer | 1300×560px | 1300×495px |
| Narrow/short | 2.0 | Comfortable | 650×560px | 650×495px |
| Narrow/short | 2.0 | Compact pointer | 650×560px | 650×495px |

Actual Activity scale reports approximately1.2999999523 or exactly2.0, at density1.625/260dpi. All eight report multiwindow mode. Requested rectangles start at48,48px; actual rectangles start at48,59px while retaining the requested dimensions. The captured root is65px shorter than decor/window height. Reported system-bar/cutout insets are all zero; this record does **not** attribute the difference to a measured caption or infer caption geometry.

Each case captures the top viewport and final-actions viewport, yielding16 PNGs. The leader inspected all16 and accepted headings, native field/selected draft and readable visible actions in the bounded light-theme content. The final captures show Apply/New/Close fully readable. Vertical scrolling intentionally clips offscreen items at viewport edges. The screenshots do not show every action at its separately asserted scroll position; the56 semantics measurements cover those positions. Baseline appearance remains System with currently light content; scaled Dark rendering is not qualified here.

## Restoration, fixture boundaries and remaining gates

All eight successful capture directories are copied, locally hashed and removed by exact receipt-bound filenames; directory absence is verified. The two earlier failed partial capture directories are intentionally retained as evidence. No additional store fixtures are created. Raw local reports retain private task/window/generation context; only sanitized metrics are published.

Finally settlement restores the original current-user font-scale literal **1.0** and verifies default-display framework configuration **1.0**, rather than treating the settings literal alone as configuration proof. Eight normal production-owner restorations return effective app preferences to their baseline. Profile/trust rows and generations, vault receipt hashes, product APK bytes, boot ID/count, display density and recorded night-mode setting are unchanged. Both products are stopped; the two installed test packages are removed and all three possible instrumentation package absences are verified. Cleanup reports no errors.

This run establishes these workspace layouts and lifecycle/draft invariants at these exact sizes and scales. It does not qualify arbitrary smaller windows, maximize/fullscreen or external-display transitions, all native managers/dialogs, protected vault presentation, scaled Dark appearance, hit-target overlap, font-ink bounds, physical keyboard/pointer/IME behavior, screen readers or remote-engine layouts. P1-S2's complete native workspace/component review and later feature-specific gates remain open.

## Final review and repository checks

An independent direct Codex GPT6.1-Sol reviewer verified all11 selected source hashes, five frozen APKs,16 PNG dimensions/hashes, eight raw report hashes and their sanitized measurements, build/failure log receipts and actual eight JUnit completions/restorations. All56 raw overflow flags remain intact. The leader separately inspected all16 native captures and accepted the final source correction.

Repository Python AST32files, Ruff, JavaScript syntax, generated Android-token freshness and Git whitespace checks pass. Project validation accepts126JSON/302Markdown/2,334local links/503sources/57API records/82declared contrast checks. Schema checks use the existing `.venv` and accept three schemas/three examples while rejecting six invalid cases; an initial system-Python invocation lacked that optional dependency and reported no schema result. These repository checks do not expand device qualification. All seven hosted workflows pass at predecessor90a8c00; this increment requires its own exact-commit CI after push.
