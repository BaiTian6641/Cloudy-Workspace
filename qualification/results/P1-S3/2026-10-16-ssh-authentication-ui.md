# Native per-session SSH authentication

**Date/stage:** 2026-10-16, P1-S3. **Outcome:** bounded native authentication and lifecycle PASS. Console and Desktop **0.1.9/code10** are installed and stopped at final build03 bytes. The [build manifest](2026-10-16-ssh-authentication-ui-build.json) binds source/toolchain/APKs; the [device manifest](2026-10-16-ssh-authentication-ui.json) separates the ten build02 cases from the six affected final-build reruns. No full SSH, P1-S3 or capability gate is promoted.

## Implemented operation

Both workspaces now open the nonexported `CloudySshAuthenticateActivity`. It loads the owning app's saved SSH profiles, displays the selected endpoint/revision and accepts transient native username/password input. The explicit action verifies the fresh connection's key against current accepted trust and uses the existing exact-profile/trust admission coordinator. It authenticates once, then closes the transport without opening a command, terminal, SFTP channel or reusable session. Success copy describes that finite result; denial is generic authentication failure rather than a claimed wrong password.

The screen uses existing Cloudy typography/control geometry and native `TextFieldValue` editing, preserving selection/composition while the entry remains active. `FLAG_SECURE` is set before composition; the decor excludes autofill descendants. No credential enters saved state, Intents, preferences or a renderer bridge. Input is cleared on submission, profile changes, Cancel, pause, state save and destruction. A keyed credential epoch also disposes field-internal undo state and rejects late callbacks from an older field. Clearing only the external value was insufficient according to the selected Foundation source (R284/R285). This is a lifecycle/undo mitigation, not complete JVM/IME memory erasure.

One process-owned engine and operation slot cover preparation, DNS, authentication and retirement. A second operation receives Busy instead of retaining another queued password. The original fifteen-second deadline, one numeric address, same coordinator cancellation signal and remaining handle authority flow into the engine. Runtime disposal and slot release precede the main-thread completion callback. Permission requests end the attempt and clear input; a grant never replays authentication. The operation retains only non-secret pending uncertainty through recreation.

## Real Googlebook evidence

Target: **HP Googlebook 14, Android17/API37, arm64-v8a, build CL3B.260622.276.R2, incremental16560795**. Each test has a fresh parent-owned **Apache MINA SSHD2.20.0** loopback fixture, using the pinned [fixture source and dependency closure](../../fixtures/ssh-password/README.md). USB ADB reverse connects the owning product to that fixture. Credentials are randomly generated fixture values supplied through stdin into a UID-owned0600 CE/no-backup file; no personal password, vault grant or screen-lock prompt is involved. Host accounts, PAM and system SSH configuration are unchanged.

| Case, per owning app | Actual observation | Build evidence |
| --- | --- | --- |
| Success | One ordinary password request, actual host authentication, successful UI result, closed peer and empty input. Framework Ctrl+Z/Ctrl+Shift+Z recover neither cleared field. | build02 device03 and build03 device04 |
| Denial | One request, actual server rejection, generic unconfirmed result, no retry/fallback/channel, empty input and closed peer. | build02 device03, carried forward |
| Password-change refusal | One initial non-change request and actual change-required challenge; typed refusal, zero change-handler/retry/fallback/channel, empty input and closed peer. | build02 device03, carried forward |
| Cancel while waiting | Parent first observes the actual password request and atomically acknowledges the exact run/case. Cancel retires work; explicit Refresh sends no new request. Undo/redo cannot recover cleared input. | build02 device03 and build03 device04 |
| Recreation | Unsubmitted draft recreation restores neither selection nor credentials. After fresh explicit selection/entry and a host-confirmed request, recreation retires the attempt; explicit Refresh sends no new request. Two actual framework saves contain no tested username/password byte patterns. | build02 device03 and build03 device04 |

