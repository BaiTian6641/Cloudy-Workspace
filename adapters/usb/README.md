# USB ownership adapters

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S5, P3-S4/S6.

## Responsibility

Qualified Android provider/workstation consumer discovery, enrollment, plug/replug auto-attach, state queries, attach/release/reconcile, reset identity sets and protected-operation state. See the [plug-and-play design](../../docs/02-architecture/usb-plug-and-play.md).

## Boundary

The backend is the ownership authority. Auto-attach only an enrolled device with confirmed idle ownership to its approved host. Unknown/protected ownership prevents reassignment; no flash retry or lease-timeout ownership assumption. Read state around toggle-only backend controls.

## First implementation work

Program/verify the actual board/probe and trace reenumeration; qualify a lab-side fallback if needed.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
