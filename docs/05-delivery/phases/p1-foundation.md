# P1 — Shared foundation and design

**Status:** P1-S1 complete; P1-S2 native context/controls and P1-S3 protected identity in progress; other stages planned. **Active effort:** 15–24 person-days before contingency. **Purpose:** Establish two independent Android desktop packages, shared design/lifecycle contracts and early engine/distribution decisions, including native streaming at P1-S5.

The canonical stage data is [backlog.json](../backlog.json). [Rule 0](../../00-project/platform-rule-zero.md) defines desktop scope. Follow [capability outcomes](../feasibility-gates.md) and [independent release gates](../release-gates.md). Estimates are active effort, not elapsed time; re-estimate after physical evidence. Revision 02 moved the former P5-S1 decision to P1-S5; other stage IDs retain their identity.

| Stage | Outcome | Effort, person-days | Prerequisites |
| --- | --- | ---: | --- |
| P1-S1 | Native app targets and shared-module build foundation | 3–4 | P0-S3 |
| P1-S2 | Cloudy design system and native shell | 5–8 | P1-S1 |
| P1-S3 | Identity, typed contracts, and lifecycle persistence | 3–5 | P1-S1, P0-S2 |
| P1-S5 | Early native-stream adoption and distribution decision | 2–3 | P0-S4, P1-S1 |
| P1-S4 | CI, dependency decision, and foundation gate | 2–4 | P1-S2, P1-S3 |

The [ADR0012 refinement](../../adr/0012-foundation-construction-and-platform-acceptance.md) supports construction on demonstrated native primitives. It leaves CAP-PLATFORM experimental and required at product acceptance; every P1 work item and exit below remains required. Product dependencies and actual shell/input/security checks still need their own evidence.

## P1-S1 — Native app targets and shared-module build foundation

**Owner:** Android/build developer. **Prerequisites:** P0-S3 investigation complete. **Status:** done. See the [actual local foundation result](../../../qualification/results/P1-S1/2026-10-16-native-foundation.md) and [stage review](../../06-validation/2026-10-16-native-foundation-review.md).

**Required supported capabilities:** CAP-FOUNDATION.

Implementation entry: use the [reviewed Compose candidate stack](../../07-reference/2026-10-16-compose-foundation-entry.md), then qualify the actual product dependency closure and build. The diagnostic native Views results do not qualify Compose. Start with real local window/state behavior and independently installed packages.

Work:

1. Establish two independently installable signed development APK targets, shared source boundaries and reproducible Android/NDK toolchains selected from P0.
2. Apply Rule 0: desktop/laptop Googlebook target, separate package IDs/app data, no phone/tablet UI workstream; document manifest/ABI/minSdk/targetSdk and channel assumptions.
3. Build native shells with per-window context and real lifecycle state; keep baseline SSH/RDP/VNC independent of the custom host agent.
4. Document APK installation versus AAB publishing and verify package install/update/uninstall using authorized Android tooling.

Deliverables:

- Two installable debug APKs and toolchain lock record
- Module graph and minimal launch smoke evidence

Exit evidence:

- Each app launches with the other app absent.
- No source module depends on the other app process or UI.

