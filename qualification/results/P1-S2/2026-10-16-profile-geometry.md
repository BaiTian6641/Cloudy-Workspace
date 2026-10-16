# Native profile reveal and scaled confirmation repair

**Date:** 2026-10-16. **Outcome:** Console/Desktop **0.1.7/code8** repair two observed profile-manager defects. Six actual geometry cases and sixteen existing profile/lifecycle regression cases pass on the HP Googlebook. Six default opt-in checks skip before fixtures or Activity launch. Both products are installed and stopped, temporary test packages are absent, and all recorded data/configuration preservation checks pass. P1-S2 and P1-S3 remain in progress.

The [source review](../../../docs/06-validation/2026-10-16-profile-geometry-review.md) explains the implementation and independent review. The [build/device manifest](2026-10-16-profile-geometry-build.json) binds 101 source files, five final APKs, four Lint reports, build/run receipts, all 38 retained PNGs and sanitized geometry. Private serials, catalog generations, fixture IDs, labels and hosts are excluded from the public manifest. The unchanged external Word feedback remains review input, not implementation authority.

## Observed problem and repair

On the unchanged 0.1.6 Console, selecting the final test-owned profile after fifteen preceding test profiles left the viewport on the saved list. The accepted editor was above it and its label field did not compose during a passive five-second wait. The test deliberately did not scroll after Edit. At a separate short-window/200% text setting, the Delete confirmation displayed its title and actions but had no readable space for the saved identity; the saved label's measured text height was zero. Both failures have retained screenshots, reports and successful cleanup.

The repair preserves the existing local store and controller. `rememberLazyListState` and an editor-ID-keyed `LaunchedEffect` deliberately reveal an accepted new/replaced editor. A saveable last-revealed ID prevents an existing restored editor from becoming a new navigation request. Normal text, selection, protocol and review changes retain the same ID. Successful Save/Reapply/Reload produces a fresh normalized editor and therefore also reveals it. No focus request or database operation is added by scrolling.

The confirmation now puts its title, explanation and frozen saved identity/revision into one scrollable text region, keeping Confirm and Cancel outside that region. Moving the title into the same region matters: scrolling only the old body would still leave too little height for one scaled line. The request-keyed scroll state starts each fresh confirmation at the top. Original request closures, generation/revision checks, foreground interaction checks, pause dismissal and no-replay behavior remain unchanged. Text keeps its existing roles and user scale; no palette change, font reduction, truncation or fixed compact row height is introduced.

