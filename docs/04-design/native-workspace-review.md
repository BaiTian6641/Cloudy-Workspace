# Native workspace prototype review

**Purpose:** qualify the revised desktop interactions before freezing component geometry and interaction contracts. **Stage:** P1-S2, with engine-backed behavior confirmed in the relevant P2/P3/P5 stages. **State:** specification only; no prototype or hardware result is claimed.

Keep the current Carbon × Material 3 direction and [canonical tokens](../../shared/design-system/tokens.json). Token names and semantic roles can stabilize early. Treat sizes, density, pane constraints and focus behavior as proposed until this review passes on the target Googlebook. A browser specimen or static screenshot does not establish native input, window or permission behavior.

## Representative working scene

Use sanitized synthetic project names in the native shell, then qualified engine probes where available. Show two host contexts, a Console terminal with its adjacent SFTP pane and active transfer, two separate X11 app windows with an owned modal, a native RDP or VNC surface, and a device row undergoing a permission/ownership interruption. Exercise the scenes in a repeatable sequence rather than opening every inspector permanently.

| Review | Required observation | Evidence / associated cases |
| --- | --- | --- |
| Terminal and files | The terminal remains usable; the bound host/path and pin/follow state survive resizing and tab changes; a pending transfer never silently changes destination | Native capture and keyboard/pointer trace; Q-C09/Q-C10 |
| X11 windows and modal | Task switching, native captions, popup placement and owned-dialog focus are correct; a modal cannot send typing to the wrong host/window | Qualified P0 broker capture; Q-C08/Q-P02 |
| RDP/VNC surface | Input capture/release is reachable; local versus remote shortcut and IME ownership is clear; protocol and session context remain visible | Native input trace and release demonstration; Q-D07/Q-D08 |
| Device interruption | Busy, unknown and permission-required states identify the actual host/device and leave other work usable; routine enrolled hotplug is quiet | Permission/ownership scenario capture; Q-D06 |
| File publication | Preparing, saved-copy, replacement, conflict and unknown publication outcomes are distinct; the chosen save policy is visible without implying race-free writes on every server | S13/S08 walkthrough and later transfer evidence; Q-C09/Q-D03/Q-D05 |
| Accessibility and density | Keyboard-only reachability, screen-reader labels, both themes/densities, long/CJK/RTL labels, reduced motion and 100–200% text scaling preserve essential actions | Native semantics/focus evidence; Q-A01 |
| Window constraints | Narrow/short tiled windows, maximize/fullscreen, external-display move and scaling changes preserve useful work and host context | Actual client/display/build identities; Q-P02/Q-P03 |

## Review outcomes

Record each scenario as pass, fail, blocked or inconclusive, with the exact build and evidence paths. Synthetic models can pass layout/state presentation only; mark engine, USB, IME or display behavior untested until its real probe runs. A missing engine does not justify a fake passing native-client result.

P1-S2 freezes only the component/interaction decisions demonstrated by its native shell and available probes. Feature-specific components retain a provisional marker until their implementation acceptance cases pass. Blocking shell defects include obscured system controls, focus traps, unreachable scaled actions, ambiguous operation destinations and loss of essential working area. Record remaining feature-specific defects against their implementing stages.

Revisit a token or component only when the review demonstrates a need. Preserve system captions, native IME, ANSI semantics and the separation between media/protocol bytes and application chrome. The output is a short decision record linking observed problems, accepted changes and deferred engine-dependent checks.
