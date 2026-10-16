# Protected discovery and files handoff

**Implemented source, 16 October 2026; P1-S3 remains in progress.** Both products build as **0.1.12/code13**. The [transport evidence](../../qualification/results/P1-S3/2026-10-16-files-handoff-transport.md) records exact build/device scope and remaining acceptance. The full P0–P6 objective and existing stage, capability and release gates remain active.

## Checkpoint and evidence boundary

Corrected strict build03 passes compiler, dependency verification, JVM checks and four zero-finding Lint reports. Its generated AIDL output has the expected transaction IDs but omits trailing-data enforcement; the implemented public-Parcel guard supplies that check. The Googlebook separately passes54 isolated build02 Android storage/wire/Parcel/identity-policy cases with28 resource cleanup receipts, then seven corrected build03 Parcel cases. Fourteen genuine both-direction discovery/denial IPC cases and14 binding retirements pass after the retained failed first attempt. Only the genuine IPC cases establish scoped incoming sibling acceptance; policy checks do not. Normal visible destination/BAL and authenticated listing remain unrun. Read the linked evidence for actual transport results.

The earlier [0.1.11 own-app result](../../qualification/results/P1-S3/2026-10-16-files-location-ui.md) retains its exact listing/account/registration/manager/ticket/retirement evidence. It is not a blanket rerun on the changed build. Source review repaired package-manager/crypto work under SSH coordination, unexpected launch outcome classification, and the sender submission/departure race.

## Owning apps and deliberate disclosure

Each independent app keeps its own profiles, pins, private accounts, resource catalog, credentials, replay ledger and SSH transports. No shared UID, shared secret store, started/sticky service or permanent daemon is introduced. Ordinary SSH/files remains useful with the sibling absent. A signed sibling is an authenticated caller, not an owner of the receiver's credentials.

PRIVATE/SHARED/REVOKED semantics and permanent tuple retirement remain unchanged. Only a deliberately SHARED, current profile/trust-bound location can appear in discovery or authorize submit. The receiver exports only the disclosed label and four opaque IDs, never endpoint, account, navigation path, profile/trust identifiers or credentials. The initial root is a navigation destination, not confinement or stable remote-object identity.

Own-app metadata creation remains downstream of actual authenticated listing, current proof, exact account and explicit foreground consent. The new discovery page does not enroll, authenticate, reserve a request or grant later access. Each page is a fresh observation; resource/profile/trust changes can make it stale immediately.

## Wire and stable statuses

The generated synchronous AIDL interface is `io.github.baitian6641.cloudy.platform.ICloudyFilesHandoff`:

```aidl
byte[] discover(int protocolVersion, String afterResourceId) = 0;
byte[] submit(in byte[] envelopeUtf8) = 1;
```

Selected build-tools36 generated Java confirms `TRANSACTION_discover = FIRST_CALL_TRANSACTION+0` and submit `+1`; the explicit AIDL method IDs are permanent. No callback Binder, FD, Bundle, command or arbitrary Parcelable argument exists. Calls run off main and remain synchronous; unbind/timeout is not transaction cancellation.

Submit input remains unchanged HandoffJson/v1: max8192 UTF-8 bytes, exact existing keys/types, strict bounded timestamps/UUID/IDs, max120-second lifetime/five-second future allowance. OPEN_FILES is the implemented action. Known OPEN_CONSOLE/OPEN_DESKTOP return UNSUPPORTED_ACTION before replay reservation; unknown actions fail envelope validation. Registry references must additionally satisfy existing distinct canonical v4 tuple rules.

Reply codecs use strict UTF-8, exact keys/types, fixed integer wire IDs, duplicate/unknown-field rejection and no causes/raw payload in errors. Discovery max65536 bytes,32 descriptors; submit max1024 bytes. Fixed integer tokens do not accept strings, exponent/fraction or sign coercion.

