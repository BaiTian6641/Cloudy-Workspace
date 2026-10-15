# P0-S3 exec/PTY diagnostic source review

**Reviewed:** 2026-10-15. **Outcome:** primary and independent source reviews accepted the bounded **0.6.0 / code8** diagnostic for subsequent Android experiments. The final local build, Android Lint and JVM tests passed. A separate host OpenSSH preflight passed. **No Android 0.6.0 exec/PTY or active-lifecycle result exists at this checkpoint.** P0-S3 remains in progress; CAP-SSH and CAP-TERMINAL remain pending, with no dependent product-stage activation.

The immutable build reference is **`49607be8-a053-4f7f-8d22-3764b7e63136`**. The [package audit](2026-10-15-ssh-pty-probe-package.json) binds the APK and app source hashes. A subsequent device manifest must bind this reference to the committed source, exact installed APK and actual device/backend identities. The build reference is an identifier, not a source or APK hash.

## Implemented increment

The existing strict fixture configuration, host-key verification, explicit trust enrollment, key authentication and SFTP route remain. The new operation opens a separate exec session for the fixed `cloudy-exec-probe-v1` command, drains actual stdout and stderr with independent bounded readers, closes the command and requires its received exit status. The fixture contract is 19 bytes per stream and exit 17. Actual bytes and hashes are recorded; configured expectations alone cannot complete an attempt.

A fresh session then requests a real PTY with explicit echo/newline/canonical settings and initial dimensions of 80 columns by 24 rows. Two explicit controls request a resize to 132 by 43 and completion of the finite handshake. Resize acceptance requires the backend's actual terminal-size response. Completion requires the exact marker, EOF, channel closure and successful cleanup. The public SSHJ Shell interface supplies no exit-status getter, so the Android probe does not invent or cast to one. These calls use pinned SSHJ 0.41.2 symbols documented in the [exec/PTY handoff](../03-platform/googlebook/p0-s3-exec-pty-followup.md), including source records R93–R98; they are library APIs, not newly available Android framework APIs.

Each output stream is capped at 4 KiB; the fixed PTY input totals 12 bytes. No arbitrary command, shell interpolation or free-form terminal input is accepted. The worker uses a 60-second SFTP or 180-second exec/PTY total deadline from the shared operation enum, with separate bounded connection/request/read waits. This is a finite protocol diagnostic, not a terminal renderer, interactive shell product or performance benchmark.

One process-owned runtime retains the active attempt across observer recreation. The native screen adds explicit exec/PTY and phase-specific controls; actual task IDs and fresh Activity identities remain visible for later lifecycle evidence. The runtime fences a control against its current attempt, operation phase and visible observer. It records a phase durably before releasing a queued control to perform network I/O. Cancellation closes the owned socket and wakes control waits independently of the blocked protocol worker. Losing the last observer cancels work; there is no new background service or hidden-session classification.

## History and provenance

Journal schema 2 adds immutable per-attempt operation and build identity. Its schema-1 reader retains the five historical [0.5.0 SSH/SFTP attempts](../../qualification/results/P0-S3/2026-10-15-ssh-sftp-probe.md), their existing fields and endpoint trust. They become explicitly identified legacy SFTP rows with null build identity and `external-legacy` provenance. Rewriting them from 0.6.0 does not attribute their execution to the new build; their original external evidence remains authoritative.

New attempts require an explicit operation and build identity. Restoration cannot place a legacy row after a recorded-build row, change terminal history into running work, or reuse a prior attempt ID. The unchanged limits retain eight attempts and 32 events per attempt, with counted event overflow and no attempt eviction. Recovery interrupts unfinished work without reconnecting or creating another attempt. Model validation and migration tests do not prove Android AtomicFile behavior or a real on-device schema migration; those remain runtime checks.

## Review findings resolved

Different authors and reviewers inspected the engine/control/parser, state/journal migration, runtime/UI integration and host fixture. The primary agent reviewed the integrated changes and performed the build and host preflight. The following material corrections were accepted before this checkpoint:

