# Protected files handoff: build and real sibling transport

**16 October 2026 — scoped PASS after a retained failed attempt. P1-S3 remains in progress.** Console and Desktop **0.1.12/code13** now implement protected discovery, request admission and native sender/destination composition. Actual Googlebook testing establishes both-direction discovery and negative submit behavior. A normal shared-location Open through the visible sender, Android background-activity-launch policy, fresh receiver-owned authentication and actual remote listing/read remains unrun.

Read the [implementation contract](../../../docs/02-architecture/protected-files-handoff.md), [frozen build manifest](2026-10-16-files-handoff-build.json) and [sanitized evidence manifest](2026-10-16-files-handoff-evidence.json). The latter records exact source/build/runner/result hashes and private evidence locations without credentials, serials, private catalogs or raw transcripts. Earlier [own-app registration/navigation](2026-10-16-files-location-ui.md) evidence remains tied to its original build.

## Implemented behavior

Each app owns its own bound AIDL service, receiver-owned signature permission, profiles, trust, resource catalog, replay ledger and credentials. Every transaction captures the actual incoming Binder identity and admits only the exact signed sibling. Explicit discovery discloses only current SHARED labels and opaque resource IDs. Submit validates the envelope and full registered tuple before durable reservation; final caller/resource/freshness checks and a one-use private ticket guard receiver navigation. Unsupported terminal/desktop actions return typed rejection before reservation.

The native sender requires deliberate Refresh, selection and Open. It sends no credentials, performs no automatic replay, and keeps a timed-out operation uncertain until its actual synchronous call/binding retires. The receiver must authenticate independently. Source composition is implemented; successful visible receiver navigation is a separate qualification claim.

## Build and focused Android checks

| Evidence | Result and boundary |
| --- | --- |
| Corrected strict build03 | PASS: Kotlin/Java/AIDL compilation, dependency verification, both product APKs and three instrumentation APKs; 224 unchanged source bindings and five exact APK hashes. Four Android Lint reports have zero findings. |
| Retained JVM reports | 127 cases: 107 passes, 20 skips, zero failures/errors. Build03 reruns the platform task; domain, SSH and design tasks are UP-TO-DATE. These are not 107 newly executed build03 cases. |
| Build02 standalone platform run | 54 distinct actual Android cases: 28 resource-store, seven caller-policy, eight endpoint-policy, seven wire and four Parcel cases; zero skips/failures, 28 separate resource cleanup receipts. Policy cases do not substitute for genuine IPC. |
| Corrected build03 Parcel run | Seven actual methods pass, zero skips/failures. Independent valid-input checks cover one-way rejection, required reply, combined flags and acceptance of a representative non-oneway bit, plus descriptor, bounds and trailing-data checks. No product installation or launch in this lane. |

Do not add the two Android runs into a unique-case total: the corrected Parcel run includes repeated and strengthened cases. Build03's raw log records 324 actionable tasks, 52 executed and 272 up-to-date, completing in 49 seconds. All source and artifact bindings were checked before and after device execution.

## Retained failure and compatibility correction

Transport01 genuinely ran seven Console-origin cases. Six ordinary transactions failed at the transaction stage; the seventh caught `SecurityException`. All seven bindings retired, but that exception alone cannot establish that trailing-data validation ran, because an earlier caller/flag guard could reject first. Desktop's suite did not start. This attempt remains FAILED.

