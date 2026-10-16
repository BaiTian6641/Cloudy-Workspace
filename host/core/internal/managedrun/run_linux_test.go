//go:build linux

package managedrun

import (
	"context"
	"errors"
	"os"
	"reflect"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobrecorder"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

const installationID = "11111111-1111-4111-8111-111111111111"

var testIDs = []string{"22222222-2222-4222-8222-222222222222", "33333333-3333-4333-8333-333333333333", "44444444-4444-4444-8444-444444444444"}

type fixture struct {
	store                                        *jobevents.Store
	delegation                                   *os.File
	boot                                         bootidentity.ID
	a                                            admission
	op                                           operations
	started, waited, settled, canceled, appended int
	metadata                                     map[kind]jobrecorder.Metadata
	at                                           map[kind]time.Time
}

func newFixture(t *testing.T) *fixture {
	t.Helper()
	boot, e := bootidentity.Read()
	if e != nil {
		t.Fatal("actual boot observation unavailable")
	}
	store, e := jobevents.Init(t.TempDir(), installationID)
	if e != nil {
		if store != nil {
			_ = store.Close()
		}
		t.Fatal("real fixture store init failed")
	}
	t.Cleanup(func() {
		if e := store.Close(); e != nil {
			t.Error("real fixture store close failed")
		}
	})
	f, e := os.Open(t.TempDir())
	if e != nil {
		t.Fatal("fixture directory open failed")
	}
	t.Cleanup(func() {
		if e := f.Close(); e != nil {
			t.Error("fixture directory close failed")
		}
	})
	x := &fixture{store: store, delegation: f, boot: boot, metadata: make(map[kind]jobrecorder.Metadata), at: make(map[kind]time.Time)}
	next := 0
	x.a = admission{boot: func() (bootidentity.ID, error) { return boot, nil }, uuid: func() (string, error) { v := testIDs[next%len(testIDs)]; next++; return v, nil }, read: func(s *jobevents.Store) (jobevents.Batch, error) { return s.ReadAfter(jobevents.Cursor{}, 1) }, lookup: (*jobevents.Store).Lookup, start: func(context.Context, *os.File, jobs.Spec) (*jobs.Job, error) { x.started++; return &jobs.Job{}, nil }}
	// The zero Job and opaque Observation are test-only seams. They never run a
	// process or establish public recorder integration. Append writes synthetic
	// whitelisted fixture status through a REAL temporary store.
	capture := func(k kind) func(context.Context, *jobs.Job, jobrecorder.Metadata) (jobrecorder.Observation, error) {
		return func(_ context.Context, _ *jobs.Job, m jobrecorder.Metadata) (jobrecorder.Observation, error) {
			if k == waitObservation {
				x.waited++
			} else {
				x.settled++
			}
			x.metadata[k] = m
			x.at[k] = time.Date(2026, 10, 16, 0, 0, int(k), 0, time.UTC)
			return jobrecorder.Observation{}, nil
		}
	}
	x.op = operations{wait: capture(waitObservation), settle: capture(settleObservation), cancel: func(*jobs.Job) { x.canceled++ }, append: func(k kind, _ jobrecorder.Observation, s *jobevents.Store) (jobevents.Receipt, error) {
		x.appended++
		m := x.metadata[k]
		receipt, e := s.AppendJobStatus(jobevents.StatusInput{EventID: m.EventID, JobID: m.JobID, Revision: m.Revision, BootID: m.BootID, OccurredAt: x.at[k], Observed: jobs.Snapshot{Outcome: "exited", StartResolved: true, Started: true, Reaped: true, ExitCode: 0, Retirement: "confirmed", IOCompletion: "complete"}})
		if e != nil {
			return jobevents.Receipt{}, &jobrecorder.Failure{Stage: "append", Code: "persistence-failed"}
		}
		return receipt, nil
	}}
	return x
}
func (x *fixture) start(t *testing.T) *Run {
	t.Helper()
	r, e := start(context.Background(), x.delegation, x.store, jobs.Spec{}, x.a, x.op)
	if e != nil || r == nil {
		t.Fatal("fixture admission failed")
	}
	return r
}
func requireFailure(t *testing.T, e error, code string) {
	t.Helper()
	var f *Failure
	if !errors.As(e, &f) || f.Code != code {
		t.Fatalf("wrong fixed failure: %T", e)
	}
	if strings.Contains(e.Error(), "private") {
		t.Fatal("raw error forwarded")
	}
}

func TestAdmissionAndOriginalOwner(t *testing.T) {
	for _, name := range []string{"nil-context", "canceled", "nil-delegation", "nil-store", "boot", "entropy", "duplicate", "not-reset", "partial", "catalog-full", "job-collision", "event-collision", "canceled-after-read", "lookup-error", "nil-job", "retained-job-error"} {
		if !t.Run(name, func(t *testing.T) {
			x := newFixture(t)
			ctx := context.Background()
			delegation, store := x.delegation, x.store
			switch name {
			case "nil-context":
				ctx = nil
			case "canceled":
				c, cancel := context.WithCancel(ctx)
				cancel()
				ctx = c
			case "nil-delegation":
				delegation = nil
			case "nil-store":
				store = nil
			case "boot":
				x.a.boot = func() (bootidentity.ID, error) { return bootidentity.ID{}, errors.New("private boot path") }
			case "entropy":
				x.a.uuid = func() (string, error) { return "", errors.New("private entropy cause") }
			case "duplicate":
				x.a.uuid = func() (string, error) { return testIDs[0], nil }
			case "not-reset":
				x.a.read = func(*jobevents.Store) (jobevents.Batch, error) { return jobevents.Batch{}, nil }
			case "partial":
				real := x.a.read
				x.a.read = func(s *jobevents.Store) (jobevents.Batch, error) { b, e := real(s); b.HasMore = true; return b, e }
			case "catalog-full":
				real := x.a.read
				x.a.read = func(s *jobevents.Store) (jobevents.Batch, error) {
					b, e := real(s)
					b.Snapshot = make([]jobevents.Event, jobevents.MaxJobs)
					return b, e
				}
			case "job-collision":
				real := x.a.read
				x.a.read = func(s *jobevents.Store) (jobevents.Batch, error) {
					b, e := real(s)
					b.Snapshot = []jobevents.Event{{ResourceID: testIDs[1]}}
					return b, e
				}
			case "event-collision":
				x.a.lookup = func(*jobevents.Store, string) (jobevents.Presence, error) {
					return jobevents.Presence{Found: true}, nil
				}
			case "canceled-after-read":
				c, cancel := context.WithCancel(ctx)
				ctx = c
				real := x.a.read
				x.a.read = func(s *jobevents.Store) (jobevents.Batch, error) { b, e := real(s); cancel(); return b, e }
			case "lookup-error":
				x.a.lookup = func(*jobevents.Store, string) (jobevents.Presence, error) {
					return jobevents.Presence{}, errors.New("private store path")
				}
			case "nil-job":
				x.a.start = func(context.Context, *os.File, jobs.Spec) (*jobs.Job, error) { x.started++; return nil, nil }
			case "retained-job-error":
				x.a.start = func(context.Context, *os.File, jobs.Spec) (*jobs.Job, error) {
					x.started++
					return &jobs.Job{}, errors.New("private start cause")
				}
			}
			r, e := start(ctx, delegation, store, jobs.Spec{}, x.a, x.op)
			if e == nil {
				t.Fatal("failed admission accepted")
			}
			if name == "retained-job-error" {
				if r == nil || r.job == nil || r.Retired() {
					t.Fatal("partial owner lost or retired")
				}
				requireFailure(t, e, "owner-retained")
				return
			}
			if r != nil {
				t.Fatal("unexpected admitted owner")
			}
			if name != "nil-job" && x.started != 0 {
				t.Fatal("job started before preflight completed")
			}
		}) {
			return
		}
	}
}

func TestWaitCaptureAndExactRealStoreRetry(t *testing.T) {
	x := newFixture(t)
	r := x.start(t)
	if _, e := r.WaitAndRecord(nil); e == nil {
		t.Fatal("nil observer accepted")
	}
	if x.waited != 0 {
		t.Fatal("nil observer consumed wait")
	}
	first, e := r.WaitAndRecord(context.Background())
	if e != nil || !first.Recorded || !first.Captured || first.Retired {
		t.Fatal("wait receipt/status invalid")
	}
	metadata, at := x.metadata[waitObservation], x.at[waitObservation]
	second, e := r.WaitAndRecord(context.Background())
	if e != nil || second.Receipt != first.Receipt || x.waited != 1 || x.appended != 1 {
		t.Fatal("repeat wait recaptured or reappended")
	}
	retry, e := r.RetryWaitAppend()
	if e != nil || !retry.Receipt.AlreadyObserved || retry.Receipt.Sequence != first.Receipt.Sequence || x.waited != 1 || x.appended != 2 {
		t.Fatal("exact retry changed observation")
	}
	if !reflect.DeepEqual(metadata, x.metadata[waitObservation]) || !at.Equal(x.at[waitObservation]) {
		t.Fatal("metadata/time changed")
	}
	p := r.PendingObservations()
	if !p.HasWait || p.HasSettlement {
		t.Fatal("pending capture state invalid")
	}
	if e := r.Cancel(); e != nil || x.canceled != 1 {
		t.Fatal("explicit cancel did not reach owner")
	}
	settled, e := r.SettleAndRecord(context.Background())
	if e != nil || !settled.Retired || !r.Retired() || !settled.Recorded || x.settled != 1 {
		t.Fatal("settlement not composed")
	}
	beforeAppend := x.appended
	cached, e := r.WaitAndRecord(context.Background())
	if e != nil || cached.Receipt != retry.Receipt || !cached.Retired || x.waited != 1 || x.appended != beforeAppend {
		t.Fatal("terminal guard replaced an existing cached Wait")
	}
	again, e := r.RetrySettleAppend()
	if e != nil || !again.Receipt.AlreadyObserved || x.settled != 1 {
		t.Fatal("settle retry reobserved")
	}
	reset, e := x.store.ReadAfter(jobevents.Cursor{}, 1)
	if e != nil {
		t.Fatal("reset lookup failed")
	}
	batch, e := x.store.ReadAfter(jobevents.Cursor{StreamID: reset.Next.StreamID}, 8)
	if e != nil || len(batch.Events) != 2 {
		t.Fatal("real history mismatch")
	}
}

func TestBusyDoesNotStarveCancel(t *testing.T) {
	x := newFixture(t)
	r := x.start(t)
	entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
	capture := r.op.wait
	r.op.wait = func(ctx context.Context, j *jobs.Job, m jobrecorder.Metadata) (jobrecorder.Observation, error) {
		close(entered)
		<-release
		return capture(ctx, j, m)
	}
	var once sync.Once
	defer func() {
		once.Do(func() { close(release) })
		select {
		case <-done:
		case <-time.After(time.Second):
			t.Error("test-only observer did not join")
		}
	}()
	go func() { defer close(done); _, _ = r.WaitAndRecord(context.Background()) }()
	select {
	case <-entered:
	case <-time.After(time.Second):
		t.Fatal("wait seam did not start")
	}
	if _, e := r.SettleAndRecord(context.Background()); e == nil {
		t.Fatal("concurrent mutable operation accepted")
	} else {
		requireFailure(t, e, "busy")
	}
	canceled := make(chan error, 1)
	go func() { canceled <- r.Cancel() }()
	select {
	case e := <-canceled:
		if e != nil {
			t.Fatal("cancel failed")
		}
	case <-time.After(time.Second):
		t.Fatal("cancel starved by wait")
	}
	once.Do(func() { close(release) })
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("wait seam did not retire")
	}
	if x.canceled != 1 || x.waited != 1 {
		t.Fatal("call counts differ")
	}
}