- **Cleanup ownership:** if an owned helper remains alive after its bounded join, the engine preserves `helper-cleanup-timeout`. Cleanup uncertainty takes precedence over late cancellation, deadline or rekey result changes. The runtime keeps admission occupied and unavailable until process restart, so a new attempt cannot overlap an unretired helper. The UI preserves this error instead of masking it with a later cancellation-request overlay.
- **Durable control ordering:** the control can be offered once while a phase is being published, but it cannot be taken for I/O until that publication succeeds. An unsolicited matching response cannot advance the phase without an accepted and applied control. Exact attempt identity also fences late UI actions from a prior run.
- **Host audit failure:** a partial audit write or failed `fsync` leaves the fixture audit permanently broken for that channel. Further append attempts fail, including the terminal record; a later clean-looking completion cannot conceal uncertain evidence. The fixture's terminal event describes intended exit, while the controller must observe actual process termination separately.

No remaining actionable source finding was reported in these reviewed areas. This acceptance concerns the bounded implementation and its stated failure semantics; it does not establish hardware behavior or continuous-session lifecycle support.

## Actual local validation and package

The final strict build ran `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest` with the pinned [toolchain](../03-platform/googlebook/probe-toolchain.md), dependency verification and warnings-as-errors policy. It passed in **44 seconds**, with **51 tasks: 15 executed and 36 up-to-date**. The ignored log is `.cache/bootstrap/ssh-pty-build-02.log`.

Read-only inspection of the generated XML confirmed **134 JVM tests in 16 suites**, with zero failures, errors or skips, and **zero Android Lint issues**. The increment adds 25 JVM tests: four state/provenance cases, nine journal-codec migration/admission cases, seven byte-stream protocol cases and five control-ordering cases. They exercise actual production pure helpers, including malformed migration, preserved legacy history/trust, bounded input/output, stale or unsolicited controls, failed-publication cleanup and cancellation. They do not substitute for Android persistence, sockets, PTY transport, physical input or process-death experiments.

The APK is **15,827,911 bytes**, SHA-256 **`4f673be2ca5c0f2f4dcf9659645c827885f3aa4ee0952405d64d5f4c3d1a4f40`**. Read-only verification matched all **76** app source/resource/test hashes in the package audit and all **20** packaged notice assets. The runtime graph remains the same 30 coordinates, with no new dependency. The existing five manifest permissions and component/service scope are unchanged. In particular, this increment adds no foreground service or permission. The [dependency adoption record](2026-10-15-ssh-dependency-adoption.md), provider policy, license handling and narrowly scoped existing lint exceptions remain applicable.

## Separate host preflight and remaining device gates

The primary agent exercised the restricted backend through a real OpenSSH client. The successful private `host-03/preflight03-result.json` records stdout/stderr of 19 bytes each, exec exit 17, an actual PTY observation of 80 by 24 followed by 132 by 43, a 62-byte transcript and host-client exit 0 after EOF. The transcript SHA-256 is `2a1d94e913eac24e63ee3455706198abc39eb51033b4e5a7eb659a94771d21f1`. Independent read-only checks matched those exported stream sizes and hashes. This host exit result is separate from the Android Shell API's completion contract.

An earlier sandboxed PTY allocation failed because its device-node group mapping prevented the required ownership change. The authorized rerun used the same non-root fixture outside that sandbox and passed; it did not relax SSH host-key checking, authorize arbitrary commands or change the Android implementation. All three preflight server shutdown records report the listener closed. Raw configuration, endpoints, keys, logs and audit records remain private under `qualification/results/private/ssh-probe/host-03/`.

The historical forced-`internal-sftp` fixture remains separate. The new restricted exec/PTY backend uses a fixed dispatcher and explicit PTY permission while preserving forwarding and arbitrary-command restrictions. Its real input waits make lifecycle experiments possible without artificial delays.

After committing and verifying the source/package identity, the three remaining journal slots are intended for: successful exec/PTY with active recreation/two-observer behavior; injected process loss while genuinely awaiting input, followed by interruption without replay; and active explicit cancellation. Preserve and export all five legacy attempts before installing the update, then verify actual schema migration and unchanged trust/history. Observe actual worker/helper exit and backend cleanup independently of an on-screen cancellation request. Record any failed case honestly rather than silently resetting the eight-attempt journal.

Physical keyboard/mouse input, native IME, focus release, terminal accessibility/rendering, durable remote sessions, background/foreground-service classification, tunnels, X11 and product integration remain unqualified. The host preflight and local test results do not change those gates or complete P0-S3.
