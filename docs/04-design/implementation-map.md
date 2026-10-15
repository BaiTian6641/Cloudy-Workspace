# Design implementation mapping

## Token pipeline

`shared/design-system/tokens.json` is the canonical proposed palette/metric source. The specimen stylesheet's generated token block comes from it. During P1, add a small generator for Compose `ColorScheme`, typography/spacing/shape values, and renderer theme values, with generated-file checks. Do not maintain independent hand-edited palettes in two apps.

The token JSON includes paired contrast checks and fixed terminal ANSI colors. Native UI may use Material components with Cloudy wrappers; any Carbon reference token is mapped by role rather than imported as a global override. The [Compose M3 guide](https://developer.android.com/develop/ui/compose/designsystems/material3) is the implementation starting point, with exact versions selected at P1.

## Surface map

| Product surface | Proposed implementation | Design responsibility |
| --- | --- | --- |
| Window shell/navigation | Compose + selected adaptive/window libraries | Current-window layout, focus, insets and instance state |
| Buttons/fields/dialogs/menus | M3 semantic components wrapped by Cloudy contracts | Roles, shape, density, keyboard and accessibility |
| Dense tables/tree | Native components/custom layout after semantics review | Alignment, selection, virtualization, readable metadata |
| Terminal | Isolated local renderer experiment in native host | ANSI palette, exact input/selection/resize; no general web browsing |
| Stream | Native decoder/surface/backend | Input capture and media lifecycle, no React/JSON frame path |
| Files/attachments | System picker, URI grants, native transfer UI | Clear source/destination and commit/conflict states |
| Device status | Native ownership state machine view | Authority/unknown/protection semantics |
| Diagnostics | Native bounded structured views/export | Exact error, context, redaction and measurement provenance |

## What the specimen demonstrates

The offline specimen is original HTML/CSS/JavaScript. It is a review surface for the visual language and limited local interactions. It provides app selection, appearance/density changes, simulated connection states, inspector visibility, and a keyboard-operated command palette. All hosts, files, output, traffic, and progress are synthetic. The illustrative desktop scene is not a vendor screenshot or a working stream.

It deliberately does not establish Android IME, SSH, screen-reader terminal support, native video, pointer capture, cross-app IPC, backend launch APIs, or USB compatibility. The screen specification and stage gates remain the authority for those behaviors.

## P1 implementation sequence

1. Lock schema/version/names for tokens and map them to native roles.
2. Implement shared shell and shared controls with semantic states and keyboard behavior.
3. Add current-window resize/caption/instance probes with guarded platform calls.
4. Port S02 and S06 as native skeletons using synthetic models first.
5. Review text scale, light/dark, narrow windows, focus, IME handoff, and density on hardware.
6. Bind real domain state only after its authority/failure semantics exist.

Keep reusable component APIs small. A button takes a semantic role/action/state, not arbitrary color overrides. File/USB/session components render typed states with required identity/context. Product copy should not expose implementation details unless they help the user recover or choose meaningfully.
