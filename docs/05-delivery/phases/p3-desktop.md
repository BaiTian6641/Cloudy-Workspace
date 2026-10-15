# P3 — Desktop companion release

**Status:** planned. **Active effort:** 27–46 person-days before contingency. **Purpose:** Deliver a desktop companion with native RDP/VNC, high-performance external stream handoff, shared files and enrolled USB plug-and-play.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Estimates are active effort, not elapsed time or staffing commitments; re-estimate after P0. Stage IDs remain stable; the dependency order can place a later-numbered addition before an existing release gate.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P3-S1 | Desktop hosts, workspaces, and capability presentation | 2–3 | P1-S4 |
| P3-S2 | External desktop handoff and backend setup | 2–4 | P3-S1, P0-S4 |
| P3-S3 | File browser, transfer journal, and conflict-safe writes | 4–6 | P3-S1, P2-S7 |
| P3-S4 | USB ownership and protected operation orchestration | 3–5 | P3-S1, P0-S5 |
| P3-S6 | Enrolled USB plug-and-play and reenumeration recovery | 3–6 | P3-S4, P0-S5, P1-S3 |
| P3-S7 | Native integrated RDP client | 6–10 | P3-S1, P0-S4, P1-S4 |
| P3-S8 | Native integrated VNC client | 5–9 | P3-S1, P0-S4, P1-S4 |
| P3-S5 | Console/Desktop vertical slice and companion gate | 2–3 | P3-S2, P3-S3, P3-S4, P3-S6, P3-S7, P3-S8, P2-S5, P0-S6 |

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

**Owner:** Files developer. **Prerequisites:** P3-S1, P2-S7. **Status:** planned.

Work:

1. Implement distinct local/SFTP/system-Drive-provider roots with supported-operation metadata.
2. Build list/table, selection, details, staged copy, journal, resumable transfer, and commit states.
3. Bind resume to source version, validate destination, preserve conflict copies, and bound caches.
4. Exercise huge directories, symlinks, case/reserved names, permission failures, source change, cancellation, and interrupted commit.
5. Reuse the Console-qualified file/transfer components with independent Desktop credentials/jobs; distinguish cross-root orchestration from session-level file context.

Deliverables:

- Full file/transfer workflow with state journal
- Conflict/resume/cancel evidence

Exit evidence:

- No silent overwrite or false remote-commit success.
- Unsupported backend semantics are visible and do not silently fall back to destructive behavior.

Record evidence under `qualification/results/P3-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D03. Official/upstream source IDs: G13, R14, R15; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S4 — USB ownership and protected operation orchestration

**Owner:** Device/integration developer. **Prerequisites:** P3-S1, P0-S5. **Status:** planned.

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

Work:

1. Embed the qualified maintained RDP engine behind the native surface/input interface with explicit profile, server identity and credential handling.
2. Support required certificate/NLA authentication, desktop rendering, keyboard/IME, pointer/wheel, scaling/resize, clipboard policy and capture release.
3. Expose negotiated transport/graphics/capabilities and connection/session recovery; qualify UDP/TCP conditions and primary Windows/compatible server behavior.
4. Record optional RemoteApp/gateway/redirection support separately; test with Console and the custom host agent absent.

Deliverables:

- Integrated RDP profile/connect/session workflow
- Pinned engine/server/security/input/recovery and performance matrix

Exit evidence:

- A native RDP session passes identity/auth/input/render/recovery cases on the primary supported server; external launch alone is insufficient.
- No silent security downgrade, protocol switch, duplicated input or unsupported session/RemoteApp claim.

Record evidence under `qualification/results/P3-S7/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D07, Q-D02, Q-N02, Q-N03, Q-S01. Official/upstream source IDs: R21, R22, R23, R31, G09, G10; resolve them in the [source register](../../07-reference/source-register.md).

## P3-S8 — Native integrated VNC client

**Owner:** VNC/native/Android developer. **Prerequisites:** P3-S1, P0-S4, P1-S4. **Status:** planned.

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

## P3-S5 — Console/Desktop vertical slice and companion gate

**Owner:** Product/QA owner. **Prerequisites:** P3-S2, P3-S3, P3-S4, P3-S6, P3-S7, P3-S8, P2-S5, P0-S6. **Status:** planned.

Work:

1. Connect/build in Console, upload an artifact, open the same registered workspace in Desktop, and start visual work.
2. Program/verify the attached target, inspect output, and preserve exact host/project/device context.
3. Interrupt/restore connectivity without replaying commands or losing ownership accounting.
4. Export a measured usage report and publish R2 limitations/dependency installations.
5. Run native RDP and VNC independently, then the enrolled USB plug/build/program/inspect path; document capability differences and partial milestones.

Deliverables:

- First complete real-project vertical-slice report
- R2 companion release candidate/support table

Exit evidence:

- R2 includes working native RDP/VNC, files and enrolled USB plug-and-play on qualified combinations plus honest high-performance stream handoff.
- No app borrows another app credential/process; protected ownership and file/source context remain correct across handoff.

Record evidence under `qualification/results/P3-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X02, Q-X03, Q-N01, Q-D06, Q-D07, Q-D08, Q-N03. Official/upstream source IDs: G02, R08; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Native RDP/VNC and enrolled USB plug-and-play are full R2 gates. External viewers/manual device attach may help diagnosis but remain partial when the required integrated behavior is unmet. Every done stage links actual evidence; a documentation check does not qualify a device.
