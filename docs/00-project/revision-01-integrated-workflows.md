# Revision 01 — Desktop scope and integrated workflows

**Date:** 2026-10-14. **Authority:** the user's four workflow revisions and subsequent desktop-platform clarification. **Result:** planning revision, not implemented or qualified features. The immutable intake and initial planning-validation report remain historical evidence.

## Review findings and decisions

| Request | Gap in the initial plan | Revised requirement | Delivery / evidence |
| --- | --- | --- | --- |
| Rule 0 | Android packaging and desktop priority were implicit; adaptive wording could imply a phone/tablet workstream | Googlebook desktop/laptop applications using Android package/lifecycle rules; no phone/tablet optimization commitment | [Platform rule](platform-rule-zero.md), P1-S1/S2, Q-P03 |
| Separate X11 windows | X11 was excluded from v1; opening a full desktop or detaching a terminal would not satisfy this request | Console SSH X11 forwarding with each remote top-level app represented by a separate native desktop window; dialogs retain ownership | P0-S7 feasibility, P2-S6 implementation; Q-C07/C08 |
| Performance plus RDP/VNC | Only an external high-performance client path had a concrete early stage; RDP/VNC were possible later adapters | Three explicit Desktop lanes: high-performance streaming, integrated RDP client, integrated VNC client; benchmark latency and recovery | P0-S4/S6, P3-S7/S8, P5-S2/S4; Q-D07/D08, Q-N02/N03 |
| USB plug-and-play | Manual ownership/protection was specified without the normal reconnection experience | Enroll once where supported, then discover and auto-attach a known idle device to its approved host; reconcile permissions and backend truth | P0-S5, P3-S6; Q-D06 |
| Integrated SFTP | Console only had attachment uploads; the full browser lived in Desktop | A session-linked SFTP pane in Console with browsing, upload/download/edit, transfer queue, and directional drag/drop | P2-S7, shared transfer reuse in P3-S3; Q-C09/C10 |

## Platform and upstream findings

Google documents Googlebook on the Android stack with desktop window/input foundations. Android APIs and app lifecycle remain binding. Cloudy's desktop priority is now explicit in [Rule 0](platform-rule-zero.md); small-window adaptation is for desktop tiling, not a phone UI branch. G01/G02/G03/G31/G32 provide the official reference trail.

MobaXterm's [manual](https://mobaxterm.mobatek.net/documentation.html) documents both its default multiwindow X server and its adjacent SSH file browser. These are two distinct interaction requirements. An Android X server is feasible as a candidate, but [Termux:X11](https://github.com/termux/termux-x11) documents an app plus companion package; that is not evidence of a ready-made per-X11-app Googlebook window API. The window broker is a substantive P0 risk.

SSH's [X11 protocol](https://www.rfc-editor.org/rfc/rfc4254.html) and [OpenSSH configuration](https://man.openbsd.org/ssh_config) supply forwarding/authentication behavior. They do not supply a native Android X server or make GUI clients durable across transport loss. X11 compatibility, remote Wayland desktops, and game/video streaming have separate support claims.

[FreeRDP 3.27.1](https://github.com/FreeRDP/FreeRDP/releases/tag/3.27.1) includes Android RAIL/RemoteApp window support; describing all Android RemoteApp as unsupported would now be stale. RemoteApp is a separate Windows application-delivery capability, not the X11 solution. Qualification must pin code and the server's published apps. [RFB](https://www.rfc-editor.org/rfc/rfc6143.html) and the [TigerVNC viewer](https://tigervnc.org/doc/vncviewer.html) document VNC negotiation/security variants; they do not promise identical features across every VNC vendor.

[VirtualHere's client API](https://www.virtualhere.com/client_api) includes state queries and device/port auto-use controls. This creates a practical plug-and-play candidate. Commands must be verified against the installed client's `HELP`; the documented auto-use setters are toggles, so blind retry can invert policy. Android [USB permissions](https://developer.android.com/develop/connectivity/usb/host) still apply, including detach/reconnect behavior. Universal zero-prompt forwarding is not established.

## What changes elsewhere

The [X11 design](../02-architecture/x11-windows.md), [Console files design](../02-architecture/console-files.md), [desktop backend design](../02-architecture/remote-desktop-backends.md), and [USB design](../02-architecture/usb-plug-and-play.md) define boundaries, states, failure handling, and acceptance work. [Latency qualification](../06-validation/desktop-performance.md) makes Parsec/Moonlight comparison reproducible and treats GeForce NOW as an experience reference without assuming an embedding API or comparable host.

Full R1 Console and R2 Desktop gates include these requested workflows. An earlier SSH-only development milestone remains useful, but it must be labeled partial. A fallback that opens one X desktop cannot be represented as completion of separate X11 application windows. External RDP/VNC launch alone cannot complete the integrated client stages. P0 blockers require explicit scope revision and re-estimation.

No hardware pass, app implementation, benchmark result, embedded upstream code, or new distribution right is claimed by this revision. The [roadmap](../05-delivery/roadmap.md), [risks](decisions-risks.md), and [ADR 0006](../adr/0006-desktop-integrated-workflows.md) carry the resulting commitments and dependencies.