func TestObserverEndingAndUncertainSettlementRemainOwned(t *testing.T) {
	x := newFixture(t)
	r := x.start(t)
	xwait := r.op.wait
	r.op.wait = func(ctx context.Context, j *jobs.Job, m jobrecorder.Metadata) (jobrecorder.Observation, error) {
		o, _ := xwait(ctx, j, m)
		return o, &jobrecorder.Failure{Stage: "capture-wait", Code: "observation-failed", CompletionUnknown: true}
	}
	wait, e := r.WaitAndRecord(context.Background())
	if e == nil || !wait.Recorded || !wait.CaptureFailed || wait.Retired || x.canceled != 0 {
		t.Fatal("observer ending altered lifetime/status")
	}
	actual := r.op.settle
	r.op.settle = func(context.Context, *jobs.Job, jobrecorder.Metadata) (jobrecorder.Observation, error) {
		x.settled++
		return jobrecorder.Observation{}, &jobrecorder.Failure{Stage: "capture-settle", Code: "observation-failed", CompletionUnknown: true}
	}
	before := x.appended
	failed, e := r.SettleAndRecord(context.Background())
	if e == nil || failed.Captured || r.Retired() || x.appended != before || r.job == nil || r.store == nil {
		t.Fatal("uncertain owner or terminal rule violated")
	}
	r.op.settle = actual
	terminal, e := r.SettleAndRecord(context.Background())
	if e != nil || !terminal.Retired || !terminal.Recorded {
		t.Fatal("explicit settlement did not resume")
	}
}

