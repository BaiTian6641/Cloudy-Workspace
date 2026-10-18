# Console SSH keyboard-interactive authentication - single-prompt slice

**Work:** 2026-10-18. **Branch:** `codex/console-r1-implementation`. **Scope:** one supported keyboard-interactive flow for C03: exactly one server prompt, answered once by a bounded response. Multi-prompt or repeated challenges are refused. It does not complete dynamic multi-prompt UI, OTP enrollment, persisted credential references, jumps or full C03/R1.

## Implemented boundary

- `SshKeyboardInteractiveCredentials` is a single-transfer username + response credential with bounded UTF-8 validation and wipe-on-close. It shares `SshSessionCredentials` ownership with password and key material.
- `SshOneReleaseKeyboardInteractiveResponder` implements SSHJ's `ChallengeResponseProvider`:
  - reports no submethods and never retries;
  - returns the response for the first prompt only; a second prompt in the same challenge, a retry, a cancelled attempt or an inactive attempt throws a typed refusal and sets `unsupported`;
  - erases the response after the authentication call.
- `SshAuthenticationCheck`, `SshAuthenticatedTransportFactory.connectKeyboardInteractive` and `SshAuthenticationCoordinator.beginKeyboardInteractive` use the same exact profile/trust/ownership gate, host-pin admission, expiry, single-transfer and retirement path as password/key. No password, public-key or agent fallback is attempted.
- Console adds a third method choice (Keyboard-interactive) with one secret response field and method-aware Connect enablement. No server prompt text is rendered by this slice; the user supplies the response for the single supported prompt, and the UI states that multi-prompt challenges are refused.

## Real-server qualification

Fixture: tracked MINA SSHD 2.20.0 loopback server (`qualification/fixtures/ssh-terminal`), now with a keyboard-interactive authenticator that issues one `Response: ` prompt and accepts only the existing generated password. Run `run-A6HdbJ`, port `33469`, host key `SHA256:swbwjg9uFNS1k5WLoAcAGYcvln69czsxYF5mLFAJh1Q`.

Command:

```bash
CLOUDY_SSH_KEY_FIXTURE_ROOT="$PWD/qualification/results/private/ssh-terminal-01/run-A6HdbJ" \
  ./gradlew :ssh:test \
  --tests io.github.baitian6641.cloudy.ssh.SshKeyboardInteractiveHostTest \
  --tests io.github.baitian6641.cloudy.ssh.SshKeyAuthenticationHostTest \
  --offline --no-daemon --console=plain --dependency-verification=strict
```

Executed result: **6/6 passed** (3 keyboard-interactive + 3 public-key regression):

1. The accepted response creates a retained transport through `connectKeyboardInteractive`, retires one ownership proof and closes once.
2. A wrong response is refused as `AUTHENTICATION_FAILED`; the admitted response is disposed and no other method is configured.
3. A pre-cancelled attempt returns `CANCELLED` without admission and leaves the caller-owned response to the caller.

Unit coverage: `SshKeyboardInteractiveCredentialsTest` covers copy/one-transfer/wipe, limits (username/response), malformed Unicode, one-release answering, repeated-prompt refusal with the typed unsupported flag, and cancellation never releasing the secret. The default `:ssh:test` suite ran 89 tests: 57 passed, 0 failed/errored and 32 opt-in host tests explicitly skipped; the six host methods (three interactive + three key regression) were executed separately against the fixture above.

Device coordination: `SshAuthenticationCoordinatorTest` ran **23/23 passed** on the owned API37 emulator, including `keyboardInteractiveGateUsesTheSameCurrentTrust` (exact current profile/trust, revoke cancels, no admission after revocation). This is an app-store/coordination test, not a network authentication.

## Product journey

`3f57c027-05da-4529-a57e-f92c459b2311` (Console debug `238f6592…`, test APK `fd048765…`, `--auth keyboard-interactive`) passes the real Home→search→Connect→SSH journey: the product method selector exposes Keyboard-interactive, Connect is disabled before a response, the single fixture prompt authenticates with the response, the host-observed command and 20,000-byte host-matched paste complete, the session exits/CLOSES, the recreated window does not auto-connect or restore the draft, and the exact profile/trust baseline is restored. The controller records `authMethod=keyboard-interactive`.

Password regression `90831d5d-4e7f-43fa-86e8-66049fe4f800` (same APKs) passes the same journey with `--auth password`, including the key-surface probe and host-matched paste.

Three earlier attempts are preserved as driver observations, not product passes: `05dafddd…` reached the host effect and 20,000-byte paste but the exit check looked for the password field instead of the interactive response field; `e0f596d1…` and `e7e76cb1…` timed out in Home search (the `home_row` accessibility lookup, not the visible UI; the failure capture shows the correct filtered row). The credential-tag and catalog-readiness corrections were test-driver fixes; no product behavior or assertion was relaxed. The recurring Home-search lookup flake remains an explicit harness limitation.

## Still open

- Dynamic multi-prompt interaction (server-provided prompt sequence in the UI), OTP/duo-style flows and per-prompt validation.
- Per-hop jump trust/authentication and persisted app-owned key/credential references under the approved vault policy. The product key-document picker journey is recorded separately in the public-key evidence.
- Physical Googlebook qualification.

A single-prompt result does not establish general keyboard-interactive interoperability or full C03.
