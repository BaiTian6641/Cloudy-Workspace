# Console native terminal interactions — emulator integration

**Work:** 2026-10-17–18. **App:** Console0.1.19/code20, independently installed on the owned emulator without Desktop or the custom Cloudy host agent. **Branch:** `codex/console-r1-implementation`. The separate whole-action PTY repair is published as `842432c14607ae218c3331885c4c69d7ec591378`; the interaction source is a subsequent reviewed increment. **Scope:** verified C05/C13 slices, not complete M3, Q-C02, physical Googlebook support or R1.

## Implemented boundary

- `CloudyTerminalActivity` owns foreground Android clipboard access, native Edit/Find/paste-review controls, public keyboard-shortcut hints, one local interaction surface and attempt/renderer/epoch fences. Already-focused paste stays immediate; a native-surface approval is consumed once after composition and actual renderer-focus confirmation. Focus loss, pause, replacement and retirement drop pending actions, never replay them.
- `TerminalClipboardPolicy` accepts one supplied plain-text representation, including the required alternative in standard Android `newHtmlText` clips. URI/Intent items and coercion/provider fetching are refused. Whole text is validated before admission; previews escape controls/backslashes without splitting a surrogate pair. Copies carry Android's sensitive hint, which is not a secrecy or erasure guarantee.
- `TerminalInteraction`, `TerminalWebViewBridge` and `terminal.js` retain the48-byte identity/sequence envelope. Clipboard text is capped at60KiB; transformed input at64KiB. Public xterm paste handles line endings/bracketing once, appends no Enter, and rejects an oversized transformed action whole.
- Completed pointer selection, explicit Copy, local shortcuts, saved secondary-click policy, remote-mouse Shift override and Ctrl+wheel font zoom have separate ownership. Ordinary terminal control chords remain terminal input. Output, search and programmatic selection never authorize clipboard writes; OSC52 and browser clipboard access remain blocked.
- The pinned public search addon implements literal Find in2000-line scrollback, a1000-highlight cap, next/previous and correlated counts. Clear disposes the addon and its delayed refresh; later Find creates a fresh instance. Native Find reserves a bounded scrollable region rather than covering the matched line.
- Height-only viewport changes preserve only an exact bounded selection through public selection/marker APIs. The normal and alternate buffers and pointer selection are exercised. If trimming or changed contents prevent exact restoration, selection is visibly cleared; no stale-text copy cache or private engine mutation exists.

The0.1.19 app evidence below uses the earlier patched core bundle. MIT `@xterm/addon-search`0.17.0-beta.310 matches core6.1.0-beta.315's upstream commit. Explicit `--fetch-search` acquisition verifies the archive; normal product preparation remains offline. See [R378/R379 and G262](../../../docs/07-reference/sources.json), [API42](../../../docs/03-platform/googlebook/api-tracker.json), and the [terminal module](../../../shared/android/terminal/README.md).

## Actual full-size app workflow

Run **`6df51820-6668-4283-b513-0ae1d377e72e`** passes without temporary prototype diagnostics:

1. Real Home search/selection/Connect and first strict SSH authentication.
2. Independently observed remote command effect and protected terminal pixels.
3. Native Shift+F10 menu focus; local A/Escape handling does not reach the remote PTY.
4. Standard HTML clip's supplied text reaches an escaped multiline preview; Cancel sends no bytes.
5. A single20,000-byte plain-text paste reaches a real SSH/kernel PTY exactly. The independent host witness compares complete bytes/hash and excludes menu/cancelled-preview input.
6. Native Find reports1/1 and the matched line is visibly below the search panel. Select all followed by Edit/Copy copies actual remote output and applies the sensitive hint.
7. Explicit exit, CLOSED state, empty recreated credential drafts, exact logical profile/trust restoration and owned clipboard restoration.

Parent inspected the protected preview/search images; no warning counter or screenshot alone is treated as the workflow proof. Real transport is SSHJ0.41.2 to the finite Apache MINA2.20.0/util-linux2.41.3 fixture. That fixture does not propagate remote window changes, so this is not product remote-PTY resize qualification.

| Artifact | SHA-256 |
| --- | --- |
| Console debug APK | `9084e13bdbca98afd37fe97016df14f9a9c1b8754cc20282d9e524f6d0f48453` |
| Full-size test APK | `0dc6f65ab276d74cf53de37e68f1ade677c2faed8a0a06505e690a5db16d3255` |
| Controller | `8245adbe0a12264b6cbc48971a82b77b95e7a7a8d1505cfebab09302aba52c93` |
| Final WebView component APK | `cf82ddb1661df37cfb02833a5064bd2a4303be1c3d9360183901e2c169163929` |
| Unchanged patched xterm bundle | `e40abf9c26cd201221a1783eb7be1f7c944f57cc5525b67f2ba53ca105b35119` |

