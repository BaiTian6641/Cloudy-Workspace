# P1 — Shared foundation and design

**Status:** planned. **Active effort:** 9–15 person-days before contingency. **Purpose:** Create independent native app shells, shared contracts, and the Cloudy visual system.

The canonical stage data is [backlog.json](../backlog.json). Estimates assume one experienced developer using established components; hardware access and upstream blockers can add elapsed time. Owner labels are roles, not staffed assignments.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P1-S1 | Native app targets and shared-module build foundation | 2–3 | P0-S3 |
| P1-S2 | Cloudy design system and native shell | 4–6 | P1-S1 |
| P1-S3 | Identity, typed contracts, and lifecycle persistence | 2–4 | P1-S1, P0-S2 |
| P1-S4 | CI, dependency decision, and foundation gate | 1–2 | P1-S2, P1-S3 |

## P1-S1 — Native app targets and shared-module build foundation

**Owner:** Android/build developer. **Prerequisites:** P0-S3. **Status:** planned.

Work:

1. Create independent Console/Desktop application IDs, launcher entries, manifests, and debug install paths.
2. Pin JDK/SDK/AGP/Kotlin/Compose/WindowManager and selected engine dependencies with a reproducible wrapper/lock strategy.
3. Create domain/platform/identity/SSH/transfer/IPC/diagnostics module boundaries and isolated renderer assets.
4. Add protocol version constants and fixture loading; enforce dependency direction in the build graph.

Deliverables:

- Two installable debug APKs and toolchain lock record
- Module graph and minimal launch smoke evidence

Exit evidence:

- Each app launches with the other app absent.
- No source module depends on the other app process or UI.

Record evidence under `qualification/results/P1-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01. Official source IDs: G01, G19; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S2 — Cloudy design system and native shell

**Owner:** Design/Android developer. **Prerequisites:** P1-S1. **Status:** planned.

Work:

1. Map canonical tokens to native ColorScheme, typography, spacing, shape, and fixed terminal palette; generate instead of hand-copying values.
2. Implement semantic shared controls, navigation, inspector, menu/dialog/palette, focus, and density contracts.
3. Build native S02/S06 skeletons using synthetic models and guarded caption/window behavior.
4. Review both appearances, long names/CJK/RTL, 200% font scale, narrow windows, touch/keyboard, and actual display scaling.

Deliverables:

- Native Cloudy component gallery and two shell screens
- Design review evidence linked to screen IDs

Exit evidence:

- No system control overlap, focus trap, unreadable semantic pair, or unreachable scaled action.
- Visual choices preserve terminal/file/media working area and clear host context.

Record evidence under `qualification/results/P1-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-A01, Q-P02. Official source IDs: D01, D02, D03, D04, D05, D06, D09, G03; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S3 — Identity, typed contracts, and lifecycle persistence

**Owner:** Platform/security developer. **Prerequisites:** P1-S1, P0-S2. **Status:** planned.

Work:

1. Implement per-app protected credential handles, known-host trust storage, enrollment cancellation, and revocation.
2. Implement signature-protected explicit IPC with bounded versioned resource envelopes, caller validation, expiration, and receiver-side resource resolution.
3. Add persisted session/layout metadata, transfer journals, typed errors, and bounded redacted diagnostics.
4. Implement the qualified foreground/background strategy, timeout handlers, focus-loss cleanup, and reconnect metadata.

Deliverables:

- Identity/IPC/lifecycle library and schema fixtures
- Threat-boundary and denial/revocation evidence

Exit evidence:

- No exported secret/raw command through handoff, logs, or renderer messages.
- Process recreation restores metadata without replaying a side effect.

Record evidence under `qualification/results/P1-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X02, Q-S01. Official source IDs: G15, G16, G17, G18; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S4 — CI, dependency decision, and foundation gate

**Owner:** Maintainer/build developer. **Prerequisites:** P1-S2, P1-S3. **Status:** planned.

Work:

1. Add real Android build/lint/unit/instrumentation checks and contract fixture validation; retain documentation checks.
2. Record dependency versions/licenses/update owners and choose the project distribution/license direction before embedding upstream implementations.
3. Exercise mismatched app/protocol versions, missing recipient, unavailable permissions, and independent install/uninstall.
4. Publish the P1 evidence bundle and refine remaining stage estimates using actual implementation results.

Deliverables:

- Reproducible CI artifacts and dependency register
- Foundation gate record with unresolved issues

Exit evidence:

- Both apps install independently and communicate only through validated resource contracts.
- CI output is labeled separately from physical-device evidence.

Record evidence under `qualification/results/P1-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-S01. Official source IDs: R06, R07, G02; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Every stage must satisfy its exit evidence or record an accepted, qualified fallback before this phase is declared complete. Fix boundary/lifecycle/focus problems before binding real operations; retain proposed status for unqualified framework choices.
