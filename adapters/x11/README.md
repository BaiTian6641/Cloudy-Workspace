# Local X server engine adapter

**State:** planned module boundary; no implementation. **Stages:** P0-S7, P1-S4, P2-S6.

Investigate maintained Android-capable X servers, with Termux:X11 as a feasibility reference. Pin source/build/license/security and prove native top-level metadata/rendering hooks for the Android window broker. The existence of an Android X server does not prove per-application Googlebook windows or generic embedding rights.

Expose bounded native X connections, authentication/cookie policy and window lifecycle hooks without general UI bridges. Read the [X11 specification](../../docs/02-architecture/x11-windows.md). Do not commit downloaded upstream implementations to this scaffold.
