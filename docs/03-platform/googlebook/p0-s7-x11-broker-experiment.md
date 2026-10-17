# P0-S7 bounded experiment — X11 broker two-window proof

**Stage:** P0-S7 (X11 forwarding and native application-window feasibility), cases Q-C07/Q-C08. **State:** experiment packet prepared 2026-10-16; the stage remains `planned` until the build below exists and its stop condition is met. This packet authorizes no device run by itself; the parent controls fixtures, ADB and the Googlebook device on execution.

## Hypothesis

A maintained Android-capable X server (the Termux:X11/Lorie lineage is the first candidate per the [native X11 engine entry](native-x11-engine-entry.md)) plus a small Cloudy broker can present **two real top-level XIDs from one isolated display** as two independently focusable native Googlebook windows, with correct owned-dialog behavior and RFC 4254 §6.3 cookie isolation over ordinary SSH.

## Owner and scope

- **Owner:** parent Codex (integration leader). Bounded workers may prepare engine archives/patches and the diagnostic module; the parent owns device execution and acceptance.
- **In scope:** exact engine/submodule archives, upstream patches and notices staged in ignored storage with recorded hashes; a private diagnostic build with a public APK/JNI owner and bounded lifetime; Composite-redirected per-XID pixmaps into two native `SurfaceView` windows; `WM_DELETE_WINDOW`, one `WM_TRANSIENT_FOR` dialog and one override-redirect popup; per-window keyboard/IME/pointer coordinate mapping; SSH `x11-req` and incoming `x11` channels with real `xauth` substitution; wrong-cookie rejection; two isolated host contexts.
- **Out of scope:** GPU/GLX/zero-copy, audio, clipboard/XDND (disabled initially), restricted/SECURITY mode claims, tmux-style persistence claims, a new X server implementation, phone/tablet targets.

## Stop condition

Recorded two-XID/two-native-window result with correct owned dialog and real SSH/cookie isolation, **or** a concrete blocker at engine build, Composite redirect, native surface presentation, dialog mapping, input mapping or SSH/cookie isolation. Either outcome is recorded with exact build/ABI/toolchain identities; a compile success alone is not a pass and a fallback (single full X desktop, streamed desktop) is never relabeled as one.

## Evidence plan

Record under `qualification/results/P0-S7/` per the [run template](../../06-validation/templates/run.md): engine/patch hashes, module/build identities, the two-window capture with window/task identity, owned-dialog and popup behavior, cookie substitution and rejection logs, SSH drop / surface recreation / process-death outcomes, and the surviving/ended/unknown client accounting. On completion, update [api-tracker.json](api-tracker.json), the CAP-X11 entry in [feasibility-gates.md](../../05-delivery/feasibility-gates.md), and check the [S14 window-list design](../../04-design/screen-specs.md) and [X11 architecture](../../02-architecture/x11-windows.md) against observed behavior.

## Current boundary

No engine archive, diagnostic build or device run exists yet for this packet. The next executable step on resume is staging the exact Termux:X11 engine archives and patches in ignored storage and compiling the private diagnostic, before any two-window claim is attempted.
