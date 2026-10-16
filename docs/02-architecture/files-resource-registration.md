# Receiver-owned file locations and protected handoff

**Implementation contract, 2026-10-16; P1-S3 in progress.** This follows the [native read-only files milestone](../../qualification/results/P1-S3/2026-10-16-ssh-files-ui.md), committed as `a469351`. The [domain/storage result](../../qualification/results/P1-S3/2026-10-16-files-resource-storage.md) passes12 new JVM and20 actual Android SQLite cases, strict compilation and four zero-finding Lint reports. New product bytes are built-only; the registration UI, signed cross-app endpoint and actual registered destination remain unimplemented and unqualified. [ADR0004](../adr/0004-resource-handoff-and-trust.md), [caller/replay admission](handoff-admission.md) and the unchanged [v1 envelope](handoff-runtime-profile.md) control the trust boundary. Full Console/Desktop scope and all stage/release gates remain required.

## Receiver-owned location

A location belongs to exactly one app. It records that app's exact SSH profile generation, identity, revision and endpoint; accepted host-key generation, revision and key; exact account name; and a directory path that the user deliberately registers after a successful authenticated listing. The account name is receiver-private metadata, never a password or an exported credential. It is not normalized or silently changed. A later connection must authenticate that exact account using credentials owned by the receiving app.

The initial directory is a **navigation destination**, not a filesystem sandbox or a stable remote object. Existing server/account access rules apply, and the native browser may subsequently navigate elsewhere. Relative paths retain server interpretation; no local filesystem normalization, prefix-based confinement, inode guarantee or automatic host mutation follows. The UI must describe this accurately. An actual directory listing establishes that the path was accessible for the authenticated account at that time; it does not promise future reachability.

One immutable direct-SSH aggregate supplies four distinct, locally generated canonical v4 UUIDs: host, environment, workspace and resource. Their relationship is one-to-one in this first route: exact local profile, direct SFTP environment, starting directory and open-files navigation. All four must match one stored record. This does not substitute for the later general host-agent/environment registry. IDs are opaque references, not secret capabilities or aliases for profile/window/vault identifiers.

The validated domain proposal contains a label, full profile/trust snapshots, path and account. It grants no authority by construction. The owning controller must bind a deliberate registration confirmation to the actual completed listing, current authenticated proof and selection/lifecycle epoch, then recheck the profile/trust/proof before admitting the store mutation. A sender cannot create enrollment by supplying a valid-looking tuple or hostname. A persisted row alone is not proof of this UI/transport admission; their implementation and evidence remain necessary.

## State and persistence

| State | Meaning | Allowed next state |
| --- | --- | --- |
| PRIVATE | Receiver-owned location, not disclosed or admitted through sibling navigation | SHARED after explicit disclosure confirmation; or REVOKED |
| SHARED | Exact signed counterpart may discover this explicitly shared label/reference and request navigation | REVOKED |
| REVOKED | Retained tombstone; no resource lookup, reconnection or sharing authority | None |

Creation allocates a fresh tuple at revision1. Share and revoke compare the store generation, resource ID and exact revision before a checked revision increment. No retarget, reactivation, automatic profile following, deletion-and-reuse or re-enable operation exists. The v1 envelope carries no resource revision, so revocation must permanently retire the tuple; a later registration uses new IDs even for the same path. Private/shared records become stale when their bound profile or trust changes, including accepting the same key again at a new trust revision.

The receiver owns an Android SQLite catalog in credential-encrypted no-backup storage and its default process. Reuse the existing permanent lock, installation marker, store generation, nondeleting corruption handler, nonblocking admission and DELETE/EXTRA transaction policy. Use the existing SSH coordination lock before store monitors/file locks. Existing initialized files open without CREATE; missing or corrupt established artifacts deny access and remain preserved. Do not clear a catalog to repair replay or capacity errors.

Capacity is512 retained registrations, including tombstones, and4MiB database size excluding journal/filesystem overhead. A snapshot exposes an immutable list. The store reads exact current profile/trust under the coordination lock before create/share/resolve. Revoke remains possible for a stale binding. Whole-app reset, replacement of all private files and snapshot rollback are outside the existing persistence guarantee. Power-loss durability is not established by a normal reopen test.

Storage success proves a committed metadata operation only. Production controller consent, actual listing, live resource invalidation and protected IPC are separate obligations. Do not add an inert listener or empty action to stand in for these effects.

## Deliberate counterpart discovery

The first route uses **receiver publication and explicit counterpart discovery**, rather than a new export-file/import-offer protocol or an additional persisted sender shortcut catalog. The user selects Share for one receiver location and confirms disclosure of its label to the named Cloudy counterpart. The sender separately selects Browse shared locations in that app. Only current SHARED records are returned after exact signed-caller validation; the receiver never exports its entire private catalog.

The bounded discovery result contains protocol version, opaque tuple and deliberately disclosed label. It excludes host/address, profile and trust identifiers, directory, account, passwords and credentials. Discovery never enrolls, authenticates or navigates. The sender selects a returned tuple and creates a fresh bounded v1 request. Discovery is not authorization for a later submit: the receiver resolves current authorization again. No registration is automatically shared, and a malformed or stale sender reference never creates one.

