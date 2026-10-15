# Current execution state

**Updated:** 2026-10-15, America/Chicago. **Goal:** active in Codex; implement and qualify the approved P0–P6 scope. **Current stage:** P0-S1, inventory and reproducible qualification setup. **Application state:** real native diagnostic APK installed and exercised; no Console, Desktop or host implementation yet.

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
- This host reports Ubuntu 26.04.1 LTS and a Wayland session. No TCP listener was found on port 22 in the initial check; a ready SSH server is not established.
- Three official Android skills were installed from a pinned revision, with hashes checked and upstream licenses retained. Use the [skill register](../07-reference/development-skills.md) and project overrides.
- Python AST/Ruff checks and nine planning regressions pass. The real [probe](../../qualification/probes/device-baseline/README.md) compiles, passes Android Lint and its three JVM tests. Its selected [toolchain](../03-platform/googlebook/probe-toolchain.md) has a verified wrapper, dependency locks and checksum metadata.
- [Device run](../../qualification/results/P0-S1/2026-10-15-device-probe.md): actual APK installation/cold launch, API report export, Refresh button and ADB HOME/return passed on the identified build. The framework reported ten input entries and 36 video-decoder entries, with no collector errors. Counts include system/virtual/alias/special entries and imply no physical input or decoding performance qualification.
- [Primary Linux host prerequisites](../03-platform/linux-primary-host-qualification.md) identify KDE Wayland/Plasma 6.5.5 and candidate existing-session RDP/VNC/stream routes; servers and real protocol support remain unqualified.
- Git remote access succeeds with a command-local SSH config override for a host SSH configuration permission error. Host-key checking remains enabled. Check the latest history and remote SHA before assuming a push succeeded.
- Reviewed setup commit `ebb827861b4970b3695a929da4dd8e8674791efd` was pushed to `origin/codex/project-foundation`, and the remote SHA was verified. [GitHub Actions run 38685129757](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38685129757) passed every repository check for that exact commit. Subsequent documentation commits do not replace that recorded code-check identity.

## Next concrete work

1. Finish clean-cache/hosted Android CI verification. Initial pushed source `cf6630b6ff98ea8131ced1ef7ebaef36a33494af` passed repository CI, but its Android job failed; a fresh-cache local run exposed missing transitive metadata checksums. Five metadata checksums were checked against canonical artifacts and added. A clean checkout/cache build then passed all 49 tasks with strict verification; hosted confirmation for the correction is next.
2. Complete remaining P0-S1 primary Linux route/project inventory and record unavailable peripherals. The USB board is deliberately undecided; no repeat selection is needed until hardware becomes available. Preserve full generic implementation scope and physical qualification limits.
3. Prepare the selected Linux SSH/SFTP and desktop backend prerequisites in bounded experiments. Keep later protocol acceptance separate from inventory/setup; Windows-specific results await a target.
4. Proceed to P0-S2 only when its prerequisites are met; qualify API/window/lifecycle/input behavior before activating product implementation stages.

## Evidence boundaries

All feature capability outcomes remain pending. The diagnostic run adds install/launch, real reported API values and a bounded ADB-driven lifecycle smoke check. No actual video decoding, remote protocol, X11 broker, USB forwarding, latency, full physical desktop interaction or release support has passed. Raw device serials and local diagnostics remain in ignored private inventory/evidence; source-controlled records contain sanitized observations.

Read [history](../00-project/history.md) and the relevant phase before resuming after a context change. Do not advance a stage from this prose alone; reconcile its backlog status and actual acceptance evidence.
