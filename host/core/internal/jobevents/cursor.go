//go:build linux

package jobevents

import (
	"sort"

	bolt "go.etcd.io/bbolt"
)

type Cursor struct {
	StreamID      string
	AfterSequence uint64
}
type Batch struct {
	Events      []Event
	Next        Cursor
	HasMore     bool
	ResetNeeded bool
	GapReason   string
	// Snapshot covers only this bounded recorder catalog, not all host jobs.
	Snapshot []Event
}

func (s *Store) ReadAfter(cursor Cursor, limit int) (Batch, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || !s.ready {
		return Batch{}, &Failure{Code: "closed-or-unready"}
	}
	if limit < 1 || limit > MaxBatch || cursor.AfterSequence > MaxSequence {
		return Batch{}, &Failure{Code: "invalid-cursor"}
	}
	var result Batch
	err := s.db.View(func(tx *bolt.Tx) error {
		st, err := s.load(tx)
		if err != nil {
			return err
		}
		result.Next = cursor
		switch {
		case cursor.StreamID != st.meta.StreamID:
			result.GapReason = "stream-reset"
		case cursor.AfterSequence > st.meta.Head:
			result.GapReason = "ahead"
		case cursor.AfterSequence < st.meta.Floor-1:
			result.GapReason = "retention"
		}
		if result.GapReason != "" {
			result.ResetNeeded = true
			result.Next = Cursor{st.meta.StreamID, st.meta.Head}
			keys := make([]string, 0, len(st.latest))
			for key := range st.latest {
				keys = append(keys, key)
			}
			sort.Strings(keys)
			for _, key := range keys {
				result.Snapshot = append(result.Snapshot, st.latest[key].Event)
			}
			return nil
		}
		for _, r := range st.events {
			if r.Event.Sequence <= cursor.AfterSequence {
				continue
			}
			if len(result.Events) == limit {
				break
			}
			result.Events = append(result.Events, r.Event)
			result.Next.AfterSequence = r.Event.Sequence
		}
		result.HasMore = result.Next.AfterSequence < st.meta.Head
		return nil
	})
	if err != nil {
		s.poisoned = true
		return Batch{}, &Failure{Code: "read-failed", cause: err}
	}
	return result, nil
}

type Presence struct {
	Found   bool
	Event   Event
	Receipt Receipt
}

// Lookup searches the retained-event/latest horizon only. Absent is not proof an
// uncertain commit never happened; callers retain that uncertainty and never replay a job.
func (s *Store) Lookup(eventID string) (Presence, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || !s.ready {
		return Presence{}, &Failure{Code: "closed-or-unready"}
	}
	if !validUUID(eventID) {
		return Presence{}, &Failure{Code: "invalid-input"}
	}
	var result Presence
	err := s.db.View(func(tx *bolt.Tx) error {
		st, err := s.load(tx)
		if err != nil {
			return err
		}
		if r, ok := st.byID[eventID]; ok {
			result = Presence{true, r.Event, receipt(r, true)}
		}
		return nil
	})
	if err != nil {
		s.poisoned = true
		return Presence{}, &Failure{Code: "read-failed", cause: err}
	}
	return result, nil
}
