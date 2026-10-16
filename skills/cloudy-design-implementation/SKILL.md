---
name: cloudy-design-implementation
description: Implement and visually review Cloudy client or host UI against canonical design tokens, desktop interaction contracts and actual rendered evidence. Use for UI changes and worker visual review.
---

# Cloudy design implementation

Read the [worker visual contract](../../docs/04-design/worker-visual-acceptance.md), relevant screen/interaction specification and [current accepted feedback](../../docs/04-design/2026-10-16-external-feedback-review.md#current-disposition-at-520a0b9). Use [tokens.json](../../shared/design-system/tokens.json) and existing components; never create an alternate palette, phone flow or compressed fixed-height theme to fit one screen.

Receive a bounded screen/state packet with source base, owned files, actual actions, viewport/scale constraints, reference images and checks. Preserve OS controls, keyboard/pointer/native IME, meaningful host context and permission/input ownership. For RDP/VNC/streaming use the [remote session contract](../../docs/04-design/remote-session-presentation.md); do not theme the remote application or put video bytes through general UI bridges.

Build and render the actual changed target, inspect pixels, fix relevant defects and capture the final affected states. Label synthetic specimens and native screenshots separately. Record source/build/image hashes, OS, viewport, theme/density/scale and observed state. A still image does not establish keyboard, pointer capture, accessibility speech or engine behavior; pair it with applicable interaction checks.

Kimi K3 and GLM5.3-Flash have bounded OMP image-route evidence, with leader-corrected findings. Include only authorized sanitized images and check actual routing/attachment. Require pixel-anchored findings and explicit uncertainty about hidden content. Text-only answers and uninspected screenshots do not count. Direct Codex Sol/parent reviews results before acceptance; do not treat a confident visual claim as proof.

Return implemented behavior, files, exact checks and captures, accepted deviations and remaining limits. Shared design changes remain parent-owned. This original repository-local skill supports the current Cloudy design; it grants no autonomous device control or external-data transmission authority.
