# P0-S4 — Paired application catalog build checkpoint

**2026-10-15: streaming diagnostic 0.1.8 / code 9 passes the actual build and bounded source review. Device catalog discovery and playback remain pending.** Build reference `eae22de0-8329-4400-b571-43958ba00e61` adds an explicit real paired HTTPS application-list operation and selection of an actual returned application ID. This is a build-only checkpoint, with no new installation or runtime observation. The [manifest](2026-10-15-stream-catalog-build.json) binds source inputs, preparation, build, package audit and tests; the [review](../../../docs/06-validation/2026-10-15-stream-catalog-review.md) records the authored boundaries and limits.

| Check | Retained result |
| --- | --- |
| Offline preparation | 670 staged files; exact pairing → playback → catalog overlay composition |
| Actual Android build | Assemble, Lint, JVM tests and runtime dependency recording exited 0; 40,006 ms |
| JVM tests | 38 tests, zero failures, errors or skips; nine new catalog cases |
| Android Lint | Zero owned findings and zero unbaselined errors; 27 inactive upstream baseline errors, 338 warnings and one hint retained |
| APK | 24,804,371 bytes; SHA256 `8221d871d9b9a99d87cb25f7f92f90c66b64c419d6db9c16fd40e5d7ce461f74` |
| Package audit | Actual manifest, native bytes, 16 KiB native ZIP alignment, signature continuity, selected notices and unchanged 39-artifact runtime closure checked |
| Runtime | No new device prompt, real catalog, application selection, launch, video/audio, physical input or performance observation |

The application ID is `dev.cloudy.qualification.stream`, minimum API 35 and compile/target API 37; identity qualification requires API 37. Permissions are unchanged. Test-only kxml2 2.3.0 supplies a real parser implementation for JVM tests and does not enter the runtime dependency closure. The native library remains 5,110,376 bytes with SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`. No native rebuild or unchanged engine revalidation was needed. The [earlier playback UI build](2026-10-15-stream-playback-ui-build.md) and [accepted first pairing result](2026-10-15-stream-first-pairing.md) retain their original separate scopes.

## Implemented operation

The [catalog controller and third overlay](../../probes/native-stream/catalog/README.md) use the exact patched Moonlight `NvHTTP.getAppListRaw()` and quiet upstream parser. The operation admits only GET HTTPS `/applist` on the approved immutable numeric peer and explicit positive HTTPS port. It verifies the exact certificate leaf, including otherwise CA-valid certificates, and keeps the actual call registered through bounded body consumption/cancellation. It does not bootstrap server information over HTTP, pair, accept a PIN, launch, resume, retry, redirect or unpair.

A private eight-field `no_backup/catalog-config.json` supplies public peer/trust configuration without an application ID. An explicit native button requires local-network permission, a READY app-owned identity and fresh DEVICE_CREDENTIAL authentication with actual type 1. Qualification, provider acquisition and the real catalog read share the same live attempt and original maximum 30-second callback window. No authorization or provider is reused after retirement.

The parser supplies actual IDs; policy accepts at most 128 positive distinct entries with bounded native text titles. A new read clears the old peer, list and selection before admission. Failed or empty results cannot leave a previous ID launchable, and stale generation callbacks cannot restore one. Deliberate selection constructs the existing immutable playback profile. Preview requires a separate fresh credential approval and has no guessed-index or old `playback-config.json` fallback. Catalog reading does not enter or consume the native playback allowance.

The catalog journal joins shared pairing/playback admission and exact-byte previous-process reconciliation. Unfinished, malformed or evidence-unavailable records block all three network lanes. Actual worker/prompt retirement and successful terminal write/readback precede catalog lease release. Journals retain typed completion/stage, actual call/body-byte counts and application count, excluding titles, XML, endpoints, certificate bodies, keys and PINs. Reconciliation preserves originals, records host state UNKNOWN and performs no network replay or host cleanup.

The third overlay changes only the controlled quiet parser. Leading whitespace before the first App no longer dereferences an empty list. Malformed IDs are rejected with a fixed error before the upstream String-ID setter can log raw peer text. Ordinary nonquiet behavior and the frozen identity/pairing/playback sources remain unchanged. Exact before/after hashes and new catalog source/test hashes are locked.

## Review and qualification limit

Independent read-only review found no actionable blocker in strict endpoint/leaf admission, call/body cancellation, original authorization age, actual ID selection, stale-result clearing, journal closure/reconciliation or the narrow parser changes. The author's strict scoped compile and 21 root JVM tests passed before the parent ran the full 38-test Android build. Four new tests execute the actual composed upstream parser; five exercise catalog selection, bounds and deadline/call cancellation policy. These are not AndroidKeyStore, prompt, TLS or host integration results.

A fail-closed lifecycle limit remains: finishing the Activity while a system prompt is outstanding can retain ownership and unfinished evidence until process restart rather than falsely close the operation. Restart does not itself reconcile durable evidence or establish host state. Native playback still permits one run per process. Physical catalog discovery, selected application availability, approved LAN media reachability, same-key authorization and actual playback remain pending. Frame presentation, audio, input, latency, reconnect, product vault, release-compliance closure and CAP-STREAM are not promoted. The 27 inactive upstream Lint entries must be revisited before their paths are enabled.
