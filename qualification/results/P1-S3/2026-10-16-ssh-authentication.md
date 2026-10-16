# Finite SSH password authentication and exact local admission

**Date:** 2026-10-16. **Outcome:** strict build02 passes, four Android Lint reports contain zero findings, and 52 actual Android coordinator/store cases pass. Final host03 executes all ten real-server cases successfully with exact wire and retirement assertions. The original host01 preauthentication rekey expectation fails and remains preserved; the final test measures the pinned engine’s actual limitation. This is an engine/coordinator construction increment toward P1-S3 and P2, not installed-product password authentication, full SSH adoption or stage completion.

The [sanitized machine-readable manifest](2026-10-16-ssh-authentication.json) binds 146 frozen source/build inputs, five APKs, test reports and evidence hashes. Build evidence is retained in `.cache/ssh-authentication-01/build-02/`; actual raw device and host packets are retained under `qualification/results/private/foundation/2026-10-16-ssh-authentication-device-01/` and `2026-10-16-ssh-authentication-host-01/`, `02/` and `03/`. Private inventory, serials, credential configuration and transcripts are excluded here.

## Implemented boundary

`SshAuthenticationCheck` makes a finite fresh SSH connection to a caller-selected numeric address for an exact logical peer and accepted public-key identity. The retained narrow negotiation policy is ECDH P-256, ECDSA P-256, AES-128-GCM, HMAC-SHA256 and no compression. The host verifier accepts only the first exact verified key; subsequent verifier callbacks are rejected. The maintained SSHJ transport performs signature verification before the adapter's host-verifier decision. Credential admission follows successful initial connection/KEX, not merely receipt of an unverified key candidate.

The engine invokes only one explicit password authentication method. It does not use SSHJ's `authPassword` convenience fallback to keyboard-interactive. The strict method refuses `SSH_MSG_USERAUTH_PASSWD_CHANGEREQ` before parsing the prompt, requesting another password or constructing a change packet; retries are disabled. This matters because SSHJ's default update provider returns null, and its sensitive-string encoder would serialize that as an empty new password. Username and password inputs are copied without trimming or normalization; limits are 256 and 4096 UTF-16 units respectively. Owned mutable arrays are erased on disposal. Immutable username strings and upstream packet copies prevent a complete JVM zeroization guarantee.

The worker owns a cancellable socket, whole-attempt deadline, shutdown helper and actual SSHJ Reader retirement. The retained 0.41.2-specific nonemitting Reader logging hook captures thread ownership and terminal notification; success requires joining the Reader and helpers and confirming socket closure. Missing or uncertain retirement quarantines that engine instance. A generic `AUTHENTICATION_FAILED` result does not assert wrong credentials: it can include transport loss after key verification. There is no channel, command, terminal, PTY, SFTP or reusable session returned.

`SshAuthenticationCoordinator` registers at most 16 finite attempts and takes ownership of per-session credentials on every preparation outcome. It binds the exact current profile, profile-store generation, accepted trust snapshot/generation/revision/key, owning app and a 45-second elapsed-time lifetime. Admission rereads those identities under a process-wide coordination lock, then transfers credentials once through the same cancellation signal's short atomic gate. Callers must pass that handle's cancellation instance to the engine. Cancel winning the gate prevents transfer; cancellation after transfer can stop transport but cannot retract admitted bytes.

Lock order is coordination, existing store monitor, permanent file lock and SQLite; the transfer boundary uses coordination, cancellation and short credential ownership. Profile update/delete and trust accept/revoke invalidate affected attempts after CAS validation and immediately before a possible mutation. Unrelated profiles/peers remain independent. Handle close cancels and disposes without waiting on SQLite, including accidental main-thread admission. Expiry tasks are bounded by the 16 handles and removed on close; admission rechecks elapsed time even if suspend delays the timer. Production stores/coordinator require the owning UID and default app process name; explicit internal root/owner configurations are test-only. These process-local guarantees do not support an uncoordinated second production process.

## Frozen build02

Build02 runs **12:38:08.499727Z–12:38:45.441392Z**, from base `b8bbab9541993c40f1752146dc22f95bf88716ea` plus the frozen uncommitted increment. Strict dependency verification succeeds with 322 actionable tasks: 40 executed and 282 up-to-date. All 146 recorded source inputs remain unchanged during the build; the report author independently compared all five frozen APK copies against that receipt. Final source comparison finds only the host-test file changed; all production, application and Android instrumentation inputs still match build02. Host03 recompiles and executes that changed JVM test. Build02/Lint/device evidence is carried forward for unchanged inputs, rather than reported as a fresh full build or repeated Android run.

