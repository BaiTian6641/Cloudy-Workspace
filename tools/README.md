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
