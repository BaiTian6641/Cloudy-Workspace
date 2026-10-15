# Cloudy Console product specification

**Release:** R1, with optional structured integrations in R3. **Primary job:** enter a trustworthy remote environment quickly and stay productive through long terminal and agent sessions.

**Target:** Googlebook desktop/laptop under [Rule 0](../00-project/platform-rule-zero.md). The app is an Android package with native desktop input/windows. Phone/tablet layouts are outside scope. A narrow tiled desktop window still keeps every session and file action reachable.

## Information architecture

The navigation pane contains workspaces, hosts, environments, and saved layouts. A workspace selects a host/environment and one or more registered project directories. Host identity remains visible even when the pane collapses. The main area is tabs with a split tree. A tab represents a work context; each leaf is one PTY. An adjacent SFTP pane can follow the selected session or stay visibly pinned. Search, forwards, transfers and activity have independent focus. SSH-forwarded X11 top-level applications open in separate native desktop windows, with a focusable window list in Console.

Do not merge host, environment, workspace, and session into one overloaded name. For example: `MS-02Ultra / WSL Ubuntu / FPGA controller / build`. Display the OS/environment when path interpretation or persistence differs.

## Requirements

| ID | Capability | Required behavior | Release / stage |
| --- | --- | --- | --- |
| C01 | Host profiles | Hostname/address, port, username, environment, tags, jump chain, credential reference; no secret export | R1 / P2-S1 |
| C02 | Trust | Show key algorithm/fingerprint on first use; explain changed keys and stop connection pending deliberate resolution | R1 / P2-S1 |
| C03 | Authentication | Keys and agent/library-supported methods selected in P0; password/keyboard-interactive only with native prompt handling | R1 / P2-S1 |
| C04 | PTY | Negotiated TERM, exact byte stream, resize, alternate screen, bracketed paste, UTF-8 and bounded backpressure | R1 / P2-S2 |
| C05 | Input | Desktop keyboard/mouse/trackpad input, Ctrl+C terminal semantics, Ctrl+Shift+C/V copy/paste, direct selection, discoverable context/Edit menus and native field shortcuts; system IME and visible focus. Follow the [desktop interaction contract](../04-design/desktop-terminal-interaction-contract.md); no touch-only prerequisite or paste submission outside configured policy | R1 / P2-S2 |
| C06 | Layout | Tabs, vertical/horizontal splits, pane focus, zoom active pane, saved layout, independent windows | R1 / P2-S3 |
| C07 | Persistence | Attach to named tmux sessions where configured; label ordinary Windows SSH non-persistent; no command replay | R1 / P2-S3 |
| C08 | SFTP attachments | Pick/drop/paste file or image, choose host/project destination, verify upload, insert appropriate remote path | R1 / P2-S4 |
| C09 | Local forwards | Explicit localhost bind, destination, owner session, status, close control; browser previews open outside privileged renderer | R1 / P2-S4 |
| C10 | Agent CLI support | Ordinary PTY support for qualified CLIs and generic shell; no provider-specific runtime dependency | R1 / P2-S5 |
| C11 | Composer | Optional local multiline buffer with explicit insert/send behavior and per-tool newline policy | R1 / P2-S5 |
| C12 | Structured status | Optional running/waiting/completed/failed events only from supported current integrations | R3 / P4-S4 |
| C13 | Session search | Local bounded scrollback search, match count, next/previous; transcript logging off by default | R1 / P2-S3 |
| C14 | Jump hosts | Verified trust at each hop, no silent authentication fallback; close dependent channels cleanly | R1 / P2-S1 |
| C15 | Import/export | Preview supported SSH-config fields; import non-secrets only; identify unsupported directives | R1 follow-up / P2-S5 |
| C16 | X11 forwarding | Standard SSH X11 request/channels, remote xauth/prerequisites, per-context authentication/isolation, explicit trusted-mode policy | R1 / P0-S7, P2-S6 |
| C17 | Separate X11 app windows | Native top-level desktop windows, owned dialogs, focus/IME/resize/close behavior and visible host identity; no full-X-desktop substitute | R1 / P2-S6 |
| C18 | Integrated SFTP browser | Session-linked list/breadcrumbs, browse/mkdir/rename/download/upload/staged edit, selection and persistent transfer status | R1 / P2-S7 |
| C19 | File drag-and-drop | Local app→remote upload, remote→local/downloaded URI export, multi-file/directory handling and terminal reference insertion after commit | R1 / P2-S7 |
| C20 | Directory context | Explicit project/path default and pin/follow-session state; optional verified shell-directory signal, no prompt scraping | R1 / P2-S7 |

