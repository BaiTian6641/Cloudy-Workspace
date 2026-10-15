# Platform and lifecycle adapters

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S2, P1-S2/S3.

## Responsibility

Window insets/instances, input and IME integration, URI grants, permission state, connection lifecycle, timeout, and process recreation wrappers.

## Boundary

Guard version-specific calls and preserve system controls/shortcuts. A foreground service is not an unrestricted keepalive mechanism.

## First implementation work

Use the API tracker and focused device probes before wiring durable product assumptions.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
