# Native desktop client integration boundary

**State:** planned module boundary; no implementation. **Stages:** P0-S4, P3-S7/S8, P5-S2.

Own the typed Desktop profile/capability/lifecycle/native-surface/input boundary shared by independently maintained RDP, VNC and performance-stream adapters. Preserve protocol-specific trust, session, encoding, optional channel and reconnect semantics. No generic JSON media path or silent protocol fallback.

Read the [backend design](../../../docs/02-architecture/remote-desktop-backends.md) and [performance protocol](../../../docs/06-validation/desktop-performance.md). Standard RDP/VNC need compatible servers, not the custom Cloudy host agent. This source boundary does not imply a running shared service between apps.