Exact app/test/source snapshots are in `.cache/console-interaction-2026-10-17/candidate08-untraced-pass/`; the private run receipt, host capture, phase ledger and images are under `qualification/results/private/console-terminal-resume/6df51820-6668-4283-b513-0ae1d377e72e/`. Earlier snapshots retain their original boundaries.

## Executed checks

| Boundary | Passed | Skipped | Qualification limit |
| --- | ---: | ---: | --- |
| Real Chrome product adapter cases |30|0| Synthetic DOM/native endpoint; actual pinned core/fit/search, not physical input |
| Actual Android WebView methods |10|0| Packaged renderer/public APIs; not a second-app clipboard journey |
| Terminal JVM methods |21|0| Codec/flow/bounds contracts |
| Platform JVM methods |28|0| Native policies and existing platform contracts |
| SSH JVM methods |48|26| Opt-in host cases skipped explicitly, not passed |
| Python asset/source-build cases |46|0| Archive/hash/offline/acquisition policy |

Strict offline Console app/test assembly and Lint pass. Affected Desktop app/test compilation and Lint pass using the explicit verified native reuse declaration, without installing Desktop or running VNC. Lint reports that Java-only `:domain`/`:ssh` are external dependencies; it does not claim to analyze them. Python AST38, Ruff, authored JavaScript syntax and offline asset/provenance checks pass. Schema validation passes in the repository virtual environment; the system Python's missing schema dependency was a preserved setup failure, not an application defect.

The ten-method Android component result was observed in delivered `bg_150` output. Its APK/hash manifest is retained in `.cache/console-interaction-2026-10-17/renderer-final/`; the completed process handle expired before a separate raw-text export, so no retained raw log is claimed. Browser before/after reports and final JVM totals are retained under the same interaction evidence root.

## Review and preserved failures

Nineteen bounded exact `deepseek/deepseek-flash` assignments have received parent review. The last pair reviewed native clipboard/focus/lifecycle and renderer input/search/protocol in parallel, read-only. Parent retained integration, devices/fixtures, all executed checks and Git; worker success statements were not acceptance.

- Native lookup races stopped earlier tests before pointer injection. The driver now uses the visible enabled node it actually observed, not a second racy root lookup.
- `2de99eba…` reached real Paste and exposed the incorrect text/plain-only MIME rule. Android's documented HTML clip alternative repaired it; no HTML parsing was added.
- `d94e3ce1…` passed functionally, but pixels exposed a false touch-mode focus warning and a Find panel hiding its match. Explicit focusable initial actions and a bounded reserved Find region repair those boundaries without a production timing delay.
- `0e501d20…` and `83ae5987…` reached exact paste/visible Find, then Copy had no selection. Exact pinned source and `6a685213…`/`386f1033…` traces show a real row-count resize clearing a successful Select all. Public exact-text preservation fixes it; temporary method wrappers were removed. Browser fail-before/pass-after cases also cover search-refresh retirement, duplicate clear, re-entry, normal/alternate/pointer resize and visible refusal when exact restoration is impossible.
- `46beeb9b…` shows an empty capture-start command after the original host proof. The old driver accepted any DOM input event as a commit witness. It now observes the exact requested edit before Enter; the passing untraced run uses that stricter witness. The earlier event's exact cause remains unclassified; no blanket physical/native-IME reliability claim follows.
- Expired fixture authentication attempts remain distinct from app input defects. Strict-build failures involving imports, plural resources, a scoped layout receiver and a test-only accessibility-action symbol were repaired before the cited checks.

## Subsequent late-input candidate — native qualification pending

Run `19f01d92…` again lost the second committed command even with the exact-edit witness. A new real-Chrome schedule reproduces a concrete engine race: keydown229, an empty fallback timer, then committed input/keyup/Enter emits only CR. This is a source defect, not a reason to weaken the byte assertion.

The authored patch now spans `CompositionHelper.ts`, `CoreBrowserTerminal.ts` and internal `ICompositionHelper`. An empty early timer retains its non-composing baseline; the actual input event or next key consumes it once. Blur and composition takeover cancel that pending owner. Existing preedit/finalization behavior and normal control-key semantics remain upstream. No native character mapper or private runtime mutation was added. LSP references were attempted but no TypeScript server was available; the complete113-file source set was searched and the candidate compiled with the pinned compiler.

