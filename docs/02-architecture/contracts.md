# Proposed contracts and state ownership

The [schemas](../../protocol/README.md) define the initial serialization vocabulary. Resource handoff now has a bounded [domain model and Android codec](handoff-runtime-profile.md); no handoff endpoint or caller authentication is implemented by that codec. Other schema artifacts remain proposals. Freeze transport compatibility during P1 and add transport-specific authentication tests before exposure.

## Resource handoff

Use a versioned envelope containing action, host, environment, workspace, resource, request ID, and creation/expiry times. The receiver resolves resource IDs to approved roots. Do not send commands, credential material, or source-app absolute paths. Keep signature-level access control for privileged IPC and validate the caller identity in addition to the envelope.

Actions initially select a resource: `open-console`, `open-desktop`, `open-files`. A browser link can propose navigation after validation; it cannot enroll credentials, execute a command, assign USB, or approve an agent operation. Android Continue On is a distinct cross-device state API and is not the authority boundary for these operations.

## Capability manifest

Return `protocolVersion`, supported versions, installation ID, boot ID, session ID, agent/backend versions, environment IDs, desktop backend/permission flags, USB consumer support, notification fidelity, and event support. The example schema keeps the initial capability vocabulary small; extensions follow a negotiated namespace.

Use `unknown`, `unavailable`, `permission-required`, `ready`, and `error` deliberately. Absence of data is not false, zero usage, an unlocked session, or a released device. Tie dynamic capabilities to observation time and session identity.

## Requests and events

A managed operation requires authenticated identity, allowed scope/resource, request ID, expiration, arguments validated by operation type, and an idempotency policy. Idempotency means the server recognizes a repeated request ID; it does not authorize retrying arbitrary side effects. USB flash and shell execution are never blindly retried.

An event has event ID, stream ID, monotonically increasing stream sequence, host/boot identity, occurrence time, kind, resource/job reference, and structured payload. Consumers commit a cursor only after persisting the event. On expired retention or stream reset, the server returns a gap marker and fresh snapshot; clients do not invent a complete history. Deduplicate by stable event ID, not by title/body equality.

## Ownership table

| State | Authority | Client persistence |
| --- | --- | --- |
| SSH host trust | Enrolled Console/Desktop trust store | Per-app protected record |
| Terminal layout | Owning Console window/app | Layout + remote session identifiers, no command replay |
| Live PTY/process | Selected host | Reference and last observed status |
| Device owner | USB backend/provider/consumer reconciliation | Last confirmed owner + observation time |
| Transfer source version | Source backend | Job journal and source identity |
| Remote file commit | Destination backend | Confirmed commit result, not merely bytes sent |
| Managed job status | Host supervisor/integration | Event cursor + cached status |
| Notification action | Origin adapter/current source event | Expiry and capability flags |

## Compatibility policy

Resource handoff v1 uses its existing single numeric `version` field, with no implicit minor version. It rejects unknown members and unsupported versions. A future optional field therefore requires an explicit negotiated compatibility policy or a new supported version; do not silently add major/minor fields to v1. Other protocol families must define their own explicit version policy before implementation. Do not repurpose an enum value or an identifier's meaning. Wire schema validation, semantic validation, and authorization are separate checks. JSON Schemas cannot establish request expiry relative to the current clock, ownership, path scope, authenticity, or replay safety; receiver/runtime code must enforce those rules. The [runtime profile](handoff-runtime-profile.md) adds size, timestamp and lifetime limits without claiming those authority checks are complete.

File publication additionally declares exclusive-create, ordinary/conditional replacement and acknowledgement semantics per backend. An overwrite confirmation is scoped to a file/destination/staged revision and cannot authorize future saves. A timeout after publication is completion-unknown until reconciled; see [the publication contract](file-publication.md).
