# Console terminal source repair and real SSH emulator workflow — 2026-10-17

**Status:** verified bounded product workflow; M3/P2-S2 and R1 remain incomplete. **Scope:** C04/C05 construction, with native profile/trust/password admission and actual SSH. **Source:** `31ac4a4` plus the reviewed integration and source-build changes committed with this record. The user's current direction is emulator-first functionality/UX development, followed by separate actual Googlebook acceptance.

## Confirmed failure and source repair

The [native viewport repair](2026-10-17-terminal-viewport-repair.md) fixed a separate zero-height CSS viewport. The remaining actual Android event trace then exposed:

```text
keydown 229, textarea empty
composed insertText, committed command present
keyup 229
Enter before the deferred textarea fallback runs
textarea cleared; only CR reaches the remote shell
```

The selected xterm6.1.0-beta.315 `CompositionHelper` defers non-composing key229 input. Fast Enter/Ctrl+C cleared that pending text before its timer consumed it. The parent reproduced the exact ordering against the real bundle in Chrome: Enter produced only `0d`, interrupt only `03`; preceding committed text was lost.

The reviewed authored source patch flushes a pending fallback before a non229 key **only when neither composing nor finalizing composition**, cancels its timer and clears its retained baseline on consumption. Parent review removed a proposed composition-start flush because it did not prove committed-text ownership. Composition-start/finalization and unrelated replacement policy remain upstream. No native character mapper, textarea-diff adapter, private runtime monkeypatch, synthesized paste or arbitrary key delay was added.

- Original source: xterm6.1.0-beta.315, commit `991857a1b1cacf06f9531ba74a6e927a7a6398bb`.
- Authored patch: `tools/patches/xterm-6.1.0-beta.315-flush-committed-input.patch`, SHA-256 **`50b12b541ddff267ba11d901c554caffc2889c8d670e1ad3f09a9ac82982e124`**.
- Build-only compiler: exact esbuild0.28.0 Linux x86_64 native binary, SHA-256 **`aafacdf135322bf47c882a4ea4db33d0375583f5b9c3fd2d4e12258e470568be`**.
- Product bundle: **355781 bytes**, SHA-256 **`e40abf9c26cd201221a1783eb7be1f7c944f57cc5525b67f2ba53ca105b35119`**.

[`renderer-build.json`](../../../shared/android/terminal/renderer-build.json) binds source/archive, patch, compiler, entry and exact output. The build reuses the existing bounded archive helpers and strict patch replay, executes no npm/package scripts, and uses an isolated fixed compiler environment. Normal build/check are offline; only explicit `--fetch-compiler` setup may fetch the pinned tool. Corrupt caches are not silently replaced. Product preparation has no old-renderer fallback. The historical diagnostic retains its original vendor bytes and evidence.