Record evidence under `qualification/results/P1-S1/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-P03. Official/upstream source IDs: G01, G02, G31, G32; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S2 — Cloudy design system and native shell

Apply [D14 desktop interaction](../../04-design/desktop-terminal-interaction-contract.md): keyboard shortcuts, ordinary native editing, direct pointer selection and context menus remain required regardless of the reused engine. No long-press or mobile key row is a prerequisite.

**Owner:** Design/Android developer. **Prerequisites:** P1-S1. **Status:** in progress. The [committed-context increment](../../../qualification/results/P1-S2/2026-10-16-committed-context.md) is a bounded first result; the work and exits below remain required.

The [profile geometry follow-through](../../../qualification/results/P1-S2/2026-10-16-profile-geometry.md) repairs two actual manager defects and passes six scaled geometry plus sixteen profile/lifecycle regressions on0.1.7. It narrows the manager review; physical input/accessibility and the full component/engine scene below remain required.

**Required supported capabilities:** CAP-FOUNDATION.

Work:

1. Implement canonical Cloudy tokens and native desktop controls with user font scaling, two themes, density and system caption bounds.
2. Prototype resizable terminal+SFTP arrangements, X11 app/dialog windows, backend profile/trust screens, USB enrollment and diagnostics.
3. Qualify keyboard navigation, shortcut discovery, right-click/hover/cursors, range selection, pointer/touch access and focus ownership on Googlebook.
4. Run narrow tiled/short windows, multi-instance and display-scale changes; preserve desktop workflows without adding phone/tablet navigation.
5. Run the native workspace review with two host contexts, terminal/SFTP, X11 modal, RDP/VNC input release and a device interruption; label synthetic and engine-backed evidence separately.
6. Record Q-A02 shell/geometry findings and feature-specific pending checks before freezing revised interaction components.

Deliverables:

- Native desktop component catalog and S01–S17 interaction review
- Rule 0 and applicable official desktop-quality evidence ledger
- Native workspace review record with accepted geometry/focus decisions and explicit provisional components

Exit evidence:

- No system control overlap, focus trap, unreadable semantic pair, or unreachable scaled action.
- Visual choices preserve terminal/file/media working area and clear host context.
- Freeze only demonstrated native component geometry/interaction; unresolved engine-dependent behavior remains provisional and linked to its feature stage.

Record evidence under `qualification/results/P1-S2/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-P02, Q-P03, Q-A01, Q-A02. Official/upstream source IDs: G01, G02, G03, D01, D02, D03, D04, D05, D06, D09; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S3 — Identity, typed contracts, and lifecycle persistence

**Owner:** Platform/security developer. **Prerequisites:** P1-S1, P0-S2. **Status:** in progress. The [vault source review](../../06-validation/2026-10-16-product-vault-review.md) and [storage tests](../../../qualification/results/P1-S3/2026-10-16-vault-storage-tests.md) leave actual product authentication and all other exits open. The historical [0.1.2 UI entry](../../../qualification/results/P1-S3/2026-10-16-vault-ui-entry.md), [manual-harness build](../../06-validation/2026-10-16-vault-harness-review.md) and subsequent [0.1.3 metadata-only Activity recreation](../../../qualification/results/P1-S3/2026-10-16-vault-metadata-recreation.md) do not promote those gates. The earlier0.1.3/code4 apps were installed and stopped; one explicit observe case per app passes, while default non-opt-in invocations skip. Save, AES, authentication, credential-key generation, process-death recovery and credential isolation remain unqualified.

**Required supported capabilities:** CAP-FOUNDATION.

Work:

1. Implement per-app protected credential handles, known-host trust storage, enrollment cancellation, and revocation.
2. Implement signature-protected explicit IPC with bounded versioned resource envelopes, caller validation, expiration, and receiver-side resource resolution.
3. Add persisted session/layout metadata, transfer journals, typed errors, and bounded redacted diagnostics.
4. Implement the qualified foreground/background strategy, timeout handlers, focus-loss cleanup, and reconnect metadata.
5. Define X11 broker/SSH/window lifetimes, URI staging grants, per-backend client ownership and USB enrollment restoration without a shared immortal daemon.

The [handoff codec increment](../../../qualification/results/P1-S3/2026-10-16-handoff-codec.md) implements bounded v1 serialization/domain validation, with strict compilation/Lint and scoped actual Android tests. The [runtime profile](../../02-architecture/handoff-runtime-profile.md) records its limits. The later protected transport increment implements signed-caller transport, receiver-owned resolution and replay admission with scoped tests. Work item2 remains incomplete pending positive normal Open/BAL/fresh receiver authentication/list/read and broader negative-caller/lifecycle acceptance.

