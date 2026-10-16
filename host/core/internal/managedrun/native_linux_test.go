//go:build linux

package managedrun

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
	"syscall"
	"testing"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

// Inert until the parent verifies this exact binary and a finite independent
// owned-unit envelope. No provisioning, service command or guessed-PID kill.
func TestNativeManagedRun(t *testing.T) {
	if os.Getenv("CLOUDY_MANAGEDRUN_NATIVE_ACK") != "owned-managedrun-v1" {
		t.Skip("parent-reviewed native envelope not selected")
	}
	binary, e := os.Executable()
	if e != nil {
		t.Fatal("fixture binary unavailable")
	}
	file, e := os.Open(binary)
	if e != nil {
		t.Fatal("fixture binary open failed")
	}
	digest := sha256.New()
	_, readErr := io.Copy(digest, file)
	closeErr := file.Close()
	if readErr != nil || closeErr != nil || hex.EncodeToString(digest.Sum(nil)) != os.Getenv("CLOUDY_MANAGEDRUN_BINARY_SHA256") {
		t.Fatal("frozen fixture binary mismatch")
	}
	path := os.Getenv("CLOUDY_MANAGEDRUN_DELEGATION")
	if !filepath.IsAbs(path) || filepath.Clean(path) != path || !strings.HasPrefix(path, "/sys/fs/cgroup/user.slice/") {
		t.Fatal("exact owned delegation required")
	}
	canonical, e := filepath.EvalSymlinks(path)
	if e != nil || canonical != path {
		t.Fatal("aliased delegation refused")
	}
	delegation, e := os.Open(path)
	if e != nil {
		t.Fatal("owned delegation unavailable")
	}
	delegationCloseAttempted := false
	closeDelegation := func() {
		if delegationCloseAttempted {
			return
		}
		delegationCloseAttempted = true
		if e := delegation.Close(); e != nil {
			t.Error("fixture delegation close uncertain")
		}
	}
	defer closeDelegation()
	// The parent supplies an existing private evidence parent. Neither this
	// explicit child nor its contents are registered with testing cleanup.
	fixtureParent := os.Getenv("CLOUDY_MANAGEDRUN_FIXTURE_PARENT")
	if !filepath.IsAbs(fixtureParent) || filepath.Clean(fixtureParent) != fixtureParent {
		t.Fatal("explicit private fixture parent required")
	}
	parentInfo, e := os.Lstat(fixtureParent)
	if e != nil || !parentInfo.IsDir() || parentInfo.Mode()&os.ModeSymlink != 0 || parentInfo.Mode().Perm() != 0700 {
		t.Fatal("private fixture parent invalid")
	}
	parentStat, ok := parentInfo.Sys().(*syscall.Stat_t)
	if !ok || parentStat.Uid != uint32(os.Geteuid()) {
		t.Fatal("fixture parent owner differs")
	}
	parentCanonical, e := filepath.EvalSymlinks(fixtureParent)
	if e != nil || parentCanonical != fixtureParent {
		t.Fatal("aliased fixture parent refused")
	}
	fixtureRoot, e := os.MkdirTemp(fixtureParent, "managedrun-")
	if e != nil {
		t.Fatal("owned fixture creation failed")
	}
	rootInfo, e := os.Lstat(fixtureRoot)
	if e != nil {
		t.Fatal("owned fixture observation failed; preserve parent evidence")
	}
	stateDir, cwd := filepath.Join(fixtureRoot, "state"), filepath.Join(fixtureRoot, "cwd")
	reportPath := filepath.Join(fixtureParent, "managedrun-fixture.json")
	report, e := os.OpenFile(reportPath, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if e != nil {
		t.Fatal("fixture evidence report unavailable; preserve owned parent")
	}
	rawReport, e := json.Marshal(struct{ Root, State, Cwd string }{fixtureRoot, stateDir, cwd})
	if e != nil {
		if closeErr := report.Close(); closeErr != nil {
			t.Error("fixture report close uncertain")
		}
		t.Fatal("fixture report encoding failed")
	}
	n, writeErr := report.Write(rawReport)
	syncErr := report.Sync()
	reportCloseErr := report.Close()
	if writeErr != nil || n != len(rawReport) || syncErr != nil || reportCloseErr != nil {
		t.Fatal("fixture report write/close uncertain; preserve parent")
	}
	for _, dir := range []string{stateDir, cwd} {
		if e := os.Mkdir(dir, 0700); e != nil {
			t.Fatal("owned fixture directory creation failed")
		}
	}
	var owner *Run
	var store *jobevents.Store
	defer func() {
		// References remain reachable only through this function's deferred
		// calls. They do not survive return or process exit. The independent
		// finite parent supervisor owns containment/recovery after test failure.
		defer runtime.KeepAlive(owner)
		defer runtime.KeepAlive(store)
		if owner != nil && !owner.Retired() {
			t.Error("uncertain retirement; preserve fixture for parent recovery")
			return
		}
		if store != nil {
			if e := store.Close(); e != nil {
				t.Error("real store close uncertain; preserve fixture")
				return
			}
		}
		// The original test descriptor also closes before any evidence is
		// removed. A failed Close is attempted once and preserves the fixture.
		closeDelegation()
		if t.Failed() || owner == nil {
			return
		}
		current, e := os.Lstat(fixtureRoot)
		if e != nil || !current.IsDir() || !os.SameFile(rootInfo, current) {
			t.Error("owned fixture identity changed; preserve evidence")
			return
		}
		// Cleanup starts only after confirmed real retirement, checked Close
		// and all assertions pass. Failure retains report and remaining paths.
		if e := os.RemoveAll(fixtureRoot); e != nil {
			t.Error("owned fixture cleanup incomplete")
			return
		}
		if e := os.Remove(reportPath); e != nil {
			t.Error("fixture report cleanup incomplete")
		}
	}()
	store, e = jobevents.Init(stateDir, installationID)
	if e != nil {
		t.Fatal("real store initialization failed; preserve fixture")
	}
	spec := jobs.Spec{Executable: binary, Args: []string{"-test.run=^TestManagedRunFixtureChild$"}, Cwd: cwd, Env: []string{"CLOUDY_MANAGEDRUN_CHILD=owned-hold-v1", "CLOUDY_MANAGEDRUN_PARENT=" + strings.TrimPrefix(path, "/sys/fs/cgroup")}, Runtime: 4 * time.Second}
	owner, e = Start(context.Background(), delegation, store, spec)
	if e != nil || owner == nil {
		t.Fatal("native admission failed; preserve any returned owner")
	}
	group := ""
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		ctx, cancel := context.WithTimeout(context.Background(), 40*time.Millisecond)
		snapshot, waitErr := owner.job.Wait(ctx)
		cancel()
		if snapshot.Started && !snapshot.Reaped && snapshot.Outcome == "pending" && bytes.Contains(snapshot.Stdout.Bytes, []byte("READY\n")) {
			var found []string
			for _, line := range strings.Split(string(snapshot.Stdout.Bytes), "\n") {
				if strings.HasPrefix(line, "GROUP=") {
					found = append(found, strings.TrimPrefix(line, "GROUP="))
				}
			}
			if len(found) != 1 || !regexp.MustCompile(`^cloudy-job-[0-9a-f]{32}$`).MatchString(found[0]) {
				t.Fatal("actual child membership missing")
			}
			group = filepath.Join(path, found[0])
			break
		}
		if waitErr == nil || snapshot.Outcome != "pending" {
			t.Fatal("fixture ended before readiness")
		}
	}
	if group == "" {
		t.Fatal("fixture readiness deadline exceeded; retain original owner")
	}
	observer, cancel := context.WithTimeout(context.Background(), time.Millisecond)
	first, e := owner.WaitAndRecord(observer)
	cancel()
	if e == nil || !first.Captured || !first.Recorded || !first.CaptureFailed || first.Retired {
		t.Fatal("pending observation/lifetime classification differs")
	}
	retry, e := owner.RetryWaitAppend()
	if e == nil {
		t.Fatal("observer failure silently erased by append retry")
	}
	if !retry.Recorded || !retry.Receipt.AlreadyObserved || retry.Receipt.Sequence != first.Receipt.Sequence {
		t.Fatal("actual wait retry changed event")
	}
	if e := owner.Cancel(); e != nil {
		t.Fatal("actual cancel failed")
	}
	settlement, settleCancel := context.WithTimeout(context.Background(), 8*time.Second)
	terminal, e := owner.SettleAndRecord(settlement)
	settleCancel()
	if e != nil || !terminal.Captured || !terminal.Recorded || !terminal.Retired || !owner.Retired() {
		t.Fatal("actual settlement/recording failed; retain owner")
	}
	exact, e := owner.RetrySettleAppend()
	if e != nil || !exact.Receipt.AlreadyObserved || exact.Receipt.Sequence != terminal.Receipt.Sequence {
		t.Fatal("actual terminal retry changed event")
	}
	if _, e := os.Lstat(group); !os.IsNotExist(e) {
		t.Fatal("original owned child absence not established")
	}
	reset, e := store.ReadAfter(jobevents.Cursor{}, 1)
	if e != nil || !reset.ResetNeeded || len(reset.Snapshot) != 1 {
		t.Fatal("actual catalog reset failed")
	}
	history, e := store.ReadAfter(jobevents.Cursor{StreamID: reset.Next.StreamID}, 8)
	if e != nil || len(history.Events) != 2 || history.HasMore {
		t.Fatal("actual two-observation history differs")
	}
	ids := owner.IDs()
	waitEvent, settledEvent := history.Events[0], history.Events[1]
	if waitEvent.EventID != ids.WaitEventID || settledEvent.EventID != ids.SettleEventID || waitEvent.ResourceID != ids.JobID || settledEvent.ResourceID != ids.JobID || waitEvent.Payload.ObservationRevision != 1 || settledEvent.Payload.ObservationRevision != 2 {
		t.Fatal("frozen identities/revisions changed")
	}
	p := settledEvent.Payload
	if p.Outcome != "canceled" || !p.StartResolved || !p.Started || !p.Reaped || p.Retirement != "confirmed" {
		t.Fatal("actual terminal payload differs")
	}
	raw, e := json.Marshal(history.Events)
	if e != nil || bytes.Contains(raw, []byte("READY")) || bytes.Contains(raw, []byte("GROUP=")) {
		t.Fatal("fixture output reached event persistence")
	}
}

