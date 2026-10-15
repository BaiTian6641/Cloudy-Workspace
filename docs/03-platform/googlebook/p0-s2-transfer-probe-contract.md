# P0-S2 — Real UIDT transfer experiment

**Checked:** 2026-10-15. **Owner:** Android/platform developer. **Status:** 0.4.0 implementation compiled, linted and independently reviewed; device cases pending. This is a diagnostic extension, not Console SFTP, a remote editor, a production transfer engine or a completed platform gate. Preserve [operation lifetimes](p0-s2-operation-lifetimes.md), [Rule 0](../../00-project/platform-rule-zero.md) and [publication recovery](../../02-architecture/file-publication.md).

## Actual operation and scope

Download one generated finite file over HTTPS from the selected Ubuntu host using a real Android user-initiated data transfer job. The user opens a separate native diagnostic screen and explicitly starts the operation while visible. Window recreation must reattach to process-owned state; it must not start another download. A 64 MiB fixture and optional server throttle make lifecycle transitions observable. Neither the file generation nor the throttle is a performance benchmark or a simulation of network success.

The first route is direct Wi-Fi/Ethernet LAN. Do not require Internet validation, unmetered access or a cellular route. Retain the default exclusion of VPN networks for this experiment and use only the `Network` assigned in `JobParameters`; no default-network fallback or global process-network binding. A changed/lost assigned network interrupts the attempt. VPN support is separate qualification.

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
