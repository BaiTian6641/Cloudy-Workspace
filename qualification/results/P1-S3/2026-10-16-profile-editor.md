# Native connection profile editor

**Date:** 2026-10-16. **Outcome:** Console/Desktop0.1.4/code5 implement local profile CRUD, explicit concurrent-edit reconciliation and bounded draft restoration. Twenty-six scoped Android cases pass on the HP Googlebook. P1-S2/P1-S3 remain in progress; remote connections, trust, credentials and handoff resources are separate unfinished integrations.

## Implementation and review

The nonexported `CloudyProfilesActivity` is reachable through each local workspace's Connection profiles action. It derives ownership from the application package and uses the production `ConnectionProfiles` store. Save/Create, Update and confirmed Delete execute real SQLite operations off the main thread. Console offers SSH; Desktop additionally offers RDP, VNC and streaming endpoint configuration. There is no Connect control or implied backend adoption.

The editor keeps its original catalog generation, row ID and revision while preserving native field text/selection and live composition. A conflict retains the draft. Review reads the current saved row separately; confirmed Reapply submits that exact reviewed revision. A second competing change still fails. A deleted row cannot be recreated by its old editor. Delete confirmation displays the actual saved label/endpoint/revision, independently of unsaved fields.

Only bounded text, selection, editor identity, original version and a pending-mutation flag enter framework saved state. Catalogs, composition spans, confirmation actions and reviewed-version approval do not. A restored pending flag means uncertainty, never permission to repeat a write. One bounded worker queue per app process orders reads after admitted writes across Activity recreation. New mutations require a resumed Activity and are disabled after a saved-state snapshot. Unavailable storage is distinct from an empty catalog.

Two direct Codex GPT6.1-Sol workers supplied disjoint state/screen/test source. A separate reviewer and parent inspected the final implementation. The parent corrected an unavailable-store exit trap: explicit discard/leave remains possible when an uncertain operation cannot be checked. Queue rejection or failed reapply also preserves earlier uncertainty. These corrected branches have source review, but no fault-injected UI execution in this packet.

The screen and confirmation dialog use canonical Cloudy geometry through `CloudyControlMetrics`, mapped section typography and the accepted surface-container role. No palette/density value changed. Native fields, wrapping actions, lazy catalog rows and caption/IME inset handling are implemented. Those source choices are not full visual, accessibility or physical-input qualification.

## Build and device evidence

The [build manifest](2026-10-16-profile-editor-build.json) binds changed source files and all five APKs. Frozen artifacts/logs are in `.cache/profile-editor-01/build-02/`; the full private source manifest also covers unchanged surrounding Android source. Strict dependency verification, Kotlin compilation and Console/Desktop/platform Android Lint pass:31seconds,303 tasks,63 executed and zero findings in each of the three Lint reports. Selected dependency versions/locks/checksums are unchanged. Repository Python AST passes for32 authored files, full Ruff passes, generated Android tokens match, and project validation passes120 JSON files,294 Markdown files,2,223 local links,488 sources and53 API records. These static checks do not substitute for the separate device cases below.

Build01 compiled the implementation and tests but failed Lint on two unused string resources. Both were removed; its log remains in `.cache/profile-editor-01/build-01/`. It is not accepted build evidence. Unchanged domain/identity regression results carry forward from the [storage increment](2026-10-16-connection-profiles.md) under D12.

Actual device: **HP Googlebook14, Android17/API37, arm64-v8a, build CL3B.260622.276.R2, incremental16560795**. Execution ran from **08:56:21.381431Z to08:56:53.150974Z**. Private evidence: `qualification/results/private/foundation/2026-10-16-profile-editor-01/`, with `state-tests.log`, `console-tests.log`, `desktop-tests.log`, package/cleanup receipts and `result.json`.

| Actual Android suite | Passed | What it establishes |
| --- | ---: | --- |
| ProfileEditorStateTest | 10 | Real Bundle/Parcel round trips, text/selection, composition removal on restore, strict schema/types/ownership/version binding, bounded Unicode and atomic invalid-save rejection |
| ConsoleProfileActivityTest | 6 | Production-store CRUD, unsaved draft/selection recreation, conflict and explicit reapply, changed-again rejection, deleted-row rejection and cancelled deletion |
| DesktopProfileActivityTest | 6 | Same contracts in the independently installed Desktop package, including an RDP profile in the CRUD case |
| Existing window contracts | 4 | Two per app: committed context/draft restoration and invalid-name protection with the new entry action present |

Every case reports actual status0; no assumption skip or default manual-vault invocation is counted. Profile tests use distinct fixture labels/IDs and exclude all initially existing IDs from cleanup. Passing cleanup asserts those test-created IDs are absent. Both app APKs were installed with replacement, without clearing app data. Both are now0.1.4/code5 and stopped. All three test APKs were successfully uninstalled and package absence checked. Boot ID/count and both preexisting manual-vault receipt hashes remain unchanged. No authentication prompt, secret operation, network connection, host fixture or reboot was initiated.

## OMP review and boundaries

Bounded task `PROFILE-COPY-002` used actual `zai/glm-5.3-flash` through OMP18.7.0, with zero tools, no fallback and a12.616second terminal result. Its packet contained only current project UI strings and the public profile contract; the supplied Word feedback, credentials and private device records were not sent. The parent accepted two copy suggestions with edits. Three others were rejected, including a false suggestion that saving a deleted editor would create a new profile. Assignment, packet hashes, events and leader decision remain under `.cache/omp-workers/2026-10-16-profile-editor/copy-01/`. The earlier Kimi timeout is preserved separately and was not treated as successful work.

This run does **not** establish admitted-write recreation, completion-unknown UI reconciliation, unavailable-store exit behavior, process-death recovery, simultaneous separate-process writers, catalog isolation between products, physical keyboard/IME, native themes/font scaling/pointer/accessibility or full profile/session integration. The current tests execute scripted Compose actions against actual Android components and storage, not physical input. Existing stage gates remain unchanged.

Failure recovery for the test harness is explicit: its current cleanup first waits for UI idleness. A future UI/scroll failure may prevent that wait and leave this run's uniquely labeled fixture rows. Such a run must be recorded as failed with cleanup unproven. Stop the owning process, inspect the private failure record and reconcile only its exact IDs/prefix before another run; never clear the app or delete unrelated profiles. The successful packet above required all cleanup assertions to pass. See [testing instructions](../../../testing/foundation/README.md).
