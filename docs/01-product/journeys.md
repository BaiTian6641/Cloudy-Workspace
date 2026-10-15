# End-to-end journeys

These journeys join product requirements, design states, stages, and acceptance evidence. They are proposed flows; the specimen illustrates only a subset.

## J01 — First trusted Console connection

1. Create host profile with address, username, environment, and optional jump chain.
2. Select credential enrollment in Console's native UI.
3. Compare the displayed server fingerprint with a trusted source; save explicit trust.
4. Authenticate and open the selected project/session.
5. Show host/environment, persistence mode, and connection state beside the active pane.

Failure paths: DNS/route failure remains distinct from key mismatch and authentication denial. A changed key stops before authentication. Canceling enrollment does not leave a partially usable credential. **Stages:** P2-S1/S2. **Evidence:** Q-C01, redacted logs, known-host record, changed-key attempt.

## J02 — Agent work with an attachment and browser preview

1. Attach to a configured durable session and start an ordinary CLI on the host.
2. Drop an image; preview local source, destination host/project, name, and size.
3. Upload to the session staging directory and verify completion/hash.
4. Insert the remote reference without auto-submitting a command.
5. Create a loopback-only forward for the host preview and open it in the external browser.
6. Close the forward independently of the PTY.

Failure paths: source changed during upload, invalid Windows/WSL path, port collision, app eviction, authentication callback timeout. Preserve the attachment and show a retryable transfer job; never send a half-uploaded path. **Stages:** P2-S4/S5. **Evidence:** Q-C04/C05.

## J03 — Switch to visual work on the same project

Console sends `hostId`, `environmentId`, `workspaceId`, and registered `resourceId` through an explicit protected IPC route. Desktop resolves the resource under its own profile and asks for its own pairing/authentication if needed. It presents the selected backend and starts the stream or provides the manual-launch fallback. Unsupported or missing recipients produce an install/setup hint without losing Console state.

**Stages:** P1-S3, P3-S2/S5. **Evidence:** Q-X01 with both app-version skew and missing-app cases. No shell command, credential, or local Android path is passed as a resource action.

## J04 — Program hardware with exclusive ownership

Inspect the device identity and current consumer. Select a host, attach through the qualified backend, and wait for confirmed ownership. Start programming in the host tool; enable protected mode when operation state is not available. Follow approved reset/bootloader identities. On loss of transport, preserve the last confirmed owner with an unknown-state label until the consumer/provider state is reconciled. Release intentionally before changing host.

**Stages:** P0-S5, P3-S4, P6-S1. **Evidence:** Q-D04, flash/verify logs, reset identities, interrupted transport, explicit recovery. A fallback provider near the board is recorded as a different topology.

## J05 — Edit a remote document safely

Choose a named root and file, stage a versioned local copy, open through the system's document workflow, and commit only if the remote version still matches. A competing remote edit creates a conflict copy with timestamps/source details. Offline edits remain queued and visibly uncommitted. Deleting a local cache copy does not delete the remote file.

**Stages:** P3-S3, P5-S3. **Evidence:** Q-D03 and Q-D05, including permission revocation and process death during writeback.

## J06 — Resume after network or client loss

Restore the layout immediately from local metadata; show disconnected state while route/authentication recover. Reattach to a named remote session after checking identity. Do not replay unsent input or approvals. Fetch events after the last confirmed cursor; indicate history gaps. Reconcile transfers by version/offset and USB by actual backend ownership. A host reboot changes boot identity and invalidates assumptions about live sessions.

**Stages:** P2-S3, P4-S1, P6-S1. **Evidence:** Q-C03, Q-X02, Q-X03.

## J07 — Work on a metered link

Choose Console by default. Desktop presents the selected bitrate/frame rate and a labeled estimate before streaming, with separate measured session bytes once available. Hide/close pauses media at the backend, not merely in the UI. Large transfers and thumbnails require explicit policy. A budget threshold does not interrupt active programming.

**Stages:** P0-S6, P5-S4. **Evidence:** Q-N01, router/OS counters compared to app payload counters.
