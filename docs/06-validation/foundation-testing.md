# Foundation testing strategy

**Scope:** the actual P1-S1 root graph `:console`, `:desktop`, `:domain`, `:platform`, `:design-system`, with later identity/profile/trust increments recorded below. Strict build12 and its initial bounded cases establish completed P1-S1 local construction acceptance; the opening table retains that baseline rather than current cumulative counts. Current installed products are0.1.5/code6, bound by the [SSH trust-manager result](../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager.md). Follow [D12](../05-delivery/library-reuse-policy.md) and the [reviewed skill overrides](../07-reference/development-skills.md).

## Test boundaries

| Layer | Actual cases | What they establish |
| --- | --- | --- |
| `:domain` JUnit4 | Five tests | Fresh local IDs, immutable rename, owner/schema/ID restoration rejection and Unicode/name bounds, including line/paragraph separators |
| `:design-system` JUnit4 | Four draft-codec tests | Bounded native-editor snapshot decoding and rejection; no Compose rendering or Android framework execution |
| `:platform` Android instrumentation | Five tests | Real Bundle/Parcel restoration, malformed field/type rejection, owner/schema validation and invalid serialized ID/name handling |
| Each app Android instrumentation | Two shared Activity cases per app | Committed context/system labels plus unsaved draft survive actual Activity recreation; invalid edit leaves committed identity/labels unchanged |

The shared Activity source is actually `testing/foundation/src/androidTest/kotlin`, wired into both app targets through `AndroidSourceSet.kotlin.directories`. It is not a sixth production Gradle module or shared runtime process. With AGP built-in Kotlin, adding a Kotlin directory to the Java source set is insufficient; the [official migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin) describes the Kotlin source-set configuration. Keep source discovery verified rather than interpreting an empty test APK as a pass.

Use the upstream `AndroidJUnitRunner`, runner/core1.7.0, ext JUnit1.3.0 and explicit test-only Espresso3.7.0, retaining JUnit4.13.2. Explicit test-only Espresso core/idling-resource3.7.0 overrides Compose's3.5.0 floor: the old floor failed on the actual Android17 target before either Activity assertion. The [official issue2507](https://github.com/android/android-test/issues/2507) and fixed upstream release support this narrow correction; no product runtime dependency changed. Compose UI testing is BOM2026.09.00 / ui-test-junit4 1.12.1, using `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` and explicit idle synchronization. The selected artifact deprecates the v1 factory; see [G184 migration](https://developer.android.com/develop/ui/compose/testing/migrate-v2). ActivityScenario drives the real Activity and Compose semantics drive the native editor. No custom runner, Hilt, Robolectric, mocking, screenshot plugin or coverage expansion is required by this slice. Introduce another tool only for a concrete uncovered requirement. Framework stubs and pure codec tests cannot count as actual Android Bundle, Activity or input evidence.

## Build and discovery

From the repository root, using the parent-selected existing JDK/SDK, isolated Gradle user directory and accepted Android signing directory:

```bash
./gradlew --dependency-verification=strict --no-daemon --console=plain --max-workers=4 \
  :console:assembleDebug :desktop:assembleDebug \
  :console:lintDebug :desktop:lintDebug \
  :domain:test :design-system:testDebugUnitTest \
  :platform:assembleDebugAndroidTest \
  :console:assembleDebugAndroidTest :desktop:assembleDebugAndroidTest
```

Record actual compiler, Lint and JUnit reports and test APK discovery from that build. Test compilation/package success is not execution. An absent/skipped test suite is not passing. Build12 executes all nine JVM tests; the recorded device run executes all nine instrumentation cases. These are distinct suites, not18 Android tests.

## Connected execution and independence

The parent first performs the independent-package install/launch cases: Console with Desktop absent and Desktop with Console absent, preserving existing diagnostic/evidence data. Connected test tooling may install the module's own test dependencies; do not use that to infer independent launch. Select the actual device explicitly and run only these modules:

