# P0-S3: finite exec, PTY and active-owner follow-up

**Checked:** 2026-10-15, America/Chicago. **State:** researched handoff; the exec/PTY increment and the experiments below are not implemented or qualified by this document. **Owner:** P0-S3 probe maintainer, with an independent source and evidence reviewer. Read the [engine brief](p0-s3-ssh-engine-qualification.md), [0.5.0 source/package review](../../06-validation/2026-10-15-ssh-probe-review.md), [operation-lifetime boundary](p0-s2-operation-lifetimes.md) and [current execution state](../../05-delivery/execution-state.md).

The next increment should add one finite exec and a separate PTY exchange to the existing trusted SSH transport. It does not need a terminal renderer, arbitrary command input, a production host agent, a foreground service or a new dependency. The budget below assumes five retained 0.5.0 attempts and three available slots; inspect the actual journal before starting. This planning assumption does not publish or accept those five device results. CAP-SSH and CAP-TERMINAL remain pending.

## Preserve the existing evidence first

Export and hash the complete 0.5.0 journal and relevant private fixture artifacts before changing the APK or journal reader. Preserve the five attempt identities, trust decisions and source/APK bindings in their original evidence. Keep keys, endpoints and raw transcripts private. No history eviction, reset, remote deletion or automatic replay is part of this increment.

The current journal has a top-level probe version; that field alone cannot identify the binary that executed every historical row after an upgrade. Give each new attempt an immutable execution-build reference, backed by the exact version/code, source commit and APK hash in its evidence manifest. Interpret old rows through their original 0.5.0 evidence; do not silently relabel them with the new top-level version. Any schema migration must preserve legacy attempt/trust semantics and explicitly identify unavailable legacy provenance. Test both legacy restoration and new records before deployment.

## Three-attempt order

| Slot | Real operation and intervention | Required evidence |
| --- | --- | --- |
| **6: successful exec/PTY and active recreation** | Run a fixed harmless byte-producing exec with an expected nonzero exit status. Open a fresh PTY channel and reach a real fixed-token input wait. Recreate the Activity, open a second observer and close the first. Query initial size, request a preset resize, query again, send the fixed completion token and finish. | Exact stdout/stderr lengths and hashes, expected exit status, initial and resized server-observed dimensions, unchanged attempt/process during recreation, fresh Activity generation, continued work while the second observer remains, and actual cleanup. |
| **7: process loss during active PTY** | Reach a real remote-ready/input-wait state, inject actual process death, then relaunch and observe the journal and server. | Old process absence, a new process identity, the same attempt reconciled to interrupted, no automatic authentication/channel request, and the remote socket/helper outcome. Describe the actual injected stimulus; do not call it natural low-memory eviction. |
| **8: explicit cancellation after recovery** | Deliberately start another finite PTY operation, confirm remote readiness and press Cancel while its read is pending. | Immediate cancellation request, actual worker/socket exit, terminal journal commit and observed remote cleanup. This also tests that recovered interruption permits a later explicit attempt. |

Put process loss at slot 7: recovery at slot 8 would confound absence of replay with a full-history admission limit. Do not consume slots with connection smoke tests that lack the required fixture policy; perform backend configuration/preflight checks first. An unexpected failure consumes its real slot and remains evidence. Preserve and review the batch before deliberately archiving it; do not increase the limit to hide a failed run.

The genuine PTY input wait is the active operation, not a mock delay. Use a fixed bounded request/response handshake with actual remote reads and writes, finite per-step and overall deadlines, and no sleeps or invented progress. The existing ten-second channel-read timeout must be considered when scheduling UI interventions; any phase-specific change must stay within the reviewed overall deadline and be documented. Hiding all observers before a process-kill test would first invoke the existing cancellation policy and invalidate an active-process-loss claim.

## Pinned public symbols and limits

R93–R96 and R98 below were inspected at SSHJ **0.41.2**, upstream commit **`6fad160fe36605a68847ac8a527750c615b4bd04`**. They are upstream library APIs/implementation references, not Android SDK symbols or API-level guarantees. Their presence does not establish first-version availability, Android execution or server behavior. No Android API-tracker entry is added for them.

