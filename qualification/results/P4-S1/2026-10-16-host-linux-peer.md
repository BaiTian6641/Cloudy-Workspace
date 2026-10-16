# HOST-LINUX-PEER01 finite local result

**2026-10-16; parent and independent source/local runtime acceptance, with no capability/stage promotion.** The [shared peer contract](../../../docs/03-platform/host-local-peer.md) now has a real Linux `RequireUID(*net.UnixConn, expectedUID uint32) error` using existing Go1.27.2/xsys0.49.0. The [manifest](2026-10-16-host-linux-peer.json) binds exact source, module, raw test, compiled artifact and review hashes. Host0.1.2 CLI, jobs and Windows native safety fixture are unchanged.

## Operation and actual checks

The function checks actual socket stream type, connected UNIX peer and SO_PEERCRED UID inside borrowed RawConn.Control. It compares the observation with a trusted caller-specified expected UID, with no UID0 bypass or invented Darwin version field. It does not read/write bytes, mutate deadlines, duplicate/close the caller's descriptor, retain its integer or expose PID/GID. Fixed private code/stage errors do not unwrap raw endpoint causes.

Six commands passed: selected compiler identity, gofmt, changed-package Linux vet, Linux race execution, Darwin amd64 and Darwin arm64 test cross-compilation. On the selected **Linux kernel7.0.0-38-generic, x86_64** host, raw race output contains **6 named passes,0 skips/failures**, with count1 and15s test timeout:

- Kernel UID observation and deliberately unequal expected UID rejection on real stream socketpairs, followed by bidirectional fixed data under2s endpoint deadlines.
- Actual datagram rejection.
- Actual unconnected stream refusal after Go FileConn conversion.
- Nil and already-closed connection rejection.
- Uninitialized typed connection rejection.
- Private non-unwrapped error cause.

Both original descriptors are owned before either conversion; each FileConn duplicate is separately owned and checked on close. No external listener, child process, account, service or namespace was created/changed. The unequal expected UID is a policy negative control against the same observed peer, not an actual other-user process. No injected kernel-query/close failure or cross-namespace deployment is claimed.

Both Darwin source02 files and module files remain byte-identical. Darwin binaries cross-compiled only in this packet; no native macOS or jobs test was rerun. Earlier [macOS construction evidence](2026-10-16-host-macos-peer.md) is preserved rather than replaced. Independent direct Sol review matched all four sources, module/snapshot/patch/artifact hashes, raw six starts/passes and empty stderr; it found no actionable finding and did not rerun checks. The parent inspected source/tests before acceptance. No external model was dispatched for this small maintained-API operation.

## Exact limits and subsequent hosted status

Linux SO_PEERCRED is a connection-time credential snapshot, not live program identity. A matching trusted expected UID does not authenticate a particular executable, login/GUI session, code signature, consent or remote paired identity. Numeric UIDs are namespace-relative; trusted caller policy must not come from a peer's request. Descriptor transfer and hostile same-user/namespace deployment require a later profile. No registered listener, helper protocol, command admission or event/boot/session identity is supplied. **macOS managed descendant jobs remain unimplemented.** P4-S1 prerequisites P1-S4/P3-S1 remain unchanged. [Linux unix(7)](https://man7.org/linux/man-pages/man7/unix.7.html), [user_namespaces(7)](https://man7.org/linux/man-pages/man7/user_namespaces.7.html), [selected x/sys wrapper](https://github.com/golang/sys/blob/b6b8557e8e2974925aec7a136ba0e323eedc5f43/unix/syscall_linux.go#L1283).

Separate public metadata for exactf2f8c16d558f76f9b6eb54f1af06af5487e055b7 [host run38960391718](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38960391718) reports all3OS jobs successful, including the Windows native-fixture and evidence-upload steps. Its macOS compile/vet/test step also succeeds. This predecessor commit does not include the new Linux peer source and is not hosted acceptance of it. A single unauthenticated artifact-archive retrieval returned HTTP401. Raw Windows case counts/retirement, exact remote source/binary bindings, and individual macOS case outcomes/skips remain uninspected; no archive digest was validated from bytes. No credentials, retry or bypass were used. The manifest binds the access-review receipt and public artifact metadata only; native per-case acceptance is not fabricated from successful hosted status.