func TestPersistenceUncertaintyIsSeparateAndSticky(t *testing.T) {
	x := newFixture(t)
	r := x.start(t)
	real := r.op.append
	faulted := false
	r.op.append = func(k kind, o jobrecorder.Observation, s *jobevents.Store) (jobevents.Receipt, error) {
		if !faulted {
			faulted = true
			return jobevents.Receipt{}, &jobrecorder.Failure{Stage: "append", Code: "persistence-failed", CompletionUnknown: true, Claimed: true}
		}
		return real(k, o, s)
	}
	result, e := r.SettleAndRecord(context.Background())
	if e == nil || !result.Retired || !r.Retired() || !result.PersistenceUnknown || !result.Claimed || result.Recorded {
		t.Fatal("persistence effects flattened into retirement")
	}
	pending := r.PendingObservations()
	if !pending.HasSettlement || !pending.SettlementPersistenceUnknown {
		t.Fatal("pending immutable observation lost")
	}
	retry, e := r.RetrySettleAppend()
	if e != nil || !retry.Recorded || !retry.PersistenceUnknown || !retry.Claimed || x.settled != 1 {
		t.Fatal("retry erased uncertainty or reobserved")
	}
	// This is a pure injected flag test, not a real OS commit/close fault. The
	// temporary database nevertheless performs the successful append transaction.
}

