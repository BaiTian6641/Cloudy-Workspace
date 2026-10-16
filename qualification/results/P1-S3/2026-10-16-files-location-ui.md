# Native file-location registration and destination qualification

**2026-10-16, P1-S3 construction. Outcome: scoped own-app native registration, manager and account-bound destination PASS.** Console/Desktop **0.1.11/code12** are installed and stopped. Read the [contract](../../../docs/02-architecture/files-resource-registration.md), [sanitized result](2026-10-16-files-location-ui.json) and [frozen build binding](2026-10-16-files-location-ui-build.json). Protected counterpart discovery/submit remains unimplemented; no stage, capability or release gate is promoted.

## Actual implementation

A successful authenticated SFTP listing can offer a bounded registration candidate. The model binds its exact account, profile/trust, directory, authenticated proof, current store generation, owner/focus/listing epochs and elapsed lifetime. Confirmation consumes that candidate once and rechecks the current authority before real persistence. Cancel, departure and a new listing invalidate the prior proposal. A late listing cannot establish authority for a replacement owner. Registration uses an inline native confirmation so opening a separate dialog does not itself consume window focus. Unknown write outcomes stay visible and do not automatically retry.

The native manager loads the actual owning-app catalog and performs explicit inspect, share and revoke operations against the original generation/revision. Its text states that cross-app discovery is not available yet. Share currently changes the real publication state; it does not send a message or credential to another application. Off-main workers retain ownership until completion. A changed/hidden owner cannot admit a stale confirmation or automatically repeat an uncertain write.

Open issues a bounded in-process one-use ticket to the nonexported files Activity. The receiving screen selects the actual registered account and starting directory and requires fresh native password entry before authentication. The account field is read-only while still supporting native text selection. Registration metadata contains no password. The directory is a navigation destination, not a sandbox or a stable remote object; server/account access rules remain authoritative.

The coordinator validates the full current registration before password admission and when establishing an authenticated owner. Exact resource share/revoke mutations retire affected registered owners; ordinary connections to the same peer remain independent. A consumed ticket or old Activity state cannot restore authentication authority. Cloudy’s custom saved-state record contains only schema, owner and pending/uncertain metadata, never the candidate, ticket, account or credential. This describes that custom record rather than the entire framework Bundle; no candidate or ticket authority is reconstructed from it. Current connection cleanup remains the existing actual SSH/SFTP retirement route.

## Build and review

Direct Codex GPT6.1-Sol coworkers owned disjoint authentication/store, model/ticket, manager and test work. The parent integrated screens and owned build/device/Git acceptance. Independent review corrected disappearing registration outcomes after Disconnect, read-only account selection handling and silent oversized-label rejection. A further test review corrected the observer to require the actual schema2/four-key saved state and protected AtomicFile receipt `.bak`/`.new` remnants. Both repairs are in final build02.

Build02 passes strict dependency verification, compilation/assembly and four Android Lint reports with zero findings. All 206 recorded source hashes and five APK hashes match. Its two product APKs are byte-identical to build01; the intervening changes are test-only. Frozen JVM reports contain 100 passes, 20 fixture-dependent skips and zero failures/errors. Build02's four JVM tasks are UP-TO-DATE: build01 reran SSH/platform/design tasks, while domain results carry forward. These are not100 newly executed build02 tests. The credential class includes four passing cases, including the new exact account observation/ownership case.

OMP FILES-LOCATION-COPY-001 completed on actual `zai/glm-5.3-flash` in 20.627seconds with zero tools. The parent accepted selected strings-only suggestions with edits, rejected a reference to a nonexistent string, and retained the packet/review. It supplied no security, layout or runtime evidence. No original user document, private inventory or test credential was sent.

The parent reviewed both finite runners. Final runners bind build02, preserve bounded internal error codes and validate all owned config/receipt hashes before deleting any file. They record partial removals if cleanup fails. The inherited unreachable held-read branch adds no new held-read coverage. Existing source/fixture/dependency evidence is reused rather than rerunning unrelated engine probes.

## Actual Googlebook evidence

Target: HP Googlebook14, Android17/API37, arm64-v8a, buildCL3B.260622.276.R2, incremental16560795. The host fixture is the existing pinned ApacheSSHD2.20.0 implementation with a fresh generated account/key and synthetic tree for each case. Actual SSH traffic travels through an owned USB ADB reverse route. No personal credential, system-account change, permission request or screen-lock prompt is involved; this does not establish LAN/hostname behavior.

First, the standalone platform package executes 47 unique Android cases in 2.567seconds: 21 coordinator, 22 resource-store and 4 registration/owner/ticket policy cases, with zero failures/skips. The 22 resource cleanup receipts are separate from JUnit completions. Two coordinator methods explicitly use reflected proofs for policy checks; none of this standalone run is real network-authentication evidence. Actual network/authenticated-owner evidence comes from the product cases below.

| Actual case, repeated in each app | Authentications / SFTP channels per app | Observed result |
| --- | ---: | --- |
| Register listed directory and preserve ordinary session | 2 / 5 | Actual root/nested listing, native registration, ordinary proof survives direct share/revoke and another read; a second registration is inspected, cancelled, shared and revoked through the native manager |
| Cancel and framework-focus invalidation | 1 / 3 | Cancel creates no row; explicit refreshed listing and a new proposal are invalidated by a framework focus callback without mutation; ordinary established proof remains live |
| Open registered directory and revoke its owner | 2 / 4 | Actual manager Open launches the private ticket destination; exact account requires fresh authentication, initial listing uses the nondefault directory, marker read succeeds, and revocation retires the real proof with no retained preview |

All six app/case combinations pass, with 18 default non-opt-in skips counted separately. Host records confirm 10 actual password authentications,24 SFTP channels and all matching peer closures **before** parent force-stop, fixture shutdown or reverse removal. Client and host-worker settlement are independent requirements. Physical pointer/focus behavior is not inferred from Compose actions or the injected focus callback.

Both receipt families report exact fixture baseline restoration. Test cleanup waits for owned windows, manager/model workers and actual engine retirement before removing only full-column matches for its resource rows, then its profile/trust records. The permanent location catalog was previously absent in both products and is now initialized and empty; anchors/generations remain. This is an intentional persisted initialization, not restoration of byte-for-byte absence. Existing profiles/trust, vault receipts, preferences, system settings and boot identity are preserved.

All six synthetic trees remain unchanged, host workers/listeners and owned reverse routes are closed, exact temporary config/receipt files are collected and removed, and all three test packages are absent. Both product APKs match build02 and both products are stopped. Raw private receipts remain in `qualification/results/private/foundation/2026-10-16-files-location-platform-device-01/` and `qualification/results/private/foundation/2026-10-16-files-location-ui-01/`; account/path details and credentials are not published.

## Remaining boundaries

Actual 30-second candidate expiry, uncertain registration writes across process recreation, physical keyboard/pointer/focus/IME/accessibility, separate-process resource persistence and power loss remain unqualified. This does not implement signed counterpart discovery/submit, caller/BAL behavior, a saved-vault credential release, durable background sessions, uploads/edit/drop/X11 or Desktop engines. Existing accepted native-files cases remain evidence for their exact earlier builds; this increment does not claim every earlier case was rerun on0.1.11. Full P0–P6 work and independent app/suite gates remain active.
