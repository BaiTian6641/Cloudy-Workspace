# Host installation identity — HOST-IDENTITY

**2026-10-16; v1 directory profile and historical0.1.1 result.** Linux/macOS keep this profile. Current Windows initialization uses the separately reviewed [0.1.2 permanent-file profile](host-windows-identity-profile.md); the directory-sync limitation below describes the earlier Windows route, not a reason to retry or delete its claim. Source and Linux checks were independently reviewed. This bounded P4-S1 increment extends the local [host core](host-core-entry.md). It does not implement pairing, remote control, boot/session identity, jobs, listeners, service installation or a production capability manifest. Googlebook client work continues independently under the [parallel contract](../05-delivery/parallel-execution-contract.md).

## Explicit commands and continuity

`cloudy-host identity init --state-dir <existing-directory>` explicitly claims a fixed `installation-identity-v1` child directory using exclusive `Mkdir`. Only that successful claimant may generate a cryptographically random UUIDv4 and publish `installation-identity.json` inside it. The caller supplies an already trusted, app-owned state directory; no default path, account/environment lookup or parent-directory creation is implied.

`cloudy-host identity read --state-dir <existing-directory>` is read-only. Any existing initialization child entry prevents init from creating, repairing or replacing identity, including an empty directory, corrupt record, symlink or non-directory. Every failure after a successful child claim preserves the anchor. If an established record disappears, init cannot silently regenerate it. Recovery/reset is a separate future operation. Deleting the entire anchor or cloning the state directory is outside continuity guarantees; copied state copies the identity.

The record is strict bounded JSON, at most512 bytes, with exactly `identityRecordVersion: 1` and canonical lowercase UUIDv4 `installationId`. Reject duplicate/unknown/missing keys, invalid types/version/UUID, trailing values, oversized content and observed symlink/nonregular entries. Installation identity is nonsecret self-identification, never authentication, pairing authorization, machine identity, OS boot, user logon/session or process identity.

## Publication and failure boundary

Use scoped `os.Root` operations in the trusted directory and claimed child. A privately named exclusive temporary regular file holds the complete record; write, sync and close it before no-replace hard-link publication to the fixed record name. Replacing rename is excluded. Unsupported hard links fail honestly. Preserve the child and uncertain state on failure; cleanup targets only the attempt's owned temporary file. Parent-directory and child-directory sync results, cleanup and output failures must remain explicit. A failure after publication cannot authorize rollback, replacement or a second identity. Readback establishes current visibility/contents, not power-loss durability.

Go's official [`os` documentation](https://pkg.go.dev/os) defines Root confinement, Mkdir, Link and File.Sync. Root follows permitted internal relative symlinks, crosses filesystem boundaries and does not by itself prevent hostile same-user mutation. OpenRoot follows symlinks in its directory argument. Observed entry checks and opened-handle comparisons must not be represented as race-free no-follow operations. [Go's traversal-resistant API explanation](https://go.dev/blog/osroot) describes the path-validation race. Caller-owned directory trust is therefore a precondition; Windows mode bits do not establish reviewed ACL privacy. Precise filesystem/platform durability remains subject to actual evidence.

Use Go1.27.2 standard library [`crypto/rand`](https://pkg.go.dev/crypto/rand) and [RFC9562 UUIDv4](https://www.rfc-editor.org/rfc/rfc9562.html#section-5.4). Current crypto/rand.Read fills or fails irrecoverably; a synthetic injected entropy source is test-only. Errors exposed to CLI users are fixed safe text without state paths, raw OS error strings, account or environment data. Executable version becomes0.1.1; existing inspect remains a local diagnostic and does not gain fabricated production identifiers.

## Acceptance boundary

Require source/diff review, selected-compiler formatting/vet/race tests, strict parser and concurrent-init cases, isolated real process init/read/missing/corrupt behavior, and injected write/sync/close/publication/output failure classification. Use only owned ignored fixtures. Compile Linux/Windows/macOS targets separately from native OS execution. No installed user state, credentials, service, listener or device operation belongs to this increment. No P4 integration prerequisite or capability is promoted by a build or diagnostic result.


## Accepted local construction result

Direct Sol implemented the store and explicit CLI in `host/core` at version0.1.1. The [accepted result and source manifest](../../qualification/results/P4-S1/2026-10-16-host-installation-identity.md) bind verification03:25 retained checks,42 named Linux race-test/subtest passes, zero named skips/failures, and three rebuilt product plus installation-test targets. Actual Linux process cases cover stable init/read, refusal/preservation, concurrent initialization and post-publication output failure. Windows/macOS local evidence is compilation only. Earlier review findings and the failed Windows classifier-fixture compile are retained separately.

Independent review closed two successive overly broad skip findings. The final test gate requires an isolated real directory Sync probe and the exact actual initializer cause, reached stage, claimed/unpublished state, retained empty anchor and refusal to regenerate. The private cause does not change fixed public CLI errors. Injected failures remain synthetic adapter coverage; no hardware power-loss claim follows.

Required directory-sync failure immediately after a new claim leaves an incomplete anchor and no automatically recoverable identity. That behavior is deliberate under the no-regeneration contract. The Windows read-only handle route can encounter the documented write-access limitation. A precisely reported skipped persistence scenario is not a Windows identity support pass. Native Windows/macOS filesystem behavior, installation/ACL privacy and recovery remain open. A future adapter needs a separately reviewed durability/recovery profile; never silently remove required Sync to make a test green.

The actual OMP Kimi and Flash runs completed after the parent submitted the human-authorized packet through normal approval review; the two earlier child refusals sent nothing. Kimi's proposed code is rejected for placeholder/compile/parser/cleanup defects. Flash's failure plan is useful with corrections. Direct Sol source was implemented independently; no model proposal was merged merely because the process exited successfully. See the result for exact review scope. Regular external coworker rotation is permitted as recorded in the [parallel contract](../05-delivery/parallel-execution-contract.md), with explicit model/payload/result tracking.

## Official API and qualification mapping

Checked2026-10-16. Resolve R309–R313 in the [source register](../07-reference/sources.json). These are host APIs; they do not promote Android API-tracker or Googlebook capability status.

| API / reference | Selected availability and use | Failure route / remaining qualification |
| --- | --- | --- |
| `os.OpenRoot`, `Root.Mkdir`, `Root.Link`, `Root.Lstat`, `os.SameFile` (R309/R310) | Selected Go1.27.2; Root introduced in Go1.24. Scoped trusted-parent claim and no-replace publication with observed-handle checks | Fail and preserve state on unsupported links/changed entries. Root is not a hostile same-user mutation or ACL proof; actual filesystem lanes remain separate. |
| `os.File.Sync` (R309) | Selected Go1.27.2, file and directory requests are checked; tested Linux operations complete | Incomplete/uncertain state is retained; no portable power-loss guarantee or automatic recovery. |
| `crypto/rand.Read`, RFC9562 section5.4 (R311/R312) | Selected Go1.27.2 random bytes plus UUIDv4 version/variant fields | Current random Read fills or terminates irrecoverably. No substitute entropy or claimed authentication from the ID. |
| `FlushFileBuffers` and `GENERIC_WRITE` (R313) | Official Win32 handle-right requirement; selected Go Windows Sync route | Exact real handle limitation may block initialization. Native tests must report it explicitly; Windows usability and recovery remain unqualified. |
