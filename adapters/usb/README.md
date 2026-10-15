# USB ownership adapters

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S5, P3-S4.

## Responsibility

Qualified Android provider/workstation consumer discovery, attach/release/reconcile, identity sets, and protected-operation state.

## Boundary

The backend is the ownership authority. No automatic reassignment/flash retry or lease-timeout assumption.

## First implementation work

Program/verify the actual board/probe and trace reenumeration; qualify a lab-side fallback if needed.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
