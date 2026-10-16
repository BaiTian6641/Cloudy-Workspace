//go:build linux

package jobrecorder

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

// Native fixed identity: second deliberate event is another canonical UUID at
// the next revision. Boot/Job identity always come from the real fixture
// metadata, never manufactured here.
const settleEvent2 = "00000005-1234-4234-8234-123456789abc"

// runNativeCaptureSettle composes the real public CaptureWait/CaptureSettle
// against one actual retained child job and appends both observations to the
// real owned store. Test-only; requires nativeFixture/nativeReady/nativeGroup
// from native_linux_test.go. The driver requires explicit parent-owned opt-in.
func runNativeCaptureSettle(t *testing.T, delegation *os.File, owners *[]*jobs.Job) {
	t.Helper()

	job, store, meta := nativeFixture(t, delegation, owners, "hold")
	originalEvent := meta.EventID

	// Real Wait observations until READY; the child stays held.
	snap := nativeReady(t, job)
	group := nativeGroup(t, snap, delegation)

	// Public CaptureWait with a bounded observer context. The observer
	// timeout ends only the observation, never the job lifetime.
	waitCtx, waitCancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer waitCancel()
	o1, err := CaptureWait(waitCtx, job, meta)
	safeFailure(t, err, "capture-wait", "observation-failed")
	if o1.outcome != "pending" || !o1.startResolved || !o1.started || o1.reaped || o1.retirement != "pending" {
		t.Fatal("wait observation not truthful pending")
	}
	r1, err := o1.AppendTo(store)
	if err != nil || r1.Sequence == 0 || r1.AlreadyObserved {
		t.Fatal("pending append failed")
	}

	// Only after the retained pending observation is persisted does the
	// caller commit the stop.
	job.Cancel()
	meta.EventID = settleEvent2
	meta.Revision = 2

	settleCtx, settleCancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer settleCancel()
	o2, err := CaptureSettle(settleCtx, job, meta)
	if err != nil {
		t.Fatal("settle capture failed")
	}
	if o2.outcome != "canceled" || !o2.reaped || o2.retirement != "confirmed" {
		t.Fatal("settle observation not terminal")
	}
	r2, err := o2.AppendTo(store)
	if err != nil || r2.Sequence <= r1.Sequence || r2.AlreadyObserved {
		t.Fatal("terminal append failed")
	}
	retry, err := o2.AppendTo(store)
	if err != nil || retry.Sequence != r2.Sequence || !retry.AlreadyObserved || retry.Durability != "not-reassessed" {
		t.Fatal("exact retry changed capture")
	}

	// Full history via the mandated two-step reset resume.
	head, err := store.ReadAfter(jobevents.Cursor{}, 1)
	if err != nil || !head.ResetNeeded || len(head.Snapshot) != 1 {
		t.Fatal("stream reset resume failed")
	}
	batch, err := store.ReadAfter(jobevents.Cursor{StreamID: head.Next.StreamID, AfterSequence: 0}, 64)
	if err != nil || batch.ResetNeeded || len(batch.Events) != 2 {
		t.Fatal("history read failed")
	}
	nativeSanitized(t, batch.Events)
	first, second := batch.Events[0], batch.Events[1]
	if first.EventID != originalEvent || first.Payload.Outcome != "pending" || first.Payload.Reaped || first.Payload.ExitCode != nil || first.Payload.Signal != nil {
		t.Fatal("original pending event changed")
	}
	if second.EventID != settleEvent2 || second.Payload.Outcome != "canceled" || !second.Payload.Reaped || second.Payload.Retirement != "confirmed" {
		t.Fatal("terminal event wrong")
	}
	t1, err1 := time.Parse(time.RFC3339Nano, first.OccurredAt)
	t2, err2 := time.Parse(time.RFC3339Nano, second.OccurredAt)
	if err1 != nil || err2 != nil || t2.Before(t1) {
		t.Fatal("captured event time order invalid")
	}

	// Owned child is retired and the borrowed delegation remains usable.
	nativeGroupAbsent(t, group)
	if _, err := delegation.Stat(); err != nil {
		t.Fatal("borrowed delegation unusable")
	}
}
