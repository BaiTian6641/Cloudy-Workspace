# P2 — Console daily-use release

**Status:** planned. **Active effort:** 33–59 person-days before contingency. **Purpose:** Qualify reusable SSH/SFTP transport and transfer source, then deliver the full Console workflow and its independent functional, packaging and sustained-use release gate.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

P2-S1 and P2-S8 are reusable source stages for both apps, with no terminal renderer or Console UI prerequisite. P2-S8's 3–5 days move 2–3 days from P2-S7 and 1–2 from P3-S3; the project total is unchanged. Console retains all attachment, pane/drop, X11 and release requirements and repeats its integration-specific checks.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P2-S1 | Shared SSH transport, authentication, and jumps | 3–5 | P1-S4 |
| P2-S8 | Shared SFTP transfer, staging, and publication core | 3–5 | P2-S1, P1-S3 |
| P2-S2 | Interactive terminal correctness and bounded data path | 3–5 | P2-S1 |
| P2-S3 | Tabs, panes, windows, search, and durable sessions | 3–5 | P2-S2 |
| P2-S4 | SFTP attachments and local browser forwards | 3–5 | P2-S3, P2-S8 |
| P2-S6 | SSH X11 forwarding and separate native app windows | 10–20 | P0-S7, P1-S4, P2-S3 |
| P2-S7 | Session-linked SFTP pane and bidirectional drag-and-drop | 3–6 | P2-S4, P1-S3, P2-S8 |
| P2-S5 | Agent workflows, profile import and independent Console release gate | 5–8 | P2-S4, P2-S6, P2-S7 |

## P2-S1 — Shared SSH transport, authentication, and jumps

**Owner:** SSH/platform developer. **Prerequisites:** P1-S4. **Status:** planned.

**Required supported capabilities:** CAP-SSH.

Work:

1. Implement reusable SSH profile/engine adapters for connect/cancel, host trust, authentication and typed failures; each consuming app supplies its own profile store, credentials, jobs and lifecycle.
2. Add key/password/keyboard-interactive methods supported by the selected engine through a typed native authentication-prompt contract; do not require a PTY, terminal renderer or Console process.
3. Implement jump chains with trust at every hop and clean channel shutdown.
4. Exercise key change, wrong credentials, offline DNS/route, authentication cancel and per-host revocation in an isolated native test consumer, then repeat applicable cases in each consuming app.

Deliverables:

- Reusable SSH transport/authentication/jump library and native prompt contract
- Profile/trust/authentication fixtures and evidence independent of Console UI

Exit evidence:

- A plain SSH server works through the shared library with no PTY, terminal renderer, other app process or custom host agent; source reuse never shares credential stores.
- Changed keys block before secrets are sent and errors identify the failing layer.

Record evidence under `qualification/results/P2-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-S01, Q-F02. Official/upstream source IDs: G18, R10; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S8 — Shared SFTP transfer, staging, and publication core

**Owner:** Files/SSH/Android developer. **Prerequisites:** P2-S1, P1-S3. **Status:** planned.

**Required supported capabilities:** CAP-SSH.

Work:

1. Implement shared SFTP browsing/action primitives, capability discovery and URI-grant-aware upload/download/directory jobs on the qualified SSH transport; no terminal, pane, tab or installed Console dependency.
2. Build bounded local staging, source-version-bound resume, integrity checks, transfer journals and typed publication/recovery states with per-app storage, credentials, grants and job ownership.
3. Implement exclusive-copy and conditional/ordinary publication semantics. Every non-conditional overwrite requires explicit consent bound to the exact file/destination/save generation; expose a typed decision request that each app presents in native UI, with save-copy/cancel and no remembered blanket consent.
4. Define staging content-provider grants and late/seekable receiver behavior without requiring the later remote DocumentsProvider. Preserve local work and reconcile lost acknowledgements/process death without replaying destructive operations.
5. Exercise actual SSH/SFTP servers through a native qualification consumer with the terminal renderer absent: source changes, denied/revoked URI access, directory partial failure, name collisions, another writer between check/publish and ambiguous completion.
6. Publish the reusable source contract and evidence; Console attachments/pane and Desktop cross-root UI must repeat their integration-specific cases using the same core with independent app state.

Deliverables:

- Shared SSH/SFTP transfer library, journal/staging content-provider contract and typed publication decision interface
- Native consumer evidence for integrity, per-save consent, concurrent writes and recovery without Console UI

Exit evidence:

- Browse/upload/download/stage-edit and cancellation/recovery pass against the qualified ordinary SSH/SFTP server with no terminal renderer, Console panes or custom host agent.
- Every non-conditional overwrite is bound to its explicit save decision; canceled or obsolete decisions preserve staging and cannot publish a newer revision.
- Exclusive-copy collisions never truncate an existing file, and ambiguous completion never triggers blind rename/overwrite replay or a false remote-commit claim.
- The core owns no global credentials or permanently shared process; consuming apps independently own their stores, URI grants, lifecycle and transfer jobs.

Record evidence under `qualification/results/P2-S8/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-F01, Q-F02, Q-S01. Official/upstream source IDs: G11, G13, G17, R10, R34, R35; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S2 — Interactive terminal correctness and bounded data path

