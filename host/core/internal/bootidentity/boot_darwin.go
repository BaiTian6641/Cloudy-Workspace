//go:build darwin

// Package bootidentity observes the caller's kernel boot-session UUID. It does
// not supply authenticated host, installation, process or login-session identity.
package bootidentity

import "golang.org/x/sys/unix"

const bootSysctlName = "kern.bootsessionuuid"

// ID is an immutable, comparable canonical UUID observation. Its zero value is
// invalid and String returns empty for that value. Only a successful Read creates one.
type ID struct{ canonical string }

func (id ID) String() string { return id.canonical }

type failure struct {
	stage string
	cause error
}

// Do not wrap the backend error or disclose its text or the observed identifier.
func (f *failure) Error() string { return "boot-identity-unavailable:" + f.stage }

// Read queries the fixed kernel node anew. Inspected Apple XNU initializes its
// random v4 UUID at boot and exports a read-only uuid_string_t. This named node
// has no claimed stable SDK/minimum-version guarantee: absent/restricted nodes
// fail rather than manufacture, cache or substitute another kind of identity.
//
// Sysctl removes the kernel string's terminal NUL. The upstream wrapper allocates
// the kernel-reported size, not a caller-limited buffer; this reader trusts the
// kernel view and accepts only the expected 36 ASCII characters afterward.
// There is no owned FD or Close, cancellation deadline or cross-reboot proof.
func Read() (ID, error) { return readWith(unix.Sysctl) }

func readWith(query func(string) (string, error)) (ID, error) {
	if query == nil {
		return ID{}, &failure{stage: "query"}
	}
	value, err := query(bootSysctlName)
	if err != nil {
		// A value accompanying an error is not an authoritative observation.
		return ID{}, &failure{stage: "query", cause: err}
	}
	return parse(value)
}

func parse(value string) (ID, error) {
	if len(value) != 36 {
		return ID{}, &failure{stage: "format"}
	}
	var canonical [36]byte
	for i := range canonical {
		b := value[i]
		if i == 8 || i == 13 || i == 18 || i == 23 {
			if b != '-' {
				return ID{}, &failure{stage: "format"}
			}
		} else {
			if b >= 'A' && b <= 'F' {
				b += 'a' - 'A'
			}
			if !((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f')) {
				return ID{}, &failure{stage: "format"}
			}
		}
		canonical[i] = b
	}
	// Apple's inspected kernel uuid_generate sets random-v4/RFC variant bits.
	// Refuse a changed format rather than silently broaden the typed contract.
	if canonical[14] != '4' || (canonical[19] != '8' && canonical[19] != '9' && canonical[19] != 'a' && canonical[19] != 'b') {
		return ID{}, &failure{stage: "format"}
	}
	return ID{canonical: string(canonical[:])}, nil
}
