# Streaming host: bounded Sunshine runtime review

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4. **Result:** the selected real Sunshine AppImage started in a finite loopback fixture, created KWin/PipeWire capture streams and completed its H.264/HEVC/AV1 VA-API startup probes after two concrete integration corrections. This is host startup/capture-path/encoder-probe evidence. It does **not** qualify client pairing, client-certificate TLS, delivered client frames, keyboard/relative input, hardware performance or CAP-STREAM.

The integration leader performed the retained runs; this review only read their scripts/results/logs and official sources. It performed no new host launch, build, device operation or system change. Follow the [host entry](../03-platform/googlebook/native-stream-host-entry.md), [native adoption entry](../03-platform/googlebook/native-stream-adoption-entry.md) and [release/master distinction](../07-reference/2026-10-15-sunshine-source-drift.md).

## Exact inputs and finite fixture

The selected server is official Sunshine **v2026.914.233613**, source revision **`63d35f702ee9e362e43263742981836ec0710384`**, extracted from the verified x86_64 AppImage. Package SHA256 is `6f80297a853bcf8cda114ff131b5c0a8c9a4d96a2a9c3dee1f04c39c54206846`; actual executable SHA256 is **`6946d8ac8bb0e99da1e51b374029c8b24dcabb315265a0e7004d046eb6f143fd`**. The accepted loader preflight remains `.cache/native-stream-host/preflight-02/preflight.json`. The Ubuntu DEB's Qt/miniupnpc incompatibilities are historical separate evidence, not repaired by a system upgrade.

The actual host uses Ubuntu26.04/Wayland KDE, KWin **`4:6.5.5-0ubuntu2`** and KService **6.23.0**. Source reasoning below uses matching upstream tags; it does not claim a byte-for-byte audit of Ubuntu's downstream build or use master as the installed implementation.

Each live fixture used explicit private config/state/key/certificate/apps/log paths, an empty app list, loopback-only IPv4 binding, UPnP disabled, local-only web administration, KWin capture and the selected VA-API render-node route. Audio/controller use was disabled. The scripts asserted that KWin permission checks were not disabled, verified the executable hash and checked selected ports before launch. The process had its own group, a 60-second observation ceiling and TERM/KILL cleanup. Private host certificate material and machine-specific paths remain in ignored evidence, not this report.

## Actual sequence and outcomes

| Run | Actual outcome | Evidence limit |
| --- | --- | --- |
| `runtime-01` | Sunshine created its normal permission entry, waited 3s, but `zkde_screencast_unstable_v1` remained absent from its registry. Loopback HTTP server information returned 200 and web TLS1.3 presented the fixture's exact certificate. | Host service startup passed; KWin capture permission selection failed. |
| `runtime-02` | A separately named, correctly quoted permission entry plus desktop validation/cache refresh admitted actual KWin capture. Logs show PipeWire stream creation and transition to streaming. Running from the private run directory left relative OpenGL shader resources unavailable; VA-API initialization failed. Sunshine subsequently logged H.264/HEVC/AV1 Vulkan encoder discovery. | Do not report the requested VA-API route as successful merely because fallback encoders were found. |
| `runtime-03` | Prelaunch port binding failed with `OSError`, errno98. The retained failure says `processStarted=false`, `permissionCreated=false`. | No Sunshine process or capture attempt occurred. A bind error alone does not identify a live listener or its owner. |
| `runtime-04` | Preserved the quoted permission/cache procedure and launched with working directory equal to the extracted AppDir. Logs show actual KWin/PipeWire streams and discovery of **`h264_vaapi`**, **`hevc_vaapi`** and **`av1_vaapi`** after their startup probes. Loopback HTTP returned 200 and web TLS1.3 matched the private fixture certificate. | Actual startup encoder probes passed; no client negotiation, delivered frames or sustained/performance result exists. |

The three live processes exited0 after approximately 12.2–12.3s: 12,233ms, 12,321ms and 12,322ms respectively. Each result verifies all four observed listener ports closed after stop. Owned generated permission entries were removed; runs02/04 also removed the quoted fixture entry and completed the user desktop-cache cleanup successfully. The failed run03 is preserved rather than counted as a successful runtime case.