// Only this explicit finite test role can execute. It verifies creation-time
// membership in one fresh child of the exact parent-provided delegation.
func TestManagedRunFixtureChild(t *testing.T) {
	if os.Getenv("CLOUDY_MANAGEDRUN_CHILD") == "" {
		t.Skip("test-only child not selected")
	}
	if os.Getenv("CLOUDY_MANAGEDRUN_CHILD") != "owned-hold-v1" {
		os.Exit(90)
	}
	parent := os.Getenv("CLOUDY_MANAGEDRUN_PARENT")
	if !strings.HasPrefix(parent, "/user.slice/") || filepath.Clean(parent) != parent {
		os.Exit(91)
	}
	file, e := os.Open("/proc/self/cgroup")
	if e != nil {
		os.Exit(92)
	}
	raw, readErr := io.ReadAll(io.LimitReader(file, 65537))
	closeErr := file.Close()
	if readErr != nil || closeErr != nil || len(raw) > 65536 {
		os.Exit(93)
	}
	var groups []string
	for _, line := range strings.Split(string(raw), "\n") {
		if strings.HasPrefix(line, "0::") {
			groups = append(groups, strings.TrimPrefix(line, "0::"))
		}
	}
	if len(groups) != 1 || filepath.Dir(groups[0]) != parent || !regexp.MustCompile(`^cloudy-job-[0-9a-f]{32}$`).MatchString(filepath.Base(groups[0])) {
		os.Exit(94)
	}
	if _, e := fmt.Fprintf(os.Stdout, "GROUP=%s\nREADY\n", filepath.Base(groups[0])); e != nil {
		os.Exit(95)
	}
	time.Sleep(10 * time.Second)
	os.Exit(0)
}
