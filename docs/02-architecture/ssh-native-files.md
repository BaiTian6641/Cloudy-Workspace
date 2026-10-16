# Native retained SSH files

**Scope:** P1-S3 ownership construction and the bounded CAP-SSH experiment. This native caller uses the [retained engine](ssh-retained-session.md) and existing owning-app profile/trust stores. Source implementation alone does not complete P1-S3 or activate P2-S1/P2-S8. See [execution state](../05-delivery/execution-state.md) for accepted builds, installed versions and actual qualification.

## Owning product and explicit connection

Console and Desktop each declare the same source Activity privately in their own package. The workspace's **Browse SSH files** action opens that package's screen with no endpoint, credential, trust or connection extras. The screen loads the owning app's real profiles; launch and permission completion never authenticate. Shared source does not share processes, storage, credentials or sockets.

The user selects a saved SSH profile and supplies a username/password for this connection. Before authentication, the coordinator rereads the exact profile generation/revision and current accepted host key. The retained engine independently verifies the server key on the actual transport. Unknown, changed or revoked trust is not implicitly enrolled by this screen. The selected logical peer remains the trust identity when DNS supplies a numeric address.

Username/password fields use native Compose text values during editing. The Activity clears them on submission, selection change, focus loss, pause, state save and destruction. A keyed field epoch disposes undo state and fences stale input callbacks. The mutable credential holder is transferred to the model's bounded attempt and consumed/closed on every path; it is not persisted. No saved-vault authorization follows. FLAG_SECURE is set before composition and autofill is excluded; these settings do not establish whole-process, IME, overlay or memory-erasure guarantees.

An attempt has one original 15-second preparation budget, including at most five seconds of DNS and at most ten seconds of engine connection/authentication, clipped to the remaining budget and coordinator deadline. It chooses one returned address. It does not queue credentials, retry another address, reconnect, or replay after a permission grant. The screen offers local-network permission separately and requires fresh input afterward.

## Visible ownership and lifecycle

`SshFilesSessionModel` is an Activity-scoped ViewModel constructed with an Application, never an Activity or View. It owns live observable state and one session lease. A private process-owned runtime retains the actual transport factory, capacity-one lease and cleanup quarantine beyond any one Activity/ViewModel. A competing owner receives Busy; creating a new screen cannot reset uncertainty.

| Event | Policy |
| --- | --- |
| Focus loss or pause while Connecting | Clear transient input and cancel that attempt, including worker promotion that has not yet published an established UI state |
| Focus loss or pause after published Connected/Reading | Keep the established connection while the owner remains visible; do not treat desktop focus changes as disconnection |
| Ordinary Stop / hidden window | Request disconnection; returning requires an explicit connection and new input |
| Stop for configuration recreation | Retain a published established session for a five-second handoff to a replacement owner |
| Replacement Start | Attach a new opaque instance token; retain the same model/session only while the handoff is valid, without authentication or file-operation replay |
| Expired or abandoned configuration handoff | Request retirement; a delayed main callback cannot extend the lease because attachment also checks elapsed time |
| Owner permanently cleared | Request nonblocking retirement; actual engine workers prove completion |
| Process loss / newly restored model | Restore only owner/schema/pending metadata and an interruption notice; start disconnected |

UI enablement observes `LocalWindowInfo.current.isWindowFocused` from the current Compose owner. Credential and remote-operation admission separately rechecks the Activity's current `hasWindowFocus()`. A per-Activity boolean updated only by `onWindowFocusChanged` is insufficient: the actual recreation regression retained a focused framework window while that boolean stayed false. Loss callbacks still clear transient credentials and cancel Connecting; no focus is requested or manufactured. [WindowInfo](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/WindowInfo) is a public Compose API, not an Android OS-level requirement.

Opaque tokens and attachment epochs fence stale Stop/timeout callbacks. New selection and remote operations require an attached owner; credential submission also requires resumed/focused input. Lifecycle cancellation wins against late result publication. A completed operation may update the retained model during a valid configuration handoff, but no new request is automatically dispatched on replacement Start.

The five-second handoff is Cloudy policy, not an Android guarantee. Official [multiwindow guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode), [ViewModel ownership](https://developer.android.com/topic/libraries/architecture/viewmodel) and [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle) supply the platform basis. Neither `onCleared` nor `onDestroy` is a guaranteed process-death callback. This screen adds no foreground service or background-session authority.

## Real file operations and presentation

After promotion, the model publishes Reading while the initial `.` listing owns the worker, then Connected with the actual returned entries. Subsequent listing and preview requests use the same authenticated connection and run sequentially. The existing engine opens and closes each real SFTP channel and preserves exact current profile/trust invalidation.

The UI provides explicit path listing, refresh, parent/directory navigation and regular-file previews. Parent navigation appends `/..` for server interpretation; it does not normalize a remote path as a local file. Returned names never become local publication destinations. Listings default to 128 entries and the engine rejects overflow without presenting a successful truncated list. Paths remain bounded to 4,096 UTF-16 units and names to 1,024, under the existing engine validation.

Preview reads are limited to a complete 64 KiB regular file. An already known larger size is refused locally; the actual read independently enforces metadata and cumulative bounds. Strict UTF-8 decoding supplies text when suitable; other data displays at most the first 256 bytes as hexadecimal and separately reports the complete read size. Reads are not snapshots if another program changes the remote file. Selected SSHJ filename decoding remains distinct from strict preview decoding.

Disconnect and Close stay outside the scrolling content. Identity, input, directory entries and preview scroll within safe drawing/IME insets using the Cloudy tokens and native font scaling. Completed navigation/preview may reveal its result; field edits do not trigger scrolling. Security protections remain in force during testing. Physical keyboard/IME, accessibility and narrow/scaled scene acceptance require their own evidence.

## Cancellation and actual retirement

The main thread requests close without blocking. A bounded separate retirement lane waits for setup publication and calls the engine's actual `awaitClosed` outside its notification callback. That avoids joining the callback from itself. The coordinator releases the matching proof; the runtime frees its slot only after certain completion. Published results require the exact session/generation, uncancelled authority and a nonretired proof.

Unexpected closure, profile/trust mutation, operation failure and explicit disconnect clear displayed remote content. No late preview may appear after cancellation. Cleanup uncertainty preserves the slot and quarantines the process runtime. The current factory can return `TIMED_OUT` before publishing a transport both for an authentication deadline and for an expired cleanup wait. This caller conservatively quarantines that ambiguous engine outcome; ordinary DNS/pre-engine timeouts do not take this path. A normal authentication timeout may therefore reduce availability until process restart even if cleanup later succeeds. Do not report it as proven resource release or silently bypass quarantine.

## Remaining scope and qualification

This screen is a real read-only construction increment. Full Console files still require upload/download publication, remote editing with the approved per-save overwrite policy, session-linked terminal presentation and drag/drop. Command/PTY, full terminal integration, SSH methods/jumps, separate X11 windows, saved-vault use and cross-app handoff remain independent work. The foreground visibility policy here does not replace the required durable terminal/session design.

Actual product qualification must establish real list/read reuse, established Activity recreation, focus policy, hidden-owner retirement, profile invalidation and cancellation during a host-observed held READ. Each owning package needs exact profile/trust baseline restoration, actual engine retirement, no authentication replay and host closure before fixture shutdown. Framework focus callbacks and ActivityScenario transitions establish only their sampled lifecycle routes, not physical desktop input or all system gestures. Reuse unchanged lower-level engine evidence; do not label policy simulations or CI as real network/device acceptance.
