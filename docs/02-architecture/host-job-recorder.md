# Linux job observation recorder

**HOST-JOBRECORDER01:** parent and independent review accept source02 as bounded internal Linux construction. `host/core/internal/jobrecorder` now connects the real jobs observation API to the [local event store](host-job-event-store.md). It adds no CLI, service, listener, remote admission, automatic collector or wire contract. Host executable version remains 0.1.3; no stage or capability is promoted.

## Capture and ownership

`CaptureWait(ctx, job, metadata)` calls the borrowed real `*jobs.Job.Wait` exactly once. `CaptureSettle` calls the real `Job.Settle` exactly once only on explicit invocation. Settle may continue owned cleanup after a committed stop; it is not purely passive. Ending the observer context does not call Cancel or start another job. The caller retains the Job until settlement and owns all lifetime/recovery decisions.

Metadata supplies canonical event/job UUIDs, a revision in 1 through 2^53−1 and an existing typed boot ID. The recorder never generates these identifiers, increments revisions, reads a replacement boot ID or creates an installation. It validates inputs before observing. A successful capture copies caller metadata and whitelisted status into an opaque immutable value and captures actual UTC time exactly once after the observation returns. The timestamp is an observation time, not a process transition time or an atomic system snapshot.

The retained status includes outcome, start resolution, started/reaped flags, retirement, I/O completion and exit/signal only when reaped. Allowed values and representation checks follow the accepted store profile. It retains no Job/Store owner, stdout/stderr buffers, raw failure pointers, arguments, environment, working directory or credentials. Plaintext event/job/boot metadata remains correlatable within the store; this is not authentication or encrypted storage.

## Append and failure semantics

`Observation.AppendTo(store)` borrows an existing real `*jobevents.Store` and calls AppendJobStatus. Repeated calls reuse exactly the same captured identity, revision, timestamp and status. They do not call Wait/Settle again, reread time, create another job or retry automatically. The store's exact-retry, finite retained/latest identity horizon, quota and commit-unknown rules remain unchanged. A fresh observation requires deliberate caller metadata and another explicit capture.

Capture and append return separate fixed-safe errors. Invalid input/snapshot does not yield a usable observation. A real observation error can accompany a usable pending/uncertain observation; appending it records that status, not successful completion. The error's capture stage and completion uncertainty are distinct from persistence uncertainty. Every failed append returns a zero receipt and a fixed persistence classification. Only actual Store Claimed/CompletionUnknown flags are copied; raw source errors and code strings are not retained or unwrapped. Store mutation poisoning remains in effect after uncertain commit. A successful later read or exact retry does not erase the original uncertainty or reassess durability.

The recorder never initializes, opens or closes storage. The independently borrowed Store still requires explicit caller Close. Retaining an Observation does not retain or retire the native Job; no serialized status authorizes execution or process replay. Nil context/job/store and a zero Observation fail safely. Private per-call observer/clock injection supports tests without mutable production-global seams; public calls bind the actual Job methods and time.Now.

## Evidence and integration boundary

The [result and manifest](../../qualification/results/P4-S1/2026-10-16-host-job-recorder.md) bind five actual checks and five named race-test methods, with zero skips/failures. Test-only observers supply synthetic status while real temporary bbolt databases perform append, exact retry, close/reopen and cursor reads. Actual boot reading supplies typed test context. Error-flag classification is a pure seam check, not an injected OS commit/close fault.

Positive public CaptureWait/CaptureSettle execution with an actual managed Job is still unrun and reserved for a parent-owned finite native fixture. Existing jobs and event-store evidence remains separately scoped; it is not a new end-to-end recorder qualification. Windows/Darwin recorder backends, durable observer ownership, live event collection, consumer acknowledgements, authenticated transport and host service integration remain open. No new dependency was selected.
