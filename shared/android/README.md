# Shared Android source modules

**State:** real P1-S1 `:domain` and `:platform` implementations, used by the two independent apps; other modules remain planned. **Delivery stages:** P1-S1/S3.

## Responsibility

Reusable domain, identity, SSH, transfer, platform, IPC and diagnostics source, plus native X11-window and remote-desktop integration boundaries. Apps compile the modules they need with independent credentials/storage; shared source does not require all engines in both apps.

## Boundary

No shared runtime daemon or credential database is implied. Shared modules do not import app-specific UI/navigation.

## First implementation work

The root five-module build now compiles and executes bounded local-window tests. See the [native foundation result](../../qualification/results/P1-S1/2026-10-16-native-foundation.md); full stage acceptance remains parent-owned. No identity, SSH, transfer, IPC or remote engine implementation is implied by the local-window slice.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
