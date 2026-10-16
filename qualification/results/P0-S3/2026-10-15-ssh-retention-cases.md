# P0-S3 — Finite retained SSH device cases

**2026-10-15, America/Chicago:** the already-installed baseline diagnostic **0.8.0/code12** completes the three scoped retained exec/PTY cases on the selected HP Googlebook: hide/return without a second connection, actual notification cancellation, and abrupt owned-process loss followed by explicit interrupted reconciliation without replay. These are finite device results under the [retention contract](../../../docs/03-platform/googlebook/p0-s3-ssh-retention-contract.md), not qualification of indefinite sessions, a shipping foreground-service classification or Google Play acceptance. CAP-SSH/CAP-TERMINAL and product gates are not promoted.

The [manifest](2026-10-15-ssh-retention-cases.json) binds the actual private device05/host06 receipts, current fixture/controller sources and sanitized observations. The [earlier installation, host-PTY failure and reboot incident](2026-10-15-ssh-retention-device.md) remains unchanged historical evidence. This run does not establish that incident's cause.

## Exact candidate, preservation and host boundary

No APK rebuild or reinstall occurred. The existing installed/pulled APK remains SHA256 `fc2cc377cb09aaf9ad571489a09854719e84de3a9a3a03b131fabab01fa46e09`, 16,005,509 bytes, build reference `7aab1ccd-e90b-41e1-bd51-1183042c134a`. Its [accepted build](../../../docs/06-validation/2026-10-15-ssh-retention-build.md), 206 JVM tests, zero Lint and source checkpoint `d9518d18390e2a8cca7752a0546f89d8b4e7a9a2` carry forward for unchanged inputs. The later host/controller preparation records repository checkpoint `f58d18a78665a550d58bd8927bd12ceec28a527e`; this does not relabel the installed APK's source.

The selected device lane remains HP Googlebook 14, Android 17/API 37, arm64-v8a, build `CL3B.260622.276.R2`, as identified in the preceding device report. Fresh package/APK/journal and boot-continuity receipts bind this run. Initial preservation confirms five terminal SSH records, unchanged config/trust and three available slots. The three cases fill those slots; no journal clear or capacity expansion occurs.

A new normal-workstation-context host06 fixture uses the established extracted OpenSSH 10.2p1 Ubuntu-2ubuntu3.8 server/helpers and fixed `tools/ssh_channel_fixture.py` protocol. Preparation preserves the endpoint/config identity, host identity and client authorization, changing only PID-file and audit-directory destinations. Public-key-only authentication, disabled forwarding, fixed ForceCommand and PTY policy remain. This is a separate finite fixture, with no system SSH-service or power-setting change.

Before any device connection, a workstation-local SSH client exercises the actual configured endpoint: fixed exec exits 17 with 19 stdout and 19 stderr bytes; real 80×24 PTY allocation, 132×43 resize, SIZE/FINISH exchange and zero PTY exit pass. The 62-byte PTY transcript SHA256 is `2a1d94e913eac24e63ee3455706198abc39eb51033b4e5a7eb659a94771d21f1`. This demonstrates that the selected normal-context route handles the PTY operation that previously failed in the sandbox; it does not prove a universal explanation of sandbox restrictions. The resulting **two authentications/two session starts** are the pre-device baseline and are not counted as device reconnections.

## Case 1 — Hide, return and finish

Attempt `7992de77-8609-4989-9e84-e51edcbf946e` reaches actual controlled PTY readiness and acquires service ownership. Home hides the UI for 6 seconds. The same app process, process-instance/attempt identity and live host dispatcher persist. Returning to the observer does not press Start again. Host cumulative totals stay at **three authentications, three connections and four session starts** through readiness, hiding and return: one device connection has an exec channel and a PTY channel in addition to the preflight baseline.

