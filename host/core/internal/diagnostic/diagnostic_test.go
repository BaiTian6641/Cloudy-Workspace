package diagnostic

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"
)

func TestInspectContract(t *testing.T) {
	var out, errOut bytes.Buffer
	now := time.Date(2026, 10, 16, 12, 30, 15, 123456789, time.FixedZone("test", -5*60*60))
	calls := 0
	code := Run([]string{"inspect"}, &out, &errOut, func() time.Time {
		calls++
		return now
	}, func() Runtime {
		calls++
		return Runtime{OS: "test-os", Arch: "test-arch", GoVersion: "test-go"}
	})
	if code != 0 || errOut.Len() != 0 || calls != 2 {
		t.Fatalf("exit=%d stderr=%q source calls=%d", code, errOut.String(), calls)
	}
	// An exact independently written contract catches accidental extra identity,
	// environment or wire-manifest fields as well as false capability claims.
	want := `{"localDiagnosticVersion":1,"agentVersion":"0.1.3","observedAt":"2026-10-16T17:30:15.123456789Z","runtime":{"os":"test-os","arch":"test-arch","goVersion":"test-go"},"features":{"pairing":{"state":"unavailable","reason":"not-implemented"},"remoteControl":{"state":"unavailable","reason":"not-implemented"},"jobs":{"state":"unavailable","reason":"not-implemented"},"events":{"state":"unavailable","reason":"not-implemented"},"sessionAdapters":{"state":"unavailable","reason":"not-implemented"}},"backends":{"remoteDesktop":{"state":"unknown","reason":"not-inspected"},"usb":{"state":"unknown","reason":"not-inspected"},"notifications":{"state":"unknown","reason":"not-inspected"}}}` + "\n"
	if out.String() != want {
		t.Fatalf("unexpected diagnostic JSON: %s", out.String())
	}
	var decoded map[string]any
	if err := json.Unmarshal(out.Bytes(), &decoded); err != nil {
		t.Fatal(err)
	}
}

func forbiddenClock() time.Time { panic("unexpected clock inspection") }
func forbiddenRuntime() Runtime { panic("unexpected runtime inspection") }

func TestVersionDoesNotInspectRuntime(t *testing.T) {
	var out, errOut bytes.Buffer
	code := Run([]string{"version"}, &out, &errOut, forbiddenClock, forbiddenRuntime)
	if code != 0 || out.String() != "0.1.3\n" || errOut.Len() != 0 {
		t.Fatalf("exit=%d stdout=%q stderr=%q", code, out.String(), errOut.String())
	}
}

func TestUsageRejectsUnknownAndExtraArguments(t *testing.T) {
	for _, args := range [][]string{nil, {"unknown"}, {"inspect", "extra"}, {"version", "extra"}, {"--help"}, {""}, {"private-command"}} {
		t.Run(strings.Join(args, "/"), func(t *testing.T) {
			var out, errOut bytes.Buffer
			code := Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime)
			if code != 2 || out.Len() != 0 || errOut.String() != "usage: cloudy-host inspect | version | identity init|read --state-dir <existing-directory> | context read --state-dir <existing-directory>\n" {
				t.Fatalf("exit=%d stdout=%q stderr=%q", code, out.String(), errOut.String())
			}
		})
	}
}

type failingWriter struct{}

func (failingWriter) Write([]byte) (int, error) {
	return 0, errors.New("private-output-path secret-token")
}

func TestOutputFailureIsSafe(t *testing.T) {
	for _, command := range []string{"inspect", "version"} {
		t.Run(command, func(t *testing.T) {
			var errOut bytes.Buffer
			code := Run([]string{command}, failingWriter{}, &errOut, func() time.Time { return time.Unix(0, 0) }, func() Runtime { return Runtime{} })
			if code != 1 || errOut.String() != "cloudy-host: output failed\n" {
				t.Fatalf("exit=%d stderr=%q", code, errOut.String())
			}
		})
	}
}

func TestStderrFailureStillReturnsError(t *testing.T) {
	if code := Run(nil, failingWriter{}, failingWriter{}, forbiddenClock, forbiddenRuntime); code != 2 {
		t.Fatalf("usage exit=%d", code)
	}
	if code := Run([]string{"version"}, failingWriter{}, failingWriter{}, forbiddenClock, forbiddenRuntime); code != 1 {
		t.Fatalf("output failure exit=%d", code)
	}
}

type shortWriter struct{}

