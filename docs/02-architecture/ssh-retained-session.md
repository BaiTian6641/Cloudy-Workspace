# Retained SSH ownership and bounded read-only SFTP

**Construction checkpoint: 2026-10-16, P1-S3.** Cloudy has a retained password-authenticated SSH transport, continuing owning-app profile/trust invalidation, and bounded read-only SFTP operations. The [qualification result](../../qualification/results/P1-S3/2026-10-16-ssh-retained-session.md) separates host engine behavior, simulated policy proofs and actual Android engine proofs. This is a bounded CAP-SSH experiment. It neither completes P1-S3 nor opens the gated P2 stages.

The existing [finite password check](ssh-password-admission.md) and native credential-entry route retain their finite behavior: authentication followed by connection closure. They do not automatically become retained-session UI. Installed Console/Desktop remain the preceding **0.1.9** checkpoint; the newly built product artifacts have not been installed. No background session service, terminal, command/PTY screen, upload or automatic reconnect is claimed.

## Caller and proof contract

A retained caller first uses `SshAuthenticationCoordinator.begin(exactProfile, credentials)` on an owning background worker. Preparation consumes the credential holder on every outcome. `Ready` returns the same expiring handle used by finite authentication. Its initial authority remains bounded to 45 seconds of elapsed time, and exact current profile and accepted trust must pass admission before password release.

Call `SshAuthenticatedTransportFactory.connectPassword` with these exact bindings:

```kotlin
factory.connectPassword(
    peer = handle.profile.peer,
    address = resolvedNumericAddress,
    expectedKey = handle.expectedKey,
    cancellation = handle.cancellation,
    admit = handle::admit,
    establish = handle::establishSession,
    retired = handle::releaseSession,
    timeoutMillis = remainingAuthenticationBudget,
)
```

Resolution, network permission and foreground policy remain the caller's responsibility. Bound resolution and the supplied 1–10,000ms transport budget by the original authentication lifetime. DNS or the numeric route is not trust identity. Do not replace the cancellation signal, retry another address automatically, derive credential associations from profile labels, or restore/replay authentication.

`SshAuthenticatedOwnership` is created by the engine only after actual SSHJ authentication succeeds. Its peer, complete key identity and cancellation object bind that connection; textual reconstruction is not authority. It has an internal constructor and a public read-only `isRetired` flag. Reflected test proofs exercise policy only and are explicitly separated from real engine-created proofs.

`establishSession(proof)` runs on the owning worker under coordination. It requires the admitted phase, exact peer/key, identical cancellation object, unretired proof, unchanged full profile and trust snapshots, and unexpired original authority. A second final check occurs at the cancellation/ownership gate. Failure closes the attempted transport; it cannot manufacture a `Connected` result. Successful promotion retains the exact proof, removes the authentication timer and establishes continuing ownership. The expiry callback also checks phase: cancelling its Future alone would not defeat a timer already executing.

## Authentication deadline and continuing lifetime

After successful promotion, expiration of the original authentication deadline no longer closes the established connection. It cannot authorize another password request. Credential arrays are still one-use and wiped; no password remains in the continuing platform lease. Immutable JVM/upstream copies prevent a complete-zeroization claim.

The handle remains in the process registry while established. Profile update/delete and peer trust acceptance/replacement/revocation cancel matching owners before the write attempt, preserving the existing conservative invalidation policy on failed or uncertain writes. Unrelated profiles and peers remain independent. Lock ordering stays coordination → store monitor → permanent file lock → SQLite, followed by the separate short cancellation/ownership gate; no network operation or arbitrary engine callback runs under coordination.

`close()` or invalidation signals cancellation but does not release an established proof's registry capacity. `releaseSession(proof)` removes it only for the exact promoted object with `isRetired == true`. A different proof, a close request or a Boolean trusted flag cannot release that slot. There is no cross-process cancellation service: owning package/UID, CE context and default-process restrictions remain required.

## Exact pin, negotiation and rekey

The retained path reuses SSHJ0.41.2 and the existing narrow ECDH/ECDSA P-256, AES128-GCM, HMAC-SHA256 and no-compression policy. A fresh connection verifies its own exact expected key before the one-use strict password method. Password-change refusal, no method retry and no keyboard-interactive convenience fallback remain unchanged.

Every later verifier callback must still match the same numeric route/port and complete original pin while ownership remains active. The algorithm verifier remains active for later exchanges. A changed key fails and closes the connection; rekey never creates a trust prompt, updates a pin or releases credentials again. No SQLite read or coordination acquisition occurs on SSHJ's Reader callback.

At this selected pin, authentication success sets the transport's volatile authenticated flag before delivering the worker's authentication promise. An immediate post-auth rekey may therefore reach verification before `auth()` returns. Same-key verification does not depend on a later worker-only flag. `KeyExchanger` verifies the host before client NEWKEYS. The separately documented pre-authentication rekey timeout limitation remains: retained post-auth behavior does not repair or redefine that case.

These are selected-pin implementation findings, not guarantees across future SSHJ versions. [KeyExchanger](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/KeyExchanger.java), [UserAuthImpl](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/userauth/UserAuthImpl.java) and [TransportImpl](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/TransportImpl.java) supply the upstream ordering; actual backend evidence remains separate.

## Read-only files and bounds

