# SSH host-trust domain and persistent ledger

**Date:** 2026-10-16. **Outcome:** the P1-S3 domain/storage increment passes strict compilation, three zero-issue Android Lint reports, 38 domain JVM cases and 20 actual Android storage cases. It implements durable exact-pin enrollment, replacement and retained revocation. Native trust presentation, SSH verification/authentication integration and installed-product trust isolation remain unqualified. P1-S3 and the full P0–P6 goal remain in progress.

## Implemented boundary

The [trust contract](../../../docs/02-architecture/ssh-host-trust.md) separates logical peer identity, public key identity, storage revision and proposed challenge metadata from credentials and connection authority. The immutable model uses the existing canonical host, explicit port and typed direct route. It accepts five reviewed public-key format identifiers and exactly 32-byte canonical unpadded SHA256 fingerprints. `ssh-rsa` names a key encoding; it grants no permission to negotiate SHA1 signatures. Unsupported formats cannot become an ordinary first-use challenge.

The real app-private SQLite ledger stores one accepted or revoked row per peer. An observed different key type is Changed, not Unknown. Revoked rows retain their last key and count toward the 512-row bound. First-use acceptance compares absence; replacement and reenrollment compare owner, store generation, peer, original revision, prior state and old key. Updates repeat the complete state/key predicate in SQL. Concurrent writes cannot silently overwrite the original decision, revisions cannot wrap, and there is no delete, prune, reset or upsert API.

Storage reuses the established credential-encrypted no-backup transaction/locking pattern without adding a dependency or generic database abstraction. The separate `ssh-host-trust-v1` ledger uses permanent lock/instance anchors, a 4 MiB main database ceiling, DELETE journaling and EXTRA synchronization. It validates original context, runtime package/UID and persisted owner/UID, rejects main-thread work, and preserves missing/corrupt/newer-schema state rather than silently recreating trust. Success follows transaction completion. Exceptions after an attempted write return completion unknown and require explicit reload/reconciliation.

A challenge is immutable proposal metadata, not a foreground approval or authentication grant. Saving a pin does not open or resume a connection. Controller ownership/freshness/cancellation and ordered verification-before-credential-release are still required. Full jump routing, live rekey/revocation policy and maintained-engine integration retain their separate gates.

## Review and build

Base commit: `82a5118887048c4644ce76e1f9d31cbd02b433cf`, plus this increment. Two direct Codex GPT6.1-Sol workers owned the disjoint domain and storage files; another reviewed the frozen source/tests. The parent inspected all four files and owns build, device, documentation and Git acceptance. The independent review found no actionable source defect.

The authorized OMP task `SSH-TRUST-DOMAIN-001` completed in 70.08 seconds on actual `zai/glm-5.3`, with stop reason `stop` and zero tools. Its packet contained only the domain source and architecture contract. The parent rejected both proposed findings: owner mismatch already precedes generation mismatch, and the structural challenge check is reachable for inconsistent internal proposals, as the existing JVM test demonstrates. Generation-before-peer mismatch still rejects the input; the claimed alternative retry requirement is not part of the contract. No patch or acceptance decision was taken on the worker's authority alone. Its local packet/events/disposition remain under `.cache/omp-workers/2026-10-16-ssh-trust/domain-01/SSH-TRUST-DOMAIN-001/`.

Strict build01 succeeds in 44 seconds: 305 tasks, 61 executed. It builds both products and all three instrumentation APKs, runs the domain tests and reports zero Console/Desktop/platform Lint issues. All 38 domain cases pass with no failures, errors or skips: 12 new SSH trust cases plus 26 existing cases. The new cases cover canonical peers/formats/fingerprints, generation/revision bounds, distinct evaluation states, revoked-key return, wrong context, challenge reason/old-key binding, inconsistent proposals and redaction.

The [sanitized build manifest](2026-10-16-ssh-host-trust-build.json) binds all four new source/test files, five APKs, exact command, JVM suites and Lint counts. Full source snapshots, build logs, reports and APKs are frozen under `.cache/ssh-trust-01/build-01/`; source hashes were unchanged during the build. No external dependency, permission, version or design-token change occurs. Newly built product APKs are **not installed** and must not be confused with the older installed artifacts carrying the same 0.1.4/code5 version.

Root verification also passes: 32 authored Python AST files, full Ruff, generated Android token consistency and project validation (122 JSON files, 297 Markdown files, 2,268 local links, 495 sources and 54 API records). These repository checks do not qualify product behavior.

## Actual Googlebook execution

Target: **HP Googlebook 14**, Android 17/API 37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. The standalone `io.github.baitian6641.cloudy.platform.test` package executes only `SshHostTrustStoreTest`, against its own private fixture roots. The exact test APK hash is in the build manifest.

All 20 cases pass against actual Android SQLite/filesystem/framework behavior:

- Unknown, exact match, cross-algorithm change, revocation and deliberate reenrollment; retained tombstones after reopening independent store objects.
- Stale absence/generation, forged same-revision old-key/state proposals, competing first-use/replacement and replacement-versus-revocation decisions.
- Capacity including tombstones, absent/repeated revocation and revision exhaustion.
- Owner/package rejection, persisted full-width UID, real device-protected context, actual main thread and contended file lock.
- Invalid/newer schema, noncanonical/type-invalid rows, lost established storage, interrupted initialization, mismatched marker and symlink preservation.
- Corrupt/empty database preservation, real SQLite abort triggers for both insert and revocation, and explicit reload after uncertain completion.
- Immutable catalogs and redacted representations.

Private evidence: `qualification/results/private/foundation/2026-10-16-ssh-trust-01/`. Runner: `.cache/ssh-trust-01/run_device_01.py`. The recorded test interval is 09:37:02.089804Z–09:37:04.328518Z. Instrumentation reports exactly 20 successful case statuses and `OK (20 tests)`, with no skips or failures. The temporary package is stopped and successfully uninstalled; a separate package-path check confirms absence.

The installed Console/Desktop 0.1.4/code5 APK bytes, original profile rows/catalog generations, prior vault receipt hashes and boot ID/count remain unchanged. Both products are stopped. No authentication prompt, secret operation, remote fixture or network connection is started.

## Limits and continuation

These are standalone test-owned roots. They do not demonstrate actual trust use or isolation between the two installed products. Reopening objects is not process-death evidence. Concurrent threads/instances and same-process file-lock contention are covered; simultaneous independent-process writers, physical power loss, privileged rollback and full production-capacity endurance are not claimed. The capacity case uses an internal one-row fixture. Trigger-induced write failures exercise Cloudy's uncertain-result path, not a physical power cut.

Next define the native trust-management/presentation controller around real ledger operations, with exact peer/old-new key visibility, explicit cancellation, stale-result handling, and metadata-only recreation. Keep observer challenges tied to an actual future SSH attempt; do not fabricate a server key or fake session to populate the UI. Qualify the installed app boundary when that integration exists. Pending vault authentication, resource-aware signed handoff, broader lifecycle work and native desktop interaction remain separate P1 work.

All seven hosted workflows pass for predecessor `82a5118887048c4644ce76e1f9d31cbd02b433cf`. This increment needs its own exact-commit CI after push. No stage dependency, capability outcome or release gate changes.