func (shortWriter) Write(p []byte) (int, error) { return len(p) / 2, nil }

func TestShortOutputFails(t *testing.T) {
	for _, command := range []string{"inspect", "version"} {
		var errOut bytes.Buffer
		code := Run([]string{command}, shortWriter{}, &errOut, func() time.Time { return time.Unix(0, 0) }, func() Runtime { return Runtime{} })
		if code != 1 || errOut.String() != "cloudy-host: output failed\n" {
			t.Fatalf("%s exit=%d stderr=%q", command, code, errOut.String())
		}
	}
}

func TestIdentityRejectsArgumentsBeforeIO(t *testing.T) {
	for _, args := range [][]string{
		{"identity"}, {"identity", "init"}, {"identity", "reset", "--state-dir", "private"},
		{"identity", "init", "--state-dir", ""}, {"identity", "read", "--state-dir", "private", "extra"},
		{"identity", "read", "--wrong", "private"},
	} {
		var out, errOut bytes.Buffer
		code := Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime)
		if code != 2 || out.Len() != 0 || errOut.String() != usage {
			t.Fatalf("args rejected incorrectly: exit%d stdout%q stderr%q", code, out.String(), errOut.String())
		}
	}
}
func TestIdentityMissingStateUsesSafeFailure(t *testing.T) {
	var out, errOut bytes.Buffer
	code := Run([]string{"identity", "read", "--state-dir", t.TempDir() + "/private-missing"}, &out, &errOut, forbiddenClock, forbiddenRuntime)
	if code != 1 || out.Len() != 0 || errOut.String() != "cloudy-host: invalid-state-directory\n" {
		t.Fatalf("exit%d stdout%q stderr%q", code, out.String(), errOut.String())
	}
}

func TestIdentityReadOutputAndFailureRetainState(t *testing.T) {
	dir := t.TempDir()
	// The fixture is a strict pre-existing identity, not a simulated initialization.
	// This tests presentation and output failure separately from native fsync behavior.
	if err := os.Mkdir(filepath.Join(dir, "installation-identity-v1"), 0o700); err != nil {
		t.Fatal(err)
	}
	record := filepath.Join(dir, "installation-identity-v1", "installation-identity.json")
	data := []byte(`{"identityRecordVersion":1,"installationId":"12345678-1234-4234-8234-123456789abc"}` + "\n")
	if err := os.WriteFile(record, data, 0o600); err != nil {
		t.Fatal(err)
	}
	var out, errOut bytes.Buffer
	args := []string{"identity", "read", "--state-dir", dir}
	if code := Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime); code != 0 {
		t.Fatalf("read exit%d error%s", code, errOut.String())
	}
	want := `{"localIdentityVersion":1,"agentVersion":"0.1.3","operation":"read","installationId":"12345678-1234-4234-8234-123456789abc","durability":"not-assessed"}` + "\n"
	if runtime.GOOS == "windows" {
		want = `{"localIdentityVersion":2,"agentVersion":"0.1.3","operation":"read","installationId":"12345678-1234-4234-8234-123456789abc","durability":"not-assessed","recordVersion":1,"storageProfile":"legacy-directory-v1"}` + "\n"
	}
	if out.String() != want || errOut.Len() != 0 {
		t.Fatalf("output%q error%q", out.String(), errOut.String())
	}
	for _, writer := range []io.Writer{failingWriter{}, shortWriter{}} {
		errOut.Reset()
		if code := Run(args, writer, &errOut, forbiddenClock, forbiddenRuntime); code != 1 || errOut.String() != "cloudy-host: identity output failed; existing state retained\n" {
			t.Fatalf("output failure exit%d error%q", code, errOut.String())
		}
	}
	after, err := os.ReadFile(record)
	if err != nil || !bytes.Equal(after, data) {
		t.Fatal("read/output failure changed identity")
	}
}

func TestContextRejectsArgumentsBeforeIO(t *testing.T) {
	for _, args := range [][]string{
		{"context"}, {"context", "read"}, {"context", "init", "--state-dir", "private"},
		{"context", "read", "--state-dir", ""}, {"context", "read", "--wrong", "private"},
		{"context", "read", "--state-dir", "private", "extra"},
	} {
		var out, errOut bytes.Buffer
		if code := Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime); code != 2 || out.Len() != 0 || errOut.String() != usage {
			t.Fatal("invalid context syntax entered operation")
		}
	}
}
