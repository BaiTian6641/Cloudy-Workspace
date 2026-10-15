# P0-S4 — three-advisory LibVNCClient host regressions

**Date:** 2026-10-15, America/Chicago. **Result:** 27/27 focused host cases pass with all nine valid controls receiving updates; zero failures and no ASan/UBSan/leak report. This is a portable host regression result, not Android, TLS, server or CAP-VNC qualification. See the [boundary review](../../../docs/06-validation/2026-10-15-vnc-regression-review.md) and [exact candidate/fix mapping](../../../docs/07-reference/2026-10-15-vnc-patched-source-selection.md).

The source is unchanged LibVNCClient candidate `98aaee0560baa0332339b2ef464ea96ed9b9b28c`. The exact cached upstream archive is 612,494 bytes, SHA256 `e4aba30e25cd7bcc743794ccaf4a9b7c3236d15945b9e8b8bb059b0ae1d2f1bb`; all 174 regular/symlink source records match it before and after execution. The [historical host build](2026-10-15-vnc-host-build.md) and private failed draft remain unchanged.

The prior draft exited during `lzo_init()` / `_lzo_config_check()` on miniLZO's deliberate unaligned configuration-test load, before any decoder verdict. The [official pinned source](https://raw.githubusercontent.com/LibVNC/libvncserver/98aaee0560baa0332339b2ef464ea96ed9b9b28c/src/common/minilzo.c) supplies supported `LZO_CFG_NO_UNALIGNED=1` to select portable accesses. This fresh build enables it for bundled miniLZO, retaining `-fsanitize=address,undefined -fno-omit-frame-pointer -O1 -g`. No sanitizer disable, ignore list or source patch was used. The old stop is retained as an initialization failure, not relabeled as an advisory failure or pass.

The new test invokes real `HandleRFBServerMessage` and upstream decoders through bounded in-memory read/write wrappers. zlib and bundled miniLZO generate the fixtures; the harness checks real framebuffer contents and callback counts. There is no listener, socket, network connection, credentials, system installation or Android/device operation.

| Official advisory | Actual cases across 8/16/32bpp | Verdict |
| --- | --- | --- |
| [GHSA-v9pm-47h4-jcq8](https://github.com/LibVNC/libvncserver/security/advisories/GHSA-v9pm-47h4-jcq8) — excess decompressed Tight rows | 3 valid rows and 3 excess-row inputs | Valid rows each produce one update; excess rows reject before framebuffer mutation |
| [GHSA-jcc5-8wj4-7c58](https://github.com/LibVNC/libvncserver/security/advisories/GHSA-jcc5-8wj4-7c58) — Gradient width bound | 3 width2048 controls and 3 width2049 inputs | Controls each produce one update; wider rectangles reject before mutation |
| [GHSA-87q7-v983-qwcj](https://github.com/LibVNC/libvncserver/security/advisories/GHSA-87q7-v983-qwcj) — UltraZip decompressed bounds | 3 valid subrectangles; 12 malformed cases: truncated header, truncated raw payload, excess count, large raw-size product | Controls each produce one bitmap and one update; malformed cases reject with expected bounded mutation/callbacks |

**Totals:** 27 cases, 0 failures; 3 Tight, 3 Gradient and 3 UltraZip valid controls. Every case reports zero out-of-frame callbacks and zero exhausted fixture reads, with expected framebuffer pixels. The excess-count UltraZip input permits its first valid 2×2 subrectangle to decode, then rejects the missing second header: it expects one bitmap callback and zero completed-update callbacks. It does not assert rollback. Stderr has 18 expected upstream rejection messages and no sanitizer diagnostics.

Reproduce from the repository root using the verified cached archive/source and installed host build dependencies. The output directory must not already exist:

```sh
python3 tools/run_vnc_regression.py --stage .cache/vnc-qualification/followup/portable-01
```

For another justified run, choose a new directory beneath `.cache/vnc-qualification/followup/`; do not overwrite accepted evidence. The runner configures a new static `vncclient` build with zlib/JPEG and bundled miniLZO, TLS/server/examples/upstream tests/desktop integrations disabled, then builds with `--parallel 4`. The authored harness compiles as C11 with `-Wall -Wextra -Wpedantic -Werror` and the same sanitizer/portable flags. Runtime uses `ASAN_OPTIONS=detect_leaks=1:abort_on_error=1` and `UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1`, with a 20-second limit. Configure, build, strict compile and execution all exited zero. This configuration must not become a plaintext network-client default.

The executed output root is `.cache/vnc-qualification/followup/portable-01/`: `inputs.json` binds archive/source/authored inputs; `configure.json`, `build.json`, `compile.json`, `run.json` and paired stdout/stderr record actual commands/exits; `sanitizers.json` records the environment; `result.json` binds generated artifact bytes. The [public manifest](2026-10-15-vnc-regressions.json) binds the existing private/cache evidence by relative path and hash; raw logs and downloaded implementation remain private. Parent and independent worker source/result reviews accepted this bounded increment without blockers. Authored checks recorded in `.cache/vnc-qualification/followup/checks.json`: Python AST18 and targeted Ruff passed; strict C compilation emitted no warnings. No checks were rerun for this documentation-only packet.

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Fresh portable static `libvncclient.a` | 5,070,862 | `efa9df134bf7e161b8ade965ffccfb2df7ec0f1e309bf1a1847e6e9b120e8738` |
| Regression executable | 3,777,064 | `838726254a04d402ba623bc94895f6502e87ca5dd61b968b0baf8b44e8939aa5` |
| Per-case stdout | 6,362 | `52ea3880121fed853c54e8387afd1a2df4674513521cd790d5858cfc864ce9c7` |
| Expected rejection stderr | 1,410 | `09a45e29cb8e0b44232eaaa2dc9733fcc2587455495072e273c8e17987637676` |
| Authored C harness | 11,595 | `a6454ac5e13ea71e29f1c59af1b825c69488caadd38a2500146e5037c91c1bba` |
| Authored runner | 7,524 | `120bacdaee55ed591d9e15b4904d6dc53728cea7232fc75d9e299c9ff58fe696` |

The [reuse policy](../../../docs/05-delivery/library-reuse-policy.md) permits carrying these focused results forward. Android cross-compilation with selected TLS/compression dependencies, a protected real-server adapter probe, rendering/input/cancellation/recovery, and GPL/distribution decisions remain open. The portable host configuration does not establish the behavior of the original optimized unaligned-access build or any Android production configuration. CAP-VNC remains pending.
