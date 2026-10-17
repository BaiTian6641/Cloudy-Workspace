# macOS local context observation qualification — 2026-10-16

Cloudy host **0.1.4** passed scoped native qualification of `context read --state-dir` on **macOS 26.6.2, build 25G83, arm64**, using Go 1.27.2. The result covers real sequential local installation and boot identity observation. Parent review accepted the source and evidence; publication of this source version remains a separate parent checkpoint.

The supported implementation is the existing Linux composition with its build tag extended to Darwin. Its body is unchanged: read the installation identity, read the actual boot identity, then capture UTC observation time and runtime information. The output retains local context schema version 1 and `sequential-not-atomic`. Installation durability passes through the actual reader result (`not-assessed` on read). The timestamp describes observation time after both reads, not an atomic identity snapshot.

## Executed checks

The compiler recipe passed all **nine commands** with frozen inputs unchanged, no log overflow and empty stderr: selected compiler identity, formatting, Linux vet, five affected Linux race methods, Darwin vet and test/product cross-compilation, and Windows vet/test cross-compilation. The Linux race run passed **five top-level methods, zero skips and zero failures**. Windows execution was not performed.

On the real Mac, the selected diagnostic binary passed **nine top-level methods plus two failure-order subcases: 11 aggregate named passes, zero skips and zero failures**. The tests cover real repeated context reads, missing/incomplete/corrupt state preservation, staged installation/boot failures, timestamp ordering and durability passthrough, argument rejection, inspect/version behavior and identity read/output failure preservation. Test seams establish controlled error behavior; they are not OS fault injection.

The product binary then completed **nine finite CLI cases**: version, inspect, empty-state refusal, bad-argument refusal, explicit identity initialization, two successful context reads, incomplete-state refusal and corrupt-state refusal. Expected exit codes were checked. The two successful reads used the actual Darwin boot reader and the same real, freshly initialized installation identity. Identifier-bearing outputs stay in private evidence. No existing user state was read or changed.

The private fixtures separated valid, empty, incomplete and corrupt state. Before/after evidence checks preserved the expected record bytes and tree identities; context reads did not create, repair or replace installation state. All direct children were reaped, bounded output did not overflow, and the temporary work directory was empty.

## Preservation and cleanup

The parent archived **32 exact files, including the result, and verified eight directories** before issuing an exclusive archive acknowledgement. Cleanup checked the original owned root and exact file/directory sets, removed only enumerated verified files and empty directories, and confirmed the exact owned root absent. The five transfer/acknowledgement/cleanup commands exited zero. No recursive removal or automatic retry was used.

Independent review matched **54 evidence checks**, including source, artifacts, native result, archive and cleanup bindings. These are review checks, not additional test cases. The machine-readable companion contains the complete source and evidence hashes.

## Preserved preparation findings

Three recipes were rejected before their affected execution and remain retained. Compiler driver 01 could miss a fast-exit output overflow; driver 02 checks the flag after drain joins. Native packet 01 relied on fraction parsing unsupported by target Python 3.9.6; packet 02 strictly accepts UTC Go RFC3339Nano precision 0–9 and normalizes only interval comparison to microseconds. Raw nanosecond text remains private, and no latency precision is claimed. Transfer driver 01 could miss fast-exit oversized logs; driver 02 validates final output sizes before accepting the terminal result.

The parent separately ran 31 pure UTC controls (10 precision cases, one valid leap day and 20 invalid inputs) and 15 verbose-output parser controls. These preparation controls are distinct from native qualification. The original unrun recipes and their rejection evidence have not been relabeled as failed native attempts or overwritten.

## Limits

This is a local diagnostic construction checkpoint. Returned identities are correlatable local values, not authentication authority. Sequential reads do not establish atomicity, globally trusted container identity, session identity, reboot behavior or power-loss durability. The result does not qualify a service, pairing, remote admission, wire integration, macOS managed jobs, Windows context support or P4 platform support. Existing historical results retain their original source attribution. No new hosted CI result is claimed here.

## Evidence bindings

- `sourceFreeze`: `.cache/host-darwin-context-01/source-01/freeze-01.json` — SHA-256 `d957b6a77d27c486661b75f4917760f1767e23f2007842880d34bc27cf5f8384`.
- `sourcePatch`: `.cache/host-darwin-context-01/source-01/source-01.patch` — SHA-256 `54e8bc5697d804e3a661537e462f3a328abdf6e42593e29ce0ef79c360a4c608`.
- `compiler`: `.cache/host-darwin-context-01/source-01/verification-02/result.json` — SHA-256 `a8c9b5aafdbf2c237864341bb2f852893df43ad0ad1e586783e822cc2a110c4e`.
- `compilerReview`: `.cache/host-darwin-context-01/source-01/independent-compiler-review-02.json` — SHA-256 `414e83b19d10fe4676b78e8dd9f6ce7419be8c2d0925752f2a23e73e36a650d3`.
- `packet`: `.cache/host-darwin-context-01/native-entry-01/freeze-02.json` — SHA-256 `c394c99e89f61a229a7e0f2d3d8b8cf1d5789159bacf521d5789c2b1956bc40d`.
- `transfer`: `.cache/host-darwin-context-01/native-entry-01/execute-freeze-02.json` — SHA-256 `6c12467b99d6dd1d8a9a5730d71a4d75796ded6853c2a78971b6187c02561d6f`.
- `nativeReceipt`: `qualification/results/private/host-macos/2026-10-16-context-native-01/receipt.json` — SHA-256 `dd14f3eb1a7653d74643facc7d5377409b62d7308aee93f6919a525d270661df`.
- `nativeTerminal`: `qualification/results/private/host-macos/2026-10-16-context-native-01/retrieve-terminal.stdout` — SHA-256 `87116e30379cade8618e36d953cb1f57ad29c560df52813c62865741bb348ce6`.
- `archiveCleanup`: `qualification/results/private/host-macos/2026-10-16-context-native-01/archive-cleanup-receipt-01.json` — SHA-256 `686495b2cd9d6ea7192615bf0f104cf46040c0824665e54a625049ea3d735aa8`.
- `archiveRecipe`: `.cache/host-darwin-context-01/native-entry-01/archive-freeze-01.json` — SHA-256 `9de0e9dad3664842cf27b7d1e7bc0094eb610caab12eab6174b358be45cc5875`.
- `independentActualReview`: `.cache/host-darwin-context-01/native-entry-01/independent-actual-review-01.json` — SHA-256 `05e5c1c920547367ab9292162feda8e2018031f066243e42c559a83d67c75554`.
- `preparationControls`: `.cache/host-darwin-context-01/native-entry-01/parent-controls-result-02.json` — SHA-256 `af09b264552216f1c631958be93b535c13ac6d9d3e833009ba0dd8138aea3e28`.

See [the machine-readable result](2026-10-16-host-macos-context.json) for exact source hashes, compiler artifacts, test selection and bounded-envelope metadata. Private evidence paths identify retained local evidence; their contents are intentionally excluded from this public report.
