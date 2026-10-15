# Cloudy design handbook

**Design direction:** Carbon's productive structure with Material Design 3's tonal clarity and adaptive behavior. This is an original Cloudy Workspace proposal, not an official IBM/Google theme or a requirement to combine two component frameworks.

The interface should feel calm at first glance and precise during work: pale mineral surfaces in light appearance, deep blue-green neutrals in dark appearance, restrained teal primary actions, indigo links/focus, readable productive typography, and dense aligned lists. Terminals and streamed content keep their own color semantics.

## Read in order

1. [Principles and reference synthesis](principles.md): what each design system contributes.
2. [Foundations](foundations.md): exact color/type/spacing/shape/motion rules.
3. [Layout](layout.md): windows, pane geometry, density, and responsive behavior.
4. [Components](components.md): anatomy, states, sizing, actions, and accessibility.
5. [Interaction](interaction.md): focus, keyboard, pointer, IME, errors, and reconnect.
6. [Screen specifications](screen-specs.md): both products and important edge states.
7. [Accessibility](accessibility.md): contrast pairs, input access, scaling, and test gates.
8. [Implementation mapping](implementation-map.md): native components, renderer boundary, and token ownership.

Canonical values live in [tokens.json](../../shared/design-system/tokens.json). The [specimen](preview.html) is an offline, interactive design study with synthetic data. It demonstrates Console/Desktop, light/dark, comfortable/compact density, connection states, an inspector, and a command palette. It does not connect to hosts, stream video, or execute commands.

## Concrete visual decisions

| Decision | Rule |
| --- | --- |
| Main workspace | Functional panes and quiet dividers; reserve rounded containers for bounded content, menus, and dialogs |
| Primary action | One filled teal action per task region; use tonal/outline/text treatments for others |
| Information | Host and project first, task content second, diagnostics on demand; a critical device state remains persistent |
| Color | Stable semantic foreground/background pairs; combine status with label and icon/shape |
| Type | Productive sans for UI, mono for terminal/code; short titles, sentence-case labels |
| Density | Preserve readable type; change padding/row height, never shrink all text to fit |
| Motion | Short state transitions; preserve spatial context; no decorative looping |
| Adaptive behavior | Reflow/collapse supporting panes using current window space, not the laptop's total resolution |
| Failure | Explain the specific operation, state, and next action near the affected surface |

Review the specimen on the real Googlebook at its actual scaling before adopting final sizes. Brand/font decisions are proposed; the current specimen uses locally available system fonts and no downloaded assets.

Saved review views: [Desktop light](previews/desktop-light.jpg), [Console dark](previews/console-dark.jpg). See the [planning verification record](../06-validation/planning-review.md) for the exact browser checks and their limits.
