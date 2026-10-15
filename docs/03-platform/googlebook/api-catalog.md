# Official API catalog for development

**Snapshot:** 2026-10-14. Machine-readable records: [api-tracker.json](api-tracker.json). Source IDs resolve in the [source register](../../07-reference/source-register.md). “API N” is a verified platform introduction level only where stated. Library availability and Cloudy's min/target SDK remain P0/P1 decisions.

## Windows and input

| ID | Surface / official reference | Availability and use | Fallback / gate |
| --- | --- | --- | --- |
| API01 | [`Activity.requestFullscreenMode(int, OutcomeReceiver)`](https://developer.android.com/reference/android/app/Activity#requestFullscreenMode(int,android.os.OutcomeReceiver)) | API 34; user-requested transition, handle rejection and relayout | Normal resizable window; P1-S2 |
| API02 | [`APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND`, `APPEARANCE_LIGHT_CAPTION_BARS`](https://developer.android.com/reference/android/view/WindowInsetsController) | API 35 flags; theme native caption area | Preserve ordinary caption; P1-S2 |
| API03 | [`WindowInsets.getBoundingRects(int)`](https://developer.android.com/reference/android/view/WindowInsets) | API 35; reserve occupied system-control rectangles | Standard insets and separate app toolbar; P1-S2 |
| API04 | [`PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI`](https://developer.android.com/reference/android/view/WindowManager#PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI) | API 35; requests system affordance, does not itself implement instance behavior | App's explicit New window action; P2-S3 |
| API05 | [`View.DRAG_FLAG_GLOBAL_SAME_APPLICATION`, `DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG`](https://developer.android.com/reference/android/view/View) | API 35; cross-instance drag/new task hints | Explicit move/open-in-window menu; P2-S3 |
| API06 | [`View.requestPointerCapture`, `onCapturedPointerEvent`, `releasePointerCapture`](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement) | API 26+; focused native view owns relative events | Absolute mode / qualified mouse; P0-S4 |
| API07 | [`KeyEvent`, keyboard shortcuts handling](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/commands) | Native keyboard policy; per-method/version verification at implementation | Discoverable command/menu access; P0-S3 |
| API08 | [Compose adaptive window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) | Jetpack dependency to pin; use available window space | Stable measured constraints; P1-S2 |
| API09 | [Desktop `windowEngagementInfo` / `PRECISE_POINTER` example](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing) | Guide example, exact WindowManager version/maturity not pinned here | User density setting and current input capabilities; P0-S2 |

Use the API reference's spelling `requestFullscreenMode`; a guide link may render `requestFullScreenMode`. Verify callback threading and error handling against the selected OS, and marshal UI changes to the UI thread. Do not hard-code caption heights or cover system close/maximize targets.

## Files, devices, storage, and background behavior

| ID | Surface / official reference | Proposed use and constraint | Stage |
| --- | --- | --- | --- |
| API10 | [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): `ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, persistable URI grants | User-selected imports/exports; a URI is not a host filesystem path; query supported operations | P2-S4 / P3-S3 |
| API11 | [`DocumentsProvider`](https://developer.android.com/guide/topics/providers/create-document-provider): `queryRoots`, `queryDocument`, `queryChildDocuments`, `openDocument` | Advertise only supported flags; explicit cache/version/conflict semantics | P5-S3 |
| API12 | [`UsbManager`, `UsbDevice`, `UsbDeviceConnection`](https://developer.android.com/develop/connectivity/usb/host) | Android permission/discovery/access; not a promise of general USB virtualization | P0-S5 |
| API13 | [`Service.onTimeout(int,int)` and foreground-service duration rules](https://developer.android.com/develop/background-work/services/fgs/timeout) | API 35 callback; relevant dataSync/mediaProcessing background limits need operation-specific handling | P0-S2 / P1-S3 |
| API14 | [Foreground-service types and prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types) | Determine valid strategy for SSH, transfers, stream, and connected devices independently | P1-S3 |
| API15 | [Android Keystore](https://developer.android.com/privacy-and-security/keystore): `AndroidKeyStore`, `KeyGenParameterSpec` | Per-app credential protection; exact algorithm/security level tested | P1-S3 |
| API16 | [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission): `POST_NOTIFICATIONS` | Android 13+ permission behavior; keep activity view when denied | P4-S2 |
| API17 | [`MediaCodec`, codec capabilities](https://developer.android.com/reference/android/media/MediaCodec) | Enumerate and actually decode chosen stream; no hardware capability inference by SKU | P0-S4 / P5-S2 |
| API18 | [`ConnectivityManager.NetworkCallback`, `NetworkCapabilities`](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) | Observe network change/metered hints; not proof of tailnet direct/relay path | P0-S6 / P5-S4 |
| API19 | [WebView native bridge boundary](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) | Local terminal assets, validated narrow messages, no untrusted navigation | P0-S3 / P1-S3 |
| API20 | [Android drag/drop](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop): `ClipData`, URI grant handling | File attachment imports with exact permissions; never interpret URI as shell path | P2-S4 |

## Optional Googlebook-specific continuity and unresolved interfaces

| ID | Surface | Status and Cloudy decision |
| --- | --- | --- |
| API21 | [`HandoffActivityData`](https://developer.android.com/reference/android/app/HandoffActivityData), `Activity.setHandoffEnabled`, `onHandoffActivityDataRequested` | Continue On is documented for Android 17 / API 37+. Optional phone continuity; identifiers only. Separate from app-to-app IPC. [Guide](https://developer.android.com/develop/adaptive-apps/guides/googlebook/cross-device-continuity) |
| API22 | Public Linux VM attach/start/control API | Not established by reviewed documentation; no integration contract is invented. Remote SSH baseline |
| API23 | VM USB passthrough, GPU/NPU acceleration, independent persistence | Roadmap in [preview announcement](https://developer.android.com/blog/posts/googlebook-linux-terminal-preview-vm); excluded from v1 dependency graph |
| API24 | Custom system IME/launcher | [Google Play compatibility restriction](https://developer.android.com/develop/adaptive-apps/guides/googlebook/keyboard-apps-and-launcher-compatibility); work with native system facilities |

## How to keep the catalog useful

For an implementation change: find the tracker entry, inspect the official signature and release notes, fill the exact SDK/library version, compile a minimal probe, record device evidence, and update the linked stage. Use distinct statuses for documented, experimental, roadmap, unavailable, and unknown. No successful HTTP response can promote an API to device-qualified.
