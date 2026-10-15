# Transfers and staged writes

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P2-S8 shared core; P2-S4/P2-S7 Console integration; P3-S3/P5-S3 Desktop integration. CAP-SSH qualifies transport independently of terminal renderer adoption.

## Responsibility

Journaled transfer jobs, source identity/version checks, staging/cache bounds, resume policy, commit outcomes, and conflict copies.

## Boundary

Bytes sent are not remote commit. Backend rename/cache/provider semantics must be advertised accurately.

## First implementation work

Implement exact size/hash/version fixtures, cancellation cleanup, and process-death recovery without silent overwrite.

Implement the [publication contract](../../../docs/02-architecture/file-publication.md), including consent for every remote overwrite on non-conditional backends, per-save generation binding, exclusive-name collisions, concurrent writers and ambiguous completion. Shared code does not share app credentials or overwrite consent.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
