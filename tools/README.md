# Repository verification tools

Run from the repository root. The core planning checker, AST checker, and source helper use Python 3.10+ standard-library modules. AST source discovery also requires Git.

```bash
python3 tools/validate_project.py
python3 tools/check_ast.py
python3 tools/generate_design_tokens.py --check
node --check docs/04-design/preview.js
```

The checker validates internal Markdown/HTML asset links, JSON, source/API/stage IDs, dependency cycles, phase effort sums, acceptance-case references, declared contrast pairs including ANSI colors, generated CSS consistency, specimen element references, and the preserved intake hash. Its result is limited to planning assets.

It also checks capability outcome records, evidence-path references, bounded-experiment fields and stage readiness. An activated implementation stage must have completed prerequisites and supported required capabilities. These structural checks cannot verify the truth of evidence or grant approval for a scope change.

The backlog's dependency constraints check complete ancestor paths, so an indirect X11 dependency cannot silently re-couple Desktop's release and optional host adapters cannot reappear upstream of native streaming. They also require the suite milestone to depend on both app candidates.

Install the pinned local/CI tools and run the complete repository gate:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt
.venv/bin/python tools/check_ast.py
.venv/bin/ruff check .
.venv/bin/python -m unittest discover -s tests -v
.venv/bin/python tools/validate_project.py --schemas --report artifacts/validation.json
node --check docs/04-design/preview.js
```

This validates the three draft schemas/examples and rejection of six invalid envelope cases. It does not implement authorization, time-relative expiry, replay policy, or resource semantics.

The AST checker discovers tracked and unignored new `.py`/`.pyi` files, excluding private inventories, caches, artifacts, fetched skills, and third-party trees. It reports the exact file list, count, and diagnostics as JSON and exits nonzero on discovery, parsing, or syntax-context failure. It parses with Python 3.10 grammar and compiles the resulting AST without executing the source or writing bytecode. This is a syntax gate; it does not establish runtime compatibility, type correctness, or functional behavior.

Ruff is pinned to `0.17.0` in both `requirements-dev.txt` and `pyproject.toml`. The configuration selects `E4`, `E7`, `E9`, and `F` to catch import/statement errors and Pyflakes findings without adding a formatting migration. The same commands run in `.github/workflows/docs.yml`; failures must be fixed rather than hidden with broad ignores. Keep the AST and Ruff exclusions aligned when adding dependency locations. Android compilation, Android Lint and JVM tests for the real diagnostic APK run separately in `.github/workflows/android-probe.yml`; Python syntax checks cannot substitute for them.

Tool references checked 2026-10-14: [Ruff 0.17.0 on PyPI](https://pypi.org/project/ruff/0.17.0/), [official configuration and file discovery](https://docs.astral.sh/ruff/configuration/), and [rule reference](https://docs.astral.sh/ruff/rules/). Pin updates must retain reproducible checks and review their findings.

To regenerate the specimen token CSS after intentional token edits:

```bash
python3 tools/generate_design_tokens.py
```

To inspect current source reachability without changing research status:

```bash
python3 tools/check_sources.py --only G01,G06 --output artifacts/source-check.json
python3 tools/check_sources.py --output artifacts/source-check-new.json --previous artifacts/source-check.json
```

Requests have bounded size/time/concurrency. A response hash difference is a review candidate, not proof of an API change. Failed/blocked/JavaScript-only responses do not establish that the official source is invalid. Reports go to ignored `artifacts/` by default; publish only reviewed, sanitized summaries.

## Temporary HTTPS transfer fixture

`transfer_fixture.py` supports the [P0-S2 transfer experiment](../docs/03-platform/googlebook/p0-s2-transfer-probe-contract.md). It requires Python 3.10+ and OpenSSL 3.x. Preparation creates a fresh private directory, owner-only CA/server key files, a server certificate with the selected IPv4 SAN, and `android-config.json` containing the endpoint, public CA, expected length and SHA-256. The CA/server certificates last two days. Never commit these generated files or import the private keys into Android.

After selecting the actual host interface privately, supply its address as `CLOUDY_FIXTURE_BIND`. These are instructions, not recorded qualification results:

```bash
python3 tools/transfer_fixture.py prepare \
  --private-dir qualification/results/private/transfer-probe/fixture \
  --bind "$CLOUDY_FIXTURE_BIND" --port 28443 \
  --length-bytes 67108864 --bytes-per-second 524288