This is an explicit per-location counterpart grant under ADR0004, not shared credentials or a mandatory sibling dependency. The receiver permission/signature/package policy and live registration state remain authoritative. Existing ordinary SSH/files behavior remains useful with the counterpart absent.

## Selected transport and actual destination

Use one generated AIDL interface in the shared Android platform module, with a bound-only service in each owning app's default process. No started/sticky service, shared UID, permanent daemon, exported Activity, arbitrary URI, command, file descriptor or bearer PendingIntent is required. AIDL code generation uses the existing Android toolchain; there is no additional IPC runtime dependency. Synchronous remote methods can arrive concurrently on Binder threads, so one bounded admission owner and off-main client calls are required. The interface will have explicit stable transaction IDs and bounded fixed replies; exact discovery encoding and status constants must be reviewed before exposing the endpoint. [Official AIDL contract](https://developer.android.com/develop/background-work/services/aidl).

Each receiver defines its own signature permission: `io.github.baitian6641.cloudy.console.permission.RECEIVE_HANDOFF` or `io.github.baitian6641.cloudy.desktop.permission.RECEIVE_HANDOFF`. The sibling requests it and declares only exact counterpart package visibility. The sender checks actual service/permission ownership before binding. Every transaction enforces the receiver permission and captures actual Binder UID plus exact counterpart package/signature identity before clearing identity or moving work. Delayed presentation revalidates captured package installation/update/signing facts. A same-key unrelated app or delegated Binder handle does not bypass exact-package checks. Android caches the service Binder across clients, so onBind is not a per-request caller gate. [Bound-service lifecycle](https://developer.android.com/develop/background-work/services/bound-services), [permission enforcement](https://developer.android.com/reference/android/content/Context#enforceCallingPermission(java.lang.String,%20java.lang.String)).

The admission order is authenticated caller, bounded envelope/action, current complete SHARED resource and profile/trust relationship, freshness, durable replay reservation, then final caller/resource/freshness checks and one-use receiver-private navigation ticket. Only open-files is initially actionable; open-console/open-desktop remain explicitly unsupported until their real destinations exist. Do not launch a generic picker and report success.

The private files destination consumes the ticket once, selects exactly the receiver location and displays its own endpoint/account/directory. Fresh receiver-owned authentication is required before real listing of that directory. IPC itself performs no network authentication or remote operation. The registration binding must remain valid before credential admission and through the retained session; resource revocation retires only affected registered owners, while ordinary standalone sessions remain independent. Recreated/process-restored screens must not replay a consumed envelope or reconstruct authority from Intent extras.

Admission, dispatch request, visible private destination and actual remote listing are four different outcomes. Commit-before-effect provides at-most-once admission with possible lost navigation, not exactly-once delivery. Store/transport failure after a possible commit, a timeout or lost binding means delivery unconfirmed and never causes an automatic resend or regenerated request UUID.

## Lifecycle and launch limits

One explicit visible sender operation owns one binding and one outstanding off-main synchronous call. It may stop waiting at its deadline, but worker capacity stays occupied until the actual call returns. Unbind does not cancel an in-flight Binder/SQLite operation. Do not spawn replacement workers or retry to escape uncertain completion.

The initial contract allows an already-admitted navigation to finish after the source window departs, with unconfirmed delivery reported when acknowledgment is lost. It does not promise instantaneous cross-process focus cancellation. A bounded pending receiver ticket expires before dispatch; a dispatch already begun cannot be reversed. Resource revocation, receiver authorization and fresh receiver authentication still apply. This avoids an unsupported assumption that onUnbind or Binder death identifies a particular sender window's visibility.

The visible sender opts in with public `BIND_ALLOW_ACTIVITY_STARTS` (API34; covered by productmin35) while binding. Android retains authority over background Activity launch. Target37/device behavior still requires actual qualification; a returned startActivity call is not proof of visibility. Failed or uncertain launch receives no background retry, notification workaround or private-API bypass. [Official Context flag](https://developer.android.com/reference/android/content/Context#BIND_ALLOW_ACTIVITY_STARTS).

## Implementation and evidence order

1. Domain values and real receiver SQLite persistence, strict compiler/Lint and meaningful JVM/actual Android storage cases. These establish metadata construction only.
2. Real completed-listing registration/share/revoke UI and account-bound native destination, including current proof checks and affected-owner retirement. Preserve the working standalone files route.
3. Protected discovery/submit transport, actual sibling callers, private one-use navigation and final resource gates. Both directions require real package signatures/UIDs and receiver directory listing, not local helper calls.
4. Negative same-key-unrelated/wrong-signature/delegated caller cases, duplicate/concurrent/uncertain delivery, revocation/profile/trust races, lifecycle/BAL, process restart, missing sibling and installation/update order. Reuse unchanged codec/ledger/library evidence; expand only at changed integration boundaries.

The leader owns manifests, dependencies, shared device/host operations, Git and acceptance. Direct Codex GPT6.1-Sol workers own disjoint source paths; OMP Kimi/GLM assignments receive bounded reviewed packets. All failed attempts remain evidence. API/source records distinguish public documented contracts from actual compiled and qualified behavior. Current product0.1.10 and its accepted files evidence remain the installed baseline until a separately reviewed integration is installed.
