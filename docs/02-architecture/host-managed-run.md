# Retained Linux managed-run owner

**Accepted construction boundary:** parent and independent source03/composite evidence review accept the bounded internal library and affected pure checks. Combined evidence covers seven distinct pure methods across two runs; the first run remains failed. The subsequent [one-method native composition](../../qualification/results/P4-S1/2026-10-16-host-managed-run-native.md) now passes actual retained-owner/recorder/store behavior for one controlled Linux child; this is separate from those pure checks and does not supply a service lifetime.

HOST-RETAINED-RUN01 adds one in-process owner in `host/core/internal/managedrun`. It composes the accepted Linux jobs, recorder and local event-store APIs while keeping a real returned Job reachable through uncertainty. It adds no CLI, service, registry, listener, remote admission or crash recovery. Host executable version remains 0.1.3 and P4 stage prerequisites remain unchanged.

## Admission and borrowed lifetime

`Start(lifetime, delegation, store, spec)` accepts a caller-provided retained delegation descriptor and an already initialized/open real Store. The caller keeps final Store/delegation Close responsibility and excludes concurrent Store mutation or Close for this Run's lifetime. The Store's installation binding comes from the caller's explicit reader/Init/Open flow; managedrun never creates, opens, repairs or closes storage.

Before executing, Start checks the lifetime context, reads the actual typed Linux boot identity and generates three distinct cryptographic UUIDv4 correlation IDs: job, Wait event and settled event. It requires the empty-cursor read to produce the Store's full stream-reset snapshot before making a capacity decision, rejects a full 64-job catalog and checks all three identities against retained job/event identities. It checks lifetime cancellation again before the single real jobs.Start call. This is bounded single-writer local preflight, not a durable execution reservation, authentication or exactly-once remote admission. A later storage capacity/commit failure remains possible.

The selected jobs.Spec still requires an absolute executable/cwd, direct argv, explicit nonnil exact environment and bounded vectors/input/runtime. It does not turn executable validation into confinement or a sandbox. Every nonnil Job returned with a Start error is retained in a returned Run; nil Job cannot become a successful owner. The caller must keep Run reachable while retirement is uncertain. No finalizer, automatic rerun or process reconstruction is added.

## Observation, cancellation and recording

One mutable observation, append or settlement operation runs at a time; another receives a fixed busy error. The state mutex is released before recorder/store calls. Cancel bypasses the busy gate and calls actual Job.Cancel without waiting for a blocked observation or persistence operation. Nil observer context is rejected before it can consume the one-shot Wait.

WaitAndRecord captures through public CaptureWait at most once and caches the immutable observation before append. A valid observation-ended result can coexist with a recorded pending status; observation ending never cancels the job or establishes retirement. Repeated Wait calls return the cached observation state and the latest explicit append receipt; an exact retry may update AlreadyObserved/durability without changing the observation. RetryWaitAppend explicitly reuses the exact cached observation without another Wait, timestamp, ID or job execution.

SettleAndRecord uses actual CaptureSettle. Failed settlement retains the Run/Job and is not appended as terminal. A later explicit settlement attempt can observe again because the failed terminal attempt was never persisted. The first successful settlement capture is cached with revision 2; actual successful settlement marks retirement independently of the following append. Repeated settled capture returns the cache, and RetrySettleAppend retries only its exact values.

Cached Wait handling precedes the terminal guard. If successful settlement occurred before any Wait capture, a new Wait receives fixed settled-before-wait before revision 1 can be captured or appended. An existing cached Wait remains readable after settlement. Explicit retry of an older cached observation remains subject to the Store's stale-revision rules; the owner does not rewrite its timestamp/ID or invent a newer receipt.

Retired reflects successful real settlement only. It is not inferred from process outcome, Wait return, observer timeout or persistence receipt. The Store is borrowed and must not be closed beneath a live uncertain Run. This library supplies no standalone process/service lifetime; a future CLI needs an independent owner and recovery design.

## Persistence uncertainty and privacy

Capture, recording and retirement are separate result fields. A failed append returns no receipt. Actual recorder persistence effect flags are copied and remain sticky even after a later explicit successful retry. A lookup or retry does not erase commit uncertainty. No automatic store reopen, retry, reset or job replay occurs.

PendingObservations returns copied opaque immutable recorder values. After confirmed process retirement, the caller can retain them while explicitly closing/reopening the borrowed Store and performing separately reviewed persistence recovery. The Run's earlier uncertainty remains historical truth. The Store's finite retained-ID horizon, 256-event/64-job/16-MiB bounds, poisoning and durability semantics remain unchanged.

Exported managedrun metadata, observations, errors and persisted records copy no argv, cwd, environment, input, output bytes or raw failure pointers. The retained underlying Job still owns its bounded buffers and process/retirement state. Correlation IDs and status remain plaintext local metadata and are not authentication or an encrypted journal.

## Evidence boundary

Source review closed two preserved source01 findings in source02, with a later test-only receipt expectation correction in source03: revision regression from first Wait after settlement, and automatic native-fixture directory removal on uncertainty. The proposed native fixture now uses explicit private owned directories and a private path report. Failure or uncertainty preserves evidence; cleanup requires confirmed retirement, checked Store/delegation Close and passing assertions. KeepAlive extends Go reachability only through function return; the finite outer supervisor owns recovery afterward.

The seven pure test methods use private starter/observer/append seams and real temporary database transactions. They do not launch managed jobs or establish public recorder/native integration. The commit-effect flag case is synthetic and does not inject an OS commit/close fault. The [dated result](../../qualification/results/P4-S1/2026-10-16-host-managed-run.md) preserves the failed first run and successful focused repair: six unchanged top-level methods and sixteen subtests plus one corrected method. This is not a fresh seven-method execution. The original failure was a stale receipt expectation: the cached observation stays unchanged while the latest explicit append receipt may reflect AlreadyObserved and not-reassessed durability. Native harness/child source is compiled with the package but excluded from selected test execution; it is not counted as a native skip or pass. Other platforms, service/CLI lifetime, durable admission, automatic recovery and new stage/capability claims remain outside this increment.
