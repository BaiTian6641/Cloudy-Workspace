# Foundation admission and investigation closeout review

**Date:** 2026-10-16, America/Chicago. **Scope:** the decision changes begun on October 15, the authored planning validator and mutation tests, and the Compose dependency entry. This checkpoint creates no new APK or device result. Parent integration review and an independent direct Codex GPT6.1-Sol review are complete.

## Accepted decisions

[ADR0011](../adr/0011-ssh-terminal-investigation.md) completes P0-S3 as an investigation with separate positive finite SSH transport feasibility and experimental renderer outcomes. CAP-SSH and CAP-TERMINAL remain experiments. Their missing authentication/jump, composition, clipboard/menu, accessibility and performance routes remain explicit; sampled keyboard observations do not qualify those routes. The user's trackpad selection report is preserved as user-observed evidence. No composition IME is configured, so composition remains unrun.

[ADR0012](../adr/0012-foundation-construction-and-platform-acceptance.md) admits native construction under CAP-FOUNDATION using existing exact-build Android package/window/local-state evidence. CAP-PLATFORM remains experimental and required at R1/P2-S5, R2/P3-S5 and R4/P5-S4. P1-S1 is ready, not implemented. P1-S2/S3 retain their dependencies and full work. Product scope, credentials, independent releases, original evidence identities and diagnostic limitations are preserved.

The parent compared parsed old/new backlog records: all 40 stage IDs, task lists, deliverables, exit criteria and actual stage dependencies are unchanged. Stage changes are limited to P0-S3 done, P1-S1 ready and the six named capability mappings. Separate capability outcomes/evidence and the new protective ancestry constraints are explicit decision changes.

## Review finding and correction

The independent reviewer found that direct capability mappings alone did not protect the inherited R3/P6 acceptance paths from later dependency edits. The parent added required-ancestor constraints: P4-S4 retains P2-S5/P3-S5; P6-S1 retains P2-S5/P3-S5/P5-S4; P6-S4 retains P6-S1. The implementation worker added three mutation cases that sever those paths, including alternate and intermediate routes. The independent reviewer inspected the final correction and reported the finding resolved, with no remaining actionable finding in scope.

The validator also rejects removal/substitution of the three foundation and three platform-acceptance mappings. Existing evidence/scope/completed-deciding-stage checks remain active. Synthetic policy fixtures are explicitly test-only and are not qualification evidence.

## Executed checks and limits

| Check | Actual result |
| --- | --- |
| `python3 tools/check_ast.py` | PASS, all 30 authored Python files |
| `.venv/bin/ruff check .` | PASS |
| `.venv/bin/python -m unittest discover -s tests` | PASS, 121 tests in 6.480 seconds |
| Worker targeted validator suite | PASS, 16 tests; independent reviewer inspected rather than replayed the result |
| Parsed backlog comparison | PASS, all original stage work/exits/dependencies preserved; intended status/gate changes only |

Final project/schema and staged whitespace checks are recorded in [history](../00-project/history.md). Unchanged Android compiler/JVM/Lint and JavaScript evidence carries forward under D12; a validator/documentation change is not a reason to reinstall diagnostic APKs or repeat accepted physical cases. Exact preceding commit `42c7dcae09280244faf292eb3632eb70cdbd6d7f` passes all six hosted lanes, with local receipt `.cache/terminal-physical-01/ci-42c7dca-02.json`. The new commit needs its own hosted result.

## Dependency entry and next work

The [Compose entry](../07-reference/2026-10-16-compose-foundation-entry.md) records official stable candidate pins, compiler alignment and inspected compatibility floors. The parent independently parsed the BOM/AGP POM and UI/Activity AAR metadata; `.cache/foundation-entry-01/research/parent-check.json` retains artifact URLs, hashes and extracted values. These checks support attempting a build, not declaring the full dependency closure or device compatibility qualified. No third-party implementation is staged.

Next is P1-S1: resolve and verify the actual dependency closure, implement two real independent app targets with window naming/creation/restoration, then compile, lint and exercise package/window independence on the Googlebook. Full design, identity/security, engine adoption and release acceptance retain their later gates. The three unrelated untracked streaming-input files remain excluded. The implementation goal remains active.