| JVM lane | Reported cases | Actual build02 execution |
| --- | ---: | --- |
| Domain |50 pass | Task up-to-date; carried reports, not rerun |
| Design system |4 pass | Task up-to-date; carried report, not rerun |
| SSH |32 total:21 pass,11 assumption skips | Executed; ten password-host cases and one opt-in OpenSSH observation case skip without their fixture |
| Platform JVM |2 pass | Executed |

Console, Desktop, platform and design-system Lint each report zero findings. Both product APKs and three instrumentation APKs assemble. The manifest includes exact source and report hashes; artifact identities are:

| Built artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Console debug |41370750 | `bea136887e3da9ce96b284cfd371deff9ce9a2bc08883a31a47bb9ab8b255b87` |
| Desktop debug |41370754 | `1febcd5b5bae8fe67ec64e45da135862d69be41fdeed909a03aacb38f6f3419b` |
| Platform instrumentation |42191908 | `5580c4275fbc4f689db179299090b4eb378d0a2da6dfa1b1dc1967da1c45975d` |
| Console instrumentation |3322241 | `932a196d03050124d64c36ea8ce9e626c699ed87e7d22fed2d09971143183d56` |
| Desktop instrumentation |3322241 | `9096fcd3c73ca739ddd2328af1f7725fdfe4370fe7e764db0785d8dcec6ff1f3` |

These newly built product bytes were **not installed**. The installed independent products remain the previously qualified 0.1.8/code 9 observation checkpoint.

## Actual Android coordinator/store run

Device01 runs **12:41:05.798335Z–12:41:08.760050Z** on **HP Googlebook 14, Android 17/API 37, arm64-v8a, build CL3B.260622.276.R2, incremental 16560795**. Only the exact platform instrumentation APK above is installed for this packet. The raw runner completes `OK (52 tests)` with no assumption skips:

| Actual class | Passes | Boundary |
| --- | ---: | --- |
| `SshAuthenticationCoordinatorTest` |13 | Edit/revoke before admission, post-admission invalidation, unrelated records, stale profile/trust generations, same-key re-enrollment, exact deadline, capacity, closed credentials, scheduling rejection, concurrent cancel/gate, wrong-process denial before storage and actual main-thread denial under contention |
| `ConnectionProfilesTest` |19 | Affected real SQLite/locking/profile persistence regressions |
| `SshHostTrustStoreTest` |20 | Affected real SQLite/trust CAS/tombstone regressions |

These are real Android SQLite tests in explicitly isolated test roots. The generation-change case deliberately replaces internally consistent artifacts only in its isolated fixture; it does not introduce a product reset operation. No password network authentication, native authentication UI or screen-lock prompt runs on Android in this packet.

The instrumentation package is removed. Both installed products are stopped before and after; their recorded profile/trust databases, vault qualification receipt, display-preference file hashes and boot identity/count match the baseline. The runner performs no product install/update or system-setting change. It checks product metadata, processes and data rather than freshly hashing installed APK bytes; that stronger byte-equality measurement is not claimed.

## Preserved host01 failure and server observations

The isolated Apache MINA SSHD 2.20.0 fixture (built-in NIO2 backend, Java 21 runtime with Java 17-targeted fixture classes) runs **12:39:54.113419Z–12:40:25.013483Z**. Ten opt-in `SshAuthenticationHostTest` cases actually execute, with nine passes and one failure. The passing cases include two successful authentications using the same engine instance, wrong password/server denial, actual password-change challenge refusal, transport loss, fresh wrong-key rejection before admission, denied admission, and controlled cancellation/deadline waits.

`serverRekeyDuringAuthenticationIsRefusedWithoutAnotherPassword` expects `KEY_REJECTED` but receives `TIMED_OUT`. Pinned SSHJ `KeyExchanger.isKeyExchangeAllowed()` permits the initial exchange or an exchange after authentication; it does not permit another completed KEX during preauthentication. The fixture's server-side rekey-start event therefore does not establish a second host-verifier callback. This failed test expectation is retained, not relabeled a successful live rekey qualification.

