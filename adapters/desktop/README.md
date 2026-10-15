# Desktop backend adapters

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S4, P3-S2, P5-S1/S2.

## Responsibility

Discover/setup/launch/status operations supported by pinned external clients, then optional maintained native integration.

## Boundary

Do not pretend to control an external stream lifecycle when no supported API exists. Native frames bypass UI bridges.

## First implementation work

Prove external handoff first; license/maintenance and physical input gates precede embedding.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
