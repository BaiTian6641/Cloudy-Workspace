# Cloudy Console R1 implementation plan

**Established:** 2026-10-17, America/Chicago, from `a6592be4d08ca7a8ee030076708f50c7a0664992`. **Branch:** `codex/console-r1-implementation`. **Scope:** C01–C20 in [console.md](../01-product/console.md), with C15 retained as the R1 follow-up and C12 retained as optional R3 work. The authoritative requirement/evidence matrix is [console-r1-requirements.md](console-r1-requirements.md).

This is an executable engineering record, not a completion claim or delivery-date forecast. It preserves Rule 0, independent Console/Desktop packages, ordinary SSH without the custom host agent, per-save overwrite consent, bounded real I/O, and the prohibition on placeholder product actions.

## Current execution goal — emulator-first product development

The user's resumed instruction is to fully develop Console functionality and UX first using an
emulator where practical, with Termius-inspired organization and Cloudy's desktop design language.
The parent manages detailed disjoint `deepseek/deepseek-flash` packets, reviews all proposals and
owns integration, shared runtimes, evidence and Git. This changes the development sequence, not R1 scope.

1. Resolve the reproduced renderer/real-SSH input failures with fail-before/pass-after evidence;
   retain native trust/credentials, bounded output and no command/input replay.
2. Build and run the actual app in an owned API37 desktop-sized/freeform emulator; qualify native
   home navigation, host/profile actions, terminal operation, themes, density, resizing and failure states.
3. Continue M3–M6 in dependency order: desktop input/clipboard, authentication/jumps and transfer core,
   layouts/durable sessions/forwards, then integrated X11 and session-linked file/edit/drop journeys.
4. Run the unchanged Q-C01–Q-C10 and app-scoped release gates at their actual boundaries. Emulator,
   host-process and physical Googlebook results remain separately attributed.

The current official Android CLI `medium_desktop` profile resolves to an API34 image, below
Cloudy's minimum35. Do not lower that minimum for a test. Use an isolated API37 configuration for
implementation and label its window-system limitations; actual Googlebook acceptance remains required.


## Review result and current baseline

All documents named by the implementation request exist and were read from their actual locations: `docs/01-product/console.md`, `docs/00-project/platform-rule-zero.md`, `docs/04-design/desktop-terminal-interaction-contract.md`, `docs/02-architecture/x11-windows.md`, `docs/02-architecture/console-files.md`, `docs/02-architecture/file-publication.md`, `docs/05-delivery/release-gates.md`, and `docs/06-validation/test-cases.md`. No referenced-document waiver or provisional substitute is needed.

The repository is not an empty scaffold. The independently installable native shell, organized profiles, app-owned trust/password admission and read-only SFTP are maintained. M1 adds a retained PTY on that transport; M2 demonstrates two native X11 windows in an isolated diagnostic; `3f9ce70` adopts the renderer module and `625796e` repairs Android committed-input delivery. These are scoped construction results, not full terminal/authentication, writable files, integrated product X11 or R1 acceptance.

The principal implementation gaps are real product boundaries, not documentation gaps:

1. The maintained PTY/resize/same-owner SFTP backend is host-qualified (M1). The repaired product now executes a real SSH command with visible output and exact teardown on the API37 emulator; sustained backpressure, actual remote resize and lifecycle breadth remain open.
2. The pinned xterm source build and binary bridge are maintained in `shared/android/terminal`. Reviewed repairs address spoken-feedback configuration, the embedded viewport and fast Android committed text preceding Enter/Ctrl+C. Native clipboard/menus/selection, physical input/system IME and accessibility remain incomplete.
3. The public-API X11 diagnostic presents per-XID native tasks and real forwarded xterms (M2). Product integration, popups, owner retirement, isolation breadth and remote VS Code remain unaccepted; one desktop or browser editor is not a substitute.
4. Full key/keyboard-interactive authentication, verified jumps, layouts/tmux, managed forwards, writable/journaled SFTP publication, drops, and release evidence remain open.
5. CAP-FOUNDATION supports construction only. CAP-SSH/CAP-TERMINAL remain experimental, CAP-X11 is pending, and CAP-PLATFORM is required at the final R1 gate.
6. Termux:X11's GPLv3 and constituent license/source obligations remain a Console distribution decision. A private feasibility build does not settle release licensing.

No architecture change is authorized by this plan. The first increments use the already selected SSHJ and repaired xterm candidates and the reviewed X11 engine experiment. Any replacement will require an ADR amendment with evidence, consequences, and a preserved independent ordinary-SSH baseline.

## Release-gate reconciliation

