# ADR 0006 — Desktop ecosystem and integrated workflow scope

**Refined by [ADR 0007](0007-publication-and-independent-delivery.md):** the full feature scope remains; per-save file consent, independent app release gates and earlier native-stream adoption now govern delivery.

**Status:** adopted for planning; implementation choices pending qualification. **Date:** 2026-10-14. **Amends:** [0002](0002-native-shell-and-terminal-surface.md) and [0003](0003-external-backends-first.md) without changing app independence.

Cloudy is a Googlebook desktop/laptop suite using Android packages and platform APIs. [Rule 0](../00-project/platform-rule-zero.md) excludes phone/tablet optimization work while requiring usable narrow desktop windows, accessibility, native IME, and Android lifecycle/permission behavior.

Full Console scope includes SSH-forwarded X11 applications in separate native desktop windows and an adjacent session-linked SFTP browser with bidirectional transfers and Android drag/drop. Full Desktop companion scope includes native integrated RDP and VNC clients, enrolled USB plug-and-play, files and high-performance streaming handoff. Native integrated high-performance streaming remains a separately qualified later stage, with measured latency/stability goals and a distribution decision before embedding.

Established external backends still provide early evidence and a useful stream milestone. External launch alone does not complete the native RDP/VNC requirements. A single X desktop is not a substitute for separate X11 app windows. Any change to those requirements must be explicit and record remaining work and affected acceptance gates; do not silently label a fallback feature-complete.

Consequences: dedicated X11 feasibility/broker and file-integration stages in P2; RDP/VNC and USB auto-attach stages in P3; earlier license/engine review; broader physical input/window/security/performance cases. USB auto-attach is allowed for enrolled devices with confirmed idle ownership and an approved destination. Unknown/protected ownership still prevents reassignment or flash replay.

The four detailed designs are [X11](../02-architecture/x11-windows.md), [files](../02-architecture/console-files.md), [desktop backends](../02-architecture/remote-desktop-backends.md), and [USB](../02-architecture/usb-plug-and-play.md). [Revision 01](../00-project/revision-01-integrated-workflows.md) records findings and scope changes. This ADR does not select a final X server, grant upstream embedding rights, or claim measured Googlebook compatibility.