python3 tools/transfer_fixture.py serve \
  --private-dir qualification/results/private/transfer-probe/fixture
```

`prepare` opens no listener and refuses existing directories. `serve` binds only the configured address/port until interrupted. Stop it with Ctrl-C after the experiment. It serves only exact `GET /fixture.bin`; no directories, uploads, ranges or redirects. Four bounded request workers use TLS 1.2 or later. Shutdown interrupts active sockets and joins workers. Sanitized request events contain a random request ID, UTC timestamp and byte count; `stream_written` means local writes completed, not that Android verified or durably stored the file. The server throttle supports lifecycle observations and does not measure available bandwidth.

The generated stream repeats `SHA256(fixtureId + NUL + uint64_be(blockIndex))` to fill each 64 KiB block, truncating the final block to the requested length. The maximum payload is 256 MiB. Android must independently hash its closed private file; accepting the server's hash header is insufficient. The Android config remains private because it contains a local endpoint, although its CA certificate is public material.

`tests/test_transfer_fixture.py` uses temporary loopback TLS servers with real certificate validation. Its tests cover content/hash integrity, wrong CA/identity rejection, request restrictions, file modes, configuration drift, bounded throttling/interruption and stalled-peer cleanup. These are software tests, separate from Googlebook LAN, UIDT and physical lifecycle evidence.

## Restricted SSH exec/PTY fixture

`ssh_channel_fixture.py` is a finite P0 ForceCommand dispatcher, separate from the SFTP server policy. Launch it through an absolute Python interpreter with `-I`, an absolute script path and the literal `--audit-dir` argument pointing to an existing owner-only directory. The dedicated OpenSSH policy must restrict key authentication to the generated fixture key and disable agent, X11 and TCP forwarding; PTY allocation is required for this experiment. Keep the listener bounded and stop it after qualification. No system service or login account change is required.

Only the exact `cloudy-exec-probe-v1` exec request or an empty-command PTY request is accepted. Exec emits nineteen known bytes on each stream and exits 17. PTY requires actual canonical/no-echo/no-CRLF modes and initial 80×24 dimensions, emits readiness, accepts one `SIZE` line after the client resizes, reports real `TIOCGWINSZ` dimensions, then accepts one `FINISH` line and emits its completion marker. The client separately verifies 132×43, exact bytes, EOF and channel closure. There is no arbitrary shell evaluation. Step and overall deadlines are 120/180 seconds; individual output and input lengths are bounded.

Each invocation exclusively creates a private bounded JSONL audit with fsynced events. An uncertain write/sync permanently prevents further append. A missing terminal event is inconclusive; `intendedExitCode` is not independent proof that the process disappeared. The controller must also observe exit and listener closure. Canonical input checks completed lines available at that point; uncompleted or future bytes after process exit are outside that observation.

The twenty software tests use real pipes/PTYs and subprocesses to check bytes, actual resize, terminal modes, rejected input, EOF/hangup/signals, deadlines and audit failures. The [signal-test review](../docs/06-validation/2026-10-15-ssh-fixture-signal-test-review.md) distinguishes clean input-wait interruption from a signal during audit sync. These tests do not replace OpenSSH interoperability or Googlebook qualification. The [source review](../docs/06-validation/2026-10-15-ssh-pty-probe-review.md) records the separate host preflight; the [device run](../qualification/results/P0-S3/2026-10-15-ssh-pty-probe.md) records actual Googlebook observations.

## Focused actual-library terminal check

After preparing the pinned assets, run the optional local browser regression with installed Chrome and a disposable profile:

```bash
python3 tools/prepare_terminal_assets.py
node tests/test_terminal_input_browser.cjs \
  --assets "$PWD/qualification/probes/device-baseline/app/build/generated/terminalAssets/terminal" \
  --expect-rapid fixed
