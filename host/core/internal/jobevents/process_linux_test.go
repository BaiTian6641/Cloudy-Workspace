//go:build linux

package jobevents

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"testing"
	"time"

	bolt "go.etcd.io/bbolt"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

// Only a parent-started copy of this test executable enters the finite fixture.
// It opens a parent-owned temporary store; it launches no descendants/listeners.
func init() {
	mode := os.Getenv("CLOUDY_JOBEVENTS_HELPER_MODE")
	if mode == "" {
		return
	}
	if mode != "lock" && mode != "unacknowledged" {
		os.Exit(20)
	}
	s, err := Open(os.Getenv("CLOUDY_JOBEVENTS_HELPER_DIR"), installationID)
	if err != nil {
		if s != nil {
			_ = s.Close()
		}
		os.Exit(21)
	}
	if mode == "unacknowledged" {
		boot, e := bootidentity.Read()
		if e != nil {
			_ = s.Close()
			os.Exit(22)
		}
		_, e = s.AppendJobStatus(StatusInput{testUUID(900), testUUID(901), boot, 1, time.Date(2026, 10, 16, 12, 0, 0, 0, time.UTC), jobs.Snapshot{Outcome: "rejected", Retirement: "confirmed", IOCompletion: "pending", ExitCode: -1}})
		if e != nil {
			_ = s.Close()
			os.Exit(23)
		}
		// Transaction committed, but caller receipt is deliberately withheld.
		if _, e = os.Stdout.Write([]byte("COMMITTED-NO-ACK\n")); e != nil {
			_ = s.Close()
			os.Exit(24)
		}
	} else {
		if _, err = os.Stdout.Write([]byte("READY\n")); err != nil {
			_ = s.Close()
			os.Exit(24)
		}
	}
	var b [1]byte
	_, _ = os.Stdin.Read(b[:])
	if s.Close() != nil {
		os.Exit(25)
	}
	os.Exit(0)
}

type helperOwner struct {
	cmd    *exec.Cmd
	input  io.WriteCloser
	reader *bufio.Reader
	stderr bytes.Buffer
	cancel context.CancelFunc
	waited bool
}

func startHelper(t *testing.T, mode, dir string) *helperOwner {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	path, err := os.Executable()
	if err != nil {
		cancel()
		t.Fatal("test executable unavailable")
	}
	h := &helperOwner{cmd: exec.CommandContext(ctx, path), cancel: cancel}
	h.cmd.Env = append(os.Environ(), "CLOUDY_JOBEVENTS_HELPER_MODE="+mode, "CLOUDY_JOBEVENTS_HELPER_DIR="+dir, "GORACE=atexit_sleep_ms=0")
	h.input, err = h.cmd.StdinPipe()
	if err != nil {
		cancel()
		t.Fatal("helper input failed")
	}
	output, err := h.cmd.StdoutPipe()
	if err != nil {
		_ = h.input.Close()
		cancel()
		t.Fatal("helper output failed")
	}
	h.reader = bufio.NewReader(output)
	h.cmd.Stderr = &h.stderr
	if h.cmd.Start() != nil {
		_ = h.input.Close()
		cancel()
		t.Fatal("helper start failed")
	}
	t.Cleanup(func() {
		if !h.waited {
			_ = h.cmd.Process.Kill()
			_ = h.input.Close()
			_ = h.cmd.Wait()
			h.waited = true
		}
		h.cancel()
	})
	ready := make(chan string, 1)
	go func() { line, _ := h.reader.ReadString('\n'); ready <- line }()
	want := "READY\n"
	if mode == "unacknowledged" {
		want = "COMMITTED-NO-ACK\n"
	}
	select {
	case line := <-ready:
		if line != want {
			t.Fatal("helper readiness failed")
		}
	case <-time.After(3 * time.Second):
		t.Fatal("helper readiness deadline")
	}
	return h
}
func (h *helperOwner) wait(t *testing.T, killed bool) {
	t.Helper()
	err := h.cmd.Wait()
	h.waited = true
	h.cancel()
	if killed {
		var exit *exec.ExitError
		if !errors.As(err, &exit) {
			t.Fatal("killed helper exit not observed")
		}
	} else if err != nil {
		t.Fatal("helper exit failed")
	}
	if h.stderr.Len() != 0 {
		t.Fatal("helper emitted unexpected stderr")
	}
}

func TestActualCompetingWriterLockTimeout(t *testing.T) {
	dir, s := fixture(t)
	if s.Close() != nil {
		t.Fatal("parent fixture close failed")
	}
	child := startHelper(t, "lock", dir)
	other, err := Open(dir, installationID)
	if other != nil {
		if e := other.Close(); e != nil {
			t.Fatal("partial owner close failed")
		}
	}
	failCode(t, err, "open")
	if !errors.Is(err.(*Failure).cause, bolt.ErrTimeout) {
		t.Fatal("competing writer did not use actual lock timeout")
	}
	if child.input.Close() != nil {
		t.Fatal("helper release failed")
	}
	child.wait(t, false)
	again, err := Open(dir, installationID)
	own(t, again)
	if err != nil {
		t.Fatal("store remained locked after helper close")
	}
}

func TestActualInterruptedUnacknowledgedCommit(t *testing.T) {
	dir, s := fixture(t)
	sid := stream(t, s)
	if s.Close() != nil {
		t.Fatal("parent fixture close failed")
	}
	child := startHelper(t, "unacknowledged", dir)
	if child.cmd.Process.Kill() != nil {
		t.Fatal("owned helper termination failed")
	}
	if child.input.Close() != nil {
		t.Fatal("owned input closure failed")
	}
	child.wait(t, true)
	again, err := Open(dir, installationID)
	own(t, again)
	if err != nil {
		t.Fatal("interrupted store reopen failed")
	}
	present, err := again.Lookup(testUUID(900))
	if err != nil || !present.Found || present.Receipt.Durability != "not-reassessed" {
		t.Fatal("unacknowledged event reconciliation failed")
	}
	page, err := again.ReadAfter(Cursor{sid, 0}, 1)
	if err != nil || page.ResetNeeded || len(page.Events) != 1 || page.Next.AfterSequence != 1 {
		t.Fatal("saved consumer cursor could not resume")
	}
	// Reconcile the exact same observation; do not execute/replay a job.
	boot, e := bootidentity.Read()
	if e != nil {
		t.Fatal("boot observation unavailable")
	}
	r, err := again.AppendJobStatus(StatusInput{testUUID(900), testUUID(901), boot, 1, time.Date(2026, 10, 16, 12, 0, 0, 0, time.UTC), jobs.Snapshot{Outcome: "rejected", Retirement: "confirmed", IOCompletion: "pending", ExitCode: -1}})
	if err != nil || !r.AlreadyObserved || r.Sequence != 1 || r.Durability != "not-reassessed" {
		t.Fatal("reconciliation repeated event or durability claim")
	}
}
