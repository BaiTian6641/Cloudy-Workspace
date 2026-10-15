# P1 — Shared foundation and design

**Status:** planned. **Active effort:** 15–24 person-days before contingency. **Purpose:** Establish two independent Android desktop packages, shared design/lifecycle contracts and early engine/distribution decisions, including native streaming at P1-S5.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P1-S1 | Native app targets and shared-module build foundation | 3–4 | P0-S3 |
| P1-S2 | Cloudy design system and native shell | 5–8 | P1-S1 |
| P1-S3 | Identity, typed contracts, and lifecycle persistence | 3–5 | P1-S1, P0-S2 |
| P1-S5 | Early native-stream adoption and distribution decision | 2–3 | P0-S4, P1-S1 |
| P1-S4 | CI, dependency decision, and foundation gate | 2–4 | P1-S2, P1-S3 |

## P1-S1 — Native app targets and shared-module build foundation

**Owner:** Android/build developer. **Prerequisites:** P0-S3. **Status:** planned.

**Required supported capabilities:** CAP-PLATFORM.

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

**Required supported capabilities:** CAP-PLATFORM.

Work:

1. Implement canonical Cloudy tokens and native desktop controls with user font scaling, two themes, density and system caption bounds.
2. Prototype resizable terminal+SFTP arrangements, X11 app/dialog windows, backend profile/trust screens, USB enrollment and diagnostics.
3. Qualify keyboard navigation, shortcut discovery, right-click/hover/cursors, range selection, pointer/touch access and focus ownership on Googlebook.
4. Run narrow tiled/short windows, multi-instance and display-scale changes; preserve desktop workflows without adding phone/tablet navigation.
5. Run the native workspace review with two host contexts, terminal/SFTP, X11 modal, RDP/VNC input release and a device interruption; label synthetic and engine-backed evidence separately.
6. Record Q-A02 shell/geometry findings and feature-specific pending checks before freezing revised interaction components.

Deliverables:

- Native desktop component catalog and S01–S17 interaction review
- Rule 0 and applicable official desktop-quality evidence ledger
- Native workspace review record with accepted geometry/focus decisions and explicit provisional components

Exit evidence:

- No system control overlap, focus trap, unreadable semantic pair, or unreachable scaled action.
- Visual choices preserve terminal/file/media working area and clear host context.
- Freeze only demonstrated native component geometry/interaction; unresolved engine-dependent behavior remains provisional and linked to its feature stage.

Record evidence under `qualification/results/P1-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P02, Q-P03, Q-A01, Q-A02. Official/upstream source IDs: G01, G02, G03, D01, D02, D03, D04, D05, D06, D09; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S3 — Identity, typed contracts, and lifecycle persistence

**Owner:** Platform/security developer. **Prerequisites:** P1-S1, P0-S2. **Status:** planned.

**Required supported capabilities:** CAP-PLATFORM.

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

## P1-S5 — Early native-stream adoption and distribution decision

**Owner:** Maintainer/desktop developer. **Prerequisites:** P0-S4, P1-S1. **Status:** planned.

Work:

1. Review the P0 native-engine feasibility probe and exact upstream/dependency obligations during the foundation phase, before committing the streaming implementation.
2. Resolve the selected project/distribution model, source/notice obligations, maintained-engine ownership, update strategy and reproducible build route without assuming a particular license has already been chosen.
3. Record native surface/input/decoder and backend-version boundaries; estimate remaining integration from the actual probe.
4. Record CAP-STREAM as supported, bounded experiment or blocked. A blocker does not prevent ordinary SSH, RDP or VNC work; an external-only alternative needs an explicit user-visible scope decision.
5. Keep the early external stream lane available while native integration proceeds on its own prerequisites, independently of optional host adapters and event integrations.

Deliverables:

- Early implementation/distribution ADR with selected engine and obligations
- Pinned maintenance/build plan, CAP-STREAM outcome and revised estimate

Exit evidence:

- The adoption investigation has a recorded outcome; only a supported CAP-STREAM outcome can activate native stream implementation.
- No implementation or distribution right is inferred from an external app, and no native-stream scope reduction is made without an explicit decision.

Record evidence under `qualification/results/P1-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-N02. Official/upstream source IDs: R06, R04, R05; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S4 — CI, dependency decision, and foundation gate

**Owner:** Maintainer/build developer. **Prerequisites:** P1-S2, P1-S3. **Status:** planned.

Work:

1. Add real Android build/lint/unit/instrumentation checks and contract fixture validation; retain documentation checks.
2. Record dependency versions/licenses/update owners and choose the project distribution/license direction before embedding upstream implementations.
3. Exercise mismatched app/protocol versions, missing recipient, unavailable permissions, and independent install/uninstall.
4. Publish the P1 evidence bundle and refine remaining stage estimates using actual implementation results.
5. Resolve X server, FreeRDP, VNC engine and transitive native codec/license/security/build obligations before P2/P3 embedding; P5 stream review does not substitute for this gate.
6. Finalize each engine capability outcome after its own feasibility and distribution/build review, including separate CAP-SSH transport and CAP-TERMINAL renderer adoption. A blocked renderer, X11, VNC or USB choice leaves that feature blocked without holding the independently usable foundation or unrelated app lanes.

Deliverables:

- Reproducible CI artifacts and dependency register
- Foundation gate record with unresolved issues

Exit evidence:

- Both apps install independently and communicate only through validated resource contracts.
- CI output is labeled separately from physical-device evidence.
- Every reviewed engine has an explicit scoped readiness outcome; unresolved feature-specific adoption is not hidden by the foundation being done.

Record evidence under `qualification/results/P1-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-S01. Official/upstream source IDs: R06, R07, G02, R20, R22, R25, R26, R32; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Resolve each engine's license/security/build and capability gates before integration. P1-S5 runs early but does not become a prerequisite for unrelated Console/RDP/VNC work. Freeze only demonstrated native UI behavior. Every done stage links actual evidence; a documentation check does not qualify a device.
