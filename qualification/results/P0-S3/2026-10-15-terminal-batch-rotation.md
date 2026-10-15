# P0-S3: terminal journal preservation, restoration and new batch

**Run:** 2026-10-15; initial precheck failure at **14:25 UTC**, successful seven-checkpoint drill at **14:32–14:33 UTC**. **Observed result:** the full eight-attempt, 48-event terminal journal was preserved in workstation and app-private archives, restored through the installed application with every attempt and trust entry unchanged, and then replaced by a separately provisioned zero-attempt batch. Independent review of the raw maintenance evidence is accepted. **Primary and independent public report/manifest review are accepted.** This is operator-performed qualification maintenance; P0-S3 remains in progress and CAP-SSH/CAP-TERMINAL remain pending.

## Scope and exact application identity

The installed diagnostic remained **0.7.1 / code 10**, package `dev.cloudy.qualification.baseline`, execution-source commit **`130b238e0077fe0bf0d7ab668c38771c51224243`**, build reference **`60908d62-c573-4c4b-8b0e-910e5863197e`**. Each successful maintenance phase checked the installed APK against SHA-256 **`629b71bf4cd006889252d578106fc494d09593381738e5a3e240d8f78c2a4611`**. Its preserved package is **16,636,002 bytes**. The [accepted terminal probe](2026-10-15-terminal-probe.md) and its [manifest](2026-10-15-terminal-probe-manifest.json) retain the HP Googlebook 14 / Android 17 / API 37 identity, prior host identity, original attempt outcomes and exact installed-source/package provenance.

The primary operator executed the private helper one checkpoint at a time. The report author inspected records without running a device command, listener or build. The [maintenance manifest](2026-10-15-terminal-batch-rotation-manifest.json) binds **146 selected private artifacts**, seven command-receipt ledgers and the sanitized comparison results. The exact executed helper is preserved privately with SHA-256 **`eae69be12b3d0b248f32e200c621928fbe12d008099fcd3ccc51bbe446608416`**. This procedure concerns the ordinary terminal journal and its own trust map; the earlier [SSH journal rotation](2026-10-15-ssh-batch-rotation.md) remains a separate maintenance record.

## Preserved precheck failure and retry

The first `archive` action stopped with **`MaintenanceError / process-rows`** at its 56th command receipt, the process-list precheck. Unrelated process names containing spaces did not fit the original three-token parser. The failure occurred before force-stop or archive creation; the original journal remained byte-identical. A protected-file baseline had already been written locally. The operator preserved that exact baseline in the failed action directory instead of overwriting or discarding it.

A separate recovery preflight confirmed the unchanged original journal and all ten protected files, absence of the device archive, and absence of completed archive/seed outputs. The correction requests numeric Toybox identities using `ps -A -n -o UID,PID,NAME` and splits each row with `maxsplit=2`. It retains numeric UID/PID validation and exact selected-user/package-name matching. The successful retry used a new `archive-retry` directory; the initial failure and recovery receipts remain preserved.

All seven successful phases repeated an actual shell-v2 test with exit **17** and distinct fixed stdout/stderr sentinels. Across **448** successful-path command receipts, seven were these intentional exit-17 probes and **441** returned exit 0. The manifest commits each phase's full numbered-receipt set using a deterministic filename/size/SHA-256 ledger; raw command contents remain private.

## Archives, seed and actual restore

The original terminal journal is **20,670 bytes**, SHA-256 **`8d35a416f8b1669c0ad9b841fd7e275a5b9c9d226eea8ea636d4059b23513441`**. The workstation archive and app-private `files/terminal-archive-20261015-01/terminal-journal.json` matched those exact bytes before replacement. **Ten device archive readbacks** across the completed drill continued to match the original.

The strict terminal schema requires six fields. Its **329-byte** zero-attempt seed preserves the original `schemaVersion`, `processInstanceId`, `revision`, `snapshotAtDeviceUtc` and `trustedHosts`, changing only `attempts` to `[]`; optional provider observations are omitted. The copied process/revision/time remain **historical provisioning metadata**, not a newly observed application identity. A real JVM harness using the actual compiled `TerminalJson` and `TerminalJournalCodec` accepted the original and seed, checked exact trust and copied metadata, and rejected missing required metadata. The helper also checked all 119 installed-source hashes, the compiled parser/codec hashes and APK/source bindings before performing a maintenance action.

The archive phase and each of the three replacements force-stopped only the diagnostic package for the verified current Android user. **Twenty-three post-stop/replacement absence projections** cover the archive and replacement boundaries. Regular-file and symlink checks, refusal of unexplained AtomicFile `.bak`/`.new` companions, bounded temporary-file readback, unchanged-current-file checks, same-filesystem rename and installed-byte readback guarded each replacement. Each replacement left the package stopped. The operator then explicitly launched the installed diagnostic and opened its terminal screen without pressing Connect.

| Explicit startup | Observed process identity | Actual journal result |
| --- | --- | --- |
| First seed | `20868cc1-c1e2-4fbb-905f-3df25de5dcc6` | Zero attempts, original trust, revision 1; three byte-identical samples over **1.877723 seconds** of host read-completion observations. |
| Restored original | `e3c14606-3bfb-4230-b20c-8f5e5d826353` | All eight original attempts and 48 events, exact original trust, revision 1; three byte-identical samples over **1.889354 seconds**. |
| Final seed | `2b3e875c-fc30-455c-bf07-978f3ed46aa0` | Zero attempts, original trust, revision 1; three byte-identical samples over **1.867747 seconds**. |

All three UUIDs are distinct and differ from the archived writer. Each startup wrote a fresh device timestamp and actual provider-order observations. Restoration preserved every original attempt object, including events, details, session identity and 0.7.0/0.7.1 build provenance; no prior attempt was rerun, reclassified or upgraded. The restored journal's new top-level runtime metadata differs from the original archive, while decoded history and trust match exactly. The original terminal report's rapid-input failure and other limitations remain unchanged.

Across the successful phases, **120 private hash observations** confirmed unchanged bytes for **ten protected files** spanning terminal/SSH/transfer configuration, private keys, the other operation journals and prior archives. The successful baseline also matched the preserved failed-run baseline. Public evidence exposes only comparison counts and outcomes; configuration contents, endpoints, usernames, device identifiers, trust fingerprints and private-key/configuration digests remain private.

## Native observations and qualification boundaries

Three actual **Open interactive terminal qualification** taps were matched to fresh captures of the own-app control. Each coordinate was inside its captured bounds, and each launch, capture and tap command returned exit 0. Subsequent own-app layouts showed terminal status **`closed`** after restoration and **`ready`** after the final empty batch. These are injected native-control observations. Clickability alone is not treated as proof that a button is enabled; no physical keyboard, mouse, IME, accessibility, bulk-input or TUI result follows from them.

All seven successful phase checks observed connection refusal on the selected fixture endpoint. No Connect action appears in the reviewed maintenance controls and no new terminal attempt appeared in the sampled journals. This is bounded evidence of the exercised idle maintenance path, not packet-level proof that no connection was attempted. Host command/read-completion timestamps and Android snapshot timestamps are separate clocks.

The result provides a preserved, actually restored starting point for the next qualification batch. It does not implement product history management, requalify terminal rendering, establish background-session behavior, prove power-loss durability or multi-file atomicity, or promote a capability gate. Later operations in the same private device directory are outside this frozen maintenance evidence set.
