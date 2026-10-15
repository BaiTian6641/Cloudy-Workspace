# P0-S1 diagnostic probe: stage code review

**Reviewed:** 2026-10-15. **Source checkpoint:** `cf6630b6ff98ea8131ced1ef7ebaef36a33494af`. Scope is the native diagnostic APK, its build/test/CI setup, reference provenance, Linux prerequisites and the user's deferred USB hardware choice. This is not release acceptance for Console or Desktop.

## Independent review and corrections

The implementation agent owned probe source/resources. A separate reviewer checked framework/API availability, lifecycle and threading, privacy, bounded collection, caption handling, persistence and meaningful test coverage. A separate tooling/research reviewer checked CI pins, official references, documentation consistency and the user's scope decisions. The primary agent reviewed their changes, ran the build/checks and controlled the single selected ADB target.

| Finding | Final correction |
| --- | --- |
| `ALL_CODECS` could make special-only decoders look ordinary | Video-only entries now include both supported and required low-latency/secure/tunneled flags, aliases and explicit reported-property labels |
| New observations could leave a stale saved-status message visible | Changed event/window state publishes pending; state is read at main-thread delivery so an older worker notification cannot restore an obsolete saved revision |
| Framework atomic-write completion can log commit failures without throwing | A bounded byte-for-byte readback must match before the saved revision advances; errors remain visible, with no power-loss durability claim |
| First compile found an incorrect accessibility property and an internal type exposed by a public callback | Correct platform property and a private activity listener delegate; no disabled compiler checks |
| Toolchain caches used read-only defaults | Documented local Android cache variables and Kotlin's supported in-process strategy; no host permission or trust weakening |
| SDK package names can resolve to newer revisions | CI verifies installed API 37.0, revision 2, base extension 22 and Build Tools 36.0.0 after installation |
| Moving Sunshine master and stable release differed | Recorded exact source identities and corrected overly broad macOS wording without changing support gates |

The implementation reviewer accepted the final source after re-reading the persistence, pending-status and narrow lint changes. All activity listeners/callbacks and worker ownership were examined. Decoder and input inventories are explicitly observations, not successful stream/input tests. The final file-specific lint exception permits the Gradle wrapper update advisory while preserving all other source/resource checks and warnings-as-errors.

## Verification performed

- Kotlin compiler analysis and APK assembly passed with warnings as errors. This includes actual Kotlin syntax, type and symbol checking; a separate superficial AST parser is not substituted for compilation.
- Android Lint passed with **No issues found** under the documented pin exception.
- Three JVM tests passed: bounded retention/eviction order, snapshot independence and invalid-capacity rejection. No test-only success is used for a device gate.
- A normal build passed after dependency lock/checksum generation, with verification enforced and no regeneration flags.
- Repository Python AST/compile and Ruff checks passed, all nine planning-policy regression tests passed, schema/documentation validation passed, specimen JavaScript syntax passed, and staged whitespace checks passed.
- Workflow YAML and embedded shell syntax parsed; its SDK revision assertions were executed against the actual installed SDK and passed.
- [Actual device evidence](../../qualification/results/P0-S1/2026-10-15-device-probe.md) records installation, cold launch, fresh report export, Refresh behavior and ADB-driven background/return callbacks on the identified HP build.

CI outcomes must be checked for the exact pushed commit. The initial repository CI passed, while the first Android CI job failed after SDK setup; a clean-cache local reproduction identified five missing transitive metadata checksums. They were checked against canonical Maven Central downloads and added; the clean checkout subsequently passed all 49 tasks with verification enabled. Correction `3dfd0705155ef5481e6d40c773ffdbfcb17ade86` then passed hosted [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38688126844) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38688126993). These are actual checked conclusions for that commit.

## Remaining boundaries

No physical keyboard/trackpad/IME, resize/fullscreen/display matrix, process-death recovery, actual codec operation, latency, SSH/SFTP, RDP/VNC, X11, USB or operating-system support capability is promoted by this review. The later [inventory closeout](../../qualification/results/P0-S1/2026-10-15-setup-closeout.md) completes P0-S1 setup with explicit gaps; every feature capability gate remains pending. The no-placeholder rule applies to all later stages; a scoped diagnostic app is complete only for its stated observations.

## Inventory closeout review

A separate read-only review checked the later closeout against the private normalized inventory and exact raw/public hashes, packet counts, DMI identity and backlog boundaries. It caught ambiguous generic endpoint/result fields after LAN became the primary route. The primary agent corrected those fields: the private `primaryRoute` now binds the LAN endpoint and successful shell result, and `tailnetRoute` separately retains its unsuccessful shell observation. No address or account value was added to public evidence. The public manifest remained unchanged because its LAN/tailnet observations were already separate.

Repository AST/Ruff checks, all nine planning regressions, schema/documentation validation, JavaScript syntax and whitespace checks passed after the closeout edits. No APK source changed after the physically tested checkpoint or checksum correction. Completing setup permits P0-S2 to become ready; it does not mark full Q-P01 or a feature capability supported.
