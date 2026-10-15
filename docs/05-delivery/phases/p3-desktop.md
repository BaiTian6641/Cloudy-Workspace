# P3 — Desktop companion release

**Status:** planned. **Active effort:** 28–47 person-days before contingency. **Purpose:** Deliver independently releasable Desktop R2, then test the complete Console/Desktop journey as a separate suite milestone.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P3-S1 | Desktop hosts, workspaces, and capability presentation | 2–3 | P1-S4 |
| P3-S2 | External desktop handoff and backend setup | 2–4 | P3-S1, P0-S4 |
| P3-S3 | File browser, transfer journal, and conflict-safe writes | 3–4 | P3-S1, P2-S8 |
| P3-S4 | USB ownership and protected operation orchestration | 3–5 | P3-S1, P0-S5 |
| P3-S6 | Enrolled USB plug-and-play and reenumeration recovery | 3–6 | P3-S4, P0-S5, P1-S3 |
| P3-S7 | Native integrated RDP client | 6–10 | P3-S1, P0-S4, P1-S4 |
| P3-S8 | Native integrated VNC client | 5–9 | P3-S1, P0-S4, P1-S4 |
| P3-S5 | Independent Desktop companion release gate | 3–5 | P3-S2, P3-S3, P3-S4, P3-S6, P3-S7, P3-S8, P0-S6 |
| P3-S9 | Console/Desktop suite integration milestone | 1 | P2-S5, P3-S5 |

## P3-S1 — Desktop hosts, workspaces, and capability presentation

**Owner:** Desktop developer. **Prerequisites:** P1-S4. **Status:** planned.

Work:

1. Implement manual host/workspace profiles with explicit environment/project roots and non-secret import.
2. Present installed/paired/reachable/permission-required/unknown states without conflation.
3. Add desktop/backend/display/input preference records without claiming an active session.
4. Map S06/S09 navigation and details to typed models with last-observed timestamps.

Deliverables:

- Desktop workspace/profile workflow
- Capability-state UI review

Exit evidence:

- Host, environment, project, and backend identities remain explicit.
- Missing agent/backend produces a supported setup path rather than a false connected state.

Record evidence under `qualification/results/P3-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01. Official/upstream source IDs: G02, R04; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S2 — External desktop handoff and backend setup

**Owner:** Desktop/integration developer. **Prerequisites:** P3-S1, P0-S4. **Status:** planned.

Work:

1. Implement only the supported launch/profile route proved in P0; validate target package/backend version.
2. Provide clear external transition, pairing/setup instructions, and manual-launch fallback when control is unavailable.
3. Persist per-host profile preferences and visible return-to-workspace actions.
4. Test unavailable client, unavailable host, permission denial, cancellation, and stream/client exit.

Deliverables:

- R2 stream launch/return workflow
- Backend setup/error diagnostics

Exit evidence:

- Primary visual application is reachable with documented handoff behavior.
- No unsupported upstream control API is invented or hidden behind a fake live view.

Record evidence under `qualification/results/P3-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-D02. Official/upstream source IDs: R04, R05, R06; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S3 — File browser, transfer journal, and conflict-safe writes

**Owner:** Files developer. **Prerequisites:** P3-S1, P2-S8. **Status:** planned.

Work:

1. Implement distinct local/SFTP/system-Drive-provider roots with supported-operation metadata.
2. Build list/table, selection and details UI; integrate P2-S8 staged copy, journal, resumable transfer and publication states into Desktop cross-root orchestration.
3. Preserve shared-core source-version, destination, conflict and cache constraints across local/SFTP/provider roots; qualify the actual provider-specific semantics.
4. Exercise huge directories, symlinks, case/reserved names, permission failures, source change, cancellation, and interrupted commit.
5. Reuse the independently qualified P2-S8 transfer core with Desktop-owned credentials/jobs; verify that no PTY, terminal renderer, Console package or Console file-pane implementation is required.
6. Present the shared file-publication decision in Desktop: every non-conditional remote overwrite needs an exact file/destination/save-generation choice with save-copy/cancel, no remembered blanket consent and retained pending staging.
7. Repeat the shared-core racing-writer, exclusive-name collision and lost-acknowledgement cases through actual Desktop cross-root flows; do not substitute core-only evidence for app integration evidence.

Deliverables:

- Full file/transfer workflow with state journal
- Conflict/resume/cancel evidence

Exit evidence:

- No silent overwrite or false remote-commit success.
- Unsupported backend semantics are visible and do not silently fall back to destructive behavior.
- Every non-conditional remote overwrite has consent for that file and save generation; known conflicts and ambiguous completion preserve staged work and do not trigger an automatic overwrite retry.