**Owner:** Terminal/input developer. **Prerequisites:** P2-S1. **Status:** planned.

**Required supported capabilities:** CAP-TERMINAL.

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

**Owner:** Terminal/files developer. **Prerequisites:** P2-S3, P2-S8. **Status:** planned.

Work:

1. Integrate the shared P2-S8 transfer core with picker/drop/pasted-image attachment UI, destination confirmation and hash/size validation.
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

**Required supported capabilities:** CAP-X11.

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

**Owner:** Console/files/Android developer. **Prerequisites:** P2-S4, P1-S3, P2-S8. **Status:** planned.

Work:

1. Build adjacent session-bound SFTP browsing/actions, pinned/follow-active context, breadcrumbs, search and transfer drawer using shared file/transfer models.
2. Integrate P2-S8 URI-grant-aware upload/download/directory jobs and staged exports into the Console pane and Android drag/drop; insert terminal paths only after verified commit.
3. Add explicit manual directory selection and optional validated shell integration; do not scrape prompts or infer shell CWD from SFTP.
4. Qualify staged remote edits/conflicts, late-reading receivers, changed/revoked sources, process death and tab/host switch during transfer.
5. Present the shared file-publication decision in Console: on a non-conditional backend, ask before every remote overwrite with save-copy/cancel; bind the visible choice to the exact file/destination/save generation and never remember blanket consent.
6. Repeat shared-core concurrency, exclusive-name collision and lost-acknowledgement cases through the actual Console edit/drop flows, including tab/context change; never claim stat-plus-rename prevents concurrent lost updates.

Deliverables:

- Console SFTP pane and app-specific integration of the shared journal/staging content-provider contract
- Incoming/outgoing drop and remote-edit integrity evidence

Exit evidence:

- Browse/upload/download/edit and both Android drop directions pass with Desktop absent, correct captured host/path, and no auto-submit.
- Unsupported receivers/server features show a tested Save/Share or manual fallback; publication follows the per-save consent and conflict contract.
- Every non-conditional remote overwrite has consent for that file and save generation; known conflicts and ambiguous completion preserve staged work and do not trigger an automatic overwrite retry.

Record evidence under `qualification/results/P2-S7/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C04, Q-C09, Q-C10, Q-S01, Q-F01. Official/upstream source IDs: R03, G11, G13, G25, R34, R35; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S5 — Agent workflows, profile import and independent Console release gate

**Owner:** Terminal/product QA. **Prerequisites:** P2-S4, P2-S6, P2-S7. **Status:** planned.

Work:

1. Qualify selected current agent CLIs as ordinary PTY applications with versions/authentication recorded; keep their credentials on the host.
2. Add optional local composer, discoverable newline behavior, and configurable terminal bell/task alerts without screen-scraped approval logic.
3. Add a previewed non-secret profile import with an explicit supported SSH-config subset.
4. Run a full coding day with Desktop/custom host agent absent, including separate X11 apps, session-linked SFTP upload/download/edit/drop, terminal/TUI and recovery.
5. Run the app-scoped release checklist for signed Console install/update/recovery, dependencies/notices, setup/support documentation and at least ten working days of candidate use; Desktop and the custom host agent are absent.

Deliverables:

- R1 Console candidate and CLI compatibility matrix
- Daily-use report and release limitations
- Independent Console candidate/support decision with app-scoped release evidence

Exit evidence:

- The full revised Console workflow passes including X11 windows and integrated SFTP; an earlier SSH-only build is labeled a partial milestone.
- No command/approval replay, hidden dependency on Desktop/host agent or unsupported GUI durability claim.
- Console may release after its own functional and app-scoped quality gates pass; Desktop, native streaming and optional host adapters are not release prerequisites.

Record evidence under `qualification/results/P2-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C06, Q-C07, Q-C08, Q-C09, Q-C10, Q-X02, Q-R02, Q-R01. Official/upstream source IDs: R02, R03, G02; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Full R1 requires separate X11 windows, integrated SFTP/drop and the independent app release checklist. A failed renderer or X11 gate blocks full Console but does not block Desktop R2 when its shared SSH/SFTP route and Desktop-specific capabilities are supported. Shared transport/transfer defects still block affected workflows in both apps. Every done stage links actual evidence; a documentation check does not qualify a device.
