# P2 — Console daily-use release

**Status:** planned. **Active effort:** 15–25 person-days before contingency. **Purpose:** Deliver an independent, trustworthy terminal workflow before the broader workspace.

The canonical stage data is [backlog.json](../backlog.json). Estimates assume one experienced developer using established components; hardware access and upstream blockers can add elapsed time. Owner labels are roles, not staffed assignments.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P2-S1 | Trusted SSH connectivity, authentication, and jumps | 3–5 | P1-S4 |
| P2-S2 | Interactive terminal correctness and bounded data path | 3–5 | P2-S1 |
| P2-S3 | Tabs, panes, windows, search, and durable sessions | 3–5 | P2-S2 |
| P2-S4 | SFTP attachments and local browser forwards | 3–5 | P2-S3 |
| P2-S5 | Agent workflows, profile import, and Console daily-use gate | 3–5 | P2-S4 |

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

Record evidence under `qualification/results/P2-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C01, Q-S01. Official source IDs: G18, R10; resolve them in the [source register](../../07-reference/source-register.md).

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

Record evidence under `qualification/results/P2-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C02, Q-A01. Official source IDs: G10, G17, R09, R10; resolve them in the [source register](../../07-reference/source-register.md).

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

Record evidence under `qualification/results/P2-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C03, Q-P02. Official source IDs: G03, G25, G28; resolve them in the [source register](../../07-reference/source-register.md).

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

Record evidence under `qualification/results/P2-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C04, Q-C05. Official source IDs: G11, G13, G17; resolve them in the [source register](../../07-reference/source-register.md).

## P2-S5 — Agent workflows, profile import, and Console daily-use gate

**Owner:** Terminal/product QA. **Prerequisites:** P2-S4. **Status:** planned.

Work:

1. Qualify selected current agent CLIs as ordinary PTY applications with versions/authentication recorded; keep their credentials on the host.
2. Add optional local composer, discoverable newline behavior, and configurable terminal bell/task alerts without screen-scraped approval logic.
3. Add a previewed non-secret profile import with an explicit supported SSH-config subset.
4. Run a full coding day with Desktop uninstalled and the custom host agent absent; log defects and promote only passing combinations.

Deliverables:

- R1 Console candidate and CLI compatibility matrix
- Daily-use report and release limitations

Exit evidence:

- Real edit/build/attachment/preview/reconnect workflow passes without desktop streaming.
- No repeated command/approval or hidden dependence on another app process.

Record evidence under `qualification/results/P2-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-C06, Q-X02. Official source IDs: R02, R03, G02; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Every stage must satisfy its exit evidence or record an accepted, qualified fallback before this phase is declared complete. Swap engine/renderer behind the boundary if P0 assumptions fail; do not paper over input or trust failures with UI workarounds.
