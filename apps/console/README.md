# Cloudy Console application

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S1, P2-S1–S5.

## Responsibility

Independent Android package with window/tab/split state, profile selection, terminal input, durable-session attachment, composer, small attachment uploader, and loopback-forward manager.

## Boundary

Use shared Android services/tokens and the isolated renderer. Do not require Desktop or the custom host agent for SSH. Planned package ID: `io.github.baitian6641.cloudy.console`, subject to distribution review.

## First implementation work

Start with an installable native shell, then trusted SSH and exact PTY/IME behavior before adding layout and agent conveniences.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
