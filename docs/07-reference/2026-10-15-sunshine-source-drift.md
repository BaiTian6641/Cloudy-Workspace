# Sunshine source divergence: release guidance and moving master

**Checked:** 2026-10-15, America/Chicago. **Scope:** research correction for R04/R05 and the Linux host report; no backend installation or capability qualification. The apparent macOS contradiction is a **release-versus-development-branch difference**. It is not evidence that the warning was removed and then restored on `master`.

## Verified identities and dates

| Evidence | Exact identity | Date observed in upstream metadata | Relevant finding |
| --- | --- | --- | --- |
| [Latest stable release](https://github.com/LizardByte/Sunshine/releases/tag/v2026.914.233613) | `v2026.914.233613`, resolving to commit `63d35f702ee9e362e43263742981836ec0710384` | Commit 2026-09-14 23:36:13 UTC; release published 2026-09-15 01:17:17 UTC | GitHub marks this release latest; `prerelease=false`, `draft=false` |
| [Stable getting-started source](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/docs/getting_started.md) | Same release commit | Last file change at that commit, 2026-09-14 23:36:13 UTC | macOS section retains the experimental warning and says gamepads do not work |
| [Inspected master snapshot](https://github.com/LizardByte/Sunshine/tree/f86c4c9c1113d4590ba1309ea1de2e57d47cbb22) | `f86c4c9c1113d4590ba1309ea1de2e57d47cbb22` | 2026-10-15 03:24:13 UTC | Development snapshot, not the latest stable package |
| [Master getting-started source](https://github.com/LizardByte/Sunshine/blob/f86c4c9c1113d4590ba1309ea1de2e57d47cbb22/docs/getting_started.md) | File's most recent change: `d6453a3ab2fbe7e1649d77105a42bd32182461c7` | 2026-10-08 04:37:44 UTC | Replaces the blanket warning with separately installed/licensed virtual-gamepad broker requirements; this does not establish Cloudy support |
| [Warning replacement change](https://github.com/LizardByte/Sunshine/commit/8fc40974c98991c7fffc24831cbe43e57b101cd9) | `8fc40974c98991c7fffc24831cbe43e57b101cd9`, macOS gamepad support change | 2026-09-27 20:20:34 UTC | Its getting-started diff removes the warning after the stable release and documents the separate broker |
| [Stable configuration source](https://github.com/LizardByte/Sunshine/blob/63d35f702ee9e362e43263742981836ec0710384/docs/configuration.md) | Last file change: `9124e77f8a59e0a8bfc8f490344839111b9b3427` | 2026-09-13 00:35:11 UTC | Already lists KWin, wlroots, KMS and X11 capture methods |
| [Master configuration source](https://github.com/LizardByte/Sunshine/blob/f86c4c9c1113d4590ba1309ea1de2e57d47cbb22/docs/configuration.md) | Last file change: `82b22c71f6d81d2def985fe0ef566b66893a4987` | 2026-10-11 16:02:38 UTC | Same capture categories remain, with later configuration changes |

The rendered [latest getting-started page](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2getting__started.html) contains the stable macOS warning at inspection. The page labels itself `latest` but exposes no exact source revision or reliable content-update date in the inspected content. Matching warning text is not proof that the entire rendered page is built from the stable tag. Do not assign it the master commit date.

These SHA-256 digests bind the raw UTF-8 Markdown fetched for this review; no third-party implementation was copied into the repository:

| Snapshot/file | SHA-256 |
| --- | --- |
| Stable `getting_started.md` | `0d23b0ac075707bd80c704612f3a9370b25eec4d4b1dd1fb1071a1f3e0b8f401` |
| Master `getting_started.md` | `9e4f662c971b07db6816a53fe4a072156dc80cee1613b31a3b8bd071904743b5` |
| Stable `configuration.md` | `52b96ef9c632e714bcbaa6cf7574d562660e2427ca73748a1b11a246340c7f35` |
| Master `configuration.md` | `b2d1f8ecb13fc6cae9c18c01478361a2393c580e5c757efb248df028968985f2` |

## What the divergence means

The stable source's macOS warning precedes both its DMG and Homebrew instructions. It therefore cannot be read as a warning limited to one of those installation routes. The inspected master source documents a separate licensed broker for virtual gamepads and explicitly separates that from standard macOS keyboard/mouse permission handling. A package installed through Homebrew, a DMG, or another route must be identified by its actual version/build before choosing the applicable documentation. This review installed no macOS package and tested none.

For Linux, both inspected getting-started revisions document no KMS support in the AppImage and Flatpak routes. Both configuration revisions list `kwin`, `wlr`, `kms` and `x11`; the KWin candidate is therefore not solely a post-release master claim. Package/backend/permission qualification is still required. These findings support the cautious capture-route wording in the [Linux host report](../03-platform/linux-primary-host-qualification.md).

## Corrections for integration by the primary agent

| Existing location | Problem | Suggested replacement |
| --- | --- | --- |
| [plan-review.md](../00-project/plan-review.md), “Sunshine wording” row | Treats the absence of a warning in moving getting-started source as the current general support position | “Stable release v2026.914.233613 retains the macOS experimental warning; inspected master has newer gamepad/broker guidance. Pin the chosen release/build and qualify each capture/input/permission capability.” |
| [googlebook/open-questions.md](../03-platform/googlebook/open-questions.md), Sunshine macOS row | “Current getting-started source no longer carries” is accurate only for the inspected master and obscures release guidance | “Release and master documentation differ. Stable v2026.914.233613 retains the warning; no macOS capability is qualified. Follow documentation for the selected package/build.” |
| [sources.json](sources.json), R05 note | Its master observation is too broad without a branch/date qualifier | “Moving master setup guidance differs from stable v2026.914.233613, whose macOS section retains the experimental warning. Use pinned release/build documentation; see the 2026-10-15 divergence record.” |
| [source-register.md](source-register.md), R05 row | Mirrors the ambiguous R05 summary | Use the same branch-versus-release distinction and link this record |
| R04 metadata or future pinned source entries | A generated `latest` page has no verified exact revision here | Preserve unknown update date for the rendered page; add distinct pinned release/master references if needed rather than pretending `latest` equals `master` |

When applying these corrections, update the relevant checked dates to 2026-10-15. `sourceUpdatedOn` for the moving R05 Markdown can record the inspected file-change date 2026-10-08 if the note makes clear what it dates; the experimental-warning replacement itself occurred on 2026-09-27. Preserve historical reachability reports and the immutable intake. No scope, backlog dependency or supported capability change follows from this documentation correction alone.

## Separate factual cross-check of the Linux report

This is a second source/observation check by the agent that authored the Linux report. It is **not independent reviewer acceptance**; the primary agent must inspect the result.

- OS/kernel, package versions, session environment, GPU-family/driver observations and tool visibility match the scoped command output. The report correctly avoids upgrading environment variables or installed packages into capture/input/backend success.
- Missing DRM/uinput nodes remain explicitly limited to this execution environment. No conclusion about physical absence, kernel hardware support or required host permission repair is justified yet.
- RDP/Xvnc session distinctions, TigerVNC 1.16.0 portal eligibility, VirtualHere's Linux USB/IP prerequisite and the selected-version Sunshine capture claims are supported by the linked upstream references. KRdp's stale README sections are explicitly identified rather than presented as 6.5.5 results.
- Suggested precision improvement: use the pinned stable configuration/getting-started links above in the Linux report's Sunshine paragraph, or add both stable and master links. Its current master link does not overclaim support, but a stable link makes reproduction easier.
- Suggested stage wording improvement: keep P0-S1's remaining inventory and diagnostic build/install/launch work distinct from the later real SSH/RDP/VNC/stream/USB qualification stages. Missing protocol results mean those capabilities remain pending; do not accidentally make every later protocol test a new P0-S1 prerequisite. The unknown board/probe remains an inventory gap.

No shared document, source register, device configuration or backend was changed by this research note.
