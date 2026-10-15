# SSH and SFTP boundary

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S3, P2-S1/S2/S4.

## Responsibility

Engine adapters for trusted connections, authentication, jump channels, PTYs, resize, SFTP, and local forwarding.

## Boundary

Own crypto through an established maintained engine; keep I/O off the UI thread and use bounded backpressure.

## First implementation work

Select engine through the P0 matrix, then implement cancellable typed operations and verify every hop.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
