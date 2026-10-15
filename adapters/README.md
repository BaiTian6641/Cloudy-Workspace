# External integration boundaries

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S4/S5, P3, P4, P5.

## Responsibility

Version-specific wrappers for desktop backends, USB provider/consumer workflows, and optional structured agent hooks.

## Boundary

A product being installed is not proof of an automation API. Report unsupported control/status and provide an explicit manual path.

## First implementation work

Keep upstream forks isolated and record exact tested versions, rights, update owner, and failure diagnostics.

Read the [module map](../docs/02-architecture/module-boundaries.md), [phase plan](../docs/05-delivery/README.md), and [validation strategy](../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
