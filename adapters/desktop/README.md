# Desktop backend adapters

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S4, P3-S2/S7/S8, P1-S5, P5-S2.

## Responsibility

Separate external-stream launch and native stream, RDP and VNC engine adapters. Native RDP/VNC are required P3 lanes, while native high-performance streaming has P5 distribution/performance gates. See the [backend design](../../docs/02-architecture/remote-desktop-backends.md).

## Boundary

Do not pretend to control an external stream lifecycle when no supported API exists. Native frames bypass UI bridges.

## First implementation work

Prove external stream handoff and RDP/VNC build/security paths in P0; P1 license/maintenance gates precede RDP/VNC embedding and P5 separately gates native performance streaming.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
