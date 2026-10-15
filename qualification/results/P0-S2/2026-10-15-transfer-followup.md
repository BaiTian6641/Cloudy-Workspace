# P0-S2: transfer hostname, task-removal and shell-stop follow-up

**Run:** 2026-10-15, approximately **09:08–09:19 UTC**. **Verdict:** a trusted-chain/wrong-hostname connection was rejected before payload creation; a separate finite download continued after native task closure and later completed with exact bytes; a current-user shell stop terminated the process, which automatically returned before explicit UI relaunch and reconciled the unfinished attempt without an observed new HTTP request. **This is bounded follow-up evidence. CAP-PLATFORM remains an experiment; no product gate is supported.**

## Identity and preserved first batch

All observations use the already installed **0.4.1 / code5** diagnostic, package `dev.cloudy.qualification.baseline`, source [a2b3e33d3ba4dabf40bdd1730e1eaf582c84527d](https://github.com/BaiTian6641/Cloudy-Workspace/commit/a2b3e33d3ba4dabf40bdd1730e1eaf582c84527d). Its **6,858,313-byte** APK SHA-256 is `704546e5d6a24167c7c982cf3be0e4ab27e2a1fe86221e69aa68f7a1f42d2396`. The [prior transfer manifest](2026-10-15-transfer-lifecycle-manifest.json) binds deployment and the separate compiler/lint/61-test checks. Newer **0.4.2 source/audit changes were not installed during this run** and gain no device result from it.

The device remains **HP Googlebook 14, Android17 / API37, arm64-v8a**, build `CL3B.260622.276.R2`, incremental `16560795`, security patch 2026-09-01. The primary agent performed all device/fixture actions; this report's author inspected the saved artifacts without device access. Host UTC, device UTC and device elapsed time are separate clocks. Taps were injected; physical keyboard, mouse, touch and IME behavior were not tested.

Before starting batch02, the primary agent preserved all eight attempts from the [earlier run](2026-10-15-transfer-lifecycle.md). The private archive record reports the probe process absent before export and matching device archive hashes. The author independently hashed the **180,374,016-byte** tar file and all **eight regular members**, matching every recorded size/digest. Archive SHA-256: `0ec185060a359996c09bdffa62a5aa382ef6c5dd32d13f1f5396a99b227b668a`. The members contain the journal, private configuration and six complete/partial payloads. Failures were preserved; this was a deliberate new qualification batch, not deletion of failures to make the original eight-slot matrix pass. The tar contains endpoint configuration and CA material and remains private.

The generated fixture remains **67,108,864 bytes**, expected SHA-256 `5302fb4146dff35d2ed3784bc3c8f005e2d89c19c2c722387a1f265a8236ee1f`, with an intentional **524,288-byte/s** throttle. No throughput, physical packet-route or general VPN result is inferred.

## Wrong hostname with the same trusted CA

Batch02 attempt1 used a replacement server leaf certificate with a mismatched endpoint identity. The preparation record confirms unchanged Android/host configuration, CA and server key; only the original leaf file changed. Recorded certificate checks accepted the CA chain and the alternate SAN, while rejecting the actual endpoint identity. These are distinct checks from the earlier wrong-CA case.

The first exported sample was still queued, so it is not treated as a terminal result. The settled journal reached **`failed` / `SSLPeerUnverifiedException`**, revision **3**, with a claimed worker, **zero bytes**, no verified-completion fields and no payload file. The dedicated wrong-host server log has no HTTP request event. This establishes rejection of this hostname mismatch on the identified Android build; it does not qualify every certificate chain, expiry condition or production trust policy.

The listener was stopped before restoring the original correct leaf. Its restored hash matched the original. Subsequent positive cases used that restored certificate.

## Native task closure while a transfer was running

Batch02 attempt2 was running when the primary agent tapped the freshly inspected native **Close Cloudy device baseline probe** caption control. The injected coordinate was inside that control's observed bounds. The window belonged to task **1000154**.

The saved journal advanced from **4,849,664 bytes / revision597** before the close to **5,373,952 / revision661** immediately afterward. A later scoped system observation reported the process present and zero own task-record lines; the journal reached **12,976,128 bytes / revision1589**. The independently exported live file was **13,565,952 bytes**. Those are separate samples during active writing, not an atomic byte-count comparison.

The user interface was then explicitly reopened while the same process and attempt were still running, at **33,423,360 bytes / revision4085**. The later terminal journal reached **`completed`**, revision **8199**, with **67,108,864 verified bytes** and the expected digest. The author independently hashed the exported completion payload and obtained the same length/digest. The fixture logged one request and its complete write.

This establishes continuity across the observed native task removal and subsequent return. It does not claim the whole download completed while no window existed, that every app survives task removal, or that an Activity's final callbacks were persisted. The earlier files named `batch-02-attempt-02-final-*` were still running and are excluded from completion proof; the later **`completion-*`** journal, observation and payload are the accepted completion evidence.

## Documented shell stop test and automatic process return

Google documents `adb shell cmd activity stop-app PACKAGE_NAME` as a test for user-initiated app-stop behavior. This run used that shell path; **the actual Task Manager UI Stop action was not used**. The observed UIDT behavior is reported directly, without treating the FGS-oriented testing guide as proof of every UIDT user-stop rule. [Official stop-test guidance](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

Batch02 attempt3 was running. The first command, without explicit user selection, returned zero but had **no observed effect on the active process**: all three process samples remained present, and the saved bytes advanced from **16,908,288** to **18,022,400**. A zero command exit code is not counted as a successful stop.

The subsequent command selected **`--user current`**. The package process was absent at **09:17:07.683994 UTC**, then present at **09:17:08.015186 UTC**—a **0.331192-second interval between samples**. The latest package exit diagnostic reported reason **10 / USER REQUESTED**, shell subreason **23 / STOP APP**. The subreason is an observed shell diagnostic, not a public application API or a force-stop equivalence claim.

The new process identity was `47669298-1266-403b-85ae-d96397fc6127`, replacing `751bf336-43db-4968-a617-eae6a0ccdfe7`. By the **09:17:09.144965** export, startup reconciliation had already changed the same attempt to **`interrupted` / `new-process-unfinished-attempt`**, revision **2**. This happened **before the later explicit UI relaunch**. The automatic restart trigger is not established. Process return is distinct from restarting or resuming a transfer.

The post-stop, explicitly returned and later stable exports all contain **41,287,680 bytes**, SHA-256 `db589f5daab38ac26936b1938d4e88effcf2c89614c999cbd7f06692e6a33db9`, with the same interrupted journal. The first post-stop and final stable host observations span **71.623953 seconds**. The server log contains exactly two HTTP requests for this server instance—attempt2 and attempt3—with no later request before shutdown. Attempt3 produced a connection-interruption event. This is bounded no-replay evidence despite process restart; it does not guarantee the process stays stopped.

No final job-stop callback was persisted: `stopReason` and `stopOutcome` remain null. These fields do not prove that a callback never occurred. This 0.4.1 run also cannot validate the subsequently authored 0.4.2 lifecycle audit instrumentation.

## UI return, fixture shutdown and evidence boundary

The explicit post-stop launch returned `Status: ok` / `LaunchState: WARM`. An extra helper then failed to find an **active** probe window because the quick-settings system overlay held focus; the app's Open transfer control was present behind it. That failed helper is not counted as a tap or an app failure. After overlay dismissal, a fresh layout showed the app active, the matched Open transfer action succeeded, and the final own-app view displayed **interrupted** in task **1000156**.

Server05 stopped with exit code zero. A separate **09:19:32.074593 UTC** check confirmed the dedicated listener absent. The public report includes no raw UI, screenshots, shell dumps, private network addresses, hostnames, OS process/user identifiers, certificates or keys.

The [sanitized manifest](2026-10-15-transfer-followup-manifest.json) binds **64 selected private artifacts**, eleven allowlisted journal projections, eleven host observations, seven independently hashed payload exports and five actual-control tap matches. All eight archive members were independently verified without extraction. The author, primary agent and independent reviewer accepted the report after checking the private artifacts, archive members, projections, action bounds and privacy boundaries; no actionable finding remains.

Wrong-hostname rejection and this native task-removal workflow now have bounded evidence. Actual Task Manager UI behavior, the reason for automatic process return, LAN revocation/denied-socket enforcement, assigned-network changes, deadlines/budgets, natural low-memory/reboot/power loss and physical input remain unqualified. No continuous SSH, SFTP/publication, USB, streaming, RDP/VNC or product capability is promoted by this report.
