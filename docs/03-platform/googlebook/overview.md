# Googlebook development reference

**Checked:** 2026-10-14, America/Chicago. This is a curated entry point, not a mirror of Google's manuals. The [source registry](../../07-reference/source-register.md) tracks individual references and review status.

## Platform model

Cloudy's product policy is [Rule 0](../../00-project/platform-rule-zero.md): desktop/laptop Googlebook applications using Android's package manager, app sandbox, permissions and lifecycle. Phone/tablet optimization and phone continuity are outside scope. Android install compatibility does not qualify those form factors. The [desktop application contract](desktop-application-contract.md) maps official guidance to APIs, stages and release evidence.

Google describes Googlebook as using Android technology with ChromeOS desktop foundations. Develop Android apps with adaptive desktop layouts, keyboard/pointer support, and independent resizable windows. Do not assume Chrome extensions, Crostini control, privileged filesystem access, or a ChromeOS Linux API. [Official Googlebook overview](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview).

The Linux Terminal is a preview environment separate from third-party Android app APIs. Its announced roadmap includes USB passthrough, GPU/NPU acceleration, and stronger VM persistence; Cloudy must not depend on those as delivered capabilities. [Google's October 12 announcement](https://developer.android.com/blog/posts/googlebook-linux-terminal-preview-vm).

## Reference routes

| Need | Official starting point | Cloudy artifact |
| --- | --- | --- |
| Desktop requirements | [Desktop quality and tests](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop) | [Validation cases](../../06-validation/test-cases.md) |
| Resize / caption / instances | [Desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing) | [API catalog](api-catalog.md) |
| Layout at current window size | [Window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) | [Cloudy layout rules](../../04-design/layout.md) |
| Native shell styling | [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | [Implementation map](../../04-design/implementation-map.md) |
| Installation and debugging | [Googlebook ADB guide](https://developer.android.com/develop/adaptive-apps/guides/googlebook/adb-debugging) | [Setup checklist](development-setup.md) |
| Android packages | [ADB/package tools](https://developer.android.com/tools/adb), [AAB publishing](https://developer.android.com/guide/app-bundle) | Independent signed APKs; publishing channel decided explicitly |
| Phone continuity | [Continue On integration](https://developer.android.com/develop/adaptive-apps/guides/googlebook/cross-device-continuity) | Reference only, outside desktop-only release scope |
| Keyboard/launcher restrictions | [Compatibility notice](https://developer.android.com/develop/adaptive-apps/guides/googlebook/keyboard-apps-and-launcher-compatibility) | System IME/launcher retained |
| Files / peripherals / lifecycle | Android guides linked in [API catalog](api-catalog.md) | P0/P1 qualification |

## Evidence levels

**Documented** means an official source states a capability or signature. **Compile-verified** means the chosen SDK/library resolves it. **Device-qualified** means a recorded test establishes the intended behavior. These are separate facts; none substitutes for the next.

The initial [API tracker](api-tracker.json) names exact surfaces, known API levels where verified, unknown library pins, proposed use, fallback, owner, and stage. All entries begin with `qualification: untested`. Values marked null are unresolved, not “supported on all versions.”

## Development policy

Choose compile/target/min SDK, AGP, Kotlin, Compose BOM, WindowManager, WebView baseline, NDK, and Go versions during P0/P1. Record exact resolved versions and their official release notes. Do not copy a sample requiring experimental annotations into production without an explicit decision and fallback.

Google's overview currently links experimental `mediaQuery`/Styles examples. These are research leads; current-window layout and stable platform primitives are the baseline. The library's exact artifact/version and experimental status must be pinned before adoption.

## Near-term priorities

First qualify desktop physical input/IME and windows, app lifecycle, X11 app-window broker, stream/RDP/VNC engine/decoder behavior, USB hotplug/permission, file grants/drop and latency baselines. Build the broader product around those results. [Open questions](open-questions.md) tracks unresolved mechanisms; upstream protocol engines are not Googlebook OS APIs.
