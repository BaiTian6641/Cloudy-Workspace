# Interaction, input, and recovery

## Focus ownership

Each window has one focused surface: chrome, terminal pane, stream surface, drawer, or modal. Show focus visibly on that surface/control. Moving pointer hover does not move keyboard focus. Opening a dialog/palette places focus in its first relevant control; dismissing returns it to the exact initiating control or active pane when that control no longer exists.

Tab/Shift+Tab traverse chrome and supporting controls logically. Arrow keys navigate within menus, tabs, and lists. The terminal receives input while focused, including Tab, unless a deliberately chosen local chord applies. Provide a visible “focus toolbar” action and keyboard route out of terminal/stream capture. Do not trap users in an inaccessible canvas/WebView.

## Shortcut precedence

1. OS-reserved shortcuts remain with the OS.
2. A focused modal, IME composition, or menu handles its own valid keys.
3. Deliberately assigned Cloudy shortcuts handle local actions.
4. Remaining input goes to the focused terminal or remote desktop.

Never apply a global Cloudy shortcut to a partly composed IME sequence. Keep context-specific shortcuts visible in the relevant menu and searchable in the palette. Validate on actual Googlebook keyboard layouts; do not promise the app can intercept every system chord.

| Proposed chord | In terminal | In chrome / details |
| --- | --- | --- |
| Ctrl+C | Remote interrupt | Copy where ordinary selection semantics apply |
| Ctrl+Shift+C | Copy terminal selection | Copy supported selection |
| Ctrl+Shift+V | Explicit paste | Paste into active editable field |
| Ctrl+Shift+P | Command palette, if not OS-reserved | Command palette |
| Ctrl+W | Preserve terminal meaning | Close tab only in a documented chrome context |
| Ctrl+J | Preserve literal terminal input | Normal field/menu semantics |
| Escape | Remote input unless local overlay/capture escape policy applies | Close top local overlay |
| Alt+arrow / custom chord | User-configured pane navigation after conflict test | Component-specific navigation |

The release-capture shortcut is configurable and always displayed while capture is active. A local Escape policy must not silently consume every Escape intended for a remote editor. Test a dedicated chord and focus-loss release path before selecting its default.

## Pointer and selection

Use text, resize, default, and remote tool cursors appropriately. Right-click opens a context menu by default; right-click paste is optional and explicit. Shift+drag provides local terminal selection when remote mouse reporting is active. Double/triple click selects word/line with documented word boundaries. Files support additive and range selection.

Pointer capture begins after a user action on a focused native stream view and releases on focus loss, disconnect, user release, or stream termination. Clear remote held modifiers/buttons when leaving capture. Physical trackpad and mouse are separate qualification inputs; a touchscreen trackpad simulation is not evidence for either.

## IME and multiline input

Keep preedit/candidate composition local and visually anchored. Commit the exact text once. Resizing or switching panes must not duplicate or lose a composition. The optional composer is a local buffer; its Send action explains whether it inserts only or inserts and explicitly submits. Attachments upload first, then insert a valid remote reference. Preserve large prompts as files when that is more reliable.

## State changes and network recovery

| Situation | Visible behavior | Prohibited surprise |
| --- | --- | --- |
| Connecting | Target host/environment, cancellable status | No ambiguous global spinner |
| Host key changed | Fingerprint comparison and deliberate resolution | No silent trust replacement |
| Network lost | Keep layout/output, mark transport disconnected, preserve unsent buffer | No replay after reconnect |
| Durable session reattached | Show actual attachment and restored context | No automatic last-command rerun |
| Host rebooted | New boot/session identity and ended/unknown process state | No claim prior process survived |
| Event gap | History gap marker and refreshed snapshot | No fabricated continuous history |
| USB ownership unknown | Last-confirmed owner + reconciliation action | No reassignment based on timer alone |
| File write conflict | Both versions and commit choices | No silent overwrite |

## Confirmation and reversibility

Ordinary navigation, viewing details, copying text, and opening drawers require no confirmation. Confirm when consequence is destructive or crosses trust/ownership boundaries: changed host key, terminate remote session, discard uncommitted file edits, protected device release, and broad permission enrollment. Explain the specific consequence with host/device/project context. Avoid generic “Are you sure?” copy.

## Alerts and sound

Terminal bell is a configurable alert, not a trustworthy agent-status parser. Structured job events may show explicit waiting/completed states only when the adapter knows the current job/request ID. Prefer quiet badges and in-app activity; user-selectable notification channels handle urgent device failure and job completion. Never generate a burst of fresh notifications from expired events after reconnect.
