# Darwin local job-event storage qualification — 2026-10-16

The reviewed event-store port passed one finite native run on **macOS26.6.2, build25G83, arm64**. The selected binary uses the pinned Go1.27.2 toolchain with unchanged x/sys0.49.0 and bbolt1.5.0. Independent evidence review accepts the scoped result; source integration and publication remain parent-owned.

The change admits the existing Linux store, record, cursor and shared tests on Darwin through build tags and filenames. Their bodies and storage schema remain unchanged. Two Darwin cases add reopen/cursor/refusal preservation and real separate-open writer-lock contention. The Linux process fixture is unchanged and excluded from the Darwin binary.

## Executed evidence

Five affected local compiler commands passed: compiler identity, formatting, Linux vet, Darwin vet and Darwin test cross-compilation. All frozen source/protected inputs remained unchanged. That compiler recipe ran no tests.

The actual Mac then passed **five top-level methods plus five refusal subcases: ten aggregate named passes, zero skips and zero failures**, with exact terminal PASS, exit0 and empty stderr. The three direct commands (two OS queries and one selected test binary) exited0, were reaped, and produced no output overflow. This binary has no race instrumentation.

The tests exercise real temporary filesystem/engine operations: Init/Append/Close/Open, exact retry, continued cursor, wrong-installation byte preservation, writer-lock timeout, missing/empty/oversized/symlink/FIFO database refusal, read-only metadata preflight, permanent claim and corruption refusal. The actual Darwin boot reader supplies boot observations. Job statuses are synthetic storage inputs; commit/close uncertainty hooks are controlled glue evidence, not actual disk-fault or managed-job proof.

## Archive and retirement

All **eleven exact files**, including the terminal result and selected binary, were retrieved and verified before exclusive archive acknowledgement. Three separate archive/acknowledgement/cleanup commands exited0. Cleanup used the original root identity, exact file sets/hashes and an empty temporary directory, removed only enumerated files and empty directories, and confirmed the exact owned root absent. No recursive removal or automatic retry occurred. Connection/account details, remote roots and identifier metadata remain private.

Go normally removes its temporary test fixture directories. Preservation assertions run inside the tests; this report does not claim every fixture database was retained after execution.

## Limits

This qualifies scoped local Darwin storage operations. It does not qualify macOS managed jobs, a CLI/recorder/service, pairing, authenticated remote admission, wire delivery or P4 support. Sync completion does not establish reboot or power-loss durability. Existing Linux evidence retains its original attribution. No new hostedCI result or product capability is inferred.

## Evidence bindings

- `nativeReceipt`: `qualification/results/private/host-macos/2026-10-16-events-native-01/receipt.json` — SHA-256 `994cd39f72420b9003ec8f2157a579fe7592a514d5c4a03009c878bbb2ad88c0`.
- `nativeTerminal`: `qualification/results/private/host-macos/2026-10-16-events-native-01/retrieve-terminal.stdout` — SHA-256 `8cc2095d55bf1164d552175ef3be45e95a6d37a38a1df1013cba806d97a1b560`.
- `archiveCleanupReceipt`: `qualification/results/private/host-macos/2026-10-16-events-native-01/archive-cleanup-receipt-01.json` — SHA-256 `c3f399ad87ae98aa2e69ac2be61c6cb6781409279886b6e5dfcaf8fa95c5abe8`.
- `sourceFreeze`: `.cache/host-darwin-events-01/source-01/freeze-01.json` — SHA-256 `f9757aca5da66ecb94a24bc390ff8af201fba0138430504511adc0f445e9ae91`.
- `compiler`: `.cache/host-darwin-events-01/source-01/verification-01/result.json` — SHA-256 `cd1daab20e536e4ec728618063feb367590800d503767482fb1c037de259a7d8`.
- `nativePacketFreeze`: `.cache/host-darwin-events-01/native-entry-01/freeze-01.json` — SHA-256 `a317bd41340595f9bcd07f62d9dfdfc8bc6495b64baca882f61b3a44f882658f`.
- `actualSummary`: `.cache/host-darwin-events-01/native-entry-01/actual-summary-01.json` — SHA-256 `8d88e18f3ccae4e5dcc4a3e39223588040b2079e42ed49cacd8886a78d78c1ca`.
- `independentActualReview`: `.cache/host-darwin-events-01/publication-proposal01/independent-actual-review01.json` — SHA-256 `661e276e15ba451d12691a6bda1cab03e62ede1b801942333c15fa85c70f1197`.

See `2026-10-16-host-macos-events.json` for exact source, artifact, archive and count bindings. Ignored/private evidence paths are literal references; their contents are not published.
