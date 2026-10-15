# Shared Android source modules

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P1-S1/S3.

## Responsibility

Reusable domain, identity, SSH, transfer, platform, IPC and diagnostics source, plus native X11-window and remote-desktop integration boundaries. Apps compile the modules they need with independent credentials/storage; shared source does not require all engines in both apps.

## Boundary

No shared runtime daemon or credential database is implied. Shared modules do not import app-specific UI/navigation.

## First implementation work

Create actual build modules after P0 selects versions; see each subdirectory for its ownership boundary.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
