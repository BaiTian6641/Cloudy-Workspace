# P0-S2: Linux VM integration boundary

**Checked:** 2026-10-15. **Owner:** Android/platform developer. **Scope:** API22/API23 documentation investigation; no VM was started, installed, reset or modified. This note does not qualify a local Linux backend.

Google's [12 October preview announcement, G06](https://developer.android.com/blog/posts/googlebook-linux-terminal-preview-vm) documents an isolated Debian environment, user-approved directory sharing, network passthrough, wireless ADB and localhost forwarding to the host browser. It still describes USB passthrough, VM-side USB ADB, GPU/NPU acceleration and persistence independent of the terminal window as future work. This does not change the successful external USB ADB path used to debug Cloudy's Android APK.

The official [Terminal help page, G54](https://support.google.com/googlebook/answer/18119222?hl=en) describes installing the VM image through the Terminal app, its tabs/windows/settings, VM files in Files, and launching graphical applications through its graphics screen. These are documented user workflows. They do not define a third-party attach/control contract for Cloudy.

## Investigation and decision

The review inspected G06, G54 and the [Googlebook developer overview, G01](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview), with focused official-domain searches for third-party Linux VM APIs, intents and integration. No supported public attach/control API was established in those sources. This is a bounded research finding, not proof that no interface can ever exist.

API22 therefore retains `no-public-api-established`, an empty symbol list and no asserted minimum SDK. API23 retains `roadmap`. Neither receives a device pass. Cloudy continues with ordinary remote SSH and separately qualified Android USB/protocol engines. The user has not made an embedded local VM a required release dependency, so this boundary does not block independent desktop-window or remote-host work.

A user-configured SSH server inside a future VM would be a distinct network backend requiring route, authentication, host-key, lifecycle and file-access qualification. Browser localhost forwarding does not by itself promise a route for every Android application. The existence of VM files in Files does not grant Cloudy direct filesystem access; any future user-selected URI integration must follow Android grants.

## Reopening condition

Before implementing a local VM integration, identify an official public symbol or explicit supported IPC/intent contract, including publisher, SDK/library version, permission and caller identity rules, VM ownership/lifetime and failure semantics. Then compile against that contract and run an identified device/build experiment. Record unsupported paths honestly. Do not infer ChromeOS/Crostini interfaces, depend on hidden Terminal activities/Binder services, scrape private VM files, or reconstruct privileged launch commands to fill this gap.

The documentation investigation is complete for this snapshot. Future capability changes require a source/API tracker update and fresh evidence; this note closes neither P0-S2 as a whole nor CAP-PLATFORM.
