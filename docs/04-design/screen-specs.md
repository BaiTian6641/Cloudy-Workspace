# Screen-by-screen design specifications

Screen IDs are stable references for design review and implementation tickets. The [specimen](preview.html) illustrates S02 and S06 plus selected connection and inspector states. Other screens below are specifications, not completed mockups.

## S01 — Console hosts and first connection

**Goal:** find/create the right environment and establish trust. Navigation groups favorites/recent workspaces/hosts. Main table shows name, environment, tags, last used, and observed status. A compact detail pane shows endpoint, key fingerprint when enrolled, credential reference, and persistence choice. Primary action is Connect; Add host is available when no selection exists.

First trust uses a focused dialog with host/address/port, algorithm/fingerprint, comparison guidance, and explicit save/cancel. A changed key displays old/new fingerprints and stops connection; do not bury it in a transient toast. Empty state provides Add host and optional non-secret SSH-config import. Import preview lists unsupported directives and never executes config commands.

## S02 — Console active workspace

**Goal:** work in several terminal contexts with little chrome. Left navigation uses workspace/host names. Header shows project and host/environment. Tabs represent named contexts; a two-pane terminal layout includes active-pane border/focus treatment and subtle host/session labels. A supporting inspector lists session persistence, forwards, and attachments. Status strip carries connection/path/counter provenance.

Primary action is contextual: new session or open a known workspace, not a large app-wide “Run.” Terminal uses its own dark palette and mono grid. A copied selection has immediate feedback without changing terminal content. Search opens locally with result navigation and closes back to the same pane.

**States:** connecting; authenticating; connected; detached; reconnecting; remote session ended; output backpressure limit; missing credential. Each terminal pane can have a different transport state without an all-or-nothing workspace error.

## S03 — Local composer and attachment staging

**Goal:** prepare a prompt reliably. Composer opens beneath or beside the terminal without replacing it. Show text area, attachment queue, destination host/project, insert/send policy, and explicit action. Attachment rows show name, size, upload state, and final remote reference.

If upload fails, keep the local attachment and text buffer. If the selected terminal changes host, require an explicit destination update before sending. Binary content does not enter a shell paste. Large text can be uploaded as a file with a clear reference.

## S04 — Port-forward manager

Columns: label, local bind/port, remote destination, owner session, state, action. Loopback is the default. Changing bind scope is an advanced deliberate setting. Show collision/authentication/network errors on the affected row. Closing a tunnel never implicitly closes the shell; closing its owner explains the tunnel lifetime. Open preview launches the external browser.

## S05 — Console reconnect / session recovery

Keep prior output readable and the target visible. Show route/host/authentication progress with a cancel action. Mark a local buffer unsent. Once trust and identity are checked, attach to the recorded durable session. If missing, offer a new session as an explicit action; do not reconstruct it by replaying commands. A reboot creates a distinct explanation from ordinary network loss.

## S06 — Desktop workspace overview

**Goal:** open the right visual context and inspect supporting resources. Navigation selects workspace/host. Header shows project/host/environment and a Connect/Open desktop action. The main region is an honest backend launch state in R2; the specimen uses a labeled illustrative desktop scene. Supporting panes show selected files and device ownership.

Distinguish installed backend, paired backend, available host session, and stream state. If the stream opens externally, label the destination and return affordance. Do not show live-looking metrics or thumbnails unless actually measured/current. The specimen's numbers are explicitly synthetic.

## S07 — Embedded desktop session

**Goal:** use a native remote surface while retaining escape and context. The surface takes maximum area; a minimal overlay exposes input profile, monitor, stream settings, capture/release, and diagnostics. Controls auto-hide only when there is a reliable visible/reachable recovery mechanism. Critical protected USB state remains available without hijacking remote input.

States: initial decode, playing, permission-required, stream stalled, host locked, display unavailable, backend error. Display configured profile separately from observed frame/decode statistics. No general UI bridge handles video frames.

## S08 — Files and transfers

Root selector identifies Local Googlebook, host SFTP, Drive provider, or lab location. Breadcrumb/path is selectable/copyable. File table uses density preference, sortable columns, range selection, and local horizontal overflow where needed. Inspector shows full path, source version, cached/offline state, and permissions.

Transfer drawer groups active/queued/history. Status names separate sent, verifying, committed remotely, and synced by another provider. Conflict view displays both versions with modified/size/version evidence and explicit preserve/replace/save-copy choices. An unknown remote version disables a destructive resume/commit.

## S09 — Devices and protected operation

Table shows provider/device, current consumer, state, last confirmed time, and assign/release actions. Selecting a device exposes VID/PID/serial/approved reset identities and backend diagnostics. During programming, a persistent protected-operation region names the host/device and explains release consequences. On loss, show owner unknown/last confirmed host; recovery reconciles real backend state before reassignment.

## S10 — Activity and notification preferences

Chronological items identify host/project/source and occurrence time. Filters operate on known fields; stale/replayed history is visually different from newly actionable events. An action is enabled only when its source capability and request expiry allow it. Notification settings group job completion, connection, transfer, and device events; permission denial leaves in-app history usable.

## S11 — Settings and diagnostics

Groups: appearance/density/type, shortcuts/input, hosts/trust/credentials, connection/lifecycle, file cache, notification channels, backend versions, and diagnostic export. Keep secrets in native enrollment flows, with per-app ownership explicit. Diagnostics show versions, last error, capability snapshot, and counter scope. Export offers redacted content inspection.

## S12 — Command palette

Search registered actions/workspaces/hosts, displaying scope and destination. Results label actions as local navigation, connect, or consequential operation. Enter performs the selected ordinary action; consequential operations open their normal deliberate flow. Arrow keys navigate, Escape dismisses, focus returns correctly. The palette never turns arbitrary text into a shell command.

## Review checklist for every screen

Identify its primary task, host/environment context, focus entry/exit, empty/loading/partial/error states, narrow-window behavior, readable long labels, touch/keyboard path, and the exact operation authority. Link changes to a stage and an acceptance case. A visually polished happy path alone does not pass design review.
