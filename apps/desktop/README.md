# Cloudy Desktop application

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S1, P3-S1–S8 (dependency order in backlog), P5.

## Responsibility

Independent Googlebook desktop Android package for host workspaces, native RDP/VNC clients, high-performance streaming, cross-root files/transfers, enrolled USB plug-and-play, activity and diagnostics. Phone/tablet optimization is outside scope under [Rule 0](../../docs/00-project/platform-rule-zero.md).

## Boundary

Use shared models/tokens and explicit adapters. Do not require Console for desktop/file basics. Planned package ID: `io.github.baitian6641.cloudy.desktop`, subject to distribution review.

## First implementation work

External high-performance streaming first, native RDP/VNC in P3, enrolled USB auto-attach after ownership/protection, then native high-performance streaming and document provider after their evidence/license gates.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
