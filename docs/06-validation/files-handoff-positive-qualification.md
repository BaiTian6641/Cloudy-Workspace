# Positive files handoff qualification

**2026-10-16; accepted test implementation plan, execution pending.** This is the next bounded P1-S3 case after the [protected transport result](../../qualification/results/P1-S3/2026-10-16-files-handoff-transport.md). It measures the existing [product handoff](../02-architecture/protected-files-handoff.md), independently in both directions, without treating a service acknowledgement as a visible successful launch. The parent owns ADB, credentials, host fixtures, operation correlation and acceptance. Direct Codex GPT-6.1 Sol owns the bounded test-source packet. No new production capability follows from this plan.

## Actual operation and instrumentation boundary

Use one fresh isolated SSH/SFTP host and one direction at a time. A separately completed receiver preparation test creates its uniquely owned profile/pin, connects through the real native Files UI, lists `./nested`, and registers that actual listing. The test explicitly disconnects, proves retirement, and shares the exact registration through the manager's confirmation UI. Detached store-created resources cannot satisfy this preparation.

End receiver instrumentation before the measured operation. A standalone platform-test UiAutomation controller drives the ordinary visible sender through Refresh, selection and Open. Both products run normally. The controller must not adopt shell permissions, launch the receiver Activity, forge a ticket, or call a Stub directly. Background-activity-launch eligibility would otherwise be confounded. Native accessibility nodes must be unambiguous by package, window and visible label; a Compose test tag alone is not a cross-process selector.

Require the genuine receiver window, its fixed registered account, a fresh password authentication, the registered nondefault directory listing, and an actual read of `nested.txt`. The host journal must distinguish preparation authentication from this new authentication and match the real list/read paths. Credentials remain in private provisioning and never enter instrumentation arguments, logs, external-model packets or accessibility dumps. There is no automatic retry after uncertain Open delivery.

After Disconnect, establish actual host peer closure and sender binding/worker plus receiver engine retirement before stopping either product. A new process with empty registries is not evidence that the old operation retired. If current observable state cannot establish settlement, review the smallest additional observer before proceeding. Both directions need separate successful evidence.

## Immutable preparation and exact cleanup authority

Existing fixtures keep their original baselines in memory and refuse existing receipt families. Reopening them after preparation would either fail those guards or silently treat the newly created rows as baseline. Do not bypass the guards. Platform catalog constructors are internal; use test-owned immutable snapshot data and public validated domain records instead of reflection or production visibility changes.

The accepted implementation packet owns the three existing test helpers `SshFilesFixture`, `FilesLocationsFixture`, and `SshObserveTrustFixture`, a new strict `FilesHandoffReceiverCheckpoint` codec, and an explicit phase method in `SshFilesActivityContract`. Owning-app wrappers may change only if needed to select the inherited method. Default settled tests retain their current restoration behavior. Production storage, credentials, replay policy, dependencies and schemas are outside this packet.

The v2 checkpoint binds run UUID, direction, receiver owner/package/UID, installed version/code, fixed case, preparation time, original generations/metadata/full rows, and the exact owned profile/pin/shared resource. Validate canonical domain values, unique IDs, typed integers, exact key sets and capacity bounds. The owned resource is SHARED revision2 at `./nested`, bound to the exact profile/pin and unique run label. Reject duplicate keys, invalid UTF-8, trailing input, coercions, unsupported schema, excess depth/size, foreign rows or mismatched generations.

Use a fixed run-derived file under the app's private no-backup qualification directory. Require private permissions, a regular no-symlink file, exclusive creation and an 8MiB bound; refuse existing file/backup/new families. Never overwrite a partial checkpoint. Parent archives the completed bytes and supplies its SHA256 for later phases. This hash correlates test ownership; it is not a product authentication credential. Existing mutable v1 journals are not automatically resumable.

| Phase | Required behavior |
| --- | --- |
| PREPARE | Complete the real connection/list/register/share operation and retirement, then export original baselines and exact owned rows. A failed or uncertain preparation retains rows and receipts; it never exports success or runs broad cleanup. |
| VERIFY | After the parent independently proves measured-operation retirement, import the hash-bound checkpoint and require all three stores equal original plus owned rows. No authentication, UI launch, fixture mutation or ledger reservation. |
| CLEANUP | Settle the current runtime before taking coordination locks. Validate every store, marker, schema, metadata and full row set before the first deletion. Remove exact resource, then pin, then profile with the established locks and exact predicates. Confirm the complete original baseline and retain receipts. |

Coordination spans validation and removal; do not hold locks while waiting for main/UI callbacks. Cross-database cleanup is not globally atomic. Preserve planned/confirmed step receipts and any partial failure. Ordinary cleanup refuses unexpected or partially cleaned state; a separately reviewed recovery packet is required. No prefix deletion, current-state baseline substitution, direct SQLite reset or guessed already-clean success is allowed.

## Intended replay change and acceptance

A genuine admitted Open can initialize the receiver replay ledger, add the request/expiry, prune expired rows and advance its high-water mark. Even a failed visible launch can leave admitted state. Record those intended deltas against the actual interval; retain them permanently. Do not delete, reset or rewind replay state to make a preservation check pass. Sender discovery alone must not reserve.

Compile touched Kotlin/test targets with strict dependency verification and Android Lint. Test the new codec/phase/cleanup boundaries, including wrong identity/hash/generation, malformed data, foreign rows, failed export, zero deletions on preflight rejection and preservation after partial cleanup. Keep source/build and actual device evidence separate. Do not repeat unchanged SSH or upstream engine tests without a concrete changed boundary.

Stop at ambiguous launch, privilege confounding, timeout, uncorrelated host attempt, unknown row ownership or unproven retirement. Preserve the first result and owned resources for review. No positive Open/BAL, saved-vault, physical-input, complete P1-S3 or release pass is established yet. Current implementation status belongs in the [execution record](../05-delivery/execution-state.md).
