# Explicit local host context observation

**2026-10-16; HOST-CONTEXT01 accepted in its source and finite Linux runtime scope.** Host CLI **0.1.3** adds one explicit command:

```text
cloudy-host context read --state-dir <existing-app-owned-directory>
```

Linux calls the existing [installation reader](host-installation-identity.md) and [boot reader](host-boot-identity.md), in that order. The supplied directory must already contain valid installation state. This command never initializes, repairs, replaces or generates an identity. Missing, partial or corrupt state returns a fixed safe installation-read failure without JSON. Boot-read failure likewise supplies no successful record. The actual installation reader's `Result.Durability` passes through unchanged; current Read reports `not-assessed`.

This is a bounded local diagnostic prerequisite under [P4-S1](../05-delivery/phases/p4-host-integration.md), not a listener, helper protocol, service or authenticated capability manifest. Source ownership is confined to six diagnostic files. The accepted installation, boot, peer and jobs readers/backends, module versions, checksums and protocol schemas are unchanged. P1-S4/P3-S1 prerequisites and P4 stage status remain unchanged.

## Output and caller contract

| Field | Meaning |
| --- | --- |
| `localContextVersion` | Local output profile 1, separate from the wire protocol |
| `agentVersion` | Actual executable version, 0.1.3 |
| `observedAt` | UTC observation timestamp sampled after both reads; not boot time |
| `runtime` | Actual running binary OS, architecture and Go version |
| `installationId` | Existing installation self-ID, not authentication |
| `bootId` | UUID exposed through the caller's trusted Linux procfs view |
| `identityDurability` | Unchanged result from the installation reader; no reassessment |
| `observationConsistency` | `sequential-not-atomic` |

The caller explicitly requests correlatable nonsecret IDs. Keep output within its intended local diagnostic context; the command does not forward it to a network peer. Default `inspect` remains identifier-free and continues reporting unimplemented control features unavailable and uninspected external backends unknown. No session ID, authentication/paired state, remote-job authority or readiness is inferred.

The caller must trust the existing app-owned state directory and procfs mount view. Sequential observations do not establish an atomic identity tuple or protect against hostile same-user mutation. Boot parsing's byte limit bounds data, not the duration of I/O on a hostile replacement mount. No reboot transition, container-independent/global host identity or login/session correlation is qualified.

Bad syntax returns exit2 before state access. Linux reader errors return exit1 with fixed stage-specific text, never raw OS errors, paths or IDs. Output uses the existing checked writer: writer errors or short writes return exit1; stdout may contain a partial record, so consumers require successful exit and complete JSON. Reads/output failures perform no reset, rollback, retry or cleanup mutation. On Windows/Darwin, valid command syntax returns an explicit fixed unsupported result before reading state or sampling runtime/time. This is truthful boundary behavior, not a boot backend or native platform qualification.

## Evidence and limits

The [result](../../qualification/results/P4-S1/2026-10-16-host-context.md) and [manifest](../../qualification/results/P4-S1/2026-10-16-host-context.json) bind exact source, raw checks, five product/test artifacts and independent review. Eleven checks pass with Go1.27.2: compiler identity, formatting, changed diagnostic package Linux vet/race, three product builds and Windows/Darwin diagnostic vet/test cross-compilation. Linux 7.0.0-38-generic / x86_64 executes **14 top-level tests / 25 aggregate named passes including subtests, zero skips or failures**. Reader failure/order/durability injection is composition evidence, not actual kernel fault injection.

Ten actual Linux CLI commands, each limited to five seconds, verify version/identifier-free inspect, empty-state and bad-argument refusal, new owned fixture initialization, two matching context reads, actual `/dev/full` output failure, incomplete-anchor refusal and corrupt-record refusal. Read and failure paths preserve the owned installation tree bytes/modes. ID-bearing stdout remains in ignored private captures, and no observed ID is printed in tests or published here. Windows/Darwin products and test executables only cross-compiled. No other-OS native run or new hosted CI pass is claimed. No unrelated jobs/peer/boot/installation suite was repeated.

Parent and independent review accepted the bounded source/retained Linux runtime with no actionable finding. No new dependency, OMP request, listener, job, service, ADB operation or existing user-state mutation occurred. Pairing, remote admission, logon/session identity, event persistence and full P4 integration remain open. [Go JSON encoder](https://pkg.go.dev/encoding/json@go1.27.2#Encoder.Encode), [Go io.Writer contract](https://pkg.go.dev/io@go1.27.2#Writer), [Go UTC formatting](https://pkg.go.dev/time@go1.27.2#Time.UTC).

Official output/writer/time references are tracked as R353–R355, checked16 October2026 against the selected Go1.27.2 documentation. Existing kernel/installation reference decisions remain unchanged.
