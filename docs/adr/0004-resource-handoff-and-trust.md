# ADR 0004 — Resource handoff and app-owned trust

**Status:** adopted for planning. **Date:** 2026-10-14.

Cross-app requests carry versioned host/environment/workspace/resource IDs through explicit protected IPC. The receiver resolves allowed resources and checks caller, version, expiry, and authorization. Credentials stay in the owning app; separate enrollment/keys support revocation.

Do not pass executable shell strings, private keys, or local paths that the receiver may interpret in a different OS/environment. Browser links may request navigation only. Continue On is a phone-context reference outside current desktop-only release scope, not this IPC authority model.

Tailnet membership provides a route, while SSH host trust and host-agent pairing establish application identity. The host core authorizes each scoped request and does not replay arbitrary side effects after reconnect.

The alternative is a shared daemon/store with broad command-style links, which creates availability and trust coupling. Consequences: explicit non-secret import, version-skew handling, registered roots/resources, and separate pairing. **Stages:** P1-S3/S4, P3-S9, P4-S1. Standalone package checks also run at P3-S5; cross-app handoff belongs to the separate suite milestone.