Device03 passes **10 actual tests plus10 default opt-in skips**. Device04 passes **6 affected actual tests plus10 default skips**. Each skip occurs before fixture reads, store operations or Activity launch. Each actual case verifies the secure/autofill flags, uses test-only checks of the real worker/main queues and actual engine ownership/retirement state, and proves exact profile/trust restoration. Server logs independently show one password request and one closed session before parent shutdown; success alone has an authenticated event. No channel, password-change submission, alternate method or second request is observed.

The only build02→03 production change is the primitive `mutableLongStateOf` counter plus import, resolving an informational Lint hint. The six input-retirement/recreation cases rerun on final bytes. Denial/refusal engine, coordinator, outcome mapping and tests are unchanged; their four build02 cases carry forward under the established-library/proportional-test policy. These are16 executions of10 distinct app/case combinations, not16 distinct scenarios.

All selected profile/trust rows and catalog generations, trust metadata, vault-receipt hashes, effective display preferences, font scale, density, night mode, boot ID and boot count remain unchanged. Exact installed APK hashes match final build03. Both products are stopped, test packages removed, all owned listeners closed, and the prior ADB reverse set restored. Exact provisioned config/ack/receipt files were collected/hashed and removed only after confirmed fixture restoration and host closure. Private evidence stays under `qualification/results/private/foundation/2026-10-16-ssh-auth-ui-03` and `...-04`; it is not published.

## Build, review and retained failures

Strict build03 passes Kotlin/Java compilation, dependency verification and **four zero-finding Android Lint reports**. Existing JVM reports remain50 domain,21 executed SSH plus11 fixture skips,2 platform and4 design cases, with unchanged tasks/reports reused where Gradle reports UP-TO-DATE. These are not newly executed hardware tests. Product APK SHA256:

- Console: `372fec1bb0490d395f6bf2924f38a8a09093ca9ab28ff827b6aa38a8fcd95193`
- Desktop: `1b5c9b04c1883298700f0192c5698630dd16e473b608f77b0a6fc56cfdc02a2c`

Independent direct Codex coworkers reviewed production source, test evidence semantics and the parent runner. The leader corrected completion-before-slot-release during integration, deferred test PASS until cleanup succeeds, used Android's public `JSONObject.keys()` API, made server acknowledgements atomic, and isolated runner cleanup errors so later settlement still executes. Review findings were checked against actual source; worker completion alone was not accepted as evidence.

Build01 retains a test-only compile failure from `JSONObject.keySet()`; build02 succeeds with zero errors/warnings and one state-autoboxing Hint repeated in three reports. Device01 retains an ADB argument-quoting setup failure before authentication. Device02 retains a setup timeout because `exec-out` reads output rather than forwarding stdin; its empty owned partial file was separately verified and removed. Official ADB source and a fixed non-secret stdin round-trip informed the corrected non-PTY `shell -T` path. Both failed setup attempts preserve data/settings/boot and stop their owned fixtures. They are not failed SSH authentication observations and are not erased by later passes.

## Remaining boundaries

These are framework-injected UI/keyboard cases on an actual device, not physical typing/IME, native visual/accessibility or screenshot/overlay qualification. Loopback does not test hostname resolution, LAN consent or general network interoperability. Two-window Busy and stale-before-admission UI races remain unrun; the preceding real coordinator/storage tests cover their separately named lower-level boundaries. Activity recreation does not qualify process death or power loss.

Saved-vault binding and positive human device authentication remain separate, with the existing readiness question pending and no prompt replay. Public-key/interactive methods, reusable-session rekey/revocation, jumps, terminal/SFTP/drop, X11, physical input and full independent releases remain required. The [entry contract](../../../docs/03-platform/googlebook/product-ssh-authentication-entry.md), API61 and current execution state retain these limits for subsequent workers.

Final repository gates pass32 Python AST/syntax files, full Ruff,123 repository tests, nine terminal-bridge cases, JavaScript syntax and project/schema validation. New source/API records remain partial and do not alter stage prerequisites.