Official guidance is tracked as G212 and G220–G222/API58. It documents [lazy-list state and scrolling](https://developer.android.com/develop/ui/compose/lists), [ordinary scroll modifiers](https://developer.android.com/develop/ui/compose/touch-input/scroll/scroll-modifiers), [effect keys](https://developer.android.com/develop/ui/compose/side-effects) and [Material dialog slots](https://developer.android.com/develop/ui/compose/components/dialog). The chosen confirmation arrangement is Cloudy's response to the observed geometry, not a layout mandated by those guides.

## Build and attempt history

Source base: `6f1578fa8bfb42c8b06e7f75270ffdce99122b3e` plus the reviewed production/test changes. Frozen build receipts and logs are in `.cache/profile-geometry-01/`; private device packets are `qualification/results/private/foundation/2026-10-16-profile-geometry-01/` through `04/`.

| Attempt | Actual result |
| --- | --- |
| Build01 | Test compilation fails because wildcard imports make `R` ambiguous. No device execution uses this artifact. An explicit platform `R` import repairs it. |
| Build02 / device01 | Strict compilation passes. Two unchanged-product Console observations fail as described above; exact profile/preference restoration and final settlement checks pass. Failed capture directories remain on the device. |
| Build03 / device02 | The production repair and stronger tests compile. Six geometry cases pass, three per app, followed by complete settlement. Products upgrade in place without clearing data. |
| Device03 | The host parser incorrectly expects ignored-test status `-3`; AndroidJUnitRunner reports assumption failures as `-4`. All three Console tests actually skip before evidence/fixture creation. The host reports failure and stops before regression cases; original logs and successful preservation checks remain. No product defect is inferred. |
| Device04 | Corrected parser requires the exact opt-in assumption message and status `-4`. Six default skips and sixteen actual profile/lifecycle regression passes are distinguished correctly. Full settlement passes. Product/test APK bytes remain those of build03. |

Build03 runs **11:06:55.252107Z–11:07:42.066219Z**, reports `BUILD SUCCESSFUL in46s`, 313 tasks with63 executed/250 up-to-date, strict dependency verification and unchanged source hashes during compilation. Console, Desktop, platform and design-system Lint each report zero issues. Domain38/design4 passing JVM reports carry forward through up-to-date tasks; they are not newly executed tests. No dependency or toolchain versions change.

## Actual geometry and interaction assertions

Target: **HP Googlebook14, Android17/API37, arm64-v8a, buildCL3B.260622.276.R2, incremental16560795**. Device02 runs **11:07:47.350853Z–11:08:20.336544Z**. Each row below executes once per app, for six actual JUnit passes. App density is Comfortable; appearance is the current Light rendering of System.

| Case | Actual OS window / captured root | Actual system text scale | Bounded observation |
| --- | --- | ---: | --- |
| Deep-list Edit | 650×900 / 650×835px | approximately1.3 | Heading and label field become fully visible without a test scroll after Edit; selected saved label remains exact. |
| Long-identity Delete/Cancel | 650×560 / 650×495px; dialog520×495px | 2.0 | Valid80-character ASCII label and253-character DNS host; all title, explanation, label, endpoint and revision lines become fully visible through ordinary scrolling; both fixed actions visible/enabled; Cancel retains unsaved text/selection and exact catalog. |
| Typing and recreation | 650×560 / 650×495px | 2.0 | After one deliberate scroll to the port field, typing2222 and selecting1–3 causes no further jump. Actual Activity recreation retains that text/selection and port/root coordinates within1px, with unchanged framework geometry/scale. |

Both apps measure density1.625/260dpi. Requested window origin48,48 becomes48,59 while preserving requested width/height. Root height is65px less than window/decor height; this is not a measured caption-inset claim. The test reads actual framework configuration rather than substituting a Compose density or assuming launch bounds succeeded.

The repaired dialog scroll viewport is **442×184px**, spanning x39–481/y39–223 in its own root. The five text blocks contain **2/7/5/15/1 lines** respectively. Checks retain contiguous complete text, no ellipsis/max-line loss, line/layout/node geometry and the set of fully visible lines accumulated over bounded scrolling. Long identity text is intentionally not required to fit simultaneously. The Cancel/Confirm action bounds are checked separately. Raw coarse overflow flags do not replace these measurements; the preceding [Compose semantics investigation](2026-10-16-native-window-geometry.md) still applies.

The leader viewed nine repaired Console samples and independently sampled three Desktop views. A direct Codex GPT6.1-Sol reviewer viewed the corresponding nine Desktop samples. They show the revealed editor, scrollable confirmation, retained draft after Cancel, and aligned port viewport before/after typing/recreation. Individual dialog screenshots show viewport fragments, not complete identity proof. Native single-line fields may horizontally scroll their long value; full saved identity is available in the confirmation's scrolling text. Focus styling changes after recreation; this run qualifies text/selection and viewport retention, not restored keyboard focus or IME composition.

## Regression and preservation evidence

Device04 runs **11:10:01.774790Z–11:10:42.199698Z** on unchanged build03 APKs. Per app, six existing `ProfileActivityContract` cases cover real create/update/confirmed delete, unsaved draft/selection recreation, conflict/review/reapply, another change after review, deleted-row non-recreation and Delete/Cancel preservation. Two `ProfileRecreationContract` cases cover admitted create/update completion across controlled Activity recreation without replay. Sixteen tests complete successfully; six assumption skips from the geometry class are counted separately and emit no fixture/display baseline receipt.

New geometry cases seed unique temporary profiles through the real owning-store API, after persisting the baseline and validating every draft. The deep-list target is the last owned row in actual catalog order, so unrelated preexisting UUID ordering does not decide the test. Cleanup closes the Activity, drains the actual queue, requires exact baseline-plus-owned state and deletes only known fixture IDs through revision-bound APIs. Unknown state preserves evidence/fixtures. Geometry passes require successful JUnit completion plus all six fixture-cleanup and six effective-preference restoration receipts; `passed=true` inside a report precedes final cleanup and alone is insufficient.

The host verifies actual database rows/generations, vault receipt hashes, effective preferences, installed APK hashes, boot identity/count and global font/density/night settings. Original font literal and default-display framework scale1.0 are restored after geometry. Product upgrade preserves data; no app-data clear, authentication prompt or network connection occurs. Successful capture directories are locally copied/hashed and removed by exact receipt-bound paths; the two baseline failed capture directories remain intentionally retained. Both products are stopped; all three possible instrumentation packages are absent. All final preservation checks pass with no cleanup errors.

## Scope remaining

These results repair and qualify the sampled local-profile workflows. Scaled Dark rendering, other density modes, arbitrary smaller windows, physical keyboard/mouse/trackpad, IME composition, screen readers, hit-target geometry, process death, power loss and protected authentication remain separate work. No remote protocol, credential enrollment or release capability is promoted. P1-S2/P1-S3 and the full P0–P6 goal remain active.

Independent source/test review preceded device acceptance. A separate evidence audit verifies101 source hashes, five final APKs, four zero-issue Lint files, all build/run/log/report receipts and38 PNG hashes/dimensions. Repository Python AST32files, Ruff, generated Android-token freshness and Git whitespace checks pass. Project validation accepts127JSON/304Markdown/2,349local links/506sources/58API records/82declared contrast checks; three schemas/three examples pass and six invalid examples are rejected. These repository checks do not expand runtime qualification. All seven hosted workflows pass for predecessor6f1578f; this commit requires its own hosted result after push.
