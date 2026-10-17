# Native macOS read-only event diagnostic — 16 October 2026

Host **0.1.5** passed the finite HOST-EVENTS-READ01 native execution and archive review on the user-designated **Mac Studio**, running **macOS 26.6.2, build 25G83, arm64**, with the pinned Go 1.27.2 binaries. The packet independently checked the actual OS version/build/architecture. **Separate archive acknowledgement and exact-owned cleanup also passed.** The cleanup receipt and an additional absence check confirm that the owned remote directory was removed.

## Native operation and results

The fresh source03 diagnostic binary passes **six top-level methods plus 43 named subcases: 49 aggregate passes**. The read-only store binary passes **four methods plus seven subcases: 11 aggregate passes**. Together these are **ten methods, 50 subcases and 60 aggregate named passes, with zero skips or failures**. They are non-race native results, distinct from the earlier Linux race evidence; the counts are not added together.

The positive diagnostic test invokes the real exported `diagnostic.Run` entry against an actual temporary store bound to an actual installation identity. It uses the actual Darwin boot reader and compares the complete known persisted Event, including identity and payload, through both reset and resumed output. Other selected cases cover canonical argument grammar, state refusal/noncreation, read-only file preservation, no writable metadata conversion, detached copies, mutation refusal, checked partial-owner closure, Close before successful output and fixed safe errors. Persisted job statuses are synthetic; reader/close/writer fault seams are controlled tests rather than physical OS faults or managed-job evidence.

Eleven real standalone CLI cases meet their exact expected results: version and identifier-free inspect; malformed events syntax; empty installation refusal; explicit identity initialization in three separate fresh owned roots; missing, incomplete and corrupt store refusal; and a final identity read. Syntax refusal returns 2, storage refusal returns 1 and successful identity/version/inspect operations return 0. Each refusal compares the complete applicable state snapshot before and after.

**A successful standalone journal read was not executed.** Its positive path was exercised through the native test's real `diagnostic.Run` call. The packet introduced no exporter, live-database tap or production fixture seam.

The two OS queries, two test processes and eleven CLI cases total **15 direct commands**. Each was reaped, expected exit codes were checked, bounded output did not overflow and the temporary test directory was empty. The runner had a 120-second overall command budget inside the parent's 150-second transport envelope. These limits define the finite operation, not a performance result.

## Exact archive and accepted cleanup

The parent reviewed the actual raw outputs and exact private archive: **41 regular files and ten directories**, including all five frozen packet inputs, terminal result, bounded logs and explicit identity/refusal fixtures. The machine-readable companion records the 41 file hashes without exposing identity values, private root metadata, accounts or connection details. The source03 diagnostic test binary was freshly compiled; the product/store binaries retain their unchanged source02 attribution.

The separately authorized archive acknowledgement and cleanup both exited 0. Cleanup rechecked the unchanged root identity and exact tree/file hashes before enumerated file unlink and empty-directory removal. Its bound raw output confirms **41 archived files, ten directories removed and the exact owned root absent**. A separate post-cleanup absence check also passed with exit code 0. No automatic retry occurred; removal was confined to the exact owned packet root.

Go normally removes test-created temporary databases. Thus the preserved archive contains the packet's remaining state and logs, not every database used inside the tests. Sync completion and preservation checks do not establish reboot or power-loss durability.

## Scope and evidence

This is a real local read-only diagnostic result. It does not establish a live job collector, macOS managed jobs, service, pairing, remote admission, wire delivery, Windows support or full P4/capability acceptance. The [source/local-check report](2026-10-16-host-events-read.md), [earlier native Darwin store result](2026-10-16-host-macos-events.md) and [P4 phase](../../../docs/05-delivery/phases/p4-host-integration.md) retain their separate boundaries and prerequisites.

Evidence references below are literal ignored/private paths; raw identifiers and transcripts are not published:

- `sourceFreeze`: `.cache/host-next-composition-01/implementation-01/freeze-03.json` — SHA-256 `b26c4248947b3a7ab802df661fd6e1e7223e65427447bfe3b928e06898526201`.
- `sourceAndLocalResult`: `qualification/results/P4-S1/2026-10-16-host-events-read.json` — SHA-256 `37524cb7964ac9a30ff20a4ac85d1c1090303069c04da2a37998d4c510f06e52`.
- `freshDiagnosticCompile`: `.cache/host-next-composition-01/native-mac-01/compile-01/result.json` — SHA-256 `7cad0750647ebd4e84e903d1cba47c2726bdedf5ec6095ca93c56532e30a556f`.
- `reusedProductAndStoreCompile`: `.cache/host-next-composition-01/implementation-01/verification-02/result.json` — SHA-256 `4032425778270e4f221af50c0e46130e37d7c4906df5bbe2f0c89cf749df3e5e`.
- `packetFreeze`: `.cache/host-next-composition-01/native-mac-01/freeze-01.json` — SHA-256 `b6c760d448fcd3926deb2605ae7e51eca7f24f89f8a1961bf6e54ce67d4e1ac8`.
- `packetEntryReview`: `.cache/host-next-composition-01/native-mac-01/review-01/review.json` — SHA-256 `2d0e4772fb8fc740a0191214efe532dbe97130134300bed3e2eeb97d6027db2d`.
- `nativeReceipt`: `qualification/results/private/host-macos/2026-10-16-eventsread-native-01/receipt.json` — SHA-256 `d8affdf4241f364258868f8bb949e4196e89beb2c632f616fc0a3840d6230a8e`.
- `nativeTerminal`: `qualification/results/private/host-macos/2026-10-16-eventsread-native-01/retrieve-terminal.stdout` — SHA-256 `ed64118b3efa342a605e19e994395e271d1e1019d230a7be63ffb5278e464c5c`.
- `parentArchiveReview`: `qualification/results/private/host-macos/2026-10-16-eventsread-native-01/parent-archive-review-01.json` — SHA-256 `fbc0b4c9197765c9279f02d4f6b38447d02077a2113e41c9546725fabdb53b9b`.

- `cleanupReceipt`: `qualification/results/private/host-macos/2026-10-16-eventsread-native-01/cleanup-receipt-01.json` — SHA-256 `f66281b2417ae264b6336124ec585edb5920a66cd29450a50b8a8173aa4b3cfb`.
- `cleanupOutput`: `qualification/results/private/host-macos/2026-10-16-eventsread-native-01/cleanup-runner-01.stdout` — SHA-256 `497c44ce1c1f71e8f76ce3fd0c4029f940291966874a9b307d6efa3a72537fc7`.
- `postCleanupAbsence`: `qualification/results/private/host-macos/2026-10-16-eventsread-native-01/post-cleanup-absence.json` — SHA-256 `a65dc259cc55ba87d7263b3981a5e036a4cab525166c485c51a6eda6c6f239ab`.

See the [machine-readable companion](2026-10-16-host-events-read-macos.json) for exact artifact hashes, test counts, CLI exit codes, archive hashes and the accepted cleanup and root-absence fields.
