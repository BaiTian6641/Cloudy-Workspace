# Retained SSH PTY + same-owner SFTP qualification — 2026-10-17

**Stages:** bounded P2-S1/P2-S2 construction, not a stage or CAP-SSH/CAP-TERMINAL promotion. **Branch:** `codex/console-r1-implementation`. **Implementation:** the independently reviewed frozen proposal in `.cache/console-terminal-entry-01/pty-proposal-01/` was applied without source drift. `SshAuthenticatedTransport.kt` SHA-256 `fa4351061337a859056b82c29766738f2d5e5251a2d9dbf0fb6b7c9f3da696a1`; `SshOwnedPty.kt` SHA-256 `d8476564cf0781b3e17a944c2f437add1e95cec7b41a0a2c21f377684acd5b95`.

## Implemented boundary

`SshAuthenticatedTransport.openPty(SshPtySize)` now performs real SSHJ channel open → `allocatePTY("xterm-256color")` → `startShell` and returns an engine-issued owner. It exposes bounded copied input, coalesced resize, bounded stdout/stderr reads and close. One PTY is allowed per retained transport; closing it conservatively closes linked SFTP and the whole transport. SFTP remains a separate short-operation lane on the same authenticated SSHJ client.

Input is limited to 16 KiB per offer and 64 KiB including the in-flight write; output uses two dedicated drainers and a bounded 256 KiB / 64-chunk queue with producer backpressure. Resize alternates with queued bytes and only the latest pending size is retained. Final retirement includes channel/socket closure and actual setup/reader/writer worker joins; uncertain cleanup retains the existing quarantine behavior. No credential, SSHJ object, terminal transcript or raw SSH channel crosses the public API.

## Executed checks

### Pure compiler/bounds

`./gradlew :ssh:compileKotlin :ssh:compileTestKotlin :ssh:test --tests 'io.github.baitian6641.cloudy.ssh.SshPtyBoundsTest'` with the documented offline strict toolchain: **BUILD SUCCESSFUL**. Seven tests pass: in-flight capacity/copy ownership, all-or-nothing invalid input, resize coalescing/fairness, close wake/stale refusal, output capacity/producer wake/drain-after-close, timeout-versus-EOF, and dimension bounds.

### Real OpenSSH PTY + SFTP

Reproducible fixture source: [qualification/fixtures/ssh-pty](../../fixtures/ssh-pty/README.md), script SHA-256 `21e3584cdcf8a3827b07cc71d8c9fda3465172f488b42f90d2f2a92577054c25`, shim source SHA-256 `7bd20d4d7d6308d057a3eb1c1122010e6c33239e105b18861d57a8eb975be94a`. Server: extracted Ubuntu OpenSSH `10.2p1-2ubuntu3.8`; loopback 127.0.0.1:18164; fresh fixture ECDSA host key and random password; namespace-private account/home. Run: ignored `qualification/results/private/ssh-pty-01/run-LPaPAf/`.

`SshPtyTransportHostTest.realPtyResizeAndFilesShareOneAuthenticatedOwner` executed against that fixture and **passed 1/1** (`--rerun-tasks`, strict offline dependencies). Preserved JUnit XML SHA-256 `7764cbcb02f53c03c472757665dbcf4f11e6b39e8700177b681f800868e2297f` in ignored `.cache/console-r1-01/pty-fixture/host-pass-LPaPAf.xml`.

The assertions establish all of the following on one authentication/transport owner:

1. Real password authentication succeeded exactly once and a real PTY shell opened; a second PTY was refused.
2. `list(".")` and `read("proof.txt")` succeeded through SFTP while the PTY lanes were live. The server log records shell id 0 and sequential SFTP subsystem ids on the same client connection.
3. Local resize admission to 123 columns × 41 rows was followed by remote `stty size` output `41 123`; the test also required the octal-generated `CLOUDY_PTY_OK` marker rather than matching echoed command text.
4. Explicit close produced `SshTransportCloseResult.Retired`, one ownership retirement callback, and stale input rejection. The server logged shell/SFTP/connection closure; fixture teardown closed the listener.

This is real host protocol evidence, not an Android renderer, remote command-success, server privilege-isolation or Googlebook result.

### Regression/package checks

Final default `:ssh:test`: 16 suites / 73 recorded tests, **47 executed successfully, 26 explicitly skipped, 0 failures, 0 errors**. The real PTY host test is skipped by default without its explicit fixture; its separate 1/1 pass above is not hidden in that count. `:console:assembleDebug :console:lintDebug` also passed with strict offline dependency verification; Lint reports `No issues found.` Debug APK SHA-256 `68e0a7deee9421890dfab8cbd704d8e5703e7f09146f9e2a731e3fd9bbb76ff4`; it was not installed by this run.

## Preserved fixture failures and repair boundary

Earlier attempts remain in ignored run directories. They found, in order: unavailable bubblewrap namespace creation, group-writable StrictModes directories, denied chroot, denied setgroups/initgroups, and an `LD_PRELOAD` path split by the workspace space. Authentication succeeded before the initgroups failure; no failure was relabeled as a PTY pass. The maintained production PTY source did not need a repair after the frozen proposal.

The final tracked fixture uses a private user/mount namespace and a test-only shim for only `chroot`, `setgroups`, and `initgroups`. This is necessary because the outer environment already denies setgroups and bwrap rejects chroot. It creates no host account/service and changes no SSH protocol, password verification, PTY, shell, SFTP or retirement behavior. It **does not qualify** OpenSSH privilege separation, account isolation, chroot or group semantics. The unrelated untracked `qualification/probes/ssh-pty-host/bring-up.sh` draft remains untouched and was not committed.

The first system-Python `--schemas` attempt reported missing development requirements; the repository `.venv` invocation then passed all three schemas/examples and six invalid-case rejections. This tooling setup failure is distinct from source qualification.

## Remaining acceptance boundaries

- No product terminal/renderer, IME/clipboard/menu, tabs/splits, tmux or X11 window behavior is added by this increment.
- Local input admission is not remote consumption; write/flush and resize have no server acknowledgment. Normal EOF is not exit-success.
- Queued output intentionally remains drainable after close; the future renderer/session owner must fence stale attachments and discard stale caller buffers.
- Real blocked-write/backpressure stress, changed-key retirement while a PTY exists, broader server concurrency, long-running sessions and device transport remain unverified.
- Key/keyboard-interactive authentication and verified jumps remain separate C03/C14 work. CAP-SSH and CAP-TERMINAL remain experimental; P2-S1/P2-S2 remain incomplete.
