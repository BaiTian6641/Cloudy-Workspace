# Connection profile storage and endpoint normalization

**Date:** 2026-10-16. **Outcome:** the local profile domain/storage increment passes strict compilation, four zero-issue Lint reports,40 JVM tests,19 actual Android storage cases and two separate-process phases. The default process-fixture invocation skips and is not a pass. Native profile-editor UI, installed-product catalog isolation, trust, credential attachment, network connections and enrolled handoff resources remain unimplemented or unqualified by this increment. P1-S3 remains in progress.

## Implementation and review

The [profile contract](../../../docs/02-architecture/connection-profiles.md) separates endpoint configuration from credentials, trust, windows and handoff resource IDs. Immutable domain objects validate label, owner/protocol, logical host/port, v4 UUID and revision. The real SQLite store persists a catalog generation and row revisions, compares both inside each mutation transaction, and rejects stale edits or deleted records without upsert. Duplicate endpoints remain distinct profiles. Storage bounds, original device-protected-context rejection, nonblocking locks, nondeleting corruption handling and explicit completion-unknown results are implemented.

Two direct Codex GPT6.1-Sol workers owned disjoint domain/extraction and platform/storage files. The parent added the identity→domain build edge and the opt-in process test, inspected their changes and ran integration checks. A separate direct reviewer found no actionable source issues. It independently compared the extracted `EndpointHost.canonicalize`, IPv4 and IPv6 method bodies with their committed vault predecessors: behavior is unchanged after indentation/method-name normalization. Existing `VaultBinding.canonicalHost` delegates through the same public signature, preserving the canonical host used in credential binding and authenticated data.

The authorized OMP Kimi editor-copy assignment reached its100-second CLI limit after100.318seconds, exit1. Actual routing was `kimi-code/k3`, with zero tools, an aborted stop and no final text answer. It produced no accepted implementation or review result and was not automatically retried. Private packet/events and disposition are retained under `.cache/omp-workers/2026-10-16-connection-profiles/ux-01/PROFILE-UX-001/`.

## Build and JVM checks

Source base: `b5a28b2b4f6c176bc4f379f51411737d945b93a6` plus this increment. The [build manifest](2026-10-16-connection-profiles-build.json) binds nine source/build/test files and all three APKs. The parent runner confirms those source files did not change during the build. Logs, test XML and APKs are frozen under `.cache/connection-profiles-01/build-01/`.

Build01 succeeds in44seconds:253 tasks,72 executed. Both product APKs and the standalone platform instrumentation APK build with strict dependency verification and Kotlin warnings as errors. Console, Desktop, platform and identity Lint each report zero issues. The JVM domain suite passes26 cases, including13 new endpoint/profile cases; identity passes14 cases, including the unchanged vault binding/codec/authorization checks through the moved host helper. No JVM cases skip or fail. Lint's existing notice that the pure JVM domain module is treated as an external dependency is retained; its Kotlin compiler and JVM tests are the executed checks for those sources.

The only module-edge change is identity→domain. No external dependency versions, locks, verification hashes, manifest permissions, app version or design tokens change. Product APKs are built only and do not replace the preserved installed0.1.3/code4 manual-vault artifacts.

Final independent acceptance review matches all nine source and three frozen APK hashes, actual build/Lint/JVM reports, device case counts, distinct-process/seed-stop observations and cleanup/boot evidence, with no actionable findings. Root checks pass32 Python AST files, full Ruff, generated-token consistency and project/schema validation:119 JSON files,293 Markdown files,2,205 local links,484 sources and52 API records. These checks do not promote any of the remaining product/UI gates.

## Actual Android execution

Target: **HP Googlebook14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Only `io.github.baitian6641.cloudy.platform.test` is installed, targeting its own standalone package. APK size32,445,283bytes; SHA256 `a74f88a4523c3989ce29a97ca2743560859f1de41dd1f0efee65a2e0a6c521bb`.

The19 `ConnectionProfilesTest` cases all pass against real Android SQLite and app-private test files:

- Create/read/update/delete, reopening independent store objects and immutable snapshots.
- Competing writer admission, revision conflict, deleted-editor rejection and fresh IDs for later creation.
- Generation mismatch for every mutation, duplicate endpoints and capacity without eviction.
- Owner/package policy, rejection of the real device-protected context, actual main-thread denial and file-lock contention.
- Revision exhaustion, malformed/newer schema, out-of-range/noncanonical rows and corruption preservation.
- Missing established artifacts, interrupted initialization, mismatched marker and symlink-target preservation.
- Real SQLite abort-trigger failure mapped to completion unknown, followed by reload showing no committed row; the trigger is a controlled test fixture, not a simulated store.
- Version-token restoration and redacted representations.

The default `ConnectionProfilesProcessTest` invocation records an assumption skip before fixture creation. The explicit `seed` phase passes after saving one profile and an edit-token receipt. The parent force-stops the standalone package and observes no PID. The explicit `verify` phase passes in a different process, reads the same generation/profile/revision, updates to revision2, rejects the old token, deletes the current row and rejects resurrection by the old editor. Only then does the test remove its own fixture. Each phase is one successful JUnit execution; the extra custom instrumentation status reporting the PID is not another test.

Private evidence: `qualification/results/private/foundation/2026-10-16-connection-profiles-01/`. The runner is `.cache/connection-profiles-01/run_device_01.py`. Execution spans08:33:34.618157Z to08:33:36.738349Z. Uninstall succeeds and independent package-path inspection confirms absence. Pre/post Console and Desktop remain stopped at0.1.3/code4. Boot ID and boot count are unchanged. No product Activity, authentication prompt, credential operation, remote fixture or network session is started.

## Boundaries and continuation

These are isolated test-owned roots in one standalone package. They establish actual Android storage and ordinary process restart, not successful catalog operations in both installed products. Concurrent threads/instances and same-process file-lock contention are covered; simultaneous independent-process writers, full512-row production capacity, physical power cuts and privileged snapshots are not claimed qualified. The capacity test uses the internal two-row limit. No product UI/density/input or full host-profile acceptance follows from storage success.

Next integrate genuine native Add/Edit/Delete, conflict/draft retention, uncertain-outcome reconciliation and metadata-only restoration. Then qualify each owning product independently. Keep the later environment/tags/jumps, trust/authentication, enrolled resources and actual session destinations in scope. Continue the separate pending protected-vault qualification only after the existing human-readiness answer is correlated with a fresh operation.

All seven hosted workflows pass for preceding exact commitb5a28b2. This new source requires its own exact-commit CI after push. The full P0–P6 goal remains active; no stage or release gate closes here.