Two controller-observation failures are retained. The first readiness read sees the preceding terminal row before asynchronous admission persists the new row. The parent inspects and continues the already-running new attempt instead of repeating Start. After Home, the public launcher resumes the existing SSH Activity, so an expected Open SSH button is absent. The current Resize/Finish controls and same attempt are verified; the parent continues that observer. These are automation assumptions that failed, not fabricated app failures or additional network attempts.

The existing Resize and Finish controls complete the real 132×43 SIZE exchange and FINISH marker. The final journal status is `completed`, with the 62-byte PTY transcript matching the host preflight protocol. The dispatcher disappears and the retained service retires. `19-result.json` records the finite pass with both observer failures and no repeated network start.

## Case 2 — Actual notification Disconnect

Attempt `82e70a77-8b27-4d01-b408-11ed1d46f0c3` reaches actual PTY readiness, then hides. The parent identifies the owned notification row titled “SSH qualification session retained,” expands that exact row, and invokes its actual **Disconnect** action. This is not a substitute Activity Cancel action or a forged direct service intent.

The durable journal becomes `cancelled`, the host dispatcher disappears and the service is absent. Explicit reopening attaches an observer without another attempt or authentication. Host cumulative totals remain **four authentications, four connections and six session starts** through hidden, retired and reopened observations. `23-result.json` records actual notification cancellation and no new attempt on reopen. The notification/service observations alone are not treated as proof of remote cleanup; the host helper and durable terminal evidence provide that boundary.

## Case 3 — Abrupt owned-process loss and explicit recovery

Attempt `4bc21d94-5392-44de-b48b-12f5eb8dad7a` reaches real PTY readiness and remains running after Home. The parent preserves the unfinished journal and verifies the exact selected package PID against the UID returned by `run-as`, including process identity. It then sends **SIGKILL through that app's run-as UID to that one verified PID**. This operation is process loss, not force-stop or reboot.

During the ensuing 6-second observation, the app process does not restart. Its persisted unfinished journal remains exactly unchanged before explicit reopening. The host dispatcher disappears, and host cumulative totals remain **five authentications, five connections and eight session starts**. No in-process cleanup callback, clean join or orderly app shutdown is claimed for the killed process.

An explicit public-launcher reopen creates a new process instance and reconciles the same existing attempt to `interrupted`, reason `new-process-unfinished-attempt`. It creates no ninth attempt, authentication, automatic service restart or network replay. `33-result.json`, before/after journal bytes and host observations establish this finite recovery result. It does not qualify behavior under every Android kill mechanism, prolonged background restriction or reboot.

## Cleanup and remaining boundary

The final SSH journal contains **eight terminal records**: all five predecessor rows and saved trust are unchanged, with the three new outcomes completed/cancelled/interrupted. Config bytes remain unchanged. After all records are terminal and the retained service is already absent, the parent force-stops only the baseline diagnostic and identified owned CLI instrumentation as cleanup. That later force-stop is separate from case 3's earlier SIGKILL. The final eight-record journal is byte-identical across cleanup, and boot identity/count remain unchanged throughout the run.

The installed APK is pulled again and matches the same 0.8.0 artifact. The finite host supervisor exits 0; the listener is closed and no scoped dispatcher helpers remain. A final normal-context process scan also confirms that the extracted sshd/session/auth processes and owned dispatcher are absent; the listener is closed. `41-cleanup.json` records the device preservation/retirement result; `host-06/normal-stopped.json` records host retirement. No journal slots remain: preserve/archive through the reviewed batch restoration contract before another SSH attempt, rather than clearing history or enlarging capacity.

These cases support the finite provisional `specialUse` experiment with the actual selected Android app/backend. They do not establish indefinite/background-session guarantees, Play distribution approval, physical terminal keyboard/IME/accessibility, all host/device combinations or product readiness. Existing 120-second PTY read and 180-second operation bounds remain. The earlier unexpected reboot remains historical and causally unresolved; this same-boot run neither reproduces nor explains it. No unchanged library/build/device work was replayed merely to prepare this report; documentation and evidence bindings are checked separately.
