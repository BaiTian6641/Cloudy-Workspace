# Terminal renderer boundary

**State:** construction module for the Console terminal renderer; not a capability or release claim. **Stages:** P2-S2/S6; [Console R1 plan](../../../docs/05-delivery/console-r1-implementation-plan.md).

## Responsibility

Own the local, origin-fenced xterm surface and its bounded native↔renderer protocol. Native SSH ownership, trust, credentials, session lifetimes and terminal semantics stay outside this module; no credential, host key, socket or raw transcript enters the WebView.

- `TerminalFrame` — 48-byte binary envelope with opaque session/attachment UUIDs, type/sequence/length bounds and cookie-free payloads.
- `TerminalFlow` — bounded output credits; exact ordered parser ACK releases bytes; close wakes producers and drain waiters.
- `TerminalWebViewBridge` — local asset loader, HTTPS origin/main-frame allowlist, binary message API, renderer feature checks, one coalesced drain, failure/render-process teardown, explicit close.
- `TerminalIdentity`, `TerminalSize`, `TerminalAdmission` — native/renderer contracts. A native owner must fence stale attachments by identity before I/O and must not treat ACK as paint or latency.

## Assets

Authored page/CSS/bridge live in `src/main/assets/terminal/`. The product core is built from
the exact xterm `6.1.0-beta.315` sources plus the reviewed
[`CompositionHelper` patch](../../../tools/patches/xterm-6.1.0-beta.315-flush-committed-input.patch).
It owns non-composing Android229 changes across both early fallback timers and following controls.
Actual input consumes the change once; blur/composition takeover cancels it, and internal paste/
selection textarea rewrites flush it first. Preedit/finalization stay upstream; no native character
mapper or private runtime monkeypatch is used.
[`renderer-build.json`](renderer-build.json) pins the source archive, patch, esbuild0.28.0 compiler,
entry and final bundle. `@xterm/addon-fit`0.12.0-beta.311 and CSS/license bytes are unchanged.
The product-only [`interaction-assets.json`](interaction-assets.json) also pins MIT
`@xterm/addon-search`0.17.0-beta.310 from the same upstream commit. Search uses its public
literal-search/decoration APIs with a1000-highlight limit; no private buffer-search implementation.
The historical diagnostic still uses its original official prebuilt archive; old evidence is not repinned.

```bash
# Explicit dependency setup; verifies every fetched archive and never executes npm/package scripts.
python3 tools/prepare_terminal_assets.py
python3 tools/build_terminal_renderer.py --fetch-compiler
python3 tools/prepare_terminal_assets.py --fetch-search
# Normal product builds and checks stay offline and fail closed on missing or changed inputs.
python3 tools/prepare_terminal_assets.py --product --offline
python3 tools/prepare_terminal_assets.py --product --check
./gradlew :terminal:assembleDebug :terminal:testDebugUnitTest :terminal:lintDebug \
  --offline --dependency-verification=strict --no-daemon
```

The host-browser regression selects the actual authored product page explicitly; the default
probe surface remains separate. Its [execution record](../../../qualification/results/P2-S2/2026-10-17-terminal-product-browser.md)
preserves the virtual-time driver failure and real-time correction.

```bash
node tests/test_terminal_input_browser.cjs --surface product \
  --assets "$PWD/shared/android/terminal/build/generated/terminalAssets/terminal"
```

The product browser cases cover fast and delayed Android committed input, consecutive CJK commits,
focus-loss cancellation, and paste/selection rewrites that must not turn into DEL or replayed output.
The reviewed source build passes36 real-Chrome cases; this does not replace actual Android/device
input qualification. A changed source/patch/compiler needs an explicit
`python3 tools/build_terminal_renderer.py --candidate --offline` build and review before updating
the output pin; product preparation never falls back to an old/unreviewed renderer.

The build compiler is Linux x86_64 host tooling, not code shipped in the Android APK. Builds on
other host architectures require their own reviewed compiler pin. APK provenance records the
modified source/patch/compiler/output; MIT notices and the separate Microsoft attribution remain bundled.


## Product consumer

`shared/android/platform/CloudyTerminalActivity` hosts the renderer below a native host/status/Disconnect bar, creates `TerminalSession` over `SshAuthenticatedTransport`/`SshOwnedPty`, and fences input/resize/retirement per attempt. Console's real home Connect route opens it only after the profile marker settles. The [emulator workflow](../../../qualification/results/P2-S2/2026-10-17-console-terminal-emulator.md) verifies command/output and cleanup; the [historical construction](../../../qualification/results/P2-S2/2026-10-17-console-terminal-construction.md) preserves the earlier input failure.

## Desktop interaction adapter

Native controls own Android clipboard reads/writes, escaped paste review, Find fields and local focus.
The renderer owns terminal selection, control-key semantics, public paste transformation and the pinned
search addon. Whole clipboard text is limited to60KiB and transformed input to64KiB; oversized actions
are refused whole, never split or automatically submitted. A standard HTML clip is accepted only
through its supplied plain-text alternative; URI/Intent coercion and browser clipboard access stay disabled.

Find is literal and bounded by the2000-line buffer/1000-highlight cap. Clear retires the addon's delayed
refresh through public disposal, so it cannot erase a later user selection. Height-only fit changes
restore only bounded identical selected text using public markers/selection APIs; an unrestorable
selection is visibly cleared instead of becoming a stale copy fallback. Column reflow and physical
input/window-system behavior retain their separate qualification boundaries.

The [interaction record](../../../qualification/results/P2-S2/2026-10-18-console-terminal-interactions.md)
binds real native clipboard/SSH bytes, actual Find/Copy, protected pixels,30 browser cases and10 packaged
WebView methods to exact sources/APKs. Synthetic/component results are not physical-input qualification.


## Explicit limits

The full-size owning-app clipboard/Find journey has scoped emulator evidence; narrow/large-text and broader lifecycle/interoperability cases remain separately tracked in the interaction record. No tabs/splits/layouts, session-linked files pane, product remote-PTY resize acceptance, TalkBack result or sustained-performance claim. Physical keyboard/system IME and spoken-screen-reader qualification remain open; this is not a complete terminal or release claim.
