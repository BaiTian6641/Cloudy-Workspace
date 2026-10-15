# Acceptance cases

These are procedures to implement/run during the linked stages. They are not results. Official desktop test IDs are traced where relevant through [Google's quality guide](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop).

| ID | Procedure and observable pass condition | Failure / boundary case | Stages |
| --- | --- | --- | --- |
| Q-P01 | Inventory actual build/ABI/WebView/inputs and install/launch development targets through authorized ADB | Wrong USB port/cable, unsupported transport, unavailable device recorded as blocker | P0-S1/S2 |
| Q-P02 | Resize, new window, maximize/fullscreen, external display, focus switching; keep content/context and system controls reachable | Small/short windows, rejected fullscreen, changed scaling, process recreation; trace T-UI_Config, T-Multi-Instance, T-Request_Fullscreen_Mode | P0-S2/S4, P1-S2, P2-S3 |
| Q-C01 | Connect to ordinary SSH with known trust and supported auth/jump chain; verify the server identity at every hop | First trust cancel, changed key, wrong credentials, wrong host, offline route; no authentication after unaccepted mismatch | P0-S3, P2-S1 |
| Q-C02 | Compare exact committed bytes for multiline paste/CJK/combining/emoji; use editor/agent alternate screen, selection, resize, mouse reporting | Composition during resize/pane switch, huge output, clipboard escape sequences, focus loss; no duplicate/lost input or stuck modifiers | P0-S3, P2-S2 |
| Q-C03 | Attach configured durable session, disconnect/evict/reopen, and reattach to the same process | Host reboot/missing tmux/session termination distinguished; unsent input/last command never replayed | P2-S3 |
| Q-C04 | Pick/drop/paste attachment, upload, compare source/destination size/hash, insert correct host/environment path | Source changes, URI revocation, canceled/interrupted upload, Windows/WSL paths; no premature reference/submit | P2-S4 |
| Q-C05 | Start loopback forward to selected host service, open external browser, close listener independently | Port collision, host loss, expired login callback, non-loopback bind request; no privileged renderer navigation | P2-S4 |
| Q-C06 | Complete full coding day with real project/TUI, Desktop uninstalled, custom agent disabled | Long output, multiple panes, disconnect and client eviction; bounded memory and no surprise replay | P2-S5, P6-S1 |
| Q-D01 | Select host/project, show accurate backend state, and launch through proved external mechanism or documented manual fallback | Missing client, unpaired/unreachable host, unsupported version, cancellation; no fabricated stream state | P0-S4, P3-S1/S2 |
| Q-D02 | Use real CAD/visual app at qualified codec/display profile; orbit/pan/zoom, type, switch windows, release capture | Physical trackpad and mouse separate; lock/display/headless/focus loss; report actual dropped frames and permissions | P0-S4, P3-S2, P5-S2 |
| Q-D03 | Stage/copy/resume remote file with source version and confirmed publish; compare hash where supported | Competing edit, changed source, invalid/reserved names, symlink, permission loss, canceled commit; no silent overwrite | P3-S3, P6-S1 |
| Q-D04 | Program and verify actual board/probe through qualified topology; trace reset identities and confirmed ownership | Consumer crash, lost link, unexpected identity, competing host, protected release; no automatic flash/reassignment | P0-S5, P3-S4 |
| Q-D05 | Open/edit selected remote document through system provider and confirm backend commit | Offline edits, consumer access pattern, source conflict, grant revoke, process death; preserve both versions/queued state | P5-S3 |
| Q-X01 | Install/run each app independently, hand off registered resource, resolve through receiver's own profile/auth | Missing recipient, caller spoof, expired/malformed envelope, version skew; no secrets/commands transferred | P1-S1/S3/S4, P3-S5 |
| Q-X02 | Interrupt/restart clients/host agent, replay after cursor, deduplicate stable IDs, show retention gaps | Old boot/stream, repeated request ID, expired event/action; no command/approval replay or alert storm | P1-S3, P2-S5, P4, P6-S1 |
| Q-X03 | Exercise whole build/program/inspect journey across network loss and protected operation | Reconcile USB and transfer authority before recovery; no timer-only ownership release or budget-triggered flash interruption | P0-S6, P3-S4/S5, P5-S4, P6-S1 |
| Q-X04 | Compare host-origin events to Android inbox/notifications with host/source attribution and declared fidelity | Denial/revocation, empty listener, loops, old actions, OS/session changes; unknown vs empty preserved | P4-S2/S3/S4 |
| Q-N01 | Sample independent terminal/video/files/USB/idle workloads over known direct/relay topology; compare payload and wire counters | Hidden stream, route switch, metered threshold, parallel bulk upload; label estimates and avoid double counting | P0-S6, P3-S5, P5-S2/S4 |
| Q-A01 | Review both themes/densities; keyboard-only use, screen reader, reduced motion, long/CJK/RTL labels, 100–200% text scale | No focus trap/obscured action/color-only state; trace T-Keyboard_Navigation, T-Hover_Parity, T-Input_Combinations | P0-S3, P1-S2, P2-S2, P6-S4 |
| Q-S01 | Exercise pairing/revoke, registered roots/scopes, key protection, redacted exports, renderer origin/message limits | Invalid caller, stale/oversized request, unknown protocol, hostile output/filename; no secret export or generic execution bridge | P1-S3/S4, P2-S1, P4-S1, P6-S2 |
| Q-R01 | Verify signed install/update, stored-data migration, compatible protocol range, supported rollback, user recovery docs, ten-day candidate use | Interrupted migration, invalid signature/version, unsupported downgrade, critical recurring defects; remain candidate if unresolved | P6-S2/S3/S4 |

## Measurement details

Use a monotonic clock for durations and record wall-clock timestamps for correlation. Note whether RTT/encode/decode/input timing comes from a backend, local instrumentation, or external capture. Hash artifacts with SHA-256 where relevant. Do not expose credentials, private terminal transcripts, or device identifiers in public evidence.

For unsafe-to-interrupt hardware operations, use a controlled test target/topology and a deliberate experiment; never inject loss into a valuable production flash just to satisfy the matrix. This does not waive the ownership/recovery gate; it defines the appropriate test setup.
