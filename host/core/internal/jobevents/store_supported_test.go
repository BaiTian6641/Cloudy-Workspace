//go:build linux || darwin

package jobevents

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	bolt "go.etcd.io/bbolt"
	"golang.org/x/sys/unix"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

const installationID = "12345678-1234-4234-8234-123456789abc"

func testUUID(i int) string { return fmt.Sprintf("%08x-1234-4234-8234-123456789abc", i) }
func own(t *testing.T, s *Store) {
	t.Helper()
	if s != nil {
		t.Cleanup(func() {
			if err := s.Close(); err != nil {
				t.Error("owned store close failed")
			}
		})
	}
}
func fixture(t *testing.T) (string, *Store) {
	t.Helper()
	dir := t.TempDir()
	s, err := Init(dir, installationID)
	own(t, s)
	if err != nil {
		t.Fatal("owned store init failed")
	}
	return dir, s
}
func input(t *testing.T, event, job int, rev uint64) StatusInput {
	t.Helper()
	boot, err := bootidentity.Read()
	if err != nil {
		t.Fatal("actual boot unavailable")
	}
	// Synthetic job observation tests persistence only, not managed execution.
	return StatusInput{testUUID(event), testUUID(job), boot, rev, time.Date(2026, 10, 16, 12, 0, 0, 0, time.UTC), jobs.Snapshot{Outcome: "exited", StartResolved: true, Started: true, Reaped: true, ExitCode: 0, Retirement: "confirmed", IOCompletion: "complete"}}
}
func failCode(t *testing.T, err error, code string) {
	t.Helper()
	f, ok := err.(*Failure)
	if !ok || f.Code != code || f.Error() != "job-events-"+code {
		t.Fatal("unexpected safe failure")
	}
	if _, ok := err.(interface{ Unwrap() error }); ok {
		t.Fatal("raw error unwrap exposed")
	}
}
func stream(t *testing.T, s *Store) string {
	t.Helper()
	b, err := s.ReadAfter(Cursor{}, 1)
	if err != nil || !b.ResetNeeded || b.GapReason != "stream-reset" {
		t.Fatal("initial stream observation failed")
	}
	return b.Next.StreamID
}
func digestFile(t *testing.T, path string) [32]byte {
	t.Helper()
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatal("owned file unavailable")
	}
	return sha256.Sum256(b)
}

func TestPersistenceResumeDedupeAndCopy(t *testing.T) {
	dir, s := fixture(t)
	sid := stream(t, s)
	in := input(t, 1, 11, 1)
	r, err := s.AppendJobStatus(in)
	if err != nil || r.Sequence != 1 || r.AlreadyObserved || r.Durability != "sync-completed" {
		t.Fatal("append failed")
	}
	b, err := s.ReadAfter(Cursor{sid, 0}, 1)
	if err != nil || b.ResetNeeded || len(b.Events) != 1 || b.Next.AfterSequence != 1 || b.HasMore {
		t.Fatal("initial read failed")
	}
	*b.Events[0].Payload.ExitCode = 99
	again, err := s.ReadAfter(Cursor{sid, 0}, 1)
	if err != nil || *again.Events[0].Payload.ExitCode != 0 {
		t.Fatal("returned value aliases stored memory")
	}
	if err = s.Close(); err != nil {
		t.Fatal("close failed")
	}
	s2, err := Open(dir, installationID)
	own(t, s2)
	if err != nil {
		t.Fatal("reopen failed")
	}
	r, err = s2.AppendJobStatus(in)
	if err != nil || !r.AlreadyObserved || r.Sequence != 1 || r.Durability != "not-reassessed" {
		t.Fatal("stable retry failed")
	}
	p, err := s2.Lookup(in.EventID)
	if err != nil || !p.Found || p.Receipt.Sequence != 1 || p.Receipt.Durability != "not-reassessed" {
		t.Fatal("reconcile lookup failed")
	}
	in.OccurredAt = in.OccurredAt.Add(time.Second)
	_, err = s2.AppendJobStatus(in)
	failCode(t, err, "conflict")
	in.EventID = testUUID(2)
	_, err = s2.AppendJobStatus(in)
	failCode(t, err, "conflict")
}

