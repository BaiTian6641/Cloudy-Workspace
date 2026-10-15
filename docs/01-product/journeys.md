# End-to-end journeys

These journeys join product requirements, design states, stages, and acceptance evidence. They are proposed flows; the specimen illustrates only a subset.

All journeys run as Googlebook desktop workflows in Android packages under [Rule 0](../00-project/platform-rule-zero.md). Use native window/task, Files, keyboard/precision pointer and system IME behavior. Phone/tablet optimization is not a parallel journey.

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

Console sends `hostId`, `environmentId`, `workspaceId`, and registered `resourceId` through an explicit protected IPC route. Desktop resolves the resource under its own profile and authenticates independently. It opens the selected native RDP/VNC profile or the explicit high-performance stream lane; external streaming has a labeled launch/return flow. Switching protocols presents session differences instead of silently substituting one desktop. Missing recipients produce an install/setup hint without losing Console state.

**Stages:** P1-S3, P3-S2/S9. **Evidence:** Q-X01 with both app-version skew and missing-app cases. This is the suite milestone; standalone Desktop release does not wait for Console. No shell command, credential, or local Android path is passed as a resource action.

## J04 — Program hardware with exclusive ownership

Enroll device identity, provider/permission, approved consumer and auto-attach policy once. On a later plug, reconcile actual idle ownership and attach to that consumer without another Cloudy dialog where grants permit. Start programming only after confirmed ownership/tool readiness; enable protection when operation state is unavailable. Follow approved reset/bootloader identities. On loss, preserve last confirmed owner plus unknown state until reconciliation. Release intentionally before changing host; no flash replay.

**Stages:** P0-S5, P3-S4/S6, P6-S1. **Evidence:** Q-D04/D06, hotplug cycles, permission outcomes, flash/verify/reset logs and ownership recovery. A provider near the board is a different topology, not proof of local Googlebook plug-and-play.

## J05 — Edit a remote document safely

Choose a named root and file, stage a local copy with its source observation, and open through the system document workflow. A backend with guaranteed conditional replacement can reject a changed version atomically. On an ordinary backend, ask before every remote overwrite of the exact staged revision, offering save-copy/cancel and explaining that a prior check cannot prevent a later racing edit. Known changes refresh the conflict choice; lost acknowledgements require reconciliation. Offline/local autosaves remain pending and visibly uncommitted. Deleting a local cache copy does not delete the remote file.

**Stages:** P2-S7, P3-S3, P5-S3. **Evidence:** Q-F01, Q-D03 and Q-D05, including permission revocation, per-save consent, racing writers and process death during publication. See the [publication contract](../02-architecture/file-publication.md).

## J06 — Resume after network or client loss

Restore the layout immediately from local metadata; show disconnected state while route/authentication recover. Reattach to a named remote session after checking identity. Do not replay unsent input or approvals. Fetch events after the last confirmed cursor; indicate history gaps. Reconcile transfers by version/offset and USB by actual backend ownership. A host reboot changes boot identity and invalidates assumptions about live sessions.

**Stages:** P2-S3, P4-S1, P6-S1. **Evidence:** Q-C03, Q-X02, Q-X03.

## J07 — Work on a metered link

Choose Console by default. Desktop presents the selected bitrate/frame rate and a labeled estimate before streaming, with separate measured session bytes once available. Hide/close pauses media at the backend, not merely in the UI. Large transfers and thumbnails require explicit policy. A budget threshold does not interrupt active programming.

**Stages:** P0-S6, P5-S4. **Evidence:** Q-N01, router/OS counters compared to app payload counters.

## J08 — Launch an X11 tool beside Console

Connect to ordinary SSH with qualified X11 forwarding, then launch a remote engineering GUI. Its native top-level window appears beside Console; open a second app and an owned dialog, move/resize them and type with the system IME. Focus them from Console's window list. Close one app normally without closing its sibling or terminal. On X/SSH loss show actual GUI termination/unknown state without replaying the launch command.

**Stages:** P0-S7, P2-S6/S5. **Evidence:** Q-C07/C08 including two trust contexts, cookie policy and process/connection loss.

## J09 — Browse and drag files beside the terminal

Open the session-linked SFTP pane at a registered project root. Drop a local multi-file selection into the named directory, then insert a committed path into the terminal. Download/drag a remote file into a local editor through a staged URI, edit and follow the per-save publication choice for the actual backend. Switch terminal hosts during a transfer and verify the job keeps its original target. Missing directory integration offers explicit Open path instead of guessing from a shell prompt.

**Stages:** P2-S7/S5, P3-S3. **Evidence:** Q-C09/C10, hash/version comparisons, grant revocation, delayed receiver and competing edits.

## J10 — Choose a native desktop protocol and recover

Select a saved RDP or VNC profile, verify server identity/security and authenticate through native UI. Work in the native surface, change scale/size if supported, switch focus and release held input. Exercise a controlled outage; show the actual recovered/new server session and measured recovery. No custom Cloudy host agent is needed. Performance streaming has its own profile and later matched-baseline measurement.

**Stages:** P0-S4/S6, P3-S7/S8, P5-S2. **Evidence:** Q-D07/D08/Q-N02/N03 with protocol-specific server/security/encoding and workload context.
