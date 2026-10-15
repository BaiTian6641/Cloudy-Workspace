# P0-S2 — Real UIDT transfer experiment

**Checked:** 2026-10-15. **Owner:** Android/platform developer. **Status:** both reviewed versions passed compilation, Android Lint and 61 JVM tests. The 0.4.0 first attempt remained queued and was cancelled before worker claim or payload; separate 0.4.1 device observations include verified payload, window continuity, cancellation, failures, explicit retry and recovery after an injected process kill. These bounded observations do not complete overall API qualification or capability gates. This is a diagnostic extension, not Console SFTP, a remote editor, a production transfer engine or a completed platform gate. Preserve [operation lifetimes](p0-s2-operation-lifetimes.md), [Rule 0](../../00-project/platform-rule-zero.md) and [publication recovery](../../02-architecture/file-publication.md).

## Actual operation and scope

Download one generated finite file over HTTPS from the selected Ubuntu host using a real Android user-initiated data transfer job. The user opens a separate native diagnostic screen and explicitly starts the operation while visible. Window recreation must reattach to process-owned state; it must not start another download. A 64 MiB fixture and optional server throttle make lifecycle transitions observable. Neither the file generation nor the throttle is a performance benchmark or a simulation of network success.

The first endpoint is the selected LAN fixture. Require an OS-selected network that advertises Wi-Fi or Ethernet, without INTERNET, VALIDATED or NOT_METERED requirements. The reviewed 0.4.1 request removes only the default `NET_CAPABILITY_NOT_VPN` requirement, preserving the other defaults and the Wi-Fi/Ethernet transport alternatives. This allows the user's existing VPN network when it advertises a matching transport; it neither selects the underlying Wi-Fi network nor disables or bypasses the VPN. Use only the `Network` assigned in `JobParameters`, with no default-network fallback or global process-network binding. A changed/lost assigned network interrupts the attempt. Transport labels do not prove the physical packet route: a VPN may advertise Wi-Fi and cellular together. This scope establishes neither Wi-Fi-only traffic nor general VPN/provider reachability. [G21 network guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state), [G86 request matching](https://developer.android.com/reference/android/net/NetworkRequest.Builder)

The actual 0.4.0 first attempt used Wi-Fi/Ethernet plus default NOT_VPN. Its own scheduler entry reported CONNECTIVITY unsatisfied while the observed application network advertised Wi-Fi and VPN without NOT_VPN. No worker was claimed and no host HTTP request or payload occurred; explicit cancellation was saved at revision 3 with zero bytes. This is a recorded scheduling limitation, not a TLS failure. The private raw scheduler/connectivity captures remain outside publication; the integrating run report owns their hashes and full provenance. Upstream `ConnectivityController` monitors each UID's default network and compares its capabilities with the request, which explains this mismatch; its pinned 2025 source is diagnostic context, not proof of the HP build's exact implementation. [G95 pinned controller source](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/job/controllers/ConnectivityController.java)

The host helper binds an explicitly selected IPv4 address and high port, serves only `/fixture.bin`, and never exposes a directory. Preparation creates an ephemeral private CA and an IP-SAN server certificate plus a known content length/hash. The Android app imports the public CA and endpoint through its debuggable private files; private keys stay on the host. Keep all endpoint-specific files, raw reports and private inventories ignored.

## Configuration and trust

The private `files/transfer-config.json` schema is version 1:

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Integer 1; unknown schemas fail closed |
| `fixtureId` | `cloudy-uidt-sha256-blocks-v1`, a generated fixture format identity |
| `url` | HTTPS URL with an explicit IPv4 address, port 1024–65535, exact `/fixture.bin` path, no query, fragment or user information |
| `expectedLengthBytes` | Finite positive payload length, capped at 256 MiB |
| `expectedSha256` | Expected 64-digit hexadecimal SHA-256 of the complete file |
| `caCertificatePem` | Public X.509 CA used only by this transfer connection |

Reject oversized/invalid configuration before scheduling. Build a dedicated `KeyStore`/`TrustManagerFactory`/`SSLContext` for this fixture CA; retain HTTPS's normal hostname verification and certificate validity checks. Never install a global trust override, permissive trust manager or hostname verifier. Refuse redirects and unexpected response/length. No cleartext fallback, authentication credentials or downloaded third-party code is involved.

## Lifecycle and integrity invariants

1. Use `JobInfo.Builder.setUserInitiated(true)` with the actual network constraint and expected byte count. The diagnostic minimum API is 35, above UIDT's API34 boundary. The job is not persisted across reboot. Handle schedule failure and exceptions as failures, with no retry loop.
2. Declare `INTERNET`, `ACCESS_NETWORK_STATE`, `RUN_USER_INITIATED_JOBS`, and target37 LAN permission. Protect the nonexported service with `BIND_JOB_SERVICE`. Request `ACCESS_LOCAL_NETWORK` on applicable runtime37 before the actual LAN action. Keep notification consent separate from scheduling eligibility. No FGS type or permission is added.
3. `onStartJob` posts the required job notification immediately and returns without performing network or disk work. Notification controls identify the exact attempt. Any asynchronous preparation that outlives the visible initiating Activity must not silently schedule later.
4. Own network I/O, cancellation and durable progress outside the Activity. Use bounded connect/read timeouts and one active attempt. Stream bytes into an app-private partial file; close/flush it and independently reread the file's length and SHA-256 before publishing a completed journal state. Successful HTTP status alone is insufficient.
5. Persist an attempt identity, expected content identity, observed bytes and explicit terminal/interruption state during work. Cancel/stop fences must prevent a late worker callback from changing a canceled or replaced attempt to completed. Do not call job completion for an execution already stopped by the scheduler.
6. A new process reconciles unfinished durable state to interrupted/unknown. It does not replay a download automatically. An explicit subsequent attempt starts from zero; HTTP Range resume and remote publication are outside this experiment. Preserve prior attempt evidence separately before an operator resets the fixture.
7. User cancel, network failure, `onStopJob`, task removal, Task Manager stop and abrupt process loss are distinct stimuli. Android need not deliver a shutdown callback; a previously saved report is historical, not proof of a live worker.

