# ADR 0010 — Open-source Desktop distribution

**Status:** user selected; adopted direction, with dependency-specific release gates retained. **Date:** 2026-10-15. The user selected “Open-source Desktop with compatible licensing and required source/notices (Recommended).” ADR number 0010 was unused when this record was created. This refines the early adoption/distribution boundary in [ADR 0007](0007-publication-and-independent-delivery.md) and preserves [ADR 0001](0001-two-independent-apps.md).

## Decision

Develop and distribute Cloudy Desktop as open-source software under terms compatible with the actual selected components, including the intended Moonlight-derived native streaming and LibVNCClient paths. Required source, notices and build materials are part of the delivery, rather than reasons to exclude those engines in advance. The direction is selected now; the exact Desktop license expression and each release's complete dependency compatibility record still require review before publication.

This decision applies to Desktop. Console remains independently installable, useful and releasable, with its own dependency and distribution decisions. Shared source must have explicit compatible terms for its actual consumers; sharing source does not automatically relicense Console, create a shared credential owner or impose a permanently running shared daemon. No blanket repository license is granted by this ADR, and no root `LICENSE` is added. Existing third-party copyrights and license terms remain applicable to their files.

The user-directed private qualification/build work can proceed without another decision about whether Desktop may be open source. Neither this ADR nor a private build result is a public release approval. Functional, security, platform and distribution gates remain independent.

## Reviewed engine context

The [pinned native-stream entry](../03-platform/googlebook/native-stream-adoption-entry.md) records Moonlight Android `b48494cb96bff23d8886c4775cc4f39a1075495d` and common-C `874ac9548f1bd6f095ef2b435c42cdde460e7821`. Their inspected [Android license file](https://github.com/moonlight-stream/moonlight-android/blob/b48494cb96bff23d8886c4775cc4f39a1075495d/LICENSE.txt) and [common-C license file](https://github.com/moonlight-stream/moonlight-common-c/blob/874ac9548f1bd6f095ef2b435c42cdde460e7821/LICENSE.txt) contain GPLv3 text. This direction permits pursuing a compatible Desktop derivative; JNI boundaries do not establish permissive licensing or settle the complete combined-work analysis.

The [LibVNCClient selection](../07-reference/2026-10-15-vnc-patched-source-selection.md) pins `98aaee0560baa0332339b2ef464ea96ed9b9b28c`; its [public header](https://github.com/LibVNC/libvncserver/blob/98aaee0560baa0332339b2ef464ea96ed9b9b28c/include/rfb/rfbclient.h) specifies GPL-2.0-or-later and its [COPYING](https://github.com/LibVNC/libvncserver/blob/98aaee0560baa0332339b2ef464ea96ed9b9b28c/COPYING) supplies GPLv2 text. Record the actual selected license option and file/dependency closure before combining and distributing. The open-source direction supports this adoption review; it is not a finding that every optional dependency or linking arrangement is compatible.

Use the existing [dependency register](../03-platform/dependency-licensing.md) and [established-library policy](../05-delivery/library-reuse-policy.md). Retain accepted version-specific research until relevant inputs change. Do not repeat an exhaustive upstream audit or replace maintained engines merely because this direction has been selected.

## Required release record

Before distributing an integrated Desktop release, P1-S4/P1-S5 and the Desktop release gate must establish:

1. **Exact terms and scope:** the Desktop license expression; every shipped component's pinned version/commit and applicable file-level licenses; the chosen GPL options where relevant; modifications, static/native/JVM links and optional components actually included. Resolve incompatible or uncertain components explicitly rather than relying on an engine's root license alone.
2. **SBOM and provenance:** an SBOM matching the actual APK and any other shipped artifacts, including transitive JVM dependencies, bundled native archives/codecs, fonts and other assets. Bind it to source/output hashes and the release identity. Record maintenance/security update owners and upstream provenance.
3. **Corresponding source and build materials:** the applicable corresponding source for the delivered binary, local patches and generated-source inputs, dependency source/provenance and required configuration/build/install material. Record toolchain versions and commands sufficient to explain the actual build. Verify the selected source-delivery mechanism and its availability; do not equate links to moving upstream branches or a binary-only archive with this gate. Address additional applicable installation-information obligations for the actual distribution context.
4. **Notices and recipient access:** complete required license texts, copyright/attribution and modification notices, and any other applicable redistribution material. Make required source/license access available through the chosen distribution route and product notices, with requirements checked against that route. A generic notices screen alone does not satisfy every source-delivery obligation.
5. **Release review:** inspect the final packaged graph and source/notices bundle against the applicable terms, including the Moonlight native Opus/libcrypto and actual Android dependency closure. Retain unresolved questions as blockers for the affected release. A source pin, sanitizer pass or device result does not close distribution review.

These are engineering delivery gates, not a legal conclusion about every component or distribution arrangement. No proprietary reference application's code/assets are authorized for reuse by this decision. Separately installed host software and external app handoff retain their own terms; they are not automatically bundled with Desktop.

## Consequences and remaining scope

The open-source direction is no longer an unanswered product choice blocking the Moonlight/VNC adoption path. Exact licensing, source/notice packaging, fork maintenance and compatible dependency selection remain concrete implementation work. Native streaming still requires real pairing/trust, protected app-owned credentials, decoder/input/lifecycle integration and device evidence. VNC retains its Android, protected-server, input/lifecycle and security gates. No capability, latency, 3D or product completion status is promoted by this ADR.

The integration leader owns the ADR index, dependency/risk/backlog and execution/history updates associated with this decision. This bounded record changes only the distribution direction and its linked native-stream wording; it does not introduce a root license or change app independence.
