package diagnostic

import (
	"bytes"
	"encoding/json"
	"errors"
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
	want := `{"localDiagnosticVersion":1,"agentVersion":"0.1.0","observedAt":"2026-10-16T17:30:15.123456789Z","runtime":{"os":"test-os","arch":"test-arch","goVersion":"test-go"},"features":{"pairing":{"state":"unavailable","reason":"not-implemented"},"remoteControl":{"state":"unavailable","reason":"not-implemented"},"jobs":{"state":"unavailable","reason":"not-implemented"},"events":{"state":"unavailable","reason":"not-implemented"},"sessionAdapters":{"state":"unavailable","reason":"not-implemented"}},"backends":{"remoteDesktop":{"state":"unknown","reason":"not-inspected"},"usb":{"state":"unknown","reason":"not-inspected"},"notifications":{"state":"unknown","reason":"not-inspected"}}}` + "\n"
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
	if code != 0 || out.String() != "0.1.0\n" || errOut.Len() != 0 {
		t.Fatalf("exit=%d stdout=%q stderr=%q", code, out.String(), errOut.String())
	}
}

func TestUsageRejectsUnknownAndExtraArguments(t *testing.T) {
	for _, args := range [][]string{nil, {"unknown"}, {"inspect", "extra"}, {"version", "extra"}, {"--help"}, {""}, {"private-command"}} {
		t.Run(strings.Join(args, "/"), func(t *testing.T) {
			var out, errOut bytes.Buffer
			code := Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime)
			if code != 2 || out.Len() != 0 || errOut.String() != "usage: cloudy-host inspect | version\n" {
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
