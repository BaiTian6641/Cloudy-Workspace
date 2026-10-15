# Optional agent and job integrations

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P2-S5, P4-S4.

## Responsibility

Versioned supported hooks for current job/session state, completion alerts, artifacts, and structured managed tasks.

## Boundary

Ordinary PTY operation remains the baseline. Do not infer approvals/waiting from terminal text or replay old approval keystrokes.

## First implementation work

Record exact tool/auth/schema versions at implementation; keep credentials and runtimes on the execution host.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
