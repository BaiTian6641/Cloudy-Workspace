//go:build linux

// Package jobevents stores bounded local job observations. Persisted status is
// neither current process truth nor authority to execute or resume an operation.
package jobevents

import (
	"crypto/sha256"
	"encoding/json"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

const (
	MaxSequence    = uint64(1<<53 - 1)
	MaxJobs        = 64
	MaxEvents      = 256
	MaxRecordBytes = 4096
	MaxFileBytes   = 16 << 20
	MaxBatch       = 64
)

type StatusInput struct {
	EventID, JobID string
	BootID         bootidentity.ID
	Revision       uint64
	OccurredAt     time.Time
	Observed       jobs.Snapshot
}

type Payload struct {
	ObservationRevision uint64 `json:"observationRevision"`
	Outcome             string `json:"outcome"`
	StartResolved       bool   `json:"startResolved"`
	Started             bool   `json:"started"`
	Reaped              bool   `json:"reaped"`
	Retirement          string `json:"retirement"`
	IOCompletion        string `json:"ioCompletion"`
	ExitCode            *int   `json:"exitCode,omitempty"`
	Signal              *int   `json:"signal,omitempty"`
}

type Event struct {
	Version        int     `json:"version"`
	EventID        string  `json:"eventId"`
	StreamID       string  `json:"streamId"`
	Sequence       uint64  `json:"sequence"`
	InstallationID string  `json:"installationId"`
	BootID         string  `json:"bootId"`
	OccurredAt     string  `json:"occurredAt"`
	Kind           string  `json:"kind"`
	ResourceID     string  `json:"resourceId"`
	Payload        Payload `json:"payload"`
}

type storedRecord struct {
	Event  Event    `json:"event"`
	Digest [32]byte `json:"digest"`
}

func validUUID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i := range s {
		c := s[i]
		if i == 8 || i == 13 || i == 18 || i == 23 {
			if c != '-' {
				return false
			}
		} else if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
			return false
		}
	}
	return s[14] == '4' && (s[19] == '8' || s[19] == '9' || s[19] == 'a' || s[19] == 'b')
}
func member(s string, values ...string) bool {
	for _, v := range values {
		if v == s {
			return true
		}
	}
	return false
}

func validateEvent(e Event) bool {
	p := e.Payload
	if e.Version != 1 || e.Kind != "job.status" || !validUUID(e.EventID) || !validUUID(e.ResourceID) || !validUUID(e.StreamID) || !validUUID(e.InstallationID) || !validUUID(e.BootID) || e.Sequence < 1 || e.Sequence > MaxSequence || p.ObservationRevision < 1 || p.ObservationRevision > MaxSequence {
		return false
	}
	t, err := time.Parse(time.RFC3339Nano, e.OccurredAt)
	if err != nil || t.IsZero() || t.UTC().Format(time.RFC3339Nano) != e.OccurredAt {
		return false
	}
	if !member(p.Outcome, "pending", "rejected", "start-failed", "exited", "canceled", "timed-out") || !member(p.Retirement, "pending", "uncertain", "confirmed") || !member(p.IOCompletion, "pending", "complete", "not-assessed", "incomplete", "unknown") {
		return false
	}
	if (p.Started && !p.StartResolved) || (p.Reaped && !p.Started) {
		return false
	}
	if !p.Reaped {
		return p.ExitCode == nil && p.Signal == nil
	}
	return p.ExitCode != nil && p.Signal != nil && *p.ExitCode >= -1 && int64(*p.ExitCode) <= 1<<32-1 && *p.Signal >= 0 && int64(*p.Signal) <= 1<<32-1
}

func observationDigest(e Event) ([32]byte, error) {
	// Sequence is assigned by this store, not part of caller observation identity.
	// The zero form is hash input only and never a published/persisted event.
	e.Sequence = 0
	data, err := json.Marshal(e)
	if err != nil {
		return [32]byte{}, err
	}
	return sha256.Sum256(data), nil
}

func makeRecord(in StatusInput, stream, installation string, sequence uint64) (storedRecord, error) {
	s := in.Observed
	p := Payload{ObservationRevision: in.Revision, Outcome: s.Outcome, StartResolved: s.StartResolved, Started: s.Started, Reaped: s.Reaped, Retirement: s.Retirement, IOCompletion: s.IOCompletion}
	if s.Reaped {
		exit, signal := s.ExitCode, s.Signal
		p.ExitCode = &exit
		p.Signal = &signal
	}
	e := Event{1, in.EventID, stream, sequence, installation, in.BootID.String(), in.OccurredAt.UTC().Format(time.RFC3339Nano), "job.status", in.JobID, p}
	if !validateEvent(e) {
		return storedRecord{}, reject("invalid-record")
	}
	digest, err := observationDigest(e)
	if err != nil {
		return storedRecord{}, reject("invalid-record")
	}
	r := storedRecord{e, digest}
	data, err := json.Marshal(r)
	if err != nil || len(data) > MaxRecordBytes {
		return storedRecord{}, reject("invalid-record")
	}
	return r, nil
}

func validateRecord(r storedRecord) bool {
	if !validateEvent(r.Event) {
		return false
	}
	d, err := observationDigest(r.Event)
	return err == nil && d == r.Digest
}
