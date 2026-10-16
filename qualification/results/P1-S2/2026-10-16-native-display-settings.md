# Native appearance and density settings

**Date:** 2026-10-16. **Outcome:** Console/Desktop **0.1.6/code7** implement independent native System/Light/Dark appearance and Comfortable/Standard pointer/Compact pointer settings. Strict build02, four zero-issue Android Lint reports and **12 actual Googlebook JUnit cases** pass. Six actual Console viewport captures were inspected by the leader. Both products are installed and stopped; P1-S2 remains in progress.

## Behavior and source review

The [settings contract](../../../docs/04-design/native-display-settings.md) describes the real per-app preferences, single process owner and native settings Activity. Field-specific off-main commits preserve the other setting and unknown keys. Missing values default safely; invalid values remain on disk until an explicit change to that field. An uncertain commit is reported honestly: cached Refresh is not proof of persistence. A subsequent explicit successful commit can clear process uncertainty. No automatic mutation replay, global night-mode change, density/font-scale override or Activity recreation is introduced.

Workspace, profiles, trust and vault roots consume the owning app's theme. Shared metrics adjust spacing and nominal control minima; Material interactive behavior and scalable text remain. The vault deletion dialog now explicitly consumes canonical section typography, raised container and20dp radius. These presentation changes preserve authentication, approval, secure-window and lifecycle behavior. This run does not visually inspect the protected vault dialog or exercise its authenticated operations.

Direct Codex GPT6.1-Sol coworkers supplied disjoint implementation, tests and references; a separate reviewer and the leader inspected source. The independent test review identified that `EXACT_BASELINE_CONFIRMED` means effective preference-value restoration, not identical file/key presence. The parent recorded before/after XML presence and values; no production preferences were deleted to recreate absence.

OMP assignment `DISPLAY-COPY-001` completed on actual `zai/glm-5.3-flash`, stop reason `stop`, zero tools,9.216seconds. The packet contained only display resource strings. Parent accepted wording suggestions with edits that retain save uncertainty and field-specific recovery. A local runner SyntaxError occurred before model invocation and is preserved; the corrected runner then completed this one assignment. Packet and disposition: `.cache/omp-workers/2026-10-16-display-settings/copy-01/DISPLAY-COPY-001/`. The original external Word feedback was not supplied to OMP.

## Build evidence

Source base `1084a5e27a76f78d6fcdf5a7e91f9f67f06cbc52` plus this increment. The [manifest](2026-10-16-native-display-settings-build.json) binds implementation/integration/test sources, five APKs, capture hashes and measurements. Frozen artifacts/logs: `.cache/display-settings-01/build-02/`.

Build01 fails only the new workspace instrumentation's nullable `RecentTaskInfo` access. The original test and log remain in `build-01/`; it was never installed. Parent changes collection mapping to `mapNotNull` before selecting the actual task. Build02 passes strict dependency verification and Kotlin compilation in24seconds:313tasks,49executed. Console, Desktop, platform and design-system Lint each report zero issues. Domain38 and design-system4 passing JVM reports are carried forward by up-to-date tasks in build02, not counted as newly executed tests. Sources remain unchanged during both builds. No external dependency, permission, palette or canonical token change occurs.

## Actual Android execution

Target: **HP Googlebook 14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Parent-owned run: **10:24:38.979190Z–10:24:59.508947Z**. Private packet `qualification/results/private/foundation/2026-10-16-display-settings-01/`; runner `.cache/display-settings-01/run_device_01.py`.

| Cases | Count | Actual scope |
| --- | ---: | --- |
| Platform preferences | 4 | Missing default reads do not create a file; real commits preserve the other field and match XML; invalid known values fall back without rewriting; explicit repair preserves unknown keys; main-thread, device-protected and wrong-owner contexts plus unsafe names are rejected. Unique test files are removed. Reopening a wrapper is not process-death evidence. |
| Settings Activity, each app | 1×2 | Actual package/UID and nonexported Activity; every native choice through selectable UI; confirmed owner values and selection; choices survive controlled Activity recreation. |
| Workspace display, each app | 1×2 | Light/Dark × three densities preserve logical window snapshot, task/title/label, Activity generation and native draft/selection. Console captures six viewports; Desktop performs behavior assertions only. |
| Profile CRUD, each app | 1×2 | Genuine production-store create/update/confirmed delete after the presentation/theme migration. |
| SSH trust, each app | 1×2 | Exact identity/state display, cancelled then confirmed revocation and retained tombstone after the presentation/theme migration. |

