// Package jobs contains the finite local-job construction profile. It grants no
// remote authority, restart/replay semantics or general-purpose sandbox claim.
package jobs

import (
	"bytes"
	"context"
	"io"
	"math"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const (
	MaxArgs        = 64
	MaxArgBytes    = 4096
	MaxVectorBytes = 32768
	MaxInputBytes  = 65536
	MaxOutputBytes = 65536
	MaxRuntime     = 30 * time.Second
	TeardownBudget = 5 * time.Second
)

type Spec struct {
	Executable string
	Args       []string
	Cwd        string
	Env        []string // Non-nil and exact; nil is rejected rather than inheriting ambient values.
	Input      []byte
	Runtime    time.Duration
}

// Failure retains raw causes internally only. Error has no path/data formatting
// and no Unwrap method: arbitrary OS errors cannot escape through this boundary.
type Failure struct {
	Code, Stage string
	cause       error
}

func (f *Failure) Error() string                       { return "job-" + f.Code + ":" + f.Stage }
func failure(code, stage string, cause error) *Failure { return &Failure{code, stage, cause} }

type Output struct {
	Bytes                []byte
	Total                int64
	Truncated, Saturated bool
}
type Snapshot struct {
	Outcome                        string // pending, rejected, start-failed, exited, canceled, timed-out
	Started, StartResolved, Reaped bool
	ExitCode                       int // Valid only when Reaped; -1 for signaled termination.
	Signal                         int
	Retirement                     string // pending, uncertain, confirmed
	Failure                        *Failure
	RetirementFailure              *Failure
	Stdout, Stderr                 Output
	IOCompletion                   string // pending, complete, not-assessed, incomplete, unknown
}

type collector struct {
	mu     sync.Mutex
	output Output
}

func (c *collector) Write(p []byte) (int, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	n := len(p)
	if int64(n) > math.MaxInt64-c.output.Total {
		c.output.Total = math.MaxInt64
		c.output.Saturated = true
	} else {
		c.output.Total += int64(n)
	}
	available := MaxOutputBytes - len(c.output.Bytes)
	if available > n {
		available = n
	}
	c.output.Bytes = append(c.output.Bytes, p[:available]...)
	if available < n {
		c.output.Truncated = true
	}
	return n, nil // Keep draining beyond the retained cap; never block on a caller callback.
}
func (c *collector) snapshot() Output {
	c.mu.Lock()
	defer c.mu.Unlock()
	out := c.output
	out.Bytes = bytes.Clone(out.Bytes)
	return out
}

func validate(s Spec) (Spec, error) {
	bad := func(stage string) (Spec, error) { return Spec{}, failure("invalid-spec", stage, nil) }
	if !filepath.IsAbs(s.Executable) || strings.ContainsRune(s.Executable, 0) {
		return bad("executable")
	}
	if !filepath.IsAbs(s.Cwd) || strings.ContainsRune(s.Cwd, 0) {
		return bad("cwd")
	}
	if s.Env == nil || len(s.Env) > 64 || len(s.Args) > MaxArgs || len(s.Input) > MaxInputBytes || s.Runtime < time.Millisecond || s.Runtime > MaxRuntime {
		return bad("limits")
	}
	total := 0
	for _, arg := range s.Args {
		if strings.ContainsRune(arg, 0) || len(arg) > MaxArgBytes {
			return bad("argv")
		}
		total += len(arg)
		if total > MaxVectorBytes {
			return bad("argv")
		}
	}
	total = 0
	keys := map[string]bool{}
	for _, entry := range s.Env {
		key, _, ok := strings.Cut(entry, "=")
		if !ok || key == "" || strings.ContainsRune(entry, 0) || keys[key] {
			return bad("environment")
		}
		for i, c := range key {
			if !(c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || i > 0 && c >= '0' && c <= '9') {
				return bad("environment")
			}
		}
		keys[key] = true
		total += len(entry)
		if total > MaxVectorBytes {
			return bad("environment")
		}
	}
	info, err := os.Stat(s.Executable)
	if err != nil || !info.Mode().IsRegular() {
		return Spec{}, failure("invalid-spec", "executable", err)
	}
	info, err = os.Stat(s.Cwd)
	if err != nil || !info.IsDir() {
		return Spec{}, failure("invalid-spec", "cwd", err)
	}
	// Validation-time Stat is not executable confinement against trusted-path replacement.
	s.Args = append([]string{}, s.Args...)
	s.Env = append([]string{}, s.Env...)
	s.Input = bytes.Clone(s.Input)
	return s, nil
}

// Job is the explicit owner even when an observer returns uncertainty. It must
// remain reachable until settlement or parent-owned recovery; there is no global
// registry and no finalizer that silently abandons or retries an operation.
type Job struct {
	mu                           sync.Mutex
	state                        Snapshot
	stdout, stderr               collector
	stop                         chan struct{}
	report                       chan struct{}
	startDone, waitDone          chan struct{}
	reportOnce                   sync.Once
	gate                         chan struct{}
	container                    containment
	killAttempted                bool
	killError                    error
	cleanupStarted               bool
	releaseContext               func() bool
	runtimeTimer, uncertainTimer *time.Timer
}

type containment interface {
	fd() int
	kill() error
	empty() (bool, error)
	cleanup() error
}

type process interface{ wait() waitResult }
type waitResult struct {
	reaped           bool
	exitCode, signal int
	ioCompletion     string
	err              error
}
type starter func(Spec, containment, io.Writer, io.Writer) (process, error)

func newJob(c containment) *Job {
	return &Job{state: Snapshot{Outcome: "pending", Retirement: "pending", ExitCode: -1, IOCompletion: "pending"}, container: c, stop: make(chan struct{}), report: make(chan struct{}), startDone: make(chan struct{}), waitDone: make(chan struct{}), gate: make(chan struct{}, 1)}
}
func (j *Job) snapshot() Snapshot {
	j.mu.Lock()
	s := j.state
	if s.Failure != nil {
		f := *s.Failure
		s.Failure = &f
	}
	if s.RetirementFailure != nil {
		f := *s.RetirementFailure
		s.RetirementFailure = &f
	}
	j.mu.Unlock()
	s.Stdout = j.stdout.snapshot()
	s.Stderr = j.stderr.snapshot()
	return s
}
func (j *Job) finishReport() {
	j.reportOnce.Do(func() {
		j.mu.Lock()
		if j.runtimeTimer != nil {
			j.runtimeTimer.Stop()
		}
		if j.uncertainTimer != nil {
			j.uncertainTimer.Stop()
		}
		if j.releaseContext != nil {
			j.releaseContext()
		}
		j.mu.Unlock()
		close(j.report)
	})
}
func (j *Job) commit(outcome string) {
	j.mu.Lock()
	defer j.mu.Unlock()
	if j.state.Outcome != "pending" {
		return
	}
	j.state.Outcome = outcome
	close(j.stop)
	// A blocked kernel Start/Wait remains explicitly owned. The observer gets a
	// bounded uncertainty report; it cannot turn that into an invented retirement.
	j.uncertainTimer = time.AfterFunc(TeardownBudget, func() {
		j.mu.Lock()
		if j.state.Retirement != "confirmed" {
			j.state.Retirement = "uncertain"
		}
		j.mu.Unlock()
		j.finishReport()
	})
}
func (j *Job) Cancel() { j.commit("canceled") }
func (j *Job) Wait(observer context.Context) (Snapshot, error) {
	if observer == nil {
		return j.snapshot(), failure("invalid-observer", "wait", nil)
	}
	select {
	case <-j.report:
		return j.snapshot(), nil
	case <-observer.Done():
		return j.snapshot(), failure("observation-ended", "wait", observer.Err())
	}
}

// Settle never starts another command or repeats a cgroup kill. It continues
// bounded observation/owned cleanup after a committed stop. Observer cancellation
// alone has no effect on job lifetime. A failed kill remains a safe uncertainty.
func (j *Job) Settle(observer context.Context) (Snapshot, error) {
	if observer == nil {
		return j.snapshot(), failure("invalid-observer", "settle", nil)
	}
	select {
	case <-j.stop:
	default:
		return j.snapshot(), failure("still-running", "settle", nil)
	}
	ctx, cancel := context.WithTimeout(observer, TeardownBudget)
	defer cancel()
	select {
	case <-j.startDone:
	case <-ctx.Done():
		return j.uncertain("start-pending", ctx.Err())
	}
	select {
	case j.gate <- struct{}{}:
		defer func() { <-j.gate }()
	case <-ctx.Done():
		return j.snapshot(), failure("observation-ended", "settle", ctx.Err())
	}
	if j.snapshot().Retirement == "confirmed" {
		return j.snapshot(), nil
	}
	if !j.cleanupStarted {
		if !j.killAttempted {
			j.killAttempted = true
			j.killError = j.container.kill()
		}
		if j.killError != nil {
			return j.uncertain("kill", j.killError)
		}
		select {
		case <-j.waitDone:
		case <-ctx.Done():
			return j.uncertain("reap-pending", ctx.Err())
		}
		s := j.snapshot()
		if s.Started && !s.Reaped {
			return j.uncertain("reap", nil)
		}
		ticker := time.NewTicker(20 * time.Millisecond)
		defer ticker.Stop()
		for {
			empty, err := j.container.empty()
			if err != nil {
				return j.uncertain("membership", err)
			}
			if empty {
				break
			}
			select {
			case <-ctx.Done():
				return j.uncertain("populated", ctx.Err())
			case <-ticker.C:
			}
		}
		j.cleanupStarted = true
	}
	if err := j.container.cleanup(); err != nil {
		return j.uncertain("cleanup", err)
	}
	j.mu.Lock()
	j.state.Retirement = "confirmed"
	j.state.RetirementFailure = nil
	if j.uncertainTimer != nil {
		j.uncertainTimer.Stop()
	}
	if j.runtimeTimer != nil {
		j.runtimeTimer.Stop()
	}
	if j.releaseContext != nil {
		j.releaseContext()
	}
	j.mu.Unlock()
	j.finishReport()
	return j.snapshot(), nil
}
func (j *Job) uncertain(stage string, cause error) (Snapshot, error) {
	e := failure("retirement-uncertain", stage, cause)
	j.mu.Lock()
	if j.state.Retirement != "confirmed" {
		j.state.Retirement = "uncertain"
		j.state.RetirementFailure = e
	}
	j.mu.Unlock()
	return j.snapshot(), e
}

func (j *Job) run(s Spec, start starter) {
	p, err := start(s, j.container, &j.stdout, &j.stderr)
	j.mu.Lock()
	j.state.StartResolved = true
	j.state.Started = err == nil
	if err != nil {
		j.state.Failure = failure("start-failed", "spawn", err)
	}
	j.mu.Unlock()
	close(j.startDone)
	if err != nil {
		j.commit("start-failed")
		close(j.waitDone)
	} else {
		go func() {
			r := p.wait()
			j.mu.Lock()
			j.state.Reaped = r.reaped
			j.state.ExitCode = r.exitCode
			j.state.Signal = r.signal
			j.state.IOCompletion = r.ioCompletion
			if r.err != nil {
				j.state.Failure = failure("wait-result", "wait", r.err)
			}
			j.mu.Unlock()
			j.commit("exited")
			close(j.waitDone)
		}()
	}
	<-j.stop
	_, _ = j.Settle(context.Background())
	j.finishReport()
}
