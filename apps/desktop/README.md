# Cloudy Desktop application

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S1, P3-S1–S5, P5.

## Responsibility

Independent Android package for host workspaces, stream orchestration, full files/transfers, exclusive device ownership, activity, and diagnostics.

## Boundary

Use shared models/tokens and explicit adapters. Do not require Console for desktop/file basics. Planned package ID: `io.github.baitian6641.cloudy.desktop`, subject to distribution review.

## First implementation work

External streaming first; native streaming and document-provider integration have separate evidence/license gates.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
