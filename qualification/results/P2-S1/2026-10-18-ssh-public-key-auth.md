# Console SSH public-key authentication - source and real-server slice

**Work:** 2026-10-18. **Branch:** `codex/console-r1-implementation`. **Scope:** a supported public-key authentication slice for C03; it does not complete keyboard-interactive prompts, verified jump chains, profile credential persistence or full C03/R1.

## Implemented boundary

- `SshSessionCredentials` is the single-transfer session credential boundary. Passwords and private keys share validation/ownership/wipe rules but dispatch to exactly one engine authentication method.
- `SshKeyCredentials` validates username, bounded UTF-8 key text and optional passphrase, transfers material once and wipes owned arrays on close. It is never serialized, logged, placed in a profile or exposed to the terminal renderer.
- `SshKeyAuthentication` parses only after exact host-pin admission:
  - OpenSSH v1 (`BEGIN OPENSSH PRIVATE KEY`), including encrypted keys through a one-release passphrase finder;
  - legacy OpenSSH PEM;
  - RSA PKCS#8.
  - PuTTY, EC PKCS#8 (the maintained SSHJ reader cannot derive its public half) and unknown armor are refused with typed codes. No password, keyboard-interactive, agent or retry fallback follows a key failure.
  - The executed client format in this run is OpenSSH v1 EC. Legacy OpenSSH PEM and RSA PKCS#8 remain parser-supported but were not executed against this fixture.
- `SshAuthenticationCoordinator.beginKey` applies the same exact profile generation, current accepted trust pin, ownership, expiry and capacity checks as the password route. `SshAuthenticationHandle.admit` remains single-transfer and method-neutral.
- `SshAuthenticatedTransportFactory.connectPublicKey` retains the authenticated transport and ownership proof only after the admitted key authenticates. The password route rejects supplied key material, and the key route accepts only key material.
- Console's terminal Connect surface adds a Password/Private-key selector, a bounded session-only document-key read, an optional passphrase field and method-aware Connect enablement. The selected document grant is consumed for that attempt only; no URI, key bytes or passphrase is persisted.

Limitation: SSHJ's `Reader` API forces an immutable text copy of the key. Owned byte arrays, usernames and passphrases are wiped, but no complete-JVM-zeroization claim is made.

## Real-server qualification

Fixture: the tracked `qualification/fixtures/ssh-terminal` Apache MINA SSHD 2.20.0 loopback server. It now also generates one accepted EC P-256 OpenSSH client key (`client_key`) and one deliberately unregistered key (`wrong_key`) inside the already-validated 0700 run directory, after its emptiness check. The accepted public half is resolved through MINA's authorized-key parser; password authentication remains unchanged.

Run `qualification/fixtures/ssh-terminal-01/run-7PRFmv`, port `43281`, host key `SHA256:J3+9+TutuJ3GfTcxmCCACX0F5feL/hxn1isJ6uITMJA`.

Command:

```bash
CLOUDY_SSH_KEY_FIXTURE_ROOT="$PWD/qualification/results/private/ssh-terminal-01/run-7PRFmv" \
  ./gradlew :ssh:test --tests io.github.baitian6641.cloudy.ssh.SshKeyAuthenticationHostTest \
  --offline --no-daemon --console=plain --dependency-verification=strict
```

Executed result: **3/3 passed** (`BUILD SUCCESSFUL`):

1. The accepted OpenSSH EC key creates a retained transport through `connectPublicKey`, retires one ownership proof and closes exactly once.
2. The unregistered `wrong_key` is refused as `AUTHENTICATION_FAILED`; the admitted credential is disposed and no password route is attempted by construction.
3. A pre-cancelled attempt returns `CANCELLED` and never admits the caller-owned key.

Unit coverage: `SshKeyCredentialsTest` exercises copy/one-transfer/wipe, closed-container behavior, limits (username/key/passphrase), malformed UTF-8/NUL, parser refusal codes, encrypted-armor detection and no-cause typed errors. The default `:ssh:test` suite ran 82 tests: 53 passed, 0 failed/errored and 29 opt-in host tests explicitly skipped; the three new key host methods were executed separately with the fixture above (3/3). Existing password/transport suites remain in the same run.

