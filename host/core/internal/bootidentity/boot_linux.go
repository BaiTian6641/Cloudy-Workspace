//go:build linux

// Package bootidentity observes the boot UUID exposed by the caller's trusted
// procfs view. It supplies neither authenticated host nor installation/session identity.
package bootidentity

import (
	"io"
	"os"
)

const bootIDPath = "/proc/sys/kernel/random/boot_id"

// ID is an immutable, comparable canonical UUID observation. Its zero value is
// invalid and String returns empty for that value. Only a successful Read creates one.
type ID struct{ canonical string }

func (id ID) String() string { return id.canonical }

type failure struct {
	stage             string
	cause, closeCause error
}

// Do not wrap OS errors: paths and identifier content must not cross this boundary.
func (f *failure) Error() string { return "boot-identity-unavailable:" + f.stage }

// Read opens the fixed kernel interface anew on every call. It never generates,
// stores or substitutes an identity. The caller must trust its procfs mount view;
// this is not proof of identity across containers or user/login sessions.
func Read() (ID, error) {
	return readWith(func() (io.ReadCloser, error) { return os.Open(bootIDPath) })
}

func readWith(open func() (io.ReadCloser, error)) (ID, error) {
	r, err := open()
	if err != nil {
		var closeErr error
		if r != nil {
			// Keep both actual causes privately and the first safe public stage.
			// Never retry Close, even when it reports an uncertain outcome.
			closeErr = r.Close()
		}
		return ID{}, &failure{stage: "open", cause: err, closeCause: closeErr}
	}
	if r == nil {
		return ID{}, &failure{stage: "open"}
	}
	// Kernel proc_dostring emits 36 UUID characters plus LF. Read one extra byte
	// to reject trailing data without allocating based on untrusted file size.
	data, readErr := io.ReadAll(io.LimitReader(r, 38))
	closeErr := r.Close()
	if readErr != nil {
		return ID{}, &failure{stage: "read", cause: readErr, closeCause: closeErr}
	}
	if closeErr != nil {
		return ID{}, &failure{stage: "close", cause: closeErr}
	}
	return parse(data)
}

func parse(data []byte) (ID, error) {
	if len(data) != 37 || data[36] != '\n' {
		return ID{}, &failure{stage: "format"}
	}
	for i, b := range data[:36] {
		if i == 8 || i == 13 || i == 18 || i == 23 {
			if b != '-' {
				return ID{}, &failure{stage: "format"}
			}
		} else if !((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f')) {
			return ID{}, &failure{stage: "format"}
		}
	}
	// The kernel generates a random version-4 RFC variant UUID for boot_id.
	if data[14] != '4' || (data[19] != '8' && data[19] != '9' && data[19] != 'a' && data[19] != 'b') {
		return ID{}, &failure{stage: "format"}
	}
	return ID{canonical: string(data[:36])}, nil
}
