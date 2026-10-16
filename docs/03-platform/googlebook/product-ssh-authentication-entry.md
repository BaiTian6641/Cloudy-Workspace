# Product SSH authentication implementation entry

**Reviewed:** 2026-10-16 against product commit `c531ee1690bbb1f996693a7d86c668896477429f` and SSHJ0.41.2 source `6fad160fe36605a68847ac8a527750c615b4bd04`. **State:** next implementation packet, not implemented or device-qualified authentication. **Owner:** Android/security and SSH workers, accepted by the integration leader. Read [host trust](../../02-architecture/ssh-host-trust.md), [observation](../../02-architecture/ssh-key-observation.md), [vault](product-vault-entry.md) and [P1](../../05-delivery/phases/p1-foundation.md). Full terminal/SFTP/X11, authentication-method, jump, rekey and session requirements remain required.

## Next concrete operation

Implement a fresh-connection authentication check using explicit per-session account/password entry, one declared method and the actual server response, followed by owned transport retirement. Keep the always-rejecting key observer unchanged. A successful finite check establishes bounded authentication evidence; it does not run a command, supply a terminal or complete a supported SSH product.

Per-session input is the existing alternative to saved credentials. It needs no device screen-lock grant, but requires actual entered credentials or narrowly owned fixture credentials. Preserve native editing, `FLAG_SECURE`, bounded mutable buffers and disposal without claiming complete JVM zeroization. Never restore credentials, replay Connect or infer a vault association from a profile label. The pending human vault-readiness question permits no automatic prompt replay.

## Authority and ownership

The fresh connection verifies its own presented key against the exact accepted owning-app pin. An earlier observation, fingerprint string or Boolean trusted flag cannot authorize it. After KEX, coordinate current profile identity, trust generation/revision/key and attempt cancellation at one authentication-admission boundary. Two independent reads leave a race. Publish the admitted connection into its cancellation/revocation ownership mechanism before releasing coordination; do not hold a trust-file lock across a prompt or network exchange. Define lock ordering and failure during ownership publication before code acceptance.

Cancellation or revocation winning before admission prevents credential release. After admission, close transport and prohibit further use; do not promise to retract bytes already admitted. Full live-session rekey/revocation needs continuing ownership and later evidence.

`VaultStorage.use(grant, consumer)` checks its lease before crypto and immediately before calling the consumer, but exposes only `SensitivePayload`. That callback does not provide a revocable guard for later authentication admission. Saved use needs a scoped lease guard and explicit non-secret credential/account references; the current profile model has no credential reference. Do not weaken or mint grants for positive tests. Actual prompt/AES/backing and authorized saved use remain human/device gates.

## Selected upstream method policy

At this pin, `SSHClient.authPassword(...)` supplies password plus keyboard-interactive fallback. Password-only policy must select `SSHClient.auth(username, method)` explicitly. Public-key, keyboard-interactive and other required workflows remain separate implementation work (R275).

`AuthPassword(finder)` also does not refuse password changes: its non-null default update provider returns null, but message60 handling builds a changed-password request. `Buffer.putSensitiveString(null)` encodes an empty string. A null constructor provider instead exposes a null dereference in `shouldRetry`. These are source findings, not executed server cases. [R276 AuthPassword](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/userauth/method/AuthPassword.java), [R277 Buffer](https://github.com/hierynomus/sshj/blob/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/common/Buffer.java).

The reviewed candidate is a small subclass using public non-final methods: reject `Message.USERAUTH_60` with `UserAuthException` before superclass handling and override `shouldRetry()` to return false. Keep a non-null provider, one-release finder and private typed refusal state; do not parse exception text or server prompts into policy. This candidate still needs compilation and behavior tests. Reuse upstream encoding, negotiation and cryptography.

## Worker ownership and acceptance

- Domain: exact owner/profile/peer/trust/attempt/source binding, finite monotonic lifetime, one-use admission and deterministic cancellation/revision races.
- SSH: fresh exact-pin transport, strict method/refusal policy, bounded credentials/results and socket/Reader retirement; preserve observer rejection.
- Platform/security: current-trust admission coordination, cancellation publication and lock ordering; separately guarded saved-vault use.
- UI after contracts freeze: nonexported native per-session entry, full host identity and lifecycle disposal; no saving or restored credentials.
- Leader: build/manifests, real fixture, evidence, independent review, Git and device operations. No new crypto dependency is proposed.

Existing OpenSSH observation/public-key evidence does not qualify password authentication. Prepare an owned password-capable environment without changing the user's account or system SSH policy. A genuine password-change challenge needs a controlled server; packet-level tests remain software evidence and cannot substitute for product/server qualification.

Required cases: successful password authentication without a session; wrong-password denial without retry/fallback; unknown/changed/revoked fresh key with zero credential-provider calls and zero userauth requests; cancellation during authentication wait; and recreation without restored secrets/commands. A real password-change challenge must produce one initial non-change request, zero change/retry/keyboard-interactive requests, typed refusal and confirmed retirement. Preserve exact profile/trust baselines, server method observations and failures. Saved-vault positive use awaits a fresh correlated human prompt and actual protection evidence.
