# Shared design system

[tokens.json](tokens.json) is the canonical proposed Cloudy palette, typography, spacing, shape, motion, density, and terminal color data. Read the [design handbook](../../docs/04-design/README.md) for roles and component behavior.

`python3 tools/generate_design_tokens.py` generates the specimen's CSS variables. `--check` reports drift without editing. P1-S2 adds native generated values and shared semantic components; no Compose implementation exists yet.
