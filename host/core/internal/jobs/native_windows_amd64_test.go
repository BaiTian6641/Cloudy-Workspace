//go:build windows && amd64

package jobs

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"reflect"
	"strconv"
	"strings"
	"testing"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

// TestNativeWindowsFixture is test-only executable behavior. It has no product
// hook and is never launched unless the parent opts into the finite native suite.
func TestNativeWindowsFixture(t *testing.T) {
	role := os.Getenv("CLOUDY_WINDOWS_JOB_FIXTURE")
	if role == "" && len(os.Args) > 3 && os.Args[3] == "cloudy-fixture-empty-environment" {
		if len(os.Environ()) != 0 {
			os.Exit(18)
		}
		fmt.Fprint(os.Stdout, "EMPTY-ENV")
		os.Exit(0)
	}
	if role == "" {
		t.Skip("test-only child entry")
	}
	switch role {
	case "echo":
		data, err := io.ReadAll(io.LimitReader(os.Stdin, MaxInputBytes+1))
		if err != nil {
			os.Exit(11)
		}
		if err = json.NewEncoder(os.Stdout).Encode(struct {
			Args, Env []string
			Input     string
		}{os.Args[3:], os.Environ(), string(data)}); err != nil {
			os.Exit(12)
		}
	case "hold":
		fmt.Fprint(os.Stdout, "READY")
		time.Sleep(20 * time.Second)
	case "exit259":
		os.Exit(259)
	case "pressure":
		_, _ = os.Stdout.Write(bytes.Repeat([]byte("x"), MaxOutputBytes+100))
		_, _ = os.Stderr.Write(bytes.Repeat([]byte("y"), MaxOutputBytes+100))
		// Deliberately never read stdin; parent must close/join its blocked writer.
		time.Sleep(20 * time.Second)
	case "descendant":
		exe, err := os.Executable()
		if err != nil {
			os.Exit(13)
		}
		cmd := exec.Command(exe, "-test.run=^TestNativeWindowsFixture$")
		cmd.Env = []string{"CLOUDY_WINDOWS_JOB_FIXTURE=hold", "SYSTEMROOT=" + os.Getenv("SYSTEMROOT")}
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if err = cmd.Start(); err != nil {
			os.Exit(14)
		}
		fmt.Fprint(os.Stdout, "SPAWNED")
	case "sentinel":
		raw, err := strconv.ParseUint(os.Getenv("CLOUDY_SENTINEL"), 10, 64)
		if err != nil {
			os.Exit(15)
		}
		// A reused numeric handle can conservatively fail this sentinel test.
		// Absence is never inferred from parent-only inspection.
		_, err = windows.WaitForSingleObject(windows.Handle(raw), 0)
		if err != windows.ERROR_INVALID_HANDLE {
			os.Exit(16)
		}
		fmt.Fprint(os.Stdout, "NOT-INHERITED")
	default:
		os.Exit(17)
	}
	os.Exit(0)
}

