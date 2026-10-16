# P0-S4 — Sunshine version compatibility build

**2026-10-15: streaming diagnostic 0.1.9 / code 10 passes the actual compatibility build; a new device attempt remains separate and pending.** Build reference `a9159dd4-2b44-4fc4-9818-bbc5ba1876f5` admits Sunshine's documented application-version sentinel through a narrow fourth staged overlay. The [manifest](2026-10-15-stream-compatibility-build.json) binds preparation/source inputs, patch, build, package audit, test reports and repository checks. This checkpoint contains no new installation, authentication, host launch, native media, video/audio or performance result.

| Check | Actual retained result |
| --- | --- |
| Offline preparation | 671 staged files; pairing → playback → catalog → compatibility overlay order |
| Android build | Assemble, Lint, JVM tests and runtime dependency recording exited 0; 40,203 ms |
| JVM tests | 42 tests, zero failures, errors or skips; four new compatibility cases |
| Android Lint | Zero owned findings and zero unbaselined errors; 27 inactive upstream baseline errors, 338 warnings and one hint retained |
| APK | 24,804,371 bytes; SHA256 `813fad6c49f3ffd08afbe5b1ffeabd834cbea0a271f9552db8f4f618c43631da` |
| Package audit | Manifest, signature continuity, selected notices, unchanged native bytes/16 KiB native ZIP alignment and unchanged 39-artifact runtime closure checked |
| Repository checks | Python AST, Ruff, 102 Python tests and schema-enabled project validation exited 0 |

The [fourth overlay](../../probes/native-stream/compatibility/README.md) targets only staged `PlaybackPolicy.serverVersion`. For the required application-version call (`optional=false`), it admits exactly `-1` as the fourth component alongside existing unsigned versions. Optional GFE versions remain unsigned or absent; component count/size, whitespace, other signs and malformed variants remain rejected. The original string passes upstream unchanged. Pinned Sunshine explicitly defines `7.1.431.-1` and documents its negative final component as the Sunshine marker; pinned Moonlight Java/native parsers already accept it. The earlier unsigned-only guard was Cloudy's restriction. Source links and the causal-inference limit are recorded in the overlay README.

The frozen root policy remains SHA256 `a9740afde71a561e5bef475d9b2e988bb549b0ee539669c8618c2583970ad11d`; the staged patched policy is `d2e306bb17d6585b0443d7d0462e55e9ba0969846fc8b83350621a4f622a870c`. Exact replay with zero fuzz and strict Java 17 compilation passed. Twelve focused tests ran against the real staged policy: four new compatibility cases and eight existing playback policy cases. Independent read-only review found the regex bounds, tests, prerequisite and locked hashes coherent, with no blocker.

The historical **0.1.8 APK, source preparation and result remain unchanged**. For 0.1.9 only, an incidental whitespace-only blank line in root `CatalogController.java` was trimmed and its authored-source pin refreshed. The catalog parser patch did not change. Earlier frozen overlays and the original playback policy were preserved. Native bytes remain SHA256 `95640675b34cd4d1a7908ff4e22949af7d214b8771e1c3bc954dbc21eea99a98`; no native rebuild was performed.

The application remains `dev.cloudy.qualification.stream`, minimum API 35 and compile/target API 37; identity qualification requires API 37. Trust, credential policy, deadlines, selected application, cancellation, launch and diagnostics interfaces are not expanded by this patch. The earlier 0.1.8 pre-native START failure is retained; its exact exception was not recorded, so rejection of the documented sentinel remains a strongly source-supported causal inference rather than a captured exception. A fresh finite device attempt must establish advancement beyond that boundary. No playback, cleanup-runtime, reconnect, vault, latency, release-compliance or CAP-STREAM promotion follows from this build. Existing inactive Lint and D16 source/notices gates remain open.
