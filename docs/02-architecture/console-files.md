# Console session-linked SFTP and drag-and-drop

**Status:** required product behavior, proposed implementation. **Stages:** P2-S8 shared core; P2-S4/P2-S7 Console integration; P3-S3 Desktop integration. **Cases:** Q-C04/Q-C09/Q-C10 and Q-F02 for shared-core qualification. The file pane works with Console alone and an ordinary SFTP-capable SSH server.

## Integrated workspace

Opening an SSH session offers an adjacent SFTP pane for that host/environment. Browse folders, create folders, upload, download, rename, delete deliberately, inspect permissions/links, copy paths, and stage a remote file for local editing. A bounded transfer queue remains visible beside the terminal. Pin the pane to a session or follow the selected pane; always show that choice and the resolved host/path. This takes the proximity and bidirectional file interaction of [MobaXterm's SSH browser](https://mobaxterm.mobatek.net/documentation.html) as the reference.

Console owns a complete session-level file workflow; Desktop adds cross-root browsing, larger workspace orchestration, Drive/provider integration, and the later system document provider. Shared filesystem and transfer source prevents two conflicting implementations. Each app still authenticates and stores jobs independently.

## Context binding and directory tracking

Use `{app profile, host-key identity, environment, SSH generation, SFTP session, pane binding}`. Capture this target when a drag/transfer starts. Switching terminal tabs must not redirect an existing job. If a drop lands on another host, show the new host/destination and require the ordinary explicit transfer action; never borrow credentials from the originating app.

SFTP does not expose an interactive shell's current directory. Default the pane to the registered project root or explicitly chosen path. Optional Follow terminal directory uses a qualified shell integration/OSC location signal with host/path validation and a visible status. It is off or unavailable for unsupported shells, nested SSH, containers, or ambiguous environments. Do not infer directories by scraping prompts or execute `pwd` into an active program. Offer a manual Open path action and a visible terminal↔files context link.

## Directional drop contract

| Gesture | Required result | Boundary |
| --- | --- | --- |
| Googlebook Files / another Android app → remote SFTP directory | Obtain valid URI grant, preview host/destination, enqueue upload; preserve grouping for multiple files | No local path guessed from a URI; permission loss blocks that job |
| Remote SFTP row → local destination pane | Download through the transfer journal; validate/publish to the chosen local URI/root | No move/delete implied by ordinary drag |
| Remote SFTP row → another Android app | Export a readable content URI for a bounded staged copy, with appropriate grant and availability | A bare SFTP path is not a content URI; unsupported receivers get Save/Share fallback |
| File → terminal/composer | Upload if required, then insert a correctly quoted path/reference into the selected environment | Never paste binary data, auto-submit a command, or insert before commit |
| Remote row → another remote directory | Explicit same-host rename/copy or cross-host copy plan according to actual capabilities | Never silently route a huge cross-host copy through the client or imply atomic cross-filesystem moves |
| File → X11 / RDP / VNC application window | Only an explicitly qualified backend file-redirection/XDND path | SFTP drop success does not prove remote GUI drag/drop |

Use Android [drag-and-drop](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop) and [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) grants. P2-S7 must prove incoming and outgoing Android app drops on Googlebook. It may use a narrowly scoped staging content provider inside Console for downloaded exports; that is distinct from P5's browsable remote `DocumentsProvider`. Choose eager staging or an on-demand stream only after receiver timeout/access-pattern tests. Show preparing/downloading progress and a Save/Share alternative when a receiver cannot wait.

## Editing, queue, and consistency

Use the same journaled transfer service as Desktop. A job captures source identity/version, source/destination, operation, size if known, grant lifetime, state, bytes, integrity result, and remote commit result. States are `queued → preparing → transferring → verifying → committing → completed`, with explicit blocked/conflict/canceled/failed outcomes. Persist jobs so a window close does not create an invisible upload. Recovery queries partial state; it does not repeat a destructive publish.

Remote editing downloads to bounded staging, remembers the source version, opens a supported local editor through a URI grant, detects an explicit save/return, checks for remote changes, and uploads a temporary file before publish. A changed or unknown version preserves a conflict copy. A closed editor or 100% byte count is not proof of remote commit. Hash verification uses supported server methods or a clearly stated readback; SFTP alone does not guarantee a server-side hash API.

Follow the [file-publication contract](file-publication.md): a check followed by rename does not prevent another writer from changing the destination in between. On backends without guaranteed conditional replacement, the user's selected policy asks before **every remote overwrite**, with save-copy/cancel alternatives and no remembered blanket consent. Bind the decision to the exact file and save generation, retain local autosaves as pending, and reconcile ambiguous completion before another attempt. Record the backend's actual guarantees rather than claiming universal conflict-free editing.

Directory transfers need a manifest, cancellation and partial-result summary, bounded concurrency, and explicit link handling. Do not follow remote symlinks recursively by default or treat Windows/WSL/container paths as interchangeable. Destructive actions name the selected host/path and provide confirmation/recovery semantics appropriate to backend support.

## Design and qualification

The native file pane uses the canonical Cloudy file table, breadcrumbs, focus ring, drag target, and transfer states. Reserve stable geometry for host/path so a tab switch cannot hide the destination. At narrow desktop window widths it becomes a reachable dock/drawer or separate file window, preserving binding. Pointer and keyboard Copy/Upload/Download/Open path actions offer the same operations.

Qualify two concurrent hosts with different roots, tab switches during a drag, nested shells, missing SFTP subsystem, revoked grants, directory drops, Unicode/reserved filenames, large files, source mutation, disk-full, conflicting edit, process death, and a receiving editor that reads late or seeks. Verify hashes/versions and the absence of automatic terminal submission. No Desktop installation or custom host agent may be needed for the required session file workflow.
