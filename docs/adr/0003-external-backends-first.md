# ADR 0003 — Established external backends before integration

**Status:** adopted for planning. **Date:** 2026-10-14.

**Amended by [ADR 0006](0006-desktop-integrated-workflows.md):** external-first remains the high-performance streaming/USB qualification strategy. Native RDP/VNC and Console X11 windows are explicit integrated requirements with separate early license/feasibility gates.

Use an ordinary SSH server, the official Tailscale client, Sunshine/Moonlight performance baseline, and VirtualHere USB candidate. Early Desktop orchestrates supported external flows. Do not write codecs, crypto, or USB virtualization drivers.

An installed backend is not evidence for a public automation API. P0 proves launch/profile/control/status mechanisms. Where absent, offer an explicit manual transition. Initial deployment may include more than the two Cloudy packages.

Native high-performance streaming requires a separate license, maintenance, packaging, input, decoder and lifecycle decision at P1-S5 during the foundation phase, based on P0 evidence. Its implementation at P5-S2 depends on native app/profile and engine readiness, independently of optional P4 host adapters. External-only remains a possible explicit scope revision, with unmet native integration recorded and estimates updated. It does not satisfy native RDP/VNC stages. Keep direct media/USB/file traffic off the control-plane connection.

The alternative is immediate deep embedding or custom implementations, which increases maintenance and qualification before demonstrating the user's hardware workflow. Consequences: visible external transitions early, adapter isolation, exact version pinning, and candid dependency setup. **Stages:** P0-S4/S5/S6, P3-S2/S4, P1-S5, P5-S2.
