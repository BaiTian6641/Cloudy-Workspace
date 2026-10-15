# ADR 0001 — Two independent applications

**Status:** adopted for planning. **Date:** 2026-10-14.

Console work should remain usable during video/device failures and on constrained links. Desktop work needs capture/input/backend/device capabilities that ordinary SSH does not. The user's current request explicitly separates these experiences.

Build Cloudy Console and Cloudy Desktop as independent Android packages. Share source libraries, design tokens, and versioned non-secret resource contracts. Keep credentials and runtime lifecycle app-owned. Console requires neither Desktop nor the custom host agent for ordinary SSH. Desktop does not require Console for its file/desktop basics.

The alternative is one app with two modes, which simplifies packaging but couples lifecycle/permissions and independent windows. A shared credential daemon could reduce duplicate enrollment but adds availability and trust complexity. Neither is selected for v1.

Consequences: duplicate some visible attachment-transfer functionality using shared implementation; support missing counterpart/version skew; explicitly import non-secret profiles; qualify independent install/uninstall. **Stages:** P1-S1/S3/S4, P3-S5.

[ADR 0007](0007-publication-and-independent-delivery.md) extends independence to delivery: P2-S5 and P3-S5 are separate app release gates, with P3-S9 for the combined journey. Each app passes its own release hygiene and sustained-use checks.
