//go:build linux || darwin

package diagnostic

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/installation"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

func eventsArgs(dir, cursor, limit string) []string {
	return []string{"events", "read", "--state-dir", dir, "--after", cursor, "--limit", limit}
}
func eventsTree(t *testing.T, dir string) map[string]string {
	t.Helper()
	values := map[string]string{}
	err := filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(dir, path)
		if err != nil {
			return err
		}
		value := fmt.Sprint(info.Mode())
		if info.Mode().IsRegular() {
			data, err := os.ReadFile(path)
			if err != nil {
				return err
			}
			value += fmt.Sprintf(":%x", sha256.Sum256(data))
		}
		values[rel] = value
		return nil
	})
	if err != nil {
		t.Fatal("private state inventory failed")
	}
	return values
}
func eventsUnchanged(t *testing.T, dir string, before map[string]string) {
	t.Helper()
	after := eventsTree(t, dir)
	if len(before) != len(after) {
		t.Fatal("event diagnostic changed state entries")
	}
	for key, value := range before {
		if after[key] != value {
			t.Fatal("event diagnostic changed state bytes or mode")
		}
	}
}
func eventsFixture(t *testing.T) (string, string, jobevents.Event) {
	t.Helper()
	dir := t.TempDir()
	identity, err := installation.Init(dir)
	if err != nil {
		t.Fatal("installation fixture failed")
	}
	store, err := jobevents.Init(dir, identity.Record.InstallationID)
	if store != nil {
		t.Cleanup(func() {
			if store.Close() != nil {
				t.Error("fixture store close failed")
			}
		})
	}
	if err != nil {
		t.Fatal("event fixture failed")
	}
	boot, err := bootidentity.Read()
	if err != nil {
		t.Fatal("actual boot read unavailable")
	}
	// Synthetic status checks persistence/composition only; no managed process runs.
	receipt, err := store.AppendJobStatus(jobevents.StatusInput{EventID: "00000810-1234-4234-8234-123456789abc",
		JobID: "00000820-1234-4234-8234-123456789abc", BootID: boot, Revision: 1,
		OccurredAt: time.Date(2026, 10, 16, 12, 0, 0, 0, time.UTC),
		Observed:   jobs.Snapshot{Outcome: "rejected", ExitCode: -1, Retirement: "confirmed", IOCompletion: "pending"}})
	if err != nil || receipt.Sequence != 1 {
		t.Fatal("event fixture append failed")
	}
	reset, err := store.ReadAfter(jobevents.Cursor{}, 1)
	if err != nil || !reset.ResetNeeded {
		t.Fatal("fixture stream unavailable")
	}
	if store.Close() != nil {
		t.Fatal("fixture writer close failed")
	}
	// Independently specified expected record: compare actual CLI JSON against
	// the inserted status/boot/installation, not merely its catalog cursor.
	expected := jobevents.Event{Version: 1, EventID: "00000810-1234-4234-8234-123456789abc",
		StreamID: reset.Next.StreamID, Sequence: 1, InstallationID: identity.Record.InstallationID,
		BootID: boot.String(), OccurredAt: "2026-10-16T12:00:00Z", Kind: "job.status",
		ResourceID: "00000820-1234-4234-8234-123456789abc",
		Payload:    jobevents.Payload{ObservationRevision: 1, Outcome: "rejected", Retirement: "confirmed", IOCompletion: "pending"}}
	return dir, reset.Next.StreamID, expected
}

