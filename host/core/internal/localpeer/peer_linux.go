//go:build linux

// Package localpeer checks one kernel credential observation on a borrowed local
// connection. UID equality is not program, session or remote-command authority.
package localpeer

import (
	"net"

	"golang.org/x/sys/unix"
)

// Keep OS causes private: even wrapped endpoint errors must not expose paths or
// account data through this boundary. The error intentionally has no Unwrap.
type failure struct {
	code, stage string
	cause       error
}

func (f *failure) Error() string { return "local-peer-" + f.code + ":" + f.stage }

// RequireUID borrows an already connected AF_UNIX SOCK_STREAM connection and
// compares its SO_PEERCRED UID with trusted caller policy. Linux captures these
// credentials at connect/listen/socketpair time; this is not a live program or
// login-session identity, nor a namespace-independent account identifier.
// No bytes, deadlines, descriptors or connection ownership are changed.
func RequireUID(conn *net.UnixConn, expectedUID uint32) error {
	if conn == nil {
		return &failure{code: "invalid-input", stage: "connection"}
	}
	raw, err := conn.SyscallConn()
	if err != nil {
		return &failure{code: "unavailable", stage: "connection", cause: err}
	}
	var observed error
	// The callback's refcount-protected descriptor is borrowed only for these
	// non-I/O queries. Never retain it, close it or obtain another owner via File.
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
		cred, err := unix.GetsockoptUcred(int(fd), unix.SOL_SOCKET, unix.SO_PEERCRED)
		if err != nil || cred == nil {
			observed = &failure{code: "unavailable", stage: "credentials", cause: err}
			return
		}
		// Ucred has no Darwin external-layout version. Do not copy that platform's
		// version gate or use its PID/GID as a stronger identity than this contract.
		if cred.Uid != expectedUID {
			observed = &failure{code: "rejected", stage: "uid"}
		}
	})
	if err != nil {
		return &failure{code: "unavailable", stage: "descriptor-control", cause: err}
	}
	return observed
}
