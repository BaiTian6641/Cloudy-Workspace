# First live native RDP sessions on Googlebook

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4, in progress. **Result:** actual wrong-pin rejection and certificate-pinned NLA connection are observed. Three attempts join/free their native instance; a connected task closure is interrupted by Android process removal and is reconciled honestly on relaunch. **No remote desktop pixels, input or performance are qualified.** The final attempt reaches host capture/encoding but stops at an unresolved Android adapter failure before its first frame.

## Exact scope and evidence

The unchanged separate `dev.cloudy.qualification.rdp` diagnostic is version 0.1.0 / code 1, buildRef `2b12a3d1-74aa-47ee-8238-d606181adbd5`, APK SHA256 `35fb24931dd5e3acdbd5a20898dbdb7c64de276743624c4b4d7469dba0d89371`. Source commit [2e15f83](https://github.com/BaiTian6641/Cloudy-Workspace/commit/2e15f830063badf5167b226b3ea3e59bdf731dea) binds the earlier [APK/load evidence](2026-10-15-rdp-apk-load.md). Target remains HP Googlebook 14, CL3B.260622.276.R2, API 37, arm64-v8a. No app/native source was changed for these sessions.

Host: Ubuntu 26.04, KDE/Wayland, KWin 6.5.5, Qt 6.9.2, KPipeWire 6.5.5; privately extracted official KRdp 6.4.5-0ubuntu1 and matching installed-version FreeRDP server 3.32.1+dfsg-0ubuntu0.26.04.1. No system packages, desktop session or display configuration were changed. The finite server binds only loopback and uses a selected ADB reverse route, fresh fixture credentials and a dedicated certificate. This is a USB-debug transport test, not LAN/VPN latency qualification.

The [manifest](2026-10-15-rdp-first-sessions-manifest.json) projects all four retained attempts and hashes the private command receipts, journals, screenshots, device logs, host logs and cleanup records. Raw data stays under `qualification/results/private/native-rdp-session/`. Both captured screenshots were immediately visually inspected: neither displays remote pixels. The separate terminal baseline and its records were not operated on.

## Observed cases

| Attempt | Stimulus and actual outcome | Boundary established |
| --- | --- | --- |
| 1 | Initial isolated host setup failed before TLS: missing declared capture interfaces and a sandbox-denied runtime-directory write. Client transport failure; zero certificate/auth callbacks; thread joined and instance freed | Real failure cleanup; not a certificate-rejection result |
| 2 | Correctly registered host; deliberately altered one hex digit of the independently derived DER leaf pin. One certificate rejection, zero authentication callbacks, zero frames/input; TLS failure, joined/freed | Actual external-certificate callback rejects the mismatched pin before authentication |
| 3 | Restore the approved pin with the same endpoint/credentials. One certificate acceptance and one authentication callback; client and server report connected; RDPGFX 8.1 AVC/YUV420 capability negotiation. Zero frames because the host stream request is null | Pinned NLA session reaches connected state; negotiation is not decoded pixels |
| 4 | Same fixture identity, add only `--monitor 0`. Host reports `Started Plasma session`, initializes capture, falls back from a failed VAAPI filter path to software x264 and logs encoded I/P frames. Client connects, then records `adapter-failure`, cancellation, joined/freed. Zero delivered frame updates/input | Capture now starts; the remaining failure is at the Android adapter boundary and is not yet diagnosed |

The app's local-network permission was granted through the observed system dialog. Configuration stayed app-private. No remote keyboard, mouse or committed-text input was sent while the remote image was unavailable.

For attempt 3, the observed native caption Close action removed the Android task and process. `ApplicationExitInfo` records `USER REQUESTED` / `REMOVE TASK`, not a native-crash classification. The journal's last connected record remained unfinished; relaunch marked it `process-interrupted` while leaving `nativeThreadJoined=false` and `nativeInstanceFreed=false`. Do not claim a graceful join for this case. The other three attempts retain actual true join/free fields.

## Host setup findings and bounded remediation

Explicit Qt `-platform wayland -platformtheme generic --help` exited 0 with no stderr. This resolves the next startup check; it does not explain the earlier inherited-theme timeout. The actual fixture retained the existing user's Wayland socket/runtime/session bus.

KWin's [service lookup](https://github.com/KDE/kwin/blob/v6.5.5/src/utils/serviceutils.h) matches the connecting executable against the canonical first token of desktop-entry `Exec`; its [interface policy](https://github.com/KDE/kwin/blob/v6.5.5/src/wayland_server.cpp) uses the matched declaration. The extracted package's `/usr/bin/krdpserver` entry did not match its actual private executable path. A temporary user desktop entry with that exact quoted path and the upstream two requested interfaces was registered through `kbuildsycoca6`, then removed through the same mechanism. No compositor permission checks were disabled. Scoped execution permission allowed normal runtime writes; the runtime directory was not redirected to evade the sandbox.

The separate zero-frame host defect is visible in exact upstream source: [SessionController](https://github.com/KDE/krdp/blob/v6.4.5/server/SessionController.cpp) supplies `-1` when the monitor option is omitted, while [PlasmaScreencastV1Session](https://github.com/KDE/krdp/blob/v6.4.5/src/PlasmaScreencastV1Session.cpp) tests `!activeStream()`. Thus the default leaves the request null and produces two null-connect warnings. `--monitor 0` makes that branch create a **workspace stream**. This case does not establish selection of physical monitor zero; the exact [workspace helper](https://github.com/KDE/krdp/blob/v6.4.5/src/screencasting.cpp) may cover multiple outputs.

## Remaining defect and next bounded change

The current Java wrapper reports only a callback exception class; `RdpSession` does not persist it before shutdown overwrites the UI detail. A resize guard rejects dimensions above 4096 or more than 4,096,000 pixels, but the current evidence does not identify the failed callback or actual requested size. Graphics-copy and cursor validation can also fail. Do not widen allocation limits or blame MediaCodec from these observations.

Next: retain a bounded first-failure record containing an authored operation name, exception class and relevant integer dimensions; reject stale callback identities, persist before stopping and retain through reap. Keep messages, stack traces, credentials and clipboard data out of the journal. Then perform one same-fixture attempt to identify the failed boundary. Pixels, actual decoder output layout, input/focus, explicit disconnect/cancel, performance and broader RDP compatibility remain pending. CAP-RDP is not supported.

## Teardown and review

All three finite fixtures are stopped/reaped and their selected listeners are closed. Both temporary desktop registrations were removed and service-cache refresh returned 0. The SAM temporary files described by [KRdp's connection source](https://github.com/KDE/krdp/blob/v6.4.5/src/RdpConnection.cpp) were absent after teardown; newly created empty runtime directories were removed. Only owned ADB reverse mappings and device configuration files were removed. Five expired host credential/key files were deleted. The app's local-network permission remains granted; its four-attempt journal is preserved and the idle task is closed.

Operator failures remain distinguishable from application results: duplicate evidence labels and a command/raw-journal filename collision stopped receipt-writing operations without deleting prior evidence; an initial cleanup assertion compared a sandbox PID with the host namespace and was replaced by namespace-correct reaping/process checks. No extra connection was inferred from those failures.

Direct GPT6.1-Sol read-only reviews supplied the exact KDE registration, SAM lifetime and monitor-branch sources. The parent inspected the actual source, controlled every listener/device operation and verified journals/logs/cleanup. Existing compilation, Lint and 14 JVM tests are carried forward for unchanged app/native code. The source milestone passed [Repository](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38779787314) and [Android baseline](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38779787224) CI; those hosted runs do not cover native runtime behavior.