`console.md` names P2-S5 as the independent Console release gate; it does not make P2-S5 an SSH-only checkpoint ordered before P2-S6/S7. The canonical [P2 phase](phases/p2-console.md) and [independent release gates](release-gates.md) already resolve the ordering:

- P2-S1 and P2-S8 qualify reusable transport and transfer source.
- P2-S2/S3/S4 build the terminal, layouts, attachments, and forwards.
- P2-S6 and P2-S7 are feature prerequisites of P2-S5.
- **P2-S5 is the final full-R1 application/release gate.** Its acceptance run includes separate X11 application windows, integrated SFTP/edit/drop, a full coding day, Q-R01/Q-R02, and the app-scoped checklist with Desktop and the custom host agent absent.

Therefore an earlier SSH/PTY/UI increment is a partial construction milestone. It must not be labeled R1, independently released as full Console, or used to close Q-C06 through Q-C10.

## Execution milestones

### M0 — Review, requirements, and reproducible baseline (completed 2026-10-17)

- **Stages/dependencies:** P2 planning; current P0-S7 and P1 foundation evidence.
- **Changes:** maintain the C01–C20 matrix; reconcile gate language; record current source, device, build, and untracked-draft boundaries.
- **Checks:** `python3 tools/validate_project.py`; strict Gradle compile/test/Lint baseline; exact Git/remote comparison.
- **Exit:** reviewed plan and reproducible baseline with failures preserved. No product capability promotion.

### M1 — Real retained PTY and same-owner SFTP boundary (completed bounded milestone 2026-10-17)

- **Stages/dependencies:** bounded P2-S1/P2-S2 construction over the existing authenticated SSHJ owner.
- **Changes:** independently review/adopt the frozen `SshOwnedPty` proposal; bounded copied input, coalesced resize, backpressured stdout/stderr, close/cancellation, and full worker retirement; keep the existing short SFTP lane on the same authenticated transport.
- **Checks:** Kotlin compiler, seven pure bounds tests, existing SSH regression suite, then an owned real POSIX OpenSSH PTY+SFTP fixture exercising remote `stty` resize and one-credential/one-transport ownership.
- **Exit:** real PTY bytes/resize and concurrent read-only SFTP pass, or a concrete defect is retained for repair. This is not renderer, IME, layout, or P2-S2 acceptance. **Result:** achieved and recorded in [the PTY backend run](../../qualification/results/P2-S1/2026-10-17-ssh-pty-backend.md), including the disclosed fixture privilege boundary and remaining stress/device cases.

### M2 — P0-S7 two-XID native-window architecture proof (completed bounded proof 2026-10-17)

- **Stages/dependencies:** P0-S7, Q-C07/Q-C08; independent of formal Console UI.
- **Changes:** APK-owned public-API engine startup; per-display cookie isolation; Composite-redirected per-XID presentation into two native surfaces; top-level/transient/popup metadata; `WM_DELETE_WINDOW`; focused input mapping and surface recreation. Exclude the hidden-API shell loader and disable clipboard/XDND initially.
- **Checks:** small real X clients with two top-level windows, owned modal and popup; wrong cookie, two trust contexts, SSH loss and surface/process lifecycle; then the existing real OpenSSH/Xvfb forwarding leg.
- **Exit:** recorded two-XID/two-native-window result with exact engine/build/ABI evidence, or a specific engine/broker blocker. A compile, two displays, or one full X desktop is only partial evidence. **Result:** two real XIDs presented in two independent native tasks, an owned transient dialog, real pointer/keyboard input, and two real OpenSSH-forwarded xterm windows with remote key input on the identified Googlebook; recorded in [the device run](../../qualification/results/P0-S7/2026-10-17-x11-broker-device.md). Override-redirect popup, IME commit, two-host isolation on Android, security mode, forced recreation and owner→window retirement remain open for P2-S6; the probe is diagnostic and no product/capability promotion follows.

### M3 — Formal terminal and desktop input (in progress, partial construction)

