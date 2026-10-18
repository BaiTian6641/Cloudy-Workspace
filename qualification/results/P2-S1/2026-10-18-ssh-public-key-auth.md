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
- Product regression `37b61b0b-a795-4d03-8db8-c4a45d50c737` (Console debug `17a60422…`, test APK `5484cde1…`) reaches the real Home→Connect→SSH journey, asserts the Password/Private-key selector is visible, asserts Connect stays disabled before any key is chosen, switches back to password and still passes the host-observed command, 20,000-byte paste, exact restoration and cleanup. The final key-specific journey still requires selecting a key document in the system picker and typing its passphrase; that interactive step was not automated. No placeholder or hidden test key was added to product code.
- Still open for C03: keyboard-interactive prompts, per-hop jump trust/authentication, persisted app-owned key references/vault policy, multiple key format interoperability across supported servers, and physical Googlebook qualification.

## Verification limits

The MINA fixture is a real SSH server but not OpenSSH interoperability, PTY/resize, privilege isolation or a Googlebook result. The engine tests prove authentication/ownership/retirement, not terminal input, X11, files or release readiness. The earlier password and interaction evidence retains its own boundaries.
