# ADR 0002 — Native shell and isolated terminal surface

**Status:** proposed pending P0-S2/S3. **Date:** 2026-10-14.

The supplied plan used Carbon React for the full app shell. The user now requests a Carbon–Material 3 visual synthesis. That visual direction does not require either web framework everywhere. Android windowing, IME, URI grants, accessibility, and lifecycle are core product behaviors.

Propose Kotlin/Compose for shared app chrome and platform workflows, Cloudy semantic tokens for the visual system, a separately evaluated local xterm.js renderer, and native video surfaces. Keep SSH/credentials/operations native and expose a narrow validated renderer contract.

| Alternative | Benefit | Cost / deciding evidence |
| --- | --- | --- |
| Full React/Carbon WebView shell | Existing web components and shared web skills | Bridge/focus/IME/accessibility/lifecycle burden; qualify on device |
| Full native shell and native terminal | Platform integration and one semantics system | Terminal engine feature/input quality must pass |
| Native shell + isolated web terminal | Strong platform boundary with a maintained renderer candidate | Two rendering/semantics systems; boundary and IME need evidence |

Choose the hybrid proposal only after exact input, TUI, accessibility, sustained output, and latency tests. If it fails, replace the renderer or revisit the shell based on measured defects. Do not treat attractive screenshots as an engine decision.

Consequences: native component work replaces assumed Carbon React reuse; tokens unify appearance; terminal protocol and streaming code remain isolated. **Stages:** P0-S3, P1-S1/S2/S3, P2-S2.
