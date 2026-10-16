//go:build darwin

package bootidentity

import (
	"errors"
	"strings"
	"testing"

	"golang.org/x/sys/unix"
)

// Synthetic parser fixture, never a sampled machine identifier.
const sample = "abcdefab-2222-4333-a444-555555555555"

func TestDarwinCanonicalAndZero(t *testing.T) {
	var zero ID
	if zero.String() != "" {
		t.Fatal("zero observation is not empty")
	}
	for _, value := range []string{sample, strings.ToUpper(sample), "aBcDeFaB-2222-4333-A444-555555555555"} {
		id, err := parse(value)
		if err != nil || id.String() != sample {
			t.Fatal("valid synthetic case did not canonicalize")
		}
		copyID := id
		id = ID{}
		if copyID.String() != sample || id == copyID {
			t.Fatal("copied immutable observation changed")
		}
	}
	for _, variant := range []byte{'8', '9', 'a', 'b'} {
		value := []byte(sample)
		value[19] = variant
		id, err := parse(string(value))
		if err != nil || id.String() != string(value) {
			t.Fatal("valid synthetic RFC variant rejected")
		}
		value[0] = '1'
		if id.String() == string(value) {
			t.Fatal("observation aliases mutable input")
		}
	}
}

func TestDarwinRejectMalformed(t *testing.T) {
	cases := map[string]string{
		"empty": "", "truncated": sample[:35], "extra": sample + "0",
		"newline": sample + "\n", "nul": sample + "\x00", "crlf": sample + "\r\n",
		"leading-space": " " + sample, "trailing-space": sample + " ",
		"embedded-nul": "\x00" + sample[1:], "braces": "{" + sample + "}",
		"hyphen": sample[:8] + "0" + sample[9:], "hex": "g" + sample[1:],
		"utf8": "é" + sample[2:], "version": sample[:14] + "1" + sample[15:],
		"variant":  sample[:19] + "7" + sample[20:],
		"nil-uuid": "00000000-0000-0000-0000-000000000000",
		"long":     strings.Repeat("a", 4096), "tab": "\t" + sample[1:],
	}
	for name, value := range cases {
		t.Run(name, func(t *testing.T) {
			id, err := parse(value)
			assertDarwinFailure(t, id, err, "format")
		})
	}
}

func assertDarwinFailure(t *testing.T, id ID, err error, stage string) {
	t.Helper()
	if id != (ID{}) || err == nil || err.Error() != "boot-identity-unavailable:"+stage {
		t.Fatal("safe failure result differs")
	}
	if errors.Unwrap(err) != nil {
		t.Fatal("private error was unwrapped")
	}
}

func TestDarwinQueryAuthorityAndNoCache(t *testing.T) {
	calls := 0
	other := "11111111-2222-4333-8444-555555555555"
	query := func(name string) (string, error) {
		calls++
		if name != "kern.bootsessionuuid" {
			t.Fatal("query did not use fixed kernel authority")
		}
		if calls == 1 {
			return strings.ToUpper(sample), nil
		}
		return other, nil
	}
	a, err := readWith(query)
	if err != nil {
		t.Fatal("first synthetic query failed")
	}
	b, err := readWith(query)
	if err != nil || calls != 2 || a.String() != sample || b.String() != other || a == b {
		t.Fatal("reader cached or substituted an observation")
	}
	id, err := readWith(func(name string) (string, error) { return "malformed", nil })
	assertDarwinFailure(t, id, err, "format")
}

func TestDarwinQueryErrorBoundary(t *testing.T) {
	for _, cause := range []error{unix.ENOENT, unix.EPERM, errors.New("synthetic-private-detail")} {
		id, err := readWith(func(name string) (string, error) { return sample, cause })
		assertDarwinFailure(t, id, err, "query")
		if errors.Is(err, cause) {
			t.Fatal("underlying backend error leaked through wrapping")
		}
		private, ok := err.(*failure)
		if !ok || private.cause != cause {
			t.Fatal("original cause was not retained privately")
		}
	}
}

func TestDarwinNilQuerySafelyRefuses(t *testing.T) {
	id, err := readWith(nil)
	assertDarwinFailure(t, id, err, "query")
}

// Actual native read only: no identifier is printed, no reboot is attempted.
// Two equal reads support stability within this observation interval only.
func TestDarwinReadActualStable(t *testing.T) {
	a, err := Read()
	if err != nil {
		t.Fatal("first actual kernel observation unavailable")
	}
	b, err := Read()
	if err != nil || a == (ID{}) || a != b {
		t.Fatal("actual kernel observations invalid or unequal")
	}
	id, err := parse(a.String())
	if err != nil || id != a {
		t.Fatal("actual kernel observation noncanonical")
	}
}