The subsequent [caller/replay increment](../../../qualification/results/P1-S3/2026-10-16-handoff-admission.md) implements exact counterpart policy and a real app-private SQLite request ledger. Nineteen Android cases and two distinct-process phases pass; caller tests establish local denial only. The later [resource domain/storage increment](../../../qualification/results/P1-S3/2026-10-16-files-resource-storage.md) adds actual exact account/profile/trust-bound metadata and SQLite create/share/revoke/resolve with12 new JVM and20 Android storage passes. The [location contract](../../02-architecture/files-resource-registration.md) now implements bounded own-app completed-listing/user-consent registration, resource-bound live retirement and a private account-bound destination. The subsequent protected transport increment implements endpoint permissions and real signed sibling discovery/denial; genuine positive cross-app effects and broader caller/lifecycle acceptance remain integration work. Source metadata and generic windows do not substitute for the requested terminal, desktop or file destination; work item2 remains incomplete.

The [connection-profile increment](../../../qualification/results/P1-S3/2026-10-16-connection-profiles.md) now provides the owning-app endpoint domain/store, with shared unchanged host normalization and transaction-checked edit tokens. Forty JVM cases,19 Android storage cases and two distinct-process phases pass. The [native editor follow-up](../../../qualification/results/P1-S3/2026-10-16-profile-editor.md) adds CRUD and bounded draft restoration with26 scoped actual Android cases. The [recovery/isolation follow-up](../../../qualification/results/P1-S3/2026-10-16-profile-recovery.md) now establishes controlled admitted-save Activity recreation and fixture-backed installed-product catalog/file isolation. Actual process death and credential/trust/resource integration remain required. The [SSH trust domain/storage result](../../../qualification/results/P1-S3/2026-10-16-ssh-host-trust.md) passes38 domain JVM cases and20 actual Android storage cases. The [native manager follow-up](../../../qualification/results/P1-S3/2026-10-16-ssh-trust-manager.md) adds own-app inspection/confirmed revocation, with strict build, zero Lint and17 actual Android cases covering stale confirmation, controlled admitted-write recreation and workspace entry. Its [contract](../../02-architecture/ssh-host-trust.md) retains separate observed-key enrollment/controller, installed-product trust isolation and engine/authentication gates. Clipboard execution, physical input and rendered component quality remain unqualified. Full host profiles, SSH transport and P2/P3 prerequisites are not promoted.

The [0.1.9 native per-session SSH entry](../../../qualification/results/P1-S3/2026-10-16-ssh-authentication-ui.md) adds actual finite owning-app authentication and lifecycle/credential retirement, with ten build02 cases and six affected final-build reruns. Current profile/trust admission and native field epochs retain independent ownership. It supplies no reusable session or saved-vault authority; all remaining work/exits below and existing P2 prerequisites remain required.

The [retained SSH ownership increment](../../../qualification/results/P1-S3/2026-10-16-ssh-retained-session.md) passes bounded host rekey/SFTP and actual Android invalidation/retirement tests. It supplies shared construction and lower-level consumer evidence. The native files follow-up below adds the foreground caller; saved-vault use, durable session recovery and the remaining P1/P2 gates are still required.

The [0.1.10 owning native files follow-up](../../../qualification/results/P1-S3/2026-10-16-ssh-files-ui.md) adds retained authentication, actual directory listing/preview reuse, a configuration-retained ViewModel and bounded foreground retirement in both independent products. Twelve distinct actual app/case combinations pass: Console's six across device03/04/05 and Desktop's six in device06, with 24 default opt-in skips recorded separately across those runs. Device03/04 remain failed whole-run records with individually accepted passes and subsequent exact cleanup/recovery. Strict build05 passes with four zero-finding Lint reports, and build04/05 product APKs are byte-identical. The [native files contract](../../02-architecture/ssh-native-files.md) separates framework focus/configuration/Stop evidence from physical desktop interaction, and records the five-second replacement-owner lease, no replay and cleanup quarantine. A genuine same-task opaque Activity cover establishes Stop retirement and return without automatic reconnect; framework focus injection remains a policy test. Earlier failed focus/test/harness attempts remain preserved. Final products are stopped with prior data/settings/boot preserved, test packages absent and owned fixtures/routes closed. This advances P1-S3 construction; protected identity, signed IPC and receiver-owned effects, full terminal/files/drop/X11 and all existing stage/capability exits remain required.

