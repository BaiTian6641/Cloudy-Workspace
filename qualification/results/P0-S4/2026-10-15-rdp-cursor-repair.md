# RDP cursor repair and physical capture follow-up

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4 remains in progress. **Result:** the user confirms **“Host trail and cursor move; release works”** with the repaired diagnostic. This is a bounded physical observation on the identified Googlebook and isolated GNOME server. Actual 3D behavior, performance and broader input/server coverage remain unqualified. Read the [source/build review](../../../docs/06-validation/2026-10-15-rdp-cursor-repair-review.md) and [hashed manifest](2026-10-15-rdp-cursor-repair.json).

## Exact candidate and setup

- Client: `dev.cloudy.qualification.rdp` **0.1.5/code6**, build reference `a5a5db42-1444-4642-bfff-aa63751a8e4f`. APK **12,746,630 bytes**, SHA256 `565ae8036040e34de98c1c83c1d365c43df83053188b7f65bbd61906578ea1df`.
- Device: HP Googlebook 14, Android API37, arm64-v8a, build `CL3B.260622.276.R2`, security patch `2026-09-01`. The user did not identify built-in trackpad versus external mouse or its model; no per-device qualification follows.
- Host: Ubuntu26.04, isolated GRD `50.2-0ubuntu0.1`, Mutter `50.1-0ubuntu2.4`, FreeRDP server `3.32.1+dfsg-0ubuntu0.26.04.1`, PipeWire `1.6.2-1ubuntu1.2`, WirePlumber `0.5.13-1ubuntu1`, libei `1.5.0-3`. Reuses the [reviewed private fixture method](2026-10-15-rdp-isolated-host.md), including surfaceless software rendering and a loopback/USB-ADB reverse route. No system package or desktop replacement.
- Host target: `host-motion-1`, Qt6.9.2; its own canvas events drive a bounded 64-point trail, crosshair and counter. Source/binary hashes and exact build inputs are bound in the manifest. Neither the target nor client prediction claims transport attribution.

## Repair and checks

The sixth reviewed native patch delivers the upstream `SetPosition` callback to the owning Java/session instance and enables its `FreeRDP_GrabMouse` dispatch prerequisite. The first five patches and standard signed-relative input route remain unchanged. Default, custom and hidden cursor states are distinct. Confirmed capture can show a default software arrow, while server-hidden stays hidden. Admitted relative movement advances a labelled local prediction; authoritative position, geometry or capture ownership changes take precedence over a crossing prediction. Prediction sends no input and cannot prove remote delivery. These boundaries follow [the exact upstream dispatch](https://github.com/FreeRDP/FreeRDP/blob/97eff6ddfbbfaa8e42a126d362686bd001ff8bdc/libfreerdp/cache/pointer.c) and the [Android capture contract](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement#pointer-capture).

Parent integration review and independent native/Java and Kotlin/UI cross-reviews found no blocking defects. Full native compilation/import, Android assembly and Android Lint with warnings treated as errors passed. **45 JVM tests passed, zero failures/errors/skips**; the 15 focused cursor/capture/failure tests are a subset, not an additional 15. The actual authored Java callback test, focused native `-Werror` compilation, strict host-target compilation, Python AST17/Ruff and 67 repository tests passed. One initial repository test command used the nonexistent `tools/tests` path and failed; the corrected `tests` run passed. It is retained as an operator error.

The packaged APK passed eight-library ELF/JNI inspection, byte comparisons for 21 notice/runtime assets, signature verification and 16KiB ZIP alignment. Earlier 0.1.4 APK and failed physical evidence remain preserved. Full source/native receipts and local checks are available in the ignored evidence directories listed below; they do not constitute hosted CI results for the future commit.

## Physical observation and evidence

Attempt13 (`2868d0af-58c1-40d0-9b99-887b723bafea`) establishes pinned NLA, real **1280 × 720** pixels, **565 frame updates**, **1,678 accepted JNI input submissions** and **1,037 relative submissions**. One certificate acceptance and one authentication callback are recorded; this run did not repeat the accepted wrong-pin test.

The operator focused the observed host canvas and requested capture through the visible control. The user exercised two capture/release intervals. The new journal preserves all eight request/confirmation/release edges, with zero omissions. Times below are elapsed milliseconds in this client attempt, not host timestamps or network latency:

| Interval | Framework confirmed | Release requested | Framework released | Relative submission increase |
| --- | ---: | ---: | ---: | ---: |
| 1 | 20,149 | 27,332 | 27,339 | 619 |
| 2 | 40,412 | 46,970 | 46,985 | 418 |

The controlled host target records **824 actual mouse moves**, one press/release pair and an empty held state at normal exit. The inspected screenshot shows its trail and count824 after release. These are whole-run host counts; they are not asserted to match relative submissions one for one or assigned to a capture interval by comparing independent clocks. The user explicitly confirms host trail and cursor movement while captured and working local release. That observation, the actual host rendering/events, and the client capture edges support this bounded result. A moving local prediction alone would not have passed it.

The [previous 0.1.4 GRD failure](2026-10-15-rdp-physical-cursor.md) and [KRdp relative-callback failure](2026-10-15-rdp-relative-portal.md) remain separate historical results. The earlier “pretty laggy” report occurred before capture on 0.1.4. This follow-up supplied no latency rating or measurement. The software compositor, diagnostic repaint path and USB relay are material test conditions; their individual contribution is not isolated. No latency improvement is claimed.

## Closure and remaining gates

All **13 attempts are closed**, and the first twelve records remain structurally identical. Closing the visible target produced `operator-close`, target exit0, zero held keys/buttons and normal server fixture termination. The client retains a `native-failure` terminal label with the upstream message that the user logged off the server session. Preserve that status: teardown joined the native thread and freed its instance, but the adapter does not yet classify intentional server logoff separately from failure.

The fixture completed in208.400s overall /206.833s active. GRD, Mutter, PipeWire, WirePlumber and namespace exited0; owned relays were terminated during cleanup. Both outer process groups, temporary keys/credentials, owned host listener, USB reverse and device fixture configuration are absent. The RDP app was explicitly stopped; the new APK remains installed. No journal was cleared or rotated.

Remaining work: actual3D consumption, input-device-specific cases, broader focus/disconnect/release behavior, authoritative position callback and hidden/default shape cases on real servers, sustained resources, measured latency, Windows/macOS lanes and the product credential vault. CAP-RDP and stage completion remain pending. Carry this accepted result forward instead of repeatedly retesting unchanged engine internals.

Private evidence roots, relative to the repository: `qualification/results/private/native-rdp-cursor/2026-10-15-run-01` (build/APK/review) and `qualification/results/private/native-rdp-session/2026-10-15-run-11` (device/operator/host). Raw screenshots, logs, endpoint identity and configuration remain ignored; the public manifest contains scoped observations and hashes.
