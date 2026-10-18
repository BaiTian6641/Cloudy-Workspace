# Console file upload/download with document grants, integrity and journal

**Date/stage:** 2026-10-18, M4/P2-S7. **Outcome:** single-file upload/download emulator
workflow PASS, with real-engine host checks. C18/C19 and Q-C09/Q-C10 remain partial; no physical
Googlebook, drag-and-drop, multi-file/directory, late/seekable receiver or sustained-use result is
claimed.

## Implemented boundary

- `SshFiles.readTo`, `writeFrom` and `digest` stream one regular file in 32 KiB chunks with a
  64 MiB bound, caller cancellation, recoverable typed failures, partial-write cleanup and SHA-256
  over the bytes actually transferred.
- Console uses Android document grants: `OpenDocument` for an upload source and `CreateDocument`
  for a download destination. The upload resolves its display name and size on the worker; the
  download writes a verified app-private stage before copying to the selected URI.
- Uploads stage to an exclusive same-directory temp, verify the remote temp by SHA-256/byte
  readback, and only then publish. A missing destination publishes directly; an existing
  destination presents **Overwrite / Save a copy / Cancel**, bound to that stage. Save-copy picks
  an unused name with no-overwrite rename. Cancellation cleans the remote temp.
- Downloads record the remote digest, stream to a stage, compare the stage SHA-256/bytes, then
  copy to the document URI and require the written byte count to match. Close errors fail the
  operation. The local hash is shown as the SHA-256 of verified bytes, not a server-side hash.
- The existing bounded transfer journal is used for both directions; it now accepts the 64 MiB
  transfer bound and can revise the final path and verified byte count on transition. Save-copy
  rows therefore name the actual copy, not only the intended target.

## Lifecycle defects found and repaired

1. Launching the document picker stopped the Files Activity, and the existing owner-stop policy
   disconnected the SSH session before the result. The first upload produced no remote file and
   the UI returned to "Ready to connect". The repair is an explicit, bounded 120 s picker handoff
   plus owner reattachment in the activity-result callback. No reconnect, replay or hidden retry
   was added.
2. The post-upload directory read could publish an empty/stale list even though the rename had
   succeeded (observed after the first successful upload; a manual refresh showed the file). The
   commit now stats the published path and inserts/updates the verified regular entry in the same
   publication.
3. The upload journal began only after staging, and a save-copy retained the original target path.
   The journal intent is now recorded before bytes stream, and the verified final path/byte count
   are revised on commit.

## Actual checks

| Check | Result |
| --- | --- |
| Real MINA fixture, `SshStreamTransferHostTest` (run `run-XPqJHH`, port 44759) | **2/2 pass**: 200,000-byte stream upload/digest/download/exclusive refusal; limit, cancellation, failing local sink and transport survival |
| Journal unit suite | **6/6 pass**, including transfer path/byte-count revision |
| Full `:ssh:test` | Run separately; opt-in host tests skipped without fixture |
| Emulator upload/download (fixture `run-Ys6oPp`, port 44961; APK `d3c94af7…`) | Uploaded `cloudy-upload.txt` (21 B); host content/hash matched; downloaded through `CreateDocument` to `/sdcard/Download/cloudy-upload.txt--` (21 B, exact text); duplicate upload showed Staged + Overwrite/Save a copy/Cancel; Save-copy created `cloudy-upload (copy).txt--`; a second duplicate with new content and Overwrite changed the exact host target to 27 B |
| Final APK upload after guard changes (fixture `run-p7nmLr`, port 33479; APK `5f68f093672dbc87c1f5573d06e80dba8eb9bad4573fecbfc07d9121859224b1`) | Uploaded `finalcheck.txt` (21 B), host content matched, 21 verified bytes and SHA-256 `02c869ac…` published, and the new entry appeared without a manual refresh |

The running package was installed with `adb install -r`; the fixture host key was seeded into the
test-only app-private trust/profile stores, which is harness preparation, not a product trust-flow
qualification. The harness picker selected a local Download file and an Android document destination.
The fixture, reverse mapping and private temp copies were stopped/removed after the run.

## Remaining boundaries

Single file only; no multi-file/directory manifest, drag-and-drop, staged content-URI export for a
receiver, post-commit terminal reference insertion, or cross-host copy. The 64 MiB bound, delayed
or seekless providers, grant revocation, source mutation, disk-full, process death during the SAF
copy, and UI cancellation remain unqualified. Downloads verify the remote digest and copied byte
count but cannot read back the provider's final bytes; no server-side hash is assumed. C18/C19 and
Q-C09/Q-C10 remain open.
