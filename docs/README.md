# Documentation map

The numbered folders describe one development program. Read project decisions before implementation details, and use the phase files to find executable next steps.

| Area | Key documents |
| --- | --- |
| Project | [Rule 0](00-project/platform-rule-zero.md), [scope](00-project/vision-scope.md), [intake review](00-project/plan-review.md), [Revision 01](00-project/revision-01-integrated-workflows.md), [Revision 02 decisions](00-project/revision-02-final-review.md), [repository map](00-project/repository-map.md), [risks](00-project/decisions-risks.md) |
| Product | [Console](01-product/console.md), [Desktop](01-product/desktop.md), [journeys](01-product/journeys.md), [reference products](01-product/reference-products.md) |
| Architecture | [System](02-architecture/system.md), [module boundaries](02-architecture/module-boundaries.md), [contracts](02-architecture/contracts.md), [security/lifecycle](02-architecture/security-and-lifecycle.md), [network/data](02-architecture/network-and-data.md) |
| Integrated workflows | [X11 app windows](02-architecture/x11-windows.md), [Console SFTP/drop](02-architecture/console-files.md), [file publication](02-architecture/file-publication.md), [native desktop backends](02-architecture/remote-desktop-backends.md), [USB plug-and-play](02-architecture/usb-plug-and-play.md) |
| Platform | [Googlebook overview](03-platform/googlebook/overview.md), [desktop application contract](03-platform/googlebook/desktop-application-contract.md), [setup](03-platform/googlebook/development-setup.md), [API catalog](03-platform/googlebook/api-catalog.md), [open questions](03-platform/googlebook/open-questions.md), [host adapters](03-platform/host-integrations.md), [dependency review](03-platform/dependency-licensing.md) |
| Design | [Handbook](04-design/README.md), [principles](04-design/principles.md), [foundations](04-design/foundations.md), [layout](04-design/layout.md), [components](04-design/components.md), [interaction](04-design/interaction.md), [screens](04-design/screen-specs.md), [accessibility](04-design/accessibility.md), [implementation](04-design/implementation-map.md), [native workspace review](04-design/native-workspace-review.md) |
| Execution | [Engineering workflow](05-delivery/engineering-workflow.md), [current state](05-delivery/execution-state.md), [history](00-project/history.md), [reviewed skills](07-reference/development-skills.md), [setup review](06-validation/2026-10-15-execution-setup-review.md) |
| Delivery | [Roadmap](05-delivery/roadmap.md), [phase index](05-delivery/README.md), [machine-readable backlog](05-delivery/backlog.json), [capability outcomes](05-delivery/feasibility-gates.md), [independent release gates](05-delivery/release-gates.md) |
| Validation | [Strategy](06-validation/strategy.md), [latency/stability protocol](06-validation/desktop-performance.md), [compatibility](06-validation/compatibility-matrix.md), [test cases](06-validation/test-cases.md), [run template](06-validation/templates/run.md), [initial planning checks](06-validation/planning-review.md), [Revision 01 checks](06-validation/revision-01-planning-review.md), [Revision 02 checks](06-validation/revision-02-planning-review.md) |
| Reference | [Source register](07-reference/source-register.md), [source data](07-reference/sources.json), [watchlist](07-reference/watchlist.md) |
| Decisions | [ADR index](adr/README.md) |
| Intake | [Original plan and provenance](intake/README.md) |

The [desktop workflow design](04-design/desktop-workflows.md) expands the visual system for S13–S17; the offline specimen remains the initial subset. Cross-links are repository-relative for GitHub/local use. P0-S1 qualification setup is **complete**, with a real diagnostic APK, device run and explicit inventory gaps; P0-S2 investigation is complete with CAP-PLATFORM in a bounded experiment; P0-S3 SSH/terminal qualification is in progress. Console, Desktop and host implementation remain planned. See the [setup closeout](../qualification/results/P0-S1/2026-10-15-setup-closeout.md).

## Active qualification references

- [P0 diagnostic toolchain](03-platform/googlebook/probe-toolchain.md) and [real device-baseline probe](../qualification/probes/device-baseline/README.md).
- [P0-S2 platform decision](03-platform/googlebook/p0-s2-platform-decision.md) and [operation lifetimes](03-platform/googlebook/p0-s2-operation-lifetimes.md): accepted finite UIDT/window evidence, unresolved continuous-session classification and required physical-input/backend cases.
- [P0-S3 SSH/SFTP engine brief](03-platform/googlebook/p0-s3-ssh-engine-qualification.md) and [real host preflight](../qualification/results/P0-S3/2026-10-15-ssh-host-preflight.md).
- Actual [SSH/SFTP](../qualification/results/P0-S3/2026-10-15-ssh-sftp-probe.md), [exec/PTY ownership](../qualification/results/P0-S3/2026-10-15-ssh-pty-probe.md), [archive/restoration](../qualification/results/P0-S3/2026-10-15-ssh-batch-rotation.md) and [lifecycle/recovery](../qualification/results/P0-S3/2026-10-15-ssh-lifecycle.md) evidence; the [real terminal increment](03-platform/googlebook/p0-s3-terminal-renderer-increment.md) is the next implementation.
- [Primary Linux host prerequisites](03-platform/linux-primary-host-qualification.md).
- [Sunshine release/master source divergence](07-reference/2026-10-15-sunshine-source-drift.md).