Discovery successful object has exactly `version:1,status:0,locations:[{hostId,environmentId,workspaceId,resourceId,label}],nextAfterResourceId`. Failure has exactly `version:1,status:N`. Statuses:0 OK;1 UNSUPPORTED_VERSION;2 DENIED;3 INVALID_INPUT;4 BUSY;5 UNAVAILABLE. Labels preserve existing valid trimmed domain form, max80 code points/160 UTF-16 units; references disclose no authority. Strict increasing resourceId order, no duplicates. Cursor is empty or canonical v4 resource ID. A nonempty continuation equals the last resourceId of a full32-entry page; no continuation means end of this observation. Store-level internal pages separately support1..32 entries. No total-private-count field or stable catalog snapshot promise exists.

Submit object has exactly `version:1,status:N,consumption:C`:

| ID | Status | Consumption |
| --- | --- | --- |
|0|DISPATCH_REQUESTED|RESERVED(1)|
|1|DENIED|NONE(0)|
|2|INVALID_ENVELOPE|NONE(0)|
|3|UNSUPPORTED_ACTION|NONE(0)|
|4|RESOURCE_UNAVAILABLE|NONE(0)|
|5|EXPIRED|NONE(0)|
|6|NOT_YET_VALID|NONE(0)|
|7|DUPLICATE|NONE(0)|
|8|CLOCK_ROLLBACK|NONE(0)|
|9|CAPACITY|NONE(0)|
|10|BUSY|NONE(0)|
|11|ADMISSION_UNCONFIRMED|UNKNOWN(2)|
|12|ADMITTED_NOT_DISPATCHED|RESERVED(1)|
|13|ADMITTED_DELIVERY_UNCONFIRMED|RESERVED(1)|

These pairs are validated, not independently combinable. Consumption describes this call: DUPLICATE means a prior reservation exists, but this call made no new one. Post-reservation failures use admitted statuses; ledger unavailable/commit uncertainty cannot claim NONE. DISPATCH_REQUESTED only means startActivity returned, not visible destination/authentication/listing. Permission/parcel rejection may be a fixed SecurityException rather than a JSON response; remote or malformed/lost responses are not evidence of no admission.

## Identity, signature permissions and pre-unmarshal boundary

Each app defines `<its exact package>.permission.FILES_HANDOFF` at signature protection level, requests only the sibling's permission and declares exact sibling package visibility. CloudyFilesHandoffService is exported with that receiver-owned permission, bound-only in the default process. Shared-locations and files Activities remain nonexported.

Sender HandoffEndpoint verifies actual own/default-process/UID/CE identity; exact enabled sibling package/UID/signature exclusivity; explicit enabled/exported service class/default process; exact permission name, signature base protection and receiver ownership; granted sender permission and component enable state. It repeats endpoint verification after binding. A same-key unrelated app cannot select the endpoint merely by naming it.

Service onTransact enforces permission and captures genuine caller before delegating to generated decode; method-entry capture repeats to reject local/direct bypass. captureIncoming uses Binder.getCallingUidOrThrow (API29), rejects own UID, then reads exact counterpart/receiver UID-exclusive identity/signature policy and installation/signing snapshots. Captured proof has no public supplied-UID factory. Facts include version/install/update times and defensive digest lists of current signers/history. Recheck uses captured facts and current PackageManager, never a worker's Binder UID. Observed update/replacement/signing changes deny conservatively; privileged identical-metadata rollback is outside this check. PM/crypto runs off-main and outside shared SSH coordination. Capturing twice does not create two caller grants.

After capture, FilesHandoffParcel rejects unsupported transaction codes, the public FLAG_ONEWAY bit, no reply, FDs and total parcel bytes before argument decode. Caps are1024 bytes discovery,8192+1024 submit, zero descriptor-query payload. Selected build-tools36 generated Java enforces interface token but omits enforceNoDataAvail. The source correction therefore clones the bounded public Parcel via obtain/appendFrom, sets the clone to original dataPosition, enforces exact generated descriptor, reads only the two known argument shapes with decoded size bounds and invokes public enforceNoDataAvail (API33). It recycles the clone and leaves original cursor untouched for generated decoding. Framework bookkeeping bits may accompany a synchronous transaction, so an equality-to-zero flag check is invalid. Production uses only public IBinder.FLAG_ONEWAY; no hidden flag constant or downloaded generator implementation is patched. Null inputs are rejected semantically at actual method entry.

