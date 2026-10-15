# Official source register

Research snapshot: **2026-10-14, America/Chicago**. Canonical data: [sources.json](sources.json). Entries are official publisher/vendor sources. No forum or secondary article is used as a platform authority.

`reviewed` means the relevant claim was inspected. `located` means the official entry point was found and opened, but exact signatures/version details still need implementation review. `javascript-required` means this tool could not inspect the page body; do not treat it as verified content. A checked date is not an API or hardware certification.

Source update dates are recorded only where the inspected page provided one. Null means not recorded, not that a page is current forever. Read [watchlist.md](watchlist.md) for refresh/change handling.

| ID | Official reference | Review status | Development use |
| --- | --- | --- | --- |
| G01 | [Googlebook adaptive development overview](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview) | reviewed | Android and desktop product model; layout/input/tooling entry points. |
| G02 | [Desktop app quality and test IDs](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop) | reviewed | Desktop acceptance vocabulary and official test IDs. |
| G03 | [Support desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing) | reviewed | Caption insets, instance/task behavior, drag/drop, and engagement example. |
| G04 | [ADB debugging on Googlebook](https://developer.android.com/develop/adaptive-apps/guides/googlebook/adb-debugging) | reviewed | Model-specific debugging transports and HP left USB port. |
| G05 | [Keyboard apps and launcher compatibility](https://developer.android.com/develop/adaptive-apps/guides/googlebook/keyboard-apps-and-launcher-compatibility) | reviewed | Current Play restriction for custom IMEs and launchers. |
| G06 | [Linux Terminal Preview announcement](https://developer.android.com/blog/posts/googlebook-linux-terminal-preview-vm) | reviewed | Preview/roadmap boundary for VM integration and peripherals. |
| G07 | [Cross-device continuity on Googlebook](https://developer.android.com/develop/adaptive-apps/guides/googlebook/cross-device-continuity) | reviewed | API 37 phone continuity; receiving context and safe identifiers. |
| G08 | [Use window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) | located | Pin the adaptive library and inspect current window classification behavior. |
| G09 | [Pointer capture guide](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement) | reviewed | Focused-view capture/release from API 26; physical trackpad still requires qualification. |
| G10 | [Handle keyboard actions](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/commands) | located | Exact keyboard callback/shortcut behavior to inspect during input spike. |
| G11 | [Enable drag and drop](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop) | located | ClipData and grant-aware attachment integration entry point. |
| G12 | [Create a document provider](https://developer.android.com/guide/topics/providers/create-document-provider) | located | Remote document provider operations and supported flags. |
| G13 | [Storage Access Framework files](https://developer.android.com/training/data-storage/shared/documents-files) | located | User-chosen document URIs and persisted access; refresh exact methods at P2. |
| G14 | [USB host overview](https://developer.android.com/develop/connectivity/usb/host) | located | Android device permission and connection API starting point. |
| G15 | [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout) | reviewed | Target-dependent six-hour rules and API 35 timeout callback. |
| G16 | [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | located | Operation-specific type/prerequisite decision; no type selected for SSH yet. |
| G17 | [WebView native bridge security](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) | located | Bridge attack boundary; local assets and validated origin/message design. |
| G18 | [Android Keystore system](https://developer.android.com/privacy-and-security/keystore) | located | Protected key material; SSH engine algorithm integration remains untested. |
| G19 | [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | located | Native theme/component implementation route; pin Compose at P1. |
| G20 | [MediaCodec API reference](https://developer.android.com/reference/android/media/MediaCodec) | located | Decoder API entry point; enumerate and measure actual device support. |
| G21 | [Read network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) | located | NetworkCallback and capabilities; not tailnet path telemetry. |
| G22 | [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) | located | Permission handling and denied-state fallback. |
| G23 | [WindowInsetsController reference](https://developer.android.com/reference/android/view/WindowInsetsController) | reviewed | Caption appearance flags introduced at API 35. |
| G24 | [Activity API reference](https://developer.android.com/reference/android/app/Activity) | reviewed | requestFullscreenMode signature, API 34, and callback/rejection constraints. |
| G25 | [View API reference](https://developer.android.com/reference/android/view/View) | reviewed | API 35 cross-instance drag flags; other View methods need per-method review. |
| G26 | [HandoffActivityData reference](https://developer.android.com/reference/android/app/HandoffActivityData) | reviewed | API 37 handoff data, separate from Console/Desktop IPC. |
| G27 | [WindowInsets reference](https://developer.android.com/reference/android/view/WindowInsets) | reviewed | API 35 bounding rectangles for obscured system UI areas. |
| G28 | [WindowManager reference](https://developer.android.com/reference/android/view/WindowManager) | reviewed | API 35 multi-instance System UI property does not implement task behavior. |
| D01 | [Carbon color foundations](https://www.carbondesignsystem.com/building-blocks/foundations/color/overview) | reviewed | Semantic roles and neutral layering as design references. |
| D02 | [Carbon typography foundations](https://www.carbondesignsystem.com/building-blocks/foundations/typography/overview) | reviewed | Plex and productive type hierarchy; Cloudy sizes are original proposals. |
| D03 | [Carbon 2x grid](https://www.carbondesignsystem.com/building-blocks/foundations/2x-grid/overview) | reviewed | Consistent layout rhythm and tokenized sizing. |
| D04 | [Carbon data table guidelines](https://www.carbondesignsystem.com/building-blocks/core/components/data-table/guidelines) | reviewed | Dense tables, row hierarchy, selection, and action placement. |
| D05 | [Material 3 color roles](https://m3.material.io/styles/color/the-color-system) | reviewed | Paired semantic roles and tonal surfaces; exact Cloudy hex values are original. |
| D06 | [Material 3 applying typography](https://m3.material.io/styles/typography/applying-type) | reviewed | Role-based type hierarchy; not a mandate for one font or every size. |
| D07 | [Material 3 shape](https://m3.material.io/styles/shape/shape-morph) | javascript-required | Official entry point located; readable text unavailable in this research tool. Cloudy radii are proposed independently. |
| D08 | [Material 3 easing and duration](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs) | javascript-required | Official entry point located; exact tokens not claimed. Cloudy motion values are proposals. |
| D09 | [WCAG 2.2 recommendation](https://www.w3.org/TR/WCAG22/) | reviewed | Contrast, keyboard access, target size, focus and reflow reference; full conformance not claimed. |
| R01 | [Termius product overview](https://termius.com/) | reviewed | Host/session organization and product vocabulary; not feature parity or platform promises. |
| R02 | [Termius documentation](https://docs.termius.com/) | reviewed | Workspaces, snippets, keychain and split-view references. |
| R03 | [MobaXterm official manual](https://mobaxterm.mobatek.net/documentation.html) | reviewed | Terminal/SFTP/tunnel proximity; Windows/X11 assumptions not ported. |
| R04 | [Sunshine compatibility](https://docs.lizardbyte.dev/projects/sunshine/latest/) | reviewed | Moving backend/capture/encoder compatibility reference, not hardware proof. |
| R05 | [Sunshine getting started source](https://github.com/LizardByte/Sunshine/blob/master/docs/getting_started.md) | reviewed | Moving setup guidance; pin actual release. Original macOS warning is not current wording. |
| R06 | [Moonlight Android source and license](https://github.com/moonlight-stream/moonlight-android) | reviewed | Android client and GPL-3.0 licensing decision before integration. |
| R07 | [VirtualHere Android server](https://www.virtualhere.com/android) | reviewed | Android USB-provider candidate; probe/API/licensing still need qualification. |
| R08 | [Tailscale connection types](https://tailscale.com/docs/reference/connection-types) | located | Direct/relay diagnostics reference; observed path must be measured. |
| R09 | [xterm.js terminal sequences](https://xtermjs.org/docs/api/vtfeatures/) | located | Pinned renderer sequence support reference. |
| R10 | [xterm.js embedding security](https://xtermjs.org/docs/guides/security/) | located | Terminal escape/embedding boundary and secure rendering reference. |
| R11 | [Windows notification listener](https://learn.microsoft.com/en-us/windows/apps/develop/notifications/app-notifications/notification-listener) | reviewed | Capability, consent, source/text/dismissal, revoked access behavior. |
| R12 | [UNUserNotificationCenter](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter) | javascript-required | Official entry point; tool could not read dynamic page or linked Markdown. Universal third-party capture remains unestablished. |
| R13 | [Desktop Notifications specification](https://specifications.freedesktop.org/notification/latest-single/) | located | Client/server protocol; do not infer a portable observer API. |
| R14 | [Google Drive desktop requirements](https://support.google.com/drive/answer/2375082) | located | Host-client support authority, separate from Android provider behavior. |
| R15 | [rclone mount and VFS](https://rclone.org/commands/rclone_mount/) | located | Host cache/writeback/conflict reference; not selected as an embedded client. |
| R16 | [Tailscale with other VPNs](https://tailscale.com/docs/reference/faq/other-vpns) | located | Android VPN coexistence constraints; Cloudy adds no competing VPN. |
