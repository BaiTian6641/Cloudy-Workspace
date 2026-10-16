//go:build linux

package jobs

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"
)

// This harness is inert unless the parent explicitly supplies its newly owned
// transient delegation. It never provisions systemd, moves existing processes,
// or falls back to a session/process group. Parent owns the finite outer guard.
func TestNativeOwnedJobs(t *testing.T) {
	if os.Getenv("CLOUDY_JOBS_NATIVE_ACK") != "owned-delegation-v1" {
		t.Skip("parent-owned native delegation fixture not selected")
	}
	path := os.Getenv("CLOUDY_JOBS_DELEGATION")
	if !filepath.IsAbs(path) {
		t.Fatal("exact parent-owned absolute delegation required")
	}
	delegation, err := os.Open(path)
	if err != nil {
		t.Fatal("owned delegation open failed")
	}
	defer delegation.Close()
	var owners []*Job
	// Keep every admitted owner reachable through the full test lifetime. On any
	// failure the entire harness stops; the parent's finite outer unit retains
	// containment authority across test-process exit. No later case is launched.
	defer func() { runtime.KeepAlive(owners) }()
	if !t.Run("missing-capability", func(t *testing.T) {
		ordinary, err := os.Open(t.TempDir())
		if err != nil {
			t.Fatal("ordinary fixture open failed")
		}
		defer ordinary.Close()
		j, err := Start(context.Background(), ordinary, validSpec(t))
		if j != nil {
			owners = append(owners, j)
		}
		if err == nil || j == nil {
			t.Fatal("missing capability not refused")
		}
		observer, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		s, e := j.Wait(observer)
		if e != nil || s.Started || s.Outcome != "rejected" || s.Retirement != "confirmed" {
			t.Fatal("missing-capability effects uncertain")
		}
	}) {
		t.Fatal("stop after first failed native case; parent outer guard owns recovery")
	}
	exe, err := os.Executable()
	if err != nil {
		t.Fatal("fixture executable unavailable")
	}
	for _, role := range []string{"exit0", "exit3", "pressure", "descendant", "deadline", "cancel"} {
		if !t.Run(role, func(t *testing.T) {
			runtime := 2 * time.Second
			if role == "deadline" {
				runtime = 100 * time.Millisecond
			}
			input := []byte(nil)
			if role == "pressure" {
				input = bytes.Repeat([]byte("i"), MaxInputBytes)
			}
			spec := Spec{Executable: exe, Args: []string{"-test.run=^TestJobsFixtureProcess$"}, Cwd: t.TempDir(), Env: []string{"CLOUDY_JOBS_FIXTURE=" + role}, Input: input, Runtime: runtime}
			j, err := Start(context.Background(), delegation, spec)
			if j != nil {
				owners = append(owners, j)
			}
			if err != nil || j == nil {
				t.Fatal("native admission failed; preserve owner/delegation")
			}
			if role == "cancel" {
				observer, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
				s, e := j.Wait(observer)
				cancel()
				if e == nil || s.Outcome != "pending" {
					t.Fatal("observer timeout changed lifetime")
				}
				j.Cancel()
			}
			observer, cancel := context.WithTimeout(context.Background(), 8*time.Second)
			defer cancel()
			s, err := j.Wait(observer)
			if err != nil || !s.Started || !s.Reaped || s.Retirement != "confirmed" {
				t.Fatal("native job not confirmed retired; parent recovery required")
			}
			group := j.container.(*childGroup)
			if !bytes.Contains(s.Stdout.Bytes, []byte("GROUP="+group.name+"\n")) {
				t.Fatal("fixture did not observe owned creation-time membership")
			}
			want := "exited"
			if role == "cancel" {
				want = "canceled"
			}
			if role == "deadline" {
				want = "timed-out"
			}
			if s.Outcome != want {
				t.Fatal("unexpected committed outcome")
			}
			if role == "exit3" && s.ExitCode != 3 {
				t.Fatal("nonzero status lost")
			}
			if (role == "exit0" || role == "pressure" || role == "descendant") && s.ExitCode != 0 {
				t.Fatal("fixture exit failed")
			}
			if role == "pressure" && (!s.Stdout.Truncated || !s.Stderr.Truncated || s.Stdout.Total < 70000 || s.Stderr.Total != 70000 || !bytes.Contains(s.Stdout.Bytes, []byte("INPUT=65536\n"))) {
				t.Fatal("bounded bidirectional pressure evidence missing")
			}
			if role == "descendant" && !bytes.Contains(s.Stdout.Bytes, []byte("DESCENDANT\n")) {
				t.Fatal("grandchild fixture did not start")
			}
			// Cleanup closes only the owned child/duplicated parent and removes the fresh
			// empty child. The original caller delegation remains valid and unmodified.
			if _, err := delegation.Stat(); err != nil {
				t.Fatal("caller delegation handle consumed")
			}
		}) {
			t.Fatal("stop after first failed native case; parent outer guard owns recovery")
		}
	}
}

// Test-only actual executable fixture. It is not a product backend/test double.
// Only the parent-gated native harness launches it inside its owned cgroup.
func TestJobsFixtureProcess(t *testing.T) {
	role := os.Getenv("CLOUDY_JOBS_FIXTURE")
	if role == "" {
		t.Skip("test-only subprocess fixture not selected")
	}
	data, err := os.ReadFile("/proc/self/cgroup")
	if err != nil {
		os.Exit(91)
	}
	var group string
	for _, line := range strings.Split(string(data), "\n") {
		if strings.HasPrefix(line, "0::") {
			group = filepath.Base(strings.TrimPrefix(line, "0::"))
		}
	}
	if group == "" {
		os.Exit(92)
	}
	fmt.Printf("GROUP=%s\n", group)
	switch role {
	case "exit0":
		os.Exit(0)
	case "exit3":
		os.Exit(3)
	case "pressure":
		b, err := io.ReadAll(io.LimitReader(os.Stdin, MaxInputBytes+1))
		if err != nil {
			os.Exit(93)
		}
		fmt.Printf("INPUT=%d\n", len(b))
		_, _ = os.Stdout.Write(bytes.Repeat([]byte("o"), 70000))
		_, _ = os.Stderr.Write(bytes.Repeat([]byte("e"), 70000))
		os.Exit(0)
	case "descendant":
		exe, err := os.Executable()
		if err != nil {
			os.Exit(94)
		}
		ready, signal, err := os.Pipe()
		if err != nil {
			os.Exit(95)
		}
		cmd := exec.Command(exe, "-test.run=^TestJobsFixtureProcess$")
		cmd.Env = []string{"CLOUDY_JOBS_FIXTURE=grandchild"}
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		cmd.ExtraFiles = []*os.File{signal}
		if cmd.Start() != nil {
			os.Exit(96)
		}
		_ = signal.Close()
		var ack [1]byte
		_ = ready.SetReadDeadline(time.Now().Add(time.Second))
		n, err := ready.Read(ack[:])
		_ = ready.Close()
		_ = cmd.Process.Release()
		if err != nil || n != 1 {
			os.Exit(97)
		}
		os.Exit(0)
	case "grandchild":
		fmt.Print("DESCENDANT\n")
		ready := os.NewFile(3, "fixture-ready")
		if ready == nil {
			os.Exit(98)
		}
		_, _ = ready.Write([]byte("1"))
		_ = ready.Close()
		// Hold the inherited stdout/stderr open after the direct parent exits.
		time.Sleep(20 * time.Second)
		os.Exit(0)
	case "deadline", "cancel":
		time.Sleep(20 * time.Second)
		os.Exit(0)
	default:
		os.Exit(99)
	}
}
