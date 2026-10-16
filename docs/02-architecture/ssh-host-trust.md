# SSH host-trust construction entry

**Reviewed:** 2026-10-16. **Status:** proposed P1-S3 construction under CAP-FOUNDATION. This entry defines the next bounded trust domain/storage work; it implements no verifier, enrollment UI, connection or device result. [ADR0011](../adr/0011-ssh-terminal-investigation.md) retains SSHJ0.41.2 as an experimental candidate. P1-S4 adoption, CAP-SSH support and the full [P2-S1 transport/authentication/jump scope](../05-delivery/phases/p2-console.md#p2-s1--shared-ssh-transport-authentication-and-jumps) remain separate gates.

Read with [connection profiles](connection-profiles.md), [security and lifecycle](security-and-lifecycle.md) and [handoff admission](handoff-admission.md). A saved profile supplies configuration; a trust record identifies an accepted SSH peer key. Neither supplies credentials, an authenticated session or a registered handoff resource. RDP/VNC TLS certificate trust retains its [separate contract](rdp-authentication-security.md).

## Initial identity and policy

Console and Desktop own independent credential-encrypted no-backup trust ledgers. Shared source never imports the sibling's pins, diagnostic fixtures or credential records. Ordinary SSH requires neither a sibling nor the custom host agent.

The initial slice accepts **one exact SHA256 key pin per logical peer**. Identity uses the existing `EndpointHost` canonicalization, explicit port and a typed `Direct` route. Do not use a profile label, window UUID, resolved IP or local forwarding socket as peer identity. Unsupported routed identities fail explicitly; an arbitrary opaque route string is not a substitute for a reviewed route model.

Full jump support remains required at P2-S1: an ordered route of logical hops and final target, with each hop independently verified before its authentication. Gateway trust never proves the target. Profile-label changes do not change trust; endpoint/protocol/route changes must resolve their exact new identity rather than inherit a profile UUID's authority. OpenSSH's [HostKeyAlias](https://man.openbsd.org/ssh_config#HostKeyAlias) (R17) similarly separates a host-key lookup identity from a transport hostname; this is reference guidance, not an adopted OpenSSH-config feature.

Unknown, matching, changed and revoked states remain distinct. Any different observed key at an enrolled peer is **changed**, including a different key type. It must not become ordinary first use because that algorithm lacks a row. Revocation retains a tombstone; forgetting a pin cannot silently reset the peer to first use. Deliberate re-enrollment from revoked state requires an explicit reviewed action.

## Proposed public model

These names describe intended interfaces, not existing classes:

| Type | Required contents or outcomes |
| --- | --- |
| `SshPeerIdentity` | Canonical logical host, port and typed route; initially `Direct` only |
| `SshHostKeyIdentity` | SSH key-format identifier and canonical SHA256 fingerprint; no private material |
| `SshTrustSnapshot` | Store generation, exact peer, positive revision, accepted key or revocation tombstone |
| `SshTrustEvaluation` | `Match`, `Unknown`, `Changed`, `Revoked`, or fixed-code `Unavailable`; comparisons include the observed key |
| `SshTrustChallenge` | Attempt identity, presentation/session generation, exact peer and observed key, original store generation/revision and first-use/replacement/re-enrollment reason |
| `SshTrustEditToken` | Immutable store generation, exact peer and original revision; first enrollment also binds the observed absence |
| `SshTrustMutationResult` | Saved/revoked, conflict, stale generation, unavailable or completion unknown; success follows commit |

Private constructors validate bounded fields. Ordinary string representations redact endpoints, fingerprints and identifiers. Mutable byte representations, if used internally, require defensive copies. Display fingerprints use canonical `SHA256:` plus unpadded standard Base64 for exactly32 digest bytes. Public-key parsing, serialization and cryptography belong to maintained libraries, not this domain model.

## Enrollment and lifetime

No credential authentication operation proceeds before exact host-key acceptance. First-use presentation identifies host, port, route, key type and SHA256 fingerprint, with comparison guidance and explicit save/cancel. Replacement displays old/new identities prominently. Every approval binds the observed key and original generation/revision; concurrent enrollment, replacement or revocation invalidates it.

The intended first adapter flow is deliberately finite:

1. Capture the exact validated profile/peer attempt and observe the server key through SSHJ.
2. Match an existing pin or reject the connection with a typed trust challenge; release transport ownership on rejection/cancellation.
3. Present the challenge in the foreground and commit only the explicitly approved pin with atomic compare-and-set.
4. Require a deliberate fresh Connect action. Its new connection verifies the actual presented key again before releasing credentials.

Saving trust does not automatically resume authentication. Cancelled, hidden/destroyed or superseded presentations release no approval. Restored metadata never authorizes enrollment or replays Connect. A completion-unknown write requires reload/reconciliation, without automatic retry or claimed success.

Live-session rekey and revocation effects are **required adapter work**, not an established policy or an inherited diagnostic pass. Define connection ownership, callback checks, cancellation and active-session consequences before that route is accepted.

## Library boundary

Reviewed upstream revision: `6fad160fe36605a68847ac8a527750c615b4bd04`, matching ADR0011's SSHJ candidate. [`HostKeyVerifier`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/verification/HostKeyVerifier.java) (R267) supplies `verify(hostname, port, PublicKey)` and `findExistingAlgorithms`. The future Cloudy adapter captures logical attempt identity independently of callback transport addresses, and returns true only for an exact authorized pin. Algorithm preference must stay inside the approved modern algorithm policy; key-format names do not authorize deprecated signature algorithms.

[`FingerprintVerifier`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/verification/FingerprintVerifier.java) (R268) uses SSHJ's `Buffer.PlainBuffer.putPublicKey` and supports SHA256 comparison. Restrict Cloudy's interface to SHA256 rather than exposing its MD5/SHA1 options. [`SecurityUtils.getFingerprint`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/common/SecurityUtils.java) (R269) uses MD5 at this pin and is not the display helper.

[`OpenSSHKnownHosts`](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/transport/verification/OpenSSHKnownHosts.java) (R270) can skip malformed entries, matches applicability by key type and writes directly to files. Its raw persistence is not Cloudy's durable fail-closed ledger. Any later explicit known-host import needs its own preview/rejection contract. [OpenSSH strict checking](https://man.openbsd.org/ssh_config#StrictHostKeyChecking) (R17) distinguishes confirmed first use from automatic acceptance; no permissive verifier or automatic changed-key acceptance is authorized here.

## Construction ownership and acceptance

A future domain worker may own new `shared/android/domain/.../SshHostTrust.kt` and its JVM test. A separate storage worker may own new `shared/android/platform/.../SshHostTrustStore.kt` and Android tests. Paths are proposals under the existing package roots. The leader owns build edges, source/API registration, native UI, engine integration and acceptance; workers must receive exact non-overlapping paths before dispatch.

Reuse the established app-private SQLite generation/CAS pattern: off-main operations, original device-protected-context denial, bounded storage, nondeleting corruption handling and no automatic reset of lost established state. Enrollment/replacement/revocation compare expected state and mutate within one transaction. Failure after a possible commit returns completion unknown. Whole-app reset and privileged snapshot rollback remain outside ordinary persistence guarantees.

Required gates:

- Domain/storage: canonical host/port and route separation; cross-key-type changed detection; tombstones; concurrent first-use/replacement/revocation conflicts; generation mismatch; bounded capacity; missing/corrupt/newer-schema denial; no credential data.
- Native UI: exact peer and old/new fingerprint visibility, foreground/cancel ownership, stale challenge rejection, recreation without approval replay and honest uncertain/unavailable states.
- Engine: real unknown/changed/revoked rejection before credential release; explicit enrollment followed by fresh exact-key acceptance; wrong port/forwarded peer rejection; cancellation cleanup; separately qualified rekey/revocation and ordered jump hops.
- Ownership: each installed product independently manages its own trust; isolated fixture roots alone cannot establish that result.

Apply [D12 library reuse](../05-delivery/library-reuse-policy.md): carry unchanged accepted P0 evidence forward, test Cloudy's changed boundary and rely on upstream coverage for unchanged parser/crypto internals. No new dependency adoption, device pass, supported capability or stage completion follows from this entry.
