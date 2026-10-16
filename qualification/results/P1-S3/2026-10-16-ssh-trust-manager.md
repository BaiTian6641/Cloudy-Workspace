# Native SSH host-key management

**Date:** 2026-10-16. **Outcome:** Console and Desktop **0.1.5/code6** implement native inspection and confirmed revocation of their own saved SSH host keys. Strict build02, three zero-issue Android Lint reports and 17 actual Android cases pass. Both exact product APKs are installed and stopped. P1-S3 remains in progress; this screen does not enroll a key, connect to SSH, release credentials or disconnect an active session.

## Implemented behavior and review

The workspace opens a nonexported **SSH host trust** Activity through its **SSH host keys** action. It reads the actual owning-app ledger from the [accepted storage increment](2026-10-16-ssh-host-trust.md). Inspection displays canonical host, port, direct route, key format, SHA256 fingerprint, saved state and revision. Revoked rows retain their last key. Native text selection and an explicit fingerprint-only clipboard action are implemented; clipboard execution and physical selection are not qualified by this run.

Revocation confirmation freezes the exact displayed snapshot and its original owner/generation/peer/revision token. A competing replacement cannot silently become the target of the old confirmation. Cancel and Activity recreation discard the dialog. Pause/save/destroy clear its approval; no confirmation or selected snapshot is restored. A process-wide bounded single-thread queue performs real storage work off the main thread and orders recreated reads after admitted writes. Closing remains possible during work; an admitted revoke may finish, with no automatic retry or claimed rollback.

The custom saved-state record contains only schema, owner and a pending/uncertain boolean. The pending bit is set before queue admission. A recreated initial read preserves uncertainty; a successful explicit Refresh reconciles the current catalog without claiming which attempt caused its state. Framework/Compose list state is separate and may contain ordinary list-key metadata. An unavailable catalog never renders as a successfully empty one. No credentials, network effect or approval grant is supplied by this UI metadata.

Direct Codex GPT6.1-Sol workers authored the manager and tests in disjoint files. A separate reviewer and the parent inspected the changes. Parent prereview corrected a custom non-saveable LazyColumn key to an unambiguous primitive key, added deliberate scrolling to selected details, and moved Bundle presence inspection into the restore error boundary. A copy finding was also corrected: rejected-state text now asks for inspection rather than promising an explicit Refresh gate that automatic initial loading did not enforce.

OMP task `SSH-TRUST-UI-COPY-001` completed on actual `zai/glm-5.3-flash` in 17.62 seconds, stop reason `stop`, zero tools. The parent accepted two concise wording edits and adjusted the third to cover stale generation, missing records and changed state. Only resource strings were supplied. Packet/events/disposition remain in `.cache/omp-workers/2026-10-16-ssh-trust-ui/copy-01/SSH-TRUST-UI-COPY-001/`; this copy review proves no runtime behavior.

## Build evidence

Source base: `5c577697fe16ed5931761a7c77410d191e906ecc` plus this increment. The [build manifest](2026-10-16-ssh-trust-manager-build.json) binds 17 implementation/integration/test files and five APKs. Source hashes did not change during either build. Frozen reports/logs/APKs are under `.cache/ssh-trust-ui-01/build-02/`.

Build01 fails in the two app instrumentation compilations because the new test imported `assertDoesNotExist` as an extension; the selected API supplies it as a member. The parent removes that import and preserves the failed log, source hash and original file under `build-01/`. No device execution used it.

Build02 passes in 16 seconds: 305 tasks, 18 executed. Console, Desktop and platform Lint each report zero issues. The domain test task is up-to-date, carrying its 38 passing reports forward; there is no changed domain source and no new domain execution claim. No external dependency, token or permission change occurs. Both manifests register the app-private Activity, and both versions advance from 0.1.4/code5 to 0.1.5/code6.

## Actual Android cases and cleanup

