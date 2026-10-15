Cloudy diagnostic 0.3.0 — third-party runtime notices
Checked: 2026-10-15

This directory accompanies the Android diagnostic's 18 resolved runtime binary
artifacts. It retains license terms and notices independently of whether an
upstream AAR/JAR embeds those texts or Android resource packaging preserves them.
It contains no third-party implementation source.

Apache-2.0.txt is the full, unchanged Apache License 2.0 text from the pinned
Kotlin repository. Its GWT and Guava license files have identical bytes.
upstream/androidx-LICENSE.txt preserves the identical license entry found in
three runtime artifacts: annotation-jvm, window, and window-core-android.
runtime-copyrights.txt preserves source-archive copyright lines with duplicate
lines collapsed within each component and source comment delimiters removed.

Additional applicable notices and embedded-source terms:

* upstream/coroutines-NOTICE.txt and upstream/coroutines-LICENSE.txt retain the
  pinned kotlinx.coroutines upstream files. Its Maven JARs do not embed them.
* upstream/kotlin-COPYRIGHT.txt retains Kotlin's upstream copyright text.
* kotlin-stdlib-attributions.txt retains the upstream path-specific attributions
  for JVM/common stdlib code derived from GWT, Guava, ThreeTenBP, and Boost.
* upstream/kotlin-threetenbp-LICENSE.txt retains the BSD-3-Clause terms and
  attribution for the time implementation's embedded portion.
* upstream/kotlin-boost-LICENSE.txt retains the Boost Software License 1.0 text
  for the JVM math implementation's embedded portion.

provenance.json lists exact component versions, binary/source archive and POM
URLs and SHA-256 hashes, notice extraction paths, upstream revisions, and asset
hashes. Top-level Apache labels in a POM do not replace the additional embedded
portion notices above. Hashes bind fetched bytes, not publisher signatures.

Five dependency coordinates are metadata-only wrappers/BOMs; their resolved
binary variants are covered. Build-only compiler, Gradle/AGP, JUnit, and SDK
components are outside this app-runtime collection. The Kotlin compiler's own
NOTICE expressly concerns compiler distribution and is not included as an app
runtime notice. JS, Wasm and native-only stdlib portions are also excluded.

These files are source assets intended for assets/third_party_notices/ in the
APK. Final APK inspection must confirm package version 0.3.0 and byte equality
for every asset; source presence or a POM inventory alone is not packaging proof.
The diagnostic dependency collection is not a Cloudy product license decision.
