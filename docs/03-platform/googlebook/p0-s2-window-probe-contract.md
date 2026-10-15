# P0-S2 native window probe contract

**Owner:** Android/platform developer. **Official references checked:** 2026-10-15, America/Chicago. **Status:** researched contract for the first bounded P0-S2 implementation increment; acceptance below requires separately recorded results. This document does not close P0-S2 or activate CAP-PLATFORM.

Read the [desktop application contract](desktop-application-contract.md), [P0 stage](../../05-delivery/phases/p0-qualification.md), [API tracker](api-tracker.json), and [probe README](../../../qualification/probes/device-baseline/README.md). The [P0-S1 device run](../../../qualification/results/P0-S1/2026-10-15-device-probe.md) established a working diagnostic build and partial lifecycle/inset observations. It did not qualify multiple windows, resize transitions, saved-state restoration, physical input, or application workflows.

## Bounded implementation

Extend the existing native diagnostic APK with a user-triggered **New window** action, a small independent counter and fixed-choice marker in each window, and **Recreate this Activity**. Preserve system captions and placement. Record each window's bounds, attached insets, lifecycle, focus, identity, and state transitions in the existing bounded private report. These are functioning diagnostic controls; there is no simulated SSH, X11, streaming, USB, or file-transfer success.

The current probe uses minSdk 35 and the separately pinned [toolchain](probe-toolchain.md). This installation boundary covers every platform API used below. It does not select the product minimum OS or prove that every desktop affordance exists on every API-35 device. No new AndroidX dependency is required for this increment.

## Exact platform contracts

