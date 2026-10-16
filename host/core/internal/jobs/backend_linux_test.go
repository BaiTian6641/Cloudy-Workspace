//go:build linux

package jobs

import (
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"sync"
	"testing"
	"time"
)

// These doubles model coordinator boundaries only; they never launch a command,
// create/kill a cgroup, or qualify a real process descendant.
type fakeContainer struct {
	mu                                sync.Mutex
	kills, cleanups                   int
	killErr, errorEmpty, errorCleanup error
	onKill                            func()
}

func (*fakeContainer) fd() int { return -1 }
func (c *fakeContainer) kill() error {
	c.mu.Lock()
	c.kills++
	fn, e := c.onKill, c.killErr
	c.mu.Unlock()
	if fn != nil {
		fn()
	}
	return e
}
func (c *fakeContainer) empty() (bool, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.errorEmpty == nil, c.errorEmpty
}
func (c *fakeContainer) cleanup() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.cleanups++
	return c.errorCleanup
}

type fakeProcess struct {
	done   chan struct{}
	mu     sync.Mutex
	waits  int
	result waitResult
}

func (p *fakeProcess) wait() waitResult {
	p.mu.Lock()
	p.waits++
	p.mu.Unlock()
	<-p.done
	return p.result
}
func testObserver(t *testing.T) context.Context {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	t.Cleanup(cancel)
	return ctx
}
func TestCoordinatorNonzeroReapedAndSingleRetirement(t *testing.T) {
	p := &fakeProcess{done: make(chan struct{}), result: waitResult{reaped: true, exitCode: 3}}
	close(p.done)
	c := &fakeContainer{}
	j := newJob(c)
	go j.run(Spec{}, func(Spec, containment, io.Writer, io.Writer) (process, error) { return p, nil })
	s, e := j.Wait(testObserver(t))
	if e != nil || s.Outcome != "exited" || !s.Reaped || s.ExitCode != 3 || s.Retirement != "confirmed" {
		t.Fatal("wrong result", s, e)
	}
	j.Cancel()
	s, e = j.Settle(testObserver(t))
	if e != nil || s.Outcome != "exited" {
		t.Fatal("stop trigger changed")
	}
	if c.kills != 1 || c.cleanups != 1 || p.waits != 1 {
		t.Fatal("repeated operation")
	}
}
func TestObserverTimeoutDoesNotCancelAndCancelRetires(t *testing.T) {
	p := &fakeProcess{done: make(chan struct{}), result: waitResult{reaped: true, exitCode: -1, signal: 9}}
	c := &fakeContainer{onKill: func() { close(p.done) }}
	j := newJob(c)
	go j.run(Spec{}, func(Spec, containment, io.Writer, io.Writer) (process, error) { return p, nil })
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	s, e := j.Wait(ctx)
	if e == nil || s.Outcome != "pending" {
		t.Fatal("observer canceled job")
	}
	j.Cancel()
	j.Cancel()
	s, e = j.Wait(testObserver(t))
	if e != nil || s.Outcome != "canceled" || s.Signal != 9 || s.Retirement != "confirmed" {
		t.Fatal("cancel did not retire", s, e)
	}
	if c.kills != 1 || p.waits != 1 {
		t.Fatal("cancel repeated")
	}
}
func TestRetirementFailureRetainsOwnerAndDoesNotRetryKill(t *testing.T) {
	p := &fakeProcess{done: make(chan struct{}), result: waitResult{reaped: true, exitCode: 0}}
	close(p.done)
	c := &fakeContainer{killErr: errors.New("private /path")}
	j := newJob(c)
	go j.run(Spec{}, func(Spec, containment, io.Writer, io.Writer) (process, error) { return p, nil })
	s, e := j.Wait(testObserver(t))
	if e != nil || s.Retirement != "uncertain" || j.container != c || s.RetirementFailure.Error() != "job-retirement-uncertain:kill" {
		t.Fatal("lost owner", s, e)
	}
	_, e = j.Settle(testObserver(t))
	if e == nil || c.kills != 1 || c.cleanups != 0 {
		t.Fatal("retried kill or lost state")
	}
}
func TestSettlementCanContinueObservationWithoutRelaunch(t *testing.T) {
	p := &fakeProcess{done: make(chan struct{}), result: waitResult{reaped: true, exitCode: 0}}
	close(p.done)
	c := &fakeContainer{errorEmpty: errors.New("read failed")}
	j := newJob(c)
	go j.run(Spec{}, func(Spec, containment, io.Writer, io.Writer) (process, error) { return p, nil })
	s, _ := j.Wait(testObserver(t))
	if s.Retirement != "uncertain" {
		t.Fatal("query error accepted")
	}
	c.mu.Lock()
	c.errorEmpty = nil
	c.mu.Unlock()
	s, e := j.Settle(testObserver(t))
	if e != nil || s.Retirement != "confirmed" || s.RetirementFailure != nil || c.kills != 1 {
		t.Fatal("observation did not settle", s, e)
	}
}
func TestStartFailureAndCanceledStartRetainExplicitOwner(t *testing.T) {
	for _, canceled := range []bool{false, true} {
		c := &fakeContainer{}
		j := newJob(c)
		if canceled {
			j.Cancel()
		}
		go j.run(Spec{}, func(Spec, containment, io.Writer, io.Writer) (process, error) {
			return nil, errors.New("private spawn error")
		})
		s, e := j.Wait(testObserver(t))
		want := "start-failed"
		if canceled {
			want = "canceled"
		}
		if e != nil || s.Outcome != want || s.Started || !s.StartResolved || s.Retirement != "confirmed" {
			t.Fatal("wrong failed start", s, e)
		}
	}
}
func TestPreCanceledAndNonCgroupNeverSpawn(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	f, e := os.Open(t.TempDir())
	if e != nil {
		t.Fatal(e)
	}
	defer f.Close()
	if j, e := Start(ctx, f, validSpec(t)); e == nil || j != nil {
		t.Fatal("pre-canceled input admitted")
	}
	j, e := Start(context.Background(), f, validSpec(t))
	if e == nil || j == nil {
		t.Fatal("failed owned FD not reported")
	}
	s, e := j.Wait(testObserver(t))
	if e != nil || s.Started || s.Outcome != "rejected" || s.Retirement != "confirmed" {
		t.Fatal("noncgroup mutated/launched", s, e)
	}
}
func TestPopulatedParserRejectsAmbiguousEvidence(t *testing.T) {
	for _, v := range []string{"", "frozen 0", "populated 0\npopulated 1", "populated 00", "populated 2", "populated 0 extra"} {
		if _, err := parsePopulated([]byte(v)); err == nil {
			t.Fatal("bad membership accepted")
		}
	}
	for _, v := range []string{"populated 0\nfrozen 0\n", "populated 1\n"} {
		empty, err := parsePopulated([]byte(v))
		if err != nil || empty != (v[10] == '0') {
			t.Fatal("valid membership rejected")
		}
	}
}

