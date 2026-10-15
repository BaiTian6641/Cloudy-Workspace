# Rule 0 — A desktop suite in Google's Android ecosystem

**Authority:** the user's revised product direction, 2026-10-14. **Status:** adopted for planning. This rule applies to both applications, every phase, and every design review.

Cloudy Workspace targets **Googlebook desktop and laptop use**. Googlebook uses the Android technology stack and Android application packaging, permissions, and lifecycle. Cloudy Console and Cloudy Desktop are independently installed Android applications designed as desktop tools: keyboard and precision pointer first, resizable windows, multiple work contexts, dense information, and integrated file/device workflows.

Android package compatibility can allow a build to install or launch on a phone or tablet. That does not put those form factors in Cloudy's supported product scope. **There is no phone UI workstream or tablet optimization commitment.** Phone/tablet behavior is incidental and unqualified; it must not determine the desktop navigation, density, interaction model, or release schedule. Future support would need a separate scope decision and evidence.

## What this requires

| Area | Cloudy rule | Development consequence |
| --- | --- | --- |
| Platform | Googlebook desktop environment, Android app model | Native Android shell and NDK engines where needed; SDK/ABI versions chosen from actual devices |
| Packaging | Two signed Android app identities and independent app data | APKs for supported direct installation/testing; AAB publishing if Play is selected; no Linux package as the client baseline |
| Input | Physical keyboard, trackpad, and mouse are primary | Discoverable shortcuts, right-click menus, hover, accurate cursors, range selection, predictable focus and capture release |
| Windows | Native desktop tasks, resize, maximize/fullscreen, multiple instances | Preserve system caption controls, task identity, display changes, and per-window context |
| Layout | Functional panes and user-adjustable density | Keep terminal/files/tools visible together when space allows; avoid a handset navigation model stretched across a laptop |
| Narrow windows | Still a desktop requirement | A narrow tiled Googlebook window must keep work reachable; collapsing a pane is not a phone-support promise |
| Android behavior | Respect permissions, IME, URI grants, app sandbox, background limits | Desktop branding does not grant an immortal daemon, arbitrary filesystem access, or privileged OS control |
| Accessibility | Keyboard access, readable type, native semantics, user scaling | Preserve useful touch access on a touchscreen Googlebook; desktop priority does not remove accessibility obligations |
| Ecosystem | Work with Googlebook Files, browser, task/window system, and Android pickers | Use supported intents and document URIs, explicit app handoff, and native window integration |
| Qualification | Actual Googlebook builds and desktop workflows | Emulators assist development; passing on a phone/tablet does not qualify the product |

Google's [Googlebook development overview](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview) describes the Android stack, free-form windows, precision input, and multi-pane workflows. Its [desktop quality guide](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop) supplies the applicable desktop test vocabulary. Those documents support the platform approach; the decision to exclude phone/tablet optimization is **Cloudy product scope**, not a claim that Google requires desktop-only apps.

Android installation and package-manager tools are documented in [ADB](https://developer.android.com/tools/adb#pm). An [Android App Bundle](https://developer.android.com/guide/app-bundle) is a publishing format from which Play generates installable APKs; it is not a directly installed desktop binary.

## Scope and platform boundaries

Googlebook's ChromeOS desktop foundations do not establish Crostini compatibility, Chrome extensions, Linux desktop packaging, or private VM control APIs. Local Linux preview features remain separately tracked. The client baseline runs in the Android app environment; ordinary SSH connects to remote compute.

Do not select a custom launcher or system IME as a shortcut to desktop integration. Check Google's [Googlebook compatibility restrictions](https://developer.android.com/develop/adaptive-apps/guides/googlebook/keyboard-apps-and-launcher-compatibility). Cloudy's native input integration uses the installed system IME.

Phone Continue On, handset onboarding, portrait handset screens, touch-only navigation, and tablet layouts have no required delivery stages. Continue On remains a reference capability, outside the release gates. External-display desktop modes on other Android hardware are future qualification candidates, not inherited support.

P1 must document actual package/manifest/ABI requirements. P6 must verify the chosen distribution channel and available device targeting; do not invent a Googlebook-only manifest flag or assume a screen-size filter identifies the OS. An unsupported-device message may explain scope when detected reliably, without guessing from display size alone.

Read the [desktop application contract](../03-platform/googlebook/desktop-application-contract.md) for the official API and acceptance mapping. A change that adds a mobile-specific dependency or compromises desktop usability must revisit this rule explicitly.