func TestNilAndUncapturedOwners(t *testing.T) {
	var r *Run
	if r.Retired() || r.PendingObservations().HasWait || r.IDs() != (IDs{}) {
		t.Fatal("nil owner invented state")
	}
	if e := r.Cancel(); e == nil {
		t.Fatal("nil cancel accepted")
	}
	if _, e := r.WaitAndRecord(context.Background()); e == nil {
		t.Fatal("nil wait accepted")
	}
	x := newFixture(t)
	r = x.start(t)
	if _, e := r.RetryWaitAppend(); e == nil {
		t.Fatal("uncaptured retry accepted")
	}
}

// Real temporary storage proves no revision-1 append is attempted after revision2.
// The starter/observers remain private test seams, not managed-process evidence.
func TestSettleBeforeWaitRefusesNewObservation(t *testing.T) {
	x := newFixture(t)
	r := x.start(t)
	terminal, e := r.SettleAndRecord(context.Background())
	if e != nil || !terminal.Recorded || !terminal.Retired || x.settled != 1 || x.appended != 1 {
		t.Fatal("fixture terminal recording failed")
	}
	for i := 0; i < 2; i++ {
		result, e := r.WaitAndRecord(context.Background())
		requireFailure(t, e, "settled-before-wait")
		if !result.Retired || result.Captured || r.waited || x.waited != 0 || x.appended != 1 {
			t.Fatal("settled-first guard captured or appended late Wait")
		}
	}
	if _, e := r.RetryWaitAppend(); e == nil {
		t.Fatal("uncaptured Wait retry accepted")
	}
	reset, e := x.store.ReadAfter(jobevents.Cursor{}, 1)
	if e != nil {
		t.Fatal("fixture reset failed")
	}
	history, e := x.store.ReadAfter(jobevents.Cursor{StreamID: reset.Next.StreamID}, 8)
	if e != nil || len(history.Events) != 1 || history.Events[0].Payload.ObservationRevision != 2 || history.Events[0].EventID != r.IDs().SettleEventID {
		t.Fatal("real history regressed")
	}
	if r.PendingObservations().HasWait || !r.PendingObservations().HasSettlement {
		t.Fatal("pending observation identity changed")
	}
}
