//go:build linux

package jobrecorder

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

// Inert by default. Parent verifies the exact finite outer unit and frozen binary
// before enabling this harness. No cgroup provisioning or guessed PID kill occurs here.
func TestNativeRecorderComposition(t *testing.T) {
	if os.Getenv("CLOUDY_RECORDER_NATIVE_ACK") != "owned-recorder-delegation-v1" {
		t.Skip("parent-owned recorder fixture not selected")
	}
	binary, err := os.Executable()
	if err != nil {
		t.Fatal("fixture binary unavailable")
	}
	file, err := os.Open(binary)
	if err != nil {
		t.Fatal("fixture binary open failed")
	}
	hash := sha256.New()
	_, readErr := io.Copy(hash, file)
	closeErr := file.Close()
	if readErr != nil || closeErr != nil || hex.EncodeToString(hash.Sum(nil)) != os.Getenv("CLOUDY_RECORDER_BINARY_SHA256") {
		t.Fatal("frozen fixture binary mismatch")
	}
	path := os.Getenv("CLOUDY_RECORDER_DELEGATION")
	if !filepath.IsAbs(path) || filepath.Clean(path) != path || !strings.HasPrefix(path, "/sys/fs/cgroup/user.slice/") {
		t.Fatal("exact owned delegation required")
	}
	canonical, err := filepath.EvalSymlinks(path)
	if err != nil || canonical != path {
		t.Fatal("aliased owned delegation refused")
	}
	delegation, err := os.Open(path)
	if err != nil {
		t.Fatal("owned delegation unavailable")
	}
	defer func() {
		if err := delegation.Close(); err != nil {
			t.Error("borrowed fixture delegation close failed")
		}
	}()
	var owners []*jobs.Job
	// Every nonnil owner remains reachable through failure. Parent's independent
	// finite outer unit retains containment if a subtest cannot establish retirement.
	defer func() { runtime.KeepAlive(owners) }()
	for _, test := range []struct {
		name string
		run  func(*testing.T, *os.File, *[]*jobs.Job)
	}{
		{"capture-wait", runNativeCaptureWait}, {"capture-settle", runNativeCaptureSettle},
	} {
		if !t.Run(test.name, func(t *testing.T) { test.run(t, delegation, &owners) }) {
			t.Fatal("stop after first failed native case; parent owns recovery")
		}
	}
}

func nativeFixture(t *testing.T, delegation *os.File, owners *[]*jobs.Job, role string) (*jobs.Job, *jobevents.Store, Metadata) {
	t.Helper()
	if role != "exit0" && role != "hold" {
		t.Fatal("invalid finite fixture role")
	}
	boot, err := bootidentity.Read()
	if err != nil {
		t.Fatal("actual boot read failed")
	}
	_, store := fixture(t) // Real owned temp DB; existing helper checks every nonnil Close.
	binary, err := os.Executable()
	if err != nil {
		t.Fatal("fixture binary unavailable")
	}
	group := strings.TrimPrefix(delegation.Name(), "/sys/fs/cgroup")
	spec := jobs.Spec{Executable: binary, Args: []string{"-test.run=^TestRecorderFixtureProcess$"}, Cwd: t.TempDir(),
		Env:     []string{"CLOUDY_RECORDER_CHILD=" + role, "CLOUDY_RECORDER_CHILD_ACK=owned-child-v1", "CLOUDY_RECORDER_EXPECTED_GROUP=" + group},
		Runtime: 4 * time.Second}
	job, err := jobs.Start(context.Background(), delegation, spec)
	if job != nil {
		*owners = append(*owners, job)
	}
	if err != nil || job == nil {
		t.Fatal("native job admission failed; retain owner")
	}
	return job, store, Metadata{EventID: event, JobID: jobID, Revision: 1, BootID: boot}
}

func nativeReady(t *testing.T, job *jobs.Job) jobs.Snapshot {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		ctx, cancel := context.WithTimeout(context.Background(), 40*time.Millisecond)
		snapshot, err := job.Wait(ctx)
		cancel()
		if snapshot.Started && !snapshot.Reaped && snapshot.Outcome == "pending" && bytes.Contains(snapshot.Stdout.Bytes, []byte("READY\n")) {
			return snapshot
		}
		if err == nil || snapshot.Outcome != "pending" {
			t.Fatal("fixture ended before actual readiness")
		}
	}
	t.Fatal("fixture readiness deadline exceeded; parent owns recovery")
	return jobs.Snapshot{}
}

func nativeGroup(t *testing.T, snapshot jobs.Snapshot, delegation *os.File) string {
	t.Helper()
	var found []string
	for _, line := range strings.Split(string(snapshot.Stdout.Bytes), "\n") {
		if strings.HasPrefix(line, "GROUP=") {
			found = append(found, strings.TrimPrefix(line, "GROUP="))
		}
	}
	if len(found) != 1 || !regexp.MustCompile(`^cloudy-job-[0-9a-f]{32}$`).MatchString(found[0]) {
		t.Fatal("actual child membership marker missing")
	}
	return filepath.Join(delegation.Name(), found[0])
}
func nativeGroupAbsent(t *testing.T, group string) {
	t.Helper()
	if _, err := os.Lstat(group); !os.IsNotExist(err) {
		t.Fatal("owned child retirement not observed")
	}
}

// The only executable fixture roles are finite. They verify actual creation-time
// child membership before emitting fixed noncredential markers. No descendants.
func TestRecorderFixtureProcess(t *testing.T) {
	role := os.Getenv("CLOUDY_RECORDER_CHILD")
	if role == "" {
		t.Skip("test-only owned child not selected")
	}
	if os.Getenv("CLOUDY_RECORDER_CHILD_ACK") != "owned-child-v1" || (role != "exit0" && role != "hold") {
		os.Exit(90)
	}
	parent := os.Getenv("CLOUDY_RECORDER_EXPECTED_GROUP")
	if !strings.HasPrefix(parent, "/user.slice/") || filepath.Clean(parent) != parent {
		os.Exit(91)
	}
	data, err := os.ReadFile("/proc/self/cgroup")
	if err != nil || len(data) > 65536 {
		os.Exit(92)
	}
	var groups []string
	for _, line := range strings.Split(string(data), "\n") {
		if strings.HasPrefix(line, "0::") {
			groups = append(groups, strings.TrimPrefix(line, "0::"))
		}
	}
	if len(groups) != 1 || filepath.Dir(groups[0]) != parent || !regexp.MustCompile(`^cloudy-job-[0-9a-f]{32}$`).MatchString(filepath.Base(groups[0])) {
		os.Exit(93)
	}
	if _, err := fmt.Fprintf(os.Stdout, "GROUP=%s\nREADY\n", filepath.Base(groups[0])); err != nil {
		os.Exit(94)
	}
	if role == "hold" {
		time.Sleep(10 * time.Second)
	}
	os.Exit(0)
}

// Fixed noncredential fixture markers may occur in Job stdout, never persisted status.
func nativeSanitized(t *testing.T, events []jobevents.Event) {
	t.Helper()
	raw, err := json.Marshal(events)
	if err != nil || bytes.Contains(raw, []byte("GROUP=")) || bytes.Contains(raw, []byte("READY")) {
		t.Fatal("fixture output leaked into status records")
	}
}
