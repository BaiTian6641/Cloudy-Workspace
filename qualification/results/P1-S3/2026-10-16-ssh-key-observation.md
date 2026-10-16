# Credential-free SSH key observation and explicit trust enrollment

**Date:** 2026-10-16. **Outcome:** a bounded real observation route is implemented in Console/Desktop **0.1.8 / code 9**. Fourteen actual Android cases pass, six default opt-in cases skip, and the separately executed host SSH suite passes thirteen JVM cases. This establishes signed-key observation, explicit first-use enrollment and a deliberate fresh key check in the tested route. It does not authenticate, complete an SSH session, adopt the full engine or close P1-S3/CAP-SSH.

The [sanitized summary](2026-10-16-ssh-key-observation.json) records counts, artifact identities and preservation. The [build manifest](2026-10-16-ssh-key-observation-build.json) records the frozen compilation and artifact evidence. Raw private packets remain in `qualification/results/private/foundation/2026-10-16-ssh-observation-02/`, `03/` and `04/`. Endpoints, ports, serials, UIDs, profile identities/digests and transcripts are excluded from this public record.

## Implemented boundary

The nonexported native Activity selects an actual saved owning-app SSH profile. An explicit Check uses the cancellable resolver and finite SSHJ observer. Logical peer identity remains distinct from its numeric transport address. Unknown, changed and revoked observations can produce an exact owner/profile/attempt-bound challenge; matched trust reports that authentication was not attempted. The observer intentionally rejects the host-key verifier and returns observed metadata only after transport retirement. No credential, authentication, terminal, command or SFTP API is exposed by this route.

A foreground controller uses monotonic bounded attempt/presentation lifetimes, selection/revision binding, cancellation and one-shot approval. Pause, cancellation, supersession and destruction retire approval. Real store CAS uses the original generation/revision/absence token. Approval is checked at worker execution; an already admitted write can still finish after lifecycle loss. Saving never resumes the rejected connection: a fresh explicit Check is required. Pending/uncertain saved metadata contains no selected peer, observed key, attempt or approval. Full verification-to-authentication ordering remains later work because this route releases no credentials.

## Build, dependencies and packaging

Strict build 03 runs **11:52:09.684213Z–11:52:36.011647Z**, reports success in 26 seconds with 322 tasks/55 executed/267 up-to-date, and retains unchanged source hashes during the build. Its 136 source inputs and five APK identities are bound by the build receipt and public summary. Four Lint reports contain zero findings. JVM reports carried into build 03 are not new executions; the separately identified host run below supplies fresh SSH-suite evidence.

The retained runtime closure is SSHJ 0.41.2, asn-one 0.6.0, BC bcprov/bcpkix/bcutil 1.86 and SLF4J API/NOP 2.0.20. This reuses the reviewed [dependency decision](../../../docs/06-validation/2026-10-15-ssh-dependency-adoption.md), rather than selecting new crypto or replacing Android providers. Strict locks/verification remain enabled. The artifact-scoped BC EST Lint exception remains limited to the reviewed 1.86 JAR; this observation adapter introduces no EST/BCJSSE use.

The packaging check confirms all nine SSH notice/provenance assets match source bytes in each product APK and only the intended NOP logging provider is declared. The earlier copied provenance manifest bound the old README length/hash despite an edited README; the current manifest corrects that record and states its credential-free product scope. Legal texts retain the reviewed upstream bytes. Packaging establishes inclusion, not complete Desktop distribution/license acceptance.

## Actual host and Android execution

The isolated workstation OpenSSH fixture uses the selected upstream10.2 binary without changing the system SSH service. Host run 02 executes **11:48:10.934646Z–11:48:30.838558Z** with `:ssh:test --rerun-tasks` and strict dependency verification. Its 13 JVM cases comprise codec, observer ownership/policy and five host-test cases, including controlled cancellation/deadline behavior. The OpenSSH fixture records two connections and zero user-authentication requests, accepted authentications or sessions; its listener closes. Other deliberately withheld greeting/key-exchange sockets are finite test fixtures, not successful SSH sessions.

Actual Android target: **HP Googlebook 14, Android 17 / API 37, arm64-v8a, build CL3B.260622.276.R2, incremental 16560795**. The route is numeric app loopback through an owned USB ADB reverse to isolated workstation OpenSSH. This is not a LAN permission test or a hostname-DNS success test.

| Packet / suite | Actual JUnit passes | Assumption skips | Scope |
| --- | ---: | ---: | --- |
| Device 03 standalone platform | 8 | 0 | Six resolver cases and two real transport cases, including independently referenced signed-key observation/reuse and already-cancelled denial |
| Device 03 Console Activity | 3 | 3 | Real observation/Cancel/recreation; explicit first-use Save then fresh Match; stale first-use CAS refusal |
| Device 04 Desktop Activity | 3 | 3 | The same three owning-product cases and default opt-in denial |
| **Total Android** | **14** | **6** | Custom instrumentation statuses and cleanup receipts are not additional JUnit cases |

The six product cases use independently supplied public-key metadata and real app-owned profile/trust stores. Save plans the only permissible fixture row before mutation; fresh Match requires another explicit Check and does not rewrite trust. The stale case commits a real competing enrollment after presentation, then verifies the original absence CAS returns conflict without overwrite. Cancel/recreation restores neither selection nor approval. Default tests assume-skip before product Activity launch or fixture creation.

Device 03 runs **11:53:35.467432Z–11:53:46.194852Z**; its original host runner returns false after all eleven JUnit cases and three skips because receipt collection assumed the wrong Android user storage root. The original result is preserved. A separate collection-repair record recovers all three Console receipts with `PASSED` and `EXACT_BASELINE_RESTORED`; it does not rerun or relabel tests. Device 04 runs **11:55:06.953242Z–11:55:15.949161Z** and reports a terminal pass for Desktop alone.

Together the device fixtures record **ten connections**: six in device 03 and four in device 04. Server receive counters contain zero client `SSH_MSG_NEWKEYS` packets (type 21) and zero user-authentication requests (type 50), with zero accepted authentications or sessions. The server can send its own NEWKEYS before the client rejects; no completed client handshake or authenticated connection is inferred from key observation. Host and Android connection totals remain separate.

## Cleanup and remaining gates

Six product fixture-cleanup receipts confirm exact baseline restoration. Cleanup closes the Activity, settles its worker/main callbacks, verifies the permanent trust lock/metadata and complete baseline-plus-independently-planned fixture, then removes only that exact test row in a transaction. Profile deletion uses its recorded revision-bound API token. This guarded test disposal does not add a product trust-delete/reset API; unknown state preserves evidence.

Both 0.1.8 / code 9 product APKs are installed and stopped with build 03 bytes. All three possible instrumentation packages are absent. Profile/trust rows and generations, vault receipts, effective display preferences, boot identity/count and recorded system display settings remain preserved. Owned USB reverse mappings return to baseline and fixture listeners close. No app-data clear, authentication prompt or system SSH service change occurs. Device 03's collection failure remains historical evidence rather than a product failure.

LAN permission behavior, successful hostname DNS, alternate negotiated algorithms, live changed/revoked UI cases, queued-approval lifecycle races, full authentication ordering, live rekey/revocation, jumps, real remote operations, process death, power loss, physical input and native visual/accessibility acceptance retain separate gates. No full SSH engine adoption, capability promotion, P1 completion or supported remote-session claim follows from this result.
