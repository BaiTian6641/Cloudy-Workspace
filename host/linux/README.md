# Linux session/compositor adapter

**State:** planned module boundary; no application/library implementation yet. **Delivery stages:** P0-S4, P4-S3.

## Responsibility

Explicit GNOME/KDE/wlroots/X11/Wayland capability discovery, chosen capture/input path, user-session lifecycle, and supported notification integration.

## Boundary

A D-Bus notification specification is not a portable observer API. Record portal/KMS privilege and lock/login limits.

## First implementation work

Qualify exact compositor/backend/driver combinations rather than a broad Linux support badge.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
