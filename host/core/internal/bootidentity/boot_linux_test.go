//go:build linux

package bootidentity

import (
	"errors"
	"io"
	"strings"
	"testing"
)

// Synthetic parser fixture, never an observed machine identifier.
const sample = "11111111-2222-4333-8444-555555555555\n"

func TestReadActualStable(t *testing.T) {
	a, err := Read()
	if err != nil {
		t.Fatal("first actual read failed")
	}
	b, err := Read()
	if err != nil {
		t.Fatal("second actual read failed")
	}
	if a.String() == "" || a != b {
		t.Fatal("actual observations invalid or unequal")
	}
	if _, err := parse([]byte(a.String() + "\n")); err != nil {
		t.Fatal("actual observation noncanonical")
	}
}

func TestCanonicalAndZero(t *testing.T) {
	var zero ID
	if zero.String() != "" {
		t.Fatal("zero ID is not empty")
	}
	for _, variant := range []string{"8", "9", "a", "b"} {
		data := []byte(sample)
		data[19] = variant[0]
		id, err := parse(data)
		if err != nil || id.String() != string(data[:36]) {
			t.Fatal("canonical synthetic record rejected")
		}
		data[0] = 'f'
		if id.String() == string(data[:36]) {
			t.Fatal("ID aliases mutable input")
		}
	}
}

func TestRejectMalformed(t *testing.T) {
	cases := map[string]string{
		"empty": "", "missing-newline": sample[:36], "truncated": sample[:35],
		"extra": sample + "x", "double-newline": sample + "\n",
		"crlf": sample[:36] + "\r\n", "leading-space": " " + sample,
		"uppercase": "AAAAAAAA-2222-4333-8444-555555555555\n",
		"nonhex":    "g" + sample[1:], "nul": "\x00" + sample[1:],
		"hyphen":   sample[:8] + "0" + sample[9:],
		"version":  sample[:14] + "1" + sample[15:],
		"variant":  sample[:19] + "7" + sample[20:],
		"nil-uuid": "00000000-0000-0000-0000-000000000000\n",
		"utf8":     "é" + sample[2:],
	}
	for name, data := range cases {
		t.Run(name, func(t *testing.T) {
			id, err := parse([]byte(data))
			assertFailure(t, id, err, "format")
		})
	}
}

type ownedReader struct {
	reader   io.Reader
	closeErr error
	closes   int
	bytes    int
}

func (r *ownedReader) Read(p []byte) (int, error) {
	n, err := r.reader.Read(p)
	r.bytes += n
	return n, err
}
func (r *ownedReader) Close() error { r.closes++; return r.closeErr }

type brokenReader struct{}

func (brokenReader) Read(p []byte) (int, error) {
	copy(p, sample)
	return min(len(p), len(sample)), errors.New("private identifier/path details")
}

func assertFailure(t *testing.T, id ID, err error, stage string) {
	t.Helper()
	if id != (ID{}) || err == nil || err.Error() != "boot-identity-unavailable:"+stage {
		t.Fatal("unexpected failure boundary")
	}
	if _, ok := err.(interface{ Unwrap() error }); ok {
		t.Fatal("failure exposes underlying error")
	}
}

func TestReadAndCloseFailures(t *testing.T) {
	for _, tc := range []struct {
		name     string
		reader   io.Reader
		closeErr error
		stage    string
	}{
		{"success", strings.NewReader(sample), nil, ""},
		{"read", brokenReader{}, nil, "read"},
		{"close", strings.NewReader(sample), errors.New("private close path"), "close"},
		{"read-first", brokenReader{}, errors.New("private close path"), "read"},
		{"format", strings.NewReader("bad"), nil, "format"},
		{"format-close", strings.NewReader("bad"), errors.New("private close path"), "close"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := &ownedReader{reader: tc.reader, closeErr: tc.closeErr}
			id, err := readWith(func() (io.ReadCloser, error) { return r, nil })
			if tc.stage == "" {
				if err != nil || id.String() != sample[:36] {
					t.Fatal("valid read failed")
				}
			} else {
				assertFailure(t, id, err, tc.stage)
			}
			if tc.stage == "read" && tc.closeErr != nil && err.(*failure).closeCause != tc.closeErr {
				t.Fatal("secondary close failure lost")
			}
			if r.closes != 1 {
				t.Fatal("owner not closed exactly once")
			}
		})
	}
}

func TestBoundedRead(t *testing.T) {
	r := &ownedReader{reader: strings.NewReader(sample + strings.Repeat("x", 4096))}
	id, err := readWith(func() (io.ReadCloser, error) { return r, nil })
	assertFailure(t, id, err, "format")
	if r.bytes != 38 || r.closes != 1 {
		t.Fatal("read exceeded bound or lost owner")
	}
}

func TestOpenFailures(t *testing.T) {
	for _, partial := range []bool{false, true} {
		r := &ownedReader{reader: strings.NewReader(sample), closeErr: errors.New("private close")}
		id, err := readWith(func() (io.ReadCloser, error) {
			if partial {
				return r, errors.New("private open")
			}
			return nil, errors.New("private open")
		})
		assertFailure(t, id, err, "open")
		want := 0
		if partial {
			want = 1
		}
		if r.closes != want || r.bytes != 0 {
			t.Fatal("partial open ownership lost")
		}
	}
	id, err := readWith(func() (io.ReadCloser, error) { return nil, nil })
	assertFailure(t, id, err, "open")
}
