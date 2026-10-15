# Dependencies, maintenance, and licensing decisions

This is an engineering due-diligence register. Final distribution terms require review against the actual chosen code/version and project license; the table is not a legal conclusion.

Use the [established-library policy](../05-delivery/library-reuse-policy.md) when selecting and integrating these engines. Reuse the recorded version/license/provenance review until relevant inputs change; focus new verification on Cloudy's integration and observed defects. The actual SSH diagnostic now uses SSHJ 0.41.2 under its [adoption record](../06-validation/2026-10-15-ssh-dependency-adoption.md); the generic candidate rows below retain product-selection scope.

| Candidate | Intended role | Initial integration | Decision gate |
| --- | --- | --- | --- |
| Kotlin / Compose / AndroidX | Native app shell and platform behavior | Pin compatible artifacts after P0 | P1-S1 |
| Carbon | Design reference; optional reference components only if useful | Adapt concepts/tokens; no whole React shell dependency assumed | P1-S2 |
| Material 3 Compose | Native semantic components/theme | Cloudy component mapping and original tokens | P1-S2 |
| IBM Plex / Noto | UI/mono/CJK typography candidates | No font binaries shipped in initial specimen | P1-S4: actual font licenses/subsets |
| xterm.js | Terminal renderer candidate | Pinned local assets after input/security qualification | P0-S3 / P1-S4 |
| Maintained JVM SSH engine | SSH/SFTP/jumps/forwarding candidate | Compare Android support, algorithms, footprint, licensing | P0-S3 |
| libssh2-style native engine | Alternative SSH boundary | NDK packaging/update burden in same comparison | P0-S3 |
| Maintained Android X server; Termux:X11 reference | Local X server with native top-level broker | P0 prototype; no assumed shared UID/root/private API or embedding right | P0-S7 / P1-S4: exact engine/files/build/license and maintenance |
| FreeRDP | Native RDP and optional later RemoteApp | P0 3.32.2 arm64 native/JNI build accepted; Android integration pending | P1-S4: Apache-2.0 core plus selected MPL-2.0 JNI/Java files, actual dependency/codec obligations; preserve exact pin |
| Maintained RFB engine; LibVNCClient candidate | Native VNC client | P0 exact `98aaee0560baa0332339b2ef464ea96ed9b9b28c` source/build candidate, P3 integration | Three named fixes source-covered; 27 focused portable host regressions pass; Android/TLS/server integration pending. P1-S4: GPL file/license/dependency model |
| Sunshine | Host desktop backend | Separately installed | P0-S4 |
| Moonlight Android | Client baseline; possible later native integration | Pinned v12.2 native feasibility alongside external handoff | P1-S5: compatible open-source Desktop selected; exact source/notices/build/fork-maintenance obligations |
| VirtualHere | Android USB provider + host consumers | External licensed software | P0-S5 / P1-S4: trial/license/API/redistribution terms |
| Tailscale | Existing private network route | Official separate app | P0-S6: route/one-VPN behavior; no admin key in client |
| rclone | Optional Linux cloud-file adapter | Host-configured, not embedded by default | P3-S3/P5-S3: cache/conflicts and actual license |
| Go | Optional host core | Select toolchain at implementation | P1-S1/P4-S1 |

The [Moonlight repository](https://github.com/moonlight-stream/moonlight-android) identifies its GPL-3.0 license. Invoking an external app and distributing a derived/integrated implementation are distinct choices; do not assume one licenses the other. [VirtualHere's Android page](https://www.virtualhere.com/android) and [consumer API](https://www.virtualhere.com/client_api) are entry points; API availability does not grant redistribution rights. The [FreeRDP root license](https://github.com/FreeRDP/FreeRDP/blob/master/LICENSE) and [LibVNC copying terms](https://github.com/LibVNC/libvncserver/blob/master/COPYING) are starting evidence, not substitutes for reviewing the selected build.

Native RDP/VNC and X11 need P1-S4 decisions before their P2/P3 implementation. The native-stream adoption decision is also scheduled in the foundation at P1-S5; optional host adapters are not its prerequisite. Parsec/GeForce NOW are experience references only; no embedded SDK/client or proprietary assets are selected. Keep the [LibVNC security policy](https://github.com/LibVNC/libvncserver/security) and actual release/advisory fixes in the engine selection record.

## Required record before embedding

For every distributed dependency record version/commit, source URL, license identifier/text, local modifications, build reproducibility, update owner, security advisory route, notice/source obligations, and evidence for the actual permitted distribution. Create an SBOM for the selected release. Keep upstream forks isolated and minimize patches.

Select the project's own license before public code release. The current repository has no blanket open-source grant. Keep product-reference research distinct from implementation reuse and avoid copying proprietary assets, icons, screenshots, or component CSS into the product.

The [accepted VNC source selection](../07-reference/2026-10-15-vnc-patched-source-selection.md) resolves only exact source coverage for the three identified advisories. It does not establish runtime decoder safety, Android/device support, CAP-VNC acceptance or a distributable license arrangement.

The [FreeRDP native build](../06-validation/2026-10-15-native-rdp-build.md) records actual OpenSSL4.0.1, cJSON1.7.19 and uriparser1.0.2 outputs, the resource-only patch and mixed selected-file licenses. MediaCodec adds no bundled codec implementation; that fact does not settle AVC obligations. This private build does not decide product distribution terms.

## Selected Desktop distribution direction

The user selected [ADR0010](../adr/0010-desktop-open-source-distribution.md): open-source Desktop with compatible licensing and required source/notices/build materials. This settles the direction for the pinned Moonlight/VNC adoption path; exact release license expression and actual artifact closure remain review work. Console retains independent terms; no blanket repository grant is inferred. The [native-stream entry](googlebook/native-stream-adoption-entry.md) pins real source/submodule/static-input provenance; the [VNC regressions](../../qualification/results/P0-S4/2026-10-15-vnc-regressions.md) close only the named host decoder question.
