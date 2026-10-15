# Console X11 native window integration

**State:** planned module boundary; no implementation. **Stages:** P0-S7, P2-S6.

Own Android task/surface binding, generation-scoped X window records, transient/modal/popup ownership, native input/IME adaptation and host context. A maintained engine in `adapters/x11` owns X protocol/rendering. Pixels never use the terminal WebView bridge. Console can use this without Desktop or the custom host agent.

Read the [X11 design](../../../docs/02-architecture/x11-windows.md) and [module boundaries](../../../docs/02-architecture/module-boundaries.md). Separate native app windows and correct dialogs are the gate; a single X desktop is partial. No root, shared signing identity, private VM APIs or claimed GUI durability after X connection loss.
