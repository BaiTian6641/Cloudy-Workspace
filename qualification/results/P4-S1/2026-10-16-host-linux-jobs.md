# Linux local-job construction result

**2026-10-16; bounded HOST-JOBS01 source and native fixture accepted.** The [internal jobs profile](../../../docs/03-platform/host-local-jobs.md) now performs real local process execution in an exclusively owned child cgroup, with bounded input/output, explicit job ownership, cancellation and checked retirement. The existing host0.1.2 CLI/identity implementation is unchanged. No remote-execution endpoint, pairing, service installation, Windows/macOS jobs support or P4-S1 completion follows.

## Source and compiler evidence

Final verification02 binds five authored files and three compiled test artifacts. Seven checks pass: gofmt, exact module integrity, Linux vet, focused Linux race tests, and Linux/Windows/macOS test compilation. The selected compiler is Go1.27.2; exact x/sys v0.49.0 now supplies reviewed Unix wrappers as well as the prior Windows identity wrappers. At verification02, all16 prior inputs were byte-identical to the identity checkpoint. The subsequent host README documentation update is separate; the existing code, module and notice inputs remain unchanged.

The Linux race output records32 named test/subtest passes, zero named skips and zero failures. These cover admission bounds/copies, output limits/saturation/concurrency, safe errors, non-cgroup refusal and injected lifecycle/uncertainty controls. They are not32 executable jobs. The native fixture is explicitly opt-in; ordinary portable test/cross-build results do not certify cgroup or other-OS behavior.

Parent and independent source review identified two repairs before native execution. The test harness now aborts its whole packet after any failed subtest and retains every admitted Job through its lifetime. `IOCompletion` now distinguishes complete, not-assessed, incomplete and unknown: selected Go can mask copy errors behind nonzero ExitError, so exit status alone cannot certify output completeness. A focused synthetic classifier test covers that rule. Verification01 and review01 remain preserved; final02 closes both findings without a pipe implementation rewrite.

## Actual Linux fixture

The parent executed the exact frozen Linux test binary once on Ubuntu26.04/resolute, kernel7.0.0-38-generic, x86_64. The [manifest](2026-10-16-host-linux-jobs.json) binds source/compiler/binary, launcher, raw private output, bootstrap/control receipts and reviews. The finite user service was freshly named and verified absent, then its actual identity/group/PID was verified before releasing the private execution gate. It used explicit runtime/stop bounds and only its own delegation. No existing cgroup membership, service or system setting was changed.

| Actual subtest | Accepted evidence and boundary |
| --- | --- |
| Missing capability | Ordinary directory refused before process launch; acquired descriptor owner settles. |
| Exit0 | Child observes its actual creation-time owned-group membership; exit/reap and retirement confirmed. |
| Exit3 | Actual nonzero status preserved independently of successful reap/retirement. |
| Pressure |65536bytes stdin consumed; stdout/stderr exceed retained caps, continue draining and report truncation/counters. |
| Descendant | Real grandchild starts and holds inherited output pipes after the direct parent exits; owned subtree termination, empty membership and resource cleanup complete. |
| Deadline | Actual child is retired after the finite runtime trigger; committed outcome is timed-out. |
| Cancel | Observer timeout leaves the job pending; explicit cancellation then retires it. |

All seven subtests and their parent test pass, with zero skips/failures, empty native stderr and process exit0. Six library jobs launch; the missing-capability case launches none. The descendant role additionally creates its real grandchild. The library's source-bound assertions require actual child reap, container retirement and an intact caller delegation handle. Outer controls confirm the service and its cgroup disappear naturally. There is one service launch, no stop/reset/collect command and no cleanup uncertainty.

The independently reviewed launcher pins the accepted verification receipt and binary, checks its own unit membership before testing, and preserves failures. Initial launcher preparation findings—mutable receipt acceptance, implicit stop grace, and missing-field terminal defaults—were corrected before this only actual run. Parent inspected the final recipe; a separate direct Codex GPT-6.1 Sol review matched the actual gate/control/test results and found no blocker. These are observed fixture results, not inferred success from compilation or merely an inactive service.

## Remaining boundaries

The start/cancel race and injected kernel/I/O failure conditions remain synthetic control coverage. Output completeness is not asserted for every native role; descendant-held pipes can require bounded closure and incomplete capture. Kernel/hardware stalls retain ownership rather than guaranteeing prompt termination. The outer fixture's service lifecycle is not product supervisor-death/restart recovery. Trusted caller/no hostile same-user membership mutation remains a precondition; this is not a general sandbox or containment of delegated external-service work.

Windows/macOS compile portable types/tests only; native job backends and their actual fixtures remain open. Remote admission requires separately implemented authenticated pairing/scopes and replay/expiry policy. Persistent events, service packaging, session adapters and full P4 gates remain planned. P0–P6 continues, with client construction in parallel.

The bounded public-design Kimi K3 assignment completed with normal stop, zero tools and no fallback; useful advice received explicit coordinator/parent corrections. Direct Codex GPT-6.1 Sol authored the actual source independently, another Sol reviewed it, and the parent executed/accepted this native fixture. External-model advice is not presented as code or device evidence. Official API/source records R325–R328 link the selected upstream contracts.