## Why the two corrections were necessary

Pinned [Sunshine kwingrab.cpp](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/src/platform/linux/kwingrab.cpp) writes an unquoted `Exec=` executable path into its temporary desktop entry. The actual workspace path contains spaces. Tagged [KWin6.5.5 serviceutils.h](https://github.com/KDE/kwin/blob/v6.5.5/src/utils/serviceutils.h) uses `QProcess::splitCommand(service->exec())`, then compares the first token's canonical path with the real client executable. That generated entry cannot identify the full executable correctly. Tagged [KService6.23.0 KApplicationTrader](https://github.com/KDE/kservice/blob/v6.23.0/src/services/kapplicationtrader.cpp) queries its validated service cache; [KWin's permission filter](https://github.com/KDE/kwin/blob/v6.5.5/src/wayland_server.cpp) then caches requested interfaces per Wayland client.

The correction created a uniquely named Cloudy entry outside Sunshine's cleanup prefix, with the exact quoted executable and only `X-KDE-Wayland-Interfaces=zkde_screencast_unstable_v1`. `desktop-file-validate` and `kbuildsycoca6 --noincremental` succeeded before a fresh process. Merely editing Sunshine's own entry would be fragile: its helper treats the entire `Exec=` suffix as a raw path and can delete quoted entries under its prefix as stale. No compositor permission bypass or restart was used.

The second correction restored the package's intended working directory. The pinned [AppRun](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/packaging/linux/AppImage/AppRun) changes into its AppDir before executing the server. Directly invoking its binary from the private run directory did not reproduce that resource environment. `runtime-02` reports relative `./usr/share/sunshine/shaders/opengl/...` failures; `runtime-04` restores the working-directory route and records successful VA-API probes. Logs retain upstream warnings, including AV1 slice-control limitations; encoder availability is not absence of warnings or a performance certificate.

The AppImage's unsupported KMS route was not substituted. A logged failed capability attempt did not trigger granting `cap_sys_admin`, running as root, installing packages or changing udev rules. User permission-file/cache writes were the scoped host integration changes, with cleanup verified. The stable [setup](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/docs/getting_started.md) and [configuration](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/docs/configuration.md) distinguish KWin, KMS and encoder selection; neither source implies client support from a startup probe.

## Retained evidence and remaining gate

Private evidence is `.cache/native-stream-host/runtime-{01,02,03,04}/`, the matching `.cache/native-stream-host/runtime_probe*.py` scripts, `.cache/native-stream-host/source-review/` and the tagged source inspection described above. Raw logs, certificates, private keys and host-specific inventories remain ignored. Safe result bindings at this review freeze are:

| Private record | SHA256 |
| --- | --- |
| `runtime-01/result.json` | `76091666041afc4b3373fc1629317ea145112df66e3cdd18d646e1da23f7defc` |
| `runtime-02/result.json` | `bdcab76c101a9b00a423163c47db96e101c81c530c70ba797235e8438cec33b2` |
| `runtime-03/operator-failure.json` | `33696c9e8ea1255c5a78e6f9f1be073e407b92a5dd9917a9b7363d6fb3daf620` |
| `runtime-04/result.json` | `13b81bacb608e5ec90938565eeb9f6e576d957d5373af75b06e2f03d1be121ea` |

The parent owns canonical machine-readable bindings and phase/state integration. The next fixture must still qualify actual Android-owned pairing/trust/client-key TLS, network cancellation, real changing client Surface output, selected codec negotiation, input/release and sustained measurements. Loopback web TLS is server-certificate observation, not opaque Android client-key TLS. Actual KWin/PipeWire startup activity and VA-API codec probes narrow the remaining feasibility work; they do not certify the full hardware/streaming requirement.

The parent's [machine-readable manifest](../../qualification/results/P0-S4/2026-10-15-stream-host-runtime.json) now binds the exact runtime records, operator scripts, private logs/configuration and executable. Canonical phase/current-state integration preserves these bounded claims.
