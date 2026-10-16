# Specimen typography and density visual review — 2026-10-16

The corrected final browser run passes the bounded synthetic HTML specimen review: sixteen layout captures and ten interaction-state captures. This advances the existing P1-S2 design specimen; it does not qualify native Android controls or complete P1-S2. The [sanitized manifest](2026-10-16-specimen-density.json) records source hashes, screenshot hashes, dimensions, computed metrics and viewport/inspector state before and after each capture. Absolute local navigation URLs are excluded from the public manifest.

## Reviewed change

The specimen binds section headings to the existing 20/28 role, explanatory body text to 14/20, and metadata to 12/16. Comfortable, Standard pointer and Compact pointer use nominal 48/40/32 CSS-pixel row/control minima with content-driven growth and unchanged text size. The HTML select control includes explanatory help and polite announcements. Neutral workspace monograms retain an existing-token outline across row fills; visible focus and native control semantics remain present. Canonical tokens and palette are unchanged.

## Execution and attempt history

The accepted run used Google Chrome 155.0.8059.39, headless Chrome/CDP, with the Chrome sandbox enabled outside the command sandbox. Evidence remains under `.cache/specimen-density-01/`; no screenshots or third-party binaries are copied into this public report. Specimen source hashes remained unchanged across the attempts.

| Attempt | Actual disposition |
| --- | --- |
| run-01 | Chrome SUID sandbox startup abort inside the command sandbox. No frame captured; no visual pass. |
| run-02 | Harness reported PASS, sixteen layouts plus ten states. Review found unmatched inspector arrangements and transient announcements covering footer notes; this was not final visual acceptance. |
| run-03 | Failed before any render because the new-document URL comparison did not encode spaces. Zero layout and state captures. Failure preserved. |
| run-04 | Final PASS, sixteen layouts plus ten states. Uses normalized `new URL(...).href`, completed navigation/font loading, actual inspector toggle for wide-open/narrow-closed state, natural announcement expiry, and viewport-only capture. |

Run-02's navigation acknowledgment could precede replacement of the previous complete document; its readiness predicate did not distinguish documents. Its evidence does not prove which mechanism caused every observed pane transition. The final harness establishes the intended pane through the actual UI and records stable viewport/pane state around each capture instead of treating that artifact as a product defect.

## Direct visual review

The independent direct Codex GPT6.1-Sol reviewer viewed all sixteen final layout PNGs through `view_image`: Console and Desktop at 1440px in both appearances and all three density tiers (twelve), plus both apps and appearances at 560px Compact pointer (four). No actionable visible clipping, overlap or hierarchy defect was observed. All wide layouts now show the same open inspector; both appearances retain readable section/body/metadata distinctions. Rows and controls change spacing without shrinking text, multiline metadata remains contained, and narrow explanatory text wraps. File columns remain readable and the narrow Console pane selector is visible. Announcements have expired before capture. The local review receipt is `.cache/specimen-density-01/run-04/layout-visual-review.md`.

The integration leader separately viewed all ten final state captures: light/dark rest, hover, hover plus keyboard focus, selected plus focus, and command palette. The neutral monogram outline remains visibly distinct; keyboard focus and selected state are visible, and inspector state is stable. Pressed/down state was not covered.

## Qualification boundary

These are synthetic HTML references, with synthetic sessions, devices and telemetry. CSS pixels and the nominal density minima do not establish native dp, native input sizing or physical hit targets. Only content in the captured viewport is accepted; offscreen/footer content is excluded wherever outside it. The captures do not establish arbitrary browser zoom or font scaling, native Android profile/trust/vault presentation, scaled-native workspace behavior, physical keyboard/pointer/touch input, system caption controls, or real host/session/device functionality. Native application and font-scale evidence remains separate. Neither this specimen nor its successful render adopts an engine or creates a supported connection capability.

The parent retains integration, source checks and final acceptance. This report uses the existing captured evidence and does not claim a new APK build or device run.

A separate direct Codex reviewer recomputed the four source hashes and all26 PNG hashes/dimensions, compared the sanitized manifest to the raw record, and checked all four attempt dispositions. The leader accepted the source diff and independently viewed all ten state captures.
