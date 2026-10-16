# Retained SSH ownership and read-only SFTP qualification

**Date/stage:** 2026-10-16, P1-S3 and bounded CAP-SSH experiment. **Outcome:** scoped host and lower-level Android integration PASS. The [build manifest](2026-10-16-ssh-retained-session-build.json) and [sanitized run manifest](2026-10-16-ssh-retained-session.json) bind source, artifacts and results. This does not complete P1-S3, activate gated P2 stages or qualify full SSH/SFTP products. Installed Console/Desktop remain the accepted0.1.9/code10 finite-authentication builds, stopped and unchanged; new product artifacts are built-only.

## Implemented boundary

The separate retained engine performs actual password authentication, creates an engine-owned proof, promotes that proof through exact current-profile/trust checks, then provides bounded read-only SFTP on the authenticated connection. Its platform handle keeps live invalidation and registry capacity until the exact proof has actually retired. The finite observer and native password-check screens retain their earlier close-on-return behavior. The [contract](../../../docs/02-architecture/ssh-retained-session.md) defines callback binding, deadlines, exact-pin rekey, per-operation limits and actual thread/socket/channel retirement.

Selected SSHJ0.41.2, commit `6fad160fe36605a68847ac8a527750c615b4bd04`, continues negotiation, encoding and cryptography. Cloudy's selected-pin public/protected adapter tracks partial SessionChannel construction and joins the actual SFTP PacketReader. A256KiB input-frame bound precedes upstream allocation; directory entry and complete-file bounds remain separate. No library fork, automatic reconnect, write, command/PTY, local publication or retained product UI is added.

## Strict build and source review

Build01 succeeds in54seconds,322 Gradle tasks with56 executed; other tasks report their actual up-to-date state. Strict dependency verification, Kotlin/Java compilation, both product APKs and platform/product test APKs pass. Console, Desktop, platform and design-system Lint each report **zero findings**. The source manifest remains unchanged during the build. SSH reports31 executed passes plus20 fixture assumption skips, including ten new proof/bounds/framing unit cases and nine unrun-at-build retained-host cases. The separate host execution below runs those nine cases for real. Carried-forward reports contain50 domain,2 platform JVM and4 design cases; they are not extra device tests.

The fixture passes `javac --release17 -Xlint:all -Werror`. Its only closure addition is host-only `sshd-sftp2.20.0`; the [dependency lock](../../fixtures/ssh-session/dependencies.json) records exact binary/source/POM hashes. The SFTP binary matches the already verified official Apache release archive. No Android dependency pin or permission changes.

Direct Codex GPT6.1-Sol coworkers implemented disjoint engine, coordinator and fixture/test areas. An independent reviewer and leader examined source and evidence. Review required an explicit `KEY_REJECTED` assertion in the changed-key host case; the leader also fixed the facade's inactive-operation path to preserve the actual terminal cause instead of generic Closed. The device-runner review found a temporary-route creation/recording gap; recording the attempted route before ADB and exact mapping inspection during cleanup resolves it. Bounded stdin provisioning uses a private temporary file, length check, atomic rename and hash check.

OMP18.7.0 task `SSH-SESSION-SEC-001` used actual `zai/glm-5.3`, zero tools, and completed its contract-only review in70.269seconds. The leader retained useful cancellation/expiry/retirement/memory-bound conditions, corrected assumptions about final deadline checks and intentional quarantine, and rejected unsupported unsafe-publication claims. SSHJ's lossy filename decoding is documented rather than relabelled strict rejection. Private packet, routing/events and leader disposition remain under `.cache/omp-workers/2026-10-16-ssh-session/runtime-01/SSH-SESSION-SEC-001/`. Model output is not a security or device pass.

## Actual maintained-server cases

Host01 uses four isolated loopback **Apache MINA SSHD2.20.0** fixtures from [source](../../fixtures/ssh-session/README.md), exact commit `2221febd396cd3daafdfbf2ff982c6d0eb69a203`. They serve dedicated bounded synthetic files through the actual maintained SFTP subsystem. Random fixture credentials and ephemeral keys remain private; system accounts, PAM and SSH services are unchanged.

