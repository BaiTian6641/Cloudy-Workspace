//go:build linux || darwin

package diagnostic

import (
	"encoding/json"
	"io"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/installation"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
)

const maxEventsOutputBytes = 1 << 20

type eventsCursor struct {
	StreamID      string `json:"streamId"`
	AfterSequence uint64 `json:"afterSequence"`
}
type eventsBatch struct {
	Events      []jobevents.Event `json:"events"`
	Next        eventsCursor      `json:"next"`
	HasMore     bool              `json:"hasMore"`
	ResetNeeded bool              `json:"resetNeeded"`
	GapReason   string            `json:"gapReason"`
	Snapshot    []jobevents.Event `json:"snapshot"`
}
type eventsObservation struct {
	LocalEventsVersion int         `json:"localEventsVersion"`
	AgentVersion       string      `json:"agentVersion"`
	ObservedAt         string      `json:"observedAt"`
	Runtime            Runtime     `json:"runtime"`
	StorageAccess      string      `json:"storageAccess"`
	Batch              eventsBatch `json:"batch"`
}

// Private, per-call composition seam; production always opens the actual
// read-only bbolt owner. No mutable global hook or alternate state source exists.
type eventsReader interface {
	ReadAfter(jobevents.Cursor, int) (jobevents.Batch, error)
	Close() error
}

func runEventsRead(request eventsRequest, out, errOut io.Writer, now func() time.Time, facts func() Runtime) int {
	return observeEvents(request, out, errOut, now, facts, installation.Read,
		func(dir, id string) (eventsReader, error) {
			store, err := jobevents.OpenReadOnly(dir, id)
			if store == nil {
				return nil, err // Do not hide a nil pointer inside a nonnil interface.
			}
			return store, err
		})
}

func eventsFailure(errOut io.Writer, stage string) int {
	if errOut != nil {
		_, _ = io.WriteString(errOut, "cloudy-host: events "+stage+"\n")
	}
	return 1
}

func observeEvents(request eventsRequest, out, errOut io.Writer, now func() time.Time, facts func() Runtime,
	readInstallation func(string) (installation.Result, error),
	open func(string, string) (eventsReader, error)) int {
	identity, err := readInstallation(request.dir)
	if err != nil {
		return eventsFailure(errOut, "installation read failed")
	}
	store, err := open(request.dir, identity.Record.InstallationID)
	if err != nil || store == nil {
		if store != nil && store.Close() != nil {
			return eventsFailure(errOut, "open failed; cleanup uncertain")
		}
		return eventsFailure(errOut, "open failed")
	}
	batch, readErr := store.ReadAfter(jobevents.Cursor{StreamID: request.stream, AfterSequence: request.after}, request.limit)
	// ReadAfter returns detached copies, so close before any successful output.
	// A close failure cannot be relabeled as a usable batch or acknowledged cursor.
	closeErr := store.Close()
	if readErr != nil {
		if closeErr != nil {
			return eventsFailure(errOut, "read failed; cleanup uncertain")
		}
		return eventsFailure(errOut, "read failed")
	}
	if closeErr != nil {
		return eventsFailure(errOut, "close uncertain")
	}
	if now == nil || facts == nil {
		return eventsFailure(errOut, "observation unavailable")
	}
	if batch.Events == nil {
		batch.Events = []jobevents.Event{}
	}
	if batch.Snapshot == nil {
		batch.Snapshot = []jobevents.Event{}
	}
	response := eventsObservation{1, AgentVersion, now().UTC().Format(time.RFC3339Nano), facts(), "read-only",
		eventsBatch{batch.Events, eventsCursor{batch.Next.StreamID, batch.Next.AfterSequence}, batch.HasMore,
			batch.ResetNeeded, batch.GapReason, batch.Snapshot}}
	encoded, err := json.Marshal(response)
	if err != nil || len(encoded)+1 > maxEventsOutputBytes || out == nil {
		return eventsFailure(errOut, "output failed")
	}
	if _, err = (checkedWriter{out}).Write(append(encoded, '\n')); err != nil {
		// Partial output is possible; no success or cursor persistence is asserted.
		return eventsFailure(errOut, "output failed")
	}
	return 0
}