The host01 server summary records 11 connections and 11 closures, 10 established key exchanges, nine password requests with the change flag false, two accepted/authenticated connections, two denials, two waiting requests, one password-change challenge and one rekey-start event. Its wire assertions pass, with no change-password or alternate-method request recorded. This is a genuine maintained-server exchange, not OpenSSH/PAM or a system-account qualification. The server reports no event overflow or I/O failure, closes its listener, and requires no system-account or system-SSH-policy changes. These are workstation fixture results, separate from Android and installed-product evidence.

## Final host03 results and exact binding

Host02 runs **12:48:34.163802Z–12:49:02.159763Z**, with ten passes and actual server KEXINIT-write evidence. A redundant negative assertion used `action_duplicate`, whereas the fixture emits `duplicate_action_request`; the assertion is corrected before the final run. This intermediate pass remains separate from final host03.

Host03 runs **12:49:24.136133Z–12:49:52.082082Z**. Strict `:ssh:test --tests …SshAuthenticationHostTest --rerun-tasks` recompiles Kotlin production/test sources and passes **ten tests, zero skips, failures or errors**. The corrected test checks the emitted `duplicate_action_request` name. The runner reports successful wire assertions, a closed listener and no cleanup errors. All 18 recorded source hashes and nine compiled fixture-class hashes independently match their retained/current bytes. Fixture-build03 records successful `javac --release 17 -Xlint:all -Werror`; its source SHA-256 is `2610e7b06aa669ad622aa6cefd54cf449f1fc9a902e2035f26bbba09dc058139`. The manifest binds every fixture class, final host source and raw result/report/log hash without publishing generated credentials.

The final server records 77 events, 11 actual connections and 11 closures, 10 established initial key exchanges, nine password requests with the change flag false, two authenticated connections and zero opened channels. Per-case assertions establish one connection and the exact request count, rather than inferring no retry/fallback from a final status. The genuine change-password challenge produces no password-change request or change-handler invocation. Cancellation/deadline cases first wait for the server to observe a real password request.

For the preauthentication rekey limitation case, the fixture proves exactly one server rekey KEXINIT was actually written; the client starts no second negotiation and sends no second password request. The finite attempt returns `TIMED_OUT` and retires. No duplicate action, canceled/failed fixture action, authenticated outcome or second key establishment is accepted for that case. This is bounded evidence of the pinned preauthentication behavior, not live rekey support, a second verified host key, or full session revocation qualification.

The exact client pin is **SSHJ 0.41.2, commit `6fad160fe36605a68847ac8a527750c615b4bd04`**, retaining asn-one 0.6.0, BC bcprov/bcpkix/bcutil 1.86 and SLF4J API/NOP 2.0.20. The host fixture pin is **Apache MINA SSHD 2.20.0, commit `2221febd396cd3daafdfbf2ff982c6d0eb69a203`**, with built-in NIO2 and pinned SLF4J API/NOP 2.0.20. Its dependencies remain qualification-only and are not added to product runtime; exact JAR hashes appear in the manifest. This fixture is not OpenSSH/PAM or system-account qualification.

Two independent source reviews and the integration leader's review examine credential admission, cancellation/retirement, exact-key/rekey boundaries, strict password-change refusal and evidence binding. The outcome-classification repair uses `AUTHENTICATION_FAILED` for unconfirmed authentication, and the final real host suite covers controlled transport loss. These reviews and finite tests do not replace the Android-network, lifecycle, vault and complete-session gates below.

## Remaining integration and acceptance gates

Native per-session authentication entry and its foreground/lifecycle behavior, saved-vault credential release and real human device-authentication proof, actual Android-network password authentication, successful hostname/local-network permission routes, rekey/revocation behavior for reusable sessions, jumps, terminals/PTYs, commands, SFTP/drop and separate X11 windows remain required. This finite check opens no remote operation and promotes neither CAP-SSH nor full P1-S3/P2 completion. Process death, power loss, complete credential-memory erasure, alternate algorithms, physical input and a full session's sustained ownership remain separately unqualified. The full P0–P6 implementation direction remains active.

Final repository gates pass:32 Python AST/syntax files, full Ruff,123 repository regressions, JavaScript syntax, nine terminal-bridge cases and project/schema validation (132JSON,312Markdown,2,416local links,524sources,60API records). These supplement the strict Kotlin/Java compiler, four Android Lint reports and actual host/device evidence above; they do not independently qualify Android authentication.