The [0.1.11 native locations follow-up](../../../qualification/results/P1-S3/2026-10-16-files-location-ui.md) qualifies six actual product cases across both apps, with18 separate default opt-in skips,10 real authentications and24 SFTP channels. The platform run passes47 actual cases (21 coordinator,22 resource store,4 registration policy),0 skips and22 cleanup receipts. Strict build02 records four zero-finding Lint reports and206 input bindings; retained JVM reports contain100 passes/20 skips, with all four JVM tasks UP-TO-DATE in build02. Actual listing-bound foreground consent, manager Share/Revoke, exact-account one-use Open and affected registered-session retirement are implemented. At that historical version Share reported counterpart discovery unavailable; the later API64 increment below supersedes that source status. Final0.1.11/code12 products are stopped; prior profile/trust/vault/settings/boot are preserved, owned fixtures/routes/test APKs are removed, and both previously absent resource catalogs remain initialized and empty. At that checkpoint thirty-second expiry, uncertain-write recreation, physical focus and signed sibling transport remained open. The later scoped transport result does not close the other exits. Metadata checkpoint `be321e3204d122fb39244e35fbb6d05ceae068a4` separately passes all seven hosted lanes.

The [0.1.12 protected files handoff result](../../../qualification/results/P1-S3/2026-10-16-files-handoff-transport.md) adds generated AIDL, signature/actual-caller enforcement, bounded discovery/replies, SHARED resolution/replay/ticket admission and native sender composition. Fourteen genuine both-direction discovery/denial cases and14 separate binding retirements pass; corrected build03 has224 source bindings/five APKs/four zero-finding Lint reports, with seven focused Parcel cases and the prior54-case platform run scoped separately. First failed transport/recovery evidence remains retained. Positive normal Open/BAL/fresh receiver authentication/list/read, broader negative callers and lifecycle remain required; work item2 and P1-S3 stay incomplete. Predecessor native-location commit `1b968613ea30455f0804cb04d5d502f10a75b258` has seven passing hosted lanes; current source needs its own CI.

The [positive handoff test foundation](../../../qualification/results/P1-S3/2026-10-16-handoff-test-foundation.md) adds immutable retained receiver phases and a standalone ordinary-process controller, with strict builds and eight actual isolated Googlebook parser/SQLite passes. The [fixture path observer](../../../qualification/results/P1-S3/2026-10-16-handoff-path-observer.md) now passes strict Java compilation and14 pure assertions; actual callback/path behavior, exact old-operation retirement, full phase sequencing and both-direction normal Open remain required. That test-only checkpoint retains installed0.1.13. The subsequent [passive observation construction](../../../qualification/results/P1-S3/2026-10-16-handoff-observation-build.md) builds0.1.14/code15 with231 input/seven APK bindings, seven clean Lint reports and16 executed platform JVM cases. Source review closes publication/grammar findings; release excludes the collector. Android observer runtime and both ordinary Open directions remain unrun, so work item2 and P1-S3 are not complete. The later [attempt04 and recovery](../../../qualification/results/P1-S3/2026-10-16-handoff-prepared-controller-failure.md) pass actual PREPARE plus original-checkpoint VERIFY/CLEANUP, but the ordinary controller fails before Open. The [test-only controller diagnostic](../../../qualification/results/P1-S3/2026-10-16-handoff-controller-diagnostics.md) subsequently passes three real privacy methods with exact product/replay preservation; normal positive handoff remains unqualified. The [fresh selected-card timeout](../../../qualification/results/P1-S3/2026-10-16-handoff-selected-card-timeout.md) narrows the failed controller boundary and passes exact recovery, without establishing the individual accessibility predicate or normal Open.