func TestRetentionGapRevisionAndIDHorizon(t *testing.T) {
	_, s := fixture(t)
	sid := stream(t, s)
	old := input(t, 1, 11, 1)
	if _, err := s.AppendJobStatus(old); err != nil {
		t.Fatal("old job append failed")
	}
	var last StatusInput
	for i := 1; i <= MaxEvents+1; i++ {
		last = input(t, 100+i, 12, uint64(i))
		if _, err := s.AppendJobStatus(last); err != nil {
			t.Fatal("retention append failed")
		}
	}
	gap, err := s.ReadAfter(Cursor{sid, 0}, 1)
	if err != nil || !gap.ResetNeeded || gap.GapReason != "retention" || len(gap.Snapshot) != 2 || gap.Next.AfterSequence != 258 {
		t.Fatal("retention snapshot failed")
	}
	page, err := s.ReadAfter(Cursor{sid, 2}, MaxBatch)
	if err != nil || page.ResetNeeded || len(page.Events) != 64 || page.Events[0].Sequence != 3 || page.Next.AfterSequence != 66 || !page.HasMore {
		t.Fatal("floor pagination failed")
	}
	for _, c := range []Cursor{{sid, 1}, {sid, 259}, {testUUID(999), 0}} {
		b, e := s.ReadAfter(c, 1)
		if e != nil || !b.ResetNeeded || b.Next.AfterSequence != 258 || len(b.Snapshot) != 2 {
			t.Fatal("gap boundary failed")
		}
	}
	end, err := s.ReadAfter(Cursor{sid, 258}, 1)
	if err != nil || end.ResetNeeded || len(end.Events) != 0 || end.HasMore || end.Next.AfterSequence != 258 {
		t.Fatal("head cursor failed")
	}
	// This ID is pruned from events but remains latest for job11; cross-job reuse refuses.
	collision := input(t, 1, 13, 1)
	_, err = s.AppendJobStatus(collision)
	failCode(t, err, "conflict")
	stale := input(t, 101, 12, 1)
	r, err := s.AppendJobStatus(stale)
	failCode(t, err, "stale")
	if r.Sequence != 0 {
		t.Fatal("stale invented receipt")
	}
	p, err := s.Lookup(old.EventID)
	if err != nil || !p.Found || p.Receipt.Sequence != 1 {
		t.Fatal("latest horizon lost")
	}
	p, err = s.Lookup(testUUID(101))
	if err != nil || p.Found {
		t.Fatal("expired history fabricated")
	}
	_, err = s.AppendJobStatus(last)
	if err != nil {
		t.Fatal("latest retry failed")
	}
}

