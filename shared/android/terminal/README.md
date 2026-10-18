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
It flushes pending committed Android input before a following Enter/Ctrl+C can clear the textarea.
Composition-start/finalization policy remains upstream; no native character forwarding is added.
[`renderer-build.json`](renderer-build.json) pins the source archive, patch, esbuild0.28.0 compiler,
entry and final bundle. `@xterm/addon-fit`0.12.0-beta.311 and CSS/license bytes are unchanged.
The historical diagnostic still uses its original official prebuilt archive; old evidence is not repinned.

```bash
# Explicit dependency setup; verifies every fetched archive and never executes npm/package scripts.
python3 tools/prepare_terminal_assets.py
python3 tools/build_terminal_renderer.py --fetch-compiler
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

The product browser cases include fast Android `keydown229 → composed insertText → Enter/Ctrl+C`,
which previously emitted only the control byte. The source-built candidate emits the committed
text exactly once before the control. Seven real-Chrome cases pass; this does not replace actual
Android/device input qualification. A changed source/patch/compiler needs an explicit
`python3 tools/build_terminal_renderer.py --candidate --offline` build and review before updating
the output pin; product preparation never falls back to an old/unreviewed renderer.

The build compiler is Linux x86_64 host tooling, not code shipped in the Android APK. Builds on
other host architectures require their own reviewed compiler pin. APK provenance records the
modified source/patch/compiler/output; MIT notices and the separate Microsoft attribution remain bundled.


## Product consumer

`shared/android/platform/CloudyTerminalActivity` hosts the renderer below a native host/status/Disconnect bar, creates `TerminalSession` over `SshAuthenticatedTransport`/`SshOwnedPty`, and fences input/resize/retirement per attempt. Console's real home Connect route opens it only after the profile marker settles. The [emulator workflow](../../../qualification/results/P2-S2/2026-10-17-console-terminal-emulator.md) verifies command/output and cleanup; the [historical construction](../../../qualification/results/P2-S2/2026-10-17-console-terminal-construction.md) preserves the earlier input failure.

## Explicit limits

No tabs/splits/layouts, session-linked files pane, clipboard/menus, resize acceptance, TalkBack result or performance claim. The [committed-input repair](../../../qualification/results/P2-S2/2026-10-17-terminal-input-repair.md) passes three actual Googlebook WebView cases (ASCII, Unicode/LF and terminal controls). Physical keyboard/system-IME and spoken-screen-reader qualification remain open; this is not a complete terminal or release claim.