Record evidence under `qualification/results/P3-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D03, Q-F01, Q-F02. Official/upstream source IDs: G13, R14, R15, R34, R35; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S4 — USB ownership and protected operation orchestration

**Owner:** Device/integration developer. **Prerequisites:** P3-S1, P0-S5. **Status:** planned.

**Required supported capabilities:** CAP-USB.

Work:

1. Wrap the qualified provider/consumer control or manual workflow with version-aware capability discovery.
2. Implement backend-confirmed ownership states, explicit attach/release, reset identity set, and protection UI.
3. Reconcile after transport loss, app restart, consumer failure, and conflicting host requests.
4. Expose logs/errors and a deliberate recovery path; never automatically retry programming or assign an unknown device.

Deliverables:

- Device ownership view and reconciliation adapter
- Programming protection/failure evidence

Exit evidence:

- Only a confirmed released device can be reassigned.
- The primary actual probe passes the integrated program/verify journey.

Record evidence under `qualification/results/P3-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D04, Q-X03. Official/upstream source IDs: G14, R07; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S6 — Enrolled USB plug-and-play and reenumeration recovery

**Owner:** USB/device/Android developer. **Prerequisites:** P3-S4, P0-S5, P1-S3. **Status:** planned.

**Required supported capabilities:** CAP-USB.

Work:

1. Implement enrollment for provider/device/serial-or-port identity, approved reset aliases, one consumer and visible reversible auto-attach policy.
2. Observe provider attach/detach and permission status; reconcile consumer state before auto-attaching only known idle devices to the enrolled host.
3. Handle toggle-only backend controls, duplicate events, ambiguous timeout, consumer restart and approved same-owner reenumeration without blind retry.
4. Run at least 30 normal hotplug cycles plus permission, contention, loss and protected-operation failures with exact tool/firmware identities.

Deliverables:

- USB enrollment and quiet plug/replug workflow
- Cycle timing/identity/ownership and permission evidence

Exit evidence:

- Enrolled normal hotplug reaches confirmed ready without repeated Cloudy dialogs where platform grants permit; first-use/OS prompts remain explicit.
- Unknown/protected/competing ownership never causes reassignment or flash replay; published support is device/topology-specific.

Record evidence under `qualification/results/P3-S6/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D04, Q-D06, Q-X03. Official/upstream source IDs: G14, R07, R28; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S7 — Native integrated RDP client

**Owner:** RDP/native/Android developer. **Prerequisites:** P3-S1, P0-S4, P1-S4. **Status:** planned.

**Required supported capabilities:** CAP-RDP.

Work:

1. Embed the qualified maintained RDP engine behind the native surface/input interface with explicit profile, server identity and credential handling.
2. Support verified certificate/NLA authentication and the P1-S3 on-Googlebook vault, desktop keyboard/IME/buttons/wheel, genuinely negotiated relative mouse for qualified 3D, scaling/resize, explicit clipboard policy and local capture release.
3. Expose actual negotiated transport/graphics/input capabilities and recovery; qualify UDP/TCP conditions and Linux/Windows/macOS server-specific host rows. macOS requires a selected compatible RDP server; VNC is a separate profile.
4. Record optional RemoteApp/gateway/redirection support separately; test with Console and the custom host agent absent.

Deliverables:

- Integrated RDP profile/connect/session workflow
- Pinned engine/server/security/input/recovery and performance matrix

Exit evidence:

- A native RDP session passes identity/auth/input/render/recovery cases on the primary supported server; external launch alone is insufficient.
- No silent security downgrade, protocol switch, duplicated input or unsupported session/RemoteApp claim.

Record evidence under `qualification/results/P3-S7/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D07, Q-D09, Q-D02, Q-N02, Q-N03, Q-S01, Q-S02. Official/upstream source IDs: R21, R22, R23, R31, G09, G10; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S8 — Native integrated VNC client

**Owner:** VNC/native/Android developer. **Prerequisites:** P3-S1, P0-S4, P1-S4. **Status:** planned.

**Required supported capabilities:** CAP-VNC.

Work:

1. Embed the license/security-approved RFB client behind the native desktop interface and add explicit encoding/security/server profiles.
2. Support framebuffer/cursor/input, resize if negotiated, clipboard policy and a verified encrypted or authenticated-tunnel route.
3. Bound update/decode memory without discarding dependent protocol state; expose actual encoding/security and server extension limits.
4. Qualify primary VNC servers, malformed/unsupported updates, network recovery and file handoff through independent SFTP where available.

