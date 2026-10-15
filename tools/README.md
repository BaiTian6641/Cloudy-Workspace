# Planning-asset tools

Run from the repository root. The core checker and source helper use only Python 3.10+ standard-library modules.

```bash
python3 tools/validate_project.py
python3 tools/generate_design_tokens.py --check
node --check docs/04-design/preview.js
```

The checker validates internal Markdown/HTML asset links, JSON, source/API/stage IDs, dependency cycles, phase effort sums, acceptance-case references, declared contrast pairs including ANSI colors, generated CSS consistency, specimen element references, and the preserved intake hash. Its result is limited to planning assets.

Optional schema checks:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt
.venv/bin/python tools/validate_project.py --schemas --report artifacts/validation.json
```

This validates the three draft schemas/examples and rejection of six invalid envelope cases. It does not implement authorization, time-relative expiry, replay policy, or resource semantics.

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
