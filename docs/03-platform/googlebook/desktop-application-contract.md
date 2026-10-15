# Googlebook desktop application contract

**Checked:** 2026-10-14. **Product authority:** [Rule 0](../../00-project/platform-rule-zero.md). **Implementation state:** proposed; all device/API experiments remain untested.

The two clients are Android packages for the Googlebook desktop ecosystem. Use Android Studio/SDK, Compose for native application chrome, and NDK components for qualified protocol/rendering engines. Build and install each package independently. Select minSdk, targetSdk, ABI, SDK extensions, and AndroidX versions after P0 inspection; the existence of one API does not set the whole application's minimum version.

## Official guidance mapped to work

| Desktop contract | Official reference / symbols | Cloudy implementation and gate |
| --- | --- | --- |
| Reconfigure a desktop window | G01/G03/G08; window size classes and actual usable bounds | Resizable panes, no orientation lock, no device-size heuristic; P0-S2, P1-S2, Q-P02 |
| Respect native captions | G23/G27; caption appearance and occupied rectangles, API 35 where used | Retain system controls and guard optional caption integration; API02/API03 |
| Independent tasks/windows | G25/G28; multi-instance affordances plus actual Activity/task lifecycle | Console windows and X11 top-level windows have explicit view/session identity; P2-S3/S6, API04/API26 |
| Fullscreen with recovery | G24; `Activity.requestFullscreenMode`, API 34 | User action, callback/rejection handling, visible release/return route; API01 |
| Precision input | G09/G10; `KeyEvent`, keyboard shortcut helper, pointer capture API 26 | Native focus/IME, menus/hover, capture release, physical trackpad tests; API06/API07 |
| Desktop file operations | G11/G12/G13; `ClipData`, URI grants, picker, `DocumentsProvider` | Session-linked SFTP and visible directional drops; no assumption a remote path is a local URI; API10/API11/API20 |
| USB presence and permission | G14; `UsbManager`, attach/detach actions, permission check/request | Qualified provider owns device access; Cloudy reconciles observations and enrollment; API12/API28 |
| Native media | G20/G29/G30; `MediaCodec`, `KEY_LOW_LATENCY`, `FEATURE_LowLatency` | Native surface and capability-checked optional low latency mode; measure rather than infer; API17/API27 |
| App lifecycle | G15/G16; foreground-service types/timeouts | Explicit per-operation lifetimes and restoration; no permanently running shared daemon; API13/API14 |
| Android distribution | G31/G32; APK install/package manager and AAB publishing | Separate package IDs/signatures/data; install/update/uninstall tests; API32, P1-S1, P2-S5/P3-S5, P6-S3 |

Source IDs resolve in the [register](../../07-reference/source-register.md); exact availability, fallback, and qualification work are in the [API tracker](api-tracker.json). Some entries are Android-wide APIs, others are upstream protocol interfaces. The tracker labels their provenance; it does not call FreeRDP or X11 a Googlebook OS API.

## Applicable official quality checks

Trace Cloudy's Q-P02, Q-P03, Q-A01, Q-C09/C10 and desktop cases to G02's `T-UI_Config`, `T-Desktop_Menus`, `T-Scrollbar_Display`, `T-Keyboard_Navigation`, `T-Keyboard_Parity`, `T-Input_Combinations`, `T-Triple_Click`, `T-Multi-Instance`, `T-Request_Fullscreen_Mode`, `T-Drag_Drop_Support`, `T-Drag_Drop_Batch`, `T-File_Management_Basics`, `T-File_Picker`, and `T-Custom_Cursors` as relevant.

Maintain an applicability ledger in each release run: implemented feature, official test, Cloudy case, device/build, result/evidence, and justified not-applicable entry. Printing, stylus-specific tools, PiP, and phone continuity are not automatic product commitments merely because a broad guideline mentions them. Do not claim a Play badge or full guideline certification from a partial checklist.

## Desktop validation matrix

Test keyboard-only navigation; keyboard plus trackpad and external mouse separately; optional touchscreen access on Googlebook; right-click and hover; modifier/range selection; native IME composition; 100–200% text scale; narrow tiled and short windows; maximize/fullscreen and rejected requests; multiple app tasks; X11 dialogs; external-display move and scaling change; focus loss; suspend/resume; permission denial; process recreation.

The desktop emulator is an early layout/input tool. Real Googlebook hardware is required for input latency, precision-trackpad behavior, USB hotplug, thermal sustained decode, caption bounds, and sleep/background behavior. Phone/tablet testing is not a substitute or a release gate.

## Packaging and discovery decisions

Use provisional independent IDs already listed in the app READMEs. Do not require telephony or touchscreen-only navigation in the manifests. Declare hardware requirements according to actual feature use; an optional USB feature must not accidentally exclude SSH-only users. Per-backend capabilities are discoverable at runtime.

Play versus signed direct distribution, device-catalog eligibility, dependency obligations, and signing-key recovery start in P1 and must be resolved for each independent app release at P2-S5/P3-S5, with a full-suite refresh at P6-S3. Verify supported Googlebook targeting with the actual channel; broad Android compatibility does not expand the published support matrix. Store operational data under each app's sandbox and grant only selected file access through Android mechanisms.