func TestEventsActualReadAndStatePreservation(t *testing.T) {
	dir, stream, expected := eventsFixture(t)
	before := eventsTree(t, dir)
	for _, cursor := range []string{"reset", stream + ":0", stream + ":1"} {
		var out, errOut bytes.Buffer
		if Run(eventsArgs(dir, cursor, "1"), &out, &errOut, time.Now, CurrentRuntime) != 0 || errOut.Len() != 0 {
			t.Fatal("actual events command failed")
		}
		var result eventsObservation
		if json.Unmarshal(out.Bytes(), &result) != nil {
			t.Fatal("events JSON invalid")
		}
		if result.LocalEventsVersion != 1 || result.AgentVersion != AgentVersion || result.StorageAccess != "read-only" || result.Runtime != CurrentRuntime() || result.Batch.Next.StreamID != stream || result.Batch.Next.AfterSequence != 1 {
			t.Fatal("events observation boundary incorrect")
		}
		if _, err := time.Parse(time.RFC3339Nano, result.ObservedAt); err != nil {
			t.Fatal("observation time invalid")
		}
		switch cursor {
		case "reset":
			if !result.Batch.ResetNeeded || result.Batch.GapReason != "stream-reset" || len(result.Batch.Snapshot) != 1 || len(result.Batch.Events) != 0 {
				t.Fatal("reset snapshot incorrect")
			}
			if !reflect.DeepEqual(result.Batch.Snapshot[0], expected) {
				t.Fatal("reset snapshot differs from persisted input/boot identity")
			}
		case stream + ":0":
			if result.Batch.ResetNeeded || len(result.Batch.Events) != 1 || result.Batch.HasMore {
				t.Fatal("cursor resume incorrect")
			}
			if !reflect.DeepEqual(result.Batch.Events[0], expected) {
				t.Fatal("resumed event differs from persisted input/boot identity")
			}
		case stream + ":1":
			if result.Batch.ResetNeeded || len(result.Batch.Events) != 0 || result.Batch.HasMore {
				t.Fatal("empty terminal batch incorrect")
			}
		}
		eventsUnchanged(t, dir, before)
	}
}

func TestEventsRejectsArgumentsBeforeSources(t *testing.T) {
	dir := t.TempDir()
	base := eventsArgs(dir, "reset", "1")
	cases := [][]string{{"events"}, append(append([]string{}, base...), "extra")}
	for index, values := range map[int][]string{1: {"watch", ""}, 2: {"--dir"}, 3: {""},
		4: {"--cursor"}, 5: {"", "RESET", "reset:0", "bad:0", "12345678-1234-4234-8234-123456789abc:-1", "12345678-1234-4234-8234-123456789abc:01", "12345678-1234-4234-8234-123456789abc:1e0", "12345678-1234-4234-8234-123456789abc:9007199254740992", "12345678-1234-4234-8234-123456789abc:0:0", "12345678-1234-4234-8234-123456789abC:0"},
		6: {"--count"}, 7: {"0", "65", "01", "-1", "+1", "1.0", "１", " 1", "1 "}} {
		for _, value := range values {
			args := append([]string{}, base...)
			args[index] = value
			cases = append(cases, args)
		}
	}
	for index, args := range cases {
		t.Run(fmt.Sprint(index), func(t *testing.T) {
			var out, errOut bytes.Buffer
			if Run(args, &out, &errOut, forbiddenClock, forbiddenRuntime) != 2 || out.Len() != 0 || errOut.String() != usage {
				t.Fatal("bad events syntax crossed source boundary")
			}
		})
	}
	if entries, err := os.ReadDir(dir); err != nil || len(entries) != 0 {
		t.Fatal("bad syntax changed state")
	}
	if eventsMaxSequence != jobevents.MaxSequence || eventsMaxBatch != jobevents.MaxBatch {
		t.Fatal("local parser limits drifted from store")
	}
	for _, cursor := range []string{"reset", "12345678-1234-4234-8234-123456789abc:0", "12345678-1234-4234-8234-123456789abc:9007199254740991"} {
		if _, ok := parseEvents(eventsArgs(dir, cursor, "64")); !ok {
			t.Fatal("valid canonical boundary rejected")
		}
	}
}