func TestQuotaCursorInputAndSnapshotWhitelist(t *testing.T) {
	_, s := fixture(t)
	sid := stream(t, s)
	for i := 1; i <= MaxJobs; i++ {
		if _, err := s.AppendJobStatus(input(t, i, 1000+i, 1)); err != nil {
			t.Fatal("job catalog admission failed")
		}
	}
	_, err := s.AppendJobStatus(input(t, 500, 1500, 1))
	failCode(t, err, "job-quota")
	b, err := s.ReadAfter(Cursor{sid, 0}, 64)
	if err != nil || len(b.Events) != 64 || b.Next.AfterSequence != 64 {
		t.Fatal("quota advanced state")
	}
	for _, limit := range []int{0, 65} {
		_, err = s.ReadAfter(Cursor{sid, 0}, limit)
		failCode(t, err, "invalid-cursor")
	}
	_, err = s.ReadAfter(Cursor{sid, MaxSequence + 1}, 1)
	failCode(t, err, "invalid-cursor")
	in := input(t, 501, 1001, 2)
	in.Observed.Stdout.Bytes = []byte("secret-output")
	in.Observed.Stderr.Bytes = []byte("secret-output")
	in.Observed.Failure = &jobs.Failure{Code: "secret-output", Stage: "secret-output"}
	if _, err = s.AppendJobStatus(in); err != nil {
		t.Fatal("whitelist append failed")
	}
	p, err := s.Lookup(in.EventID)
	if err != nil || !p.Found {
		t.Fatal("whitelist lookup failed")
	}
	data, err := json.Marshal(p.Event)
	if err != nil || bytes.Contains(data, []byte("secret-output")) {
		t.Fatal("private data persisted")
	}
	cases := []func(*StatusInput){func(i *StatusInput) { i.Observed.Outcome = "secret-output" }, func(i *StatusInput) { i.Observed.Retirement = "bad" }, func(i *StatusInput) { i.Observed.IOCompletion = "bad" }, func(i *StatusInput) { i.Observed.StartResolved = false }, func(i *StatusInput) { i.Observed.Started = false }, func(i *StatusInput) { i.Revision = 0 }, func(i *StatusInput) { i.Revision = MaxSequence + 1 }, func(i *StatusInput) { i.EventID = "bad" }, func(i *StatusInput) { i.BootID = bootidentity.ID{} }, func(i *StatusInput) { i.OccurredAt = time.Time{} }, func(i *StatusInput) { i.Observed.ExitCode = -2 }}
	for _, change := range cases {
		x := input(t, 600, 1001, 3)
		change(&x)
		_, err = s.AppendJobStatus(x)
		failCode(t, err, "invalid-record")
	}
	// Legitimate rejected/prestart pending-I/O is distinct from completed I/O.
	x := input(t, 601, 1001, 3)
	x.Observed = jobs.Snapshot{Outcome: "rejected", Retirement: "confirmed", IOCompletion: "pending", ExitCode: -1}
	if _, err = s.AppendJobStatus(x); err != nil {
		t.Fatal("prestart status incorrectly strengthened")
	}
}

func TestUnknownCommitReconcileAndStickyClose(t *testing.T) {
	dir, s := fixture(t)
	in := input(t, 1, 11, 1)
	s.commitFault = func() error { return errors.New("private sync cause") }
	r, err := s.AppendJobStatus(in)
	failCode(t, err, "commit-unknown")
	if !err.(*Failure).CompletionUnknown || r.Sequence != 0 {
		t.Fatal("unknown commit receipt fabricated")
	}
	_, err = s.AppendJobStatus(input(t, 2, 11, 2))
	failCode(t, err, "mutation-poisoned")
	if err = s.Close(); err != nil {
		t.Fatal("close after unknown failed")
	}
	s2, err := Open(dir, installationID)
	expectCloseFailure := false
	if s2 != nil {
		t.Cleanup(func() {
			e := s2.Close()
			if expectCloseFailure {
				failCode(t, e, "close-uncertain")
			} else if e != nil {
				t.Error("owner close failed")
			}
		})
	}
	if err != nil {
		t.Fatal("reconcile reopen failed")
	}
	p, err := s2.Lookup(in.EventID)
	if err != nil || !p.Found || p.Receipt.Durability != "not-reassessed" {
		t.Fatal("present was mistaken for original durability")
	}
	// Test-only post-close failure: actual DB/roots are closed first. Sticky error is glue evidence.
	expectCloseFailure = true
	s2.closeFault = func() error { return errors.New("private close cause") }
	err = s2.Close()
	failCode(t, err, "close-uncertain")
	err = s2.Close()
	failCode(t, err, "close-uncertain")
	_, err = s2.ReadAfter(Cursor{}, 1)
	failCode(t, err, "closed-or-unready")
}

