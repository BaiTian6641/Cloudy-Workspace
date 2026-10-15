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
| Contextual SFTP | Lightweight attachments and optional file drawer | Full browser, conflicts, provider integration | P2-S4, P3-S3, P5-S3 |
| Port-forward manager | Loopback listeners, owner, lifetime, close | Open preview via Console handoff if installed | P2-S4 |
| Saved workspace | PTY layout and attach policy | Host/project/desktop/device preferences | P2-S3, P3-S1 |
| Remote graphical session | Context handoff | Explicit native backend | P3-S2, P5-S2 |
| Serial / hardware tools | Future qualified adapter | Provider/consumer ownership | P0-S5, P3-S4 |
| X11 server | Not in v1 | Not a substitute for Wayland desktop streaming | Deferred |
| Synchronized vault / team sharing | Deferred | Deferred | Separate trust and product decision |

Before adding another reference product, state the concrete user problem it resolves. More protocol icons do not by themselves improve the two core workflows. Validate the interaction on actual Googlebook input and lifecycle behavior rather than copying the reference layout.
