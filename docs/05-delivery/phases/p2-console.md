# P2 — Console daily-use release

**Status:** planned. **Active effort:** 30–54 person-days before contingency. **Purpose:** Deliver the independent desktop Console with trusted SSH, separate X11 application windows and adjacent SFTP/drop workflows.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Estimates are active effort, not elapsed time or staffing commitments; re-estimate after P0. Stage IDs remain stable; the dependency order can place a later-numbered addition before an existing release gate.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P2-S1 | Trusted SSH connectivity, authentication, and jumps | 3–5 | P1-S4 |
| P2-S2 | Interactive terminal correctness and bounded data path | 3–5 | P2-S1 |
| P2-S3 | Tabs, panes, windows, search, and durable sessions | 3–5 | P2-S2 |
| P2-S4 | SFTP attachments and local browser forwards | 3–5 | P2-S3 |
| P2-S6 | SSH X11 forwarding and separate native app windows | 10–20 | P0-S7, P1-S4, P2-S3 |
| P2-S7 | Session-linked SFTP pane and bidirectional drag-and-drop | 5–9 | P2-S4, P1-S3 |
| P2-S5 | Agent workflows, profile import, and Console daily-use gate | 3–5 | P2-S4, P2-S6, P2-S7 |

## P2-S1 — Trusted SSH connectivity, authentication, and jumps

**Owner:** Terminal developer. **Prerequisites:** P1-S4. **Status:** planned.

Work:

1. Implement profiles and engine adapters for connect/cancel, host trust, authentication, and typed failures.
2. Add key/password/keyboard-interactive methods supported by the selected engine and keep prompts in native UI.
3. Implement jump chains with trust at every hop and clean channel shutdown.
4. Exercise key change, wrong credentials, offline DNS/route, authentication cancel, and per-host revocation.

Deliverables:

- Trusted SSH connection workflow
- Profile/trust/authentication fixtures

Exit evidence:

- A plain SSH server works without Desktop or the custom host agent.
- Changed keys block before secrets are sent and errors identify the failing layer.

Record evidence under `qualification/results/P2-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-S01. Official/upstream source IDs: G18, R10; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S2 — Interactive terminal correctness and bounded data path

**Owner:** Terminal/input developer. **Prerequisites:** P2-S1. **Status:** planned.

Work:

1. Connect PTY bytes/resize to the selected renderer with bounded queues/backpressure off the UI thread.
2. Implement negotiated terminal features, exact paste/composition, selection, clipboard policy, and external link opening.
3. Exercise editors, agent menus/diffs, alternate screen, mouse reporting, Unicode widths, and long prompts.
4. Measure local input/render latency, sustained output, scrollback bounds, and accessible terminal behavior.

Deliverables:

- Working PTY surface and supported-feature declaration
- Exact-byte and sustained-load evidence

Exit evidence:

- No duplicate/lost paste or committed IME text and no stuck modifiers.
- Supported output rate is sustained without silent loss or unbounded growth.

Record evidence under `qualification/results/P2-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C02, Q-A01. Official/upstream source IDs: G10, G17, R09, R10; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S3 — Tabs, panes, windows, search, and durable sessions

**Owner:** Terminal/Android developer. **Prerequisites:** P2-S2. **Status:** planned.

Work:

1. Implement split trees, keyboard focus, tab naming/close semantics, pane zoom, and local scrollback search.
2. Persist layouts per window profile and support explicit independent tasks/windows with safe resize behavior.
3. Attach to configured tmux sessions on supported environments; label ordinary Windows sessions non-persistent.
4. Recover from network loss/process eviction/host reboot with actual session identity and no input/command replay.

Deliverables:

- Saved multi-pane/multi-window workflow
- Durability/resize/restore compatibility matrix

Exit evidence:

- Session state distinguishes disconnect, detach, ended process, and unknown.
- A saved layout that no longer fits keeps sessions reachable without terminating them.

Record evidence under `qualification/results/P2-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C03, Q-P02. Official/upstream source IDs: G03, G25, G28; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S4 — SFTP attachments and local browser forwards

**Owner:** Terminal/files developer. **Prerequisites:** P2-S3. **Status:** planned.

Work:

1. Add picker/drop/pasted-image staging with destination confirmation and hash/size validation.
2. Insert environment-correct remote paths only after completion; preserve failed jobs and local text.
3. Implement explicit loopback forwards, collisions, lifetime/close controls, and external browser previews.
4. Exercise URI permission revoke, source change, interrupted upload, Windows/WSL quoting, and callback timeout.

Deliverables:

- Attachment uploader and forward manager
- Hash/path/permission and preview evidence

Exit evidence:

- No half-uploaded attachment reference or implicit shell submission.
- Forwarded content never loads inside the privileged renderer.

Record evidence under `qualification/results/P2-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C04, Q-C05. Official/upstream source IDs: G11, G13, G17; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S6 — SSH X11 forwarding and separate native app windows

