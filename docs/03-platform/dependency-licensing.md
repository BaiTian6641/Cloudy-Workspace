# Dependencies, maintenance, and licensing decisions

This is an engineering due-diligence register. Final distribution terms require review against the actual chosen code/version and project license; the table is not a legal conclusion.

| Candidate | Intended role | Initial integration | Decision gate |
| --- | --- | --- | --- |
| Kotlin / Compose / AndroidX | Native app shell and platform behavior | Pin compatible artifacts after P0 | P1-S1 |
| Carbon | Design reference; optional reference components only if useful | Adapt concepts/tokens; no whole React shell dependency assumed | P1-S2 |
| Material 3 Compose | Native semantic components/theme | Cloudy component mapping and original tokens | P1-S2 |
| IBM Plex / Noto | UI/mono/CJK typography candidates | No font binaries shipped in initial specimen | P1-S4: actual font licenses/subsets |
| xterm.js | Terminal renderer candidate | Pinned local assets after input/security qualification | P0-S3 / P1-S4 |
| Maintained JVM SSH engine | SSH/SFTP/jumps/forwarding candidate | Compare Android support, algorithms, footprint, licensing | P0-S3 |
| libssh2-style native engine | Alternative SSH boundary | NDK packaging/update burden in same comparison | P0-S3 |
| Sunshine | Host desktop backend | Separately installed | P0-S4 |
| Moonlight Android | Client baseline; possible later native integration | External app first | P5-S1: GPL-3.0 source/distribution and fork maintenance decision |
| VirtualHere | Android USB provider + host consumers | External licensed software | P0-S5 / P1-S4: trial/license/API/redistribution terms |
| Tailscale | Existing private network route | Official separate app | P0-S6: route/one-VPN behavior; no admin key in client |
| rclone | Optional Linux cloud-file adapter | Host-configured, not embedded by default | P3-S3/P5-S3: cache/conflicts and actual license |
| Go | Optional host core | Select toolchain at implementation | P1-S1/P4-S1 |

The [Moonlight repository](https://github.com/moonlight-stream/moonlight-android) identifies its GPL-3.0 license. Invoking an external app and distributing a derived/integrated implementation are distinct choices; do not assume one licenses the other. [VirtualHere's Android page](https://www.virtualhere.com/android) is the initial vendor entry point; integration rights and control interfaces remain a separate investigation.

## Required record before embedding

For every distributed dependency record version/commit, source URL, license identifier/text, local modifications, build reproducibility, update owner, security advisory route, notice/source obligations, and evidence for the actual permitted distribution. Create an SBOM for the selected release. Keep upstream forks isolated and minimize patches.

Select the project's own license before public code release. The current repository has no blanket open-source grant. Keep product-reference research distinct from implementation reuse and avoid copying proprietary assets, icons, screenshots, or component CSS into the product.
