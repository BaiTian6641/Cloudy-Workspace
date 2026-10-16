# Current execution state

**Updated:** 2026-10-15, America/Chicago. **Authority:** current user instructions, including “no playback” and a comprehensive review before continuing. **Goal:** active P0–P6 implementation. **This file describes the latest checkpoint; historical build/run documents are evidence, not a queue of commands to repeat.** Read the [comprehensive streaming review](../06-validation/2026-10-15-stream-comprehensive-review.md) with this file.

## Current conclusion and next action

**Streaming playback is not working for the user. Visual playback acceptance is FAILED.** The actual 0.1.9 attempt reached the native engine and hardware-reported decoder, but the user's final report is “no playback.” Its 1,517 rendered-frame callbacks do not establish visible or moving video. Do not promote CAP-STREAM, infer a successful viewing session, or ask the user to repeat an unchanged test from those counters.

The requested source/evidence/document review is complete; SR-01 through SR-06 remain open code/observation findings and SR-07 documentation continuity is corrected. Resolve the actionable findings in a separate reviewed increment, then plan one clearly identified fresh attempt. No streaming test is currently waiting for user input. All temporary streaming host/canvas processes and seven ports are closed, its device profile is removed and the diagnostic is stopped. Old prompt questions, process references and fixture scripts are expired context; verify actual state before any new operation. General authorization to implement and perform scoped tests remains in force; this review order is not a newly invented approval requirement.

## Stage and product status

P0-S1 setup and P0-S2 initial investigation are complete. P0-S3 SSH/terminal, P0-S4 desktop engines and P0-S5 USB investigation remain in progress. The other 35 stages are planned. CAP-PLATFORM is a bounded experiment, seven capabilities are pending and none is supported. Stage counts are not a completion percentage. Console, Desktop and the custom host product are not implemented; real diagnostic applications exist.

| Lane | Latest installed or built checkpoint | Accepted evidence and remaining boundary |
| --- | --- | --- |
| Platform | HP Googlebook 14, Android 17/API 37, arm64, build CL3B.260622.276.R2 | [P0-S2 decision](../03-platform/googlebook/p0-s2-platform-decision.md): bounded native windows, restoration, font scaling, engagement and finite transfers; continuous-session ownership remains unresolved |
| SSH/SFTP and terminal | Terminal diagnostic 0.7.2/code11 installed; latest owned fixture closed | [Actual rapid/paced bytes and native resize](../../qualification/results/P0-S3/2026-10-15-terminal-upstream-device.md) pass in scope; physical keyboard/IME, selection/copy/paste, accessibility, two synthetic editing limits and continuous ownership remain open |
| RDP | 0.1.6/code7 installed and stopped | [0.1.5 physical cursor result](../../qualification/results/P0-S4/2026-10-15-rdp-cursor-repair.md): user confirms host trail/cursor movement and release on isolated GNOME RDP. [0.1.6 rendering sample](../../qualification/results/P0-S4/2026-10-15-rdp-rendering.md) shows no measured latency improvement. KRdp relative movement failure, input-device subtype, actual 3D, vault, broader hosts and performance remain open |
| VNC | 0.1.2/code3 installed and stopped; 0.1.3/code4 built, reviewed, **not installed** | [Real protected session](../../qualification/results/P0-S4/2026-10-15-vnc-first-session.md): frames, pointer and disconnect work; user reports keys work and about one minute of blank startup. Controlled host key count is zero and startup was not measured. [Timing/F8/focus build](../../qualification/results/P0-S4/2026-10-15-vnc-timing-build.md) passes 10 JVM tests/zero Lint; its device result is pending |
| Streaming | 0.1.9/code10 installed and stopped | [Actual decoder session](../../qualification/results/P0-S4/2026-10-15-stream-decoder-session.md): same-key credential authorization, real catalog/launch, native start and decoder callbacks; **visual playback failed**. Audio capture disabled, remote input absent, performance unmeasured. [Compatibility build](../../qualification/results/P0-S4/2026-10-15-stream-compatibility-build.md) passes 42 JVM tests/zero owned Lint; these do not change the visual verdict |
| USB | Native diagnostic 0.1.0 load/enumeration only | [Actual preflight](../../qualification/results/P0-S5/2026-10-15-usb-native-preflight.md) sees two unselected devices. Granted access, export/import, reset/programming and plug-and-play remain unqualified; representative hardware is deliberately undecided |
| Separate X11 windows, integrated files and product shells | Planned | Retain full Console separate-window X11 and session-linked SFTP/drop, full Desktop native RDP/VNC/stream/USB and independent release gates |