This is an application-side bound after Binder native receipt, not protection against all native parcel allocation. It does not turn the clone guard's local tests into caller authentication. The linked report binds the correction to its frozen build and separately scoped Parcel and transport cases.

## Receiver admission and actual main dispatch

FilesHandoffReceiver accepts only own app CE/default process and off-main execution, rechecks captured caller and uses one process-wide nonblocking permit with no payload queue. Discovery checks protocol and cursor, loads real current catalogs and returns a projected page; unavailable profile/trust stores fail the whole page rather than masquerading as stale/empty. Stale individual rows are omitted. Caller is rechecked before disclosure returns.

Submit checks caller, envelope/action and all four registered IDs before reservation. Under existing SSH coordination ordering it resolves exact SHARED row/current full profile+accepted-trust binding, checks receiver freshness and calls the real replay ledger. Existing ledger4096-retained-ID/2MiB/clock-floor/nondeleting-corruption/DELETE+EXTRA rules remain unchanged. Only committed RESERVED can proceed. It resolves the exact row again; after releasing coordination it rechecks caller/freshness, issues the handoff-only ticket and transfers permit ownership to dispatch. No replay reservation is undone, automatic resend performed or replacement request ID generated after uncertainty.

The main acknowledgment budget is1 second. Atomic PENDING->DISPATCHING races PENDING->CANCELLED. Pending timeout/deadline cancels and removes queued work/ticket so blocked main cannot launch it later. Once dispatch starts, timeout cannot retract it: delivery is unconfirmed and permit remains owned until actual runnable exit. Main starts the explicit private Files Activity with a local opaque ticket and new document/task flags. No PM/SQLite runs on main or while worker waits. Known ActivityNotFound/SecurityException refusals return admitted-not-dispatched; unexpected framework errors enter terminal UNCONFIRMED while actual runnable/permit retirement still completes. Framework return remains distinct from actual visibility.

Package/store update and Activity start cannot be atomic. The private screen can open and report unavailable if authorization changes after preparation. Ticket consumption and later registered credential/session gates protect actual remote access; no database lock is held waiting for main to pretend a stronger guarantee. A newly captured source departure does not undo a committed receiver reservation; Android may independently refuse launch after visibility/binding changes.

## Handoff-only ticket and receiver authentication

Ticket map remains process-private capacity16, max30-second monotonic lifetime, clipped to original envelope expiry for handoff provenance. The sender never receives the token. Origin binds captured caller, full original envelope and exact SHARED registration; issue rechecks current row and caller. Consume removes first, checks own package/UID/default process/CE, monotonic expiry, original UTC freshness, exact caller facts, action/tuple and current full SHARED registration, then rechecks caller/deadline outside coordination before returning. Failed consumption never reinserts authority. Manager's ordinary private Open retains its separate own-app behavior.

The receiver selects only that exact registration/account/initial directory. It does not authenticate from an IPC message. Fresh receiver-owned password is mandatory; coordinator beginRegistered/admit/establish recheck exact account/full row/profile/trust. Resource revocation cancels affected registered owners through actual transport retirement without cancelling unrelated ordinary sessions. No process/config restore reconstructs a consumed handoff from envelope/token saved state.

## Explicit sender UI and lifecycle

The receiver's own manager now offers a local entry to CloudySharedLocationsActivity. Its secure native screen names the counterpart and exposes explicit Refresh first page, Next page, Select and Request Open in counterpart; Close stays available. It shows only disclosed labels, not private endpoint/account/path. Selection is current-page object identity and supplies no request until explicit Open.

No onCreate/onResume discovery/submit occurs. Refresh and Next are separate explicit bounded observations; page/selection clear before each operation. Open allocates one canonical fresh UUID and30-second request for that deliberate click, sets pending before client queue admission and clears page/selection. No UUID is regenerated by timeout, reconnection or lifecycle callbacks.

