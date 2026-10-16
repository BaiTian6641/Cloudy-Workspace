# Android local window editor

This library implements the P1-S1 Compose editor base in `io.github.baitian6641.cloudy.design`. It has no application/domain dependency and contains no remote controls. The owning Activity supplies real rename, new-window and close operations. `onRename` returns null on success or a localized error.

`CloudyTheme` uses generated canonical light/dark colors, typography, spacing and shapes. System fonts remain the fallback until font distribution review. `WindowWorkspaceScreen` keeps native `TextFieldValue` selection/composition and an identity-validated saveable draft scoped by window identity. Oversized edits are rejected in full with an explicit error, preserving the previous draft; no paste truncation occurs. The parent must supply a bounded current name. No global shortcut handler consumes native field editing.

The complete editor scrolls inside safe system/caption and IME insets. Actions wrap, remain in scroll content and use comfortable target height. Actual narrow-window, IME, accessibility/font-scale and lifecycle behavior requires parent build/device qualification. This is not the complete P1-S2 component catalog.

Run `python3 tools/generate_android_tokens.py` after changing canonical tokens, or `python3 tools/generate_android_tokens.py --check` to detect drift. Generated constants are checked in; Gradle need not run Python. Parent owns library build configuration and resources namespace, which must be `io.github.baitian6641.cloudy.design`.

The single-line name field uses Apply name wording. The parent notice owns successful-update feedback; the editor displays only validation/operation errors and makes no durable workspace-file persistence claim.