Sources: [exact CompositionHelper](https://github.com/xtermjs/xterm.js/blob/991857a1b1cacf06f9531ba74a6e927a7a6398bb/src/browser/input/CompositionHelper.ts), [exact terminal key handling](https://github.com/xtermjs/xterm.js/blob/991857a1b1cacf06f9531ba74a6e927a7a6398bb/src/browser/CoreBrowserTerminal.ts), [compiler publication](https://registry.npmjs.org/@esbuild/linux-x64/0.28.0), [compiler API](https://esbuild.github.io/api/). Source register R376/R377; the source patch is a Cloudy modification, not a claim that upstream released a new fixed version.

## Host/browser checks actually executed

- Real Chrome **155.0.8059.39**: seven product cases pass, including fast committed text followed immediately by Enter and Ctrl+C; Unicode/combining/emoji, canceled preedit and closed-attachment refusal retain exact-byte checks. Fail-before and pass-after reports are in `.cache/console-resume-2026-10-17/fast-ime-before.json` and `fast-ime-after.json`.
- Real source compilation produced the candidate above; product preparation rebuilt it and matched the reviewed output hash. Read-only verification passed.
- **40** terminal asset/source-build Python tests passed. They cover bounds, pin/patch/archive violations, offline/read-only behavior, corruption and output refusal; copied-output/wiring-only proposal tests were removed during parent review.
- Python AST and Ruff passed at their recorded source inputs; strict Console app/test assembly, Console/terminal Lint and relevant JVM tasks passed. Java-only `:domain`/`:ssh` are not Android-Lint analyzed; their warning remains disclosed.
- Native fixture compilation uses `javac -Xlint:all -Werror`. The [maintained fixture](../../fixtures/ssh-terminal/README.md) passed actual host SSH password, kernel-PTY/CR operation and wrong-password refusal at its stated boundary.

## Actual emulator product workflow

Environment:

- Android Emulator **37.2.12**, build **16428233**, KVM; x86_64 Google APIs image **android-37.0 revision6**.
- Android17/API37 fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
- WebView **com.google.android.webview145.0.7632.218**; 2880×1800 at240dpi; SwiftShader.
- Console **0.1.18/code19**, owning process/store; Desktop absent. Real loopback SSH through an owned ADB reverse mapping to MINA2.20.0 and util-linux2.41.3's actual kernel PTY. No custom Cloudy host agent.
- `FLAG_SECURE` retained; native UiAutomation/InputConnection/key injection, not physical hardware input or an installed IME's candidate/preedit workflow. No DevTools attached.

Operation **`ca0c892c-0ef0-4561-b422-9d38c04da963`** passed:

1. Create an owned profile/current trusted pin; enter native transient credentials.
2. Recreate the Activity and verify that credential drafts/selection do not authorize replay; explicitly select and connect again.
3. Authenticate to the real SSH server and open the owned PTY/renderer.
4. Commit the actual command and inject Enter through the native window input path.
5. Independently observe the host proof file and exact bytes before issuing the app-private witness.
6. Capture the actual protected app window; the parent inspected visible command text, `CLOUDY_TERMINAL_ROUNDTRIP`, shell prompt and cursor.
7. Explicitly send `exit`/Enter; observe **`Session ended: CLOSED`**, empty enabled credential fields and actual owner retirement.
8. Restore the exact profile/trust baseline. Receipt: **`caseOutcome=PASSED`, `phase=EXACT_BASELINE_RESTORED`**.

App APK SHA-256 **`b8906cb3b5b810c442cfa268a4844667e3b95a7d14ffb1dc44aecd5135bc5e25`**; instrumentation APK **`26feeb8f34cf89ef7d511186c77d3892a272d1733db3948258e73e04248ff95b`**. The full private directory is `qualification/results/private/console-terminal-resume/ca0c892c-0ef0-4561-b422-9d38c04da963/`. Earlier failures and observer-dependent outcomes remain separately preserved, not relabeled as passes.

The cleaned direct-Activity run **`1cd4ca21-6ca5-4db3-a61b-301b6c913e16`** also passed, without ART tracing, passive event/DOM dumps or accessibility-tree scaffolding. Its receipt binds app APK **`d81a8095a4c454eab47ecd8a06a9f42b60510dfef6ff44e101aed41d49421015`** and test APK **`f395f60babf4fabdd2bb70a33c6826adbe8a97d82032aa2f45000d35e6aeb02f`**. The parent inspected its visible command/output. Those hashes are not attributed to later frozen APKs.

## Complete home-to-terminal journey

Operation **`b6f93a4d-6c12-4586-a964-b8772f9ad61f`** passed the actual **Workspace home → search owned profile → select host → Connect → native credentials → real SSH/PTy → command/output → exit** journey. The test observes the Activity actually launched by Home; it does not substitute a direct terminal Intent or refresh/rebind the profile before first authentication. Only after that session retires does it check credential-draft recreation and refusal of automatic reconnect.

Parent source review found that asynchronous `recordUse` increments the profile revision while the authentication guard requires an exact current revision. Home now waits for successful publication and a matching reloaded version before navigation, with the existing generation fence plus current window focus/lifecycle checks. Failed or uncertain writes stay visible and do not launch/retry automatically. Revision/trust admission was not relaxed. The label now says **Last connection attempt**, not a successful-connection claim.

The fixture adopts only the expected owned row's single revision increment and in-attempt timestamp; owner, endpoint, label, protocol, tags and favorite must match. Every foreign row's complete metadata remains unchanged. Parent review corrected worker cleanup to close only the fresh owned profile's Activities, never all resumed terminals.

- App APK: **`124075190d772f5522ea33b27904f21f766392307a5392b8f730985db075eeca`**.
- Instrumentation APK: **`79994b457328fe0822561258487147a9b83078096be1af10a94b93e3150c41c6`**.
- Controller SHA-256: **`2af6c2e719ade8e04694f2a8d481eb186c77b9bf021d638a035b56afd911ecb4`**.
- Frozen APKs and source manifest: `.cache/console-emulator-2026-10-17/home-journey-build/`; controller changes after that source archive are bound separately by the hash above.
- Private evidence: `qualification/results/private/console-terminal-resume/b6f93a4d-6c12-4586-a964-b8772f9ad61f/`.
- Actual result: independently matched host proof, inspected protected pixels with `CLOUDY_TERMINAL_ROUNDTRIP`, **Session ended: CLOSED**, empty credentials after recreation, **PASSED / EXACT_BASELINE_RESTORED**. This means exact logical profile/trust rows and metadata, not unchanged SQLite file bytes.
- Strict offline build/Lint succeeded with 248 tasks (40 executed); Java-only `:domain`/`:ssh` remain outside Android Lint analysis. The owned MINA fixture then retired through its run-local stop file and exited 0.

Failed operations remain preserved: `5b607c54-3978-45e3-b8fe-1840b69a13ae` executed the remote command but failed at witness validation and restored the baseline. Source review found non-atomic witness publication could expose an empty/partial file; that run's exact failed predicate was not recorded. Attempt `1bde773e-22b3-4553-b98d-1aab98108124` failed before instrumentation because the Android sandbox denied hard-link publication. The controller now writes a private temporary file, closes it, then uses non-clobber rename and verifies that it was not skipped. No Android security setting was bypassed.

The maintained controller is [`tools/run_console_terminal_smoke.py`](../../../tools/run_console_terminal_smoke.py); see its explicit fixture/target invocation in the fixture README. Controller timeout does not prove Android-side retirement, and no app-data reset is used to manufacture a clean result.

## Emulator/window-system boundary

The official Android CLI `medium_desktop` profile currently resolves to API34, below Cloudy's min35, so it was not used to weaken the app requirement. The owned API37 image supports the app and controlled freeform/developer-windowing experiments, but does not advertise PC/freeform platform features by default and does not reproduce Googlebook's native caption behavior. Temporary emulator-only OEM-resource overlays did not survive reboot. Real resize bounds were observed using `am task resize`; they are development evidence, not Googlebook platform certification. No hidden/system configuration enters application code.

## Acceptance status

| Acceptance | This record establishes | Still required |
| --- | --- | --- |
| Q-C01 | Existing trusted-pin/password route exercised through actual app and server | First-use/change/cancel matrix, key/keyboard-interactive methods and every jump hop |
| Q-C02 | Actual app committed-command/Enter/output/retirement; bounded browser regression and prior component slices | Physical keyboard/pointer/system IME/TalkBack, clipboard/menus/copy-on-select, resize/backpressure/alternate-screen breadth |
| Q-C03 | No command/input replay in this explicit foreground construction | Configured durable remote sessions, eviction/reopen/host-reboot distinctions |
| Q-C04/Q-C05 | No new attachment-upload or managed-forward claim | Real integrations and their acceptance cases |
| Q-C06 | No coding-day or sustained-use claim | Full workflow with Desktop absent/custom agent disabled and exact candidate duration |
| Q-C07/Q-C08 | Earlier host/probe X11 evidence remains separate | Integrated isolated native app windows, dialogs/popups/lifecycle and remote installed VS Code |
| Q-C09/Q-C10 | Earlier read-only SFTP evidence remains separate | Session-linked writable files, staged editing/publication, persistent transfers and bidirectional drop |

M3 continues toward the complete desktop interaction contract. This repair neither reduces full R1 scope nor turns experimental/partial capabilities into supported releases.