```

The harness executes the actual selected core/fit bundles, authored bridge and stylesheet with synthetic DOM events and a test-only native endpoint. It requires an absolute asset directory and supports `--chrome` for an installed executable. It installs nothing, starts no server and preserves the browser sandbox. Eleven input cases and one startup/fit/output parser smoke remain bounded. Exit zero means declared **characterizations** match; inspect `allInputIntegrity` and each `inputIntegrity` separately. The current candidate retains coalesced-deletion and equal-length-replacement failures. Neither virtual-clock timing nor parser ACK proves input latency or painted pixels. Actual resize, APK CSP, WebView, physical input, native IME and accessibility require their own device checks. CI parses both harness files but does not claim to execute this local browser check.

The [candidate review](../docs/06-validation/2026-10-15-terminal-upstream-review.md) records the exact executed pair, failure limits and retained earlier harness runs. To compare an older retained bundle directory, select its documented `--expect-rapid duplicated` characterization; never label its expected defect as input correctness.

### Product renderer source build

The product uses the same pinned xterm source with a bounded committed-input scheduling repair;
the diagnostic pin above remains unchanged. On the selected Linux x86_64 build host:

```bash
python3 tools/prepare_terminal_assets.py
python3 tools/build_terminal_renderer.py --fetch-compiler
python3 tools/prepare_terminal_assets.py --fetch-search
python3 tools/prepare_terminal_assets.py --product --offline
python3 tools/prepare_terminal_assets.py --product --check
node tests/test_terminal_input_browser.cjs --surface product \
  --assets "$PWD/shared/android/terminal/build/generated/terminalAssets/terminal"
```

The explicit setup commands acquire the reviewed compiler and same-commit search addon; corrupt
caches are not silently replaced. `--fetch-search` cannot be combined with `--offline` or `--check`.
Normal product preparation/check never install packages, execute package scripts or fetch dependencies.
The source, authored patch, compiler and output are pinned in
[`renderer-build.json`](../shared/android/terminal/renderer-build.json). The source build reuses
the existing bounded archive helpers and strict patch replay. `--candidate --offline` generates
an ignored candidate for review; it cannot substitute an unreviewed product bundle.


## Real native RDP packaging and offline check

[prepare_rdp_native.py](prepare_rdp_native.py) prepares the separate [native RDP diagnostic](../qualification/probes/native-rdp/README.md). It uses Python's standard library, performs no downloads/builds, and requires the actual completed FreeRDP 3.32.2 arm64 build, pristine pinned archive, all six reviewed patches, canonical MPL text and matching receipt. Use the exact import command in that diagnostic README, including current `--receipt .cache/native-rdp/cursor-native-receipt.json`; preserve the existing SDK/toolchain and dependency caches rather than rebuilding unchanged inputs.

```bash
python3 tools/prepare_rdp_native.py --check
./qualification/probes/device-baseline/gradlew \
  -p qualification/probes/native-rdp \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The importer checks original source against the pinned archive plus patches, records provenance, validates AArch64 ELF alignment/dependencies, and generates exactly eight native libraries, notices and corresponding modified MPL bridge source assets. There are no dummy libraries or simulated connection fallbacks. The offline check rejects absent/extra/changed prepared files and current lock/patch drift; the native APK's preBuild invokes it. Shared-wrapper `-p` selects this separate package/project and does not replace the device-baseline app. Neither import nor offline check establishes actual server pixels, APK/device loading or interoperability.