func TestEventsMissingAndWrongStateNeverInitialize(t *testing.T) {
	for _, kind := range []string{"missing-installation", "missing-store", "incomplete-store", "corrupt-store", "wrong-installation"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			if kind != "missing-installation" {
				identity, err := installation.Init(dir)
				if err != nil {
					t.Fatal("identity fixture failed")
				}
				if kind == "incomplete-store" {
					if os.Mkdir(filepath.Join(dir, jobevents.AnchorName), 0700) != nil {
						t.Fatal("anchor fixture failed")
					}
				} else if kind == "corrupt-store" || kind == "wrong-installation" {
					id := identity.Record.InstallationID
					if kind == "wrong-installation" {
						id = "00000830-1234-4234-8234-123456789abc"
					}
					store, err := jobevents.Init(dir, id)
					if store != nil {
						t.Cleanup(func() {
							if store.Close() != nil {
								t.Error("store fixture close failed")
							}
						})
					}
					if err != nil || store.Close() != nil {
						t.Fatal("store fixture failed")
					}
					if kind == "corrupt-store" && os.WriteFile(filepath.Join(dir, jobevents.AnchorName, jobevents.DatabaseName), []byte("invalid"), 0600) != nil {
						t.Fatal("corrupt fixture failed")
					}
				}
			}
			before := eventsTree(t, dir)
			for range 2 {
				var out, errOut bytes.Buffer
				if Run(eventsArgs(dir, "reset", "1"), &out, &errOut, forbiddenClock, forbiddenRuntime) != 1 || out.Len() != 0 {
					t.Fatal("invalid state produced output")
				}
				want := "cloudy-host: events open failed\n"
				if kind == "missing-installation" {
					want = "cloudy-host: events installation read failed\n"
				}
				if errOut.String() != want {
					t.Fatal("invalid state error not fixed-safe")
				}
				eventsUnchanged(t, dir, before)
			}
		})
	}
}

type testEventsReader struct {
	batch             jobevents.Batch
	readErr, closeErr error
	reads, closes     int
}

func (s *testEventsReader) ReadAfter(jobevents.Cursor, int) (jobevents.Batch, error) {
	s.reads++
	return s.batch, s.readErr
}
func (s *testEventsReader) Close() error { s.closes++; return s.closeErr }

func TestEventsStageErrorsRetainCloseAndPrivacy(t *testing.T) {
	for _, name := range []string{"installation", "open", "nil-owner", "partial-close", "read", "read-close", "close"} {
		t.Run(name, func(t *testing.T) {
			private := errors.New("private path and cause")
			reader := &testEventsReader{}
			var out, errOut bytes.Buffer
			openCalls := 0
			want := ""
			read := func(string) (installation.Result, error) {
				if name == "installation" {
					want = "installation read failed"
					return installation.Result{}, private
				}
				return installation.Result{Record: installation.Record{InstallationID: "00000840-1234-4234-8234-123456789abc"}}, nil
			}
			open := func(string, string) (eventsReader, error) {
				openCalls++
				switch name {
				case "open":
					want = "open failed"
					return reader, private
				case "nil-owner":
					want = "open failed"
					return nil, nil
				case "partial-close":
					want = "open failed; cleanup uncertain"
					reader.closeErr = private
					return reader, private
				case "read":
					want = "read failed"
					reader.readErr = private
				case "read-close":
					want = "read failed; cleanup uncertain"
					reader.readErr = private
					reader.closeErr = private
				case "close":
					want = "close uncertain"
					reader.closeErr = private
				}
				return reader, nil
			}
			if observeEvents(eventsRequest{}, &out, &errOut, forbiddenClock, forbiddenRuntime, read, open) != 1 || out.Len() != 0 || errOut.String() != "cloudy-host: events "+want+"\n" {
				t.Fatal("staged safe failure incorrect")
			}
			if name == "installation" {
				if openCalls != 0 || reader.closes != 0 {
					t.Fatal("installation failure crossed store boundary")
				}
			} else if name == "nil-owner" {
				if reader.closes != 0 {
					t.Fatal("closed nonexistent owner")
				}
			} else if reader.closes != 1 {
				t.Fatal("partial/failed store not closed exactly once")
			}
		})
	}
}

