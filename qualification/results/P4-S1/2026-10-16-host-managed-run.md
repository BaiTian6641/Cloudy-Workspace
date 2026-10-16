# Retained Linux managed-run construction — 2026-10-16

**Result:** parent and independent review accept source03 and the bounded composite pure-check evidence. Six unchanged top-level methods plus sixteen admission subtests pass in the first run; the one corrected method passes in a focused second run. These are seven distinct methods across two runs, not a fresh seven-method rerun. The first run remains failed. No native, CLI/service, remote capability or P4 stage pass follows. Host executable remains 0.1.3.

## Actual checks and preserved failure

On Linux kernel 7.0.0-38-generic, x86_64, Go 1.27.2, the parent first ran seven offline commands. Compiler identity, formatting, module verification, exact three-module runtime closure, actual test-name listing and package vet passed. Race execution selected seven pure methods: six top-level methods and sixteen subtests passed; TestWaitCaptureAndExactRealStoreRetry failed, with zero skips and a failed package result. The immutable result is 310b5919ee2bf37543f57c6d8307c4b11bdb04300c6154869a9bfcdab603dc69.

The source diagnosis identified a stale expected receipt in the test. An earlier explicit exact append retry updates the latest receipt's AlreadyObserved flag to true. The later cached Wait returns that latest receipt while preserving its immutable observation, but the assertion compared the original first-append receipt. Source03 changes exactly that comparison to retry.Receipt. Production, native fixture, helpers and the other six methods are byte-for-byte unchanged. The source02 failure is preserved rather than relabeled.

The parent then ran only affected formatting, package vet/recompilation and TestWaitCaptureAndExactRealStoreRetry with race/count1 and a 60-second timeout. All three commands pass. The exact one method and its package pass, with zero skips/failures; no other method executes in that run. Result ba74d1b7e31b0ee1808fd9fba686fec28af1eb4e7ae8acb88d7f404c5a9328cd binds the successful focused run. The previous owned Go cache is reused under the same pinned inputs with count1, without downloading or repeating unchanged backend/upstream/Android suites.

The [manifest](2026-10-16-host-managed-run.json) binds current/source02 files, both result receipts, fourteen first-run and six focused raw outputs, tool/module/check inputs, source/repair freezes and independent source reviews. The composite evidence is seven distinct top-level methods and sixteen unchanged subtests, or 23 distinct named passes. It does not erase the original failed method/package or claim all seven methods ran against source03.

## Ownership and source review

The [contract](../../../docs/02-architecture/host-managed-run.md) describes the real retained Job, borrowed single-writer Store lifetime, actual typed boot/frozen generated correlation metadata, full-snapshot capacity/collision preflight, busy gate with independently usable Cancel, one-shot cached Wait and terminal-only successful settlement. Retired is independent of persistence. Commit uncertainty stays sticky, and copied opaque observations support explicit caller-owned storage recovery after confirmed process retirement. There is no durable admission ledger, automatic retry/reopen/replay or lifetime beyond the caller process.

Source01 review preserved two findings. Source02 refuses settled-before-wait before creating revision 1 after successful terminal settlement, while preserving an existing cached Wait. Its native fixture no longer registers store/cwd for testing's automatic cleanup. Explicit private paths/report remain on failure or uncertainty; cleanup requires confirmed retirement, checked Store/delegation Close and passing assertions. KeepAlive extends references only through function return, with the independent finite outer supervisor retaining recovery authority afterward.

Independent source02 receipt a50966cc84d60e3c51c44458109df3fafb9766de41d3edc293e7f23ab9426dcd verifies 47 bindings and closes those two source findings. Source03 receipt de8ef2af687fd7e6994ec8dadc6b5eed95c3ee8f04fe694c9387185390e007a1 verifies 52 bindings, the one-expression test correction and the affected-only recipe. These source reviews did not execute tests. The separate independent actual-evidence receipt d2cf7e7d7d89867dede137c961e67cc884a3f7433f457e4da9025924fe8ed704 verifies current inputs, six focused raw outputs, the retained first-run evidence and exact one-expression delta. It accepts the composite pure-check scope without rerunning tests. Parent review accepts the same bounded milestone.

## Qualification limits

The pure tests use private starter/observer/append seams with real temporary bbolt transactions and actual boot reading. They do not launch managed jobs or qualify positive public recorder/native composition. Synthetic persistence-effect flags are not actual OS commit/close fault injection. The native harness/child source compiled with the package but was excluded from execution; neither is counted as a default skip or native pass. The retained underlying Job may own bounded output/state; exported metadata, errors and event persistence copy only the approved whitelist.

No other-OS managedrun backend, service/registry/CLI, authenticated remote admission, automatic crash recovery or new hosted CI result is claimed. Stage prerequisites and ordinary agent-independent SSH remain unchanged.
