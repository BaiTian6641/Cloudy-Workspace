# P0-S3: SSH journal archive, restoration and new-batch maintenance

**Run:** 2026-10-15, approximately **11:47–11:52 UTC**. **Observed result:** the full eight-attempt SSH journal was preserved in workstation and app-private archives, restored through the actual installed application with every attempt and trust entry unchanged, then replaced by a separately provisioned zero-attempt batch. **Public report review:** primary and independent review accepted; all 120 selected artifact hashes/sizes, receipt ledgers, startup journals and restored history/trust were checked. This is operator-performed qualification maintenance; P0-S3 remains in progress and CAP-SSH/CAP-TERMINAL remain pending.

## Exact scope and identities

The installed application remained **0.6.0 / code 8**, package `dev.cloudy.qualification.baseline`, source **`af9deaed594fe53fbe52c4825a2e3e96a0e8fd83`**, build reference **`49607be8-a053-4f7f-8d22-3764b7e63136`**. The installed APK hash was checked in each successful maintenance phase and matched **`4f673be2ca5c0f2f4dcf9659645c827885f3aa4ee0952405d64d5f4c3d1a4f40`**; its preserved package is **15,827,911 bytes**. The [package audit](../../../docs/06-validation/2026-10-15-ssh-pty-probe-package.json) and [accepted device evidence](2026-10-15-ssh-pty-probe.md) retain the application, HP Googlebook 14 / Android 17 / API 37 and backend identities. The maintenance checkpoint was **`297cd24a73bfce16b3fed5726c35f387748398fd`**; it does not replace the APK's execution-source identity.

The primary operator performed the actions following the [batch handoff](../../../docs/03-platform/googlebook/p0-s3-journal-batch-handoff.md). The evidence author inspected the frozen records and final screenshot without running a device command, listener or build. The [manifest](2026-10-15-ssh-batch-rotation-manifest.json) binds **120 selected private artifacts**, their sizes and SHA-256 hashes, plus the exact projections and comparison boundaries. The existing [SSH/SFTP](2026-10-15-ssh-sftp-probe.md) and [exec/PTY](2026-10-15-ssh-pty-probe.md) reports remain historical execution evidence.

## Preserved failure and corrected transport

The first archive action failed. Its `exec-out` path supplied a quoted whole shell program as the command name, so the remote program was not executed. Host status **0** did not establish remote success: errors appeared in stdout, and archive readback returned a missing-file error. The helper stopped with `RuntimeError / Device archive differs`. No device archive was created by that action, and independent subsequent readback confirmed that the active journal remained byte-identical to the original. The failure directory and unsuccessful archive export remain preserved.

The corrected helper uses `adb shell -T` with shell-v2 and a single quoted remote argument vector. Before retrying, actual preflights verified remote exit **17**, successful exit **0**, and distinct fixed stdout/stderr sentinels with exit 17. Each of the seven subsequent phases repeated the exit-17/stream probe. All **119** successful-path command receipts had their expected status: seven intentional exit-17 probes and 112 exit-0 operations. These checks establish the tested command transport; they do not convert the earlier host status 0 into success.

The original and corrected helpers are separately hashed in the manifest. The retry used a fresh evidence directory, so the failed action was neither overwritten nor silently discarded.

## Actual archive and restoration

The original journal is **23,309 bytes**, SHA-256 **`49b888efcf0c91ebabe7b1044ed22b5c7b99bcb0e175026a01c1f71afa99d664`**. Its workstation copy and app-private `files/ssh-archive-20261015-01/ssh-journal.json` were byte-matched. **Ten** device archive readbacks across the subsequent phases continued to match those exact original bytes.

Before replacement, the selected package was force-stopped for its actual user. The successful archive and three replacement phases retained **25** own-package absence samples, including checks before replacement and explicit launch. The helper checked regular files, absence of unexpected AtomicFile companions, unchanged original bytes, bounded temporary-file readback and the installed result of a same-filesystem rename. These are sampled, stopped-package checks; no multi-file transaction or power-loss durability is claimed.

The **193-byte** seed contains only schema version 2, zero attempts and the exact existing trusted-host map. It contains no invented application process, revision, build or timestamp. A real JVM preflight compiled and ran a harness against the actual `SshJournalCodec`, accepting the seed with zero attempts and exact trust. The recorded compiled-codec hash was independently rechecked. Android parsing, persistence and startup were then exercised separately by the following launches.

| Explicit startup | Observed app process identity | Journal result |
| --- | --- | --- |
| First seed | `7e31cec2-6a5f-433b-93a7-448f2ff0891f` | Zero attempts, original trust, revision 1; three byte-identical samples over **1.675859 seconds** of host read-completion observations. |
| Restored original | `702734c9-db9b-4d7b-94ba-6de735b888d6` | All eight original attempt objects and original trust exactly equal after decoding, revision 1; three byte-identical samples over **1.692485 seconds**. |
| Final seed | `09d58378-a668-44f7-bddb-813519b25fa7` | Zero attempts, original trust, revision 1; three byte-identical samples over **1.697101 seconds**. |

All three process identities are distinct and differ from the original writer. Restoration preserved the two prior SFTP completions, trust-required, authentication-failed and changed-key-rejected rows, and the completed, interrupted and cancelled exec/PTY rows. Every original event, detail and provenance field was compared exactly; no attempt was rerun or relabelled. The restored application's new top-level process/revision/timestamp metadata means its rewritten journal is not byte-identical to the archive, even though its attempts and trust match exactly.

Four private comparison records also establish unchanged SSH configuration, client-key bytes and transfer journal across maintenance. No key material, private-key digest, endpoint identity or raw command output is published.

## Native observations and limits

Three actual **Open SSH transport qualification** taps were matched to fresh captures of that exact own-app control; each coordinate was inside its captured bounds and each command returned exit 0. These were injected taps, not physical-input qualification. The final screenshot was inspected by the primary operator and evidence author: it shows the SSH screen with `ready`, zero attempts, revision 1 and the final process identity. The screenshot remains private because it also contains trust and desktop information. Clickability alone is not treated as proof of an enabled button.

The precheck found both selected fixture listeners refusing connections and no live own fixture processes; seven phase checks also observed refusal on the selected SSH endpoint. The journal samples record no new SSH attempt during these bounded intervals. No packet capture was performed, and a closed listener is not proof that no connection was attempted. Host command times and Android snapshot times remain separate clocks.

This result qualifies the exercised archive/restore procedure and provides a preserved starting point for a later batch. It is not an in-app history-management feature, remote-file re-audit, background-session result, crash-durability guarantee or capability promotion. Later operations under the same private device directory are outside this frozen maintenance record.
