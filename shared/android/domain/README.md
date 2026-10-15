# Domain models

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S1.

## Responsibility

Immutable host/environment/workspace/resource identifiers, capability states, transfer/device/session state vocabulary, and typed errors.

## Boundary

Keep serialization and UI semantics separate; unknown is distinct from false/unavailable/failed.

## First implementation work

Add version conversion and model fixtures; do not encode OS behavior by machine model name.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
