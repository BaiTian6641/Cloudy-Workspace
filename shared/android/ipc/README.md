# Cross-app resource handoff

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S3/S4, P3-S9; independent-install checks also apply at P3-S5.

## Responsibility

Explicit signature-protected navigation/import routes using versioned host/environment/workspace/resource IDs.

## Boundary

Validate caller/version/expiry and resolve resources on the receiver. Never accept raw commands or shared secrets.

## First implementation work

Exercise missing recipient, stale envelope, spoofed caller, unknown version, and independent install/uninstall.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
