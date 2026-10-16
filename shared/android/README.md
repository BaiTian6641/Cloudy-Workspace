# Shared Android source modules

**State:** real P1-S1 `:domain` and `:platform` implementations, used by the two independent apps; P1-S3 now adds real `:identity` source; other modules remain planned. **Delivery stages:** P1-S1/S3.

## Responsibility

Reusable domain, identity, SSH, transfer, platform, IPC and diagnostics source, plus native X11-window and remote-desktop integration boundaries. Apps compile the modules they need with independent credentials/storage; shared source does not require all engines in both apps.

## Boundary

No shared runtime daemon or credential database is implied. Shared modules do not import app-specific UI/navigation.

## First implementation work

The root six-module build now compiles local-window and app-owned identity source, with bounded tests. See the [native foundation result](../../qualification/results/P1-S1/2026-10-16-native-foundation.md); full stage acceptance remains parent-owned. The [vault increment](../../docs/06-validation/2026-10-16-product-vault-review.md) adds real identity source/storage tests; actual product authentication remains unqualified. SSH, transfer, IPC and remote engine product integration remain planned.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
