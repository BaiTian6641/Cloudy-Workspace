//go:build darwin

// Package localpeer checks one kernel credential observation on a borrowed local
// connection. A matching UID does not identify an application or login session,
// authenticate a remote peer, or authorize a product command.
package localpeer

import (
	"net"

	"golang.org/x/sys/unix"
)

// XUCRED_VERSION is the public external credential layout version in Apple's
// sys/ucred.h at apple-oss-distributions/xnu commit
// f6217f891ac0bb64f3d375211650a4c1ff8ca1ea. Use the maintained x/sys Xucred ABI rather than copying a struct.
const xucredVersion = 0

// failure deliberately has no Unwrap method. OS errors can contain private
// endpoint data; only fixed code/stage text crosses this boundary.
type failure struct {
	code, stage string
	cause       error
}

func (f *failure) Error() string { return "local-peer-" + f.code + ":" + f.stage }

// RequireUID borrows an already connected AF_UNIX SOCK_STREAM connection and
// checks its kernel-reported peer effective UID against caller-owned policy.
// expectedUID must come from a trusted authority, not from the peer's request.
// No bytes, deadlines, descriptors or connection ownership are changed. The
// credential snapshot does not establish current GUI/session or code identity.
func RequireUID(conn *net.UnixConn, expectedUID uint32) error {
	if conn == nil {
		return &failure{code: "invalid-input", stage: "connection"}
	}
	raw, err := conn.SyscallConn()
	if err != nil {
		return &failure{code: "unavailable", stage: "connection", cause: err}
	}
	var observed error
	// Control keeps this exact descriptor valid for this callback only. Do not
	// retain the integer, close it, or use File() to acquire another owner.
	err = raw.Control(func(fd uintptr) {
		typ, err := unix.GetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_TYPE)
		if err != nil {
			observed = &failure{code: "unavailable", stage: "socket-type", cause: err}
			return
		}
		if typ != unix.SOCK_STREAM {
			observed = &failure{code: "rejected", stage: "socket-type"}
			return
		}
		peer, err := unix.Getpeername(int(fd))
		if err != nil {
			observed = &failure{code: "unavailable", stage: "connected-peer", cause: err}
			return
		}
		if _, ok := peer.(*unix.SockaddrUnix); !ok {
			observed = &failure{code: "rejected", stage: "socket-domain"}
			return
		}
		cred, err := unix.GetsockoptXucred(int(fd), unix.SOL_LOCAL, unix.LOCAL_PEERCRED)
		if err != nil {
			observed = &failure{code: "unavailable", stage: "credentials", cause: err}
			return
		}
		observed = requireCredential(cred, expectedUID)
	})
	if err != nil {
		return &failure{code: "unavailable", stage: "descriptor-control", cause: err}
	}
	return observed
}

func requireCredential(cred *unix.Xucred, expectedUID uint32) error {
	if cred == nil || cred.Version != xucredVersion {
		return &failure{code: "unavailable", stage: "credential-version"}
	}
	if cred.Uid != expectedUID {
		return &failure{code: "rejected", stage: "uid"}
	}
	return nil
}