**Latest scoped handoff acceptance:** [forward08](../../../qualification/results/P1-S3/2026-10-16-handoff-positive-console-desktop.md) and [reverse01](../../../qualification/results/P1-S3/2026-10-16-handoff-positive-desktop-console.md) each pass one controlled debug ordinary Open, fresh receiver authentication, nested list/read and original-operation retirement with complete preserved-state teardown. Earlier unrun/failed statements above describe their historical checkpoint only. Current authority is reverse01 finalBaseline; products0.1.15/code16 are stopped and all three test packages absent. General BAL, release runtime, physical UX, broader caller/lifecycle and all stage exits remain required; work item2 and P1-S3 are incomplete.

Deliverables:

- Identity/IPC/lifecycle library and schema fixtures
- Threat-boundary and denial/revocation evidence

Exit evidence:

- No exported secret/raw command through handoff, logs, or renderer messages.
- Process recreation restores metadata without replaying a side effect.

Record evidence under `qualification/results/P1-S3/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-X02, Q-S01. Official/upstream source IDs: G15, G16, G17, G18; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S5 — Early native-stream adoption and distribution decision

**Owner:** Maintainer/desktop developer. **Prerequisites:** P0-S4, P1-S1. **Status:** planned.

Work:

1. Review the P0 native-engine feasibility probe and exact upstream/dependency obligations during the foundation phase, before committing the streaming implementation.
2. Apply [ADR0010](../../adr/0010-desktop-open-source-distribution.md): compatible open-source Desktop is selected. Finalize the exact license expression, actual dependency graph/SBOM, corresponding source/notices/build materials, engine ownership and reproducible build route; Console terms remain independent.
3. Record native surface/input/decoder and backend-version boundaries; estimate remaining integration from the actual probe.
4. Record CAP-STREAM as supported, bounded experiment or blocked. A blocker does not prevent ordinary SSH, RDP or VNC work; an external-only alternative needs an explicit user-visible scope decision.
5. Keep the early external stream lane available while native integration proceeds on its own prerequisites, independently of optional host adapters and event integrations.

Deliverables:

- Early implementation/distribution ADR with selected engine and obligations
- Pinned maintenance/build plan, CAP-STREAM outcome and revised estimate

Exit evidence:

- The adoption investigation has a recorded outcome; only a supported CAP-STREAM outcome can activate native stream implementation.
- No implementation or distribution right is inferred from an external app, and no native-stream scope reduction is made without an explicit decision.

Record evidence under `qualification/results/P1-S5/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-D01, Q-N02. Official/upstream source IDs: R06, R04, R05; resolve them in the [source register](../../07-reference/source-register.md).

## P1-S4 — CI, dependency decision, and foundation gate

The [VNC engine construction](../../../qualification/results/P1-S4/2026-10-16-vnc-engine-construction.md) is now an independently compiled Android library with actual JNI/Java, eight owner JVM passes and both zero-issue Lint variants. It remains outside the root graph and Desktop dependency; exact runtime/adoption, distribution closure and every stage prerequisite/exit below remain required. This bounded construction does not activate or finish P1-S4. Subsequent [source review](../../06-validation/2026-10-16-vnc-cleanup-race-review.md) finds an implicit uncertain-cleanup retry race. The [Java repair](../../../qualification/results/P1-S4/2026-10-16-vnc-cleanup-repair.md) now passes ten actual owner JVM methods, both Lint variants and corrected offline packaging inspection in a source-set projection. The [explicit retained-native entry](../../../qualification/results/P1-S4/2026-10-16-vnc-native-reuse.md) now verifies actual directCLI and canonical `:vnc:verifyNative` with three caller-bound properties and unchanged original native bytes/receipt. Fourteen helper methods pass. This verifies the DSL/property/Exec entry only; complete canonical Java build, root/app adoption, device runtime and release closure remain open.

**Owner:** Maintainer/build developer. **Prerequisites:** P1-S2, P1-S3. **Status:** planned.

Work:

