# Local read-only host event diagnostic — 16 October 2026

Host **0.1.5** adds a real, explicit read of existing local job-event history on Linux and Darwin. Source 03 and its affected local checks have independent review acceptance. The separate [actual Mac packet](2026-10-16-host-events-read-macos.md) now has its own accepted native results, archive and exact cleanup. This source/local-check result does not qualify a paired service or complete P4.

## Command and operation

```text
cloudy-host events read --state-dir <existing-trusted-directory> --after <reset|stream-uuid:sequence> --limit <1..64>
```

The command accepts exactly eight arguments in that order. It validates all syntax before platform, state, clock or runtime access. The cursor is either `reset` or a canonical lowercase UUIDv4 followed by a colon and an ASCII decimal sequence from zero through 9,007,199,254,740,991. Limits are canonical decimal values from one through 64. Leading zeros, signs, whitespace and exponents are rejected.

The real production route reads the established installation identity, opens the existing journal through `jobevents.OpenReadOnly`, calls `ReadAfter`, and checks Close before sampling observation time or writing a successful result. The store retains a bbolt read-only engine throughout its lifetime. Its rooted file callback uses read-only, no-create access and preserves the existing no-follow, file-size and identity checks. It never performs the writable reopen/freelist conversion used by ordinary `Open`. Existing `Init` and writable `Open` retain their original paths.

Missing, incomplete, corrupt or installation-mismatched state is refused without initialization or repair. Append on a read-only owner refuses before mutation without poisoning valid reads. Every nonnil partial owner remains caller-owned until checked Close. Installation, open, read, close and output errors use fixed safe text rather than raw path-bearing causes.

The bounded local JSON record reports `localEventsVersion: 1`, executable version, observation time/runtime, `storageAccess: read-only`, and a batch with events, next cursor, reset/gap state and latest-record snapshot. A reset returns retained state; it does not fabricate an event. Historical boot IDs remain the originally persisted values. No cursor is saved or acknowledged, and no automatic paging/watch/repair occurs. Output is limited to one MiB before writing; writer failure may leave partial stdout, so consumers require both exit 0 and complete JSON. Syntax returns 2; source, unsupported-platform and output failures return 1.

Windows parses syntax and then reports unsupported before state/time access. This is an honest diagnostic boundary, not a Windows storage backend. The command assumes a trusted application-owned parent directory; database-byte preservation does not promise unchanged access-time metadata or protection from hostile same-user mutation.

## Source and actual local checks

The first source attempt passed compiler identity/formatting but failed vet: import text had been inserted into the Runtime field's struct tag. That failed receipt is retained. Source 02 repaired only that tag and formatting, then passed twelve actual affected commands: formatting, Linux vet, two selected Linux race runs, Darwin vet/two test cross-compiles, Windows vet/test cross-compile, and Linux/Darwin/Windows product builds. The pinned Go 1.27.2 identity from the first attempt remains applicable; it was not needlessly rerun.

Source 02's job-event run passes **six top-level methods / 18 aggregate results / 12 subcases**; its diagnostic run passes **nine methods / 59 aggregate results / 50 subcases**. Source 03 then strengthens only `TestEventsActualReadAndStatePreservation`: an independently specified full expected Event, including actual installation/boot identity and known payload, must equal both the CLI reset snapshot and resumed event. Production is unchanged. Its formatting, vet and that single race method pass separately, with no skips or failures.

Current applicable evidence is therefore **15 distinct top-level methods, 77 aggregate results and 62 subcases**, combining source 02's unchanged cases with source 03's updated method. This is **not a fresh 15-method run** and the updated method is not counted twice. All bound inputs remain unchanged within each run. Real temporary database/readonly/refusal operations execute on Linux; persisted job statuses and controlled error seams remain synthetic. No managed job was launched by these cases.

Six source 02 compiled artifacts retain verified hashes and sizes in the JSON companion. Their product-source attribution remains valid because production did not change. The source 02 Darwin diagnostic test binary lacks source 03's new assertion; the separate [actual Mac packet](2026-10-16-host-events-read-macos.md) therefore binds a fresh source 03 test compile. No Darwin/Windows execution or byte-reproducibility claim follows from cross-compilation itself.

## Limits and related evidence

This is persisted-history inspection, not live process truth, authenticated identity, remote admission or authority to execute/resume jobs. Test seams do not qualify physical disk faults; no reboot or power-loss guarantee is added. No service, listener, pairing, consumer acknowledgement, wire/schema change or formal capability/release promotion is included.

The earlier [Linux store](2026-10-16-host-job-event-store.md), [Darwin store](2026-10-16-host-macos-events.md), [Linux context](2026-10-16-host-context.md) and [Darwin context](2026-10-16-host-macos-context.md) retain their original source and runtime scopes. The [P4 phase](../../../docs/05-delivery/phases/p4-host-integration.md) retains all integration prerequisites and exit requirements. The linked native Mac result is separate evidence, not a reinterpretation of cross-compilation.

## Evidence bindings

Ignored local evidence is identified literally, with hashes, rather than through unavailable checkout links:

- `sourceFreeze`: `.cache/host-next-composition-01/implementation-01/freeze-03.json` — SHA-256 `b26c4248947b3a7ab802df661fd6e1e7223e65427447bfe3b928e06898526201`.
- `originalPatch`: `.cache/host-next-composition-01/implementation-01/source-01.patch` — SHA-256 `18c0634f8fd0ccbbb829264fb932db2ea0816dfcfda7c2002ca0c858b7cebc3d`.
- `tagRepair`: `.cache/host-next-composition-01/implementation-01/repair-02.patch` — SHA-256 `c32adce95e78289bcb7b1bde183316408269e5f374d4239c6a0697324057018b`.
- `payloadAssertionRepair`: `.cache/host-next-composition-01/implementation-01/repair-03.patch` — SHA-256 `d2277e798f8c0241952bd5c9783de67ab86bbec07fb8d0443140f24476d59e62`.
- `preservedFailedSource 01`: `.cache/host-next-composition-01/implementation-01/verification-01/result.json` — SHA-256 `77e52c74cd0dad35310c8536fe82555772deeda177966b5da39543d9f1eab719`.
- `source02Checks`: `.cache/host-next-composition-01/implementation-01/verification-02/result.json` — SHA-256 `4032425778270e4f221af50c0e46130e37d7c4906df5bbe2f0c89cf749df3e5e`.
- `source03Checks`: `.cache/host-next-composition-01/implementation-01/verification-03/result.json` — SHA-256 `bae47bcd68f9d5068354b8580f6e3cd06d1edc6d31b8fa80d0ae3a90c93fa70a`.
- `handoff`: `.cache/host-next-composition-01/implementation-01/handoff-03.json` — SHA-256 `3edb7896924b242377d5b432791967a368ae687d7c7970877ce7682d8dbb0c94`.
- `independentReview`: `.cache/host-next-composition-01/implementation-01/independent-source-review-03/review.json` — SHA-256 `8058ea4258afbc56d75b0278576518a58ec2d160be305aa63e4300b0a00faa51`.

See the [machine-readable result](2026-10-16-host-events-read.json) for exact source/protected-input bindings, command results and artifact identities. No machine inventory, connection detail or private transcript is included.
