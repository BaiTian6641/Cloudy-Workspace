# SSH host trust and native management

**Reviewed:** 2026-10-16. **Status:** accepted P1-S3 domain/storage and native inspection/confirmed-revocation increments under CAP-FOUNDATION; stage remains in progress. The [original storage result](../../qualification/results/P1-S3/2026-10-16-ssh-host-trust.md) and [storage build manifest](../../qualification/results/P1-S3/2026-10-16-ssh-host-trust-build.json) retain their historical scope. The subsequent [native manager result](../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager.md) and [manager build manifest](../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager-build.json) qualify finite own-app inspection, Cancel, confirmed revocation and controlled Activity recreation. Console/Desktop0.1.5/code6 are installed and stopped. Observed-key enrollment, SSH verification/connection and the credential-release gate remain unimplemented. [ADR0011](../adr/0011-ssh-terminal-investigation.md) retains SSHJ0.41.2 as an experimental candidate. P1-S4 adoption, CAP-SSH support and the full [P2-S1 transport/authentication/jump scope](../05-delivery/phases/p2-console.md#p2-s1--shared-ssh-transport-authentication-and-jumps) remain separate gates.

Read with [connection profiles](connection-profiles.md), [security and lifecycle](security-and-lifecycle.md) and [handoff admission](handoff-admission.md). A saved profile supplies configuration; a trust record identifies an accepted SSH peer key. Neither supplies credentials, an authenticated session or a registered handoff resource. RDP/VNC TLS certificate trust retains its [separate contract](rdp-authentication-security.md).

## Initial identity and policy

The storage API binds each ledger to its owning app, package and process UID in credential-encrypted no-backup storage. Installed-product trust isolation remains unqualified. Shared source never imports the sibling's pins, diagnostic fixtures or credential records. Ordinary SSH requires neither a sibling nor the custom host agent.

The initial slice accepts **one exact SHA256 key pin per logical peer**. Supported domain key-format identifiers are `ssh-ed25519`, `ecdsa-sha2-nistp256`, `ecdsa-sha2-nistp384`, `ecdsa-sha2-nistp521` and `ssh-rsa`. Here `ssh-rsa` identifies public-key encoding, not permission to use a SHA1 signature algorithm; negotiated signatures retain their separate modern policy ([RFC8332](https://www.rfc-editor.org/rfc/rfc8332.html), R271). Certificates and all other formats fail closed as unsupported, never falling back to first-use enrollment. Identity uses the existing `EndpointHost` canonicalization, explicit port and a typed `Direct` route. Do not use a profile label, window UUID, resolved IP or local forwarding socket as peer identity. Unsupported routed identities fail explicitly; an arbitrary opaque route string is not a substitute for a reviewed route model.

Full jump support remains required at P2-S1: an ordered route of logical hops and final target, with each hop independently verified before its authentication. Gateway trust never proves the target. Profile-label changes do not change trust; endpoint/protocol/route changes must resolve their exact new identity rather than inherit a profile UUID's authority. OpenSSH's [HostKeyAlias](https://man.openbsd.org/ssh_config#HostKeyAlias) (R17) similarly separates a host-key lookup identity from a transport hostname; this is reference guidance, not an adopted OpenSSH-config feature.

Unknown, matching, changed and revoked states remain distinct. Any different observed key at an enrolled peer is **changed**, including a different key type. It must not become ordinary first use because that algorithm lacks a row. Revocation retains a tombstone; forgetting a pin cannot silently reset the peer to first use. Deliberate re-enrollment from revoked state requires an explicit reviewed action.

## Implemented domain/storage APIs

The current source defines these immutable values and typed results:

| Type | Required contents or outcomes |
| --- | --- |
| `SshPeerIdentity` | Canonical logical host, port and typed route; initially `Direct` only |
| `SshHostKeyIdentity` | SSH key-format identifier and canonical SHA256 fingerprint; no private material |
| `SshTrustSnapshot` | Owner, store generation, exact peer, positive revision and `SshTrustStatus.Accepted` or `.Revoked` retaining the last key |
| `SshTrustEvaluation` | `Match`, `Unknown`, `Changed`, `Revoked`; each binds the observed key, edit token and original snapshot when present |
| `SshTrustChallenge` | Attempt identity, presentation/session generation, exact peer and observed key, original store generation/revision and first-use/replacement/re-enrollment reason |
| `SshTrustEditToken` | Owner, generation, peer and expected revision; revision 0 denotes observed absence |
| `SshHostTrustStore` | `load`, `lookup`, `evaluate`, `accept(challenge)` and `revoke(token)`; persistence only |
| `SshTrustStoreResult` | `Success(value)` or `Failure(SshTrustStoreCode)`; unavailable is a storage failure, not an evaluation subtype; success follows commit |

The implemented immutable challenge contains proposal metadata, **not a grant**, live controller or authentication admission. Private constructors validate bounded fields. Ordinary string representations redact endpoints, fingerprints and identifiers. Mutable byte representations, if used internally, require defensive copies. Fingerprints use canonical `SHA256:` plus unpadded standard Base64 for exactly 32 digest bytes; decoder/re-encoder checks reject aliases ([G211](https://developer.android.com/reference/java/util/Base64)). Public-key parsing, serialization and cryptography belong to maintained libraries, not this domain model.

## Native inspection and confirmed revocation

Each independently installed product opens its nonexported `CloudySshTrustActivity` from the workspace. The manager loads the actual owning-app ledger and displays canonical host, port, direct route, key format, SHA256 fingerprint, Accepted/Revoked state and revision. Selection is bound to the loaded immutable catalog. Revoked rows keep their last key and expose no deletion or re-enrollment control. This manager has no manual fingerprint enrollment, import, Connect or authentication action.

Confirmation freezes the original displayed snapshot and owner/generation/peer/revision token. Cancel, pause, save and destruction discard it; neither selection nor approval is restored. A competing replacement invalidates the old revision and produces a conflict requiring a fresh read and new confirmation. Reads that fail clear details and do not turn unavailability into a successfully empty list. Returning from pause reloads committed data without authorizing a mutation.

A private process-wide single-thread executor with a bounded16-entry queue performs storage work off the main thread. The pending bit is set before mutation admission. Recreated initial reads are ordered behind an admitted predecessor, but successful automatic loading preserves restored uncertainty. Only successful explicit Refresh reconciles the current catalog, without asserting which attempt caused its state. Queue rejection preserves earlier uncertainty. Close remains available while busy or unavailable; a previously admitted revocation can still commit, without automatic retry or an implied rollback.

The custom versioned saved-state record contains only `schema`, owning-app `owner` and the pending/uncertain boolean. It contains no selected snapshot, confirmation or authorization. This statement is limited to that custom record: framework/Compose saved list state is separate and can contain ordinary primitive peer-key metadata. Lazy item keys use an unambiguous primitive representation, and inspection scrolls to the selected details ([G212](https://developer.android.com/develop/ui/compose/lists)).

Selectable exact fields and an explicit fingerprint-only native `ClipboardManager` action are implemented. Copying is user-requested, uses a fixed public-fingerprint label, reports failures without raw provider details, and relies on API33+ system copy feedback rather than duplicate app notifications ([G125](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)). Clipboard execution and physical selection remain untested. Bounded transient state and lifecycle handling follow [G58](https://developer.android.com/topic/libraries/architecture/saving-states) and [G208](https://developer.android.com/guide/components/activities/activity-lifecycle); Activity recreation is not actual process-death evidence.

Strict manager build02 passes with zero Console/Desktop/platform Lint findings. Seventeen actual Android cases pass on the identified HP API37 build: three custom Bundle/Parcel cases, four trust Activity cases per product, and three workspace cases per product. The controlled admitted-revocation case commits once across Activity recreation and preserves uncertainty until explicit Refresh. Exact fixture cleanup restores each baseline; both trust ledgers remain initialized and empty, profile/vault/boot observations are unchanged, and all three temporary test packages are removed. See the manager result and manifest above for artifact identities, the preserved first build failure and the case-by-case limits.

## Enrollment and lifetime

The following observed-key enrollment/controller and authentication flow remains future adapter work. The implemented manager inspects and revokes existing records only.

No credential authentication operation proceeds before exact host-key acceptance. First-use presentation identifies host, port, route, key type and SHA256 fingerprint, with comparison guidance and explicit save/cancel. Replacement displays old/new identities prominently. Every approval binds the observed key and original generation/revision; concurrent enrollment, replacement or revocation invalidates it.

The intended first adapter flow is deliberately finite:

1. Capture the exact validated profile/peer attempt and observe the server key through SSHJ.
2. Match an existing pin or reject the connection with a typed trust challenge; release transport ownership on rejection/cancellation.
3. Present the challenge in the foreground and commit only the explicitly approved pin with atomic compare-and-set.
4. Require a deliberate fresh Connect action. Its new connection verifies the actual presented key again before releasing credentials.

Saving trust does not automatically resume authentication. Cancelled, hidden/destroyed or superseded presentations release no approval. Restored metadata never authorizes enrollment or replays Connect. A completion-unknown write requires reload/reconciliation, without automatic retry or claimed success.

Live-session rekey and revocation effects are **required adapter work**, not an established policy or an inherited diagnostic pass. Define connection ownership, callback checks, cancellation and active-session consequences before that route is accepted.

The first two invariants have domain/storage coverage; observed-key enrollment-controller and authentication integration remain pending:

- **Complete expected-state CAS:** enrollment binds owner, store generation, canonical peer and observed absence; revision 0 represents absence only. Replacement/re-enrollment binds the original positive revision and accepted/revoked state, including its last key. Concurrent enrollment conflicts; no upsert or delete-then-create bypass is permitted.
- **Exact match result:** a match identifies the peer, actual observed key and matched store version. It is not a reusable Boolean approval or credential grant. The fresh connection compares its own handshake key again; the earlier observation cannot authorize it.
- **Live challenge ownership:** proposed controller policy gives each challenge a receiver-owned monotonic deadline no more than 120 seconds after presentation. Acceptance requires the current attempt and presentation generation as well as an unexpired deadline. Pause, destruction, cancellation, supersession or profile/peer selection change retires it immediately. Never persist the deadline as approval or restart it on restoration. These rules define the future observed-key challenge controller. The current manager clears revocation confirmations on lifecycle loss, but does not present or approve an observed handshake key.
- **Verification-to-authentication ordering:** before credential release, the transport owner must coordinate current peer/key/version revalidation with trust replacement/revocation and attempt cancellation. A revision change or cancellation that wins before the authentication gate must prevent release. Define the synchronization boundary and test the race; merely checking twice without ordering does not close it. Post-gate revocation and live rekey consequences still need explicit session policy and adapter qualification.


## Library boundary

Reviewed upstream revision: `6fad160fe36605a68847ac8a527750c615b4bd04`, matching ADR0011's SSHJ candidate. [`HostKeyVerifier`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/verification/HostKeyVerifier.java) (R267) supplies `verify(hostname, port, PublicKey)` and `findExistingAlgorithms`. The future Cloudy adapter captures logical attempt identity independently of callback transport addresses, and returns true only for an exact authorized pin. Algorithm preference must stay inside the approved modern algorithm policy; key-format names do not authorize deprecated signature algorithms.

[`FingerprintVerifier`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/verification/FingerprintVerifier.java) (R268) uses SSHJ's `Buffer.PlainBuffer.putPublicKey` and supports SHA256 comparison. Restrict Cloudy's interface to SHA256 rather than exposing its MD5/SHA1 options. [`SecurityUtils.getFingerprint`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/common/SecurityUtils.java) (R269) uses MD5 at this pin and is not the display helper.

[`OpenSSHKnownHosts`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/verification/OpenSSHKnownHosts.java) (R270) can skip malformed entries, matches applicability by key type and writes directly to files. Its raw persistence is not Cloudy's durable fail-closed ledger. Any later explicit known-host import needs its own preview/rejection contract. [OpenSSH strict checking](https://man.openbsd.org/ssh_config#StrictHostKeyChecking) (R17) distinguishes confirmed first use from automatic acceptance; no permissive verifier or automatic changed-key acceptance is authorized here.

## Construction ownership and acceptance

Direct Codex workers authored [SshHostTrust.kt](../../shared/android/domain/src/main/kotlin/io/github/baitian6641/cloudy/domain/SshHostTrust.kt), its [JVM test](../../shared/android/domain/src/test/kotlin/io/github/baitian6641/cloudy/domain/SshHostTrustTest.kt), [SshHostTrustStore.kt](../../shared/android/platform/src/main/kotlin/io/github/baitian6641/cloudy/platform/SshHostTrustStore.kt) and its [Android test](../../shared/android/platform/src/androidTest/kotlin/io/github/baitian6641/cloudy/platform/SshHostTrustStoreTest.kt). The leader retains integration and acceptance. The subsequent manager adds [CloudySshTrustActivity](../../shared/android/platform/src/main/kotlin/io/github/baitian6641/cloudy/platform/CloudySshTrustActivity.kt), [SshTrustScreen](../../shared/android/platform/src/main/kotlin/io/github/baitian6641/cloudy/platform/SshTrustScreen.kt) and [SshTrustUiState](../../shared/android/platform/src/main/kotlin/io/github/baitian6641/cloudy/platform/SshTrustUiState.kt); its source and test hashes belong to the manager build manifest.

The store follows the established app-private SQLite generation/CAS pattern: off-main operations, original device-protected-context denial, bounded storage, nondeleting corruption handling and no automatic reset of lost established state. Capacity is 512 peers including tombstones, with a 4 MiB main-database ceiling excluding journal/filesystem overhead. Enrollment/replacement/revocation compare expected state and mutate within one transaction. Failure after a possible commit returns completion unknown. Whole-app reset and privileged snapshot rollback remain outside ordinary persistence guarantees.

**Historical domain/storage checkpoint:** strict build01 passes with 38 domain JVM cases, including 12 new trust cases, and zero Console/Desktop/platform Lint issues. Twenty actual standalone Android storage cases pass on HP Googlebook, API37 build `CL3B.260622.276.R2`. At that checkpoint the test APK was removed and installed0.1.4/code5 products, profile/vault state and boot observations remained unchanged; the later manager checkpoint above installs0.1.5/code6. These are isolated-root storage checks, not installed-product trust isolation, process-restart or physical power-loss evidence. Exact artifacts and results belong to the linked qualification records.

Remaining gates:

- Observed-key enrollment UI/controller: exact old/new handshake-key presentation, foreground/cancel ownership, current-attempt/deadline validation and recreation without approval replay remain unimplemented. The native inspection/revocation manager has its finite tested scope above. Its clipboard, physical input/selection, both-theme/scaled/narrow visual quality, unavailable-store exit and actual process-death behavior remain unqualified.
- Engine: real unknown/changed/revoked rejection before credential release; explicit enrollment followed by fresh exact-key acceptance; wrong port/forwarded peer rejection; cancellation cleanup; separately qualified rekey/revocation and ordered jump hops.
- Ownership: each actual product now passes finite own-app trust management cases with exact baseline restoration. Cross-app trust-file isolation remains unqualified; neither isolated-root storage tests nor separate own-app management cases establish it.

Apply [D12 library reuse](../05-delivery/library-reuse-policy.md): carry unchanged accepted P0 evidence forward, test Cloudy's changed boundary and rely on upstream coverage for unchanged parser/crypto internals. The historical storage result and subsequent finite native manager result do not adopt an engine, qualify enrollment/authentication/jumps or live-session behavior, or complete any capability, stage or release gate.
