# Product vision and scope

**Working names:** Cloudy Console and Cloudy Desktop. These rename the original GoogleBook Terminal and Remote Workspace concepts; package names remain provisional until P1. The suite is Cloudy Workspace. “Desktop” is the remote-work application, not an OS launcher replacement.

**[Rule 0](platform-rule-zero.md):** Googlebook desktop/laptop applications using Android packaging, permissions and lifecycle. Phone/tablet installation compatibility does not add a supported form factor. Design and release work prioritize physical keyboard, trackpad/mouse, native resizable windows, desktop Files integration and multitasking; no phone/tablet optimization is scheduled.

The primary user works from an HP Googlebook 14 while compute, repositories, toolchains, agent credentials, and graphical applications remain on owned Windows, macOS, and Linux hosts. The initial inventory names Mac Studio, MS-02Ultra, Zenbook Duo, and ZBook Ultra; their installed OSes and actual peripherals must be recorded rather than inferred from model names.

## Product promises

1. Start productive terminal work with adjacent remote files and separate X11 app windows without bringing up a full desktop stream.
2. Move from a terminal project to its streaming/RDP/VNC desktop, files, and enrolled plug-and-play hardware while preserving host and project context.
3. Always know which host, environment, session, network path, and device owner an action affects.
4. Survive ordinary network and client lifecycle failures without replaying work.
5. Present dense technical information in a calm, accessible visual system.

## Release slices

| Slice | Included | Excluded until later gates |
| --- | --- | --- |
| R0: qualification | Desktop Android/window/input, SSH/renderer/X11 broker, stream/RDP/VNC engines, probe/hotplug, latency experiments | Product readiness claims |
| R1: Console daily use | Trusted SSH, PTY, layout, tmux where available, separate X11 app windows, session-linked SFTP/drop/edit, local tunnels | Graphical agent approval automation, local VM attach |
| R2: Desktop companion | Saved hosts/workspaces, native RDP/VNC, external high-performance stream handoff, files/queue, enrolled USB plug-and-play | Embedded high-performance video until P5, universal cloud filesystem |
| R3: integrated workspace | Agent capabilities/events, qualified OS adapters, richer file access | Full notification fidelity on every OS |
| R4: integrated desktop | Qualified native high-performance streaming with measured latency/stability, document provider, metered policy | Any unsupported compositor/probe combination |
| R5: supported release | Signed packages, migration/rollback, published support matrix, dogfooding | Unmeasured hardware promises |

R1 can ship before Desktop. R2 may require separate Tailscale, Moonlight, and VirtualHere installations. “Two products” does not promise only two installed packages.

An early SSH-only Console or launcher-only Desktop is a useful partial development milestone. It does not complete the revised R1/R2 gates. Native streaming distribution and X11/USB feasibility can require explicit scope revision; fallback labels must preserve what remains unmet.

## Explicit scope limits

No new SSH cryptography, video codec, USB kernel driver, universal cloud mount, automatic shell/agent approvals, destructive bidirectional sync, mandatory central cloud, cross-host live-process migration, phone/tablet optimization, or phone-continuity requirement. Linux VM control and device-specific features stay optional until a supported interface is found. A user-session helper is required for many host capabilities; a service running at boot is not proof it can capture a user's desktop.

## Success measures

The first useful Console release completes a real coding day with exact paste, IME, reconnect, and artifact/tunnel workflows. The first useful Desktop release completes a real build–program–inspect cycle on a qualified host/probe. Release claims attach to recorded combinations, not to broad OS names.

Proposed performance budgets are in [validation](../06-validation/strategy.md). They are hypotheses to refine after P0, not results.