func TestOpenRefusalAndReadOnlyPreflight(t *testing.T) {
	for _, kind := range []string{"missing", "empty", "oversize", "symlink", "fifo"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			anchor := filepath.Join(dir, AnchorName)
			if err := os.Mkdir(anchor, 0700); err != nil {
				t.Fatal("anchor fixture failed")
			}
			file := filepath.Join(anchor, DatabaseName)
			switch kind {
			case "empty":
				if err := os.WriteFile(file, nil, 0600); err != nil {
					t.Fatal("empty fixture failed")
				}
			case "oversize":
				f, e := os.OpenFile(file, os.O_CREATE|os.O_EXCL|os.O_RDWR, 0600)
				if e != nil {
					t.Fatal("size fixture failed")
				}
				e = f.Truncate(MaxFileBytes + 1)
				ce := f.Close()
				if e != nil || ce != nil {
					t.Fatal("size fixture failed")
				}
			case "symlink":
				target := filepath.Join(dir, "owned-target")
				if os.WriteFile(target, []byte("preserve"), 0600) != nil || os.Symlink(target, file) != nil {
					t.Fatal("link fixture failed")
				}
			case "fifo":
				if unix.Mkfifo(file, 0600) != nil {
					t.Fatal("fifo fixture failed")
				}
			}
			s, err := Open(dir, installationID)
			own(t, s)
			if err == nil {
				t.Fatal("invalid file admitted")
			}
			info, e := os.Lstat(file)
			if kind == "missing" {
				if !errors.Is(e, os.ErrNotExist) {
					t.Fatal("open created file")
				}
			} else if e != nil || info == nil {
				t.Fatal("refusal removed entry")
			}
		})
	}
	dir, s := fixture(t)
	if err := s.Close(); err != nil {
		t.Fatal("fixture close failed")
	}
	path := filepath.Join(dir, AnchorName, DatabaseName)
	before := digestFile(t, path)
	bad, err := Open(dir, testUUID(55))
	own(t, bad)
	if err == nil {
		t.Fatal("wrong installation admitted")
	}
	if bad != nil && bad.Close() != nil {
		t.Fatal("partial owner close failed")
	}
	if digestFile(t, path) != before {
		t.Fatal("read-only binding refusal wrote database")
	}
	// A valid engine file with no synced freelist would convert on writable Open.
	// Invalid Cloudy metadata must be refused by read-only preflight first.
	db, err := bolt.Open(path, 0600, &bolt.Options{NoFreelistSync: true})
	if err != nil {
		t.Fatal("owned raw fixture open failed")
	}
	err = db.Update(func(tx *bolt.Tx) error { return tx.Bucket(metaBucket).Put(headerKey, []byte(`{"version":999}`)) })
	ce := db.Close()
	if err != nil || ce != nil {
		t.Fatal("owned invalid metadata fixture failed")
	}
	before = digestFile(t, path)
	bad, err = Open(dir, installationID)
	own(t, bad)
	if err == nil {
		t.Fatal("bad metadata admitted")
	}
	if bad != nil && bad.Close() != nil {
		t.Fatal("partial owner close failed")
	}
	if digestFile(t, path) != before {
		t.Fatal("metadata refusal triggered writable conversion")
	}
}

func TestPermanentClaimAndCorruptionNoRepair(t *testing.T) {
	dir := t.TempDir()
	if os.Mkdir(filepath.Join(dir, AnchorName), 0700) != nil {
		t.Fatal("fixture failed")
	}
	s, err := Init(dir, installationID)
	own(t, s)
	if err == nil {
		t.Fatal("existing anchor initialized")
	}
	entries, err := os.ReadDir(filepath.Join(dir, AnchorName))
	if err != nil || len(entries) != 0 {
		t.Fatal("existing anchor repaired")
	}
	_, s2 := fixture(t)
	sid := stream(t, s2)
	if _, err = s2.AppendJobStatus(input(t, 1, 11, 1)); err != nil {
		t.Fatal("fixture append failed")
	}
	if err = s2.db.Update(func(tx *bolt.Tx) error { return tx.Bucket(eventBucket).Delete(seqKey(1)) }); err != nil {
		t.Fatal("corruption fixture failed")
	}
	_, err = s2.ReadAfter(Cursor{sid, 0}, 1)
	failCode(t, err, "read-failed")
	_, err = s2.AppendJobStatus(input(t, 2, 11, 2))
	failCode(t, err, "mutation-poisoned")
}