The current0.1.6 native diagnostic passed local Kotlin/Java compilation, Android Lint and 48 JVM tests; its [bounded framebuffer result](../qualification/results/P0-S4/2026-10-15-rdp-framebuffer.md) retains actual displayed pixels and explicit cleanup. The separate native CI workflow now declares those gates after a real native source build and verified import; its [first complete hosted run](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38782707586) passed for `cdc9163`. Existing baseline CI remains separate. Follow the established-library policy: validate the changed authored packaging/trust/ownership/input boundary, retain upstream engine evidence, and expand only for a concrete observed failure or changed consequential input. Do not rerun unchanged APK/device evidence for documentation-only edits.

`build_rdp_native.py` runs the already configured, source-verified native target with four jobs and records an actual successful receipt for the importer. It verifies source before/after, rejects changed tracked inputs, confines fresh receipt/log paths to this checkout’s ignored cache, and terminates the build process group on interruption/deadline. Follow the probe recipe for source acquisition/configuration; no SDK or system installation is performed.

## Native RDP fresh setup and CI

`python3 tools/setup_rdp_native.py --sdk "$ANDROID_HOME" --stage /tmp/cloudy-rdp-fresh-stage` downloads or reuses hash-verified archives, applies the locked patches and configures a **fresh** isolated native tree. It never builds or installs dependencies. The stage must not already exist and must contain no whitespace. Cached downloads live under `.cache/native-rdp/downloads`; corrupt cache entries fail rather than being trusted.

Use `build_rdp_native.py` next to compile the real target and produce its fresh receipt, then import with `prepare_rdp_native.py` and the checked-in canonical MPL text. See the [complete native recipe](../qualification/probes/native-rdp/README.md). The [separate hosted lane](../.github/workflows/android-native-rdp.yml) executes that real source/receipt/import path before APK assembly, Lint and JVM tests. It caches only verified downloads and retains bounded diagnostic reports. The [first complete hosted run](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38782707586) passed for `cdc9163`; it does not establish device/runtime compatibility.

## Controlled native host input target

[rdp_input_target.cpp](rdp_input_target.cpp) is a finite Qt 6 Widgets target for observing events in its own editor/canvas and visible changing test content. It has no network input transport or global hook. Compile against installed Qt 6 Widgets into an ignored cache:

```bash
mkdir -p .cache/rdp-input-qualification/host-target
c++ -std=c++17 -fPIC -Wall -Wextra -Wpedantic -Werror \
  tools/rdp_input_target.cpp $(pkg-config --cflags --libs Qt6Widgets) \
  -o .cache/rdp-input-qualification/host-target/rdp_input_target
QT_QPA_PLATFORM=wayland .cache/rdp-input-qualification/host-target/rdp_input_target \
  --log /tmp/cloudy-input-fresh.jsonl --seconds 600
```

Use a fresh private log path. Creation is exclusive, mode 0600, with final-component symlinks rejected. The target bounds events (4,000), bytes (2 MiB), editor text (256 UTF-16 units) and duration (60–1,200 seconds). Clipboard shortcuts, selection publication, middle-paste, context menu and drop are deliberately disabled; ordinary native editing remains. Held-state logs report actual transitions rather than synthesizing balance on focus loss. This fixture cannot qualify clipboard behavior, transport delivery or latency by itself. First observe a known local input event as a positive control, then a controlled remote event; distinguish events outside its observed widgets. Keep logs private because they can contain text. `QFile::flush` is not a durability guarantee. External SIGTERM can terminate without a completion record; the [first run](../qualification/results/P0-S4/2026-10-15-rdp-input-boundary.md) records that case honestly.

## Native USB access diagnostic preparation

[prepare_usb_native.py](prepare_usb_native.py) builds the pinned official libusb1.0.30 shared library with its upstream NDK recipe and Cloudy's small descriptor-only JNI boundary. The [separate probe README](../qualification/probes/native-usb/README.md) gives the exact SDK/JDK/Gradle environment, build commands and limits. `--build` verifies the archive before using it and emits generated libraries, corresponding source/notices and a receipt; `--check` is offline and rejects drift. Gradle preBuild requires that receipt. Keep downloaded source and generated artifacts in ignored cache/build directories.