These invariants were checked in the [source review](../../06-validation/2026-10-15-transfer-probe-review.md). Compiler/JVM evidence and actual device cases remain distinct. The official [UIDT guide](https://developer.android.com/develop/background-work/background-tasks/uidt), [network API](https://developer.android.com/reference/android/net/Network) and [TLS guidance](https://developer.android.com/privacy-and-security/security-ssl) provide the public contracts; exact symbols and availability are maintained in [API39/API41](api-tracker.json).

## Planned acceptance cases

| Case | Actual stimulus and evidence required |
| --- | --- |
| T01 — permission and trust | Explicit LAN denial and grant/retry; actual TLS connection; wrong trust/hostname must fail without partial success |
| T02 — complete download | Visible user start, actual host request, received bytes, independent device-file hash/length equals generated host fixture |
| T03 — window lifetime | Recreate, minimize and restore during active bytes; same attempt and one request/worker, final integrity verified |
| T04 — cancel | Cancel while bytes arrive; exact attempt terminal cancellation and no subsequent completed state; bounded transport cleanup |
| T05 — transport loss | Stop the dedicated fixture during progress; durable failure/partial evidence; explicit new attempt, no silent replay |
| T06 — process/user stop | Separately observe task removal/Task Manager stop/process loss; on return reconcile durable unfinished state, no duplicate request; record unavailable system affordances honestly |

JVM state/race tests and loopback HTTPS fixture tests are software evidence only. Physical cases require exact probe source/APK, host/server, device build, actual stimulus, private evidence hashes and a reviewed sanitized report. No case promotes SFTP, X11, USB, streaming, RDP/VNC or CAP-PLATFORM by itself.

## Bounded device observations

The [0.4.0 preflight](../../../qualification/results/P0-S2/2026-10-15-transfer-preflight.md) records actual permission grant/denial UI, app admission while denied, and queued-attempt recreation/cancellation. Its zero-byte result is retained separately from the [0.4.1 lifecycle run](../../../qualification/results/P0-S2/2026-10-15-transfer-lifecycle.md). Grant did not start work automatically; a disabled Download action is not a socket request denied by the OS.

The lifecycle run binds source `a2b3e33d3ba4dabf40bdd1730e1eaf582c84527d`, version 0.4.1/code5 and APK SHA-256 `704546e5d6a24167c7c982cf3be0e4ab27e2a1fe86221e69aa68f7a1f42d2396`. Attempt 2 completed 67,108,864 bytes with an independently exported SHA-256 matching the generated fixture; recreation, minimize and return retained the active attempt. Attempt 3 exercised native-screen cancellation and stable partial bytes. Attempt 4 exercised notification cancellation while the transfer Activity was hidden, then returned to the same process and saved terminal cancellation. These terminal observations are distinct from the receiver's earlier cancellation-request acknowledgment. Attempt 5 changed only the trusted CA and failed with `SSLHandshakeException`, zero bytes and no payload file; it does not test hostname mismatch. Attempt 6 stopped the host fixture during progress, failed with `ProtocolException`, retained stable partial bytes and did not automatically retry when the server returned. Attempt 7 was a fresh explicit retry and completed another independently exported 67,108,864-byte file with the expected hash.

Attempt 8 was interrupted by an injected, package-scoped `SIGKILL` during claimed work. Four bounded process checks observed absence. The saved pre-kill journal remained historical `running` state at 4,915,200 bytes, while the exported partial file contained 5,177,344 bytes; a checkpoint counter is not the current file length. On relaunch, a different recording process reconciled the same attempt to `interrupted` with reason `new-process-unfinished-attempt`. The partial file's length/hash stayed identical, and recovery revision 3 was unchanged more than 64 seconds later. No probe job was present in the scoped scheduler check, and the restored fixture recorded only the explicit attempts 7 and 8, with no replay after relaunch. Its listener was then stopped and closure verified. All eight attempts remained retained: capacity also disabled a further Start, so this run does not establish post-recovery Start eligibility. The injected kill is separate from task removal, Task Manager stop, force-stop and natural low-memory termination.

The existing selected route reached this fixture after permission grant; transport labels do not identify its physical packet path or prove direct-LAN permission enforcement through a VPN. Wrong-hostname rejection, permission revocation, assigned-network change and Task Manager stop remain untested. Host-stop failure does not substitute for loss/change of the assigned Android network. No result establishes arbitrary power-loss durability, physical input, indefinite background reliability or production SFTP publication/recovery.