| Symbols / declaration | Availability and meaning | Probe choice and fallback |
| --- | --- | --- |
| `Intent.FLAG_ACTIVITY_NEW_DOCUMENT`, `Intent.FLAG_ACTIVITY_MULTIPLE_TASK` | API 21 / API 1; together request a new document task rather than matching an existing task | Explicit self-targeting Intent from the foreground control; observe actual task/window creation. Surface launch failures. [Intent reference](https://developer.android.com/reference/android/content/Intent) |
| `android:launchMode`, `android:documentLaunchMode` | Keep `standard` and default `none`; `never` overrides document flags, while `always` changes all launches | Keep normal launcher behavior and apply the flags only to the deliberate action. [Activity manifest](https://developer.android.com/guide/topics/manifest/activity-element) |
| `WindowManager.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` | API 35; activity- or application-level property; application value is inherited unless an activity overrides it | Add the property to the probe Activity. It advertises SystemUI affordances and does not define task launch semantics. In-app New window remains available if the affordance is absent. [WindowManager reference](https://developer.android.com/reference/android/view/WindowManager) |
| `Activity.getTaskId()`, `isTaskRoot()` | API 1; task ID is opaque and constant for the Activity lifetime | Record observed task ID and root state alongside app-generated identity; never treat the task ID as a permanent document identifier. [Activity reference](https://developer.android.com/reference/android/app/Activity) |
| `Activity.recreate()`, `onSaveInstanceState(Bundle)`, `onCreate(Bundle)` | API 11 / API 1 / API 1; recreation destroys the current Activity and creates another instance; saved instance state is conditional | Save only small per-window values, call superclass methods, and restore from that Activity's own Bundle. Missing or invalid state produces a reported fresh state. [Activity reference](https://developer.android.com/reference/android/app/Activity) |
| `onWindowFocusChanged(boolean)`, `onTopResumedActivityChanged(boolean)` | API 1 / API 29; focus and top-resumed are distinct observations | Record both independently with ordinary lifecycle callbacks. Do not infer one from the other. [Activity reference](https://developer.android.com/reference/android/app/Activity) |
| `WindowManager.getCurrentWindowMetrics()`, `WindowMetrics.getBounds()`, `getDensity()` | API 30 / API 30 / API 34 | Record actual current bounds and density after layout changes; preserve source labels. [WindowMetrics reference](https://developer.android.com/reference/android/view/WindowMetrics) |
| `WindowInsets.Type.captionBar()`, `WindowInsets.getInsets(int)`, `getBoundingRects(int)`, `getFrame()` | API 30 / API 30 / API 35 / API 35 | Keep attached-decor and window-metrics inset observations distinct. Missing attached insets remain unknown. Bounding rectangles use the inset frame, not global display coordinates. [WindowInsets reference](https://developer.android.com/reference/android/view/WindowInsets), [inset types](https://developer.android.com/reference/android/view/WindowInsets.Type) |

Do not set `FLAG_ACTIVITY_LAUNCH_ADJACENT` here: its API-24 contract concerns split-screen, requires `FLAG_ACTIVITY_NEW_TASK`, and does not guarantee desktop placement. Do not prescribe window bounds or add custom caption controls. The manifest's `maxRecents` limits retained document tasks in Recents; it is not a live-Activity or memory quota. These boundaries follow the [Intent reference](https://developer.android.com/reference/android/content/Intent) and [Recents guide](https://developer.android.com/guide/components/activities/recents).

The activity-level declaration is:

```xml
<property
    android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI"
    android:value="true" />
```

## State, lifetime, and evidence ownership

The following are **Cloudy diagnostic design rules**, not additional Android guarantees:

1. Generate a logical window UUID for a fresh window. Preserve it only through validated saved state belonging to that Activity. Generate a new Activity-generation identity on every creation. Record the current process-run identity separately.
2. Keep marker and counter state local to each Activity. A newly launched window must not copy the launching window's Bundle, counter, or logical UUID. Validate all restored values; use fixed markers rather than storing user-entered text.
3. Attribute every window event and snapshot to logical window, actual task ID, and generation. The recorder must not let an old generation's delayed event or layout update overwrite or close its replacement. An active logical-ID collision across distinct tasks must be rejected and reported rather than merged.
4. Bound the diagnostic registry explicitly. An eight-record policy is reasonable for this two-window experiment: retain live records, evict only eligible closed history with an omission count, and report capacity rejection. A disabled New window control is a local convenience, not a guarantee that SystemUI cannot launch another Activity. An untracked instance must remain visibly untracked rather than borrowing another window's evidence.
5. Backgrounding, focus loss, and `onStop` do not by themselves close a logical window. Preserve replacement attribution through configuration changes. Treat destruction, finishing, and changing-configuration observations separately; process termination may leave no final callback.
6. Preserve the existing event/file bounds, private storage, save-error reporting, and readback-confirmed saved revision. Report omitted events and untracked windows. Coalesce window snapshots and ignore identical observations so rendering a report cannot create an endless layout/save loop. Refreshing global inventory must not reset per-window state.

For target API 28 and later, `onSaveInstanceState` occurs after `onStop` when it is called; finishing need not save state. Do not erase state on stop. [Activity reference](https://developer.android.com/reference/android/app/Activity)

`recreate()` is an Activity experiment. It does not terminate the process. Configuration recreation and system-initiated process-death recovery require different evidence; process memory alone cannot restore the latter. Keep saved state small. User dismissal or force-stop is not a substitute for testing system-initiated restoration. [Saving UI states](https://developer.android.com/topic/libraries/architecture/saving-states)

## Acceptance for this increment

These local case labels refine Q-P01/Q-P02; they are not additional product capability verdicts. Record pass, fail, blocked, or not run for every case under `qualification/results/P0-S2/`, with the APK hash, source revision, exact device/build, and action provenance.

| Local case | Action and required evidence |
| --- | --- |
| W01 — Separate instances | Launch A normally, then B through New window. Observe two native tasks and distinct logical IDs; retain the actual task IDs and screenshots/observations of both windows. Change A's marker/counter and confirm B is unchanged, then reverse the check. A successful `startActivity` call alone is insufficient. |
| W02 — Activity recreation | Give A and B different state. Recreate A using its explicit control. Confirm A's logical ID and state survive, its generation changes, and B's state/identity remain unchanged. The same-process outcome is labeled Activity recreation, with saved/restored observations. No process-death claim follows. |
| W03 — Resize and captions | Resize each window, including a narrow tiled and short desktop window. Capture actual bounds/density and attached caption insets before/after; confirm controls remain reachable and outside native caption controls. Record whether Android recreated the Activity. Later text-scale checks at 100–200% need their own observations; absence of a change does not count as coverage. |
| W04 — Focus transitions | Alternate A/B focus and background/return the app. Preserve focus, top-resumed, and lifecycle events under their correct window/generation. Do not require a universal callback ordering or treat multiple resumed activities as a failure. Identify injected automation separately from physical keyboard/trackpad/mouse actions. |
| W05 — Close isolation | Close A with the native window control and confirm B remains usable with unchanged state. Record the observed finishing/destruction evidence; do not infer closure merely from a stopped state. Any missing callback remains missing evidence. |
| W06 — Bounded and quiet | After updates settle, verify no continuing self-generated revision/save churn without input or framework changes. Exercise registry capacity and stale-generation handling in deterministic tests, with visible rejection and omission counts. Verify save failures cannot display a false current saved revision. |

Meaningful JVM tests should exercise restoration validation, state isolation, capacity/closed-record policy, and old-generation rejection. A pure model test cannot prove Android delivered a Bundle, created two desktop windows, or laid out controls safely. Kotlin compilation, Android Lint, and unit tests are build checks; record actual Googlebook observations separately.

No key text, clipboard data, input-device identifiers, accounts, host inventories, or unrelated applications belong in the report. Probe-generated logical/generation IDs and task IDs are diagnostic correlation data; disclose them only within the bounded qualification evidence. Redact unrelated desktop content from screenshots.

## Remaining P0-S2 work and open observations

The subsequent [window run](../../../qualification/results/P0-S2/2026-10-15-window-probe.md) records the bounded W01–W06 observations. A separate [process-restoration run](../../../qualification/results/P0-S2/2026-10-15-process-restoration.md) verified saved-state restoration after an injected background-process kill; it does not establish natural low-memory killing or remote-operation durability. Per-operation foreground-service/timeout strategies, precise engagement APIs, and the final CAP-PLATFORM decision remain pending. The separate [Linux VM investigation](p0-s2-linux-vm-boundary.md) records that no public attach/control contract was established; it qualifies no VM backend. Physical keyboard, IME, trackpad, mouse, external-display, and text-scale coverage require distinct recorded actions. Two plain probe tasks do not qualify X11 window ownership, terminal IME, streaming input, or independent production app lifetimes.

The optional SystemUI multi-instance affordance remains unexercised. Caption behavior through resize/maximize and correlated callback/state observations are recorded in the window run, with source differences preserved. Missing hardware or an unexercised path stays unknown; this contract does not extend the scope of either result.

## Official source provenance

Publisher for every source below: **Google, Android Developers**. Checked date: **2026-10-15**. Existing source IDs resolve in the [source register](../../07-reference/source-register.md). The additional contracts are registered as G55–G58.

| Source | Register relationship / visible update date |
| --- | --- |
| [Desktop windowing guide](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing) | Existing G03; source updated 2026-08-04 |
| [Activity reference](https://developer.android.com/reference/android/app/Activity) | Existing G24; individual symbol availability inspected; page update date not recorded |
| [WindowInsets reference](https://developer.android.com/reference/android/view/WindowInsets) and [types](https://developer.android.com/reference/android/view/WindowInsets.Type) | Existing G27/G44; page update dates not recorded |
| [WindowManager reference](https://developer.android.com/reference/android/view/WindowManager) and [WindowMetrics](https://developer.android.com/reference/android/view/WindowMetrics) | Existing G28/G43; page update dates not recorded |
| [Intent reference](https://developer.android.com/reference/android/content/Intent) | G55; exact flag contracts inspected; page update date not recorded |
| [Activity manifest element](https://developer.android.com/guide/topics/manifest/activity-element) | G56; launch/document modes and Recents boundary inspected; page update date not recorded |
| [Recents guide](https://developer.android.com/guide/components/activities/recents) | G57; source updated 2026-04-22 |
| [Saving UI states](https://developer.android.com/topic/libraries/architecture/saving-states) | G58; recreation/process-death distinction inspected; page update date not recorded |
