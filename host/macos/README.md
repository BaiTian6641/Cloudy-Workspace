# macOS user-session adapter

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S4, P4-S3.

## Responsibility

Capture/input permission state, selected desktop backend integration, own-job events, and supported app integrations.

## Boundary

Do not claim a public universal observer for other apps notifications from the app-scoped notification-center reference.

## First implementation work

Test actual macOS/backend versions, permission changes, lock/headless/display/restart behavior.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
