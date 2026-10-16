# Host core

**State:** finite local runtime diagnostic implemented; production host core remains incomplete. **Delivery stages:** P4-S1 construction experiment HOST-EXP01, with stage prerequisites retained.

## Responsibility

Pairing/authenticated transport, scoped requests, registered resources, expiry/idempotency policy, capability manifests, job supervisor, bounded event retention/cursors/gaps, and redacted logs.

## Boundary

Do not implement OS capture/notification APIs directly in core or automatically replay shell/flash side effects.

## Local diagnostic

Go1.27.2, standard library only. From this directory, using the selected compiler:

```sh
GOTOOLCHAIN=local go run ./cmd/cloudy-host inspect
GOTOOLCHAIN=local go run ./cmd/cloudy-host version
gofmt -l .
GOTOOLCHAIN=local go vet ./...
GOTOOLCHAIN=local go test ./...
```

`inspect` prints actual runtime/target facts and UTC observation time. Unimplemented Cloudy control features report unavailable; external backends remain unknown because they were not inspected. This command does not create identities, inspect accounts/environment, open a listener, install a service or execute a job. It is not the production capability wire manifest. Require successful process exit and complete JSON; failed stdout can contain a partial record.

The [entry/toolchain record](../../docs/03-platform/host-core-entry.md) describes exact local archive verification and build commands. The [evidence](../../qualification/results/P4-S1/2026-10-16-host-baseline.md) distinguishes actual Linux CLI execution from Windows/macOS cross-builds. Remote transport, authenticated pairing, persisted identities/events, job ownership and user-session adapters remain implementation work. Do not treat this diagnostic as a supported paired host service.

Read the [module map](../../docs/02-architecture/module-boundaries.md), [phase plan](../../docs/05-delivery/README.md), and [validation strategy](../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