One process-wide client slot and one actual worker operation own a binding. Bind from the actual visible Activity uses BIND_AUTO_CREATE|BIND_ALLOW_ACTIVITY_STARTS. Bind wait is4 seconds; UI timeout10 seconds. UI timeout/departure deactivates and unbinds, but global capacity returns only after actual synchronous call return and binding retirement. Unknown binding retirement retains capacity until process exit. It does not spawn a replacement worker to escape a held transaction.

Submission/deactivation share a tiny synchronized gate: departure winning prevents IPC submission; submit winning marks mayHaveSubmitted before IPC. It cannot retract admitted bytes. Focus loss/pause/save/destroy clear page/selection and abandon; late callbacks cannot restore page/selection when inactive, and disposed/finishing Activity ignores publication. Saved state includes only exact schema/owner/pending metadata; no descriptor, request, token, dialog, credential or replay command. Malformed restoration is conservatively uncertain.

An unknown Open keeps selection/Next/Open disabled. After actual retirement an explicit first-page Refresh can expose new choices and clear the UI block; its message explicitly says the earlier outcome remains unknown. Discovery is not outcome reconciliation and does not retry that envelope. Noninteractive late submit completion remains conservatively pending even if the worker eventually receives a definitive response. A user can make a distinct deliberate new choice, but no automatic repeat occurs. Close cannot prove the remote transaction stopped.

## Official source links and remaining qualification

Official references: [G239 official AIDL guide](https://developer.android.com/develop/background-work/services/aidl), [G240 bound services](https://developer.android.com/develop/background-work/services/bound-services), [G241 BIND_ALLOW_ACTIVITY_STARTS](https://developer.android.com/reference/android/content/Context#BIND_ALLOW_ACTIVITY_STARTS), [G242 enforceCallingPermission](https://developer.android.com/reference/android/content/Context#enforceCallingPermission(java.lang.String,%20java.lang.String)), [G243 getCallingUidOrThrow](https://developer.android.com/reference/android/os/Binder#getCallingUidOrThrow()). Public [Parcel APIs](https://developer.android.com/reference/android/os/Parcel) cover clone/cursor/size/FD/enforceNoDataAvail. API29/33/34 requirements fit productmin35; availability does not prove target37/device behavior. The source register tracks public Parcel/IBinder APIs and the inspected AOSP Binder bookkeeping behavior; selected generated build output remains authoritative for this build.

Actual required qualification remains:

- Carry forward the recorded strict build and 54 actual platform cases. Pure dispatch-state/local declaration/Parcel cases remain policy evidence, not sibling authority.
- Genuine target-product UID/process sibling transport both directions: empty discovery, version/cursor/malformed/trailing input, unsupported actions and random unregistered tuple with NONE consumption, no replay initialization. Parent must inspect actual receiver ledger/catalog baselines; sender-private snapshots cannot prove receiver preservation. This denial slice intentionally uses no BAL opt-in or navigation.
- Positive normal product Refresh->Select->Open in both directions using actual visible sender binding, actual receiver private screen, fresh account authentication and registered nondefault directory list/read. Receiver-targeted instrumentation or explicit harness destination launch cannot establish normal BAL/visibility. No generic picker or empty fabricated list counts.
- Wrong-signature, same-key unrelated, delegated Binder and exact permission/service ownership denials; install/enable/update/order and missing sibling; captured identity change before issue/consume.
- Duplicate/concurrent submit, original-envelope expiry/future skew/rollback/capacity, profile/trust/resource races, pending/main timeout and unexpected launch uncertainty, blocked actual client call, focus/source departure, consumed reservation with death before dispatch, ticket loss/restart and no replay. Preserve permanent production ledger/clock history rather than reset it for cleanup.
- Exact owned fixture cleanup while preserving prior app data, catalog/ledger history, credentials and settings; production installs, versions/SHAs and actual peer/worker retirement receipts. Physical input/focus, narrow/scaled UI/accessibility and broader system-gesture behavior remain separate.

Full protected saved identity, durable/background sessions, terminal/files writes/drop/X11 and Desktop engines remain open. No stage or capability is promoted by this integration increment.
