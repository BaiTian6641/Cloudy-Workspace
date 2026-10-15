# P0-S1: real Googlebook diagnostic APK

**Run:** 2026-10-15, 05:26–05:28 UTC. **Verdict:** bounded build/install/launch/report/refresh/background-resume smoke passed. **Stage:** P0-S1 remains in progress; all product capability gates remain pending. This report supersedes only the earlier baseline's missing-APK observation; it does not rewrite that historical run.

Source: [`cf6630b6ff98ea8131ced1ef7ebaef36a33494af`](https://github.com/BaiTian6641/Cloudy-Workspace/commit/cf6630b6ff98ea8131ced1ef7ebaef36a33494af). Package `dev.cloudy.qualification.baseline`, debug version 0.1.0/1. APK SHA-256: `653dd35130a06453d6795b0893ead384152516bf0104fcfa6efd655ac90d00df`. The [public manifest](2026-10-15-device-probe.json) records the exact observations and hashes of locally retained raw exports. Manifest SHA-256: `d10e972c05b7fb1471444fedae4d0c9252166d5ac1c3edc3c6d3937cc8e652c7`.

The target reports HP Googlebook 14, Android 17/API 37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`, security patch 2026-09-01. ADB selected the authorized USB target explicitly. Transport serials, raw UI hierarchy and full build/input/codec exports remain ignored private evidence. No personal app content is published.

## What ran

1. Compiled the actual Kotlin probe, assembled its APK, ran Android Lint with warnings as errors and ran three JVM history tests. AGP 9.4.0 / Kotlin 2.2.10, Gradle 9.6.0, Oracle JDK 21.0.9, compile SDK 37.0 revision 2, Build Tools 36.0.0, target 37/minimum 35. See [toolchain and integrity](../../../docs/03-platform/googlebook/probe-toolchain.md).
2. Repeated the normal build with dependency verification and locks enforced, without regenerating metadata. It passed; lint reported **No issues found**. The one documented lint exclusion is the update notice for the intentionally pinned wrapper file.
3. `adb install -r` returned **Success**. Explicit launcher invocation returned **Status: ok**, cold launch, reported total 188 ms. This is launch-command timing, not an end-to-end responsiveness or input latency measurement.
4. Retrieved the app's actual private report via `run-as`. All sections reported observations, zero nested collector errors, no omitted input/decoder entries and no dropped events in this short run.
5. Located the app's enabled Refresh diagnostics button in a fresh UI hierarchy and injected a tap at its actual bounds. The saved report advanced from revision 8 to 10, with a new collection timestamp. This proves the control performs a fresh real query; it is not a physical pointer test.
6. Injected HOME, then explicitly relaunched the activity. Reports advanced to revisions 16 and 21 and recorded pause/stop, focus loss, start/resume, top-resumed changes and focus regain. The process retained its event history during this sequence. Activity recreation, process death, suspend, forced write failures and power loss were not exercised.

## Useful observations

- The framework returned **10 input entries**, including reported keyboard, mouse/touchpad and touchscreen source classes. Entries include virtual/system devices; this is not a physical-device count or a behavioral test.
- `MediaCodecList.ALL_CODECS` returned **36 video-decoder entries**, including aliases and special secure entries. The manifest preserves per-MIME reported hardware/low-latency/required-secure counts. No codec was instantiated, no frame was decoded, and no performance claim follows.
- The window was in multi-window mode, with 2073 × 1296 px decor bounds and density 1.625. The reported physical display mode was 2880 × 1800 at approximately 120 Hz; this is a display API report, not measured rendering throughput.
- The attached decor view reported a visible 65 px caption inset and two occupied caption rectangles. At the same observation, `currentWindowMetrics.windowInsets` reported no caption. Preserve the distinction: actual attached-view insets matter for layout, and this observation does not establish every resize/fullscreen/display transition. Do not hard-code the observed height.

The probe is a diagnostic app with actual API calls, bounded private storage and no requested permissions. It implements no Console/Desktop remote feature. Review fixes and remaining scope are in the [code review record](../../../docs/06-validation/2026-10-15-device-probe-review.md).

## Next qualification

Finish the primary Linux route/project inventory and classify unavailable peripherals, then proceed through P0-S2's window/lifecycle/input probes. The user deliberately left USB hardware undecided while preserving full general-purpose implementation scope. Physical USB attach/reset/programming, remote protocols, X11 windows, actual decoding/latency, physical IME/input, external displays and Windows/macOS support remain unqualified.