Independent official-source review found a concrete compatibility defect: the guard required every Binder flag to equal zero. AOSP [BinderProxy](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/BinderProxy.java) can add framework bookkeeping to synchronous calls, and [Binder](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/Binder.java) forwards flags to `onTransact`. The corrected guard masks only the public [FLAG_ONEWAY](https://developer.android.com/reference/android/os/IBinder#FLAG_ONEWAY) bit and still requires a reply, correct descriptor/shape, no FDs, bounded input and no trailing data. No hidden constant is imported. The failed device run's numeric flags were not measured; that exact causal attribution remains an inference, not an observed fact.

Selected build-tools36 generated Java omits trailing-data enforcement. The application validates a bounded copy with public [Parcel APIs](https://developer.android.com/reference/android/os/Parcel), including API33 `enforceNoDataAvail`, while leaving the original cursor for generated decoding. Local Parcel tests establish policy, not caller identity. The corrected transport test first requires successful ordinary discovery on the same binding before it can credit malformed trailing-data rejection. Fixed allowlist error categories now preserve useful failure classification without exception text or causes.

The leader started transport01 before the runner author's final freeze. Its exact executed script was recovered separately and matched to the recorded SHA-256; the later unexecuted revision was not substituted for it. Recovery01 safely refused before mutation because fresh SQLite tuples were compared with JSON lists. Serialized metadata and all private hashes matched; recovery02 normalized the representation, passed review and verified exact preservation before stopping idle products and removing the owned test packages. No data reset, credential deletion, replay-ledger clearing or test-result replacement occurred. The corrected runners were frozen and reviewed before execution.

## Corrected genuine transport02

Device: **HP Googlebook 14**, Android 17/API37, arm64-v8a, build **CL3B.260622.276.R2**, incremental **16560795**. Both exact build03 APKs were installed with data-preserving replacement. The run lasted from **16:53:33.863 to 16:53:53.405 UTC** on 16 October 2026. No host listener, network credential fixture or authentication prompt was used.

Both actual product UIDs/default processes bind the real sibling service. Each direction passes these seven cases:

1. Successful discovery of the receiver's existing empty catalog.
2. Typed unsupported discovery version.
3. Typed malformed cursor rejection.
4. Malformed envelope rejection with no reservation.
5. Both known unsupported actions rejected with no reservation.
6. A genuinely unregistered files tuple rejected with no reservation.
7. Successful discovery on the same binding followed by rejected trailing Parcel data.

**Fourteen actual test passes, zero skips/failures, fourteen separate binding-retirement receipts.** The Console invocation took 0.662 seconds and Desktop 0.669 seconds; these suite durations are not user-facing latency or throughput benchmarks. No local Stub or fabricated caller proof substitutes for these IPC calls. The test bindings deliberately omit activity-start opt-in, and no positive destination launch is requested.

Final checks confirm both products installed at the exact corrected hashes and stopped; all three instrumentation packages absent; unchanged private profile/trust/vault/preferences/resource/replay files, empty resource catalogs and metadata, system settings, boot identity/count and ADB reverse mappings. Unlike the earlier registration milestone, this increment introduces no new persistent catalog or replay initialization. No fixture/host cleanup is inferred from test completion: binding receipts and final package/data observations are separate requirements.

## Review and remaining work

Direct **GPT-6.1 Sol** workers authored bounded components and reviewed source, runners and evidence; the leader inspected and integrated them. Reviews corrected work under the SSH coordination lock, sender departure/submission ordering, uncertain launch classification, generated-Parcel shape handling and the synchronous flag defect. OMP's selected `zai/glm-5.3` context-only review produced no completed final result within its bound and was not accepted as review evidence; a preceding local runtime write failure is retained separately. No worker had device or Git ownership.

Repository acceptance also passes:32 authored Python files through AST/compile, pinned Ruff,123 repository tests, schema/planning validation, generated design-token consistency and JavaScript syntax. The validator records554 official sources,66 API entries and the unchanged40-stage graph.

The previous checkpoint `1b968613ea30455f0804cb04d5d502f10a75b258` is pushed and all seven exact-SHA hosted lanes passed, recorded in the manifest. Those checks are not inherited by the new source commit. No capability, stage or release gate changes here.

Next required integration: both-direction normal visible Refresh/select/Open to a real SHARED nondefault directory, exact receiver-owned account authentication and listing/read, actual destination visibility/BAL and peer retirement. Then cover relevant wrong/delegated callers, missing sibling/install order, replacement/revocation, uncertain delivery and lifecycle cases. Reuse established primitive/engine evidence proportionately; do not repeat settled diagnostics merely to accumulate tests. Saved-vault product use, desktop physical input/accessibility, full terminal/files/edit/drop/X11 and VNC-first Desktop engine integration remain open under the full P0–P6 goal.