Target: **HP Googlebook 14**, Android 17/API 37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Private packet: `qualification/results/private/foundation/2026-10-16-ssh-trust-ui-01/`; runner: `.cache/ssh-trust-ui-01/run_device_01.py`. Recorded interval: 09:54:20.728187Z–09:54:40.028172Z.

The standalone platform package passes three real Bundle/Parcel cases: owning-app uncertainty metadata roundtrip, missing versus wrong-owner/type state, and rejection of malformed versions/fields or injected approval. They do not execute storage or grant trust.

Each actual product passes four trust Activity cases:

1. Exact displayed identity/state, Cancel without mutation, and confirmed revoke with retained tombstone.
2. A competing real store replacement invalidates the original dialog; confirmation fails and the replacement key remains accepted.
3. Recreating an unconfirmed dialog discards confirmation and selection without mutating the pin.
4. A real queued revoke is admitted before recreation, commits once afterward, and leaves uncertainty until successful explicit Refresh. Subsequent Refresh/recreation does not increment the revision again.

Each case verifies its actual package/UID and nonexported Activity. Each product also passes three workspace cases: the two existing committed-window/draft regressions plus opening the manager from the workspace and returning to the same logical window. There are **17 successful JUnit cases: 3 + 4×2 + 3×2**, with no skips or failures. Custom fixture status events are not counted as additional cases.

Trust fixtures use unique `.invalid` peers and public digest values, seeded through the production store. Before mutations, test-only private receipts record the baseline digest, owner/package/UID/generation and independently expected transition. Cleanup closes the Activity, releases the test gate and proves worker/main settlement before locking the existing permanent anchor and opening SQLite. Inside a transaction it verifies schema, metadata and the entire baseline-plus-confirmed-fixture catalog, deletes only exact eight-field fixture rows, and checks restoration before commit. Production `load` then verifies the baseline again. Any unexpected state or uncertain settlement preserves the fixture and receipt. This narrow test-only disposal is not a product deletion/reset API.

All eight fixture cleanups report exact baseline restoration. Both new own-app trust ledgers remain initialized and empty; their permanent files are retained. Existing profile rows/generations and vault receipt hashes are unchanged. Installed APK bytes match build02. All three temporary test packages are successfully uninstalled and independently confirmed absent. Both products are stopped; boot ID/count are unchanged. No app data is cleared and no authentication prompt, remote host or network session starts.

## Remaining qualification

These cases establish real owning-product management and controlled Activity recreation. They do not prove cross-app trust-file isolation, process death, simultaneous independent-process writes, physical power loss, clipboard execution, physical keyboard/mouse behavior, both-theme/scaled/narrow visual quality or unavailable-store exit behavior. The source implements those UI error/exit paths, but a review is not their device pass. Test-only queue gates control scheduling; they do not simulate store outcomes.

Observed-key enrollment still needs a real SSH attempt, a foreground challenge controller and the ordered verification-to-authentication boundary. No arbitrary manual fingerprint, fabricated server key or test fixture may substitute for that integration. Jump routing, live rekey/revocation policy and engine adoption remain separate requirements. The full P0–P6 objective, P1-S2/P1-S3 work and release gates remain open.

All seven hosted workflows pass for predecessor `5c577697fe16ed5931761a7c77410d191e906ecc`; this new checkpoint requires its own exact-commit hosted result after push.


## Final integration checks

An independent direct Codex reviewer checked all17 source hashes, all five frozen APK hashes/sizes, the build log/Lint reports, actual3+7+7 JUnit completions, eight cleanup events and direct before/after preservation snapshots. No concrete discrepancy remained. Parent checks pass: authored Python AST32 files, Ruff, generated Android token freshness, project validation123 JSON/298 Markdown/2,293 local links/496 sources/55 APIs/82 declared contrast checks, and Git whitespace validation. Kotlin compilation and Android Lint are the language-appropriate checks from build02. These repository checks add no device, accessibility or rendered visual claim.
