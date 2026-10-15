# Source and API tracking workflow

This is a manual development workflow. No recurring automation or external notifications have been configured.

## What to refresh

| Area | When | Review question | Owner role |
| --- | --- | --- | --- |
| Googlebook platform/VM | Weekly during P0/P1; before local-VM work | What shipped, what is still roadmap, and is there a public third-party API? | Platform |
| API/AndroidX versions | Before using/upgrading a surface | Exact signature, OS/library level, experimental annotation, fallback? | Android |
| FGS/background/permissions | Before lifecycle implementation and target upgrade | Is the chosen operation strategy valid on this target/build? | Android |
| Sunshine/Moonlight/USB | Before each backend version qualification | Supported capture/input/control/ownership behavior and license changes? | Integration |
| X11 / SSH / window broker | Before engine adoption and security updates | X11 engine hooks, isolation/authentication, native top-level window behavior and license/build changes? | X11/terminal |
| RDP / VNC engines and server matrix | Before embedding, upgrades and release | Actual release/commit, advisory fixes, license/dependencies, security/encoding/session and optional channels? | Desktop/native |
| Android packaging / desktop quality | At P1 and release-channel changes | Independent APK/AAB behavior, device targeting, applicable desktop guidelines and no phone/tablet scope drift? | Platform/maintainer |
| Carbon/M3 | When revising design foundations | Is the reference concept still current? Does it alter a Cloudy decision? | Design |
| Distribution/target SDK | Before release candidate | What are the actual current channel requirements? | Maintainer |

## Update procedure

1. Find the source/API ID and the linked feature/stage.
2. Open the official page and the exact API reference/release notes. Record publication/update/access dates separately.
3. Update `reviewStatus` only for content actually inspected. A JavaScript gate, access error, or search snippet is not a full signature review.
4. Add availability/version/deprecation changes to the API record, with a fallback and affected stage/ADR.
5. Compile/run a focused probe where behavior matters. Link the result before changing qualification to passed.
6. Run documentation checks and review all downstream feature/support claims.

## Reachability and drift helper

`python3 tools/check_sources.py --output artifacts/source-check.json` performs bounded read-only requests to registry URLs and writes status, final URL, response hash, and error. Compare with an earlier report using `--previous`. Hash changes are **review candidates**: navigation, cookies, localization, or timestamps may change without an API change. A stable hash also does not prove compatibility. The helper does not copy source bodies or mutate the registry automatically.

Use `--only G01,G06` for a small targeted check. Failed/blocked responses remain visible rather than being labeled dead official documentation. Dynamic-page entries still need a browser or another official readable representation.

The [initial reachability report](reachability-2026-10-14.json) records 53 successful responses. Its timestamps are UTC; the filename follows the project's America/Chicago research date. It does not change content-review or device-qualification status.

The [Revision 01 report](revision-01-reachability-2026-10-14.json) covers all 74 current references. Reachability is a read-only URL check; semantic source review, engine build verification and device qualification are separate work.

## Change record template

```text
Date / source ID / API ID:
Official page and version:
Observed change, with short supporting excerpt or paraphrase:
Previous assumption:
Affected requirements, stages, ADRs:
Probe needed and fallback:
Evidence path and reviewer:
```

Avoid mirroring full copyrighted manuals. Prefer short source-bound notes, exact symbol links, and reproducible experiments.