`SshAuthenticatedTransport.files` exposes synchronous-worker `list` and `read` operations, one active channel at a time. Another operation returns Busy immediately. Sequential operations create fresh SFTP channels on the same retained authenticated connection. No raw SSHClient, SFTPClient, remote stream or writable handle escapes the public adapter.

| Boundary | Current bound/behavior |
| --- | --- |
| Remote path | 1–4,096 UTF-16 units; valid bounded text, no normalization or local-path interpretation |
| Returned leaf name | 1–1,024 UTF-16 units; controls, malformed surrogates, separators and dot leaves rejected |
| Directory listing | Default 128, maximum 512 entries; selector BREAK stops further accumulation/requests; overflow is failure |
| Complete regular-file read | Default 65,536, maximum 1,048,576 bytes; explicit READ handle; bounded chunks and maxBytes+1 detection |
| Operation deadline | Default 5,000, permitted 1–10,000ms across initialization, requests, reads and child retirement |
| Incoming SFTP frame | 262,144 bytes maximum, checked before exposing the final length-prefix byte to upstream allocation |

Read overflow never returns a successful prefix. Size metadata is not trusted as a complete-file bound; reads continue to explicit EOF within the cap. A remote file may change during the operation: no snapshot consistency, stable size or atomic remote view is promised. Listings return bounded immutable entry collections; byte results expose defensive copies. Remote names are untrusted remote/display values and must never be concatenated into trusted local destinations.

SSHJ's directory scanner decodes the name, discarded longname and attributes before invoking the selector. Its string decoder has a 32,768-byte bound but uses Java String decoding that can replace malformed UTF-8. Cloudy validates the resulting names; it does **not** claim strict wire UTF-8 rejection or lossless access to arbitrary non-UTF8 filenames. [RemoteDirectory](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/sftp/RemoteDirectory.java), [Buffer](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/common/Buffer.java).

The frame guard is necessary independently of facade limits: selected PacketReader otherwise accepts the inherited 1GiB Buffer maximum before allocation. The guard preserves accepted bytes and delegates all message decoding, requests and cryptography to SSHJ. Its bound is not a claim that total process memory or lower SSH/channel buffering is limited to one frame. No library fork or new protocol engine is introduced.

## Ownership hooks and actual retirement

`SshOwnedSftp` uses selected-pin public/protected surfaces: explicit SFTPEngine construction, its protected actual PacketReader, public SessionChannel construction/open, and Session/Subsystem delegation for the guarded input. The tracked factory publishes the actual channel before potentially failing open/subsystem requests, including partial constructor failure. SSHJ retains protocol behavior. `SFTPClient.ls` closes its RemoteDirectory on selector BREAK; explicit file handles close on every path.

SFTPEngine.close interrupts PacketReader but does not join it. Cloudy joins the actual SFTP reader after each operation and retains any outstanding reader/channel for transport cleanup. API close completion or a thread name is insufficient retirement evidence. These hooks require review when the selected library changes. Source-register linkage is R286–R293 in [sources.json](../07-reference/sources.json), alongside existing R57/R58/R95/R273–R277; compiler acceptance and actual qualification are distinct.

The retained owner wakes for cancellation, deadline, policy rejection or peer disconnect; cancellation callbacks perform no socket I/O. Idle channel reads and the authenticated socket's SO_TIMEOUT are explicitly cleared so quiet sessions can remain connected. Finite observer/authentication defaults remain unchanged. Per-operation watchdogs and request deadlines remain active. Failed admitted protocol operations retire the transport rather than reconnecting or reusing an uncertain child.

`requestClose()` is nonblocking. Shutdown closes the owned socket and uses a shared two-second budget for setup publication, transport Reader, active operation, SFTP PacketReader/channel and close helper. A notification waiter joins the owner before marking the proof retired and invoking the platform release callback. `awaitClosed` has a finite caller budget and additionally joins that notification publisher. A timeout waiting is not successful retirement. Factory capacity is reusable only after owner and notification exit; uncertain cleanup or callback failure quarantines it. No success claim, reset or registry release may hide outstanding resources.

## Evidence and next integration

The scoped checkpoint passes strict build01 and four zero-finding Android Lint reports. Host01 passes **9/9 actual maintained-server cases**. Device01 passes **61 Android policy/storage cases**, including **nine explicitly reflected/simulated-proof cases**, plus **three actual engine-proof cases**; three default opt-in cases skip. The reflected cases are not authentication evidence. Exact artifact, hardware, scenario and preservation details belong to the [qualification result](../../qualification/results/P1-S3/2026-10-16-ssh-retained-session.md). Installed 0.1.9 product bytes remain unchanged; new product artifacts are built-only.

A real native retained caller remains to be integrated. It must own a stable factory, explicit foreground policy, exact profile/attempt binding, bounded resolver and permission behavior, transient input disposal, cancellation/close and visible retirement state. Creating a new Activity/factory must not reset quarantine. Lifecycle loss must not restore credentials, replay authentication or imply a session survived. The present finite native authentication screen continues to close its connection.

Command/PTY ownership, terminal byte flow and desktop input, integrated SFTP browsing/local publication/drop, X11, jumps, other authentication methods, saved-vault lease/account binding, background retention/recovery and broader network/backend/device qualification retain their separate gates. This increment provides real retained ownership and read-only SFTP construction without promoting full SSH or platform product acceptance.
