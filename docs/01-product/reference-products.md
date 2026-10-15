# Reference-product investigation

**Method:** inspect vendor documentation for interaction patterns; do not infer implementation rights, API availability, or Android parity from a screenshot. These are product references, not dependencies. Reviewed 2026-10-14.

## Termius

Termius presents host groups, saved connections, a keychain/known-hosts area, workspace restoration, SFTP, snippets, and forwarding in one navigable environment. Use its [official product overview](https://termius.com/) and [documentation index](https://docs.termius.com/) when examining exact current behavior. Feature availability can vary by platform and plan; Cloudy does not inherit those claims.

**Patterns to adopt:** quick host selection; stable host/environment identity; saved arrangements; first-class key/trust management; compact forwarding/transfer controls.

**Cloudy-specific choices:** ordinary SSH without a suite account; independent app keys; no automatic snippet execution; local-only non-secret profile import; agent runtimes remain on the chosen host. A remembered layout restores views and attachments, never the last command.

## MobaXterm

MobaXterm documents a Windows application combining terminals with remote-session tools, an embedded X server, SSH-connected graphical SFTP browsing, and a tunnel manager. Its [official manual](https://mobaxterm.mobatek.net/documentation.html) is the reference for those capabilities.

**Patterns to adopt:** a file pane tied to the current connection; immediate transfer visibility; explicit saved-session settings; graphical tunnel management; proximity between terminal and remote files.

**Cloudy-specific choices:** do not port its Windows/X11 assumptions to Android/Wayland; keep Desktop video distinct from X11 forwarding; keep USB ownership outside the file browser; avoid a mandatory monolithic shell for all work.

## Comparison and scope

| Pattern | Cloudy Console | Cloudy Desktop | Stage |
| --- | --- | --- | --- |
| Host/session organization | Hosts, tags, workspaces, environments | Same non-secret identity vocabulary | P1-S1, P2-S1, P3-S1 |
| Split terminals | Primary content | Open selected project in Console | P2-S3 |
| Contextual SFTP | Required session-linked browser, bidirectional drop, staged editing and queue | Shared file/transfer model; cross-root/provider orchestration | P2-S4/S7, P3-S3, P5-S3 |
| Port-forward manager | Loopback listeners, owner, lifetime, close | Open preview via Console handoff if installed | P2-S4 |
| Saved workspace | PTY layout and attach policy | Host/project/desktop/device preferences | P2-S3, P3-S1 |
| Remote graphical session | X11 app windows or explicit Desktop handoff | Native RDP/VNC and high-performance stream lane | P2-S6, P3-S2/S7/S8, P5-S2 |
| Serial / hardware tools | Optional future direct serial adapter | Enrolled USB plug-and-play and protected provider/consumer ownership | P0-S5, P3-S4/S6 |
| X11 server | Required separate native top-level windows and owned dialogs | Distinct full desktop/protocol workloads | P0-S7, P2-S6 |
| Performance references | Terminal/X11 responsiveness measured separately | Moonlight/Sunshine and Parsec comparisons; GeForce NOW contextual experience reference | P0-S4/S6, P5-S2 |
| Synchronized vault / team sharing | Deferred | Deferred | Separate trust and product decision |

Use [Rule 0](../00-project/platform-rule-zero.md) when adapting references: desktop workflow quality within Android's app model. Parsec/GeForce NOW references do not establish embedding rights or an automation API. Validate input, native windows and lifecycle on Googlebook; the [revision review](../00-project/revision-01-integrated-workflows.md) records the resulting choices.
