# HOST-LINUX-BOOT01 — real Linux boot UUID observation

**2026-10-16 — source and retained finite Linux runtime accepted after parent and independent review.** This internal Linux-only increment supplies `bootidentity.Read() (ID, error)` and immutable canonical `ID.String()`, with a fixed trusted-procfs observation boundary. The [contract](../../../docs/03-platform/host-boot-identity.md) describes operation, fixed errors and limits; the [manifest](2026-10-16-host-linux-boot.json) binds source, modules, raw verification, pinned official references and review status. Parent source read found no blocker. Independent review matched both sources, snapshots, modules, freeze/patch/reference metadata and all eight raw output hashes; it parsed six top-level / 27 aggregate passes with zero skips/failures and found no actionable finding. The reviewer ran no tests again. This is not P4-S1 completion or service/capability/event integration.

Every call opens the actual `/proc/sys/kernel/random/boot_id`, consumes at most 38 bytes, requires canonical lowercase UUIDv4/RFC variant plus LF, and closes once before success. Open/read/close failure gives no ID, with fixed non-unwrapped public errors. No identifier is generated, cached, substituted or printed by tests. No dependency, executable version, CLI, schema or other-OS stub changed.

Four checks pass using selected Go1.27.2: compiler identity, gofmt, changed-package vet and race-test execution (`-count=1`, `-timeout=15s`). On actual Linux 7.0.0-38-generic / x86_64, **6 top-level methods and 27 aggregate named passes including subtests,0 skips or failures** cover:

- Two real fixed-path reads, both valid and equal, without exposing the observed value.
- Canonical synthetic variants, zero-value behavior and input-copy immutability.
- Fifteen malformed synthetic record controls.
- Injected read/close/combined failure and malformed-input owner closure.
- Oversize input rejection after exactly38 consumed bytes.
- Open errors, partially returned owner closure and nil-owner refusal.

Synthetic failure cases are not actual kernel/close fault injection. Both source and module hashes stayed unchanged during verification. No unrelated jobs/peer tests, hosted run, reboot, namespace/mount/account change, process job or listener was performed. The caller trusts its procfs view; the byte bound is not a hostile-mount I/O deadline. Equality in one finite run does not qualify reboot change or global host/session/installation identity.

Pinned Linux v6.18 source/documentation at commit `7d0a66e4bb9081d75c82ec4957c50034cb0ea449` establishes the inspected `boot_id`/UUIDv4/newline semantics; the actual host kernel identity above is separate. Full source URLs, content hashes and checked date are in the manifest. [Kernel documentation](https://github.com/torvalds/linux/blob/7d0a66e4bb9081d75c82ec4957c50034cb0ea449/Documentation/admin-guide/sysctl/kernel.rst#random).

## Separate macOS design research

One context-only public-spec/reference Kimi request completed in 86.087 seconds, exit 0 / normal stop, with actual provider `kimi-code`, model `k3`, zero observed tool calls and no alternate route. Payload hashes remained fixed. Only newly authored public summaries plus the actual skill preamble were supplied; no project code/private evidence was sent. The final-only response and direct coordinator correction are separately bound in the manifest. No model statement is native runtime evidence or implementation acceptance.

The scoped outcome is no demonstrated maintained public macOS route meeting current arbitrary-descendant containment/retirement. The new Apple descendant ES API's recursive auth/notify scope does not itself document kill-all/exact-empty semantics; its macOS 27.0 metadata, beta/restricted-entitlement limits and explicit no-root/no-TCC behavior remain distinct. Queue-marker callback completion does not establish empty ownership, and uncertain cleanup must retain the owner. No native macOS operation or Darwin job implementation occurred; no global impossibility or platform adoption is claimed. Parent accepted this corrected bounded research result separately from this Linux source review.