```bash
ANDROID_SERIAL="$QUALIFIED_DEVICE_SERIAL" ./gradlew \
  --dependency-verification=strict --no-daemon --console=plain --max-workers=4 \
  :platform:connectedDebugAndroidTest \
  :console:connectedDebugAndroidTest :desktop:connectedDebugAndroidTest
```

Console package is `io.github.baitian6641.cloudy.console`; Desktop is `io.github.baitian6641.cloudy.desktop`. Neither product requests network/storage permissions or runs a remote engine in this slice. Shared source does not share app state, credentials or another app's process. Keep the actual product/test APK identities and app/window context bound to each result.

ActivityScenario recreation is not process death, a cold-launch persistence guarantee or a physical keyboard/native IME pass. Compose text entry is framework test stimulus. These tests do not qualify accessibility, system caption geometry, physical shortcut behavior, continuous sessions or remote features. Parent-owned device observations and later P1/product exits retain those distinct boundaries. Preserve failed builds and test results; do not rebuild unchanged upstream components or rerun accepted device cases just to document this strategy.

## Actual bounded execution checkpoint

The [native foundation report](../../qualification/results/P1-S1/2026-10-16-native-foundation.md) records strict build12 without dependency-regeneration flags: domain5/design4 JVM cases pass, with zero findings in both app Lint runs. On HP Googlebook14 Android17/API37 build `CL3B.260622.276.R2`, platform5 real Bundle/Parcel cases, Console2 and Desktop2 Activity cases pass (nine connected cases). Each product installs/launches with the counterpart absent.

The first Console connected run fails in Espresso3.5 reflection. The explicit test-only Espresso3.7 pin repairs that test-framework compatibility boundary; final product APK hashes remain frozen and unchanged. Preserve that failed run separately from final passing reports. Desktop New/Close, both same-version package replacements and independent uninstall receipts now pass; P1-S1 is complete in this local construction scope. These results do not qualify physical input/native IME, process death or remote features.

## P1-S3 storage increment

The foundation command/CI now also runs `:identity:testDebugUnitTest :identity:lintDebug :identity:assembleDebugAndroidTest`. Strict build04 passes with14 identity JVM cases, adding to the existing domain5/design4 reports. The parent separately executed only `:identity:connectedDebugAndroidTest` for18 passing tests on the explicit Googlebook. The [result](../../qualification/results/P1-S3/2026-10-16-vault-storage-tests.md) uses real Android file APIs but test-only software/fault-injection crypto; alias derivation is not key generation. It does not qualify actual product authorization/AES or rerun the earlier product Activity suites. The [source review](2026-10-16-product-vault-review.md) defines the next product checks. That storage-test checkpoint left0.1.1 installed. The subsequent [UI-entry run](../../qualification/results/P1-S3/2026-10-16-vault-ui-entry.md) installs0.1.2/code3; it starts no authentication and does not qualify AES.

## Opt-in product-vault qualification

The [0.1.3 build review](2026-10-16-vault-harness-review.md) adds one shared manual instrumentation case per app and a non-exported debug-only Activity using actual production prompt/storage/crypto. The [run contract](../../testing/foundation/README.md) requires explicit arguments and human readiness for authenticated actions. Default runs skip before Activity launch; never count that skip as a device pass. The150second test and ten-minute Activity deadlines do not automatically delete a fixture. Both app/test targets compile, strict Lint passes and actual release manifests exclude the debug Activity. The later [metadata-only execution](../../qualification/results/P1-S3/2026-10-16-vault-metadata-recreation.md) installs both0.1.3/code4 products and records default assumption skips separately from one explicit observe pass per app. The two metadata OPEN events per app share a process across Activity recreation. No Save, AES, authentication or credential-key generation occurred; process-death recovery and credential isolation remain unqualified. Both apps are stopped, test packages were successfully uninstalled and boot ID/count remain unchanged. The earlier failed host preflight remains preserved; the pending human authentication-readiness question is unanswered.

