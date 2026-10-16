# Console-to-Desktop ordinary files handoff: one finite debug pass

On 2026-10-16, actual attempt08 passed on the HP Googlebook 14 running Android 17/API 37, build `CL3B.260622.276.R2` / incremental 16560795 / arm64-v8a, with independently installed Console and Desktop 0.1.15/code 16. The parent executed the reviewed finite packet once. The supervisor terminal is `FINAL_TEARDOWN_PRESERVED`, passed true, with host exit 0. This qualifies this controlled Console-to-Desktop fixture operation; the reverse direction and broader P1 acceptance remain open.

The [matching manifest](2026-10-16-handoff-positive-console-desktop.json) binds the exact source/build/artifacts, actual upgrade authority, frozen helpers and private archived evidence. The build receipt is `c25b1ccd83745e85172da620017ca5d58131b1c6106d7b18e8d463641669c88b`; current 232 compiled inputs and seven frozen APKs were verified against it. The operation used the already installed artifacts from the actual [preserved 0.1.15 upgrade](2026-10-16-handoff-observer-terminal-clear-build.md), with receipt `7686c1b53228c4585caef2f31c7b0c32aea93cc2bd1507fe91a4555eb5711015`. No additional build, installation or qualification retry was performed within this attempt.

## Actual operation and evidence

Four real instrumentation invocations each completed exactly one method with `OK (1 test)`, one terminal instrument code -1, no skip/failure and empty instrumentation stderr:

| Invocation | Actual evidence |
|---|---|
| Receiver PREPARE | One `PREPARED_RETIRED` status; real fixture authentication/listing and exact retained registration/checkpoint preparation |
| Ordinary sender controller | One `consoleToDesktop` method; exactly eight ordered phase statuses |
| Receiver VERIFY | One `VERIFIED_RETAINED` status against the original immutable checkpoint |
| Receiver CLEANUP | Seven ordered statuses terminating `CLEANED_EXACT`, removing only the three original owned rows |

The sender controller used the normal Console UI: Refresh, select the owned registered fixture and activate Open. It did not launch a receiver intent directly. The private Desktop receiver presented fresh fixture authentication, connected, displayed the registered nondefault nested directory/file preview, disconnected the same session and followed the ordinary planned close sequence. The checked public accessibility-cache freshness call necessarily succeeded before the existing root query; its invocation count was not separately measured. The exact predicates and single Open remained unchanged.

The sender observer sealed a valid complete five-event original-call chain. The receiver observer sealed a valid complete nine-event original-operation chain:

`DISPATCH_STARTED → DISPATCH_RETIRED → RECEIVER_CREATED → SESSION_CLAIMED → TRANSPORT_ESTABLISHED → TRANSPORT_RETIRED → SAME_SESSION_IDLE → MODEL_CLEARED → RECEIVER_DESTROYED`.

The receiver clear used the exact original model and second 0, after certain original-session retirement and same-session IDLE, after the controller's planned receiver-close phase and before matching original receiver destruction. The corrected observer and the strict parent runner both accepted this sequence. Clear alone did not supply retirement evidence. This runtime result supports the narrowly corrected terminal-clear predicate; the historical 0.1.14 [attempt07 observer failure](2026-10-16-handoff-postopen-observer-failure.md) remains failed and separately reconciled.

The actual bound Apache MINA SSHD qualification fixture recorded 52 primary events and 10 bounded path events. Two authenticated sessions closed. The measured second session completed a nested-directory listing and nested-file read; the preparation session is counted separately. Both journals reported no overflow or I/O failure. The parent retained the original host process/stdin handle, requested its owned shutdown, observed exit 0, `serverClosed=true`, `workersRetired=true`, the listener closed and the synthetic tree unchanged. This is fixture evidence, not a Cloudy host-product qualification.

## Preservation and finite teardown

Read-only inspection verified all 1095 archive hashes from the terminal supervisor and re-parsed the actual method outputs, observer receipts and replay delta without rerunning device or host operations. The supervisor receipt SHA is `c5ee191cfd6400bba74ae3604ba2026ece649ab78a91773a0ece5cb1898a9539`. The device runner intentionally ends with `devicePortionPassed=true`, `passed=false` and `DEVICE_PORTION_PASSED_PARENT_FINAL_TEARDOWN_REQUIRED`; the supervisor supplies the completed overall teardown result.

The original profile/trust/resource store rows and metadata were restored, typed preferences including unknown values were preserved, and the device/boot/system settings/full package UIDs remained equal to the accepted upgrade baseline. Console had zero replay rows before the operation and remained unchanged. Desktop had one prior legitimate replay row and one final row: production admission validated exactly one new request and pruned only expired prior rows. The final replay state exactly equals the measured post-Open state; no replay reset or manual row deletion occurred.

Only the two exact product `files/profileInstalled` metadata paths could differ under the reviewed ProfileInstaller 1.4.0 guard. Console ultimately published a strict current-PM-correlated 24-byte marker; Desktop retained its exact original marker. Original and observed raw bytes were archived. Schema 1/result 327680/current-profile-size 0, fixed package APK/version/fullUID/PMsecond/timezone and all other protected-file checks passed. PM text has seconds precision; this correlation does not establish millisecond identity, durability or authorization. No marker was forced, written, deleted or restored by qualification code.

After actual receiver and sender retirement was accepted, the finite packet removed exactly four owned configuration files and its one exact reverse route, verified the original checkpoint phases, preserved immutable checkpoint/phase/observer evidence, and uninstalled exactly the two owning test packages and standalone controller test package. Both product processes were stopped at final. No generic cleanup or retry ran.

## Scope limits

This is one Console-to-Desktop debug fixture pass using SSHJ 0.41.2 and the exact bound host fixture. Desktop-to-Console, arbitrary server/account combinations, release runtime, sustained operation and general BAL compatibility remain unqualified. Accessibility assertions and source-bound ordinary actions do not constitute human visual, physical pointer/keyboard or broad usability qualification. The observer ledger contains instance tokens and lifecycle events, not tickets; this result does not claim wrong-ticket tests. No phase, capability or full P1 completion is promoted by this report.
