# Native VNC / RFB client engine

**State:** planned adapter boundary; no implementation. **Stages:** P0-S4, P1-S4, P3-S8.

Select a maintained license/security-approved RFB engine; LibVNCClient is a candidate and TigerVNC an interoperability reference. Own security/encoding negotiation, framebuffer state, cursor/input and optional extensions. Do not drop dependent framebuffer updates or assume vendor file-transfer extensions.

Read the [backend design](../../../docs/02-architecture/remote-desktop-backends.md). Require a qualified protected route and explicit trust policy. Native integrated client evidence is required; external viewer launch is partial.
