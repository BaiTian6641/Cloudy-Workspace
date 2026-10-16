# HOST-CONTEXT01 — explicit local context CLI

**2026-10-16; parent and independent source/retained finite Linux runtime acceptance, no actionable finding.** Host **0.1.3** adds `context read --state-dir <existing-app-owned-directory>` on Linux, composing the accepted installation and boot readers. The [contract](../../../docs/03-platform/host-context-observation.md) and [manifest](2026-10-16-host-context.json) describe exact output/trust boundaries and bind six diagnostic files, unchanged modules/protected readers, frozen source patch, all raw checks, five compiled artifacts, actual process receipts and independent review.

Installation Read precedes Boot Read. The response passes through actual installation durability, currently `not-assessed`, and samples UTC observedAt after both reads. Its explicit localContextVersion1 output is `sequential-not-atomic`, not authenticated host/session identity or a capability response. Default inspect remains identifier-free; identity init/read retain their semantics. Missing/partial/corrupt state refuses without creation or repair. Fixed safe read/output errors cannot expose IDs, paths or raw OS causes. Output failure can leave partial stdout, requiring both exit0 and complete JSON. Windows/Darwin recognize the command but return fixed unsupported before state/time/runtime access; no platform boot backend was added.

## Executed source and process checks

Eleven checks pass using selected Go1.27.2:

- Compiler identity, gofmt, changed diagnostic package Linux vet and race execution.
- Linux amd64, Windows amd64 and Darwin arm64 product builds.
- Windows amd64 and Darwin arm64 diagnostic vet and test cross-compilation.

Actual Linux 7.0.0-38-generic / x86_64 race output records **14 top-level methods / 25 aggregate named passes including subtests, zero skips or failures**, with count1 and15s test timeout. Source/module/protected-reader bindings remain unchanged. Tests cover real owned initialization/context reads, exact field/value expectations, state preservation, timestamp-after-read ordering, unchanged reader durability, private failure ordering, invalid syntax, short output and write failure. Injected composition failures do not qualify actual kernel faults. Windows/Darwin tests remain native-unrun.

Ten actual finite Linux commands, each with a five-second timeout, pass their expected success/refusal outcomes:

1. Version reports 0.1.3.
2. Inspect remains identifier-free with unchanged feature states.
3. Empty state refuses without creating an anchor.
4. Extra arguments return2 without creating state.
5. Explicit identity init creates a new ignored owned fixture.
6. First context read yields a complete valid local record.
7. Second context read matches the fixture installation and observed boot IDs; both timestamps fall within their process intervals.
8. Actual stdout `/dev/full` failure returns1 with fixed safe text and unchanged installation tree.
9. Incomplete anchor refuses without repair.
10. Corrupt record refuses without replacement.

All identifier-bearing stdout remains privately captured in ignored cache. Published records contain only output digests and checks, not observed IDs. Owned state bytes/modes match across read/output-failure operations; installed/existing user state was not used. The immutable identity and boot readers are reused without running their unchanged test suites again. No jobs, services, listeners, accounts, mounts, devices or Git operations occurred.

The independent reviewer matched all six sources/snapshots, modules/protected readers, freeze/patch/harness, 22 raw build outputs, five artifacts and20 raw CLI output hashes. It independently parsed retained counts and both context records without printing IDs or rerunning commands. Parent read the complete source/tests before acceptance.

## Qualification limits

The caller trusts the state directory and procfs view. The boot byte limit is not an I/O deadline against a hostile replacement mount. Sequential reads do not certify atomicity, authentication, reboot change, global/container-independent identity, user sessions or power-loss durability. This internal local command does not implement IPC authorization, pairing, event persistence or remote job admission. P4-S1 prerequisites remain unchanged. Earlier exact-SHA hosted results do not certify this changed0.1.3 source; no next CI pass, Windows/Darwin native command pass or new stage/capability is claimed.
