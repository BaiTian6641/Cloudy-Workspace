//go:build !linux && !darwin

package diagnostic

import (
	"bytes"
	"os"
	"testing"
)

func TestContextUnsupportedBeforeStateAccess(t *testing.T) {
	dir := t.TempDir()
	var out, errOut bytes.Buffer
	if code := Run([]string{"context", "read", "--state-dir", dir}, &out, &errOut, forbiddenClock, forbiddenRuntime); code != 1 || out.Len() != 0 || errOut.String() != "cloudy-host: context unsupported on this platform\n" {
		t.Fatal("unsupported context boundary incorrect")
	}
	entries, err := os.ReadDir(dir)
	if err != nil || len(entries) != 0 {
		t.Fatal("unsupported context changed state")
	}
	if code := Run([]string{"context", "read", "--state-dir", dir}, &out, failingWriter{}, forbiddenClock, forbiddenRuntime); code != 1 {
		t.Fatal("failed stderr changed unsupported exit")
	}
}
