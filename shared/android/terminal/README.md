# Terminal renderer boundary

**State:** construction module for the Console terminal renderer; not a capability or release claim. **Stages:** P2-S2/S6; [terminal entry plan](../../../.cache/console-terminal-entry-01/entry.md), [Console R1 plan](../../../docs/05-delivery/console-r1-implementation-plan.md).

## Responsibility

Own the local, origin-fenced xterm surface and its bounded native↔renderer protocol. Native SSH ownership, trust, credentials, session lifetimes and terminal semantics stay outside this module; no credential, host key, socket or raw transcript enters the WebView.

- `TerminalFrame` — 48-byte binary envelope with opaque session/attachment UUIDs, type/sequence/length bounds and cookie-free payloads.
- `TerminalFlow` — bounded output credits; exact ordered parser ACK releases bytes; close wakes producers and drain waiters.
- `TerminalWebViewBridge` — local asset loader, HTTPS origin/main-frame allowlist, binary message API, renderer feature checks, one coalesced drain, failure/render-process teardown, explicit close.
- `TerminalIdentity`, `TerminalSize`, `TerminalAdmission` — native/renderer contracts. A native owner must fence stale attachments by identity before I/O and must not treat ACK as paint or latency.

## Assets

Authored page/CSS/bridge live in `src/main/assets/terminal/`. The exact xterm `6.1.0-beta.315` and `@xterm/addon-fit` `0.12.0-beta.311` archives are unpacked only from `.cache/terminal-assets` by `tools/prepare_terminal_assets.py --product --offline` into the module build directory; the build task verifies their hashes and never downloads. License/provenance files are bundled under `third_party_notices/terminal/` and `third_party_notices/upstream/`.

```bash
python3 tools/prepare_terminal_assets.py --product --offline
./gradlew :terminal:assembleDebug :terminal:testDebugUnitTest :terminal:lintDebug \
  --offline --dependency-verification=strict --no-daemon
```

## Explicit limits

No Android `View`/Compose product screen, no session coordinator or SSH authentication, no native menus/clipboard/IME acceptance, no accessibility/TalkBack result, and no performance claim. Diagnostic input/clipboard/XDND behavior from the P0-S3 probe is not product acceptance. See the Console plan for the remaining M3 integration.
