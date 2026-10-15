# Remote file publication and concurrent edits

**State:** proposed implementation contract, refined after the final review. **Applies to:** Console staged editing, Desktop transfers and the remote document provider. **Stages:** P2-S8 shared core; P2-S7, P3-S3 and P5-S3 app integration. **Reference:** API33, R34 and R35 in the [API tracker](../03-platform/googlebook/api-tracker.json) and [source register](../07-reference/source-register.md).

## The concurrency boundary

Reading remote metadata or a hash, uploading a temporary file, checking again, and renaming are separate operations. Another writer can change the destination after the last check. OpenSSH's [`posix-rename@openssh.com`](https://raw.githubusercontent.com/openssh/openssh-portable/master/PROTOCOL) version 1 requests POSIX rename with old/new paths; it has no expected-version argument. Therefore that extension alone cannot make the check and replacement conditional on the same version.

The [historical SFTP v3 draft](https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02) describes `SSH_FXF_CREAT | SSH_FXF_EXCL` and the separate rename request. It is an expired draft used to explain the protocol, not a current IETF standard or Googlebook API. Qualify the selected server and engine. Metadata such as size and modification time can miss same-size or closely timed changes; hash checks also leave a race before publication.

Atomic visibility, conditional replacement, transfer integrity and power-loss durability are separate capabilities. A successful rename may establish the published name without proving that an intervening writer was preserved or that storage survives power loss. An advisory lock only protects against writers that cooperate with it. Do not advertise universal conflict protection from a local lock, a hash, or ordinary SFTP alone.

## Backend capability record

For each qualified backend/server, record protocol/extension versions, exclusive creation behavior, rename/replace behavior, expected-version support if any, cross-filesystem limits, supported metadata preservation, and the meaning of its acknowledgement. Unsupported capabilities remain explicit.

| Publication mechanism | What must be established | Consequence |
| --- | --- | --- |
| New copy under an unused name | Exclusive creation or a qualified no-replace publish; a listing-before-create check is insufficient | An existing original/collision target remains unchanged; retry a generated name only after an unambiguous collision result |
| Conditional replacement | The backend validates the expected version and replaces under one guaranteed operation, or a coordinated mechanism demonstrably covers every writer | Automatic replacement can be considered within that exact scope |
| Ordinary replacement | The server can publish the staged data but cannot condition it on the original version | Present the limitation and apply the selected explicit save policy; never label this race-free |
| Unsupported or unknown | Server/extension behavior or completion is not established | Retain local staging and offer a supported destination or recovery action |

A unique name reduces collision probability but does not replace server-side no-replace semantics. If only exclusive direct creation is available, identify the new file as incomplete until writing and verification finish; never claim atomic visibility. Do not delete an existing destination to emulate atomic replacement.

## Save policy decision

**User-selected policy:** on a server without guaranteed conditional replacement, **ask whether to overwrite on every remote save**. See [Revision 02](../00-project/revision-02-final-review.md). The prompt names the host and exact destination, the staged edited revision, the most recent remote observation and its limits. Offer **Overwrite this save**, **Save a copy**, and **Cancel / keep pending**. There is no remember/always-overwrite checkbox or inherited consent from a previous save.

Consent is bound to the file, destination and staged save generation. Editing again, switching the destination, losing the connection before a decision, or discovering a newer remote observation invalidates the pending confirmation and requires a refreshed decision. For a batch, list the exact files/revisions and require a deliberate decision for each replacement; a broad background batch setting cannot waive future saves.

Local editor autosaves may update local staging and show Pending remote save. Each attempted remote publication still needs its own decision. Avoid stacking dialogs by presenting the latest pending revision; consent for an obsolete revision must not publish a newer one. Cancel retains the edited content and leaves the original remote file untouched.

When a backend genuinely supports conditional replacement, its qualified version check can protect the operation. A failed condition becomes a conflict requiring a new choice; never downgrade silently to an ordinary overwrite. New-file uploads use qualified no-replace semantics and report collisions rather than treating them as permission to replace an existing file.

An explicit overwrite remains a deliberate destructive operation. Recheck the displayed destination and latest observation before sending it, explain that concurrent writes can still be lost on an ordinary backend, and retain the staged edited content until the result is confirmed. User consent does not create a stronger server guarantee. Save-copy is an offered alternative, not the selected automatic default.

## Journal, interruption and recovery

Capture host identity, source/destination, original observation and its reliability, local staging hash, chosen publication operation, capability snapshot, request/generation ID and result. Distinguish prepared, transferring, verified, publication requested, published-as-copy, replaced, conflicted, canceled, failed and completion-unknown.

After a timeout or process loss during publication, query the actual named objects and record what is known. Do not blindly repeat rename, delete, overwrite or a shell command. A matching content hash is evidence of bytes, not proof that a particular writer/request won. Preserve local work and ambiguous remote objects until recovery is resolved. A local editor's save notification or close event does not establish a remote commit.

## Required concurrency cases

1. Another writer changes the destination between the final observation and publication; verify the selected policy and preserve the user's local work.
2. A same-size change occurs within the server's timestamp resolution; verify that weak metadata is not advertised as a version guarantee.
3. The generated copy name already exists, including a collision introduced after directory listing; never truncate it.
4. Permission, rename extension or cross-filesystem support is missing; keep a usable staged copy and explain the actual failure.
5. The server performs the publication but the reply is lost; reconcile without repeating a destructive operation.
6. The client or editor dies during transfer/publication, or its URI grant expires; recover staging and journal state without treating a closed editor as success.
7. Two Cloudy windows/apps edit the same file while an independent remote editor also writes; app-local locks do not substitute for backend concurrency guarantees.

Run these through the shared transfer engine and again through actual document-provider consumers. Q-F01 is the focused case, supplementing Q-C09, Q-D03 and Q-D05. Record exact server/engine identities and evidence paths; no current result is claimed.
