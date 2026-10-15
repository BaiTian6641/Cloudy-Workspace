# Compatibility matrix: observed inventory and unqualified workflows

**Updated:** 2026-10-15. The [real diagnostic run](../../qualification/results/P0-S1/2026-10-15-device-probe.md) establishes install/launch and limited framework observations; the [Linux inventory](../03-platform/linux-primary-host-qualification.md) establishes host prerequisites only. All product workflow rows below remain **unqualified**. Listed intake models are not assumed to be available or to match the selected workstation.

| Client / host | Installed OS/build | Backend/compositor/driver | Workflow | Status | Evidence |
| --- | --- | --- | --- | --- | --- |
| HP Googlebook 14 | Android 17/API 37, CL3B.260622.276.R2 | arm64-v8a; WebView 149.0.7827.5; 36 reported video decoder entries, behavior untested | Keyboard/IME/resize/process death | Unqualified | [Diagnostic observations](../../qualification/results/P0-S1/2026-10-15-device-probe.md) |
| HP Googlebook 14 | Android 17/API 37, CL3B.260622.276.R2 | Mouse/touchpad source classes reported; physical behavior untested | Relative CAD input/capture release | Unqualified | None |
| HP Googlebook 14 | Android 17/API 37, CL3B.260622.276.R2 | USB provider unqualified; board/probe deliberately undecided | Actual flash/verify/reset/owner reconciliation | Unqualified | None |
| MS-02Ultra | Unknown; do not infer Windows/Linux | GPU/driver/session unknown | Primary SSH/desktop/embedded workflow | Unqualified | None |
| Mac Studio | Unknown macOS version | Capture/input permissions/backend unknown | SSH/visual/own-job events | Unqualified | None |
| ASUS Zenbook Duo | Unknown | GPU/session unknown | Secondary host | Unqualified | None |
| HP ZBook Ultra G1a 14 inch Mobile Workstation PC (selected primary Linux host; DMI observed) | Ubuntu 26.04.1; kernel 7.0.0-38-generic | KDE Wayland/Plasma 6.5.5, amdgpu, PipeWire 1.6.2; capture/encoder untested | Capture/input/lock/headless/notifications | Unqualified | [Host inspection](../03-platform/linux-primary-host-qualification.md), [setup identity/route](../../qualification/results/P0-S1/2026-10-15-setup-closeout.md) |
| HP Googlebook 14 + selected SSH/X11 host | Unknown | SSH engine/X server/window broker unknown | Separate X11 app windows, dialogs, IME and isolation | Unqualified | None |
| HP Googlebook 14 + selected RDP server | Unknown | RDP engine/server edition/security/graphics unknown | Native RDP render/input/session recovery | Unqualified | None |
| HP Googlebook 14 + selected VNC servers | Unknown | RFB engine/encoding/security unknown | Native VNC desktop and protected route | Unqualified | None |
| HP Googlebook 14 + Android Files/editor | Unknown | URI/drop/staging receiver unknown | Session SFTP and incoming/outgoing file drops | Unqualified | None |
| HP Googlebook 14 + enrolled USB consumer | Unknown | Provider/client/driver/probe identities unknown | Repeated plug-and-play and approved reenumeration | Unqualified | None |

Phones/tablets and other Android desktop modes are outside current supported scope. APK launch compatibility is not a qualified row. Googlebook touchscreen access remains part of desktop accessibility/input review where relevant.

## Add a qualified row

Use a narrow combination: client SKU/build + app version + backend versions + host OS/session/compositor/GPU + input device + probe/tool/firmware + route/display profile. State the exact passing case IDs, run link, and exclusions. Partial support is a list of passing capabilities, not a green OS badge.

Separate native app, external backend, and lab-side fallback topologies. A future backend or OS update creates a new qualification row or a required regression run; it does not inherit every prior pass.