**Owner:** X11/Android/terminal developer. **Prerequisites:** P0-S7, P1-S4, P2-S3. **Status:** planned.

Work:

1. Integrate the qualified X server and native window broker with SSH X11 request/channel/cookie handling and per-trust-context display isolation.
2. Map top-level windows, transient/modal dialogs, popups, close requests and host labels to native Googlebook windows with accurate input/IME/scale.
3. Add per-session graphical-window list, restricted/trusted mode policy, prerequisites and layer-specific diagnostics.
4. Exercise multiple hosts/apps, reconnect, timeout, activity/process recreation and GUI termination without command replay or false tmux durability.

Deliverables:

- Independent Console X11 app-window workflow and bounded native bridge
- X11 window/input/trust/lifecycle qualification matrix

Exit evidence:

- Required separate remote app windows and owned dialogs pass on Googlebook without Desktop or custom host agent.
- Actual X server/SSH versions, restrictions and GUI durability limits are published; a single full X desktop cannot pass this gate.

Record evidence under `qualification/results/P2-S6/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C07, Q-C08, Q-P02, Q-A01. Official/upstream source IDs: R17, R18, R19, R20, R33, G03, G10, G27; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S7 — Session-linked SFTP pane and bidirectional drag-and-drop

**Owner:** Console/files/Android developer. **Prerequisites:** P2-S4, P1-S3. **Status:** planned.

Work:

1. Build adjacent session-bound SFTP browsing/actions, pinned/follow-active context, breadcrumbs, search and transfer drawer using shared file/transfer models.
2. Implement URI-grant-aware uploads, downloads, directory jobs, staged exports to other apps and terminal path insertion after verified commit.
3. Add explicit manual directory selection and optional validated shell integration; do not scrape prompts or infer shell CWD from SFTP.
4. Qualify staged remote edits/conflicts, late-reading receivers, changed/revoked sources, process death and tab/host switch during transfer.

Deliverables:

- Console SFTP pane, shared transfer journal and staging content-provider contract
- Incoming/outgoing drop and remote-edit integrity evidence

Exit evidence:

- Browse/upload/download/edit and both Android drop directions pass with Desktop absent, correct captured host/path, and no auto-submit.
- Unsupported receivers/server features show a tested Save/Share or manual path fallback; source changes preserve conflict copies.

Record evidence under `qualification/results/P2-S7/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C04, Q-C09, Q-C10, Q-S01. Official/upstream source IDs: R03, G11, G13, G25; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S5 — Agent workflows, profile import, and Console daily-use gate

**Owner:** Terminal/product QA. **Prerequisites:** P2-S4, P2-S6, P2-S7. **Status:** planned.

Work:

1. Qualify selected current agent CLIs as ordinary PTY applications with versions/authentication recorded; keep their credentials on the host.
2. Add optional local composer, discoverable newline behavior, and configurable terminal bell/task alerts without screen-scraped approval logic.
3. Add a previewed non-secret profile import with an explicit supported SSH-config subset.
4. Run a full coding day with Desktop/custom host agent absent, including separate X11 apps, session-linked SFTP upload/download/edit/drop, terminal/TUI and recovery.

Deliverables:

- R1 Console candidate and CLI compatibility matrix
- Daily-use report and release limitations

Exit evidence:

- The full revised Console workflow passes including X11 windows and integrated SFTP; an earlier SSH-only build is labeled a partial milestone.
- No command/approval replay, hidden dependency on Desktop/host agent or unsupported GUI durability claim.

Record evidence under `qualification/results/P2-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C06, Q-C07, Q-C08, Q-C09, Q-C10, Q-X02. Official/upstream source IDs: R02, R03, G02; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

SSH-only builds are partial milestones. Full R1 requires separate X11 app windows and integrated SFTP/drop. An alternative workflow must be labeled and explicitly accepted through a scope revision, not silently substituted. Every done stage links actual evidence; a documentation check does not qualify a device.
