# Console Files action UI on the API 37 emulator

**Date/stage:** 2026-10-18, P2-S7/M4 product UI slice. **Outcome:** scoped emulator workflow
PASS. This is not a Googlebook-device, physical-input/IME, accessibility, upload/download,
staged-edit, transfer-journal or Q-C09 result. C18 remains partial.

## Scope and build

The Files screen now exposes new-folder, per-entry rename, per-entry delete and explicit
overwrite-replace consent. Each action calls the retained transport's real `SshFiles` methods;
none is a local-only or placeholder operation. Delete always requires model-held consent, and a
rename first tries a non-overwriting rename: a real remote `ALREADY_EXISTS` produces the replace
consent, which then calls the explicit-overwrite rename. The screen's scope text was corrected to
say uploads, downloads, staged editing and local export are unavailable.

- Branch `codex/console-r1-implementation`; source commit `ad4f84c` (the APK was built from that
  UI source before this evidence file was committed). The screenshot/run used debug APK SHA-256
  `ce3c3339832ac6383596a28026aa454d0c4ab27903b72fadd59c5593fe339bcb`.
- Emulator: API 37 Google APIs x86_64, 2880×1800 at 240 dpi; package
  `io.github.baitian6641.cloudy.console` (debug), installed with `adb install -r`.
- Fixture: tracked MINA 2.20.0 terminal fixture run `run-XF20Ed`, port `32973`, fingerprint
  `SHA256:DtCVbrArfYSaZ+ZiIIc3nL2G8qfB1ROvkC5cI83WCtU`, with `sshd-sftp` rooted at its private
  `sftp-root`. `adb reverse tcp:32973 tcp:32973` linked the app to the host for this run only.
- Test setup wrote a disposable profile/port and accepted host pin into the emulator's app-private
  stores. This is harness preparation, not a product trust or registration journey; it does not
  establish a supported trust flow and the seeded values are not reusable credentials.

## Actual workflow observed

The emulator was driven through the real UI hierarchy (UIAutomator dumps); the final state is
captured in [the screenshot](2026-10-18-console-files-actions-preview.png).

1. Home → `SFTP UI fixture` → `Browse SSH files`; the Files screen connected with the fixture's
   generated password and showed the private virtual root at path `.`.
2. `New folder name = demo` → `Create folder`. The refreshed listing showed `demo` as a directory
   with `Open directory`, `Rename` and `Delete`.
3. Opened `./demo`; created `inner`; opened the inline rename field, replaced `inner` with
   `renamed`, and confirmed. The listing showed `renamed` and the new name.
4. `Delete` on the empty `renamed` opened the consent surface with
   `Confirm remote change`, `Delete renamed?`, and `Delete`/`Cancel`. Confirming removed the entry;
   the listing returned to empty.
5. Added private fixture files `alpha.txt` (13 bytes) and `beta.txt` (12 bytes) on the fixture's
   virtual root; `Refresh directory` published both regular files.
6. Renamed `alpha.txt` to the existing `beta.txt`. The non-overwrite attempt returned a real
   `ALREADY_EXISTS`; the UI showed `Confirm remote change`,
   `Replace the existing beta.txt with alpha.txt?` and the explicit replace note. Confirming
   `Replace` left one `beta.txt` with 13 bytes.
7. `Read preview` of the replacement decoded `alpha-content`, proving the destination content came
   from the renamed source rather than the prior 12-byte file.

No command was replayed, no directory was removed recursively, and the session was closed through
the activity's Close action after the run. The fixture was stopped and the `adb reverse` removed.
The emulator retains the disposable seeded profile/pin; it is not a repository artifact.

## Verification boundary

- `:platform:lintDebug` and `:console:lintDebug` pass with zero findings after a separate inherited
  `UseKtx` repair (`5cc180d`).
- `:platform:testDebugUnitTest` and `:console:assembleDebug` pass; the engine's real-server host
  run remains [2/2](../P1-S3/2026-10-18-sftp-writable-engine.md).
- The workflow above is emulator-only. Physical Googlebook input, IME, accessibility, narrow
  tiling, screen-reader announcements, upload/download pickers, staged editing, transfer journal,
  integrity, overwrite recovery and Q-C09 remain unqualified. The delete flow for a non-empty
  directory was host-qualified only; the UI surfaces the engine's `OPERATION_FAILED` without a
  recursive fallback.
