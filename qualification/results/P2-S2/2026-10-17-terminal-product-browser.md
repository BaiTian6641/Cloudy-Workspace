# Product terminal input browser regression — 2026-10-17

**Status:** verified host-browser regression slice; not Q-C02, physical input, Googlebook WebView, SSH or R1 acceptance. Source base: `625796e` plus the runner/fixture changes committed with this record, on `codex/console-r1-implementation`.

## Boundary and change

`tests/test_terminal_input_browser.cjs --surface product` loads the authored product `shared/android/terminal/src/main/assets/terminal/{terminal.js,terminal.css}` and the generated, hash-pinned xterm `6.1.0-beta.315` / fit `0.12.0-beta.311` assets. The product boots its actual four-part identity/font-scale/accessibility hash, with spoken-screen-reader mode off. A test-only native endpoint observes the real bridge INPUT bytes and admits its credits; no mocked xterm, private xterm API, network operation or upstream source patch is involved.

The default `--surface probe` continues to exercise the diagnostic probe and all its existing input schedules. Reports identify and hash the selected authored and vendor files, runner and fixture.

The user requested direct OMP DeepSeek Flash subagents after context compaction. The exact selector `deepseek/deepseek-flash` (catalog name DeepSeek V4.1 Flash) supplied the bounded browser proposal. The parent reviewed every changed section, removed assertions on internal DOM/default/read-only state and exact frame fragmentation, and retained only exact consumer-visible bytes, canceled-preedit zero-send and post-close refusal. This is accepted bounded work with parent corrections, not blanket model or tool/security qualification.

## Actual execution

Host browser: **Google Chrome 155.0.8059.39**, temporary owned profile, no personal profile or debug TCP listener. Commands from the repository root:

```bash
node --check tests/test_terminal_input_browser.cjs
node --check tests/fixtures/terminal_input_browser.js
node tests/test_terminal_input_browser.cjs --surface product \
  --assets "$PWD/shared/android/terminal/build/generated/terminalAssets/terminal"
node tests/test_terminal_input_browser.cjs --surface probe \
  --assets "$PWD/shared/android/terminal/build/generated/terminalAssets/terminal"
```

| Product case | Observed native INPUT bytes / invariant | Result |
| --- | --- | --- |
| Committed insertText without keydown | `61 62` (`ab`), exactly once | Pass |
| Combining text, CJK and emoji | `65 cc 81 e6 bc a2 f0 9f 91 8d` (`é漢👍`) | Pass |
| Canceled preedit then committed composition | No bytes for canceled preedit; `e4 b8 ad` (`中`) once | Pass |
| Enter then Ctrl+C | `0d 03` | Pass |
| Closed attachment | Initial `61`; replay, new text and Enter after CLOSED send no further bytes | Pass |

The probe startup/output-parser smoke and all **11** existing probe schedules matched their documented expected observations. The probe's equal-length replacement characterization remains a known input-integrity limitation; matching its historical observation is not proof that the desired replacement behavior works.

## Preserved failure and driver correction

The first virtual-time `--dump-dom` probe run failed during the fifth iframe's startup. A frame-synchronized diagnostic then timed out waiting for that iframe's animation frame, before input began. The parent replaced virtual-time DOM dumping with real-time Chrome DevTools Protocol over inherited pipes, retaining the same bundles, fresh iframe isolation, input event schedules and expected bytes. Both surfaces then completed. This is a **test-driver** correction, not a product renderer readiness or physical-input fix; no retry, widened input expectation or passing-on-timeout path was added.

Ignored execution records, including the initial failures and final source hashes, are under `.cache/omp-workers/2026-10-17-deepseek-flash/` (`probe-browser*.json`, `product-browser*.json`). These are local evidence, not distributable application artifacts. The runner closes its owned Chrome process before deleting its temporary profile.

## Remaining acceptance

The separate [Googlebook committed-input run](2026-10-17-terminal-input-repair.md) is actual Android WebView evidence at its stated boundary. Neither it nor this host regression qualifies the installed system IME, physical keyboard/trackpad, TalkBack, clipboard/menu behavior, sustained output, session-linked files, tabs/splits/layouts, X11 product windows or the full Q-C01–Q-C10 workflow. P2-S2/S6 and R1 remain in progress.
