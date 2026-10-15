# Cloudy Desktop product specification

**Release:** R2 companion with native RDP/VNC, R3 host integration, R4 native high-performance streaming. **Primary job:** operate a selected host's visual workspace and nearby hardware with clear context and ownership. **Target:** Googlebook desktop/laptop Android app under [Rule 0](../00-project/platform-rule-zero.md); no phone/tablet optimization workstream.

## Information architecture

Persistent destinations: Workspaces, Hosts, Files, Devices, Activity, Settings. A selected workspace includes host/environment, approved project roots, preferred desktop backend/profile, and optional device bindings. It stores intent and preferences rather than claiming a connection is active.

The workspace view puts the desktop or launch state in the main pane. Files and devices are supporting panes; transfer/activity drawers do not cover critical input-release controls. Host identity and local-versus-remote context remain visible around every file and device operation.

## Requirements

| ID | Capability | Required behavior | Release / stage |
| --- | --- | --- | --- |
| D01 | Hosts | Manual profiles first; optional capability discovery after pairing | R2 / P3-S1 |
| D02 | Workspace | Registered project, host/environment, desktop preferences, file roots, optional device preferences | R2 / P3-S1 |
| D03 | Stream launch | Validate installed client/host/profile, use documented mechanism if available, offer manual fallback | R2 / P3-S2 |
| D04 | Streaming input profiles | Absolute/CAD/presentation/metered profiles; native RDP/VNC input remains required in R2 | R4 / P5-S2 |
| D05 | Files | Distinct Local, SFTP host, Drive-provider, and optional lab roots; locations and semantics visible | R2 / P3-S3 |
| D06 | Transfers | Queued/running/verifying/committed/failed/conflict states; pause/resume only when backend and source version allow | R2 / P3-S3 |
| D07 | USB | One actual consuming host; attaching/in-use/reenumerating/releasing/fault/unknown states | R2 / P3-S4 |
| D08 | Protection | User-visible programming protection; do not reassign or retry a flash automatically | R2 / P3-S4 |
| D09 | Activity | Own structured job/transfer/device events with source attribution and bounded retention | R3 / P4-S1 |
| D10 | Notifications | Per-adapter fidelity flags; system permission and channel settings; no loop or reconnect storm | R3 / P4-S2/S3 |
| D11 | File provider | Selected remote roots through Android document URIs, bounded cache, conflict-safe editing | R4 / P5-S3 |
| D12 | Metered policy | Explicit bitrate/traffic boundary, stop hidden streams, opt-in prefetch, preserve protected operation | R4 / P5-S4 |
| D13 | Console handoff | Pass resource identifiers; receiver resolves paths and authenticates independently | Suite milestone / P3-S9; no standalone R2 release dependency |
| D14 | Native RDP client | Explicit RDP profile, server trust/auth, native render/input/IME, resize/scaling, session recovery and observed transport | R2 / P3-S7 |
| D15 | Native VNC client | RFB framebuffer/input, negotiated security/encoding, native surface, supported resize/clipboard and bounded recovery | R2 / P3-S8 |
| D16 | USB plug-and-play | Enroll device+approved host, then discover/replug/auto-attach when permission and idle ownership are confirmed | R2 / P3-S6 |
| D17 | Low-latency stable streaming | Measured input-to-photon/pacing/quality and impaired-link recovery compared with qualified native baselines | R4 / P0-S4/S6, P5-S2/S4 |
| D18 | Backend transparency | Show protocol/session identity, capabilities and actual route/transport when observable; no silent protocol switch | R2 / P3-S1/S7/S8 |

## Desktop transport

Use Sunshine/Moonlight as the first performance candidate, with exact versions and OS/compositor combinations recorded. External stream launch is the early delivery boundary; native integrated high-performance streaming is the later target with licensing, maintenance, input, rendering and lifecycle gates. Review [Sunshine's documentation](https://docs.lizardbyte.dev/projects/sunshine/latest/) and the [Moonlight Android source/license](https://github.com/moonlight-stream/moonlight-android). An external-only decision requires an explicit scope revision.

RDP and VNC are first-class native client lanes with dedicated P3 stages, ordinary server prerequisites and no custom host-agent dependency. FreeRDP and a maintained RFB engine are candidates, not selected/qualified implementations. Session behavior, security, lock/login support and graphics performance differ. Parsec/GeForce NOW are performance references; no SDK or embedding right is assumed. Do not claim a connected service reaches locked, logged-out, headless or pre-boot desktops without separate evidence. See the [backend design](../02-architecture/remote-desktop-backends.md) and [performance procedure](../06-validation/desktop-performance.md).

## Files and data semantics

SFTP is the first direct host filesystem path. Drive starts through the system provider; direct Drive API access is a separate OAuth/scope decision. “Unified browser” means a consistent place to operate, not identical rename/atomicity/caching behavior.

Write to a temporary destination, validate size/hash as supported, check destination/source versions, and publish using the backend's available operation. Rename atomicity is not presumed across servers/filesystems. On conflict, retain both versions and ask the user to choose. A canceled operation reports whether a temporary remote object remains.

On backends without guaranteed conditional replacement, ask before every remote overwrite, offering save-copy/cancel with no blanket future consent. A later writer can race a prior check; apply the exact generation, ambiguous-completion and exclusive-copy rules in the [publication contract](../02-architecture/file-publication.md). Local staging survives cancellation and uncertainty.

Remote editing uses local staging and a recorded source identity. Active repositories, databases, VM disks, toolchain caches, and model weights remain on the execution host. No destructive two-way sync in the first release. File-provider support uses [DocumentsProvider](https://developer.android.com/guide/topics/providers/create-document-provider); it does not create a universal POSIX mount.

## Devices

The Googlebook is the USB provider; the workstation is the consumer. P0 qualifies [VirtualHere's Android server](https://www.virtualhere.com/android) and the exact device identities. After enrollment, recognized idle devices attach to their approved host automatically where platform permissions permit. First-use grants, driver/setup and unavailable backend states stay explicit. A UI lease timer cannot establish physical release. Reconcile with the backend before another host acquires the device. The [plug-and-play design](../02-architecture/usb-plug-and-play.md) covers policy, toggles, replug and protection.

Treat firmware resets and bootloader identities as an approved set attached to one operation, not as arbitrary new devices to auto-claim. On transport loss, show ownership unknown until reconciled. Keep the protected operation banner visible and do not trigger automatic reassignment.

## Acceptance

The independent R2 gate is P3-S5: qualify native RDP/VNC, the external performance-stream lane, files and enrolled USB through the primary edit/build/program/inspect workflow with Console absent. Apply Q-D01/D02/D03/D04/D06/D07/D08, the relevant network/file cases, and Q-R02's app-scoped release checks. The later P3-S9 suite milestone adds Console build/upload and cross-app handoff. Q-D05 belongs to the later document provider; host event replay belongs to P4. See [release gates](../05-delivery/release-gates.md) and [test cases](../06-validation/test-cases.md).

Native streaming adoption is decided early at P1-S5 from P0 evidence. P5-S2 can proceed once its native app/profile and engine prerequisites pass, independently of P4 host adapters; the first R2 scope still permits a qualified external performance-stream handoff.
