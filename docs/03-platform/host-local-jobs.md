# Bounded Linux local jobs

**2026-10-16; HOST-JOBS01 construction profile, source and finite Linux runtime acceptance complete.** This is an internal local library under `host/core/internal/jobs`, developed alongside the Android clients. The existing host0.1.2 executable, identity format and diagnostic remain unchanged. There is no jobs CLI, remote-execution endpoint, pairing or installed service in this increment. P4-S1 prerequisites and integration exits remain required; ordinary SSH is independent of this optional host software.

## Caller authority and admitted operation

The caller supplies a trusted retained delegation-directory handle, lifetime context and immutable `Spec`. `Start` validates the specification and creates one fresh child in the delegated cgroup-v2 domain. It returns an explicit `Job` owner before asynchronous process launch. If preparation has already acquired resources and fails, the returned owner remains responsible for settlement even when accompanied by an error. Callers must retain a nonnil owner on every outcome.

The executable is an exact absolute existing regular file, with an explicit absolute existing working directory. There is no PATH lookup or implicit shell. Validation is not protection against replacement by a hostile same-user process; trusted invocation paths and membership ownership are preconditions. These input bounds are fixed for this initial library:

| Input | Limit and behavior |
| --- | --- |
| Arguments excluding argv0 | At most64;4096bytes each;32768bytes total; no NUL |
| Environment | Explicit nonnil list, at most64 entries/32768bytes; unique ASCII variable names; no NUL or ambient inheritance |
| Stdin | At most65536bytes, then closed |
| Stdout and stderr | Retain at most65536bytes each while continuing to drain; copied snapshots, saturating total counters and explicit truncation/saturation flags |
| Runtime | 1ms through30000ms, independently from observation |
| Settlement observation | At most5000ms per observation; kernel stalls remain owned uncertainty |

Admitted slices are copied before asynchronous use. There are no arbitrary caller I/O callbacks, PTY, disk output export, raw output logging or network side effects provided by the library. Returned bytes may contain secrets or terminal control sequences; only the explicit caller receives them. The trusted command itself remains capable of its normal OS-authorized effects.

## Exclusive child ownership

Duplicate the caller's retained directory descriptor with close-on-exec; validate actual cgroup-v2 filesystem and domain type. Create a cryptographically named exclusive child with handle-relative operations, retain its opened identity, and require its initial empty domain and required control access. The caller's original handle remains usable. The delegation root may contain the supervisor; it is never a kill target.

Use the pinned Go Linux `UseCgroupFD`/`CgroupFD` placement path to start in this child. Missing placement capability or permission refuses honestly, without an outside-cgroup fallback or start-then-migrate. Do not enable controllers, move existing processes, signal a guessed process group, or construct authority from `/proc/self/fd` paths. The selected upstream implementation uses `clone3` with `CLONE_INTO_CGROUP`; actual local placement requires native evidence. [Go1.27.2 Linux process implementation](https://github.com/golang/go/blob/go1.27.2/src/syscall/exec_linux.go)

The owned child's `cgroup.kill` and `cgroup.events` supply subtree termination and empty-membership observations. They do not establish direct-child reap or resource closure by themselves. Cloudy requires those separate facts before reporting retirement, then removes only its exact empty child and closes its duplicated parent handle. Failed queries, close, removal or uncertain start retain ownership. This is membership containment, not a security sandbox against same-user mutation or work delegated to an outside service. [Official kernel cgroup-v2 contract](https://docs.kernel.org/admin-guide/cgroup-v2.html)

## Lifetime, results and settlement

The first coordinator-committed stop reason wins: natural exit, cancellation, timeout or failed start. This is explicit ordering policy, not a claim about simultaneous wall-clock events. Actual start resolution, reap, exit status/signal and retirement are separate fields. A nonzero exit can be a successfully reaped command.

`Cancel` requests retirement once. `Wait(observerContext)` observes and never cancels or retries the job merely because its observer expires. After a stop, `Settle(observerContext)` can continue bounded observation/owned cleanup without launching again or repeating the kill request. One actual process Wait owns reap and the library-controlled I/O copies. A failed kill is retained uncertainty, not a new automatic kill attempt. An uncertain report requires the caller to retain the `Job` and arrange explicit recovery; dropping the Go value is not cleanup.

The library supplies nonblocking bounded collectors and a byte reader to established `os/exec` copying. `WaitDelay` assists pipe retirement after direct-child exit; separate job coordination handles cancellation and descendant termination. Pinned Go prioritizes a process `ExitError` over a copy error, so nonzero exit alone cannot establish complete output or observed I/O failure. Keep output-completeness assessment separate from actual exit and resource retirement. [Go1.27.2 os/exec](https://pkg.go.dev/os/exec@go1.27.2)

Errors expose fixed code/stage text and retain raw causes privately without `Unwrap`. No path, argument, environment, input or output is interpolated into general errors. Runtime and settlement budgets cannot promise prompt kernel termination. Supervisor crash/restart recovery is still unimplemented; the finite outer test service provides test safety only.

## Dependencies, platform scope and qualification

Reuse Go1.27.2 and exact `golang.org/x/sys v0.49.0`, now also using its maintained `unix` handle-relative operations. The version, authenticated sums and BSD-3-Clause notice remain those adopted for Windows identity; no new module or upgrade is selected. Windows/macOS can compile portable types/tests, but have no implemented jobs entry in this slice. Future Windows Job Object containment needs its own pre-execution assignment and handle policy; no macOS descendant backend is selected. Do not add fake operation stubs or certify those OSes from a cross-build.

A parent-owned finite delegated-service prerequisite probe has confirmed access and natural fixture retirement. That probe did not call this library. The [accepted result](../../qualification/results/P4-S1/2026-10-16-host-linux-jobs.md) binds reviewed source, seven compiler/vet/race/build checks,32 named pure passes and one exact-binary native fixture with seven passing cases: fresh placement, nonzero exit, pressure, cancellation/deadline, parent-first descendant exit/held pipes and missing-capability refusal. Pure validation and injected lifecycle tests qualify their specific boundaries only. Stop the native packet on its first failed or uncertain case and retain owners; do not continue to new jobs or relabel outer-service termination as library retirement.

Official records R325–R328 track the selected Go/kernel/x/sys contracts. No persistent event identity, remote idempotency, authenticated admission, service installation, package release or completed P4 stage follows. The [execution record](../05-delivery/execution-state.md) owns current accepted evidence and the next operation.