| Actual case | Observed requirement |
| --- | --- |
| Sequential list/read, quiet idle, read again | Exact server file bytes and listing, one authentication; quiet3.5s exceeds the3s original authentication budget without closing retention |
| Directory overflow | Server directory exceeds requested entry cap; explicit LIMIT_EXCEEDED, no successful truncated list |
| File overflow | Actual file metadata exceeds requested byte cap; explicit LIMIT_EXCEEDED, no successful prefix |
| Platform declines ownership | Actual authentication occurs, but no transport/channel is published; exact proof/callback retirement completes |
| Wrong fresh key | KEY_REJECTED before credential admission; zero authentication requests |
| Same-key post-authentication rekey | Actual server KEXINIT write, parsed peer proposal, second KeyEstablished, completed future and subsequent real SFTP; one authentication |
| Changed-key post-authentication rekey | Actual replacement-key exchange, explicit client KEY_REJECTED, failed exchange future and closed peer; no second key establishment or credential release |
| Cancellation during held READ | Actual server hold reached; concurrent factory use returns Busy, cancellation returns CANCELLED and owned workers retire |
| Deadline during held READ | Actual server hold reached; finite operation returns TIMED_OUT, retires and does not reconnect |

**9/9 tests pass, zero skips/failures/errors.** Four fixtures total nine connections/eight authentications/ten SFTP channels. All four processes exit0 with server and subsystem workers retired, listeners closed, no overflow/I/O failure or cleanup error. Actual socket/Reader/PacketReader/operation/owner/notification retirement is separately asserted by the client; server closure alone does not establish it. The fixture source/class bindings and all26 host-run source hashes match current source. Private raw evidence: `qualification/results/private/foundation/2026-10-16-ssh-session-host-01/`.

## Actual Googlebook integration

Target: **HP Googlebook14, Android17/API37, arm64-v8a, build CL3B.260622.276.R2, incremental16560795**. The separately installed platform test APK has SHA256 `2c3c7b55f6d56e82bdecae06f8640ca8ab44e013eae6a3ab787109f17be7457d`. It uses internal exclusive SQLite roots configured for the Console owner; it does not operate either installed product's stores or certify both product UI integrations.

- **61 policy/storage cases pass:**13 existing coordinator,9 new established-ownership policy,19 profile and20 trust cases. The nine new policy cases deliberately construct reflected simulated proofs to exercise expiry/promotion/capacity ordering; they are not real authentication evidence.
- **Three default skips** occur before private-file, store or engine access when no explicit run argument exists.
- **Three separate real engine-proof cases pass:** retained list/read/read followed by trust revocation; actual file read followed by profile editing and refusal of another operation; actual authentication followed by a profile edit before promotion, returning CANCELLED without a published transport/channel.

The three real cases connect through one temporary USB ADB loopback mapping to a fresh maintained fixture. Generated credentials arrive only through non-PTY stdin into UID-owned0700 directories/0600 JSON. Host evidence records3 ordinary password authentications,4 SFTP channels,3 closed sessions, server and SFTP-worker retirement. Each test proves actual proof/transport retirement and registry removal before removing its exact isolated root. Custom phase status messages are excluded from JUnit counts.

The actual installed product APK hashes, profile/trust database hashes, vault receipts, display preferences, effective/system display settings, boot ID and boot count match before/after exactly. All test packages are absent, the listener is closed, the prior reverse set is restored, and the exact generated configuration leaf is removed. No product installation, personal credential, vault grant, screen-lock prompt or system settings change occurred. All163 build source bindings, the test APK and fixture source/classes match. Private raw evidence: `qualification/results/private/foundation/2026-10-16-ssh-session-device-01/`.

## Limits and next increment

This is a lower-level native consumer and controlled-server result. It does not establish native session UI/lifecycle, process death/background retention, physical input, ordinary LAN/hostname routing, general server interoperability or full product adoption. The exact post-auth rekey evidence is host JVM evidence; the Android cases exercise real SFTP and mutation-triggered retirement without rekey. Earlier pre-authentication rekey timeout evidence remains unchanged.

File-overflow evidence here takes the metadata-bound path; the unit/source checks do not become adversarial-server or exhaustive protocol fuzzing. The facade reads to EOF using a cumulative bound, but does not promise a snapshot when a remote file changes. The frame guard is not a total-process-memory bound. Decoded filename validation does not establish strict malformed-UTF8 rejection or arbitrary non-UTF8 round-trip. Remote names are not trusted local destinations.

Next integrate a real owning foreground session caller with stable factory/cancellation, explicit connection/retirement state and native file presentation, retaining the existing stage/capability prerequisites. Saved-vault lease/account binding, other authentication methods, jump hops, command/PTY/terminal, upload/download publication/edit/drop, X11 and broader lifecycle remain required. Reuse unchanged upstream and closed probe evidence; do not repeat the old finite authentication cases without a changed risk.
