//go:build darwin

package jobevents

import (
	"path/filepath"
	"testing"
	"time"
)

// This native filesystem case persists a synthetic status with the actual boot
// reader. It does not start or qualify a Darwin managed job.
func TestDarwinReopenCursorAndRefusalPreservation(t *testing.T) {
	dir, s := fixture(t)
	sid := stream(t, s)
	first := input(t, 710, 720, 1)
	r, err := s.AppendJobStatus(first)
	if err != nil || r.Sequence != 1 || r.AlreadyObserved {
		t.Fatal("initial native append failed")
	}
	if err = s.Close(); err != nil {
		t.Fatal("initial native close failed")
	}
	path := filepath.Join(dir, AnchorName, DatabaseName)
	before := digestFile(t, path)
	wrong, err := Open(dir, testUUID(730))
	own(t, wrong)
	if err == nil {
		t.Fatal("wrong binding admitted")
	}
	if wrong != nil && wrong.Close() != nil {
		t.Fatal("refused native owner close failed")
	}
	if digestFile(t, path) != before {
		t.Fatal("refusal changed established native store")
	}
	again, err := Open(dir, installationID)
	own(t, again)
	if err != nil {
		t.Fatal("native reopen failed")
	}
	retry, err := again.AppendJobStatus(first)
	if err != nil || !retry.AlreadyObserved || retry.Sequence != r.Sequence || retry.Durability != "not-reassessed" {
		t.Fatal("native exact retry changed receipt identity")
	}
	second := input(t, 711, 720, 2)
	if second.BootID != first.BootID {
		t.Fatal("boot changed within native fixture")
	}
	next, err := again.AppendJobStatus(second)
	if err != nil || next.Sequence != 2 || next.AlreadyObserved {
		t.Fatal("native next revision failed")
	}
	batch, err := again.ReadAfter(Cursor{sid, r.Sequence}, 1)
	if err != nil || batch.ResetNeeded || batch.HasMore || batch.Next.StreamID != sid || batch.Next.AfterSequence != 2 || len(batch.Events) != 1 {
		t.Fatal("native resumed cursor failed")
	}
	if batch.Events[0].EventID != second.EventID || batch.Events[0].BootID != first.BootID.String() || batch.Events[0].Payload.ObservationRevision != 2 {
		t.Fatal("native resumed record mismatch")
	}
	if err = again.Close(); err != nil {
		t.Fatal("native reopened owner close failed")
	}
}

// Separate opens contend on the real Darwin flock path even within one process.
// The test deadline is the outer bound; Options.Timeout bounds lock acquisition.
func TestDarwinCompetingWriterLockTimeout(t *testing.T) {
	dir, _ := fixture(t)
	started := time.Now()
	other, err := Open(dir, installationID)
	own(t, other)
	if err == nil {
		t.Fatal("live writer lock was not exclusive")
	}
	failCode(t, err, "open")
	elapsed := time.Since(started)
	if elapsed < 100*time.Millisecond || elapsed > 5*time.Second {
		t.Fatal("native lock timeout outside bounded fixture interval")
	}
	if other != nil && other.Close() != nil {
		t.Fatal("contending native owner close failed")
	}
}
