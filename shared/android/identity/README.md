# App-owned identity

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S3, P2-S1.

## Responsibility

Credential handles, known-host records, pairing identities, revocation, protected wrapping/signing adapters, and enrollment state.

## Boundary

Keys/secrets remain in the owning app; raw key material never enters UI bridges, exported profiles, or ordinary logs.

## First implementation work

Qualify Keystore algorithms and selected SSH engine integration rather than assuming hardware-backed direct SSH signing.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
