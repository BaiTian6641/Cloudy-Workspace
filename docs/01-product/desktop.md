# Cloudy Desktop product specification

**Release:** R2 companion, R3 integration, R4 optional embedded streaming. **Primary job:** operate a selected host's visual workspace and nearby hardware with clear context and ownership.

## Information architecture

Persistent destinations: Workspaces, Hosts, Files, Devices, Activity, Settings. A selected workspace includes host/environment, approved project roots, preferred desktop backend/profile, and optional device bindings. It stores intent and preferences rather than claiming a connection is active.

The workspace view puts the desktop or launch state in the main pane. Files and devices are supporting panes; transfer/activity drawers do not cover critical input-release controls. Host identity and local-versus-remote context remain visible around every file and device operation.

## Requirements

| ID | Capability | Required behavior | Release / stage |
| --- | --- | --- | --- |
| D01 | Hosts | Manual profiles first; optional capability discovery after pairing | R2 / P3-S1 |
| D02 | Workspace | Registered project, host/environment, desktop preferences, file roots, optional device preferences | R2 / P3-S1 |
| D03 | Stream launch | Validate installed client/host/profile, use documented mechanism if available, offer manual fallback | R2 / P3-S2 |
| D04 | Desktop input | Absolute/CAD/presentation/metered profiles; actual physical input qualification | R4 / P5-S2 |
| D05 | Files | Distinct Local, SFTP host, Drive-provider, and optional lab roots; locations and semantics visible | R2 / P3-S3 |
| D06 | Transfers | Queued/running/verifying/committed/failed/conflict states; pause/resume only when backend and source version allow | R2 / P3-S3 |
| D07 | USB | One actual consuming host; attaching/in-use/reenumerating/releasing/fault/unknown states | R2 / P3-S4 |
| D08 | Protection | User-visible programming protection; do not reassign or retry a flash automatically | R2 / P3-S4 |
| D09 | Activity | Own structured job/transfer/device events with source attribution and bounded retention | R3 / P4-S1 |
| D10 | Notifications | Per-adapter fidelity flags; system permission and channel settings; no loop or reconnect storm | R3 / P4-S2/S3 |
| D11 | File provider | Selected remote roots through Android document URIs, bounded cache, conflict-safe editing | R4 / P5-S3 |
| D12 | Metered policy | Explicit bitrate/traffic boundary, stop hidden streams, opt-in prefetch, preserve protected operation | R4 / P5-S4 |
| D13 | Console handoff | Pass resource identifiers; receiver resolves paths and authenticates independently | R2 / P3-S5 |

## Desktop transport

Use Sunshine/Moonlight as the first performance candidate, with exact versions and OS/compositor combinations recorded. An external client launch is the initial delivery boundary. Embedded native streaming is optional and requires a licensing, maintenance, input, rendering, and lifecycle decision before implementation. Review [Sunshine's documentation](https://docs.lizardbyte.dev/projects/sunshine/latest/) and the [Moonlight Android source/license](https://github.com/moonlight-stream/moonlight-android).

RDP, RustDesk, or other backends can become explicit adapters, not silent substitutes: session behavior, lock/login support, and graphics performance differ. Do not claim that a connected service can reach a locked, logged-out, headless, or pre-boot desktop without separate evidence.

## Files and data semantics

SFTP is the first direct host filesystem path. Drive starts through the system provider; direct Drive API access is a separate OAuth/scope decision. “Unified browser” means a consistent place to operate, not identical rename/atomicity/caching behavior.

Write to a temporary destination, validate size/hash as supported, check destination/source versions, and publish using the backend's available operation. Rename atomicity is not presumed across servers/filesystems. On conflict, retain both versions and ask the user to choose. A canceled operation reports whether a temporary remote object remains.

Remote editing uses local staging and a recorded source identity. Active repositories, databases, VM disks, toolchain caches, and model weights remain on the execution host. No destructive two-way sync in the first release. File-provider support uses [DocumentsProvider](https://developer.android.com/guide/topics/providers/create-document-provider); it does not create a universal POSIX mount.

## Devices

The Googlebook is the USB provider; the workstation is the consumer. P0 qualifies [VirtualHere's Android server](https://www.virtualhere.com/android) and the exact device identities. A UI lease timer cannot establish physical release. Reconcile with the backend before another host acquires the device.

Treat firmware resets and bootloader identities as an approved set attached to one operation, not as arbitrary new devices to auto-claim. On transport loss, show ownership unknown until reconciled. Keep the protected operation banner visible and do not trigger automatic reassignment.

## Acceptance

Pass Q-D01 through Q-D05 and Q-X01 through Q-X03 in [test cases](../06-validation/test-cases.md). The MVP proof is a real project edited/built through Console, opened visually, programmed/debugged through the actual probe, and inspected with measured network use. Report each fallback as part of the achieved workflow.
