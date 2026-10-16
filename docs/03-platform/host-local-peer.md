# macOS local-socket peer UID check

**2026-10-16; HOST-MACOS-PEER01 source/build construction accepted, native execution pending.** The internal Darwin-only `host/core/internal/localpeer` library supplies one real operation:

```go
RequireUID(conn *net.UnixConn, expectedUID uint32) error
```

This is a prerequisite for a future host-core/user-session-helper boundary under [P4](../05-delivery/phases/p4-host-integration.md), not a running helper or complete authenticated IPC. The [construction result](../../qualification/results/P4-S1/2026-10-16-host-macos-peer.md) records exact source/artifact evidence. The host0.1.2 executable, diagnostic and installation identity remain unchanged. There is no listener, protocol, command admission, service installation or capability/stage promotion.

## Operation and caller ownership

The caller supplies an already connected UNIX stream connection and a trusted expected effective UID. The expected UID is policy from the owning caller; accepting a peer-supplied number would defeat that check. Zero has no implicit user mapping or bypass.

`RequireUID` borrows the connection through `UnixConn.SyscallConn` and inspects the descriptor only inside `RawConn.Control`. It checks `SO_TYPE == SOCK_STREAM`, a successful `Getpeername` with `SockaddrUnix`, then queries `GetsockoptXucred(fd, SOL_LOCAL, LOCAL_PEERCRED)`. A valid external credential layout version and exact UID equality are required. Nil/closed connections, invalid socket preconditions, query errors, missing/wrong-version credentials and UID mismatch return fixed safe errors. The raw OS cause remains private without `Unwrap`; paths, IDs or endpoint data are not interpolated into error text.

The function does not read/write bytes, change deadlines, duplicate/close descriptors or take connection ownership. The caller still owns the connection on success and failure. `RawConn.Control` protects the borrowed descriptor's callback lifetime; retaining its integer for later work is prohibited. [Go1.27.2 UnixConn.SyscallConn](https://pkg.go.dev/net@go1.27.2#UnixConn.SyscallConn), [RawConn.Control](https://pkg.go.dev/syscall@go1.27.2#RawConn).

## Exact trust boundary

A successful result establishes one kernel peer-credential observation and equality with the caller's expected effective UID. It does not identify a particular same-user program, code signature, login/GUI session, current console owner, consent, remote paired identity or authority for an operation. Connection credentials are not a continuously refreshed process identity. Descriptor transfer and hostile same-user behavior need separate protocol/deployment policy; this function alone does not resolve them. No GID, group list, PID or process inventory is exposed. [Apple getpeereid reference](https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man3/getpeereid.3.html).

Do not use a matching UID to enable remote jobs or advertise a working session adapter. macOS descendant ownership remains unimplemented in the [local-jobs contract](host-local-jobs.md). Process groups, helper presence and this socket check do not qualify job retirement.

## Public API reuse and evidence

Reuse exact Go1.27.2 and `golang.org/x/sys v0.49.0` with existing authenticated module sums/BSD-3-Clause materials. No dependency, cgo, private syscall binding or copied Apple implementation was added. The [pinned Darwin wrapper](https://github.com/golang/sys/blob/v0.49.0/unix/syscall_darwin.go) supplies `GetsockoptXucred`, types and socket constants; its maintained [upstream test](https://github.com/golang/sys/blob/v0.49.0/unix/syscall_darwin_test.go) supplies reference behavior, not Cloudy runtime acceptance. The wrapper does not expose returned socklen, so Cloudy makes no independent returned-length-validation claim.

The external layout version `XUCRED_VERSION == 0` is documented in [Apple's pinned header](https://github.com/apple-oss-distributions/xnu/blob/f6217f891ac0bb64f3d375211650a4c1ff8ca1ea/bsd/sys/ucred.h); [the socket header](https://github.com/apple-oss-distributions/xnu/blob/f6217f891ac0bb64f3d375211650a4c1ff8ca1ea/bsd/sys/un.h) defines the local peer option. [Pinned Apple libc](https://github.com/apple-oss-distributions/Libc/blob/71bbe350ab79eef58113991d817ccc6165061a64/gen/FreeBSD/getpeereid.c) corroborates the option/version check. Checked2026-10-16; exact reference hashes are in the result manifest. The archived manual and current source do not establish a minimum supported macOS release.

Current verification02 passes five format/vet/test-cross-build checks on Linux for Darwin amd64/arm64. It repairs an independently found test-evidence gap: the unconnected test now checks its duplicate connection close through `t.Cleanup` rather than discarding a deferred close error. Production `RequireUID` is unchanged. Parent and independent source02 review accepted this narrow repair with no unresolved actionable finding; the original01 finding remains preserved. The [result manifest](../../qualification/results/P4-S1/2026-10-16-host-macos-peer.json) binds the exact independent closure note/receipt and original finding receipt. This closes source review only; native endpoint behavior and fault-injected close failure remain unrun. Original verification01 and its seven checks remain historical; unchanged compiler identity/module verification were reused without a repeat. **Zero tests executed; no native macOS pass.** The six compiled test methods include real finite socketpair observation/ownership and deadline-bounded bidirectional endpoint use, datagram/unconnected/nil/closed rejection, and pure policy/privacy checks. The existing hosted macOS `go test -v ./...` can execute them after integration; exact runner OS/architecture, named results/skips and source binding are required. Socketpair/address behavior and `FileConn` conversion of an unconnected stream remain actual runtime gates. Cross-user accounts, accepted filesystem socket deployment, launchd activation, signing, logout/lock and a real helper are separate qualification work.
