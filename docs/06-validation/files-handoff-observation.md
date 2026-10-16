# Passive observation of a real files handoff

**2026-10-16; implemented and compiled in Console/Desktop0.1.14/code15, device execution pending.** This narrowly scoped debug observer supplies the missing original-object evidence for [positive handoff qualification](files-handoff-positive-qualification.md). It does not implement an alternative handoff, grant authority, or make the measured operation pass. [Build and review evidence](../../qualification/results/P1-S3/2026-10-16-handoff-observation-build.md) is separate from runtime acceptance.

## Purpose and variant boundary

An idle control, matching package/PID, or absent binding can belong to a new object or precede completion of an old synchronous call. Qualification must observe the original sender Activity/call and receiver Activity/model/session through actual completion. Hooks therefore capture bounded identity tokens at the real operation boundaries. They do not retain credentials, account/profile/resource IDs, host/path data, tickets, envelopes, content, raw errors or UI text.

Debug hooks only enqueue fixed events and small object tokens. One background collector handles private file I/O. Product admission, authentication, replay, dispatch, transport and quarantine behavior remain unchanged. There is no control endpoint, manifest permission, new dependency, replay or credential store. The release counterpart explicitly disables qualification: no file lookup, collector, queue or retained identity map. This is a disabled diagnostic, not a replacement implementation of a product action. Release class and APK inspection accompanies compilation.

## Bounded private provisioning

The parent provisions one private `noBackupFilesDir/files-positive-observation/` directory, mode0700 and owning app UID, and `config.json`, mode0600, regular and not a symlink. The ASCII JSON is at most2048bytes and has exactly these fields:

| Field | Accepted value |
| --- | --- |
| `schema` | Integer1 |
| `run` | Canonical lowercase UUIDv4 for the finite run |
| `role` | `SENDER` or `RECEIVER` |
| `operation` | `OPEN_FILES` |
| `processNonce` | Fresh canonical lowercase UUIDv4 |
| `deadlineElapsedMillis` | Integer in the future, at most300000ms after parsing |

Only JSON space/tab/CR/LF are accepted as whitespace; escaped strings, unknown/duplicate fields, coercions and trailing content are rejected. Correlation nonces are not product authorization credentials. Config must precede the first relevant hook in a fresh normal process. A preparation instrumentation process already starts and then stops an unconfigured observer; prove that preparation's real retirement, end instrumentation and retire that process before measurement. Do not infer actual retirement from killing a process.

The collector exclusively creates `claimed-<processNonce>` with public `O_CREAT|O_EXCL|O_NOFOLLOW`, mode0600. Claims are never reset or replaced. Existing receipt/backup/new families refuse collection; retained staging refuses the exclusive staging open. Missing/invalid config, an existing claim or collector failure produces missing or invalid evidence, never product success. A later deliberate run uses fresh correlation/config and retains prior evidence.

Limits are32 events,12 object references, a32-item queue,16KiB snapshots and one worker until the original role Activity is destroyed or the configured deadline expires. Every exit permanently stops that process's collector and clears its queue/map. Unconfigured debug operation starts only the bounded lookup worker and creates no receipt. There is no background retry.

## Checked receipt publication and identity chain

Each snapshot exclusively creates a same-directory `.stage` file, checks descriptor mode/type/owner, writes, checks file sync and stream close, then uses checked rename as its final required fallible action. Failed stages preserve uncertain staging and the missing or prior unsealed receipt. No cleanup or automatic retry follows. This is checked file publication; directory and power-loss durability are not promised. Public [Os](https://developer.android.com/reference/android/system/Os) operations used here are available from API21; [FileDescriptor.sync](https://developer.android.com/reference/java/io/FileDescriptor#sync()) provides a checked file synchronization operation from API1. G201/G250 and API67 track their qualification.

| Original object chain | Actual boundaries observed |
| --- | --- |
| Sender Activity → submit Call | Activity creation; accepted submit; actual execute completion; successful unbind and capacity release; original Activity destruction after planned Close. Discovery and abandon alone do not satisfy retirement. |
| Receiver dispatch token | Actual dispatch start and once-only permit release. No ticket/envelope is recorded. |
| Receiver Activity → ViewModel → session | Original Activity/model association, session claim, publication of a real connected transport, actual awaited transport retirement with certainty and successful capacity CAS, same-generation/session IDLE publication, original Activity destruction after planned Close. |

Duplicate/replacement objects, missing events, pre-terminal model clearing, uncertain retirement, bounds/order failures and expiry cannot complete an accepted chain. The receipt includes actual PID and [Process.getStartElapsedRealtime](https://developer.android.com/reference/android/os/Process#getStartElapsedRealtime()) (API24), local random nonce and elapsed event times. The parent separately checks exact APK/version/source, boot/process continuity and external phase timing. These APIs do not turn a receipt into an authorization grant.

## Runtime acceptance still required

Require a sealed receipt with `invalid=false` and a complete original chain for both roles. Those fields alone are insufficient: correlate receiver destruction with `BEFORE_PLANNED_RECEIVER_CLOSE`, sender destruction with the returned original sender's enabled-Refresh phase, and both with actual normal-process UI and fresh host authentication/nested list/read/closure. Merely displaying Refresh is read-only; the controller does not click it again to manufacture success. Replacement after premature destruction cannot qualify the old sealed run.

The parent must execute each direction against one finite actual fixture, use the frozen0.1.14 product/controller/receiver-test artifacts, prove PREPARE retirement before process replacement, and preserve the immutable receiver checkpoint and intentional replay deltas. VERIFY/CLEANUP run only after the measured operation's retirement and exact ownership checks. Old build04/build03 artifacts remain historical; they are not silently combined with the newer products. No saved-vault prompt is authorized by this qualification.

Nine executed observer JVM methods cover grammar, evidence-chain rules and the shared publication sequence. Real JVM files plus injected open/permission/partial-write/sync/close/rename failures check ordering and prior-output preservation. They do not establish Android file permissions, exclusive claims, actual lifecycle timing, BAL, network paths or physical-device behavior. These remain the finite device case's responsibility. P1-S3 and full P0–P6 stay open.
