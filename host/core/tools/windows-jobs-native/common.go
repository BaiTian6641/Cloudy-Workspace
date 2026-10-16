//go:build windows && amd64

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"os"
	"regexp"
	"strings"
)

var expectedTests = []string{
	"TestNativeWindowsOwnedJobs/exact-job-membership-and-cancel",
	"TestNativeWindowsOwnedJobs/unicode-argv-exact-environment-input",
	"TestNativeWindowsOwnedJobs/explicit-empty-environment",
	"TestNativeWindowsOwnedJobs/exit259-is-real-exit",
	"TestNativeWindowsOwnedJobs/parent-exit-retires-pipe-holding-descendant",
	"TestNativeWindowsOwnedJobs/bounded-output-blocked-input-timeout",
	"TestNativeWindowsOwnedJobs/unlisted-inheritable-handle-excluded",
	"TestNativeWindowsOwnedJobs",
}
var terminalLine = regexp.MustCompile(`^\s*--- (PASS|FAIL|SKIP): (\S+) \([0-9.]+s\)$`)

func verifyTerminals(data []byte) error {
	seen := map[string]bool{}
	allowed := map[string]bool{}
	for _, name := range expectedTests {
		allowed[name] = true
	}
	summary := 0
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSuffix(line, "\r")
		if line == "FAIL" {
			return errors.New("failed-summary")
		}
		if line == "PASS" {
			summary++
		}
		m := terminalLine.FindStringSubmatch(line)
		if m == nil {
			if strings.HasPrefix(strings.TrimSpace(line), "--- ") {
				return errors.New("unparsed-terminal")
			}
			continue
		}
		if m[1] != "PASS" || !allowed[m[2]] || seen[m[2]] {
			return errors.New("unexpected-terminal")
		}
		seen[m[2]] = true
	}
	if len(seen) != len(allowed) || summary != 1 {
		return errors.New("missing-terminal")
	}
	return nil
}
func digestFile(f *os.File) (string, error) {
	if _, err := f.Seek(0, 0); err != nil {
		return "", err
	}
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
func digestPath(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	h, readErr := digestFile(f)
	closeErr := f.Close()
	if readErr != nil {
		return "", readErr
	}
	if closeErr != nil {
		return "", closeErr
	}
	return h, nil
}
func validDigest(s string) bool {
	b, err := hex.DecodeString(s)
	return err == nil && len(b) == sha256.Size && s == strings.ToLower(s)
}
func loadSources(path string) (map[string]string, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	data, readErr := io.ReadAll(io.LimitReader(f, 65537))
	closeErr := f.Close()
	if readErr != nil {
		return nil, readErr
	}
	if closeErr != nil {
		return nil, closeErr
	}
	if len(data) > 65536 {
		return nil, errors.New("source-manifest-size")
	}
	var sources map[string]string
	if json.Unmarshal(data, &sources) != nil || len(sources) != 10 {
		return nil, errors.New("source-manifest-shape")
	}
	// Exact finite path set: eight jobs sources plus module version/checksums.
	paths := []string{"internal/jobs/backend_linux.go", "internal/jobs/backend_linux_test.go", "internal/jobs/backend_windows_amd64.go", "internal/jobs/backend_windows_amd64_test.go", "internal/jobs/native_linux_test.go", "internal/jobs/native_windows_amd64_test.go", "internal/jobs/types.go", "internal/jobs/types_test.go", "go.mod", "go.sum"}
	for _, p := range paths {
		if !validDigest(sources[p]) {
			return nil, errors.New("source-manifest-entry")
		}
	}
	return sources, nil
}
func checkSources(sources map[string]string) error {
	entries, err := os.ReadDir("internal/jobs")
	if err != nil {
		return err
	}
	for _, e := range entries {
		if e.IsDir() || sources["internal/jobs/"+e.Name()] == "" {
			return errors.New("unbound-source")
		}
	}
	for p, want := range sources {
		actual, err := digestPath(p)
		if err != nil || actual != want {
			return errors.New("source-drift")
		}
	}
	return nil
}
