# Explicit local host context observation

**2026-10-16; original Linux HOST-CONTEXT01 and subsequent Darwin context composition accepted within their recorded scopes.** Current host CLI **0.1.4** provides one explicit command:

```text
cloudy-host context read --state-dir <existing-app-owned-directory>
```

Linux and Darwin call the existing [installation reader](host-installation-identity.md) and their actual [boot reader](host-boot-identity.md), in that order. Darwin uses the separately qualified fixed kernel-node reader; a missing or restricted query returns a real error without substitute identity. The supplied directory must already contain valid installation state. This command never initializes, repairs, replaces or generates an identity. Missing, partial or corrupt state returns a fixed safe installation-read failure without JSON. Boot-read failure likewise supplies no successful record. The actual installation reader's `Result.Durability` passes through unchanged; current Read reports `not-assessed`.

This is a bounded local diagnostic prerequisite under [P4-S1](../05-delivery/phases/p4-host-integration.md), not a listener, helper protocol, service or authenticated capability manifest. The0.1.4 change shares the existing composition through Linux/Darwin build tags, updates exact version expectations and adds corrupt-state refusal coverage. The accepted installation, boot, peer and jobs readers/backends, module versions, checksums and protocol schemas are unchanged. P1-S4/P3-S1 prerequisites and P4 stage status remain unchanged.

## Output and caller contract

| Field | Meaning |
| --- | --- |
| `localContextVersion` | Local output profile 1, separate from the wire protocol |
| `agentVersion` | Actual executable version, currently0.1.4 |
| `observedAt` | UTC observation timestamp sampled after both reads; not boot time |
| `runtime` | Actual running binary OS, architecture and Go version |
| `installationId` | Existing installation self-ID, not authentication |
| `bootId` | UUID from the trusted platform boot source: Linux procfs or Darwin's fixed `kern.bootsessionuuid` query |
| `identityDurability` | Unchanged result from the installation reader; no reassessment |
| `observationConsistency` | `sequential-not-atomic` |

The caller explicitly requests correlatable nonsecret IDs. Keep output within its intended local diagnostic context; the command does not forward it to a network peer. Default `inspect` remains identifier-free and continues reporting unimplemented control features unavailable and uninspected external backends unknown. No session ID, authentication/paired state, remote-job authority or readiness is inferred.

The caller must trust the existing app-owned state directory and platform boot source: the procfs mount view on Linux or the reviewed fixed kernel node on Darwin. Sequential observations do not establish an atomic identity tuple or protect against hostile same-user mutation. Boot parsing's byte limit bounds data, not the duration of I/O on a hostile replacement mount. No reboot transition, container-independent/global host identity or login/session correlation is qualified.

Bad syntax returns exit2 before state access. Linux/Darwin reader errors return exit1 with fixed stage-specific text, never raw OS errors, paths or IDs. Output uses the existing checked writer: writer errors or short writes return exit1; stdout may contain a partial record, so consumers require successful exit and complete JSON. Reads/output failures perform no reset, rollback, retry or cleanup mutation. On Windows and other unsupported targets, valid command syntax returns an explicit fixed unsupported result before reading state or sampling runtime/time. The Darwin0.1.3 refusal remains historical;0.1.4 now uses the same real sequential composition as Linux.

## Evidence and limits

The historical0.1.3 [result](../../qualification/results/P4-S1/2026-10-16-host-context.md) and [manifest](../../qualification/results/P4-S1/2026-10-16-host-context.json) bind exact source, raw checks, five product/test artifacts and independent review. Eleven checks pass with Go1.27.2: compiler identity, formatting, changed diagnostic package Linux vet/race, three product builds and Windows/Darwin diagnostic vet/test cross-compilation. Linux 7.0.0-38-generic / x86_64 executes **14 top-level tests / 25 aggregate named passes including subtests, zero skips or failures**. Reader failure/order/durability injection is composition evidence, not actual kernel fault injection.

Ten actual Linux CLI commands, each limited to five seconds, verify version/identifier-free inspect, empty-state and bad-argument refusal, new owned fixture initialization, two matching context reads, actual `/dev/full` output failure, incomplete-anchor refusal and corrupt-record refusal. Read and failure paths preserve the owned installation tree bytes/modes. ID-bearing stdout remains in ignored private captures, and no observed ID is printed in tests or published here. Windows/Darwin products and test executables only cross-compiled at that0.1.3 checkpoint. Its result makes no other-OS native or new hosted CI claim. No unrelated jobs/peer/boot/installation suite was repeated.

Parent and independent review accepted the bounded source/retained Linux runtime with no actionable finding. No new dependency, OMP request, listener, job, service, ADB operation or existing user-state mutation occurred. Pairing, remote admission, logon/session identity and full P4 integration remain open; separately qualified local Linux event storage does not supply a remote event service. [Go JSON encoder](https://pkg.go.dev/encoding/json@go1.27.2#Encoder.Encode), [Go io.Writer contract](https://pkg.go.dev/io@go1.27.2#Writer), [Go UTC formatting](https://pkg.go.dev/time@go1.27.2#Time.UTC).

Official output/writer/time references are tracked as R353–R355, checked16 October2026 against the selected Go1.27.2 documentation. Existing kernel/installation reference decisions remain unchanged.


## Darwin composition acceptance (0.1.4)

The [separate0.1.4 result](../../qualification/results/P4-S1/2026-10-16-host-macos-context.md) and [manifest](../../qualification/results/P4-S1/2026-10-16-host-macos-context.json) bind the current source/compiler/native evidence. Nine affected compiler commands pass, including five Linux race methods, Darwin vet/test/product compilation and Windows vet/test cross-compilation. The existing production composition body is unchanged; no dependency or reader implementation is modified.

Actual Mac26.6.2/build25G83/arm64 executes nine selected top-level tests plus two subcases, zero failure/skip, and nine CLI cases. They cover version/inspect, invalid/empty/incomplete/corrupt refusal, explicit owned fixture initialization and two real context reads with equal installation/boot IDs. These reads are sequential and do not establish reboot persistence or authentication. All32 raw files/eight directories are archived and verified before exclusive acknowledgement and exact enumerated cleanup; independent review accepts54 evidence checks without repeating the operation. No observed identifiers or private machine connection details are published.

Rejected preparation recipes remain preserved and unrun. The actual packet uses Python3.9-compatible strict UTC parsing and post-exit output bounds; those supervisor corrections do not change host output. Runtime qualification is scoped to this sampled Mac. macOS managed jobs/event storage, paired service, durable recovery and complete P4 acceptance remain open.
