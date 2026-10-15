# P1 — Shared foundation and design

**Status:** planned. **Active effort:** 13–21 person-days before contingency. **Purpose:** Establish two independent Android desktop packages, shared native design/input/lifecycle contracts and licensed engine boundaries under Rule 0.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Estimates are active effort, not elapsed time or staffing commitments; re-estimate after P0. Stage IDs remain stable; the dependency order can place a later-numbered addition before an existing release gate.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P1-S1 | Native app targets and shared-module build foundation | 3–4 | P0-S3 |
| P1-S2 | Cloudy design system and native shell | 5–8 | P1-S1 |
| P1-S3 | Identity, typed contracts, and lifecycle persistence | 3–5 | P1-S1, P0-S2 |
| P1-S4 | CI, dependency decision, and foundation gate | 2–4 | P1-S2, P1-S3 |

## P1-S1 — Native app targets and shared-module build foundation

**Owner:** Android/build developer. **Prerequisites:** P0-S3. **Status:** planned.

Work:

1. Establish two independently installable signed development APK targets, shared source boundaries and reproducible Android/NDK toolchains selected from P0.
2. Apply Rule 0: desktop/laptop Googlebook target, separate package IDs/app data, no phone/tablet UI workstream; document manifest/ABI/minSdk/targetSdk and channel assumptions.
3. Build native shells with per-window context and real lifecycle state; keep baseline SSH/RDP/VNC independent of the custom host agent.
4. Document APK installation versus AAB publishing and verify package install/update/uninstall using authorized Android tooling.

Deliverables:

- Two installable debug APKs and toolchain lock record
- Module graph and minimal launch smoke evidence

Exit evidence:

- Each app launches with the other app absent.
- No source module depends on the other app process or UI.

Record evidence under `qualification/results/P1-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-P03. Official/upstream source IDs: G01, G02, G31, G32; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S2 — Cloudy design system and native shell

**Owner:** Design/Android developer. **Prerequisites:** P1-S1. **Status:** planned.

Work:

1. Implement canonical Cloudy tokens and native desktop controls with user font scaling, two themes, density and system caption bounds.
2. Prototype resizable terminal+SFTP arrangements, X11 app/dialog windows, backend profile/trust screens, USB enrollment and diagnostics.
3. Qualify keyboard navigation, shortcut discovery, right-click/hover/cursors, range selection, pointer/touch access and focus ownership on Googlebook.
4. Run narrow tiled/short windows, multi-instance and display-scale changes; preserve desktop workflows without adding phone/tablet navigation.

Deliverables:

- Native desktop component catalog and S01–S17 interaction review
- Rule 0 and applicable official desktop-quality evidence ledger

Exit evidence:

- No system control overlap, focus trap, unreadable semantic pair, or unreachable scaled action.
- Visual choices preserve terminal/file/media working area and clear host context.

Record evidence under `qualification/results/P1-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P02, Q-P03, Q-A01. Official/upstream source IDs: G01, G02, G03, D01, D02, D03, D04, D05, D06, D09; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S3 — Identity, typed contracts, and lifecycle persistence

**Owner:** Platform/security developer. **Prerequisites:** P1-S1, P0-S2. **Status:** planned.

Work:

1. Implement per-app protected credential handles, known-host trust storage, enrollment cancellation, and revocation.
2. Implement signature-protected explicit IPC with bounded versioned resource envelopes, caller validation, expiration, and receiver-side resource resolution.
3. Add persisted session/layout metadata, transfer journals, typed errors, and bounded redacted diagnostics.
4. Implement the qualified foreground/background strategy, timeout handlers, focus-loss cleanup, and reconnect metadata.
5. Define X11 broker/SSH/window lifetimes, URI staging grants, per-backend client ownership and USB enrollment restoration without a shared immortal daemon.

Deliverables:

- Identity/IPC/lifecycle library and schema fixtures
- Threat-boundary and denial/revocation evidence

Exit evidence:

- No exported secret/raw command through handoff, logs, or renderer messages.
- Process recreation restores metadata without replaying a side effect.

Record evidence under `qualification/results/P1-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X02, Q-S01. Official/upstream source IDs: G15, G16, G17, G18; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S4 — CI, dependency decision, and foundation gate

**Owner:** Maintainer/build developer. **Prerequisites:** P1-S2, P1-S3. **Status:** planned.

Work:

1. Add real Android build/lint/unit/instrumentation checks and contract fixture validation; retain documentation checks.
2. Record dependency versions/licenses/update owners and choose the project distribution/license direction before embedding upstream implementations.
3. Exercise mismatched app/protocol versions, missing recipient, unavailable permissions, and independent install/uninstall.
4. Publish the P1 evidence bundle and refine remaining stage estimates using actual implementation results.
5. Resolve X server, FreeRDP, VNC engine and transitive native codec/license/security/build obligations before P2/P3 embedding; P5 stream review does not substitute for this gate.

Deliverables:

- Reproducible CI artifacts and dependency register
- Foundation gate record with unresolved issues

Exit evidence:

- Both apps install independently and communicate only through validated resource contracts.
- CI output is labeled separately from physical-device evidence.

Record evidence under `qualification/results/P1-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-S01. Official/upstream source IDs: R06, R07, G02, R20, R22, R25, R26, R32; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Do not embed an engine before its license/security/build gate. A layout failure is resolved for Googlebook desktop use; it does not open a phone/tablet redesign workstream. Every done stage links actual evidence; a documentation check does not qualify a device.
