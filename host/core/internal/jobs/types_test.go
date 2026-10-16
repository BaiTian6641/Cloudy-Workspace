package jobs

import (
	"bytes"
	"math"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func validSpec(t *testing.T) Spec {
	t.Helper()
	exe, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	return Spec{Executable: exe, Cwd: t.TempDir(), Args: []string{"arg"}, Env: []string{}, Input: []byte("in"), Runtime: time.Second}
}
func TestValidateExactInputsAndCopies(t *testing.T) {
	s := validSpec(t)
	s.Env = []string{"A=value"}
	a, err := validate(s)
	if err != nil {
		t.Fatal(err)
	}
	s.Args[0] = "changed"
	s.Env[0] = "B=changed"
	s.Input[0] = 'x'
	if a.Args[0] != "arg" || a.Env[0] != "A=value" || string(a.Input) != "in" {
		t.Fatal("admitted input aliases caller")
	}
	empty, err := validate(validSpec(t))
	if err != nil || empty.Env == nil {
		t.Fatal("empty environment became ambient")
	}
}
func TestValidateRejectsBoundsAndAuthority(t *testing.T) {
	cases := map[string]func(*Spec){
		"path": func(s *Spec) { s.Executable = "tool" }, "cwd": func(s *Spec) { s.Cwd = "." }, "missing": func(s *Spec) { s.Executable = s.Cwd + "/missing" }, "directory": func(s *Spec) { s.Executable = s.Cwd },
		"nil-env": func(s *Spec) { s.Env = nil }, "duplicate-env": func(s *Spec) { s.Env = []string{"A=1", "A=2"} }, "invalid-env": func(s *Spec) { s.Env = []string{"1A=v"} }, "env-nul": func(s *Spec) { s.Env = []string{"A=\x00"} }, "env-noequal": func(s *Spec) { s.Env = []string{"A"} },
		"count-env": func(s *Spec) { s.Env = make([]string, 65) }, "large-env": func(s *Spec) { s.Env = []string{"A=" + strings.Repeat("x", MaxVectorBytes)} },
		"args-count": func(s *Spec) { s.Args = make([]string, 65) }, "args-size": func(s *Spec) { s.Args = []string{strings.Repeat("a", 4097)} }, "args-total": func(s *Spec) {
			s.Args = make([]string, 9)
			for i := range s.Args {
				s.Args[i] = strings.Repeat("a", 4096)
			}
		}, "arg-nul": func(s *Spec) { s.Args = []string{"x\x00"} },
		"input": func(s *Spec) { s.Input = make([]byte, MaxInputBytes+1) }, "runtime-min": func(s *Spec) { s.Runtime = time.Microsecond }, "runtime-max": func(s *Spec) { s.Runtime = MaxRuntime + 1 },
	}
	for name, change := range cases {
		t.Run(name, func(t *testing.T) {
			s := validSpec(t)
			change(&s)
			if _, err := validate(s); err == nil {
				t.Fatal("invalid admitted")
			}
		})
	}
}
func TestCollectorBoundsDrainAndSaturation(t *testing.T) {
	var c collector
	data := bytes.Repeat([]byte("x"), MaxOutputBytes+100)
	if n, err := c.Write(data); err != nil || n != len(data) {
		t.Fatal("did not drain")
	}
	s := c.snapshot()
	if len(s.Bytes) != MaxOutputBytes || s.Total != int64(len(data)) || !s.Truncated || s.Saturated {
		t.Fatal("wrong bounded result")
	}
	s.Bytes[0] = 'y'
	if c.snapshot().Bytes[0] != 'x' {
		t.Fatal("snapshot mutable alias")
	}
	c.mu.Lock()
	c.output.Total = math.MaxInt64 - 1
	c.mu.Unlock()
	_, _ = c.Write([]byte("xx"))
	if s = c.snapshot(); s.Total != math.MaxInt64 || !s.Saturated {
		t.Fatal("counter overflow")
	}
}
func TestCollectorConcurrentSnapshots(t *testing.T) {
	var c collector
	var wg sync.WaitGroup
	for range 4 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for range 100 {
				_, _ = c.Write([]byte("abcdefgh"))
				_ = c.snapshot()
			}
		}()
	}
	wg.Wait()
	if c.snapshot().Total != 3200 {
		t.Fatal("lost output")
	}
}
func TestFailureDoesNotExposeRawCause(t *testing.T) {
	e := failure("retirement-uncertain", "kill", os.ErrPermission)
	if e.Error() != "job-retirement-uncertain:kill" {
		t.Fatal("unsafe error")
	}
	if _, ok := any(e).(interface{ Unwrap() error }); ok {
		t.Fatal("raw cause exposed")
	}
}