1. Add real Android build/lint/unit/instrumentation checks and contract fixture validation; retain documentation checks.
2. Record dependency versions/licenses/update owners and actual artifact closure. Apply the selected open-source Desktop direction in ADR0010 while retaining independent Console terms; finalize compatible exact terms and source/notices/build materials before release.
3. Exercise mismatched app/protocol versions, missing recipient, unavailable permissions, and independent install/uninstall.
4. Publish the P1 evidence bundle and refine remaining stage estimates using actual implementation results.
5. Resolve X server, FreeRDP, VNC engine and transitive native codec/license/security/build obligations before P2/P3 embedding; P5 stream review does not substitute for this gate.
6. Finalize each engine capability outcome after its own feasibility and distribution/build review, including separate CAP-SSH transport and CAP-TERMINAL renderer adoption. A blocked renderer, X11, VNC or USB choice leaves that feature blocked without holding the independently usable foundation or unrelated app lanes.

Deliverables:

- Reproducible CI artifacts and dependency register
- Foundation gate record with unresolved issues

Exit evidence:

- Both apps install independently and communicate only through validated resource contracts.
- CI output is labeled separately from physical-device evidence.
- Every reviewed engine has an explicit scoped readiness outcome; unresolved feature-specific adoption is not hidden by the foundation being done.

Record evidence under `qualification/results/P1-S4/` using the [run template](../../06-validation/templates/run.md). Acceptance cases: Q-X01, Q-S01. Official/upstream source IDs: R06, R07, G02, R20, R22, R25, R26, R32; resolve them in the [source register](../../07-reference/source-register.md).

## Gate and failure route

Resolve each engine's license/security/build and capability gates before integration. P1-S5 runs early but does not become a prerequisite for unrelated Console/RDP/VNC work. Freeze only demonstrated native UI behavior. Every done stage links actual evidence; a documentation check does not qualify a device.

## RDP credential implementation detail

P1-S3 implements the [RDP authentication/vault contract](../../02-architecture/rdp-authentication-security.md) and Q-S02: explicit per-app saved credentials protected by Android Keystore and local authorization, backup/transfer exclusions, key invalidation, bound session access and secret-free exports/logs. Native trust/provider review remains P1-S4; actual RDP session integration is P3-S7. The temporary P0 diagnostic file does not satisfy the vault gate.

## P1-S2 native display checkpoint — 2026-10-16

The [0.1.6 result](../../../qualification/results/P1-S2/2026-10-16-native-display-settings.md) adds real independent appearance/density settings, live theme/spacing updates and canonical vault-dialog presentation. Strict build/four Lint reports and12 actual Android cases pass. Six current Console viewport captures at font scale1.0 were inspected across both themes/all three densities. The [display contract](../../04-design/native-display-settings.md) records nominal versus observed geometry, system-owned chrome and effective preference restoration.

This increment advances existing P1-S2 work without changing dependencies, efforts or required exits. Full component catalog, HTML specimen states/density, scaled/narrow/short native windows, physical input/focus/accessibility and engine-backed layout remain open; P1-S2 is not complete. The P1-S3 protected-vault and real SSH enrollment/authentication prerequisites remain separate.

## P1-S2 measured geometry and specimen checkpoint — 2026-10-16

The [actual native geometry result](../../../qualification/results/P1-S2/2026-10-16-native-window-geometry.md) passes eight local-workspace cases on unchanged0.1.6 products: narrow/short windows at font scales1.3/2.0, all three density tiers across the chosen configurations, seven individually reachable labels and preserved draft/window identity. Sixteen Light captures are inspected; exact system/app restoration is verified. The [HTML follow-through](../../06-validation/2026-10-16-specimen-density.md) separately passes16layouts/10states with existing canonical typography and all three densities.

This supersedes the earlier wholly unrun HTML/narrow/scaled statements only for the recorded cases. ScaledDark, physical input/focus, accessibility, profile/trust/vault geometry, caption/hit-target overlap, full component catalog and remote scene remain required. Source-identified profile dialog/editor reveal risks need actual probes. Dependencies, effort and P1-S2 exit criteria remain unchanged; P1-S2/P1-S3 and the full implementation goal stay in progress.