## Streaming facts that must survive context recovery

- The earlier 0.1.8 native-start failure remains in the [first catalog result](../../qualification/results/P0-S4/2026-10-15-stream-first-catalog.md). Official Sunshine reports `7.1.431.-1`; 0.1.9 narrowly admits that documented sentinel. It is an implemented compatibility correction, not work to repeat and not a visible-playback repair.
- Actual 0.1.9 PLAY: one type-1 screen-lock authorization on the same TEE identity; native start result 0; 1280 × 720 H.264; `c2.qti.avc.decoder.low_latency`; 1,524 decode-unit submissions, 1,517 released outputs and 1,517 rendered callbacks. The original approximately 30-second authorization ended the finite run. Worker/controller returned, native cleanup completed and matching Surface/Texture retired. No live viewport or texture-update observation was captured.
- Three new catalog reads completed 160/138/88 ms after their credential callbacks. These are same-clock callback-to-operation durations, not network-only latency. Two later reads failed CLIENT_TLS/IOException in the fixture-shutdown period; do not infer a key regression or silently discard them.
- The user first reported authentication with no moving video, later “no, no password pop0up, no changes,” then explicitly “no playback.” Journal evidence proves an authorized PLAY occurred, but does not identify which later click those replies describe. The final negative visual result remains controlling. Do not invent a prompt the user saw or explain the result away as a late click.
- The diagnostic admits only one native playback per process. After retirement its preview is removed and a process restart is required; this is a **diagnostic limitation**, not the desired Desktop reconnect behavior. Current UI clarity is a review finding. Never bypass the native guard, replay a launch or reset authorization to compensate.
- The preview was fully on-screen before the prompt, with a distorted 2007 × 390 layout for 16:9 content. Its live visibility was not recorded. The final absent texture is expected cleanup, not proof of an off-screen live preview. Scrolling/focus and surface presentation are hypotheses to investigate, not established causes.
- The 30-second combined authorization/run limit is a finite P0 diagnostic policy, not a product session duration or a latency target. Audio-disabled output-wide capture and no input forwarding were explicit fixture limits. Rendering callbacks, hardware metadata and a fullscreen host canvas do not prove visible pixels, hardware performance or capture privacy.

## Preservation and fixture recovery

Raw evidence, credentials, inventories, UI trees and host addresses remain ignored. Never publish them or stage downloaded implementations. Public reports bind exact hashes and sanitized observations.

| Lane | Preservation and operational constraints |
| --- | --- |
| Streaming | `qualification/results/private/native-stream/2026-10-15-playback-02/cleanup.json` and `final-journal-manifest.json` bind **22** final records: 12 identity, one native load, one pairing, six catalog, two playback. All **15** prior records remain byte-identical; all network records are closed. `.cache/native-stream-host/media-02/result.json` records host/canvas exit 0, seven released ports, permission cleanup and unchanged host identity. Keep retained enrollment; do not regenerate/re-pair keys. Old profile/fixture initializers are not restart commands |
| RDP | 15 of 16 diagnostic journal slots used; preserve/archive before exceeding the bound. Accepted performance evidence and both APKs remain under private native-rdp-performance/run-01, with session run-12/run-13 evidence. Existing failures remain part of history |
| VNC | Seven closed records and 0.1.2 evidence preserved; no live fixture, route or profile. Do not install 0.1.3 and call its build a timing result |
| SSH/terminal | Preserve original SSH/transfer archives and current terminal two-attempt/14-event batch, including the failed rapid-input trace. Follow linked preservation reports before changing any archive. No blanket clearing, slot expansion or fixture reuse |