// Native qualification requires a separately reviewed finite parent-owned outer
// safety fixture. Ordinary go test never creates jobs/processes here. No native
// Windows result is implied by this suite cross-compiling on Linux.
func TestNativeWindowsOwnedJobs(t *testing.T) {
	if os.Getenv("CLOUDY_NATIVE_WINDOWS_JOBS") != "1" || os.Getenv("CLOUDY_NATIVE_WINDOWS_OWNER") == "" {
		t.Skip("explicit parent-owned native fixture required")
	}
	var owners []*Job
	t.Cleanup(func() {
		for _, j := range owners {
			j.Cancel()
			ctx, cancel := context.WithTimeout(context.Background(), TeardownBudget)
			_, _ = j.Settle(ctx)
			cancel()
		}
	})
	spec := func(role string) Spec {
		s := validSpec(t)
		s.Args = []string{"-test.run=^TestNativeWindowsFixture$", "--"}
		s.Input = nil
		s.Runtime = 3 * time.Second
		s.Env = []string{"CLOUDY_WINDOWS_JOB_FIXTURE=" + role, "SYSTEMROOT=" + os.Getenv("SYSTEMROOT")}
		return s
	}
	start := func(t *testing.T, s Spec) *Job {
		t.Helper()
		j, e := StartOwned(context.Background(), s)
		if j != nil {
			owners = append(owners, j)
		}
		if e != nil || j == nil {
			t.Fatal("native admission failed", e)
		}
		return j
	}
	observe := func(t *testing.T, j *Job) Snapshot {
		t.Helper()
		ctx, cancel := context.WithTimeout(context.Background(), 9*time.Second)
		defer cancel()
		s, e := j.Wait(ctx)
		if e != nil || s.Retirement != "confirmed" {
			t.Fatal("native owner not retired", s, e)
		}
		return s
	}
	ready := func(t *testing.T, j *Job, marker string) {
		t.Helper()
		deadline := time.Now().Add(time.Second)
		for time.Now().Before(deadline) {
			s := j.snapshot()
			if strings.Contains(string(s.Stdout.Bytes), marker) {
				return
			}
			if s.Outcome != "pending" {
				t.Fatal("child ended before readiness", s)
			}
			time.Sleep(5 * time.Millisecond)
		}
		t.Fatal("child readiness unobserved")
	}
	cases := []struct {
		name string
		run  func(*testing.T)
	}{
		{"exact-job-membership-and-cancel", func(t *testing.T) {
			j := start(t, spec("hold"))
			ready(t, j, "READY")
			select {
			case j.gate <- struct{}{}:
			case <-time.After(time.Second):
				t.Fatal("membership owner gate unavailable")
			}
			func() {
				defer func() { <-j.gate }()
				if j.snapshot().Outcome != "pending" {
					t.Fatal("membership observation expired")
				}
				c := j.container.(*windowsContainer)
				// Query while this exact process handle is retained and the controlled child
				// is alive. An inherited CI outer job cannot satisfy this specific hJob list.
				var list struct {
					Assigned, Listed uint32
					IDs              [16]uintptr
				}
				var n uint32
				err := windows.QueryInformationJobObject(c.job.value, windows.JobObjectBasicProcessIdList, uintptr(unsafe.Pointer(&list)), uint32(unsafe.Sizeof(list)), &n)
				if err != nil || list.Listed != 1 || list.IDs[0] != uintptr(c.pid) {
					t.Fatal("specific owned job membership unproved", err)
				}
				var creation, exit, kernel, user windows.Filetime
				if err = windows.GetProcessTimes(c.primary.value, &creation, &exit, &kernel, &user); err != nil || creation.HighDateTime == 0 && creation.LowDateTime == 0 {
					t.Fatal("exact handle creation time unproved", err)
				}
			}()
			j.Cancel()
			s := observe(t, j)
			if s.Outcome != "canceled" || !s.Started || !s.Reaped {
				t.Fatal(s)
			}
		}},
		{"unicode-argv-exact-environment-input", func(t *testing.T) {
			s := spec("echo")
			args := []string{"", `space here`, `quote"here`, `tail\`, "雪🙂"}
			s.Args = append(s.Args, args...)
			s.Env = append(s.Env, "CLOUDY_VALUE=雪🙂")
			s.Input = []byte("finite input")
			snap := observe(t, start(t, s))
			var got struct {
				Args, Env []string
				Input     string
			}
			if snap.ExitCode != 0 || snap.IOCompletion != "complete" || json.Unmarshal(snap.Stdout.Bytes, &got) != nil || !reflect.DeepEqual(got.Args, args) || got.Input != string(s.Input) {
				t.Fatal("real argv/input mismatch", snap)
			}
			expected := append([]string{}, s.Env...)
			for _, v := range expected {
				found := false
				for _, e := range got.Env {
					if e == v {
						found = true
					}
				}
				if !found {
					t.Fatal("explicit environment value missing")
				}
			}
			if len(got.Env) != len(expected) {
				t.Fatal("ambient environment leaked")
			}
		}},
		{"explicit-empty-environment", func(t *testing.T) {
			s := spec("echo")
			s.Env = []string{}
			s.Args = append(s.Args, "cloudy-fixture-empty-environment")
			snap := observe(t, start(t, s))
			if snap.ExitCode != 0 || string(snap.Stdout.Bytes) != "EMPTY-ENV" {
				t.Fatal("empty environment was not exact", snap)
			}
		}},
		{"exit259-is-real-exit", func(t *testing.T) {
			s := observe(t, start(t, spec("exit259")))
			if !s.Reaped || s.ExitCode != 259 || s.Signal != 0 || s.Outcome != "exited" {
				t.Fatal(s)
			}
		}},
		{"parent-exit-retires-pipe-holding-descendant", func(t *testing.T) {
			// SPAWNED proves successful descendant creation/handle inheritance,
			// not necessarily that its user code reached READY before retirement.
			began := time.Now()
			s := observe(t, start(t, spec("descendant")))
			if s.ExitCode != 0 || !strings.Contains(string(s.Stdout.Bytes), "SPAWNED") || time.Since(began) > 2*time.Second {
				t.Fatal("direct exit delayed by descendant pipe", s)
			}
		}},
		{"bounded-output-blocked-input-timeout", func(t *testing.T) {
			s := spec("pressure")
			s.Input = bytes.Repeat([]byte("z"), MaxInputBytes)
			s.Runtime = 500 * time.Millisecond
			snap := observe(t, start(t, s))
			if snap.Outcome != "timed-out" || !snap.Stdout.Truncated || !snap.Stderr.Truncated || len(snap.Stdout.Bytes) != MaxOutputBytes || len(snap.Stderr.Bytes) != MaxOutputBytes {
				t.Fatal(snap)
			}
		}},
		{"unlisted-inheritable-handle-excluded", func(t *testing.T) {
			sa := windows.SecurityAttributes{Length: uint32(unsafe.Sizeof(windows.SecurityAttributes{})), InheritHandle: 1}
			h, e := windows.CreateEvent(&sa, 0, 0, nil)
			if e != nil {
				t.Fatal(e)
			}
			defer func() {
				if e := windows.CloseHandle(h); e != nil {
					t.Error("sentinel close failed")
				}
			}()
			s := spec("sentinel")
			s.Env = append(s.Env, "CLOUDY_SENTINEL="+strconv.FormatUint(uint64(h), 10))
			snap := observe(t, start(t, s))
			if snap.ExitCode != 0 || string(snap.Stdout.Bytes) != "NOT-INHERITED" {
				t.Fatal("unlisted handle inherited", snap)
			}
		}},
	}
	for _, c := range cases {
		if !t.Run(c.name, c.run) {
			t.Fatal("native case failed; no later jobs launched, owners retained until outer fixture exits")
		}
	}
}