func TestPendingStartKeepsOwnerAcrossBoundedSettlement(t *testing.T) {
	release := make(chan struct{})
	c := &fakeContainer{}
	j := newJob(c)
	go j.run(Spec{}, func(Spec, containment, io.Writer, io.Writer) (process, error) {
		<-release
		return nil, errors.New("late start failure")
	})
	j.Cancel()
	observer, cancel := context.WithTimeout(context.Background(), time.Millisecond)
	s, err := j.Settle(observer)
	cancel()
	if err == nil || s.Retirement != "uncertain" || s.StartResolved || j.container != c {
		t.Fatal("pending start lost owner")
	}
	close(release)
	s, err = j.Wait(testObserver(t))
	if err != nil || s.Retirement != "confirmed" || s.Outcome != "canceled" {
		t.Fatal("late start did not settle", s, err)
	}
}

func TestIOAssessmentDoesNotInferDrainFromExit(t *testing.T) {
	for _, c := range []struct {
		err  error
		want string
	}{
		{nil, "complete"}, {&exec.ExitError{}, "not-assessed"}, {errors.Join(&exec.ExitError{}, exec.ErrWaitDelay), "not-assessed"}, {exec.ErrWaitDelay, "incomplete"}, {errors.New("copy failed"), "unknown"},
	} {
		if got := assessIO(c.err); got != c.want {
			t.Fatalf("assessment %s want %s", got, c.want)
		}
	}
}
