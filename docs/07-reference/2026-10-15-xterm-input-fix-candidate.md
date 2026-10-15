# xterm Android input fix candidate — 2026-10-15

**Decision: researched candidate, not adopted.** The installed diagnostic still uses xterm **6.0.0**. An official upstream fix closely matches the delayed-textarea mechanism investigated in the [bounded device input trace](../../qualification/results/P0-S3/2026-10-15-terminal-input-trace.md). An exact-source method experiment reproduces the released method's duplication and the upstream method's improvement under a controlled schedule. It also exposes deletion and replacement boundaries that prevent treating the fix as complete input qualification.

## Official provenance and release status

Live official metadata checked **2026-10-15** identifies [PR #5439](https://github.com/xtermjs/xterm.js/pull/5439), “Fix key duplication on Android with physical keyboards,” containing commit [`d03416467d35c18c510a3b1853d875ecf0b3a57a`](https://github.com/xtermjs/xterm.js/commit/d03416467d35c18c510a3b1853d875ecf0b3a57a). The change adds a pending textarea timer, skips scheduling another while one exists, and clears the handle before processing its callback. It coalesces callbacks; it does not remove repeated characters by content.

The PR merged **2025-12-27 14:38:29 UTC**, merge commit `97c435862b24813f88abfe1ef665a89994561840`, after [6.0.0's release](https://github.com/xtermjs/xterm.js/releases/tag/6.0.0) on **2025-12-22**. The author's earlier commit timestamp does not establish inclusion in that release. Both GitHub's latest stable release and [npm's latest metadata](https://registry.npmjs.org/@xterm%2fxterm/latest) still identify **6.0.0**, commit `f447274f430fd22513f6adbf9862d19524471c04`.

The published [6.1.0-beta.315 metadata](https://registry.npmjs.org/@xterm%2fxterm/6.1.0-beta.315) identifies commit `991857a1b1cacf06f9531ba74a6e927a7a6398bb`, also the inspected master head. The actual prerelease tarball matched its npm SHA-512 integrity value. Its `CompositionHelper.ts` was byte-identical to the [immutable upstream source](https://raw.githubusercontent.com/xtermjs/xterm.js/991857a1b1cacf06f9531ba74a6e927a7a6398bb/src/browser/input/CompositionHelper.ts). Downloading and inspecting that archive did not install, execute or adopt its runtime bundle.

| Inspected composition source | Bytes | SHA-256 |
| --- | ---: | --- |
| Published 6.0.0 / R106 | 10,238 | `270ea7fa05ebb48e14f50a63dd61e0230f36f40f4d0232579e4b884d8579ed99` |
| Official fix commit d034164 | 10,477 | `b1a6bed25f5b972576f7c49912ab19b7809a6ae657ba95f404d128dcc3345ff0` |
| Actual 6.1.0-beta.315 source / master 991857 | 12,132 | `1e935e66830ca171456466987cb45ed0a270553901729f11dfa91f6b702e0845` |

## Bounded exact-source experiment

Private evidence lives under `qualification/results/private/terminal-probe/composition-source-experiment/`. The harness verifies source hashes, extracts the original `_handleAnyTextareaChanges` body without statement changes, and supplies a deterministic bounded FIFO, the verified DEL constant and minimal textarea/core-service fields. It executes neither the full class nor a browser, native IME, SSH path or application. Composition cases toggle only this method's state flag; no composition lifecycle/commit/cancel implementation is exercised.

The first immutable run recorded **36** cases. A separate v2 adds one multiple-deletion schedule, for **39 cases: 13 per source variant**. All predeclared characterization assertions matched; this includes reproducing undesirable behavior, so it is not an input-integrity pass. The exact fix and beta method bodies share SHA-256 `df1459844abc13713e091e12f1bfdc254e4b699116be9902e2d014cd49e41a32`; the released body hashes to `f2b497f9636b7cc12991c99bccab2dc3a3db19aab5659c2a8d18c328e97bac50`.

| Controlled method schedule | Released 6.0.0 | Official fix and beta source |
| --- | --- | --- |
| Four `stty` appends before callbacks drain | Chunks `stty`, `tty`, `ty`, `y`: ten UTF-8 bytes | One `stty` chunk: four bytes |
| Drain after each character | `s`, `t`, `t`, `y` | Same four chunks |
| Four legitimate repeated `t` characters before drain | Ten bytes through overlapping suffixes | All four intended repeats once |
| Four invocations with unchanged textarea | No output | No output |
| Four separate one-character deletions before drain | Four DEL bytes | One DEL byte |
| Equal-length replacement / in-place edit that grows | Whole new textarea value | Same whole-value behavior |
| Composition remains active when callback runs | No output | No output |

The v2 harness SHA-256 is `192bf2b5fe5ab4779b3816d7f9142fc7f27985f7d40817ad94efef5055b45332`; `results-v2.json` hashes to `33d8f18d72f7d4d908f9aa8e01cdaebf720703651c442887dccfb206d48c9485`. Original and v2 artifacts remain separately preserved. Syntax checks and local execution succeeded. Primary and independent review accepted the exact source, output hashes and scope; an independent run of the unchanged v2 harness in a separate directory matched all 39 outputs and event logs.

## Adoption boundary and next decision

The device trace recorded four DOM insertions and ten host bytes for bulk injection; this source experiment shows one exact upstream path that can generate the same suffix sequence. It still does not observe the device's actual xterm callback, bridge-frame or native-write sequence. Paced ADB success is not physical keyboard or native IME qualification.

Upstream [issue #6045](https://github.com/xtermjs/xterm.js/issues/6045) discusses deferred-difference/keydown interactions under a different event order. [Issue #5887](https://github.com/xtermjs/xterm.js/issues/5887) concerns a separate input-event gate, and [issue #6078](https://github.com/xtermjs/xterm.js/issues/6078) documents whole-textarea re-emission/replacement hazards even with the timer guard. These reports strengthen the need for scoped regression coverage; they are not Googlebook pass evidence.

Under the subsequent user-directed [reuse policy](../05-delivery/library-reuse-policy.md), compare the relevant prerelease core/fit artifact, API, distribution and provenance delta against a narrow backport of the official change, then choose explicitly. Neither route is selected here. Use the actual library for focused regressions of the affected rapid/repeated-text, deletion/replacement, composition commit/cancel and key-control paths; reuse applicable upstream coverage and stop expanding the method harness without a new finding. Repeat the bounded device bulk/paced/host-byte comparison. This is an integration regression requirement, not an exhaustive upstream event-order or terminal conformance suite. Physical keyboard, actual CJK IME and TalkBack remain separate required evidence. No UA spoofing, second native forwarding path, arbitrary content deduplication or accessibility disabling is justified. No capability gate is promoted.

## Subsequent candidate selection

The [0.7.2 review](../06-validation/2026-10-15-terminal-upstream-review.md) selects the official core6.1.0-beta.315/fit0.12.0-beta.311 pair for a bounded diagnostic device comparison. Matching artifact/API/license review, actual-library browser characterizations and Android compilation/lint pass; coalesced-deletion and replacement limitations remain. This supersedes the earlier unselected-candidate status only for the diagnostic build. The device remains on0.7.1, and physical input/IME/accessibility, actual resize and renderer adoption are unqualified.
