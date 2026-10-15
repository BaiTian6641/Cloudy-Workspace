# Networking, data use, and measurement

Use the existing official Tailscale client initially. Cloudy does not add a competing Android VPN. Routing and direct/relay visibility depend on supported diagnostics; report unknown rather than embedding tailnet administrator credentials. Reference [Tailscale connection types](https://tailscale.com/docs/reference/connection-types).

## Authorized flows to qualify

| Direction | Flow | Measurement boundary |
| --- | --- | --- |
| Googlebook ↔ host | SSH/PTYS/SFTP/local forwards/X11 channels | App payload plus optional OS wire counters; X11 separate from terminal-only samples |
| Googlebook ↔ RDP/VNC server | Native desktop protocol and negotiated channels | Per-protocol encoding/security/transport and input/response timing |
| Googlebook → host agent | Paired capabilities/requests/events | Bounded control-plane traffic |
| Host → Googlebook | Desktop video/audio | Stream payload and measured decoder/network statistics |
| Googlebook ↔ host | Desktop input / optional microphone | Separate from video downlink |
| Host consumer → Googlebook provider | USB transport | Device payload, request latency, both directions |
| Host ↔ host | Optional bulk copy | Do not route through Googlebook without reason |
| Host → model/cloud | Agent/API/download traffic | Host internet use, separate from terminal bytes |

Record required ports from the pinned backend rather than inventing a universal firewall list. Test tailnet policy and host firewall together. Discovery broadcasts are not assumed to cross a tailnet.

## Planning arithmetic

Decimal units: 1 GB = 10^9 bytes; 1 Mbps = 10^6 bits/s.

```text
video_GB = average_Mbps × hours × 0.45
illustrative_video_budget = video_GB × 1.10
large_file_budget = file_GB × 1.05
```

The multipliers are inherited planning allowances, not measured VPN/protocol overhead. Do not add an allowance to bytes already measured on the wire. Configured bitrate is a setting, not necessarily the observed average. Do not add host transmit and client receive counters as if they were separate copies of one stream.

| Proposed profile | Stream setting | Illustrative video GB/hour |
| --- | --- | ---: |
| Metered | 1920×1200, 30 fps, 6 Mbps | 2.970 |
| Balanced | 1920×1200, 60 fps, 15 Mbps | 7.425 |
| Sharp | 2560×1600, 60 fps, 30 Mbps | 14.850 |

At 15 Mbps, 4 h/day for 22 days is 653.4 GB before files/audio/other traffic. An assumed terminal rate of 0.05 GB/hour gives 8.8 GB for 176 hours. Neither number is a benchmark. The original plan's extended scenarios remain in [the intake](../intake/GoogleBook_Self_Cloud_Workspace_Plan.md).

## Counter model

Every reported number carries: units, start/end interval, direction, endpoint, network interface/path if observable, and `measured-payload`, `measured-wire`, or `estimated`. Unknown path and unknown traffic remain unknown. VPN-wide OS counters and application counters cover different populations; explain the difference rather than pretending exact reconciliation.

Measure 10–15 minute samples of terminal light/heavy redraw, idle/scrolling/CAD video, transfers, USB programming, and idle control traffic. Test same-LAN direct, real remote direct, and relayed/degraded paths where available. Keep saturation tests separate from active programming. Capture RTT distribution, loss, bytes, frame pacing, encode/decode times, dropped frames, CPU, battery, and versions.

Add X11 redraw and native RDP/VNC productivity workloads as separate rows. The [desktop performance procedure](../06-validation/desktop-performance.md) defines longer pacing/thermal runs, comparative input-to-photon measurements and impaired links. A low RTT does not establish low display latency. Prioritize interactive work using supported backend controls and bounded queues; preserve dependent RDP/VNC state and held-input transitions.

## Metered-mode policy

Default to Console for command-line work. Require an explicit stream profile, stop hidden streams, disable automatic live thumbnails and large prefetch, and show transfer sizes before starting. Allow pause/priority changes for bulk jobs without automatically interrupting protected device operations. Budget warnings explain measurement scope and uncertainty; they do not silently kill sessions.
