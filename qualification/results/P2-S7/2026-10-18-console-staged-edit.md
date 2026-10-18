# Staged remote edit, bounded transfer journal, and per-save consent

**Date/stage:** 2026-10-18, M4/P2-S7. **Outcome:** scoped host and emulator PASS. C18 remains
partial: upload/download, transfer integrity, physical-input/accessibility and Q-C09 are not
qualified by this slice.

## Implemented

- `SshFileEditor` two-phase edit: `open` captures a bounded regular file and its kind/size/mtime
  version; `stage` writes the replacement to an exclusive same-directory temp; `commit` re-stats
  the target, refuses a changed target without explicit `replaceChanged`, then renames with
  explicit overwrite; `discard` removes only the temp. Version equality compares kind, size and
  second-resolution mtime, so this is conflict detection, not atomic compare-and-swap.
- `FilesTransferJournal` app-private, bounded (64) write-ahead status: STAGED → COMMITTING →
  COMMITTED/CONFLICT/FAILED, with COMMITTING promoted to UNCERTAIN on load after process death.
  It stores profile id/revision, remote and temp paths, byte count, consent bit and timestamps;
  no credentials or content. Terminal records may be evicted oldest-first; active/uncertain
  records are never evicted. No transfer is replayed.
- Files UI: Edit text for regular files, monospace editor, dirty state, explicit per-save
  confirmation, a separate conflict-replace decision, discard confirmation, and recent
  transfer-state rows. Saving updates the listed entry from the committed stat (not a possibly
  stale post-save directory read).

## Host verification

Fixture run `run-K8eL0k` (MINA 2.20.0, port 43325, private virtual SFTP root), explicit
`CLOUDY_SSH_KEY_FIXTURE_ROOT`:

- `SshStagedEditHostTest`: **2/2 pass**. Unchanged commit replaces the exact bytes; staging leaves
  the target untouched; a host-side concurrent write produces a real `Conflict` with the current
  size, keeps the temp, leaves the target at the newer bytes, and explicit replace then commits;
  discard removes the temp; empty saves and the 65,536-byte stage bound behave as specified.
- `SshWritableFilesHostTest`: **2/2 pass**, including empty exclusive create.
- Full `:ssh:test`: 99 tests, 0 failures, 40 opt-in skips; staged/writable host tests ran
  separately with the fixture.

## Journal unit verification

`:platform:testDebugUnitTest`: 33 tests, 0 failures. `FilesTransferJournalTest` covers:
STAGED/COMMITTED reload, COMMITTING→UNCERTAIN and persistence, terminal eviction with an
all-active capacity refusal, arbitrary Unicode/percent path round-trip without a leftover temp,
and malformed-journal rejection without overwriting the original.

## Emulator workflow

API 37 emulator, 2880×1800 at 240 dpi, debug APK SHA-256
`0376a90f53e221907db922198f4b5a8fd7f7bb973f96f8d4afcddd936ae835bc`; fixture run `run-YWsEGV`
(port 46363, fingerprint `SHA256:293WlBMgtmppNyA6YwuMH01PUvQzAVVPFH1xaoRnKBo`), `adb reverse`
and a disposable seeded profile/pin (harness only, not a trust-flow pass).

1. Opened `./note.txt`, edited the text, selected Save changes, confirmed the per-save replace,
   and observed the host file become `hello editorchnged` with a `Committed` transfer row.
2. Reopened the file, changed it in the editor, then changed the same file on the host outside the
   app. Save → confirm produced `Operation status: CONFLICT`, the "Remote file changed" surface
   and unchanged host bytes `external content!`; the staged temp was not committed.
3. Selected Replace remote file; the host file became the edited text and the listing reported the
   committed 22-byte size. [Conflict consent screenshot](2026-10-18-console-files-conflict-consent.png).

The first save exposed a stale post-save directory size (12 though the host file was 18). The
model now updates the edited entry from the committed stat; the conflict run then showed 22 after
commit. This is recorded rather than hidden.

## Boundaries

Same-size writes within one mtime second can evade conflict detection. Process-death UNCERTAIN is
proven by the journal unit test, not by killing the app mid-commit. No upload/download, URI-grant
copy, recursive operation, automatic retry/merge, integrity digest, physical Googlebook input,
accessibility, or Q-C09 qualification is included. The emulator retains disposable seeded state
and journal rows; the fixture and reverse mapping were stopped/removed after the run.
