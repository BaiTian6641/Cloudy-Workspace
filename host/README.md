# Optional host integration

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P4.

## Responsibility

Separately versioned host core plus OS/user-session adapters for capabilities, pairing, managed jobs, events, and supported backend orchestration.

## Boundary

Ordinary Console SSH does not depend on this agent. Media/USB/bulk file data stay on direct backends.

## First implementation work

Separate service, user-session helper, and any narrowly justified privileged helper.

Read the [module map](../docs/02-architecture/module-boundaries.md), [phase plan](../docs/05-delivery/README.md), and [validation strategy](../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