- **Stages/dependencies:** P2-S1/P2-S2 after M1; renderer adoption still requires its capability review.
- **Changes:** product session coordinator, pinned local xterm assets, exact-origin/binary bridge, renderer-generation fencing, native clipboard/paste review, pointer selection/context/Edit menus, physical shortcuts, IME commit, resize and bounded scrollback/output.
- **Checks:** Q-C02/Q-A01 byte comparisons for multiline/CJK/combining/emoji, alternate-screen and agent TUI, rapid/long output, focus loss, narrow tiling and physical Googlebook keyboard/trackpad/available IMEs.
- **Exit:** sustained exact-byte terminal behavior without silent loss/replay; unresolved renderer/accessibility items remain explicit.
- **Result so far:** renderer adoption (`3f9ce70`), [committed-input repair](../../qualification/results/P2-S2/2026-10-17-terminal-input-repair.md) (`625796e`), [viewport repair](../../qualification/results/P2-S2/2026-10-17-terminal-viewport-repair.md) and the reviewed source-built xterm fix have separate evidence. The [emulator workflow](../../qualification/results/P2-S2/2026-10-17-console-terminal-emulator.md) binds seven real-Chrome cases, 40 build-policy tests, strict Android construction and actual SSH command/output/retirement. Historical failures are preserved. The current integration extends that check through the real home search/selection/Connect route and preserves local window drafts across responsive reflow. M3 remains in progress; clipboard/menus/copy-on-select, physical input/system IME/TalkBack and complete Q-C02 remain required.

### M4 — Complete SSH trust/auth/jumps and shared transfer core

- **Stages/dependencies:** P2-S1 then P2-S8; can proceed independently of renderer acceptance.
- **Changes:** profile environment/account/credential references; supported key and keyboard-interactive prompts; verified jump chains; writable SFTP actions, bounded staging/journal, integrity, exclusive-copy and typed per-save publication decisions.
- **Checks:** Q-C01/Q-S01/Q-F02 against real servers; Q-F01 concurrency, collisions, revoked URIs, lost acknowledgement and process death. No renderer dependency.
- **Exit:** reusable real transport/transfer source with independent app-owned state and honest unsupported capabilities.
- **Result so far:** public-key, single-prompt keyboard-interactive and one-hop jump admission are qualified; the
  [writable SFTP engine host slice](../../qualification/results/P1-S3/2026-10-18-sftp-writable-engine.md) adds exclusive
  create/bounded write/rename/removal with recoverable typed failures, and the Files screen's create/rename/delete/replace-consent
  actions pass an emulator workflow. Two-phase staged edit with conflict/explicit-replace approval and a bounded persistent journal are host/emulator-qualified;
  upload/download, integrity, physical input and Q-C09 remain. M4 is in progress.

### M5 — Layouts, durable sessions, attachments, and forwards

- **Stages/dependencies:** P2-S3 then P2-S4, consuming M3/M4.
- **Changes:** tabs/split tree/focus/zoom/saved layouts/independent tasks; bounded search; configured named tmux attachment; attachment path insertion only after verified commit; loopback-only local forwards and external browser previews.
- **Checks:** Q-C03/Q-C04/Q-C05/Q-P02, disconnect/eviction/host reboot, no unsent-input or command replay, collisions and callback timeout.
- **Exit:** recoverable multi-pane workflows and explicit session/forward ownership.

### M6 — Product X11 and complete session-linked files

- **Stages/dependencies:** P2-S6 after M2/M5; P2-S7 after M4/M5.
- **Changes:** per-profile X11 policy and actual restricted-mode availability, graphical window list/host identity/native input; adjacent pin/follow SFTP pane, staged editing, persistent transfers, bidirectional Android drops and post-commit quoted path insertion.
- **Checks:** Q-C07/Q-C08/Q-C09/Q-C10 plus Q-F01 integration cases, two hosts/apps, owned dialogs, remote VS Code with exact version/launch prerequisites, interrupted transfers and Desktop/host agent absent.
- **Exit:** real native X11 app-window and file workflows pass, with graphics/audio/Wayland/clipboard/XDND limits published.

### M7 — Final R1 candidate and independent release evidence

- **Stages/dependencies:** P2-S5 only after P2-S4/S6/S7; CAP-PLATFORM required.
- **Changes:** qualified agent CLI matrix, optional composer/newline policy, previewed non-secret SSH-config import (C15 follow-up), setup/support/notices and recovery documentation.
- **Checks:** Q-C01 through Q-C10, Q-C06 full coding day, Q-R01/Q-R02 signed install/update/migration/recovery, and ten working days on the exact candidate.
- **Exit:** full R1 only when every required capability and app-scoped release criterion is implemented and verified. Failed/blocked/unverified rows remain open; no declaration by compilation or screenshots alone.

## Validation and evidence rules

Every milestone records exact source SHA, build/toolchain/dependency identities, executed-versus-unrun checks, remote OS/server/application versions, device build and hardware, and sanitized evidence paths. Unit/synthetic tests establish only their named boundaries. Real host tests establish real server interoperability, not Googlebook presentation. Physical Googlebook results remain necessary for native input/window/product claims. Existing failed evidence and unrelated untracked drafts remain preserved and excluded until separately reviewed.