## Handoff domain and platform codec

`HandoffEnvelopeTest` runs in the existing domain JVM suite. `HandoffJsonTest` runs in the standalone platform Android test package against the actual framework parser; it needs no remote host, auth prompt or product Activity. The [first result](../../qualification/results/P1-S3/2026-10-16-handoff-codec.md) passes8 new JVM cases and13 Android cases. Hosted CI compiles the Android cases but does not execute them. Later signed-caller/replay/registry tests must exercise those actual boundaries; codec success cannot substitute for them.

The [caller/replay result](../../qualification/results/P1-S3/2026-10-16-handoff-admission.md) separately executes `HandoffCallerTest`5 and `HandoffReplayLedgerTest`14. These use actual PackageManager, Binder, SQLite, filesystem and locking; controlled clocks are test-source inputs, not device clock changes. Caller tests establish local denials, not signed counterpart acceptance. Existing unchanged codec tests are not claimed rerun.

`HandoffReplayProcessTest` skips before fixture creation unless `-e cloudyReplayPhase seed` or `verify` is supplied. Run seed, retain its completed instrumentation output, force-stop only the standalone test package and verify its PID is absent, then run verify. The second phase requires a different PID and the original fixture; it must not reset a failed predecessor. Only successful verification removes that test-owned directory. This establishes ordinary process restart for replay storage, not power-cut resilience or successful navigation. Default assumption skips are never counted as passing cases.

## Connection profile storage

The [profile result](../../qualification/results/P1-S3/2026-10-16-connection-profiles.md) runs the domain/identity JVM suites after moving canonical host normalization, and19 `ConnectionProfilesTest` cases against real Android SQLite. Test roots are isolated under the standalone package's credential-encrypted no-backup directory; this does not qualify installed-product catalog separation. Capacity is exercised with an internal two-row ceiling, and a real abort trigger exercises uncertain-outcome handling without a fake store.

`ConnectionProfilesProcessTest` requires `-e cloudyProfilesPhase seed` or `verify`, otherwise it skips before creating its fixture. Run seed, retain its receipt, force-stop the standalone package and confirm PID absence, then run verify in another process. Verification reads the original catalog generation/profile/revision and tests successful update, stale-token denial, deletion and no resurrection. Remove no interrupted fixture to manufacture a fresh pass. Custom PID status messages are not additional JUnit tests. Native editor, production owner isolation and physical power-loss behavior require their own evidence.

## Profile editor recovery and fixture settlement

`ConsoleProfileActivityTest` and `DesktopProfileActivityTest` inherit six production-store Activity cases from `ProfileActivityContract`. The [editor checkpoint](../../qualification/results/P1-S3/2026-10-16-profile-editor.md) records that earlier bounded result; `ProfileEditorStateTest` separately supplies10 real Bundle/Parcel cases. Saved-state codec success alone does not establish behavior after an admitted mutation.

`ConsoleProfileRecreationTest` and `DesktopProfileRecreationTest` add two cases per app through `ProfileRecreationContract`. A test-only gate runs in the app-owned private executor ahead of an actual UI Create or Update. The gate remains held until ActivityScenario has recreated the Activity, so the admitted mutation cannot deliver a callback to the old Activity first. Releasing it allows the real production-store mutation followed by the replacement's queued read. Assertions check pending uncertainty, disabled Save, retained draft, exactly one Create or one revision increment, and no repetition after Refresh and another recreation. The test does not substitute a fake result or store. This white-box queue-order case qualifies only the sampled Activity-recreation boundary; it is not natural process death, a power cut or a physical-input result.

Cleanup captures the real application context, closes the Scenario, releases test gates and proves bounded worker/main callback settlement before reading or deleting fixtures through the real store. It is independent of UI scrolling and semantics. Cleanup excludes every initially present ID and only reconciles the current run's uniquely identified created records. An unproved close/fence preserves the exact fixture and reports cleanup failure; it does not authorize deletion while admitted work may still run.

