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

func runNativeCaptureWait(t *testing.T, delegation *os.File, owners *[]*jobs.Job) {
	j, store, meta := nativeFixture(t, delegation, owners, "exit0")

	observer, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	first, err := CaptureWait(observer, j, meta)
	if err != nil {
		t.Fatal("native capture wait failed")
	}

	receipt, err := first.AppendTo(store)
	if err != nil || receipt.Sequence != 1 || receipt.AlreadyObserved || receipt.Durability != "sync-completed" {
		t.Fatal("native observation append failed")
	}
	repeat, err := first.AppendTo(store)
	if err != nil || repeat.Sequence != receipt.Sequence || repeat.EventID != receipt.EventID ||
		!repeat.AlreadyObserved || repeat.Durability != "not-reassessed" {
		t.Fatal("exact observation retry changed persistence")
	}
	presence, err := store.Lookup(receipt.EventID)
	if err != nil || !presence.Found || presence.Event.Sequence != receipt.Sequence {
		t.Fatal("persisted native observation absent")
	}

	laterMeta := Metadata{
		EventID:  nextEvent,
		JobID:    meta.JobID,
		Revision: meta.Revision + 1,
		BootID:   meta.BootID,
	}
	laterObserver, laterCancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer laterCancel()
	later, err := CaptureWait(laterObserver, j, laterMeta)
	if err != nil {
		t.Fatal("later native capture wait failed")
	}
	if later.occurredAt.Before(first.occurredAt) {
		t.Fatal("native capture timestamps regressed")
	}
	laterReceipt, err := later.AppendTo(store)
	if err != nil || laterReceipt.Sequence != 2 || laterReceipt.AlreadyObserved {
		t.Fatal("later native observation append failed")
	}

	reset, err := store.ReadAfter(jobevents.Cursor{}, 1)
	if err != nil || !reset.ResetNeeded || len(reset.Snapshot) != 1 || reset.Next.AfterSequence != 2 {
		t.Fatal("native event stream reset failed")
	}
	batch, err := store.ReadAfter(jobevents.Cursor{
		StreamID:      reset.Next.StreamID,
		AfterSequence: 0,
	}, 64)
	if err != nil || batch.ResetNeeded || batch.HasMore || len(batch.Events) != 2 {
		t.Fatal("native event history failed")
	}

	nativeSanitized(t, batch.Events)
	e := batch.Events[0]
	p := e.Payload
	if e.Sequence != 1 || e.EventID != meta.EventID || e.ResourceID != meta.JobID ||
		e.BootID != meta.BootID.String() || p.ObservationRevision != meta.Revision ||
		e.OccurredAt != first.occurredAt.UTC().Format(time.RFC3339Nano) {
		t.Fatal("exact retry changed captured identity")
	}
	if p.Outcome != "exited" || !p.StartResolved || !p.Started || !p.Reaped ||
		p.Retirement != "confirmed" || p.IOCompletion != "complete" ||
		p.ExitCode == nil || *p.ExitCode != 0 {
		t.Fatal("native terminal observation incorrect")
	}
	e = batch.Events[1]
	if e.Sequence != 2 || e.EventID != nextEvent || e.ResourceID != meta.JobID ||
		e.BootID != meta.BootID.String() || e.Payload.ObservationRevision != laterMeta.Revision ||
		e.OccurredAt != later.occurredAt.UTC().Format(time.RFC3339Nano) {
		t.Fatal("later capture metadata changed")
	}

	membership, membershipCancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer membershipCancel()
	snapshot, err := j.Wait(membership)
	if err != nil {
		t.Fatal("native membership observation failed")
	}
	group := nativeGroup(t, snapshot, delegation)
	nativeGroupAbsent(t, group)
	if _, err := delegation.Stat(); err != nil {
		t.Fatal("borrowed delegation became unusable")
	}
}
