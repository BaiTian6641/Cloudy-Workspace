# Optional host integration

**State:** host CLI **0.1.5** provides local diagnostics, explicit installation/context observation and read-only persisted event history in `core`; paired service and OS adapters remain planned. **Delivery stages:** P4. See the [construction entry](../docs/03-platform/host-core-entry.md) and [parallel client/host mandate](../docs/05-delivery/parallel-execution-contract.md).

## Responsibility

Separately versioned host core plus OS/user-session adapters for capabilities, pairing, managed jobs, events, and supported backend orchestration.

## Boundary

Ordinary Console SSH does not depend on this agent. Media/USB/bulk file data stay on direct backends.

## Read existing local event history

```text
cloudy-host events read --state-dir <existing-trusted-directory> --after <reset|stream-uuid:sequence> --limit <1..64>
```

The command validates syntax, reads the existing installation identity and opens an installation-bound read-only journal on Linux/Darwin. It never initializes or repairs state, writes the database or saves a cursor. It checks owner Close before successful output; missing/corrupt state and close/output failures are explicit. Historical records are not live process truth or remote execution authority. Windows reports unsupported before state/time access.

The [source and local-check result](../qualification/results/P4-S1/2026-10-16-host-events-read.md) records the exact command, 15 distinct affected methods across source 02/source 03, preserved initial vet failure and cross-built artifacts. The separate [actual Mac result](../qualification/results/P4-S1/2026-10-16-host-events-read-macos.md) passes ten native methods and eleven CLI cases, with a verified archive and exact temporary-root cleanup. Its positive persisted read runs through the real diagnostic entry; standalone CLI cases cover identity and refusal. No service or P4 support follows.

## First implementation work

Continue genuine identity/session observation, owned jobs and event persistence in bounded packets. Separate service, user-session helper, and any narrowly justified privileged helper. The local diagnostic supplies none of the remote-control/service authority by itself. The [retained Linux run owner](../docs/02-architecture/host-managed-run.md) now composes actual jobs/recorder/event storage as an internal library, with [reviewed composite pure-check evidence](../qualification/results/P4-S1/2026-10-16-host-managed-run.md). It retains uncertain jobs and separates process retirement from persistence; the subsequent [single native composition case](../qualification/results/P4-S1/2026-10-16-host-managed-run-native.md) passes with one real controlled child and confirmed cleanup. Service lifetime and authenticated remote admission remain open.

Read the [module map](../docs/02-architecture/module-boundaries.md), [phase plan](../docs/05-delivery/README.md), and [validation strategy](../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