## Session states

`Idle → Connecting → Verifying host → Authenticating → Connected → Reconnecting / Closed / Failed`.

PTY attachment has its own state: `new / attached / detached / ended / unknown`. A connected SSH transport does not prove the previous process survives. During reconnect, local text waiting to be sent remains visibly unsent; the user decides whether to insert it into the reattached session. Never replay keystrokes, submit keys, approvals, or the last shell command.

**Detach** leaves an available durable session running. **Disconnect** closes the network connection. **Close pane** removes a view and explains any session consequence. **Terminate session** sends an intentional remote operation. **Stop job** belongs to a managed job integration and is not inferred from a terminal's screen.

## Input and output rules

IME composition remains local until commit, with candidate windows correctly anchored after resize and split changes. Do not synthesize one key event per composed character. Test combining marks, wide CJK cells, emoji, line wrapping, password prompts, and alternate-screen editors.

Terminal escape handling is limited to the pinned renderer's documented features. OSC clipboard writes require a user policy per host/session; clipboard reads are not implicitly granted. Render filenames, output, titles, and links as untrusted data. Hyperlinks need an explicit open action and an external browser. Reference [xterm sequence support](https://xtermjs.org/docs/api/vtfeatures/) and [embedding security](https://xtermjs.org/docs/guides/security/).

Output queues are bounded with real backpressure and diagnostics. A full queue must not silently discard data. If the application cannot preserve a supported output rate, surface the limit and preserve recoverable state. Scrollback and transcript storage have separate size limits and policies.

## Persistence by environment

| Environment | Baseline | Honest limitation |
| --- | --- | --- |
| Linux / macOS with tmux | Attach to registered named session | Host reboot does not preserve the process |
| Windows PowerShell OpenSSH | Ordinary SSH PTY | Network loss can terminate the session |
| WSL | Explicit distro/environment and tmux | Windows and WSL paths/identities differ |
| Managed task | Agent-owned job ID/log/artifact model | Not an arbitrary interactive shell |
| Googlebook local Linux VM | Deferred capability | Public attach/control contract remains unknown |

## Acceptance

Pass Q-C01 through Q-C10 in [test cases](../06-validation/test-cases.md). A daily-use release includes Desktop absent and the custom host agent disabled, real keyboard/trackpad/IME, an agent TUI, two separate X11 app windows and owned dialogs, session-linked file browsing/editing, bidirectional drops, upload/download integrity, a browser preview and client/network interruption. Test versions and limitations become part of the support matrix. See the [X11 design](../02-architecture/x11-windows.md) and [Console files design](../02-architecture/console-files.md).

GUI lifetime is separate from tmux lifetime. A broken X connection can end the graphical app even when its shell survives. Unsupported X extensions, GPU/Wayland/audio or GUI file-drop bridges need explicit capability labels. Shared SFTP source does not create shared app credentials.

P2-S5 is the independent Console release gate and includes [app-scoped release checks](../05-delivery/release-gates.md). Desktop readiness and optional host integration do not hold R1. Remote editing follows the user's [per-save overwrite policy](../02-architecture/file-publication.md): without guaranteed conditional replacement, ask for each attempted overwrite and retain local staging when canceled or uncertain.