| Reference | Concrete route | Qualification boundary |
| --- | --- | --- |
| R98/R93 | `SSHClient.startSession()` then `Session.exec(fixedCommand)`; `Session.Command.getErrorStream()` and `getExitStatus()` | Drain bounded stdout and stderr; close before inspecting exit status, require the expected value, and reject a missing status. Open a separate session for PTY because a session is single-use. |
| R94 | `Channel.getInputStream()`, `getOutputStream()`, `join(timeout, TimeUnit)`, `close()` and `isEOF()` | Use finite waits and explicit cleanup. Read both exec streams so one cannot block the other; EOF or a returned call alone is not a verified exit result. |
| R93/R95 | `Session.allocatePTY(...)`, `startShell()`, `Session.Shell.changeWindowDimensions(...)` | Shell has no public exit-status getter. Require a fixed completion marker, EOF and cleanup without casting to `SessionChannel`. Resize requests have no requested reply: confirm dimensions through a new server query. |
| R95/R96 | `PTYMode.ECHO`, `ECHONL`, `ICANON`, `ONLCR`; explicit `allocatePTY` mode map | The pinned default-PTY implementation passes an empty mode map despite the interface's echo wording. Set and verify the selected echo/newline behavior explicitly; PTY bytes are not inherently identical to non-PTY bytes. |

Official pinned references: [SSHClient](https://raw.githubusercontent.com/hierynomus/sshj/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/SSHClient.java), [Session](https://raw.githubusercontent.com/hierynomus/sshj/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/connection/channel/direct/Session.java), [Channel](https://raw.githubusercontent.com/hierynomus/sshj/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/connection/channel/Channel.java), [SessionChannel](https://raw.githubusercontent.com/hierynomus/sshj/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/connection/channel/direct/SessionChannel.java) and [PTYMode](https://raw.githubusercontent.com/hierynomus/sshj/6fad160fe36605a68847ac8a527750c615b4bd04/src/main/java/net/schmizz/sshj/connection/channel/direct/PTYMode.java). Exact source hashes are in [sources.json](../../07-reference/sources.json); no third-party implementation is copied into this repository.

## Separate backend fixture policy

Preserve the historical forced-internal-SFTP configuration. Prepare and validate a separate temporary configuration for this experiment. `ForceCommand internal-sftp` overrides shell and exec as well as subsystem requests, so merely enabling PTY is insufficient. Use a private fixed dispatcher that permits only the exact probe exec token or fixed PTY handshake; never evaluate or interpolate `SSH_ORIGINAL_COMMAND` as shell code. The dispatcher is a qualification fixture, not a new dependency for ordinary product SSH. Keep authentication keys dedicated to the fixture and retain disabled forwarding/user-rc behavior. Enable `PermitTTY yes`; verify the effective configuration with the actual extracted OpenSSH binary before listening. [R18: OpenSSH server configuration](https://man.openbsd.org/sshd_config).

Authorized-key `restrict` also disables PTY. The documented `restrict,pty` combination restores PTY while retaining the other restrictions. Retain a forced dispatcher and reject every unrecognized input. Command restrictions do not independently disable forwarding; preserve `DisableForwarding yes` or the equivalent explicit restrictions. These are current official manual semantics, not an assertion that a particular fixture configuration has passed. [R97: OpenSSH authorized-key restrictions](https://man.openbsd.org/sshd).

Query actual terminal dimensions through `stty size` or the fixture's `TIOCGWINSZ` observation, rather than echoing requested values. Record only bounded known protocol markers, validated dimensions, byte counts/hashes and sanitized status. Do not publish arbitrary shell output, banners, environment values, endpoint/user names or raw terminal transcripts.

## Ownership changes and acceptance boundaries

The current serial I/O owner cannot execute queued input/resize work while blocked reading. Provide a bounded independent control path with immutable attempt fencing; never perform network control calls on the main thread or retain an Activity through a pending operation. Keep cancellation independent of blocking reads. A request must not free the worker slot before actual exit and terminal persistence. Continue to use the process owner across native recreation, with actual observer visibility determining last-window cancellation.

Use a small immutable operation kind and allowlisted exec/PTY result fields. Bind controls to the active attempt and current protocol phase; reject stale controls from earlier attempts or recreated windows. Tests should cover real bounded parsing, wrong/extra markers, overflow, stale controls, cancelled completion and legacy journal preservation. Compile, lint and source review precede device testing; exact source/APK/backend identity and independent evidence review precede acceptance.

If exec or PTY is rejected, record the actual rejection and keep the existing SFTP lane usable; do not silently fall back to arbitrary shell commands or fabricated dimensions. If the three slots are exhausted, preserve the failed and successful evidence and plan a separate reviewed batch.

Last-observer hiding, LAN revocation, larger SFTP cancellation, natural/system process termination, remote-process survival, direct channels, X11, a renderer and physical keyboard/IME behavior remain separate requirements. This follow-up does not complete P0-S3 or promote either capability gate.
