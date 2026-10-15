# Review of the supplied plan

**Review date:** 2026-10-14, America/Chicago. **Input:** [immutable original](../intake/GoogleBook_Self_Cloud_Workspace_Plan.md). **Authority:** the current user request supersedes the original Carbon-only UX direction. All implementation choices below remain proposed unless marked as a current repository decision.

**Current revision:** [Revision 01](revision-01-integrated-workflows.md) adds the user's X11, SFTP/drop, native RDP/VNC, USB plug-and-play requirements and [Rule 0](platform-rule-zero.md). Read it with this intake review; early external-backend milestones do not complete the revised integrated scope.

## Retain

The two independently useful apps, host-local compute, ordinary SSH, optional host agent, direct media/USB/file paths, established backends, conservative file writes, explicit USB ownership, and evidence-based release gates are sound foundations. Preserve the original distinctions among terminal persistence, agent conversation resume, and host reboot survival. Keep bandwidth assumptions separate from measurements.

## Corrections and changes

| Topic | Review finding | Resulting plan |
| --- | --- | --- |
| Product naming | User now describes integrated Console and Desktop experiences | Use Cloudy Console / Cloudy Desktop; retain the functional split |
| Platform priority | Googlebook is an Android-based desktop/laptop ecosystem | Two Android packages with desktop windows/input/workflows; no phone/tablet optimization workstream |
| Console integration | Attachments alone do not supply MobaXterm-like files or separate graphical app windows | Required session-linked SFTP/drop and SSH X11 top-level windows; P0-S7/P2-S6/S7 |
| Desktop protocol coverage | External streaming alone misses existing RDP/VNC servers | First-class native RDP/VNC lanes in P3; independent of the custom host agent |
| USB convenience | Explicit ownership needs a routine hotplug path | Enrolled idle-device auto-attach to one approved host, backend reconciliation and protected-operation rules |
| Visual language | Original §7 assumes Carbon React throughout | Specify an original Carbon × M3 system and propose a native Compose shell; qualify the renderer independently |
| Native shell decision | Visual inspiration does not require running either reference framework everywhere | [ADR 0002](../adr/0002-native-shell-and-terminal-surface.md) compares Compose, WebView, and a hybrid |
| Googlebook Linux | Google's Oct 12 preview puts USB passthrough, hardware acceleration, and independent VM persistence on its roadmap | Track as optional capabilities; see [platform limits](../03-platform/googlebook/open-questions.md) and [official announcement](https://developer.android.com/blog/posts/googlebook-linux-terminal-preview-vm) |
| Linux VM integration | Platform availability does not establish a public third-party attach/control API | Use remote SSH as baseline; no VM private APIs |
| Desktop APIs | Guides contain APIs with different OS levels and experimental library examples | Track exact symbols/availability separately from prose examples |
| Handoff | Google's Continue On is phone-to-Googlebook state continuity | Do not substitute it for Console↔Desktop IPC or process migration |
| ADB | Debugging transport depends on model/port | Follow [Google's model table](https://developer.android.com/develop/adaptive-apps/guides/googlebook/adb-debugging); HP 14 USB debugging is documented on the left port |
| SSH engine | “Native SSH” is a boundary, not a selected implementation | P0 compares maintained JVM and native options against Android requirements |
| Streaming | An app's existence does not establish a supported control/deep-link API | P0 proves external handoff; manual launch is an honest fallback |
| Sunshine wording | The original blanket macOS experimental label is not the current getting-started wording | Pin the selected release and qualify capture/input/permissions; do not repeat a stale label as current support evidence |
| Notifications | macOS arbitrary notification capture is not established | Own-job events first; capability flags for every adapter |
| Timeline | Original 18–31 person-week sum excludes newly detailed UX/pinning work | Use stage estimates and explicit contingency; do not present person-weeks as elapsed weeks |
| Licensing | Original embeds possible GPL and proprietary dependencies | Decide source/distribution model before embedding, with a stage gate |

## Assumptions carried forward

The user controls the hosts and tailnet. A physical HP Googlebook, the primary programming probe, and a supported board will be available for P0. External backend apps are acceptable for the first Desktop release. Remote environments support SSH; Windows durable-session behavior may differ from tmux on Linux/macOS. None of these assumptions is reported as verified.

## Open decisions with bounded defaults

Default to Kotlin/Compose for the Android desktop shell, a separately bundled xterm.js experiment for the terminal, SFTP for remote files, FreeRDP/maintained RFB engine investigations for native clients, and external high-performance streaming for the early R2 lane. Qualify a local X server and native window broker before adopting an engine. Freeze versions after actual toolchain/device tests. The user has not selected a distribution license, exact probe inventory, host OS matrix, or local VM requirement; these have explicit owners and stages in [risks](decisions-risks.md).

## What this repository delivers

Detailed Markdown specifications, stage-level delivery plans, a source/API tracker, architecture decisions, module ownership, contract examples, tokens, an offline visual specimen, qualification templates, a reusable research skill, and documentation checks. It does not mark P0 experiments or application implementation as completed.
