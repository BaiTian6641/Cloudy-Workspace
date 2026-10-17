# Consumer checkpoint storage — 16 October 2026

The internal Linux/Darwin `eventcheckpoint` package has accepted source and local affected-check evidence. This is a real persisted checkpoint primitive, not a new CLI command, version, service or remote acknowledgement path. Parent source and public-evidence review accepts this bounded construction checkpoint.

`Init` exclusively claims a permanent child in an existing trusted directory. `Open` binds canonical installation and consumer UUIDs to one consumer-private database; different consumers need separate caller-selected directories. The record contains only version, namespace, generation and the explicit processed-event cursor. `Read` returns copied state without acknowledging anything. `Advance` performs generation compare-and-swap in the actual bbolt write transaction. First acknowledgement and stream changes require an explicit reset acknowledgement; same-stream regression always refuses, equal explicit cursors increment generation, and overflow refuses.

The caller must still authorize acknowledgement and establish that the supplied cursor was processed. This package neither reads the journal nor proves cursor retention or eligibility. It supplies no automatic replay, retry or consumer recovery policy.

Existing state is never initialized or repaired by Open. It first validates through a read-only engine, closes it, then acquires a writable engine and revalidates. **No creation is not a universal no-physical-writes guarantee:** writable bbolt Open can update freelist metadata before second validation. Rooted no-follow/nonblocking regular-file opens and identity comparisons bound the database to 1 MiB; canonical metadata is limited to 1024 bytes. The trusted app-owned directory assumption remains explicit: os.Root confines relative access but does not establish ACL/privacy or defend against hostile same-user mutation.

Operational commit errors conservatively poison further mutation and report completion unknown. Read may expose currently visible state while retaining uncertainty; Close attempts owned resources once and preserves close failure. Non-nil partial owners require checked Close. Unknown commit/close hooks are test-only injections after real operations, not power-loss evidence. Explicit reopen observes state but cannot prove whether an earlier uncertain call completed.

Source01 independent review found that a failed Read omitted the prior unknown flag. Source02 adds that flag to the safe failure and a targeted regression: after real commit plus injected uncertainty, a real transaction corrupts metadata, actual View/load fails, and both returned uncertainty and mutation poison remain. The finding and initial evidence remain preserved.

| Actual evidence | Scope |
| --- | --- |
| Source01 Linux race run | Eight top-level methods plus 24 named subcases: 32 aggregate passes |
| Source02 affected Linux race run | One top-level method plus three subcases: four aggregate passes |
| Distinct composite coverage | Eight methods plus 25 subcases: 33 aggregate passes; not a fresh complete source02 run |
| Formatting and vet | Recorded affected checks pass on exact source; Linux/Darwin vet |
| Darwin test binary | Cross-compiled only; no actual Mac execution |

The checks use pinned Go 1.27.2 and unchanged bbolt 1.5.0 / x/sys 0.49.0 with readonly/offline dependency policy. Tests use real temporary databases for persistence, namespace/refusal, CAS, reset/regression, file guards and reopen, plus explicitly synthetic uncertainty seams. Earlier [journal storage](2026-10-16-host-job-event-store.md) and [read-only event diagnostic](2026-10-16-host-events-read.md) remain separate primitives; no existing journal/jobs/CLI source is changed.

The [JSON companion](2026-10-16-host-consumer-checkpoint.json) binds exact source, task, both verification runs, preserved finding and closure review. Principal ignored evidence references are literal paths:

- `source02Freeze`: `.cache/host-consumer-checkpoint-01/freeze-02.json` — SHA-256 `c7de9f2158c3d5985af5b8d602450a1f0b843c9939801b2bea9f7134f5fcfea6`.
- `verification01`: `.cache/host-consumer-checkpoint-01/verification-01/result.json` — SHA-256 `ef9da5389a3ce7b14fc9bde271f7f2fd8b48141a9a8c48ff16fb1184d89e91ac`.
- `verification02`: `.cache/host-consumer-checkpoint-01/verification-02/result.json` — SHA-256 `37e21e0c0f2cd52a46bfb8e6fc63e0b2b288b381f7108d2d73ff3015b5131d6a`.
- `review01`: `.cache/host-consumer-checkpoint-01/independent-review-01/review.json` — SHA-256 `af6f7664fe1eed712c5c52fc53e25404af72d9ff42634dcf4161ad1b9f38c5cd`.
- `acceptedReview02`: `.cache/host-consumer-checkpoint-01/independent-review-02/review.json` — SHA-256 `d0c5db25483ff58eeb63cd3f63d9d09cf6f52b2923ffe9163c166e50ba7ad08e`.

No Windows implementation, actual native Mac pass, paired service, authentication, remote acknowledgement, host integration or [P4 stage](../../../docs/05-delivery/phases/p4-host-integration.md) promotion is claimed.
