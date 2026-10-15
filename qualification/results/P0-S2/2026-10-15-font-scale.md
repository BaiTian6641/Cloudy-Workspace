# P0-S2: narrow-window font-scale observation

**Run:** 2026-10-15, approximately 07:05–07:14 UTC. **Verdict:** pass for the stated diagnostic control-reachability, state-preservation and setting-restoration checks. **P0-S2 remains in progress and CAP-PLATFORM remains pending.** This experiment used the existing **0.2.0** probe; it supplies no result for the separate 0.3.0 engagement implementation.

## Source and device identity

The installed debug APK was `dev.cloudy.qualification.baseline`, version **0.2.0 / 2**, report schema **2**, built from [13dce23dd4052163f22703752b16438ec81c8306](https://github.com/BaiTian6641/Cloudy-Workspace/commit/13dce23dd4052163f22703752b16438ec81c8306). APK SHA-256: `4a7a47df18b8d48541ce7f1d7ed347c0d6e48c2f79898c8e65064199d1596b32`.

Target: **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**. The [earlier window run](2026-10-15-window-probe.md) binds this source/APK to compiler, lint and 17 JVM tests. The [process-restoration run](2026-10-15-process-restoration.md) precedes this experiment; the process-start timestamp remained `2026-10-15T06:37:12.976557Z` throughout the retained font reports.

The [public JSON manifest](2026-10-15-font-scale.json) contains **nine report projections**, **30 selected correlated events**, control-presence checks and **66 private artifact hashes**. It publishes no raw UI tree, screenshot, command output, transport identifier or unrelated desktop content. Host capture timestamps and device clock readings remain separate; their difference is not a latency measurement.

## Setup and measured behavior

The primary agent retained existing window **B**, task **1000150**, with marker **C / 2**, then opened temporary window **C**, task **1000151**. These window letters identify roles in the experiment, not the displayed marker. C initially had marker A / 0, was resized, and received one explicit marker change to **B / 1** before the enlarged-font checks. The original current-user `font_scale` value was read and retained as **`1.0`**.

C's narrow window measured **648 × 600 px** at density **1.625 / 260 dpi**, with reported configuration size **399 × 369 dp**. Its attached decor reported a visible native caption with **65 px** top inset. Window/decor height includes that caption; it is not an assertion of a 600 px content area. Controls and text remained inside the app's existing scroll container.

| Setting / observation | Evidence and result |
| --- | --- |
| 100% baseline | The initial private screenshot and layout show the resized window before the marker change. The initial creation report, revision 19, predates resizing and must not be used as narrow-window geometry. Narrow geometry at font scale 1.0 is also recorded after restoration in revision 97. This run does not claim all four controls were simultaneously visible at the initial scroll position. |
| 130%, actual configuration `fontScale=1.3` | After **one injected vertical swipe**, the current own-app layout showed **New window**, **Change marker**, **Recreate this window** and **Refresh diagnostics** fully inside the content viewport. A tap matched Refresh's freshly inspected bounds. Report revision **54** contains C's refresh-requested event **52**, process refresh-started **53** and refresh-finished **54**. |
| 200%, actual configuration `fontScale=2.0` | The first swipe left only the beginning of the control group reachable in the captured layout. After the **second injected vertical swipe**, all four controls were fully inside the content viewport. A fresh bounds-matched Refresh tap produced request/start/finish events **76–78** and report revision **78**. |
| Restored 100%, actual configuration `fontScale=1.0` | The setting was restored to its exact original value and read back as **`1.0`**. Revision **97** confirms font scale 1.0 for both windows, C still **648 × 600 px**, C state **B / 1**, and B state **C / 2**. |

The four controls were observed together after scrolling at 130% and 200%; **only Refresh was invoked at those scales**. Control presence does not establish the operation of every button. The primary agent inspected the private screenshots. The evidence author independently checked the reports, own-probe layout nodes, action-to-control matching and setting-restoration records; the author did not independently inspect the pixels.

Both logical/task identities and their independent marker/counter values persisted through the scale changes. New Activity generations reported `restored-bundle`, consistent with the retained configuration-recreation events. C remained B / 1 after its explicit marker change, while B remained C / 2. This is state preservation for this diagnostic Activity, not evidence that a terminal session, transfer or stream survives recreation.

## Restoration and temporary-window cleanup

The restoration record confirms original and restored values **`1.0`**, with a matching readback at approximately **07:09:51 UTC**. The final report also retains font scale 1.0 in both window records.

The primary agent then inspected C's native caption and selected its close control. The immediate `font-cleanup-report.json` export still contained revision **97** and marked C alive; it was an older saved snapshot and does **not** prove close success. The later `font-final-report.json`, revision **105**, records C's destruction at event **104** with `changingConfigurations=false;finishing=true`, marks C's Activity no longer alive, and keeps B alive with C / 2 state. C's retained geometry, configuration and focus fields are historical after closure.

## Independent review

A separate reviewer independently matched all 66 private hashes, all nine report projections and all 30 selected events, including exact setting restoration and action-to-control matching. The primary agent read the report and rechecked all hashes and key report fields against the retained records. No reviewer inferred physical input, accessibility or operation of uninvoked controls from layout presence.

## Limits

This bounded pass covers scrolling to diagnostic controls, actual Refresh invocation, independent state preservation and exact setting restoration on this source/APK/device combination. All pointer/touch actions were injected. It does not qualify physical keyboard/trackpad/touchscreen behavior, screen readers, keyboard navigation, IME, every control action, other fonts/locales/window sizes/displays, long-running stability, or the full Console/Desktop design at enlarged text. Production font-scale acceptance and the remaining platform work require their own implementations and evidence.
