# ADR 0002 — Native shell and isolated terminal surface

**Status:** native shell/isolated-engine boundary retained; exact Compose product stack awaiting P1 build review, terminal renderer experimental under [ADR0011](0011-ssh-terminal-investigation.md). **Date:** 2026-10-14.

The supplied plan used Carbon React for the full app shell. The user now requests a Carbon–Material 3 visual synthesis. That visual direction does not require either web framework everywhere. Android windowing, IME, URI grants, accessibility, and lifecycle are core product behaviors.

Propose Kotlin/Compose for shared app chrome and platform workflows, Cloudy semantic tokens for the visual system, a separately evaluated local xterm.js renderer, and native video surfaces. Keep SSH/credentials/operations native and expose a narrow validated renderer contract.

| Alternative | Benefit | Cost / deciding evidence |
| --- | --- | --- |
| Full React/Carbon WebView shell | Existing web components and shared web skills | Bridge/focus/IME/accessibility/lifecycle burden; qualify on device |
| Full native shell and native terminal | Platform integration and one semantics system | Terminal engine feature/input quality must pass |
| Native shell + isolated web terminal | Strong platform boundary with a maintained renderer candidate | Two rendering/semantics systems; boundary and IME need evidence |

Final renderer adoption requires exact input, TUI, accessibility, sustained output and latency evidence. [ADR0011](0011-ssh-terminal-investigation.md) closes the investigation with an unadopted experiment and explicit unmet cases; [ADR0012](0012-foundation-construction-and-platform-acceptance.md) separately admits native app foundation construction. If it fails, replace the renderer or revisit the shell based on measured defects. Do not treat attractive screenshots as an engine decision.

Consequences: native component work replaces assumed Carbon React reuse; tokens unify appearance; terminal protocol and streaming code remain isolated. **Stages:** P0-S3, P1-S1/S2/S3, P2-S2.
