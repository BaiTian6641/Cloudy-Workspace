# Product foundation toolchain and package contract

Checked 2026-10-16. The root Gradle graph builds the two actual local-window apps; diagnostics retain their independent frozen build recipes. See the [candidate research](../../07-reference/2026-10-16-compose-foundation-entry.md), [module graph](../../02-architecture/module-boundaries.md) and [test strategy](../../06-validation/foundation-testing.md). Remote engines and the full native component catalog are later stage work.

| Input | Selected value |
| --- | --- |
| Runtime / bytecode | JDK21 / Java and Kotlin17 |
| Gradle / AGP | 9.6.0 / 9.4.0 |
| Kotlin / Compose compiler plugin | Both2.2.10; AGP built-in Android Kotlin |
| SDK / Build Tools | Android37.0 platform revision2, extension22 / 36.0.0 |
| App min / target | 35 / 37 |
| Compose BOM | 2026.09.00; runtime/saveable/UI/foundation1.12.1, Material3 1.4.0 |
| Activity | 1.13.0 |
| Vault prompt host | Biometric1.1.0 / Fragment1.9.1; dedicated FragmentActivity |
| Tests only | JUnit4.13.2; runner/core1.7.0; extJUnit1.3.0; Compose test1.12.1 v2 rule; Espresso core/idling3.7.0 |
| Package / version | `io.github.baitian6641.cloudy.console`, `.desktop`; candidate0.1.3/code4 built only; installed0.1.2/code3 (P1-S1 qualified0.1.0/code1) |

Use the root wrapper, never a globally selected Gradle. Its JAR SHA256 is `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`; the distribution SHA256 is `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`. The version catalog, module lockfiles and verification metadata are committed inputs. Strict lock mode rejects unrecorded selected configurations; checksum verification rejects unreviewed artifact bytes. Generating either record is a deliberate reviewed dependency change, never an ordinary CI step. The verification file can contain metadata/transitively requested versions that are not selected runtime dependencies; use the module lock configuration when determining the resolved graph.

Both runtime graphs currently select110 external component coordinates, including BOM/metadata components, and Kotlin stdlib2.2.10. This is not an artifact count or completed distribution SBOM. The sole packaged native library is the upstream `libandroidx.graphics.path.so` in arm64-v8a, armeabi-v7a, x86 and x86_64. No authored JNI/NDK build or remote engine is part of this increment; prebuilt native bytes still need their P1-S4 closure/license review. Only actual target ABIs can receive device qualification.

Both debug APKs use normal local Android debug signing, separate package UIDs/data and `documentLaunchMode=always` with standard launch mode. Incoming launcher extras/data do not restore state. New Window requests a distinct document; task-label updates and removal are guarded by task-root ownership. Each app has an original adaptive/monochrome icon. The final merged permission set contains USE_BIOMETRIC/USE_FINGERPRINT from AndroidX Biometric plus the app-specific AndroidX signature permission for non-exported receiver support; there is no Internet, local-network, storage or foreground-service permission in this slice.

All nine Android extraction domains are excluded from both cloud backup and device transfer, and allowBackup remains false. G136 documents the OEM caveat for allowBackup alone; the explicit XML is configured policy, not an executed migration test. The implemented vault source uses owning-app credential-encrypted no-backup storage; actual product protection/revocation tests remain pending. See the [dependency review](../../07-reference/2026-10-16-vault-dependency-review.md).

## Build and distribution boundaries

Set `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_USER_HOME` and `GRADLE_USER_HOME` to the reviewed local installation/cache, then use the exact commands in the [testing strategy](../../06-validation/foundation-testing.md). Keep SDKs, caches, signing keys and local inventories outside Git. The production-source build has one narrowly scoped Android Lint exception: the wrapper-file Gradle update notice, because Gradle9.6 is deliberately pinned to AGP9.4's documented baseline. Authored source/resource warnings, dependency checks and all other notices remain errors.

An APK is directly installable through the authorized Android package manager tooling. An AAB is a publishing input from which a distributor generates device APKs; it is not an `adb install` substitute. These debug APKs are development artifacts, not a Play publishing or release-signing qualification. Independent update/uninstall, lifecycle and physical Googlebook evidence are recorded separately from a compiler or hosted CI pass.

## Integration lessons to preserve

- `ComponentActivity` is part of the public base Activity API, so platform declares Activity with `api`; app subclasses must see that inherited classpath.
- AGP built-in Kotlin custom test directories use `AndroidSourceSet.kotlin.directories` (G45), not Java source wiring. No duplicate Kotlin Android plugin is applied.
- A clean build corrected stale resource-merge output after icon directory renaming; keep that failed build separate from the accepted artifact.
- Compose1.12.1 deprecates the v1 test-rule factory. Use its v2 factory with explicit idle synchronization; newer1.13-only configuration examples are not part of this pin.
- `rememberSaveable` inputs do not authenticate restored state (G179). The draft envelope explicitly includes and validates the accepted logical window ID, bounded text and selection. A rejected window snapshot cannot import the preceding window's draft.
- Activity recreation tests and framework-driven text edits do not establish process-death persistence, physical keyboard/IME, caption pixels or a usable remote feature.

The first hosted product run fails at the Gradle step. A fresh local dependency-cache reproduction identifies five missing parent/BOM metadata checksum entries; their official bytes are checked and pinned without changing selected dependencies. The [clean-build correction](../../06-validation/2026-10-16-foundation-clean-build.md) records strict resolution and a full clean compilation, preserving the original failure. Hosted recovery requires the next exact-commit result.

The [0.1.3 manual qualification increment](../../06-validation/2026-10-16-vault-harness-review.md) wires a non-exported debug-only Activity and one opt-in instrumentation case separately into each app. Both actual merged release manifests exclude that Activity. The diagnostic onCreate alone permits fixed English labels through SetTextI18n; it is not a product localization waiver. Strict build/Lint and source/test discovery pass without dependency lock/version/checksum changes. The candidate is not installed;0.1.2 UI entry is recorded separately.