The [profile recovery record](../../qualification/results/P1-S3/2026-10-16-profile-recovery.md) binds the accepted continuation. Run01 passes eight profile Activity cases per app and both isolation seeds, then preserves the initial Console `PEER_DENIAL_UNCONFIRMED` failure and both fixtures. Run02 fails host preflight before instrumentation because package output contains multiple user-specific data directories. Run03 selects the actual owning `run-as` working directory matched to package paths, then passes both revised verification directions and both exact cleanup phases using the original fixtures without reseeding. Both peer probes report `NOT_VISIBLE` / errno2 with matching external exact-path witnesses and successful own-database controls. Across the retained run and continuation,22 JUnit cases pass; two default skips are separate from those passes. Original profile rows/generations are restored to baseline, product bytes/vault receipts/boot identity remain unchanged, both app test packages are removed and both products are stopped. Initial failures remain historical evidence.

## Opt-in installed-product profile isolation

Shared-source `ProfileIsolationTest` runs separately under each installed product UID, using the real app-owned store and non-secret fixture metadata. It skips before path/store access by default. Explicit runs require a shared canonical lowercase v4 UUID in `cloudyProfileIsolationRun` and one `cloudyProfileIsolationPhase`: seed both apps, verify each direction, then clean up both apps through separate invocations. A default skip, seed pass or one direction alone does not complete the paired result. The [run instructions](../../testing/foundation/README.md#opt-in-installed-product-profile-isolation) define argument, witness, receipt and cleanup ordering.

Each seed persists intent before Create and a `READY` receipt afterward. Verification and cleanup require that exact run/owner/generation/ID/revision and fixture fields. Cleanup uses compare-and-set deletion and verifies the rest of the catalog is unchanged before removing the receipt. Existing, interrupted, stale or changed receipts refuse automatic replacement; there is no reset phase and no application-data clearing.

The revised `verify` protocol additionally requires `cloudyProfileIsolationPeerPathSha256`. Immediately before each direction, the parent must obtain the peer's actual owning `run-as` working directory, match it to the reported package data paths across users, successfully read the exact absolute peer database pathname in its owning UID context and retain that evidence with the peer's `READY` receipt/row. Never assume the first reported `dataDir` belongs to the active owning UID. The digest binds the exact UTF-8 pathname, not database bytes; it cannot replace proof of the owner-context read. Path mismatch or a missing witness fails verification.

The probing app first performs an own-database `Os.open` positive control. Peer `EACCES`/`EPERM` is reported as `PERMISSION_DENIED`; `ENOENT` is separately `NOT_VISIBLE` and eligible only under the matching external exact-path witness. Arbitrary missing paths never pass. A successful peer open, another error or failed positive control fails verification. Keep errno, both pathname digests and the exact app/build/run bound to the result without claiming an Android causal mechanism from that observation. Run03 qualifies sampled owning-product catalog separation and inability to open the witnessed sibling databases; both exact original fixtures were subsequently cleaned. Preserve the failed predecessors and their evidence. No credential protection, authentication, process-death or physical power-loss gate is promoted by this profile-only protocol.


## SSH host trust

`SshHostTrustTest` adds 12 domain JVM cases to the existing suite. `SshHostTrustStoreTest` adds 20 standalone Android cases against actual SQLite/filesystem/framework operations. The [result](../../qualification/results/P1-S3/2026-10-16-ssh-host-trust.md) and [build manifest](../../qualification/results/P1-S3/2026-10-16-ssh-host-trust-build.json) bind the accepted source and device execution. Hosted CI compiles the Android tests; it does not execute their device boundary.

Run only the exact storage class for that increment. Each case owns a unique credential-encrypted no-backup fixture root; public digest fixtures imply no network handshake. Real SQLite abort triggers exercise uncertain completion. Capacity uses a one-row test ceiling; reopen-object and concurrent-thread cases do not claim process-death, cross-process writer or physical power-loss coverage. That result left native presentation and installed-product integration to their own subsequent evidence below. Installed-product trust-file isolation and actual verifier-before-credential-release remain open.

## Native SSH trust manager

The [manager result](../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager.md) and [build manifest](../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager-build.json) bind Console/Desktop0.1.5/code6, now installed and stopped. The source adds an owning-app nonexported inspection/revocation Activity and workspace entry. Build01's test compilation fails because `assertDoesNotExist` was imported as an extension rather than used as the pinned API's member; preserve its failed log/source separately. No device test used build01. Strict build02 passes with three zero-issue Lint reports for Console, Desktop and platform. The unchanged domain task is up-to-date, retaining38 passing reports; those cases were not newly executed in this build.

Actual execution on HP Googlebook14, Android17/API37 build `CL3B.260622.276.R2`, incremental16560795, passes the following bounded suites:

| Executed suite | JUnit cases | Boundary |
| --- | ---: | --- |
| Standalone `SshTrustUiStateTest` | 3 | Real Bundle/Parcel owner-bound pending metadata, missing/wrong-type state and rejection of malformed or injected approval fields |
| Console trust manager plus workspace | 7 | Four `SshTrustActivityContract` cases and three `WindowActivityContract` cases in the actual Console package |
| Desktop trust manager plus workspace | 7 | The same four trust and three workspace cases in the actual Desktop package |

There are17 successful JUnit cases, with no skips or failures. Custom instrumentation status0 fixture/cleanup events are not additional tests. The workspace cases include the two prior committed-window/draft regressions and opening/closing the trust manager while retaining the logical window. Each trust case checks actual package/UID and the nonexported Activity.

The four trust cases cover exact displayed peer/key/state with Cancel and confirmed tombstone-preserving revocation; a real competing replacement invalidating the frozen revoke dialog; recreation discarding an unconfirmed dialog and selection; and a real admitted revoke committing exactly once across controlled Activity recreation. The last case retains uncertainty until successful explicit Refresh, then checks that subsequent Refresh/recreation does not advance the revision again. `SshTrustTestQueue` controls only the app-owned executor's ordering with a bounded gate. It supplies no fake result, storage substitute, connection or approval grant.

Fixtures are unique `.invalid` peers with public digest values, seeded through production `evaluate`/`accept`. Private test receipts record baseline digest, owner/package/UID/generation and independently expected transitions before mutation. Cleanup first closes the Activity, releases its gate and proves worker/main settlement, then locks the existing permanent anchor before opening SQLite. A transaction validates schema, metadata and the entire baseline-plus-confirmed-fixture catalog, deletes only exact eight-field fixture matches and verifies original rows/metadata before commit; production `load` confirms restoration afterward. Unexpected state or uncertain settlement preserves the exact fixture and receipt. This guarded test-only disposal does not introduce a product delete/reset API.

All eight fixture cleanups report exact baseline restoration. Both new product trust ledgers remain initialized and empty with permanent files retained. Original profile rows/generations, existing vault receipt hashes and boot ID/count remain unchanged; installed bytes match build02. All three temporary test packages are removed and verified absent, and both products are stopped. No app-data clear, authentication prompt, remote host or network session is involved.

These cases qualify sampled owning-product management and controlled Activity recreation. Process death, installed-product trust-file isolation, independent-process writers, physical power loss, clipboard execution, physical keyboard/mouse input, both-theme/scaled/narrow visual quality and unavailable-store exit behavior remain unqualified. Observed-key enrollment still requires a real SSH attempt, foreground challenge ownership and verification before credential release. Jump routing, live rekey/revocation policy and engine adoption remain separate work; P1-S2/P1-S3 and the full P0–P6 objective are not complete.
