# Isolated terminal renderer

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S3, P2-S2.

## Responsibility

Locally bundled selected engine assets, viewport/selection/theme integration, exact IME/keyboard behavior, and narrow typed native messages.

## Boundary

No SSH secrets, arbitrary network pages, generic exec bridge, or unbounded output. External previews open in the normal browser.

## First implementation work

Qualify xterm.js against native alternatives and pin sequence/accessibility/input behavior before adoption.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
