# Native stream APK: local loading observation

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4 remains in progress. **Result:** the separately named diagnostic loaded the actual Moonlight native core and returned real local metadata on Googlebook. This is a local ABI/loading result, **not a stream**. Read the [review](../../../docs/06-validation/2026-10-15-native-stream-apk-load.md), [probe recipe](../../probes/native-stream/README.md) and [hashed manifest](2026-10-15-native-stream-apk-load.json).

## Exact identities

Client `dev.cloudy.qualification.stream` **0.1.0/code1**, build reference `04cf72ef-ece2-4e15-b072-ef6e73dd203c`, uses the actual pinned Moonlight Android v12.2 Java/resource/JNI closure. APK **21,713,253 bytes**, SHA256 `b674ab7c55e4775ed3f79941b3ec95fa6acb20cd26e864d6f483f3146ca0958a`. Minimum API35, compile SDK37, inherited **target SDK36**. The app has not been relabelled target37.

Device: **HP Googlebook 14**, manufacturer HP, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`, security patch `2026-09-01`. No host or server was used.

The packaged native core is the accepted [native build](2026-10-15-native-stream-build.md), reference `f336e245-805d-4aad-bc1a-9208df8db318`: **5,110,376 bytes**, SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`. Assembly/import and APK audit preserve its exact bytes and 16 KiB alignment. APK signature verification passed. The final compiled/merged manifest has one owned Activity and zero permissions/providers/services/receivers. The source removal markers explicitly prevent OkHttp's network permission and AndroidX auto-initializer from entering this local probe.

## Actual observation

The operator installed/launched the diagnostic, selected its visible native-load action and observed the success UI. The app published exactly one record, ID `c90e90d1-01c8-4b9c-bf09-97f549a10e83`:

| Field | Recorded result |
| --- | --- |
| Status | `native-load-and-metadata-passed` |
| Failure class | none |
| Native metadata | `&corever=1` |
| Duration | **2,813,594 ns** |
| Host connections | **0** |
| Decoded frames | **0** |

The duration is one elapsed-time observation around first local class/native initialization and a metadata call. It is not network latency, decoder throughput, startup distribution or a performance benchmark. The payload is actual native output, with no simulated host/session/frame. No keys, pairing state or credential files were created by this action; the private app-files listing contains only the `native-load` directory and that single JSON. No screenshot was captured.

The initial layout predicate failed because it expected a nonempty window title. A corrected operator check used the unique visible heading and focused diagnostic component. This did not require an app change or represent an app crash. The operator closed the Activity and explicitly force-stopped the package. The stop command exited0; subsequent `pidof` exited1 with empty output. The APK remains installed and stopped.

## Build/review limits and retained history

Entry05 Android assembly/Lint passed. Final Lint reports zero new errors, 337 warnings and one baseline hint, with exactly 27 reviewed inactive upstream errors baselined. All 27 match entry01's error IDs/messages/locations, exclude owned code and must be retired for affected paths before activation. The final node-local `MissingClass` ignore applies only to the removed provider marker; it does not suppress a missing runtime component. Five focused Android-staging tests passed as reported by the parent. Runtime dependency records bind fourteen actual debug artifacts, without claiming a final SBOM or distribution closure.

Android entry01's 27-error Lint failure, entry02's missing-baseline-path failure, entry03's preparation-only superseded state and entry04's removal-marker Lint failure remain retained. Entry05 is the successful final stage. Earlier accepted native build evidence is reused, without another native rebuild or upstream protocol suite.

The next real increment needs app-owned non-exportable client identity, foreground authorization, pairing/trust and finite HTTP/native cancellation, followed by actual `NvConnection`, decoder/audio and Surface output. Existing [key-provider review](../../../docs/03-platform/googlebook/native-stream-key-provider-entry.md) identifies the time-limited authentication/TLS boundary. No pairing, certificate trust, stream, decoded image, hardware codec, physical input, audio, sustained use, latency, 3D or CAP-STREAM qualification is claimed. D16 permits compatible open-source Desktop adoption; exact dependency notices, corresponding source and build delivery remain release gates.

Private evidence lives at `qualification/results/private/native-stream/2026-10-15-load-01`; source/build/audit evidence is retained under `.cache/native-stream-build/entry-02` and `.cache/native-stream-android/entry-05`. The manifest binds selected owned sources, stage receipt, build/Lint/runtime/audit records, exact APK and device evidence by hash. It does not reproduce raw private inventories or logs.
