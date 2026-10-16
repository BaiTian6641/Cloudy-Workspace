# App-owned connection profiles

P1-S3 foundation contract, reviewed 2026-10-16. This supplies local endpoint configuration for the later P2-S1 SSH and P3 desktop integrations. It does not complete those stages, establish trust or enroll a handoff resource. Read with [security and lifecycle](security-and-lifecycle.md), [RDP credential binding](rdp-authentication-security.md) and [handoff admission](handoff-admission.md).

## Ownership and identity

Console and Desktop each own a separate private catalog. Shared source does not share data, require the sibling package, or start a common service. Console profiles select SSH. Desktop profiles can select SSH for files, RDP, VNC or streaming. A protocol choice records intent; it does not claim an installed, reachable or supported backend.

The initial record contains a random opaque v4 UUID, positive revision, display label, protocol, logical host and port. The label follows the existing single-line name policy: at most160 UTF-16 units before scanning, at most80 code points after trimming, valid Unicode, no controls or line separators, and nonblank text. Host syntax is canonicalized without DNS or interface lookup. Ports range from1 through65535. Duplicate endpoints are allowed as distinct configurations; labels and addresses are not unique identities.

Host normalization is shared with the vault, preserving existing DNS/IDN, expanded IPv6, scoped-address case and IPv4 rules. Existing vault bindings must not change merely because the helper moves into the domain module. Regression evidence must cover the delegated binding API as well as the new domain API.

Profiles contain no account names, passwords, private keys, certificates or bearer tokens. Their labels and endpoints can still be private information, so logs and ordinary string representations must not expose them. They are stored in the owning app's credential-encrypted no-backup area. Do not export this catalog implicitly.

A profile ID is not a window ID, credential ID, host-trust record, environment, workspace or registered resource. Editing its protocol/host/port creates a new revision of configuration. Future credential/trust/resource links must revalidate their exact endpoint binding; they must never follow a profile UUID blindly. Tags, environment selection, jump chains, authentication methods, explicit credential references, trust UI and import remain required later work. Their absence in this initial storage increment does not narrow C01 or the desktop requirements.

## Concurrent editing and persistence

The catalog has an immutable random generation, separate from each record's revision. A read returns the generation and an immutable profile snapshot. Every mutation requires that generation. Update and delete additionally require the originally read profile ID and revision, carried together in an immutable edit token.

Compare generation, record existence and revision inside one real SQLite transaction. A competing update returns a conflict; a deleted record returns not found; neither path performs an upsert. A stale editor cannot recreate a deleted profile. Revision overflow rejects an update. Create allocates a new random ID inside the local operation, never accepts a sender-supplied ID, and never silently merges a matching endpoint.

Use bounded app-private storage with at most512 records and a4MiB database ceiling, excluding rollback-journal and filesystem overhead. Reject main-thread and device-protected-context access before opening files. Serialize storage through process and file locks. Existing storage opens without a create flag and with a nondeleting corruption handler. An immutable lock anchor, marker and database metadata distinguish fresh initialization from incomplete or lost established state. Unknown schema, malformed metadata, missing established artifacts and corruption return unavailable; they do not become an empty catalog or trigger automatic deletion/recreation.

Return mutation success only after transaction completion. Failures after a mutation might have committed return an explicit completion-unknown result. The caller refreshes and reconciles the catalog; it never automatically repeats Create/Delete. Process death after a commit but before its callback can leave a saved change without a displayed success message. A catalog read is the authority for current local state. No exactly-once UI callback or physical power-loss guarantee is implied.

Whole-app data deletion/reinstallation or privileged replacement of all artifacts is outside ordinary persistence. Any future explicit recovery must create a new generation and reject old edit tokens. There is no reset/repair operation in this increment.

## Native editor integration

The implemented screen is **Connection profiles**, with genuine Add, Edit and confirmed Delete operations, visible protocol/endpoint and a clear separation between draft and saved data. An unavailable store must not render as a successful empty list. There is no Connect control until its real backend operation is integrated.

Keep a draft and its original edit token on a conflict. Refreshing the committed row must not overwrite the draft silently or give it a fresh revision automatically. Offer deliberate reload/discard or review-and-reapply behavior. Save draft metadata through the existing bounded Android saved-state mechanism; persist committed profiles in the database. Recreation may restore an unfinished editor, but never replays a mutation.

Ordinary native text editing, keyboard focus, visible labels, user font scaling, narrow desktop windows and both appearances remain required. Remote session, credential authorization, trust enrollment and signed handoff effects are separate operations. A profile-details page is not a terminal, remote desktop or file browser.

## Official API basis and validation

The implementation uses the existing public Android SQLite transaction/open APIs and no-backup context APIs, plus the reviewed descriptor synchronization and locking primitives (G137, G198–G204 and R266). [Android SQLiteDatabase](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase) documents transaction commit/rollback and parameter-bound update/delete. [Android's state guidance](https://developer.android.com/topic/libraries/architecture/saving-states) distinguishes transient saved UI state from persisted app data and requires database work off the main thread. These are library contracts, not device qualification.

Required evidence: unchanged vault-host normalization; domain validation; real SQLite create/read/update/delete; generation and revision conflicts; deleted-editor rejection; duplicate endpoints; owner/protocol policy; bounded capacity; corruption/newer-schema preservation; main-thread/device-protected rejection; and actual native editor/lifecycle behavior once wired. Reopening a store object does not prove process restart, and tests in isolated fixture roots do not prove both installed products' independent storage. Keep each claim tied to its actual run.

Implementation and qualification status belongs in the current [execution record](../05-delivery/execution-state.md). The signed endpoint and full enrolled-resource tuple remain pending under the handoff contract.

The [first storage increment](../../qualification/results/P1-S3/2026-10-16-connection-profiles.md) now implements this domain/store boundary and passes40 JVM cases,19 actual Android cases and two separate-process phases. The subsequent [native editor result](../../qualification/results/P1-S3/2026-10-16-profile-editor.md) implements the screen above, with12 actual product profile cases,10 Bundle/Parcel cases and4 window regressions. The [recovery/isolation follow-up](../../qualification/results/P1-S3/2026-10-16-profile-recovery.md) additionally qualifies controlled admitted-save Activity recreation and fixture-backed installed-product catalog/file isolation. Actual process death and physical/visual qualification remain open. G205/API52 record the shared IDN helper and API availability; the existing vault canonicalization remains unchanged.

The editor retains original tokens during ordinary Refresh. Review reads a separate current row; Reapply confirms the displayed row and submits that exact reviewed token, which can still conflict. Draft changes invalidate review. Deletion binds its confirmation to saved fields and the original revision, never the unsaved label. A process-wide bounded executor serializes admitted work, including reads after Activity recreation. Pending mutation is saved before dispatch; restore never dispatches it. A fresh successful explicit mutation or explicit discard/reload resolves the editor state, while failed reapply preserves earlier uncertainty. Leaving remains possible if storage cannot be read. Exact lifecycle, native text and bounded-state guidance is tracked as G206–G209/API53; a restored Bundle does not prove real process-death behavior.

The [SSH host-trust entry](ssh-host-trust.md) defines the next separate ledger. Configuration identity never supplies host-key approval; the planned exact peer/key/revision binding remains independent of profile labels and credential handles.