## Current P1-S3 observation follow-through

The [0.1.8 observation result](../../../qualification/results/P1-S3/2026-10-16-ssh-key-observation.md) advances work item1 with a real credential-free signed-key route, foreground comparison and explicit trust CAS. Fourteen actual Android cases and13 separately executed host cases pass;6 default assumptions remain skips. Exact-pin Reader retirement, source/license scope and remaining authentication/rekey/jump requirements are documented in the [contract](../../02-architecture/ssh-key-observation.md). P1-S3/P1-S4/CAP-SSH and the full Console/Desktop requirements remain open; no phase dependency or capability promotion changes.

The [finite authentication result](../../../qualification/results/P1-S3/2026-10-16-ssh-authentication.md) now supplies a real strict-password engine and exact current-profile/trust admission coordinator. Strict build/four zero-issue Lint reports,52 actual Android storage/coordinator cases and10 final real host cases pass. The first host run's rekey expectation failure is retained; pre-authentication rekey times out at the pinned library and does not qualify live rekey. New product bytes are built-only. The [next native entry](../../03-platform/googlebook/product-ssh-authentication-entry.md) must integrate transient credentials, foreground lifecycle and actual Android authentication; protected saved use and full sessions remain required. P1-S3 stays in progress and all P2 dependencies are unchanged.


## Current native authentication follow-through

The [0.1.9 native-entry result](../../../qualification/results/P1-S3/2026-10-16-ssh-authentication-ui.md) supersedes the preceding built-only/next-entry statement for this finite route. Both owning products now provide native transient credentials, actual password authentication and lifecycle/undo retirement, with ten distinct Android app/case combinations and six affected final-build reruns. At that finite-entry checkpoint, reusable sessions were still open. The subsequent 0.1.10 native files result above adds bounded foreground retention; saved-vault use, durable session recovery, protected IPC and full P1-S3 acceptance remain open. The [current foundation summary](../foundation-readiness.md) distinguishes implemented construction from remaining adoption and product gates; dependencies and capability outcomes are unchanged.

## Shared fullscreen and overlay acceptance

The user requires remote desktop ready-time fullscreen with compact local controls. The [presentation contract](../../04-design/remote-session-presentation.md) applies to P1-S2 design, P3-S7/S8 native RDP/VNC and P5-S2 native streaming. Check eligible foreground entry, denial/windowed fallback, overlay/input separation, safe escape, actual geometry and retirement. This refines existing session UX; protocol, security and physical gates remain unchanged. Sources G24/R304–R306; [worker visual checks](../../04-design/worker-visual-acceptance.md) accompany implementation.


## P1-S4 VNC wrapper runtime checkpoint — 2026-10-16

The [actual isolated Googlebook case](../../../qualification/results/P1-S4/2026-10-16-vnc-wrapper-runtime.md) passes one repaired public-Engine/JNI method with two sequential silent TCP peers, live-owner refusal, single-stop retirement and peer EOF. Separate setup/measurement/teardown preserve the complete installed0.1.15 product/system/boot baseline and remove both isolated packages. Prior compiler/JVM/Lint/native evidence is carried unchanged; no new upstream battery runs. Protected VNC negotiation/authentication, frames, input, fullscreen presentation, owning-app/root adoption and release closure remain open. This bounded runtime result changes neither P1-S4 prerequisites nor formal stage/capability acceptance. Host-side construction continues independently under the parallel execution contract.


The [bounded private VNC product entry](../../03-platform/googlebook/vnc-product-private-entry.md) now records D16 construction scope without closing planned P1-S4/P3 gates. First adapter proposal remains uncompiled/unadopted with three source findings awaiting separate repair: asynchronous secret-buffer ownership, autonomous-retirement stale presentation and foreground-return readiness. Clean foundation CI also lacks the native preparation required by unconditional root adoption. Resolve that route, affected source/build/UI checks and actual protected-frame evidence; retained release/license closure stays separate. No stage/dependency/status promotion follows from this review.
