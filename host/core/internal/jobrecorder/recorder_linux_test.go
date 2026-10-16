//go:build linux

package jobrecorder

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"testing"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

// These direct entry tests reject before any borrowed native owner can be used.
func TestBorrowedEntryRejectsNil(t *testing.T) {
	for _, capture := range []func(context.Context, *jobs.Job, Metadata) (Observation, error){CaptureWait, CaptureSettle} {
		if _, err := capture(context.Background(), nil, Metadata{}); err == nil {
			t.Fatal("nil job accepted")
		}
		if _, err := capture(nil, new(jobs.Job), Metadata{}); err == nil {
			t.Fatal("nil context accepted")
		}
	}
	if receipt, err := (Observation{}).AppendTo(nil); err == nil || receipt.Sequence != 0 {
		t.Fatal("nil store or unready observation accepted")
	}
}

const installation = "12345678-1234-4234-8234-123456789abc"
const event = "00000001-1234-4234-8234-123456789abc"
const jobID = "00000002-1234-4234-8234-123456789abc"
const nextEvent = "00000003-1234-4234-8234-123456789abc"

func metadata(t *testing.T) Metadata {
	t.Helper()
	boot, err := bootidentity.Read()
	if err != nil {
		t.Fatal("actual boot reader failed")
	}
	return Metadata{EventID: event, JobID: jobID, Revision: 1, BootID: boot}
}
func terminal() jobs.Snapshot {
	return jobs.Snapshot{Outcome: "exited", Started: true, StartResolved: true, Reaped: true,
		ExitCode: 3, Retirement: "confirmed", IOCompletion: "complete"}
}
func ownStore(t *testing.T, store *jobevents.Store) {
	t.Helper()
	if store != nil {
		t.Cleanup(func() {
			if err := store.Close(); err != nil {
				t.Error("owned store close failed")
			}
		})
	}
}
func fixture(t *testing.T) (string, *jobevents.Store) {
	t.Helper()
	dir := t.TempDir()
	store, err := jobevents.Init(dir, installation)
	ownStore(t, store)
	if err != nil {
		t.Fatal("owned store init failed")
	}
	return dir, store
}
func safeFailure(t *testing.T, err error, stage, code string) *Failure {
	t.Helper()
	f, ok := err.(*Failure)
	if !ok || f.Stage != stage || f.Code != code || err.Error() != "job-recorder-"+stage+":"+code {
		t.Fatal("wrong safe failure")
	}
	if _, ok := err.(interface{ Unwrap() error }); ok {
		t.Fatal("raw error unwrapping exposed")
	}
	return f
}