## Coordinator and product status

- The Android coordinator test adds `keyGateRequiresCurrentTrustAndTransfersKeyMaterialOnce`: exact current profile/trust is required, a revoke after preparation cancels the handle and key material is never admitted. On the owned API37 emulator (`platform-debug-androidTest.apk`), `SshAuthenticationCoordinatorTest` ran **22/22 passed** in 0.892 s (`OK (22 tests)`), including the new key case. This is a device test of the app-owned store/coordination boundary, not a real network authentication.
- **Product key-document journey passes:** `69afad68-975d-4c73-8a8c-c57683d17708` (Console debug `919cff33…`, test APK `625b4f53…`) runs Home→Connect→Private key→real system `ACTION_OPEN_DOCUMENT` picker→Downloads→the controller-pushed accepted key→username→connect. The host observes the command effect, the 20,000-byte paste matches at the host, the session exits/CLOSES, the recreated window does not auto-connect, the exact baseline is restored, the pushed key is removed and all cleanup is confirmed. No product debug entry point or hidden key reference was added.
- **Defect found and repaired:** the document result is delivered between `onStart` and `onResume`, when `onPause` has already set the UI's transient `interactive` flag false. The picker callback previously required that flag, so the selected key was silently ignored. It now accepts a non-null granted URI for the still-live window (`!disposed && !busy && !isFinishing && !isDestroyed`) and reads it immediately. The test also enters the username after returning from the picker because the activity deliberately clears native credential fields on pause. Earlier attempts with the old behavior, stale Home semantics and wrong exit-field assertions are preserved as driver/product observations, not passes.
- Product regression `37b61b0b-a795-4d03-8db8-c4a45d50c737` (earlier APKs) additionally covers the Password/Private-key selector visibility, Connect disabled before a key, and the password route.
- Still open for C03: dynamic multi-prompt keyboard-interactive UI (the single-prompt product journey is recorded separately), per-hop jump trust/authentication, persisted app-owned key/credential references under the approved vault policy, multiple key format interoperability across supported servers, and physical Googlebook qualification.

## Verification limits

The MINA fixture is a real SSH server but not OpenSSH interoperability, PTY/resize, privilege isolation or a Googlebook result. The engine tests prove authentication/ownership/retirement, not terminal input, X11, files or release readiness. The earlier password and interaction evidence retains its own boundaries.

## Persisted key reference (profile schema v4)

- The profile stores only an optional `content://` document URI (`keyUri`), never key bytes. The terminal takes a persistable read grant when the user enables **Remember key reference for this host**, saves the reference through `ConnectionProfiles.update` with Connect fenced while the mutation is in flight, and preserves jump/tags/favorite/other fields. The editor and favorite toggle preserve the reference. Schema v4 migrates v1/v2/v3 rows without changing identity or revision.
- On profile selection (including activity recreation), the app switches to the private-key method, re-reads the document, and shows the selected state without opening the picker. A revoked/missing document shows the unreadable refusal; the reference can be forgotten (toggle off) and the key remains session-only.
- **Product journey:** `0dd1a46c-d19e-41c0-87e9-be69b8293fe6` (`rememberKeyRequested=true`, Console `1130e5a6…`, test `fbd2305e…`) passes pick→remember→SSH→host-witnessed command→20,000-byte host-matched paste→exit/CLOSED→activity recreation→profile selection→key re-read without a picker→exact baseline restoration (including the seeded key reference). Platform profile/migration/editor/coordinator tests: **60/60**.
- **Limits:** this is an OS-granted document **reference**, not vault-stored key material; the key file remains external and the grant is revocable. The product test proves activity recreation, not a full process kill (the persistable grant and app-private row are designed for that boundary but were not exercised). Vault-enrolled key/passphrase storage under the existing user-auth policy remains open, as do distinct-host jump credentials and dynamic multi-prompt interaction.
