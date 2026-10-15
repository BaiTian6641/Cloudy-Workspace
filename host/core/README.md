# Host core

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P4-S1.

## Responsibility

Pairing/authenticated transport, scoped requests, registered resources, expiry/idempotency policy, capability manifests, job supervisor, bounded event retention/cursors/gaps, and redacted logs.

## Boundary

Do not implement OS capture/notification APIs directly in core or automatically replay shell/flash side effects.

## First implementation work

Select Go/toolchain and transport at implementation; schema examples are currently review artifacts only.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