// The private seam returns synthetic observations; it never launches a managed job.
func captured(t *testing.T, m Metadata, snap jobs.Snapshot) Observation {
	t.Helper()
	o, err := capture(func(*jobs.Job, context.Context) (jobs.Snapshot, error) { return snap, nil },
		func() time.Time { return time.Date(2026, 10, 16, 22, 0, 0, 0, time.UTC) }, "capture-wait", context.Background(), new(jobs.Job), m)
	if err != nil {
		t.Fatal("synthetic observation capture failed")
	}
	return o
}
func TestCaptureOnceImmutableAndRealPersistence(t *testing.T) {
	dir, store := fixture(t)
	m := metadata(t)
	original := terminal()
	original.Stdout.Bytes = []byte("PRIVATE_OUTPUT_MARKER")
	original.Stderr.Bytes = []byte("PRIVATE_OUTPUT_MARKER")
	original.Failure = &jobs.Failure{Code: "PRIVATE_FAILURE_MARKER", Stage: "PRIVATE_FAILURE_MARKER"}
	original.RetirementFailure = original.Failure
	calls, clocks := 0, 0
	start := time.Date(2026, 10, 16, 17, 0, 0, 123, time.FixedZone("test", -5*3600))
	o, err := capture(func(*jobs.Job, context.Context) (jobs.Snapshot, error) { calls++; return original, nil }, func() time.Time {
		if calls != 1 {
			t.Fatal("clock preceded observation")
		}
		clocks++
		return start
	}, "capture-wait", context.Background(), new(jobs.Job), m)
	if err != nil || !o.occurredAt.Equal(start) || o.occurredAt.Location() != time.UTC {
		t.Fatal("capture timestamp failed")
	}
	original.Outcome = "PRIVATE_CHANGED_MARKER"
	original.Stdout.Bytes[0] = 'X'
	original.Failure.Code = "CHANGED"
	m.EventID = nextEvent
	m.JobID = nextEvent
	m.Revision = 77
	if _, err := o.AppendTo(nil); err == nil {
		t.Fatal("valid observation accepted nil store")
	}
	first, err := o.AppendTo(store)
	if err != nil || first.Sequence != 1 || first.AlreadyObserved || first.Durability != "sync-completed" {
		t.Fatal("real append failed")
	}
	repeat, err := o.AppendTo(store)
	if err != nil || repeat.Sequence != first.Sequence || !repeat.AlreadyObserved || repeat.Durability != "not-reassessed" || calls != 1 || clocks != 1 {
		t.Fatal("exact retry changed capture")
	}
	if err := store.Close(); err != nil {
		t.Fatal("actual close failed")
	}
	reopened, err := jobevents.Open(dir, installation)
	ownStore(t, reopened)
	if err != nil {
		t.Fatal("actual reopen failed")
	}
	reset, err := reopened.ReadAfter(jobevents.Cursor{}, 1)
	if err != nil || !reset.ResetNeeded || len(reset.Snapshot) != 1 {
		t.Fatal("snapshot resume failed")
	}
	e := reset.Snapshot[0]
	if e.EventID != event || e.ResourceID != jobID || e.Payload.ObservationRevision != 1 || e.Payload.Outcome != "exited" || e.OccurredAt != start.UTC().Format(time.RFC3339Nano) || e.Payload.ExitCode == nil || *e.Payload.ExitCode != 3 {
		t.Fatal("captured fields changed")
	}
	data, err := json.Marshal(e)
	if err != nil || strings.Contains(string(data), "PRIVATE_") {
		t.Fatal("unapproved data persisted")
	}
	batch, err := reopened.ReadAfter(jobevents.Cursor{StreamID: e.StreamID, AfterSequence: 0}, 1)
	if err != nil || batch.ResetNeeded || len(batch.Events) != 1 || batch.Next.AfterSequence != 1 {
		t.Fatal("real event resume failed")
	}
	if _, err := (Observation{}).AppendTo(reopened); err == nil {
		t.Fatal("zero observation accepted")
	}
}
func TestObservationErrorRemainsSeparateFromPersistence(t *testing.T) {
	_, store := fixture(t)
	m := metadata(t)
	for _, stage := range []string{"capture-wait", "capture-settle"} {
		ctx, cancel := context.WithCancel(context.Background())
		cancel()
		calls, clocks := 0, 0
		o, err := capture(func(_ *jobs.Job, observed context.Context) (jobs.Snapshot, error) {
			calls++
			if observed != ctx || observed.Err() == nil {
				t.Fatal("observer context changed")
			}
			return jobs.Snapshot{Outcome: "pending", Retirement: "uncertain", IOCompletion: "unknown"}, errors.New("PRIVATE_OBSERVATION_MARKER")
		}, func() time.Time { clocks++; return time.Now() }, stage, ctx, new(jobs.Job), m)
		f := safeFailure(t, err, stage, "observation-failed")
		if !f.CompletionUnknown || f.Claimed || calls != 1 || clocks != 1 {
			t.Fatal("observation uncertainty lost")
		}
		receipt, err := o.AppendTo(store)
		if err != nil || receipt.Sequence == 0 {
			t.Fatal("truthful pending observation not persisted")
		}
		presence, lookupErr := store.Lookup(receipt.EventID)
		if lookupErr != nil || !presence.Found || presence.Event.Payload.Outcome != "pending" || presence.Event.Payload.Retirement != "uncertain" || presence.Event.Payload.IOCompletion != "unknown" || presence.Event.Payload.ExitCode != nil || presence.Event.Payload.Signal != nil {
			t.Fatal("pending observation fabricated completion")
		}
		m.EventID = nextEvent
		m.Revision++
	}
	m.EventID = "00000004-1234-4234-8234-123456789abc"
	m.Revision++
	o := captured(t, m, terminal())
	if err := store.Close(); err != nil {
		t.Fatal("close failed")
	}
	receipt, err := o.AppendTo(store)
	f := safeFailure(t, err, "append", "persistence-failed")
	if receipt.Sequence != 0 || f.CompletionUnknown || f.Claimed {
		t.Fatal("closed-store error relabeled")
	}
}
func TestMetadataAndSnapshotRejectBeforePersistence(t *testing.T) {
	good := metadata(t)
	bad := []Metadata{{}, {EventID: event, JobID: jobID, Revision: 1}, good, good, good, good}
	bad[3].JobID = "wrong"
	bad[4].Revision = 0
	bad[5].Revision = jobevents.MaxSequence + 1
	// Reject a well-shaped UUID with the wrong version before observing.
	bad[2].EventID = "00000001-1234-5234-8234-123456789abc"
	for _, m := range bad {
		called := false
		_, err := capture(func(*jobs.Job, context.Context) (jobs.Snapshot, error) { called = true; return terminal(), nil }, time.Now, "capture-wait", context.Background(), new(jobs.Job), m)
		safeFailure(t, err, "capture-wait", "invalid-input")
		if called {
			t.Fatal("invalid metadata reached observer")
		}
	}
	invalid := []jobs.Snapshot{terminal(), terminal(), terminal(), terminal(), terminal(), terminal(), terminal()}
	invalid[0].Outcome = "invented"
	invalid[1].Retirement = "invented"
	invalid[2].IOCompletion = "invented"
	invalid[3].StartResolved = false
	invalid[4].Started = false
	invalid[5].ExitCode = -2
	invalid[6].Signal = -1
	for _, snap := range invalid {
		o, err := capture(func(*jobs.Job, context.Context) (jobs.Snapshot, error) { return snap, nil }, time.Now, "capture-settle", context.Background(), new(jobs.Job), good)
		safeFailure(t, err, "capture-settle", "invalid-snapshot")
		if o.valid {
			t.Fatal("invalid snapshot captured")
		}
	}
}
func TestRevisionConflictAndEffectFlags(t *testing.T) {
	_, store := fixture(t)
	m := metadata(t)
	m.Revision = 2
	if _, err := captured(t, m, terminal()).AppendTo(store); err != nil {
		t.Fatal("initial observation failed")
	}
	m.Revision = 1
	m.EventID = nextEvent
	receipt, err := captured(t, m, terminal()).AppendTo(store)
	f := safeFailure(t, err, "append", "persistence-failed")
	if receipt.Sequence != 0 || f.CompletionUnknown {
		t.Fatal("stale revision invented receipt")
	}
	m.Revision = 2
	m.EventID = event
	snap := terminal()
	snap.ExitCode = 4
	receipt, err = captured(t, m, snap).AppendTo(store)
	safeFailure(t, err, "append", "persistence-failed")
	if receipt.Sequence != 0 {
		t.Fatal("conflict invented receipt")
	}
	// This classifies actual exported effect flags; it does not inject an OS commit fault.
	for _, source := range []error{&jobevents.Failure{Code: "PRIVATE_STORE_MARKER", CompletionUnknown: true, Claimed: true}, errors.New("PRIVATE_RAW_MARKER")} {
		f := persistenceFailure(source)
		safeFailure(t, f, "append", "persistence-failed")
		_, typed := source.(*jobevents.Failure)
		if f.CompletionUnknown != typed || f.Claimed != typed {
			t.Fatal("store effect flags not preserved")
		}
	}
}
