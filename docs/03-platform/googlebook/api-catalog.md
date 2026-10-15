# Official API catalog for development

**Snapshot:** 2026-10-15. Machine-readable records: [api-tracker.json](api-tracker.json). Source IDs resolve in the [source register](../../07-reference/source-register.md). “API N” is a verified platform introduction level only where stated. Library availability and Cloudy's min/target SDK remain P0/P1 decisions.

The target is the [Googlebook desktop application contract](desktop-application-contract.md) under [Rule 0](../../00-project/platform-rule-zero.md). Records include official Android APIs, upstream standards/engines and proposed Cloudy integration boundaries; provenance is explicit. None is device-qualified by this catalog.

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
| API20 | [Android drag/drop](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop): `ClipData`, URI grant handling | Incoming/outgoing file drops, bounded staged export and grants; a remote path is not a content URI | P2-S7 |

## Reference-only continuity and unresolved interfaces

| ID | Surface | Status and Cloudy decision |
| --- | --- | --- |
| API21 | [`HandoffActivityData`](https://developer.android.com/reference/android/app/HandoffActivityData), `Activity.setHandoffEnabled`, `onHandoffActivityDataRequested` | Continue On is documented for Android 17 / API 37+. Reference-only outside current desktop-only release scope; no required phone integration/test. Separate from app-to-app IPC. [Guide](https://developer.android.com/develop/adaptive-apps/guides/googlebook/cross-device-continuity) |
| API22 | Public Linux VM attach/start/control API | Not established by reviewed documentation; no integration contract is invented. Remote SSH baseline |
| API23 | VM USB passthrough, GPU/NPU acceleration, independent persistence | Roadmap in [preview announcement](https://developer.android.com/blog/posts/googlebook-linux-terminal-preview-vm); excluded from v1 dependency graph |
| API24 | Custom system IME/launcher | [Google Play compatibility restriction](https://developer.android.com/develop/adaptive-apps/guides/googlebook/keyboard-apps-and-launcher-compatibility); work with native system facilities |

## Revision 01 desktop integration surfaces

| ID | Provenance and exact surface | Availability / fallback / qualification |
| --- | --- | --- |
| API25 | [RFC 4254](https://www.rfc-editor.org/rfc/rfc4254.html) `x11-req` / `x11`; [OpenSSH](https://man.openbsd.org/ssh_config) forwarding settings | Upstream SSH standard, not Googlebook API; selected engine hook/cookies/trust contexts need P0-S7. Ordinary SSH stays useful if GUI blocked |
| API26 | Android Activity/task lifecycle + X11 `XID`, `WM_TRANSIENT_FOR`, `WM_DELETE_WINDOW` from [ICCCM](https://xorg.freedesktop.org/archive/X11R7.7/doc/xorg-docs/icccm/icccm.html) | Proposed Cloudy window broker; no ready Googlebook X11 API established. P0-S7/P2-S6 must prove separate app/dialog mapping; one full X desktop is partial |
| API27 | [`MediaFormat.KEY_LOW_LATENCY`](https://developer.android.com/reference/android/media/MediaFormat#KEY_LOW_LATENCY), [`CodecCapabilities.FEATURE_LowLatency`](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#FEATURE_LowLatency) | Both API 30, optional decoder feature; guard/check capability and measure on/off in P0-S4. Normal supported decoder is fallback |
| API28 | [Android USB](https://developer.android.com/develop/connectivity/usb/host) `ACTION_USB_DEVICE_ATTACHED`, `ACTION_USB_DEVICE_DETACHED`, `hasPermission`, `requestPermission` | USB host API 12; permission belongs to the real provider app. P3-S6 replug/restart/grant tests; explicit setup/manual attach if needed |
| API29 | [VirtualHere consumer API](https://www.virtualhere.com/client_api): `GET CLIENT STATE`, `USE`, `STOP USING`, `AUTO USE DEVICE`, `AUTO USE DEVICE PORT` | Vendor IPC, not OS/server-control API; qualify installed HELP, state and toggle reconciliation at P3-S6. Preserve unknown/protected ownership |
| API30 | [FreeRDP Android source](https://github.com/FreeRDP/FreeRDP/blob/master/client/Android/Studio/freeRDPCore/src/main/cpp/android_freerdp.c), `freerdp_connect`, `freerdp_disconnect`, `OnExperimentalFeature`, [MS-RDPEMT](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-rdpemt/5aa61c5c-e864-4ec6-bd80-e1df51555a48) | Upstream native client/standard; [3.27.1](https://github.com/FreeRDP/FreeRDP/releases/tag/3.27.1) adds Android RAIL. Native RDP is P3-S7; optional RemoteApp needs separate server/build evidence |
| API31 | [LibVNCClient](https://github.com/LibVNC/libvncserver/blob/master/include/rfb/rfbclient.h) `rfbGetClient`, `rfbInitClient`, `WaitForMessage`, `HandleRFBServerMessage`, `SendKeyEvent`, `SendPointerEvent` | Candidate upstream C API; P1 license/security decision before P3-S8 native VNC. Engine/server/security/encoding matrix required |
| API32 | [ADB package tools](https://developer.android.com/tools/adb): `adb install`, `adb shell pm`; [AAB publishing](https://developer.android.com/guide/app-bundle) | Android package/tooling model; P1-S1 independent signed APKs, P6-S3 channel/targeting verification. No guessed Googlebook-only manifest flag |

## Revision 02 file publication boundary

Revision 02 adds **API33**, the upstream SFTP file-publication boundary: `SSH_FXP_STAT`, `SSH_FXP_OPEN`, `SSH_FXF_CREAT`, `SSH_FXF_EXCL`, `SSH_FXP_RENAME` and `posix-rename@openssh.com`. [OpenSSH's extension](https://raw.githubusercontent.com/openssh/openssh-portable/master/PROTOCOL) has old/new path fields and no expected-version condition. The [historical v3 draft](https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02) is an expired protocol reference, not a newly released Googlebook API. P2-S7 must qualify the actual server/engine, exclusive creation, concurrent writer between check and publication, and ambiguous completion. A metadata check followed by rename cannot itself promise conflict-free replacement; retain staging and expose only supported publication choices.

## How to keep the catalog useful

For an implementation change: find the tracker entry, inspect the official signature and release notes, fill the exact SDK/library version, compile a minimal probe, record device evidence, and update the linked stage. Use distinct statuses for documented, experimental, roadmap, unavailable, and unknown. No successful HTTP response can promote an API to device-qualified.

## P0-S1 compiled and observed API subset

The [real diagnostic run](../../../qualification/results/P0-S1/2026-10-15-device-probe.md) adds partial observations without promoting the full experiments above. API03 caption bounds, API17 enumeration, API27 feature flags and API32 diagnostic installation have specific evidence notes in the tracker. Native decoding, product package distribution and complete window/input behavior remain unqualified.

| ID | Official surface | Availability, fallback and next experiment |
| --- | --- | --- |
| API34 | [InputDevice](https://developer.android.com/reference/android/view/InputDevice) and [InputManager](https://developer.android.com/reference/android/hardware/input/InputManager) source enumeration | Selected bundle needs API29 for external flag; API16 InputManager enumeration/query. No names/descriptors/IDs retained; detach is explicit. Physical input still requires tests. |
| API35 | [WindowMetrics](https://developer.android.com/reference/android/view/WindowMetrics), [WindowInsets](https://developer.android.com/reference/android/view/WindowInsets) and attached View insets | Metrics API30, density API34, caption rectangles/frame API35. The probe min35 covers the bundle; preserve native caption and handle unavailable insets. |
| API36 | [Activity lifecycle/focus/top-resumed](https://developer.android.com/reference/android/app/Activity) | Top-resumed API29; older lifecycle/focus callbacks retain their own introduction levels. ADB background/return was observed; recreation/death/operation recovery remain separate. |

The minimum levels of these bundled records reflect their newest listed method, not a claim that every method was introduced together. Reported metadata does not satisfy the full qualification experiment. No fixed caption height or assumed physical-input behavior should be derived from one run.