All12 JUnit cases pass without skips. Four custom preference-restoration statuses, two trust cleanup statuses and capture notifications are evidence events, not extra cases. No prompt, credential entry, remote host or network session is required.

## Rendered evidence and bounds

All six PNGs are actual Compose-root captures at **2073×1231px**, display density1.625 and reported font scale1.0. The application-context configuration reports260dpi,1276×798dp and uiMode17; those configuration fields are not a measurement of the captured window rectangle. Explicit Dark changes content while the system remains Light. Captures omit native caption/system chrome and cover the current local workspace, not the final remote Console layout.

The leader inspected both appearances in all three densities. The sampled viewport shows the heading, seven labeled actions, native field/selection and distinct spacing without visible text clipping or overlapping controls. This is a bounded rendered observation, not an accessibility or full component acceptance claim.

| Density | Nominal minimum | Observed seven button semantics heights | Observed label semantics height |
| --- | ---: | ---: | ---: |
| Comfortable | 48dp | 48dp | 20.31dp |
| Standard pointer | 40dp | 40dp | 20.31dp |
| Compact pointer | 32dp | 36.31dp | 20.31dp |

The report uses **clipped semantics bounds**, not hit-target extents. The compact control grows beyond its nominal minimum; text is not shrunk to force32dp. Material minimum interactive sizing is retained in source, but these measurements do not prove48dp hit targets or absence of hit-target overlap. PNG/JSON hashes are public in the manifest; raw captures remain in the ignored private packet. Each exact test-owned app capture file was copied and hashed before removal, and its owned directory absence was checked.

## Preservation and remaining qualification

Both exact0.1.6 product APKs replace0.1.5 without clearing app data. Profile rows/catalog generations, trust rows/generations and existing vault receipt hashes are unchanged. Both apps originally lacked `cloudy-display-v1.xml`; after explicit test restoration, each retains a new private XML file containing `appearance=SYSTEM` and `density=COMFORTABLE`. This restores effective defaults, not initial file absence. All three instrumentation packages are removed with independent absence checks, and both products are force-stopped. Boot ID/count, global font scale, display density and night-mode setting remain unchanged.

The build/actual tests establish this local increment. They do not qualify disk-write failure, process death, preference cross-app attack resistance, simultaneous multiwindow interactions, system appearance transitions, native caption/IME color, protected dialogs, physical keyboard/pointer, narrow/short window geometry, changed user font scales, accessibility services or long-session visual comfort. The HTML specimen's missing Standard pointer/hover-role work remains separate. Full native shell/component coverage, P1-S2/P1-S3 and all remote integration/release gates remain open.

All seven hosted workflows pass at predecessor `1084a5e27a76f78d6fcdf5a7e91f9f67f06cbc52`. This new source checkpoint requires its own post-push hosted result.

## Repository validation

Parent checks pass: authored Python AST32files, Ruff, generated Android-token freshness, Git whitespace and project validation124JSON/300Markdown/2,312local links/501sources/56API records/82declared contrast checks. Schema validation accepts three schemas/three examples and rejects six invalid cases. An initial local verification command used a nonexistent `check_python.py` name and executed no check; the corrected repository command `tools/check_ast.py` supplies the passing AST result. Kotlin compilation and Android Lint are provided by build02. These checks do not extend the device or accessibility scope.

An independent direct Codex reviewer verified all27 source hashes, five APK hashes, six PNG/report hashes, actual12 JUnit completions, new explicit-default XML and the recorded preservation/measurement claims. The reviewer identified three stale current-version/visual-review assertions in the API/toolchain/testing summaries; the parent corrected them without rewriting historical0.1.5 evidence. No substantive evidence discrepancy remains. The leader performed the six-frame visual inspection separately.
