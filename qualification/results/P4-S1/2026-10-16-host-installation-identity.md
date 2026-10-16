# HOST-IDENTITY local construction result

**2026-10-16; accepted scoped construction, P4-S1 integration remains planned.** Host core0.1.1 adds real explicit installation self-identification under the [identity contract](../../../docs/03-platform/host-installation-identity.md). The [source/artifact manifest](2026-10-16-host-installation-identity.json) binds the accepted source, checks, three binaries and review receipts. Googlebook clients continue independently; no device operation was part of this host increment.

`identity init --state-dir <existing-directory>` exclusively claims a permanent initialization directory, generates a cryptographic UUIDv4 and publishes a strict record without replacing an existing identity. `identity read` never creates or repairs state. Existing, missing-after-claim, corrupt and uncertain state cannot trigger silent regeneration. Installation ID is nonsecret self-identification, not authentication, hardware, OS boot or user logon identity. The parent directory must already be trusted and app-owned.

## Executed evidence

The accepted verification03 receipt retains25 checks. Go1.27.2 formatting/vet and **42 named Linux race-test/subtest passes** complete with zero named skips/failures. The executable entry package has no unit-test file; separate real process cases exercise it. Linux/amd64, Windows/amd64 and macOS/arm64 product binaries and installation-test binaries compile. Windows/macOS test binaries were not locally executed.

Actual Linux processes cover init/read, repeat-init refusal, missing/corrupt record preservation, missing-anchor read without writes, invalid arguments, eight simultaneous initializers with exactly one winner, and output failure through `/dev/full` after publication followed by stable read. Library tests additionally cover twelve concurrent goroutines, strict JSON/UUID/bounds checks and nine injected pre/post-publication failure boundaries. Injection is synthetic coverage, not a kernel-failure or power-loss test.

Only ignored owned temporary fixtures were used. No user state, service, listener, system setting, credential or installed backend was changed. Verification03 arguments identify its own `verification03/process-fixtures` directory; the receipt's generic footer mentions the earlier base fixture directory and is not the current fixture locator. The frozen receipt is preserved unchanged.

## Review and retained failures

Independent direct Codex GPT-6.1 Sol and parent review inspected the actual implementation and evidence. The first review found overly broad Windows test skips; the next review found that a separate successful classification could still hide a different actual initialization error. Both findings are repaired before acceptance. The actual claim-directory Sync cause is retained privately and classified precisely, while CLI error text remains fixed and safe. An unrelated actual EIO cannot be skipped because another probe encountered an access-right limitation.

The accepted gate requires the recognized error in both the separate real probe and the actual initializer, exact reached stage, claimed/unpublished state, empty retained anchor, repeat-init refusal and read failure. Symlink fixture skips are limited to the exact Windows privilege error. The revised hosted workflow prints individual cases/skips. Windows-specific positive classifier controls compiled locally; their native execution remains separate.

The first verification02 Windows test compile failed on an unavailable exported syscall constant. Its log is retained; the corrected fixture compiled separately. Earlier verification01/02 and both review findings remain historical. Verification03 rebuilds the changed production source; earlier binary hashes are not reused for this final implementation. The parent and reviewer each matched current source and final artifact hashes and inspected the actual42-pass raw output.

## External coworker results

The parent invoked the exact user-authorized specification/public-reference packets through normal approval review after two child attempts were rejected before execution. Actual OMP18.7.0 runs finished through `kimi-code/k3` and `zai/glm-5.3-flash`, with zero observed tools, normal stop and no fallback. No quota rotation was needed.

Kimi's code proposal is **rejected for adoption**: invalid placeholder syntax, other compile errors, unsafe error unwrapping, permissive version decoding and incomplete cleanup/validation. Flash's bounded failure cases are useful with corrections: failed-attempt temporary-file cleanup is not mandatory under this preserve-state contract, synthetic entropy failure is not OS evidence, and existing same-user/FIFO limits are not new discoveries. No generated model implementation was integrated. The direct Sol implementation and independent review determine acceptance.

## Remaining platform and product limits

Required directory Sync through the current read-only handle can prevent Windows initialization after claiming its anchor. This deliberate incomplete-state behavior is not a Windows installation-identity success. A reviewed platform durability/recovery adapter is future work; no reset or retry workaround is introduced. macOS native filesystem behavior, Windows ACL privacy, hostile same-user mutation and power-loss durability remain unqualified. A successful read establishes present contents only.

Pairing, remote authorization, jobs/events, OS boot/logon identities, installers/services and interactive adapters remain unimplemented. No P4 dependency, capability or release gate is promoted. Official references R309–R313 and the exact API/fallback mapping are recorded in the identity contract. The prior0.1.0 three-OS hosted CLI result remains valid for that earlier source only; this increment needs its own hosted result.
