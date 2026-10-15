# Current execution state

**Updated:** 2026-10-15, America/Chicago. **Goal:** active in Codex; implement and qualify the approved P0–P6 scope. **Current stage:** P0-S1 complete; P0-S2 in progress for platform/window/lifecycle qualification. **Application state:** real native diagnostic APK installed and exercised; no Console, Desktop or host implementation yet.

## Decisions to carry forward

- Android-packaged Googlebook desktop/laptop apps; phone/tablet optimization remains outside scope.
- Console and Desktop own independent packages, credentials, permissions and lifecycle; ordinary SSH needs no custom host agent.
- Full Console includes separate X11 application windows and session-linked SFTP/drop. Full Desktop companion includes native RDP/VNC and enrolled USB plug-and-play.
- Ask on every remote overwrite without backend conditional replacement. Do not remember blanket consent or automatically replay ambiguous publication.
- App release gates are independent; the suite has its own integration milestone. Decide native streaming feasibility/distribution in P0/P1, independently of optional host adapters.
- The user selected this Ubuntu 26.04/Wayland workstation as the primary Linux host. Windows is desired; Parsec is an accepted temporary access option. No Windows test host is identified. The user explicitly left the USB board/probe undecided because hardware is limited, while retaining full general-purpose USB implementation scope. Software discovery/enrollment/ownership/reconnect work must be real; physical forwarding/reset/programming support remains unqualified until representative hardware is available.
- Use real implementations, official references, stage reviews, AST/compiler and lint checks, reviewed delegation, timely commits/pushes and documented history. See [engineering workflow](engineering-workflow.md).

## Verified setup so far

- One HP Googlebook 14 is authorized over USB ADB. Its reported OS is Android 17 / API 37, ABI arm64-v8a. Exact non-secret build observations are in the [P0-S1 baseline](../../qualification/results/P0-S1/2026-10-15-usb-baseline.md).
- The primary host reports HP ZBook Ultra G1a 14 inch Mobile Workstation PC, Ubuntu 26.04.1 LTS and KDE Wayland. The [setup closeout](../../qualification/results/P0-S1/2026-10-15-setup-closeout.md) records the private login/fixture selection, local-LAN ICMP success and tailnet shell ICMP failure. No TCP port 22 listener is observed; SSH server readiness and the eventual app route remain unqualified.
- Three official Android skills were installed from a pinned revision, with hashes checked and upstream licenses retained. Use the [skill register](../07-reference/development-skills.md) and project overrides.
- Python AST/Ruff checks and nine planning regressions pass. The real [probe](../../qualification/probes/device-baseline/README.md) compiles, passes Android Lint and its 31 JVM tests after the reviewed P0-S2 engagement increment. Its selected [toolchain](../03-platform/googlebook/probe-toolchain.md) has a verified wrapper, dependency locks and checksum metadata.
- [Device run](../../qualification/results/P0-S1/2026-10-15-device-probe.md): actual APK installation/cold launch, API report export, Refresh button and ADB HOME/return passed on the identified build. The framework reported ten input entries and 36 video-decoder entries, with no collector errors. Counts include system/virtual/alias/special entries and imply no physical input or decoding performance qualification.
- [Primary Linux host prerequisites](../03-platform/linux-primary-host-qualification.md) identify KDE Wayland/Plasma 6.5.5 and candidate existing-session RDP/VNC/stream routes; servers and real protocol support remain unqualified.
- Git remote access succeeds with a command-local SSH config override for a host SSH configuration permission error. Host-key checking remains enabled. Check the latest history and remote SHA before assuming a push succeeded.
- Reviewed setup commit `ebb827861b4970b3695a929da4dd8e8674791efd` was pushed to `origin/codex/project-foundation`, and the remote SHA was verified. [GitHub Actions run 38685129757](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38685129757) passed every repository check for that exact commit. Subsequent documentation commits do not replace that recorded code-check identity.

## Next concrete work

1. Continue P0-S2 from its in-progress backlog state. The [window run](../../qualification/results/P0-S2/2026-10-15-window-probe.md) establishes distinct document tasks, independent state, real same-process Bundle recreation, native maximize/resize/tiling, focus/HOME/return, close isolation and a bounded save-failure/recovery observation. Its [code review](../06-validation/2026-10-15-window-probe-review.md) includes 17 passing JVM tests. The separate [process-restoration run](../../qualification/results/P0-S2/2026-10-15-process-restoration.md) establishes saved task/marker restoration after a verified, package-scoped background-process kill. The larger-font experiment is being integrated as separate evidence; physical input, operation-specific service/timeout experiments, actual engagement callbacks and final CAP-PLATFORM readiness remain separate. The [VM boundary](../03-platform/googlebook/p0-s2-linux-vm-boundary.md) and [operation lifetime research](../03-platform/googlebook/p0-s2-operation-lifetimes.md) retain documented versus qualified distinctions.
2. Install the reviewed 0.3.0 [engagement diagnostic](../03-platform/googlebook/p0-s2-engagement-probe-contract.md) and record E02–E05. Its public API compiles, 31 tests and lint pass, dependencies are locked/verified, and all 11 notice assets are present in the exact APK. See the [source review](../06-validation/2026-10-15-engagement-probe-review.md). The installed 0.2.0 evidence remains tied to its earlier source; do not attribute those results to this new collector.
3. Keep the selected local LAN as the first Linux protocol route. The shell ICMP smoke passed, while the tailnet shell test did not; verify the actual Android app route and VPN policy before diagnosing a remote backend. Do not change system routing or VPN configuration based on package presence alone.
4. Prepare selected Linux SSH/SFTP and desktop backend prerequisites in bounded later experiments, with explicit listener/authentication/trust settings. Windows-specific results await a target.
5. Continue USB API/provider/licensing investigation independently. The board is deliberately undecided; do not ask again until hardware is available. Full generic implementation remains required, and physical attach/reset/programming evidence remains pending.

## Latest reviewed delivery

Window implementation/research checkpoint [13dce23](https://github.com/BaiTian6641/Cloudy-Workspace/commit/13dce23dd4052163f22703752b16438ec81c8306) is pushed on `codex/p0-window-lifecycle`, with local/remote SHA verified. Hosted [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38691345990) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38691346070) both passed for this exact source. Its APK SHA-256 is recorded in the [physical window run](../../qualification/results/P0-S2/2026-10-15-window-probe.md). Later evidence/context commits do not change that source or APK identity. Earlier P0-S1 checks and correction identities remain in [history](../00-project/history.md).

## Evidence boundaries

All feature capability outcomes remain pending. The diagnostic runs add install/launch, actual reported API values and bounded ADB-driven window/lifecycle/save-path evidence. No actual video decoding, remote protocol, X11 broker, USB forwarding, latency, full physical desktop interaction or release support has passed. Raw device serials and local diagnostics remain in ignored private inventory/evidence; source-controlled records contain sanitized observations.

Read [history](../00-project/history.md) and the relevant phase before resuming after a context change. Do not advance a stage from this prose alone; reconcile its backlog status and actual acceptance evidence.
