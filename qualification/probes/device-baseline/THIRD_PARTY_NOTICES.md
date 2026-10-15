# Diagnostic runtime licenses and notices

**Reviewed:** 2026-10-15. **Scope:** the 0.3.0 engagement diagnostic's resolved runtime dependencies. This collection does not select Console/Desktop dependencies or a product distribution license. Final APK inclusion remains a separate build-output check.

The [bundled notice directory](app/src/main/assets/third_party_notices/README.txt) contains readable license and notice texts. Its [provenance manifest](app/src/main/assets/third_party_notices/provenance.json) records exact component versions, canonical binary/source/POM URLs and SHA-256 values, source entry paths, upstream revisions, and copied-file hashes. Runtime binary hashes were independently checked against the project's Gradle verification metadata. No dependency implementation was copied into the repository.

## Runtime coverage

The locked runtime graph has **18 binary artifacts**. All have a top-level Apache-2.0 declaration, with the additional embedded Kotlin stdlib terms described below. Debug and release runtime coordinates must remain consistent with this inventory; changes require rechecking the actual artifact and upstream notices.

| Runtime binaries | Version |
| --- | --- |
| `androidx.window:window`, `androidx.window:window-core-android` | 1.6.0-alpha05 |
| `androidx.core:core` | 1.8.0 |
| `androidx.annotation:annotation-experimental` | 1.1.0 |
| `androidx.annotation:annotation-jvm` | 1.9.1 |
| `androidx.arch.core:core-common`, `androidx.arch.core:core-runtime` | 2.1.0 |
| `androidx.collection:collection-jvm` | 1.4.2 |
| `androidx.concurrent:concurrent-futures` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common`, `androidx.lifecycle:lifecycle-runtime` | 2.3.1 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `com.google.guava:listenablefuture` | 1.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.9.0 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.jspecify:jspecify` | 1.0.0 |

Five further runtime coordinates are metadata-only: annotation, collection, window-core, coroutines-core and coroutines-bom. Their resolved binary variants are included above. Compiler/Gradle/AGP, JUnit and SDK artifacts are not app runtime dependencies and are excluded from this bundle.

## What artifact inspection found

The binary archives, including JARs nested in AARs, were inspected for license, NOTICE, COPYING and copyright files. `annotation-jvm`, `window` and `window-core-android` each embed an identical Apache license entry; it is retained once as [androidx-LICENSE.txt](app/src/main/assets/third_party_notices/upstream/androidx-LICENSE.txt). The other runtime binaries have no matching embedded text file. Matching official source archives supply copyright notices, transcribed in [runtime-copyrights.txt](app/src/main/assets/third_party_notices/runtime-copyrights.txt); deduplication and representative source entries are recorded in the provenance manifest.

POM labels alone miss relevant runtime notices:

- **Coroutines:** retain the upstream [NOTICE](app/src/main/assets/third_party_notices/upstream/coroutines-NOTICE.txt) and [license](app/src/main/assets/third_party_notices/upstream/coroutines-LICENSE.txt) from revision `d8d6f8f37978b8e202d93b34f23f101df9c5724d`, release 1.9.0. These files are absent from the published runtime JARs. [Pinned upstream NOTICE](https://github.com/Kotlin/kotlinx.coroutines/blob/d8d6f8f37978b8e202d93b34f23f101df9c5724d/license/NOTICE.txt)
- **Kotlin stdlib:** its common/JVM implementation incorporates GWT and Guava code under Apache-2.0, ThreeTenBP time code under BSD-3-Clause, and Boost JVM math code under BSL-1.0. Retain the [specific attributions](app/src/main/assets/third_party_notices/kotlin-stdlib-attributions.txt), [ThreeTenBP terms](app/src/main/assets/third_party_notices/upstream/kotlin-threetenbp-LICENSE.txt), [Boost terms](app/src/main/assets/third_party_notices/upstream/kotlin-boost-LICENSE.txt) and [Kotlin copyright](app/src/main/assets/third_party_notices/upstream/kotlin-COPYRIGHT.txt). These come from revision `c448af19ded1b1a4e96e9af6412cd9acb100ce1a`, release v2.2.10. The shared [Apache terms](app/src/main/assets/third_party_notices/Apache-2.0.txt) are byte-identical to that revision's main, GWT and Guava license files. [Pinned upstream scope mapping](https://github.com/JetBrains/kotlin/blob/c448af19ded1b1a4e96e9af6412cd9acb100ce1a/license/README.md)

The Kotlin repository's `license/NOTICE.txt` explicitly concerns the **compiler distribution**; that compiler notice and compiler-only third-party licenses are excluded. The selected stdlib notices cover the actual common/JVM portions, not JS, Wasm, native or tests. License files are retained verbatim; the stdlib attribution file contains labeled, verbatim relevant path blocks from the upstream mapping, and copyright lines are transcribed with comment delimiters removed.

## Required packaging verification

After the final 0.3.0 build, inspect the APK package/version and compare every file under `app/src/main/assets/third_party_notices/` with the corresponding `assets/third_party_notices/` ZIP entry. Verify byte equality/SHA-256, not only entry names. Reconcile the locked debug/release runtime graph with the provenance component list and keep the result with the exact APK hash. A successful source-file check or dependency POM inventory does not establish that the APK carries these notices.

This collection intentionally remains separate from build/device qualification. No API, input, lifecycle or product capability is qualified by the presence of license assets.
