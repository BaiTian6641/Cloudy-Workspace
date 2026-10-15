# Terminal upstream repair candidate and desktop input review

**Date:** 2026-10-15, America/Chicago. **Scope:** reviewed diagnostic source/package and focused software behavior. **Decision:** use the official prerelease core/fit pair for a bounded **0.7.2** device comparison; keep CAP-TERMINAL pending. This record does not qualify the desktop interaction contract or claim that the installed Googlebook input defect is repaired.

## Concrete change and adoption boundary

Rapid non-composing key-code-229 input in the existing 6.0.0 diagnostic produced `sttyttytyy` for `stty`. The [retained device trace](../../qualification/results/P0-S3/2026-10-15-terminal-input-trace.md) and [upstream fix investigation](../07-reference/2026-10-15-xterm-input-fix-candidate.md) identify a matching official pending-timer guard. The selected package changes that input path without a Cloudy key interception, content deduplication, user-agent override or accessibility workaround.

The [manifest](../../qualification/probes/device-baseline/terminal-assets.json) now selects **`@xterm/xterm@6.1.0-beta.315` plus `@xterm/addon-fit@0.12.0-beta.311`**, both at `991857a1b1cacf06f9531ba74a6e927a7a6398bb`. Fit declares peer `^6.1.0-beta.315`; fit beta315 is not the matching published version. The core's stable release remains 6.0.0 at the check. This is an official prerelease diagnostic candidate, not stable/product adoption.

The leader checked the worker's exact archive/API/license delta and the actual manifest/preparer results. Both archive SHA-512 identities, SHA-256 values, inventories and selected JS/CSS/license bytes were verified without executing npm scripts. Both packages have no runtime dependencies. Existing bridge calls and UMD exports remain compatible; the matching core CSS accompanies the JS. Fit now uses public terminal dimensions, accessible with `allowProposedApi:false`. Preserve the supplementary Microsoft MIT notice for implementations moved into `src/browser/scrollable`; its provenance URL now names the immutable implementation source. Package license bytes are unchanged.

An official published pair avoids a locally patched/minified build or an unreviewed fork. Reconsider a backport only for a concrete blocking prerelease regression. Preserve the 0.7.1 APK and old evidence; a rollback must not discard application journals or pretend to remove the old duplication defect.

## Focused actual-library results

The [browser record](2026-10-15-terminal-upstream-browser.json) binds exact bundle, bridge, stylesheet and harness hashes and the recorded Chrome **155.0.8059.39**. Each case uses a fresh iframe with the real libraries and unchanged authored bridge; only the native endpoint is substituted to collect binary input and admit credits. The fixtures are test-only and do not enter the APK.

| Observation | Stable core6.0.0 / fit0.11.0 | Candidate core beta315 / fit beta311 |
| --- | --- | --- |
| Startup/fit geometry | READY 91×17 | READY 91×17 |
| Real output parser completion | Seven-byte `smoke\r\n`, matching ACK | Same matching ACK |
| Rapid `stty` | `sttyttytyy`, ten bytes | `stty`, four bytes |
| Paced `stty` | Correct four bytes | Correct four bytes |
| Rapid repeated `tttt` | Ten `t` bytes | Correct four `t` bytes |
| Paced repeated text, single deletion | Declared bytes match | Declared bytes match |
| Synthetic composition commit/cancel/Enter and Ctrl-C/Backspace/Enter | Declared bytes match | Declared bytes match |
| Four coalesced modeled deletions | Four DEL bytes | **One DEL: input-integrity limitation** |
| Equal-length modeled replacement | `ax` rather than DEL + `x` | **Same input-integrity limitation** |

Both final runs passed one startup/fit/output smoke and all eleven declared **characterizations**. Their `allInputIntegrity` values are false. A zero harness exit means the expected observations match, not that every intended edit works. Candidate correctness improved for the observed ASCII duplication schedule; deletion/replacement limits remain explicit questions for actual event behavior and renderer adoption. No further upstream event matrix is warranted without a new concrete finding.

Earlier failed runs are retained. The first candidate attempt exhausted a parent deadline shorter than its child's readiness allowance; the fixture now preserves partial results with bounded deadlines. A subsequent resize smoke timed out identically on stable and candidate under Chrome's virtual clock, despite a real container-size change and valid output ACK. That unsuitable resize experiment was removed from the accepted smoke under D12; **actual resize remains a device requirement**. No private renderer API, stubbed observer or forced successful resize was substituted. The browser fixture does not load the APK entry page's CSP and does not prove WebView, physical input, native IME, SSH delivery, accessibility, latency or painted pixels.

## Package and checks

The [package audit](2026-10-15-terminal-upstream-package.json) identifies **0.7.2 / code11**, build reference `475bcf30-ce99-4995-a549-7fcbb1cfd31d`, APK **15,976,273 bytes**, SHA-256 **`c6f52b83d66846c528f867f3f450e66e32c8add0e14e717d8c8820b6fda9e949`**. All 24 notice assets and nine terminal assets equal the APK entries byte-for-byte. The 31-coordinate Android runtime graph, permissions and components are unchanged by this candidate. Source hashes, test XML and build/lint report hashes bind the local checks.

Executed checks passed: Python AST and Ruff, 67 Python repository regressions, nine authored bridge tests, both new JavaScript syntax checks, exact terminal asset preparation/verification, Android compilation/assembly, Android Lint and **198 JVM tests across 28 suites**. Planning/schema checks are recorded with the containing milestone. Hosted CI is reported separately against the pushed commit; local checks are not remote CI evidence.

The parent inspected the candidate review, harness diff, real outputs and package audit. A GPT6.1-Sol Codex reviewer found no blocking pin/version/interaction-contract defect and explicitly required preserving the characterization-versus-correctness distinction. The final smoke was reduced after the review's unexecuted resize caveat; the leader inspected the reduction and final results. OMP was not used for GPT6.1-Sol.

## Next bounded device work

The installed device remains **0.7.1/code10**; no device mutation occurred in this source/package increment. After committing and binding this candidate, preserve the current one-attempt journal and the original eight-attempt archive, install the exact APK, and repeat one rapid/paced byte comparison through the existing controlled Linux SSH fixture. Include real CSP/startup/output and actual resize because both bundles' virtual-clock resize check was inconclusive. No journal rotation or repeated SSH/archive qualification is needed.

Real physical keyboard, native CJK IME/preedit, selection/copy/paste, mouse/trackpad and TalkBack remain separate requirements under the [desktop interaction contract](../04-design/desktop-terminal-interaction-contract.md). Termux is a useful reference/component candidate with documented hardware shortcuts; it is not selected as Cloudy's renderer and its touch-oriented defaults cannot waive desktop behavior. A successful rapid ADB comparison would close only that specific regression, not the broader desktop input gate.
