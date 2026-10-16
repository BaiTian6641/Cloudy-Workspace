# HOST-EXP01 local diagnostic and portable builds

**2026-10-16, bounded PASS.** Source lives in `host/core`; base branch checkpoint before this increment is03b050e. The parent inspected direct Codex GPT-6.1 Sol source and an independent Sol review, then performed the checks below. [Construction scope](../../../docs/03-platform/host-core-entry.md) and [parallel execution](../../../docs/05-delivery/parallel-execution-contract.md) retain P4 integration gates.

Go1.27.2's official linux/amd64 archive was downloaded to ignored workspace cache and its70,590,635-byte size and SHA256 verified against official metadata. No global installation or dependency download occurred. The module uses only the standard library; validation set local toolchain, disabled Go environment-file overrides, disabled proxy and telemetry, and used task-owned caches.

| Check | Actual result |
| --- | --- |
| `gofmt -l .` | No files reported |
| `go vet ./...` | Exit0 |
| `go test -race -json ./...` | Six top-level tests plus nine subtests pass; no failure |
| `go build -mod=readonly -trimpath -buildvcs=false`, CGO disabled | linux/amd64, windows/amd64, darwin/arm64 compile |
| Linux executable `inspect` | Exit0, valid exact local profile, actual linux/amd64/go1.27.2 and UTC time |
| Linux executable `version` | Exit0,0.1.0 |
| Linux executable `inspect unexpected` | Exit2, fixed usage, no stdout |

Tests use controlled clock/runtime/writer fixtures for contract, privacy and failure paths. The separate actual executable check validates the real production runtime route. Neither test type observes session identity, external backends or permissions. No network connection, listener, host service, credential operation, device operation or Googlebook modification was part of this run.

| Binary | SHA256 | Local execution |
| --- | --- | --- |
| linux/amd64 | `62f5bc2fca18924f890934561130b923b4ad12e4472f7274328f8946c1255d19` | inspect/version/invalid arguments |
| windows/amd64 | `e35cf39aace1e3b0652f0bfcd4de61198a81ae893b1515d392907c8dd1c7a734` | Not run |
| darwin/arm64 | `2f556f0b840f4b873b46646748364f50d7a083f5d8737c8c7bc55743325afb17` | Not run |

The [machine-readable result](2026-10-16-host-baseline.json) binds source hashes, actual checks and artifact hashes. Local logs reside in ignored `.cache/host-01/build-01/`; source review is `.cache/host-01/code-review.md`. A new three-OS hosted workflow is configured separately; creation alone is not a hosted pass. Hosted outcomes are recorded against exact commits when available.

This result qualifies only finite local construction. No P4 stage or remote capability is promoted. Remaining work includes authentic installation/boot/session identities, pairing/scopes/revocation, registered roots, job/process-tree ownership, persisted event replay/gaps, actual OS adapters, installers, client integration and sustained use.
