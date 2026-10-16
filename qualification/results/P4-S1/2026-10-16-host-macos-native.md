# Selected native host checks on Mac Studio

**2026-10-16; actual selected native cases and exact cleanup accepted.** The user-selected Mac Studio runs macOS26.6.2/build25G83, arm64. The [manifest](2026-10-16-host-macos-native.json) binds exact sources, four Darwin artifacts, packet, private evidence digests and independent review. This is a new actual host run, separate from the previously uninspected hosted CI artifacts.

Actual execution passes **13 top-level tests /28 aggregate named cases including subtests, zero skips or failures**:

| Package | Top-level passes | Aggregate passes | Actual boundary |
| --- | ---: | ---: | --- |
| installation | 7 | 22 | Owned temporary initialization/read/no-regeneration, missing/bad parent, existing claim, concurrent claim, corrupt-read preservation, symlink/nonregular refusal and publication failure boundaries |
| localpeer | 4 | 4 | Real unnamed UNIX sockets: kernel UID observation, mismatched expected UID rejection, borrowed endpoint usability, datagram/unconnected/nil/closed refusal |
| diagnostic | 2 | 2 | Existing identity read/output failure preserves an owned fixture; context refuses before state access on Darwin |

Three additional actual CLI checks pass expected outcomes: version0.1.3; identifier-free inspect with real Darwin/arm64/Go1.27.2 runtime; and context read returning fixed unsupported with an empty owned state directory. Context refusal is truthful boundary evidence, not a macOS boot/context backend. Test output and failure details remain private; no remote address, account, serial or private inventory is published.

The parent uploads six exact files into one exclusively created owner-controlled temporary directory. Tests use per-suite owned TMPDIR, Go15-second timeout and20-second direct-process timeout; peer data I/O has2-second deadlines. Test source creates no child processes, accounts/services or network listeners: socket resources are unnamed pairs and one unconnected UNIX socket. All13 selected names start/pass once; every subtest start/pass pairs, package terminals are PASS and test stderr is empty. Source, execution and cleanup evidence received independent review without rerunning the operation.

Three previously accepted Darwin artifacts are reused after matching exact relevant sources and hashes: peer test, diagnostic test and host executable. Only the missing installation test is newly crosscompiled with selected Go1.27.2/CGO0, offline and read-only modules. Current module files include parent-added bbolt1.5.0 for separate work; fresh Darwin selected-package dependency inspection imports neither bbolt nor jobevents. Existing xsys0.49 and selected Go remain unchanged. Reused binaries retain their original receipt/module provenance; no rebuild is invented.

All24 archived file hashes match the actual terminal/archive records. The three test temporary directories are empty after cleanup; the context-state directory remains empty. Parent cleanup verifies the exact canonical root owner/mode, every individually enumerated file owner/hash, four exact empty directories and no remaining packet executable paths. It removes24 exact files and4 empty directories, then confirms the owned root is absent. Cleanup and SCP stderr are empty. No wildcard deletion or automatic failure cleanup is used.

The executed archive/cleanup helper01 has **two failed Ruff PLW1510 checks** for omitted explicit subprocess check flags. Both calls use defaultFalse and explicitly assert return codes. The parent command chain continued after the lint failure; actual01 source and receipts are preserved. The later helper02 adds explicit `check=False` at those two sites, passes parent-reported AST/Ruff checks and is **not executed**. No all-helper lint pass or replacement runtime provenance is claimed.

Qualification is limited to these selected cases on this host/build, without race instrumentation. Same-user kernel credentials and a different expected number do not establish real cross-user behavior, application/code/session identity or command authorization. Installation Sync completion does not qualify crash/power-loss durability. Darwin managed jobs, boot identity, pairing, remote admission, user-session/capture adapters and full P4 integration remain open. The new run supplies actual sampled macOS evidence; it does not retrospectively inspect or certify historical hosted native artifacts. No capability or stage is promoted.
