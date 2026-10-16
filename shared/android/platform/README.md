# Android window platform

**State:** implemented P1-S1 source, Gradle `:platform`; strict compilation and bounded device tests now pass; full stage acceptance remains parent-owned and open. This shared library depends on `:domain`, `:design-system` and `:identity` and is instantiated independently inside each app process.

`CloudyWindowActivity` hosts the native Compose workspace, validates/restores owner-bound local window metadata through `WindowBundles`, commits name changes and updates Activity/task labels. It requests a fresh document task for New Window, copies no local state into launch extras, ignores externally supplied Intent state, and closes only its own window. Framework saved state and the editor's saveable draft are distinct from durable storage or live-operation ownership.

The shared XML extraction resource excludes all nine supported domains from both cloud backup and device transfer. Both apps also disable backup. This is configured policy, not an actual migration-exclusion test or a qualified product vault. System captions, safe drawing and IME insets remain part of the native window boundary.

No shared daemon, network, remote operation or general background-session implementation exists in this library. The local credential-management source below is implemented; URI grants, session integration and other operation-specific lifecycle adapters remain P1-S3 and feature work. Read the [module map](../../../docs/02-architecture/module-boundaries.md) and [current state](../../../docs/05-delivery/execution-state.md).

## Accepted bounded build/device checkpoint

The [native foundation report](../../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 with no dependency-regeneration flags, nine passing JVM tests (domain5/design4), and zero Lint findings in both apps. On HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, five real platform Bundle/Parcel tests and two Activity tests per app pass (nine connected tests total). Each product is independently installed/launched with the other absent. The first Console instrumentation failure exposed Espresso3.5 reflection incompatibility; the test-only3.7 pin corrects it, without changing the frozen product APK hashes.

This qualifies only the tested local-window metadata/editor/restoration boundary. P1-S1 is complete with actual Desktop New/Close and both package replacement/uninstall receipts; later product requirements remain open. Physical keyboard/native IME, process-death restoration, broader geometry/accessibility and remote functionality are not established by Activity recreation or Compose test stimulus.

## Handoff admission primitives

`HandoffJson`/`HandoffEnvelope` validate the bounded resource envelope. `HandoffCaller.verifyIncoming` captures actual Binder identity and checks the expected exact counterpart; only `VERIFIED` is a positive caller result. `HandoffReplayLedger.reserve` durably consumes a UUID off-main before any effect, with nonblocking contention, capacity, expiry, clock-rollback and lost/corrupt-store denial. It performs no resource lookup or navigation. Read the [admission contract](../../../docs/02-architecture/handoff-admission.md) before endpoint integration.

The [actual result](../../../qualification/results/P1-S3/2026-10-16-handoff-admission.md) passes19 caller/storage cases and two separate-process phases. Caller acceptance from another app remains untested; no endpoint is exposed. Only the standalone test APK is installed/removed; new product APKs remain built-only. Generic local window and vault metadata are not an enrolled-resource registry.

## Local credential-management candidate

`CloudyVaultActivity` is a real shared FragmentActivity/Compose implementation, instantiated within each owning APK. It provides explicit Save on this device, Check saved credential, confirmed local deletion, Refresh, Cancel authorization and Finish local cleanup. Check performs actual scoped vault decryption/decoding without displaying secrets or pretending to connect. A selected profile/binding is immutable before authorization; deletion identifies the opaque local record, with endpoint metadata explicitly described as context rather than proof.

The Activity excludes autofill, uses a secure window and keeps credential fields out of saved state. Stop clears credential fields/pending mutable payloads and revokes prompt authority. One process-wide worker/admission gate performs storage and crypto off-main; generation guards reject stale UI completion. Oversized field edits are rejected whole. Refresh only reads metadata/state and never authenticates or reconnects. Explicit Remove unused entry calls `verifyAbsent` under the same serialized UI ownership before removing profile-only metadata; it does not delete a key or repair a corrupt journal. Cleanup-pending records remain visible rather than being reported deleted prematurely.

The [vault review](../../../docs/06-validation/2026-10-16-product-vault-review.md) and [actual storage tests](../../../qualification/results/P1-S3/2026-10-16-vault-storage-tests.md) record strict compilation, zero app/identity Lint, 14 identity JVM cases and 18 connected Android cases. The connected storage/fault cases use test-only software crypto; profile cases exercise actual AtomicFile. Product candidate 0.1.2/code3 is built, while installed 0.1.1 remains unchanged at this checkpoint. Actual product prompt/AES backing and real enrollment/use/deletion are unqualified. These checks do not complete P1-S3, known-host trust, signing, IPC, remote-session integration or product capability acceptance.
