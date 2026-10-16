# Optional host integration

**State:** HOST-EXP01 local diagnostic implemented in `core`; paired service and OS adapters remain planned. **Delivery stages:** P4. See the [construction entry](../docs/03-platform/host-core-entry.md) and [parallel client/host mandate](../docs/05-delivery/parallel-execution-contract.md).

## Responsibility

Separately versioned host core plus OS/user-session adapters for capabilities, pairing, managed jobs, events, and supported backend orchestration.

## Boundary

Ordinary Console SSH does not depend on this agent. Media/USB/bulk file data stay on direct backends.

## First implementation work

Continue genuine identity/session observation, owned jobs and event persistence in bounded packets. Separate service, user-session helper, and any narrowly justified privileged helper. The local diagnostic supplies none of the remote-control/service authority by itself. The [retained Linux run owner](../docs/02-architecture/host-managed-run.md) now composes actual jobs/recorder/event storage as an internal library, with [reviewed composite pure-check evidence](../qualification/results/P4-S1/2026-10-16-host-managed-run.md). It retains uncertain jobs and separates process retirement from persistence; native managedrun, service lifetime and authenticated remote admission remain open.

Read the [module map](../docs/02-architecture/module-boundaries.md), [phase plan](../docs/05-delivery/README.md), and [validation strategy](../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
