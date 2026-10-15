# Windows user-session adapter

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S4, P4-S2.

## Responsibility

Session/capture/backend status and permission-aware UserNotificationListener integration where packaging and consent support it.

## Boundary

Record console versus RDP/elevated/locked session behavior. Text/source/dismissal/action fidelity are separate capabilities.

## First implementation work

Qualify manifest/packaging, consent/revocation, listener empty state, background sync, and helper diagnostics.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
