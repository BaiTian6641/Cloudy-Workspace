# RDP rendering sample: status-update candidate

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4 remains in progress. **Result:** the bounded status/control change passed build, Lint and 48 JVM tests and completed a matched synthetic rendering workload. **No useful timing improvement was demonstrated.** This is Android UI rendering evidence, not physical input-to-photon or whole-RDP performance qualification. See the [review](../../../docs/06-validation/2026-10-15-rdp-status-review.md), [performance entry](../../../docs/03-platform/googlebook/rdp-performance-entry.md) and [hashed manifest](2026-10-15-rdp-rendering.json).

## Exact builds and setup

| Build | Version / code | Build reference | Preserved APK |
| --- | --- | --- | --- |
| Baseline | 0.1.5 / 6 | `a5a5db42-1444-4642-bfff-aa63751a8e4f` | 12,746,630 bytes; SHA256 `565ae8036040e34de98c1c83c1d365c43df83053188b7f65bbd61906578ea1df` |
| Candidate | 0.1.6 / 7 | `27cb59d8-a829-4424-a888-db41259a006c` | 12,763,022 bytes; SHA256 `4af1c2c4d51a90a80e11605805b69d2b6198fd999e70a1e74f2a8557faa63168` |

The two APKs are retained in private `native-rdp-performance/2026-10-15-run-01`. Candidate native libraries (eight) and assets (21) are byte-identical to baseline. Only the status/control policy and version identity changed; framebuffer/view/session/native input behavior was preserved. No native build was rerun for this increment.

Device: **HP Googlebook 14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, security patch `2026-09-01`. Host: Ubuntu26.04, isolated GNOME Remote Desktop `50.2-0ubuntu0.1`, Mutter `50.1-0ubuntu2.4`, FreeRDP server `3.32.1+dfsg-0ubuntu0.26.04.1`, PipeWire `1.6.2-1ubuntu1.2`, WirePlumber `0.5.13-1ubuntu1`, libei `1.5.0-3`. Both use the same software-rendered `host-motion-1` Qt6.9.2 fixture, **1280 × 720** session geometry, device window and loopback/USB-ADB reverse route. This reuses the [isolated fixture method](2026-10-15-rdp-isolated-host.md), not a production host or network benchmark.

## Workload and observed rendering

The operator reset `gfxinfo` before each workload and injected the same four ADB `input mouse swipe` operations, three seconds each, on the target canvas. All eight command receipts exited0. These are synthetic absolute pointer inputs, not physical captured movement. Baseline is private session run12, attempt14; candidate is run13, attempt15. The runs are a finite matched sequential comparison, without repeated/randomized trials, input-to-photon timing or uncertainty estimates sufficient for a product latency claim.

| Workload `gfxinfo` summary | Baseline 0.1.5 | Candidate 0.1.6 |
| --- | ---: | ---: |
| Android frames rendered | 1,426 | 1,442 |
| Janky frames | 1 (0.07%) | 1 (0.07%) |
| Frame-time p95 | 11 ms | 11 ms |
| Frame-time p99 | 14 ms | 15 ms |

Repeated process/window summaries in each raw file describe the same totals and are not summed. Android rendering frames are not RDP framebuffer updates. No useful timing improvement follows from these samples; the one-millisecond p99 difference also does not establish a general regression from a single short comparison.

Baseline's earlier file named `idle-gfxinfo.txt` includes warmup and operator screen activity. Its 455 frames, 136 janky frames (29.89%) and p95 23 ms are a separate observation, **not a clean idle workload or the baseline for the matched swipe comparison**. The baseline workload's “High input latency” counter1089 is an Android diagnostic counter, not a physical or end-to-end latency measurement. Status operation reduction is supported by source/policy tests, not a measured operation trace. These samples do not identify status rendering as the cause of the user's earlier 0.1.4 “pretty laggy” observation.

## Session preservation and closure

Baseline attempt14 (`f94e9a4b-aa36-4808-803c-faddfdb06179`) closed with **829 RDP frame updates, 1,418 accepted JNI inputs and zero relative inputs**. Candidate attempt15 (`4ef3bd21-c297-4758-9cad-349cb1f0df29`) closed with **793 updates, 1,433 accepted JNI inputs and zero relative inputs**. Each records one certificate acceptance, one authentication callback, relative capability available and no capture edges. JNI acceptance is not wire delivery. Neither run is a new physical capture/release test; [attempt13](2026-10-15-rdp-cursor-repair.md) remains the retained user-confirmed physical pass.

All **15 attempts are closed**. Direct journal comparison preserves the first14 records unchanged; baseline likewise preserved its first13. Both new attempts joined their native threads and freed their instances. Closing the target produced `operator-close`, target exit0 and empty held keys/buttons. The client retains `native-failure` with the server-logoff category; successful resource cleanup does not relabel that terminal status as success.

Both fixtures completed with GRD, Mutter, PipeWire, WirePlumber and namespace exit0; owned relays exited143 during cleanup. The recorded outer process groups are absent and temporary private keys, client configuration and credential directories are absent, with no secret-cleanup errors. Final cleanup observations cover app stop, absent host listener and journal preservation. The candidate remains installed but stopped.

One operator cleanup invocation reused the existing `final-user.json` output label. The exclusive receipt writer failed before executing cleanup; it did not overwrite the prior receipt or establish cleanup success. The operator retried with fresh `final-route-config-*` labels. Those successful command receipts record reverse removal, an empty subsequent reverse list and device fixture-config removal. The failure is retained here as an operator receipt note; there is no fabricated failed receipt file. `final-cleanup.json` covers host/app/history, while the fresh route/config receipts cover those additional cleanup actions.

## Acceptance limit and next step

The finite status-update experiment has met its stop condition. The bounded UI correctness change is accepted for continued qualification, without a performance improvement claim. Physical3D, sustained resources, input-device coverage, Windows/macOS, protected product credentials and full CAP-RDP acceptance remain pending. Next performance work should instrument host stages and client copy/draw/monitor/owner boundaries with a documented clock and finite workload, preserving bitmap lifetime and input order. It should not continue a status-lag attribution unsupported by this comparison.

Raw logs, screenshots, journals and endpoint/configuration data remain private under `qualification/results/private/native-rdp-session/2026-10-15-run-12` and `2026-10-15-run-13`. Public evidence contains only scoped observations, approved hardware/software identities, source/artifact paths and hashes.
