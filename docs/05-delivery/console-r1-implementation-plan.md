# Cloudy Console R1 implementation plan

**Established:** 2026-10-17, America/Chicago, from `a6592be4d08ca7a8ee030076708f50c7a0664992`. **Branch:** `codex/console-r1-implementation`. **Scope:** C01–C20 in [console.md](../01-product/console.md), with C15 retained as the R1 follow-up and C12 retained as optional R3 work. The authoritative requirement/evidence matrix is [console-r1-requirements.md](console-r1-requirements.md).

This is an executable engineering record, not a completion claim or delivery-date forecast. It preserves Rule 0, independent Console/Desktop packages, ordinary SSH without the custom host agent, per-save overwrite consent, bounded real I/O, and the prohibition on placeholder product actions.

## Review result and current baseline

All documents named by the implementation request exist and were read from their actual locations: `docs/01-product/console.md`, `docs/00-project/platform-rule-zero.md`, `docs/04-design/desktop-terminal-interaction-contract.md`, `docs/02-architecture/x11-windows.md`, `docs/02-architecture/console-files.md`, `docs/02-architecture/file-publication.md`, `docs/05-delivery/release-gates.md`, and `docs/06-validation/test-cases.md`. No referenced-document waiver or provisional substitute is needed.

The repository is not an empty scaffold. Console 0.1.17/code 18 has an independently installable native Compose/document-task shell, organized SSH profiles, app-owned trust and finite password authentication, independent window metadata, and a finite read-only SFTP surface. The shared SSHJ module also has retained transport/SFTP ownership and a host-qualified X11 request/channel/cookie leg. Existing evidence is scoped: it does not establish a complete terminal, full authentication/jump coverage, writable transfers, X11 native windows, or R1.

The principal implementation gaps are real product boundaries, not documentation gaps:

1. The retained authenticated transport has no maintained PTY owner. A frozen cache-only proposal has passed source entry review but remains uncompiled and unrun.
2. The repaired xterm candidate exists only in the diagnostic probe. Formal Console needs a generation-fenced native terminal adapter, bounded queues, desktop input/IME/menu policy, and actual renderer acceptance.
3. The X11 engine compiles for arm64, but the per-XID Composite/window broker and public-API Android owner are missing. Two displays, one root desktop, VNC/RDP, and a browser editor cannot substitute for C17.
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

### M3 — Formal terminal and desktop input

- **Stages/dependencies:** P2-S1/P2-S2 after M1; renderer adoption still requires its capability review.
- **Changes:** product session coordinator, pinned local xterm assets, exact-origin/binary bridge, renderer-generation fencing, native clipboard/paste review, pointer selection/context/Edit menus, physical shortcuts, IME commit, resize and bounded scrollback/output.
- **Checks:** Q-C02/Q-A01 byte comparisons for multiline/CJK/combining/emoji, alternate-screen and agent TUI, rapid/long output, focus loss, narrow tiling and physical Googlebook keyboard/trackpad/available IMEs.
- **Exit:** sustained exact-byte terminal behavior without silent loss/replay; unresolved renderer/accessibility items remain explicit.

### M4 — Complete SSH trust/auth/jumps and shared transfer core

- **Stages/dependencies:** P2-S1 then P2-S8; can proceed independently of renderer acceptance.
- **Changes:** profile environment/account/credential references; supported key and keyboard-interactive prompts; verified jump chains; writable SFTP actions, bounded staging/journal, integrity, exclusive-copy and typed per-save publication decisions.
- **Checks:** Q-C01/Q-S01/Q-F02 against real servers; Q-F01 concurrency, collisions, revoked URIs, lost acknowledgement and process death. No renderer dependency.
- **Exit:** reusable real transport/transfer source with independent app-owned state and honest unsupported capabilities.

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