The candidate passes33 real-Chrome cases, including delayed input, repeated delayed CJK commits and blur cancellation. Patch SHA `9b643d61303b3dff424c8c250622525634aba52f3d79f5de498f7a0562de5be7`; bundle356395 bytes, SHA `e5abf984f58417b9cf9ae8caa658aac712fe6149ade796dee8348870753ed0ca`. Exact original/patched source and before/after reports are under `.cache/terminal-input-owner-2026-10-18/`. Console source is now0.1.20/code21 and Find has a compact query/action/count layout, but no new Android or native workflow pass is claimed until those changed bytes are exercised. Two additional DeepSeek reviews are active for input ownership and fixture safety.


## Narrow/large-text work and remaining gates

Controlled900×1400 display constraints at240dpi plus font scale2.0 are an emulator layout stress case, not Googlebook tiled-window parity. Initial `949ab432…`/`6202aff0…` runs found the Home driver waiting for an off-screen Connect control before revealing it; the actual screenshot shows the selected host. After explicit reveal, `1ab6c797…` reaches real SSH and exact20,000-byte paste, then exposes off-screen Find feedback. The driver is being completed with public reveal/scroll actions and a platform event-idle barrier before pointer injection. These failed runs retain exact baseline restoration; display size and font scale return to2880×1800/1.0 after each attempt. This paragraph remains an incomplete acceptance gate until the current scaled workflow passes.

The runtime is Android Emulator37.2.12, Google APIs37.0 revision6 x86_64, Android17/API37 fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`, WebView145.0.7632.218. Physical keyboard/trackpad, system IME/composition transitions, TalkBack, unrelated-app clipboard interchange, background/focus-loss breadth, sustained output and real product remote resize remain separately unqualified. Broader authentication/jumps, layouts/tmux, forwards, writable files/edit/drop and product X11/remote VS Code remain in the [unchanged R1 plan](../../../docs/05-delivery/console-r1-implementation-plan.md).

## 2026-10-18 native IME generation-boundary repair and product journey pass

A passive lifecycle trace localized the remaining first-operation class: the framework restarted input to the same editor (`start generation=10 restarting=true field=1`) between the client's state snapshot (generation 9) and request dispatch, so the strict admission fence refused a legitimate operation as `STALE_GENERATION`. Three of four native-suite failures had this exact cause. The qualification driver now requires the published `(instance, generation, field)` tuple to remain unchanged for 750 ms before snapshotting the one-time request; the service keeps strict instance/generation/PID/positive-field/lease/expiry/consumption checks, and no product focus path, retry, dummy edit or warm-up key was added. The malformed-ID retry regression was repaired separately (`32cce90a…`).

- **Plain fixture suite** (`plain-settled-09`, APK `1450c118…`): 4/5. The residual `pointerFocusedEditorReceivesNativeEnter` injects a synthetic mouse down/up and then `sendKeyDownUpSync(Enter)` without using the IME path; it receives no newline. Classified as one remaining driver timing failure, not a product-terminal result.
- **Real-IME renderer suite** (`native-settled-08`, APK `24649c95…`): **11/11 passed**, with exact IME settings/residue restoration. Covered first committed text, native Enter after a delivered commit, Unicode/combining/emoji and composition, control bytes, native paste/search/copy, closed-attachment refusal and retirement.
- **Product journey** (`7a48b117-fd7c-469e-b2c6-280c2dfb77a3`, Console 0.1.20/code21 debug `6a5c4557…`, test APK `dff379b7…`): real Home search/selection/Connect, first authentication, IME-committed command plus native Enter, independently observed host witness `744455a7…`, protected surface capture, 20,000-byte paste byte-matched at the host, interaction schedule, explicit exit/CLOSED, credential-draft recreation, exact logical baseline restoration and cleanup. Fixture: Apache MINA SSHD 2.20.0 loopback, host `127.0.0.1:42053` through owned device reverse `18327`; emulator API37 x86_64, WebView 145.0.7632.218.
- **Emulator incident:** the supervised emulator aborted (SIGABRT in the gfxstream ColorBuffer path) during an earlier journey attempt `9abd9a6e…` whose receipt had reached `TERMINAL_PTY_OPEN`. The restarted device briefly lost the interrupted Console update; the built APK was reinstalled over retained app data. The aborted run's exact IME lease/lock, test-IME enablement and accepted trust pin were reconciled before the passing run; no crash-interrupted result is counted as a pass.
- **Still open:** physical keyboard/trackpad and the Googlebook system IMEs, TalkBack, unrelated-app clipboard interchange, the final narrow/font-scale scene, sustained output/backpressure and actual product remote resize.