Before new device/host work, inspect the actual package/version, app process, journal terminal state and existing private fixture receipt. Only the leader controls shared ADB, listeners, routes, host capture and Git. A completed historical cleanup is not proof of today's live state after unrelated user activity.

## Persistent decisions and working roles

- **Rule 0:** Googlebook desktop/laptop applications using Android package/lifecycle/permission APIs. Phone/tablet optimization is outside scope; narrow desktop windows, IME, accessibility and font scaling remain required. ChromeOS/Crostini and roadmap VM APIs are not assumed.
- Console and Desktop remain independently useful, with separate packages, credentials and release gates. Ordinary SSH requires no custom host agent. Full scope includes separate X11 windows, SFTP/drop, standard RDP/VNC and enrolled USB forwarding.
- Ask on every remote save when conditional replacement is unavailable; independent app releases plus a suite milestone. Native streaming adoption is resolved in P0/P1 independently of optional host adapters. Consult [approved decisions](../00-project/revision-02-final-review.md) for identifiers and exact wording.
- D14/D15: desktop keyboard/mouse/context menus/copy-paste, with Termius as the SSH/SFTP UX reference. Standard RDP targets Linux, Windows and macOS per actual server; true negotiated relative input and actual 3D require their own evidence. Temporary diagnostic credential provisioning is not the product vault.
- D16: open-source Desktop with compatible source/notices/build obligations; Console terms remain independent. Do not re-ask this decision or infer a blanket repository license.
- Primary available host is Ubuntu 26.04/Wayland. Windows is desired, with Parsec acceptable temporarily; Windows/macOS remain unqualified. USB hardware is deliberately undecided; retain general software scope and do not repeatedly ask for absent hardware.
- Parent Codex remains integration leader. Use **GPT6.1-Sol directly in Codex collaboration**; **OMP uses Kimi K3 only**. GLM remains deferred. Workers receive bounded context/file ownership and the leader reviews their results. Compression does not change this decision.
- No placeholders. Use official/upstream sources first, proportional [D12 library reuse](library-reuse-policy.md), scoped compiler/AST/Lint/tests, independent review and timely non-force Git commits/pushes. See [engineering workflow](engineering-workflow.md).

## Evidence, checks and continuation order

The reviewed 0.1.9 source/build checkpoint passes Python AST (28 files), full Ruff, 102 repository tests, Android compilation, 42 stream JVM tests and zero owned Lint findings. The 27 inactive upstream baseline Lint errors remain explicitly scoped in the build report. VNC 0.1.3 separately passes 10 JVM tests/zero Lint. Native bytes and unchanged library evidence carry forward under D12. Documentation-only corrections do not require another APK build or device run.

The previous pushed commit `0f42e28da8eb9b6386f3ac60f125d60fd87d9d4c` has five hosted CI passes. A new commit needs its own exact-SHA status; prior CI is not inherited. Read Git state before claiming anything is committed or pushed.

1. Follow the completed [comprehensive review](../06-validation/2026-10-15-stream-comprehensive-review.md), resolving its recorded open findings in the next streaming increment. Improve visible state/attempt correlation and presentation observability without changing accepted security/native ownership merely to obtain a pass.
2. After reviewed fixes, use one fresh finite fixture and one user question at a time. Report build/attempt/readiness/expiry explicitly; close old questions in prose before moving stages. Authentication confirmation alone never satisfies visible playback. Do not start another unchanged attempt on the assumption that the user was late.
3. Independently continue VNC startup attribution and physical terminal cases when the user is ready; keep their established evidence. RDP latency work follows its [measurement entry](../03-platform/googlebook/rdp-performance-entry.md), not repeated synthetic status optimization.
4. Update this current-state record and append to [history](../00-project/history.md) at the next reviewed milestone. Preserve old reports unchanged as historical evidence; if a source/artifact changes, issue a new build/result instead of silently repinning old evidence.
