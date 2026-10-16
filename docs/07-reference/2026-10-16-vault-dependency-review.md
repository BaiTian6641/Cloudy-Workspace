# Vault prompt dependency review

Checked 2026-10-16 for P1-S3 against the published release pages, downloaded Google Maven metadata and the actual selected runtime locks. [Biometric releases](https://developer.android.com/jetpack/androidx/releases/biometric) still identify **1.1.0** as stable; 1.4 is alpha. [Fragment releases](https://developer.android.com/jetpack/androidx/releases/fragment) identify **1.9.1** as the selected current stable version. No alpha/biometric-ktx/biometric-compose is adopted.

Stable `androidx.biometric.BiometricPrompt` uses a FragmentActivity host. The new vault management Activity supplies that host while retaining native Compose; the existing workspace Activity does not change its base class. The AndroidX lifecycle contract requires callback reattachment on recreation, while Cloudy separately revokes authorization and correlates the old prompt session. These are reviewed source decisions, not actual Googlebook prompt evidence.

## Actual selected closure

Console and Desktop each resolve **110 external component coordinates** in `debugRuntimeClasspath`, compared with96 before the increment. Coordinates include metadata/BOM components; they are not APK file counts or a distribution SBOM. The two newly declared version pins are Biometric1.1.0 and Fragment1.9.1. Their actual transitive graph also selects:

| Dependency family | Selected change and cause |
| --- | --- |
| Lifecycle | 2.9.4 to2.10.0; Fragment1.9.1 declares2.10.0 requirements |
| Tracing | 1.2.0 to2.0.0; required by Fragment |
| SavedState / Collection / Annotation | 1.3.2 to1.4.0 / 1.5.0 to1.6.0 / 1.9.1 to1.10.0 in the resolved AndroidX closure |
| AppCompat and resources | 1.2.0, explicitly requested by stable Biometric |
| Additional legacy View support | cursoradapter, customview, drawerlayout, vector drawables, loader and viewpager are actual transitive runtime dependencies |

The inspected Fragment AAR declares minSdk23, minCompileSdk34 and minAGP8.1.1; Lifecycle Runtime Android2.10.0 requires compile34/AGP8.1.1. Fragment's Kotlin stdlib floor2.1.20 is below the project's2.2.10. These fit app min35/compile37/AGP9.4 and the actual strict build passes. Old Biometric target metadata is not a restriction on the app target. The closure increase is accepted for the stable prompt route; do not describe it as dependency-free.

The inspected Biometric, Fragment, Lifecycle Runtime and AppCompat POMs declare Apache-2.0. Their source/notices and the full actual runtime closure still belong in P1-S4 release materials. This review does not complete all transitive license or distribution obligations. Existing strict module locks and SHA256 verification now include the selected artifacts; normal builds and CI do not regenerate either.

Merged product permissions add `USE_BIOMETRIC` and `USE_FINGERPRINT` from the AndroidX library, alongside the existing package-specific non-exported receiver signature permission. No network, storage or foreground-service permission is added. The actual vault prompt requests device credential only. Per-app UID/data/Keystore ownership and non-exported Activity entry are preserved.

Source records G187–G189 and existing Biometric release/prompt records track the implementation entry. The [build and review](../06-validation/2026-10-16-product-vault-review.md) and [actual storage tests](../../qualification/results/P1-S3/2026-10-16-vault-storage-tests.md) retain their distinct evidence scopes.