func TestSequenceExhaustionWithoutIncrement(t *testing.T) {
	_, s := fixture(t)
	sid := stream(t, s)
	template := input(t, 1, 11, 1)
	err := s.db.Update(func(tx *bolt.Tx) error {
		for n := MaxSequence - MaxEvents + 1; n <= MaxSequence; n++ {
			in := template
			in.EventID = testUUID(int(n%100000) + 1)
			in.Revision = n
			r, e := makeRecord(in, sid, installationID, n)
			if e != nil {
				return e
			}
			data, e := json.Marshal(r)
			if e != nil {
				return e
			}
			if e = tx.Bucket(eventBucket).Put(seqKey(n), data); e != nil {
				return e
			}
			if e = tx.Bucket(idBucket).Put([]byte(in.EventID), seqKey(n)); e != nil {
				return e
			}
			if n == MaxSequence {
				if e = tx.Bucket(latestBucket).Put([]byte(in.JobID), data); e != nil {
					return e
				}
			}
		}
		data, e := json.Marshal(metadata{1, installationID, sid, MaxSequence, MaxSequence - MaxEvents + 1, 1})
		if e != nil {
			return e
		}
		return tx.Bucket(metaBucket).Put(headerKey, data)
	})
	if err != nil {
		t.Fatal("max sequence fixture failed")
	}
	_, err = s.AppendJobStatus(input(t, 999999, 11, MaxSequence))
	failCode(t, err, "conflict")
	_, err = s.AppendJobStatus(input(t, 999999, 12, 1))
	failCode(t, err, "sequence-exhausted")
	b, err := s.ReadAfter(Cursor{sid, MaxSequence}, 1)
	if err != nil || b.Next.AfterSequence != MaxSequence || b.HasMore {
		t.Fatal("max sequence changed")
	}
}

func TestSerializeCloseAndOperations(t *testing.T) {
	_, s := fixture(t)
	in := input(t, 1, 11, 1)
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, _ = s.AppendJobStatus(in)
			_, _ = s.ReadAfter(Cursor{}, 1)
			_, _ = s.Lookup(in.EventID)
		}()
	}
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := s.Close(); err != nil {
			t.Error("concurrent close failed")
		}
	}()
	wg.Wait()
	_, err := s.AppendJobStatus(in)
	failCode(t, err, "closed-or-unready")
}

func TestRejectLatestWithoutExactHistoryOrUniqueSequence(t *testing.T) {
	for _, historical := range []bool{false, true} {
		name := "retained-sequence-identity"
		if historical {
			name = "historical-latest-sequence-unique"
		}
		t.Run(name, func(t *testing.T) {
			_, s := fixture(t)
			sid := stream(t, s)
			first := input(t, 1, 11, 1)
			if _, err := s.AppendJobStatus(first); err != nil {
				t.Fatal("real first event failed")
			}
			if historical {
				// Keep job11's historical latest after its event leaves retention.
				for i := 1; i <= MaxEvents; i++ {
					if _, err := s.AppendJobStatus(input(t, 100+i, 12, uint64(i))); err != nil {
						t.Fatal("retention fixture failed")
					}
				}
			} else {
				if _, err := s.AppendJobStatus(input(t, 2, 11, 2)); err != nil {
					t.Fatal("real latest event failed")
				}
			}
			// Valid canonical fields/digest alone cannot invent a job at sequence1.
			forged, err := makeRecord(input(t, 999, 13, 1), sid, installationID, 1)
			if err != nil {
				t.Fatal("synthetic malformed-store fixture failed")
			}
			data, err := json.Marshal(forged)
			if err != nil {
				t.Fatal("synthetic fixture encoding failed")
			}
			err = s.db.Update(func(tx *bolt.Tx) error {
				if e := tx.Bucket(latestBucket).Put([]byte(forged.Event.ResourceID), data); e != nil {
					return e
				}
				var m metadata
				if e := canonicalDecode(tx.Bucket(metaBucket).Get(headerKey), &m, 512); e != nil {
					return e
				}
				m.Jobs++
				encoded, e := json.Marshal(m)
				if e != nil {
					return e
				}
				return tx.Bucket(metaBucket).Put(headerKey, encoded)
			})
			if err != nil {
				t.Fatal("malformed-store injection failed")
			}
			_, err = s.ReadAfter(Cursor{}, 1)
			failCode(t, err, "read-failed")
			_, err = s.AppendJobStatus(input(t, 1000, 11, 3))
			failCode(t, err, "mutation-poisoned")
		})
	}
}
