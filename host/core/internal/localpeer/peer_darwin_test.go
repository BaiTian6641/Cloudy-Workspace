//go:build darwin

package localpeer

import (
	"errors"
	"io"
	"net"
	"os"
	"testing"
	"time"

	"golang.org/x/sys/unix"
)

// These finite socketpairs are the only native resources created by this suite.
// FileConn duplicates each original descriptor; close both original owners now,
// and retain/close each resulting connection separately. No external listener,
// service, user switching, child process or credential provisioning is involved.
func socketPair(t *testing.T, kind int) [2]*net.UnixConn {
	t.Helper()
	fds, err := unix.Socketpair(unix.AF_UNIX, kind, 0)
	if err != nil {
		t.Fatal("socketpair failed")
	}
	var originals [2]*os.File
	// Register both raw owners before any conversion can fail.
	for i, fd := range fds {
		unix.CloseOnExec(fd)
		originals[i] = os.NewFile(uintptr(fd), "local-peer-test")
		t.Cleanup(func() {
			if originals[i] != nil {
				if err := originals[i].Close(); err != nil {
					t.Error("original socket close failed")
				}
			}
		})
	}
	var pair [2]*net.UnixConn
	for i, file := range originals {
		conn, err := net.FileConn(file)
		if err != nil {
			t.Fatal("socket conversion failed")
		}
		t.Cleanup(func() {
			if err := conn.Close(); err != nil && !errors.Is(err, net.ErrClosed) {
				t.Error("owned connection close failed")
			}
		})
		if err := file.Close(); err != nil {
			// Never retry a failed raw-handle close: retain the failed outcome.
			originals[i] = nil
			t.Fatal("original socket close failed")
		}
		originals[i] = nil
		var ok bool
		pair[i], ok = conn.(*net.UnixConn)
		if !ok {
			t.Fatal("unexpected socket connection type")
		}
	}
	return pair
}

func TestRequireUIDKernelObservationAndBorrowedOwnership(t *testing.T) {
	pair := socketPair(t, unix.SOCK_STREAM)
	uid := uint32(os.Geteuid())
	for _, conn := range pair {
		if err := RequireUID(conn, uid); err != nil {
			t.Fatal("actual peer UID check failed", err)
		}
		if err := RequireUID(conn, uid^1); err == nil || err.Error() != "local-peer-rejected:uid" {
			t.Fatal("unequal expected UID admitted", err)
		}
	}
	// Successful and rejected observations must leave both borrowed endpoints
	// usable. Bound every real I/O operation independently of test timeouts.
	for _, conn := range pair {
		if err := conn.SetDeadline(time.Now().Add(2 * time.Second)); err != nil {
			t.Fatal("deadline failed")
		}
	}
	for sender := range 2 {
		want := []byte("finite-owned-socket")
		if n, err := pair[sender].Write(want); err != nil || n != len(want) {
			t.Fatal("borrowed endpoint write failed")
		}
		got := make([]byte, len(want))
		if _, err := io.ReadFull(pair[1-sender], got); err != nil || string(got) != string(want) {
			t.Fatal("borrowed endpoint read failed")
		}
	}
}

func TestRequireUIDRejectsDatagram(t *testing.T) {
	pair := socketPair(t, unix.SOCK_DGRAM)
	if err := RequireUID(pair[0], uint32(os.Geteuid())); err == nil || err.Error() != "local-peer-rejected:socket-type" {
		t.Fatal("datagram admitted", err)
	}
}

func TestRequireUIDRejectsUnconnectedStream(t *testing.T) {
	fd, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM, 0)
	if err != nil {
		t.Fatal("socket failed")
	}
	unix.CloseOnExec(fd)
	file := os.NewFile(uintptr(fd), "unconnected-peer-test")
	conn, convertErr := net.FileConn(file)
	closeErr := file.Close()
	if conn != nil {
		t.Cleanup(func() {
			if err := conn.Close(); err != nil {
				t.Error("unconnected socket close failed")
			}
		})
	}
	if convertErr != nil || closeErr != nil {
		t.Fatal("unconnected socket setup failed")
	}
	local, ok := conn.(*net.UnixConn)
	if !ok {
		t.Fatal("unexpected socket connection type")
	}
	if err := RequireUID(local, uint32(os.Geteuid())); err == nil || err.Error() != "local-peer-unavailable:connected-peer" {
		t.Fatal("unconnected stream admitted", err)
	}
}

func TestRequireUIDRejectsNilAndClosedConnection(t *testing.T) {
	if err := RequireUID(nil, 0); err == nil || err.Error() != "local-peer-invalid-input:connection" {
		t.Fatal("nil connection admitted", err)
	}
	pair := socketPair(t, unix.SOCK_STREAM)
	if err := pair[0].Close(); err != nil {
		t.Fatal("connection close failed")
	}
	if err := RequireUID(pair[0], uint32(os.Geteuid())); err == nil || err.Error() != "local-peer-unavailable:descriptor-control" {
		t.Fatal("closed connection admitted", err)
	}
}

// Policy-only checks do not establish real cross-user socket behavior.
func TestCredentialPolicyVersionAndExactUID(t *testing.T) {
	for _, tc := range []struct {
		name string
		cred *unix.Xucred
		want string
	}{
		{"missing", nil, "local-peer-unavailable:credential-version"},
		{"wrong-version", &unix.Xucred{Version: 1, Uid: 42}, "local-peer-unavailable:credential-version"},
		{"wrong-uid", &unix.Xucred{Uid: 43}, "local-peer-rejected:uid"},
		{"exact-uid", &unix.Xucred{Uid: 42}, ""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			err := requireCredential(tc.cred, 42)
			if tc.want == "" {
				if err != nil {
					t.Fatal(err)
				}
			} else if err == nil || err.Error() != tc.want {
				t.Fatal("credential policy changed", err)
			}
		})
	}
}

func TestFailureKeepsPrivateCause(t *testing.T) {
	secret := errors.New("private-endpoint-data")
	err := &failure{code: "unavailable", stage: "credentials", cause: secret}
	if err.Error() != "local-peer-unavailable:credentials" || errors.Unwrap(err) != nil || errors.Is(err, secret) {
		t.Fatal("private cause escaped")
	}
}
