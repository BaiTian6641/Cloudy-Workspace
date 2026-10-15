# P0-S1 setup closeout

**Recorded:** 2026-10-15 UTC / America/Chicago. **Verdict:** pass for inventory and reproducible setup, with explicit unavailable or unverified fields. **Responsible role:** device/QA owner. **Case:** Q-P01 setup subset only. P0-S1 is complete; full Q-P01 and every feature capability remain pending. P0-S2 is ready to begin.

The [manifest](2026-10-15-setup-closeout.json) binds the sanitized observations and private collection hash. Its SHA-256 is `a52864bfd62335a1343d2420773e4e29647cbf2b4bc93f18d2b6e20d333fe030`. The immutable [initial USB baseline](2026-10-15-usb-baseline.md) and [actual diagnostic APK run](2026-10-15-device-probe.md) retain their original partial verdicts; this closeout adds the remaining setup evidence.

## Identity and selected workload

The client is HP Googlebook 14, Android 17/API 37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`, WebView 149.0.7827.5. Queried `ro.boot.hardware.sku` returned empty, so the exact sales SKU remains unverified. `/proc/meminfo` reported 15,852,148 KiB MemTotal; this is OS-visible memory, not an installed-RAM marketing claim. Input, display/caption and video-codec reports are indexed from the prior probe export, without promoting them to functional tests.

The selected workstation's DMI product name reports **HP ZBook Ultra G1a 14 inch Mobile Workstation PC**. This is a newly observed identity, not an inference from the intake list. The [host inspection](../../../docs/03-platform/linux-primary-host-qualification.md) records Ubuntu 26.04.1 LTS, Linux 7.0.0-38-generic, KDE Wayland/KWin/Plasma 6.5.5, AMD PCI 1002:1586 / amdgpu and Mesa 26.0.8. MemTotal reports 98,628,860 KiB. The existing local login and a dedicated ignored fixture directory within this project are selected for later experiments. No unrelated user project is exposed by this selection.

The private inventory now contains normalized client, hosts, peripherals, backends and network-topology sections, with original snapshots preserved. Account, paths, endpoint addresses and ADB identity remain private with mode 0600. MS-02Ultra, Mac Studio and ASUS Zenbook Duo are intake-only and uninspected. Windows has no selected test target. The USB board/debug probe is deliberately undecided, as requested; no new hardware decision is required to close setup.

## Route and backend observations

Read-only route/address checks identified one address on the selected workstation in the Googlebook's reported local subnet. Two ADB-shell ICMP packets to that endpoint both received replies. This establishes a narrow local-LAN reachability smoke result; it measures no application protocol, throughput, security or input latency.

Tailscale reports Linux `1.104.1-t7f4efe814-g8da26756c`, with host backend `Running`; the Android package reports `1.104.1-t7f4efe814-g6d75bd403`. Two ADB-shell ICMP packets to the selected host's tailnet address received no replies. The shell route lookup selected `wlan0` under UID 2000. The reason is unresolved. Android permits per-app VPN policy, and Tailscale supports app exclusions, so this shell result is insufficient to diagnose the eventual Cloudy app route. Sources: [Android VPN guide, G53](https://developer.android.com/develop/connectivity/vpn) and [Tailscale Android split tunneling, R50](https://tailscale.com/docs/features/client/android-app-split-tunneling). No VPN policy was changed.

Use **local LAN as the first qualification route**, with the existing tailnet retained as an unverified remote-route candidate. The selected host has no observed TCP port 22 listener; an SSH/SFTP server must be established and its trust/authentication tested before protocol acceptance. RDP, VNC, streaming and USB backends remain candidates. No new service, listener, account or authentication policy was activated during inventory.

The defined USB fallback is a future lab-side provider on the selected Linux workstation, with the Googlebook as the remote client. It remains untested and needs representative hardware. It does not satisfy the required Googlebook-local USB plug-and-play workflow, and does not reduce the full generic implementation scope.

## Build, review and exit evidence

The exact probe source/APK identity and real install/launch/refresh/background-return results are in the [device report](2026-10-15-device-probe.md). Source correction [`3dfd070`](https://github.com/BaiTian6641/Cloudy-Workspace/commit/3dfd0705155ef5481e6d40c773ffdbfcb17ade86) completed five canonical metadata checksum entries. A fresh checkout/cache passed all 49 Android build/lint/test tasks with verification enabled. Both hosted workflows passed for that exact commit: [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38688126844) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38688126993). The earlier failed Android run remains documented; it is not overwritten by the successful correction.

The [stage review](../../../docs/06-validation/2026-10-15-device-probe-review.md) covers source, lifecycle/persistence corrections, pins, checks and evidence boundaries. The primary agent reviewed subagent changes and ran the selected-device operations. Setup exit criteria are met: an actual development APK installed and ran, inventory/topology and gaps are recorded, and support is not inferred from model names or package presence.

Next is P0-S2: exact API/window/lifecycle/input experiments and the operation-specific lifetime decisions. Physical input/IME, process death, actual decode/performance, remote protocols, X11, USB forwarding and release support remain unqualified. The tailnet failure is a bounded route investigation for later protocol work, not a reason to block independent platform probes.