Deliverables:

- Integrated VNC profile/connect/session workflow
- RFB engine/server/encoding/security and latency/recovery matrix

Exit evidence:

- Native VNC desktop/input/recovery works with Console/custom host agent absent on the supported server matrix; external viewer launch alone is insufficient.
- No plaintext/None security downgrade, state-corrupting update drops, or assumed universal file-transfer extension.

Record evidence under `qualification/results/P3-S8/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D08, Q-D02, Q-N02, Q-N03, Q-S01. Official/upstream source IDs: R24, R25, R26, R27, R32, G09, G10; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S5 — Independent Desktop companion release gate

**Owner:** Product/QA owner. **Prerequisites:** P3-S2, P3-S3, P3-S4, P3-S6, P3-S7, P3-S8, P0-S6. **Status:** planned.

Work:

1. Run native RDP and VNC, qualified external stream handoff, files and enrolled USB with Console and the optional Cloudy host agent absent; record any separately required USB consumer adapter.
2. Complete the primary edit/build/program/inspect workflow through the remote desktop and Desktop file/device tools, preserving host/project/device context.
3. Interrupt and restore connectivity without replaying commands or losing ownership accounting; export measured usage and the supported backend/device matrix.
4. Run the app-scoped release checklist for signed Desktop install/update/recovery, dependency/notices, setup/support documentation and at least ten working days of candidate use.
5. Publish the independent R2 candidate decision; Console/X11, optional host events and later native streaming do not block a qualified R2 scope.

Deliverables:

- Independent Desktop functional/quality report and R2 candidate decision
- App-scoped support table, setup/recovery material and dependency installation list

Exit evidence:

- R2 includes native RDP/VNC, files, enrolled USB plug-and-play and honest external performance-stream handoff on qualified combinations, with its own release checklist passed.
- Desktop functions with Console absent and no borrowed credentials/process; a Console-specific X11 blocker cannot hold this gate.

Record evidence under `qualification/results/P3-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X03, Q-N01, Q-D06, Q-D07, Q-D08, Q-N03, Q-R02, Q-R01. Official/upstream source IDs: G02, R08; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S9 — Console/Desktop suite integration milestone

**Owner:** Product/integration QA. **Prerequisites:** P2-S5, P3-S5. **Status:** planned.

Work:

1. Connect/build in Console, upload the artifact, hand off the registered workspace to Desktop and perform visual work.
2. Program/verify the target and inspect output while preserving host/project/device identity and each app's independent credentials.
3. Exercise missing counterpart, version skew and interruption; reconcile transfers/ownership without replaying commands.
4. Record the combined suite result separately from the already independently releasable app candidates.

Deliverables:

- Complete cross-app build/program/inspect report
- Suite handoff/version-skew and context-integrity evidence

Exit evidence:

- Both app candidates pass their own gates and the combined journey passes without sharing credentials or requiring a shared daemon.
- A suite-only failure blocks promotion of that suite integration; it does not revoke an unaffected standalone support claim.

Record evidence under `qualification/results/P3-S9/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X02, Q-X03. Official/upstream source IDs: G02, R08; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

P3-S5 is the independent Desktop R2 gate. P3-S9 is the combined suite milestone and cannot delay an otherwise qualified standalone release. Native RDP/VNC and enrolled USB remain required R2 scope. Every done stage links actual evidence; a documentation check does not qualify a device.

## D15 standard RDP, relative input and credential gate

P3-S7 follows the [desktop RDP interaction contract](../../04-design/desktop-rdp-interaction-contract.md) and [authentication/vault contract](../../02-architecture/rdp-authentication-security.md). Reuse the maintained standard RDP engine and reviewed open-source client patterns. Qualify Linux/Windows/macOS per actual server; retain macOS RDP hosting as unresolved until a maintained compatible server is selected. Windows/macOS absence does not promote them from Linux results.

Add Q-D09 for actual physical capture, negotiated relative deltas,3D behavior and local/focus-loss release; an absolute fallback does not pass it. Add Q-S02 for the P1-S3 vault, local authorization, trust/backup/invalidation and native secret lifetime. Ordinary direct pointer/keyboard/IME must follow desktop interaction with no touch-only prerequisite. P3-S7's existing foundation dependency supplies P1-S3; no new Console/host-agent dependency is introduced. Publish capability limits, including unqualified hardware/host rows. Native RDP and vault gates are still unmet.


The existing effort range remains a provisional planning hypothesis. Re-estimate the relative-input adapter, vault and additional server rows after P0/P1 evidence; no new host or security promise is represented as completed within that range.