type closeCheckedEventsWriter struct {
	t      *testing.T
	reader *testEventsReader
	bytes.Buffer
}

func (w *closeCheckedEventsWriter) Write(value []byte) (int, error) {
	if w.reader.closes != 1 {
		w.t.Error("output preceded owner Close")
	}
	return w.Buffer.Write(value)
}
func TestEventsCloseBeforeObservationAndOutput(t *testing.T) {
	reader := &testEventsReader{batch: jobevents.Batch{Next: jobevents.Cursor{StreamID: "00000850-1234-4234-8234-123456789abc", AfterSequence: 3}}}
	writer := &closeCheckedEventsWriter{t: t, reader: reader}
	var errOut bytes.Buffer
	read := func(string) (installation.Result, error) {
		return installation.Result{Record: installation.Record{InstallationID: "00000840-1234-4234-8234-123456789abc"}}, nil
	}
	open := func(dir, id string) (eventsReader, error) {
		if dir != "owned" || id != "00000840-1234-4234-8234-123456789abc" {
			t.Error("installation association lost")
		}
		return reader, nil
	}
	now := func() time.Time {
		if reader.closes != 1 {
			t.Error("clock preceded Close")
		}
		return time.Date(2026, 10, 16, 12, 0, 0, 1, time.UTC)
	}
	facts := func() Runtime {
		if reader.closes != 1 {
			t.Error("runtime preceded Close")
		}
		return Runtime{OS: "fixture", Arch: "fixture", GoVersion: "fixture"}
	}
	if observeEvents(eventsRequest{dir: "owned", limit: 1}, writer, &errOut, now, facts, read, open) != 0 || errOut.Len() != 0 || reader.reads != 1 || reader.closes != 1 {
		t.Fatal("observation ownership failed")
	}
	var top map[string]json.RawMessage
	if json.Unmarshal(writer.Bytes(), &top) != nil || len(top) != 6 {
		t.Fatal("unexpected local record fields")
	}
	var result eventsObservation
	if json.Unmarshal(writer.Bytes(), &result) != nil || result.LocalEventsVersion != 1 || result.StorageAccess != "read-only" || result.Batch.Next.AfterSequence != 3 || result.Batch.Events == nil || result.Batch.Snapshot == nil {
		t.Fatal("local observation encoding failed")
	}
}

func TestEventsOutputFailuresCloseWithoutSuccess(t *testing.T) {
	for _, name := range []string{"writer", "short", "nil", "bound"} {
		t.Run(name, func(t *testing.T) {
			reader := &testEventsReader{}
			var errOut bytes.Buffer
			var output io.Writer = failingWriter{}
			if name == "short" {
				output = eventsShortWriter{}
			}
			if name == "nil" {
				output = nil
			}
			if name == "bound" {
				output = &bytes.Buffer{}
				reader.batch.Events = []jobevents.Event{{EventID: strings.Repeat("x", maxEventsOutputBytes)}}
			}
			read := func(string) (installation.Result, error) { return installation.Result{}, nil }
			open := func(string, string) (eventsReader, error) { return reader, nil }
			if observeEvents(eventsRequest{}, output, &errOut, time.Now, CurrentRuntime, read, open) != 1 || errOut.String() != "cloudy-host: events output failed\n" || reader.closes != 1 {
				t.Fatal("output failure boundary incorrect")
			}
			if buffer, ok := output.(*bytes.Buffer); ok && buffer.Len() != 0 {
				t.Fatal("oversize response partially written")
			}
		})
	}
}

type eventsShortWriter struct{}

func (eventsShortWriter) Write(value []byte) (int, error) { return len(value) - 1, nil }
