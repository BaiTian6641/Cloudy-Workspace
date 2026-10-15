# Execution setup review

**Date:** 2026-10-15, America/Chicago. **Scope:** goal/workflow records, shared SSH/SFTP dependency correction, official skill acquisition, repository AST/lint gates and read-only ADB baseline. This is not an Android application or backend qualification report.

## Reviewed changes

- The active goal and [engineering workflow](../05-delivery/engineering-workflow.md) record the user's no-placeholder, review, comments, context, research, delegation and Git requirements.
- Independent review found Desktop files still depended on Console renderer/UI stages. `CAP-SSH`, reusable P2-S1 and P2-S8 now separate the shared transport/transfer work. Desktop remains responsible for its own actual integration tests.
- The roadmap now has 40 stages, eight pending capability gates and 35 acceptance cases. Reallocated core effort preserves the 129–223-day range; P2 becomes 33–59 and P3 becomes 28–47. P0-S1 is in progress; no stage or capability is marked complete/supported.
- Three [official Android skills](../07-reference/development-skills.md) were pinned, inspected, hash-verified and installed with upstream licenses. The source register now has 83 entries. Skill examples do not become implementation or change the selected architecture.
- The Python AST checker parses and compiles source without execution; pinned Ruff enforces E4/E7/E9/F. CI retains planning/schema and specimen JavaScript syntax checks. No Android build or Android Lint result is claimed.
- The primary agent inspected subagent changes and the actual tool output. The [ADB baseline](../../qualification/results/P0-S1/2026-10-15-usb-baseline.md) records real observations and limits; private inventory is excluded from public validation.

## Verification record

The primary agent reran the integrated local checks after reviewing the subagent changes:

| Check | Result |
| --- | --- |
| `.venv/bin/python tools/check_ast.py` | Passed on five authored Python files; parse and AST compilation only, no source execution |
| `.venv/bin/ruff check .` | Passed with pinned Ruff 0.17.0, E4/E7/E9/F |
| `.venv/bin/python -m unittest discover -s tests -v` | Nine planning-policy regressions passed, including private evidence, infinite effort caps and forbidden dependency paths |
| `.venv/bin/python tools/validate_project.py --schemas` | Passed; see the [machine-readable report](2026-10-15-execution-setup-validation.json) |
| `node --check docs/04-design/preview.js` | Passed |
| Phase/backlog content and status comparison | Passed for all 40 stages; 39 planned and P0-S1 in progress |
| `git diff --check` | Passed |

Independent code review found two validator defects: evidence under the ignored private tree was accepted, and an infinite experiment effort cap was accepted. The primary agent fixed both, inspected the regression test code and ran the resulting tests. Separate AST-checker failure fixtures verified rejection of invalid/context-invalid/newer grammar, symlinks, unreadable input, empty discovery and Git failure without executing the source.

The sanitized baseline JSON has SHA-256 `38c6ec34ad3073468620eb1e6ba496507c31ef7902c60960bac0cd4a9056706f`. All eight feature capability gates remain pending; all 33 API/integration qualification records remain untested. Local success and remote CI are distinct results. Earlier Revision 02 reports retain their original snapshot counts and are not overwritten by this setup milestone.

## Outstanding qualification

A real diagnostic APK/toolchain remains the next step. Native window/input/IME, protocol clients, decoders, USB forwarding and performance remain unqualified. The user selected the local Linux/Wayland host; Windows and a board/probe require actual identified targets. Source lookup, AST/lint success and ADB access do not establish product support.
