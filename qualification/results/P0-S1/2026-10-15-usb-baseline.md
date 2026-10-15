# P0-S1 — USB baseline and execution setup

**Record date:** 2026-10-15, America/Chicago. **Device observation:** 2026-10-15 04:56:52 UTC (2026-10-14 23:56:52 America/Chicago). **Stage:** P0-S1 in progress. **Case:** Q-P01, partial baseline only. **Responsible role:** primary agent / device qualification. **Result data:** [sanitized observations](2026-10-15-usb-baseline.json).

## Observed setup

| Item | Actual observation |
| --- | --- |
| Transport / authorization | One USB-connected ADB device in `device` state; subsequent calls explicitly selected that device |
| Model / manufacturer | HP Googlebook 14 / HP; product device `quartz` |
| Android / API / ABI | Android 17 / API 37 / `arm64-v8a` |
| Build / incremental | `CL3B.260622.276.R2` / `16560795`; `user` build; `ro.debuggable=0` |
| Reported security patch | 2026-09-01 |
| Physical display / reported density | 2880 × 1800 pixels / 260 dpi |
| Active WebView | `com.google.android.webview` 149.0.7827.5, version code 782700504 |
| ADB | 1.0.41, distribution package 34.0.5-debian |
| Local Java | 21.0.9; this is installed inventory, not the selected Android build JDK |
| Installed SDK inventory | Platforms 34/35/36 and Build Tools 35.0.0/36.1.0; API 37 platform was not present in the inspected SDK |
| Build tooling discovery | `gradle` and `sdkmanager` absent from PATH; this alone does not prove every possible installation is absent |
| Selected primary host | Current workstation, selected by user; `/etc/os-release` reports Ubuntu 26.04.1 LTS and session environment reports `wayland` |
| SSH readiness | No TCP port 22 listener observed; backend installation/authentication remains to be established |

Read-only commands were `adb devices`, selected-device `getprop` for an allowlist of build/model fields, `wm size`, `wm density` and `dumpsys webviewupdate`, plus local tool-version/package discovery. Display dimensions are reported device values, not measured window geometry or input performance. No application was installed or launched, no device setting changed, and no programming operation occurred in this run.

The [official Googlebook ADB guide](https://developer.android.com/develop/adaptive-apps/guides/googlebook/adb-debugging) (G04) documents the left USB port for HP Googlebook 14. A working USB connection was observed; the physical port location was not independently inspected. Do not infer that another HP SKU or transport has passed.

## Evidence handling

Raw transport identifiers and diagnostics were saved in the ignored `qualification/inventory.local.json`, with owner-only file permissions. This public record contains allowlisted non-unique model/build data and an anonymous device label. It contains no serial, hostname, account, IP address, credentials or personal application content. The source commit for this setup is the Git commit containing this record; no APK/hash exists because no build was performed.

## Verdict and next step

**Verdict: inconclusive for Q-P01 and stage completion.** ADB authorization and reported device identity are established. Installation/launch, the rest of the input/decoder/host inventory and actual backend behavior remain untested. All feature capability gates remain pending.

Pin a compatible build toolchain, create a real diagnostic APK, run compilation and Android Lint, then install/launch that exact artifact and record its hash/results. Continue API and desktop-interaction probes only within their stage prerequisites. Identify the board/probe before USB programming; Windows remains pending a target, with Parsec accepted temporarily by the user.