The [USB CI lane](../.github/workflows/android-native-usb.yml) runs preparation-boundary tests, an actual native build, APK compiler/Lint and ownership tests. The accepted cursor checkpoint752befe passed the native USB hosted lane; see the current execution record for exact commit/run identities. No hosted check establishes a Googlebook grant, physical FD access, forwarding, reset or programming result. This probe has no USB exporter, network permission, forced claim or transfer operation. Source/device outcomes belong to P0-S5; no representative peripheral is currently selected.

## Focused VNC host regressions

[run_vnc_regression.py](run_vnc_regression.py) compiles and executes [vnc_client_regression.c](vnc_client_regression.c) against the exact cached LibVNCClient candidate. It verifies the174 source records before/after, requires fresh output beneath `.cache/vnc-qualification/followup/`, enables full ASan/UBSan with portable miniLZO and has no sockets or device operations. The [27-case result](../qualification/results/P0-S4/2026-10-15-vnc-regressions.md) closes three identified advisory guards with real decoder controls. Reuse this result while relevant inputs remain unchanged; TLS-off test configuration is not a network-client default.

## Native streaming core preparation

[prepare_native_stream.py](prepare_native_stream.py) reconstructs four exact Moonlight/ENet/nanors archives, checks pinned static inputs, compiles the real nonRoot arm64 native core with the existing NDK and verifies the retained result offline. Follow the [recipe](../qualification/probes/native-stream/README.md). Use a fresh ignored run for justified builds; `--check --run entry-02` performs no network or compilation. Authored helper boundary tests are in `tests/test_prepare_native_stream.py`. Native build/ELF success does not establish an APK, JNI runtime, pairing, MediaCodec playback or completed source-distribution bundle.

## Android streaming and VNC native integration

[prepare_stream_android.py](prepare_stream_android.py) stages the verified native-stream source closure plus the reviewed local ABI Activity and exact library/notice inputs. It requires the owned overlay, records the exact current source closure, rejects unrecorded source/build logic, and runs offline verification before Gradle compilation. Follow the [stream recipe](../qualification/probes/native-stream/README.md) for actual `assembleDebug`, `lintDebug` and runtime dependency recording. The [Googlebook load](../qualification/results/P0-S4/2026-10-15-native-stream-apk-load.md) is local JNI initialization/metadata only. Its exact inactive-upstream Lint baseline must be retired for affected paths before pairing/streaming is enabled.

[prepare_vnc_native.py](prepare_vnc_native.py) builds the real pinned LibVNCClient/JPEG Android closure and imports the exact accepted OpenSSL libraries/headers into a fresh private stage. Its [recipe](../qualification/probes/native-vnc/README.md) and [native result](../qualification/results/P0-S4/2026-10-15-vnc-native-build.md) record actual TLS/JPEG feature checks, AArch64/16KiB ELF/dependencies, source/notices and offline integrity. This native build does not supply a JNI adapter or validate a server's security negotiation.

The [finite pairing build](../qualification/results/P0-S4/2026-10-15-stream-pairing-build.md) extends the streaming preparer with exact before/after replay of the reviewed NvHTTP/PairingManager patch and frozen authored-source checks. Its0.1.4 APK is compiled, not installed or UI-activated; no host handshake follows from preparation.

[prepare_vnc_android.py](prepare_vnc_android.py) imports the exact accepted VNC adapter bridge and three native libraries into a fresh Android stage. It rejects changed receipts/inputs, extra source/build/native payloads and symlinks. Follow the [module recipe](../qualification/probes/native-vnc/android/README.md) using a new stage name; entry03 is the retained accepted build, not an overwrite destination. Actual compiler/Lint/seven JVM tests/package audit and [Googlebook native loading](../qualification/results/P0-S4/2026-10-15-vnc-apk-load.md) pass; protected-session work is separate. The reviewed generated dependency lock is now tracked, while the Java runtime artifact set is empty.
