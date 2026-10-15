# Project history

Keep this file as a concise record of accepted decisions and reviewed milestones. Exact file changes and commit identities remain in Git. Detailed evidence belongs in linked run/review records. Do not include credentials, private equipment inventories or raw transcripts.

## 2026-10-14 — Planning foundation and integrated scope

- `e283cac`: established the project foundation, source/API tracking, delivery stages, design reference and planning checks.
- `57ec64a`: refined the desktop-first Googlebook scope and required X11, SFTP/drop, native RDP/VNC and enrolled USB workflows.
- [Revision 02](revision-02-final-review.md) records the user's every-save overwrite choice, independent app releases and early native-stream decision. Its [validation record](../06-validation/revision-02-planning-review.md) describes the planning checks performed at that snapshot.

## 2026-10-15 — Implementation goal and reviewed execution setup

The user activated phase-by-phase implementation, connected the HP Googlebook by USB, required real implementations, stage code reviews, AST/lint checks, helpful comments, reviewed subagent work, official-reference research and timely Git commits/pushes. These rules are recorded in AGENTS and the [engineering workflow](../05-delivery/engineering-workflow.md).

The primary Linux host is this Ubuntu 26.04/Wayland workstation. Windows remains desired with Parsec accepted temporarily; no Windows test target or USB board/probe was supplied. Initial ADB and host observations are recorded in the [baseline](../../qualification/results/P0-S1/2026-10-15-usb-baseline.md). They do not qualify product features.

Independent planning review found a remaining Desktop dependency on Console terminal/pane/file UI. Shared SSH/SFTP core work was separated from that renderer path, with a separate SSH capability and explicit dependency constraints. Existing effort was redistributed rather than counted twice. The current roadmap/backlog supersede the earlier 39-stage snapshot; historical reports remain unchanged.

Official Android skills were acquired at an immutable upstream revision, inspected and documented with project-specific usage limits. Real Python AST/compile and pinned Ruff lint checks were added to CI. The primary agent reviews the subagent changes and records the completed gate in the [execution setup review](../06-validation/2026-10-15-execution-setup-review.md).

This checkpoint keeps all application capabilities pending. Follow [execution state](../05-delivery/execution-state.md) for the next stage and unresolved qualification prerequisites.
