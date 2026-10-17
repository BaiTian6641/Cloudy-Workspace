//go:build !linux && !darwin

package diagnostic

import (
	"bytes"
	"os"
	"testing"
)

func TestEventsUnsupportedBeforeStateAccess(t *testing.T) {
	dir := t.TempDir()
	var out, errOut bytes.Buffer
	args := []string{"events", "read", "--state-dir", dir, "--after", "reset", "--limit", "1"}
	if Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime) != 1 || out.Len() != 0 || errOut.String() != "cloudy-host: events unsupported on this platform\n" {
		t.Fatal("unsupported events boundary incorrect")
	}
	if entries, err := os.ReadDir(dir); err != nil || len(entries) != 0 {
		t.Fatal("unsupported events changed state")
	}
	if Run(args, &out, failingWriter{}, forbiddenClock, forbiddenRuntime) != 1 {
		t.Fatal("failed stderr changed unsupported exit")
	}
	args[7] = "0"
	errOut.Reset()
	if Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime) != 2 || errOut.String() != usage {
		t.Fatal("unsupported platform bypassed syntax guard")
	}
}
