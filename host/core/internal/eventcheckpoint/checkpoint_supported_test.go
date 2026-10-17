//go:build linux || darwin

package eventcheckpoint

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"math"
	"os"
	"path/filepath"
	"sync"
	"testing"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	bolt "go.etcd.io/bbolt"
	"golang.org/x/sys/unix"
)

const installationID = "12345678-1234-4234-8234-123456789abc"
const consumerID = "22345678-1234-4234-8234-123456789abc"
const streamID = "32345678-1234-4234-8234-123456789abc"
const otherID = "42345678-1234-4234-8234-123456789abc"

func cursor(sequence uint64) jobevents.Cursor {
	return jobevents.Cursor{StreamID: streamID, AfterSequence: sequence}
}
func own(t *testing.T, s *Store) {
	t.Helper()
	if s != nil {
		t.Cleanup(func() {
			if s.Close() != nil {
				t.Error("owned checkpoint close failed")
			}
		})
	}
}
func fixture(t *testing.T) (string, *Store) {
	t.Helper()
	dir := t.TempDir()
	s, e := Init(dir, installationID, consumerID)
	own(t, s)
	if e != nil {
		t.Fatal("checkpoint init failed")
	}
	return dir, s
}
func code(t *testing.T, err error, want string) {
	t.Helper()
	var f *Failure
	if !errors.As(err, &f) || f.Code != want {
		t.Fatal("unexpected fixed failure")
	}
	if errors.Unwrap(err) != nil {
		t.Fatal("failure unwraps private cause")
	}
}
func read(t *testing.T, s *Store) State {
	t.Helper()
	v, e := s.Read()
	if e != nil {
		t.Fatal("read failed")
	}
	return v
}
func fileHash(t *testing.T, dir string) [32]byte {
	t.Helper()
	b, e := os.ReadFile(filepath.Join(dir, AnchorName, DatabaseName))
	if e != nil {
		t.Fatal("fixture hash failed")
	}
	return sha256.Sum256(b)
}

func TestExplicitAdvanceReadCloseReopen(t *testing.T) {
	dir, s := fixture(t)
	initial := read(t, s)
	if initial != (State{}) {
		t.Fatal("nonempty initial checkpoint")
	}
	before := fileHash(t, dir)
	_ = read(t, s)
	if before != fileHash(t, dir) {
		t.Fatal("read acknowledged or changed database")
	}
	_, e := s.Advance(0, cursor(7), false)
	code(t, e, "reset-required")
	if before != fileHash(t, dir) {
		t.Fatal("refusal changed database")
	}
	got, e := s.Advance(0, cursor(7), true)
	if e != nil || got.Generation != 1 || !got.HasCursor || got.Cursor != cursor(7) || got.CompletionUnknown {
		t.Fatal("advance did not persist exact cursor")
	}
	if s.Close() != nil {
		t.Fatal("close failed")
	}
	reopened, e := Open(dir, installationID, consumerID)
	own(t, reopened)
	if e != nil {
		t.Fatal("reopen failed")
	}
	if read(t, reopened) != got {
		t.Fatal("reopen changed checkpoint")
	}
	if reopened.Close() != nil || reopened.Close() != nil {
		t.Fatal("close not idempotent")
	}
	_, e = reopened.Read()
	code(t, e, "closed-or-unready")
	_, e = reopened.Advance(1, cursor(8), false)
	code(t, e, "closed-or-unready")
}

func TestGenerationAndStreamTransitions(t *testing.T) {
	dir, s := fixture(t)
	if _, e := s.Advance(0, cursor(5), true); e != nil {
		t.Fatal("initial acknowledgement failed")
	}
	cases := []struct {
		name       string
		generation uint64
		cursor     jobevents.Cursor
		reset      bool
		want       string
	}{
		{"stale", 0, cursor(6), false, "stale-generation"},
		{"regression", 1, cursor(4), false, "sequence-regression"},
		{"reset-cannot-regress", 1, cursor(4), true, "sequence-regression"},
		{"stream-change", 1, jobevents.Cursor{StreamID: otherID, AfterSequence: 0}, false, "reset-required"},
		{"invalid-stream", 1, jobevents.Cursor{StreamID: "invalid", AfterSequence: 0}, true, "invalid-cursor"},
		{"sequence-limit", 1, cursor(jobevents.MaxSequence + 1), true, "invalid-cursor"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			before := fileHash(t, dir)
			_, e := s.Advance(tc.generation, tc.cursor, tc.reset)
			code(t, e, tc.want)
			if before != fileHash(t, dir) || read(t, s).Generation != 1 {
				t.Fatal("refusal mutated checkpoint")
			}
		})
	}
	equal, e := s.Advance(1, cursor(5), false)
	if e != nil || equal.Generation != 2 {
		t.Fatal("equal explicit acknowledgement failed")
	}
	changed := jobevents.Cursor{StreamID: otherID, AfterSequence: 0}
	got, e := s.Advance(2, changed, true)
	if e != nil || got.Generation != 3 || got.Cursor != changed {
		t.Fatal("explicit stream reset failed")
	}
}

func TestConcurrentCASAndExclusiveOwner(t *testing.T) {
	dir, s := fixture(t)
	second, e := Open(dir, installationID, consumerID)
	if e == nil {
		if second != nil {
			_ = second.Close()
		}
		t.Fatal("concurrent engine owner admitted")
	}
	own(t, second)
	code(t, e, "open")
	var wg sync.WaitGroup
	var mu sync.Mutex
	successes, stales := 0, 0
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, e := s.Advance(0, cursor(1), true)
			mu.Lock()
			defer mu.Unlock()
			if e == nil {
				successes++
				return
			}
			var f *Failure
			if errors.As(e, &f) && f.Code == "stale-generation" {
				stales++
			}
		}()
	}
	wg.Wait()
	if successes != 1 || stales != 7 || read(t, s).Generation != 1 {
		t.Fatal("generation compare-and-swap failed")
	}
}

func TestNamespaceAndPermanentAnchor(t *testing.T) {
	t.Run("namespace", func(t *testing.T) {
		dir, s := fixture(t)
		if s.Close() != nil {
			t.Fatal("close failed")
		}
		before := fileHash(t, dir)
		for _, ids := range [][2]string{{otherID, consumerID}, {installationID, otherID}} {
			owner, e := Open(dir, ids[0], ids[1])
			if e == nil {
				t.Fatal("namespace mismatch admitted")
			}
			own(t, owner)
			code(t, e, "metadata")
			if owner != nil && owner.Close() != nil {
				t.Fatal("partial owner close failed")
			}
			if before != fileHash(t, dir) {
				t.Fatal("readonly namespace refusal wrote database")
			}
		}
	})
	for _, kind := range []string{"empty", "corrupt", "symlink"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			anchor := filepath.Join(dir, AnchorName)
			if kind == "symlink" {
				if os.Symlink(t.TempDir(), anchor) != nil {
					t.Fatal("fixture symlink failed")
				}
			} else {
				if os.Mkdir(anchor, 0700) != nil {
					t.Fatal("fixture directory failed")
				}
				if kind == "corrupt" && os.WriteFile(filepath.Join(anchor, DatabaseName), []byte("invalid"), 0600) != nil {
					t.Fatal("fixture write failed")
				}
			}
			s, e := Init(dir, installationID, consumerID)
			own(t, s)
			code(t, e, "claim")
			if _, e = os.Lstat(anchor); e != nil {
				t.Fatal("permanent claim lost")
			}
		})
	}
	t.Run("missing-state", func(t *testing.T) {
		dir := t.TempDir()
		s, e := Open(dir, installationID, consumerID)
		own(t, s)
		code(t, e, "anchor")
		entries, e := os.ReadDir(dir)
		if e != nil || len(entries) != 0 {
			t.Fatal("open created state")
		}
	})
	t.Run("invalid-identities", func(t *testing.T) {
		dir := t.TempDir()
		for _, id := range []string{"", "bad", "12345678-1234-1234-8234-123456789abc"} {
			s, e := Init(dir, id, consumerID)
			if s != nil {
				_ = s.Close()
				t.Fatal("invalid input acquired owner")
			}
			code(t, e, "invalid-input")
		}
	})
}

func TestFileAndCanonicalMetadataRefusals(t *testing.T) {
	for _, kind := range []string{"empty", "oversize", "symlink", "fifo", "corrupt"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			anchor := filepath.Join(dir, AnchorName)
			if os.Mkdir(anchor, 0700) != nil {
				t.Fatal("fixture mkdir failed")
			}
			path := filepath.Join(anchor, DatabaseName)
			switch kind {
			case "empty":
				if os.WriteFile(path, nil, 0600) != nil {
					t.Fatal("fixture write failed")
				}
			case "oversize":
				f, e := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
				if e != nil {
					t.Fatal("fixture create failed")
				}
				e = errors.Join(f.Truncate(MaxFileBytes+1), f.Close())
				if e != nil {
					t.Fatal("fixture truncate/close failed")
				}
			case "symlink":
				if os.Symlink(filepath.Join(t.TempDir(), "absent"), path) != nil {
					t.Fatal("fixture symlink failed")
				}
			case "fifo":
				if unix.Mkfifo(path, 0600) != nil {
					t.Fatal("fixture fifo failed")
				}
			case "corrupt":
				if os.WriteFile(path, []byte("invalid"), 0600) != nil {
					t.Fatal("fixture write failed")
				}
			}
			info, e := os.Lstat(path)
			if e != nil {
				t.Fatal("fixture stat failed")
			}
			s, e := Open(dir, installationID, consumerID)
			own(t, s)
			code(t, e, "open")
			after, e := os.Lstat(path)
			if e != nil || !os.SameFile(info, after) || info.Size() != after.Size() {
				t.Fatal("refusal replaced state")
			}
		})
	}
	for _, kind := range []string{"extra-field", "noncanonical", "extra-key", "extra-bucket", "cursor-initial-inconsistent"} {
		t.Run(kind, func(t *testing.T) {
			dir, s := fixture(t)
			e := s.db.Update(func(tx *bolt.Tx) error {
				b := tx.Bucket(bucketName)
				data := append([]byte(nil), b.Get(recordKey)...)
				switch kind {
				case "extra-field":
					data = append(append([]byte(nil), data[:len(data)-1]...), []byte(",\"extra\":true}")...)
				case "noncanonical":
					data = append([]byte(" "), data...)
				case "extra-key":
					return b.Put([]byte("extra"), []byte("x"))
				case "extra-bucket":
					_, e := tx.CreateBucket([]byte("extra"))
					return e
				case "cursor-initial-inconsistent":
					var r record
					if json.Unmarshal(data, &r) != nil {
						return errors.New("fixture")
					}
					r.HasCursor = true
					r.StreamID = streamID
					data, _ = json.Marshal(r)
				}
				return b.Put(recordKey, data)
			})
			if e != nil || s.Close() != nil {
				t.Fatal("metadata fixture failed")
			}
			before := fileHash(t, dir)
			owner, e := Open(dir, installationID, consumerID)
			own(t, owner)
			code(t, e, "metadata")
			if owner != nil && owner.Close() != nil {
				t.Fatal("partial close failed")
			}
			if before != fileHash(t, dir) {
				t.Fatal("invalid metadata changed on refusal")
			}
		})
	}
}

func TestOverflowAndCommitUnknown(t *testing.T) {
	t.Run("generation-overflow", func(t *testing.T) {
		dir, s := fixture(t)
		e := s.db.Update(func(tx *bolt.Tx) error {
			r := record{1, installationID, consumerID, math.MaxUint64, true, streamID, 1}
			data, _ := json.Marshal(r)
			return tx.Bucket(bucketName).Put(recordKey, data)
		})
		if e != nil {
			t.Fatal("overflow fixture failed")
		}
		before := fileHash(t, dir)
		_, e = s.Advance(math.MaxUint64, cursor(2), false)
		code(t, e, "generation-overflow")
		if before != fileHash(t, dir) {
			t.Fatal("overflow mutated checkpoint")
		}
	})
	t.Run("post-commit-uncertainty", func(t *testing.T) {
		dir, s := fixture(t)
		s.commitFault = func() error { return errors.New("private synthetic path/token") }
		_, e := s.Advance(0, cursor(9), true)
		code(t, e, "commit-unknown")
		var f *Failure
		_ = errors.As(e, &f)
		if !f.CompletionUnknown {
			t.Fatal("uncertainty flattened")
		}
		observed := read(t, s)
		if !observed.CompletionUnknown || observed.Generation != 1 || observed.Cursor != cursor(9) {
			t.Fatal("actual committed state/uncertainty lost")
		}
		_, e = s.Advance(1, cursor(10), false)
		code(t, e, "mutation-poisoned")
		if s.Close() != nil {
			t.Fatal("close failed")
		}
		owner, e := Open(dir, installationID, consumerID)
		own(t, owner)
		if e != nil {
			t.Fatal("explicit reopen failed")
		}
		if read(t, owner).Cursor != cursor(9) {
			t.Fatal("committed cursor lost")
		}
	})
	t.Run("unknown-then-failed-load", func(t *testing.T) {
		_, s := fixture(t)
		s.commitFault = func() error { return errors.New("synthetic commit uncertainty") }
		_, err := s.Advance(0, cursor(9), true)
		code(t, err, "commit-unknown")
		// Corrupt actual persisted metadata through a real transaction. Read's View
		// executes and load fails; this is not a fake View/read callback.
		if s.db.Update(func(tx *bolt.Tx) error {
			return tx.Bucket(bucketName).Put(recordKey, []byte("invalid"))
		}) != nil {
			t.Fatal("failed-load fixture transaction failed")
		}
		_, err = s.Read()
		code(t, err, "read-failed")
		var failure *Failure
		if !errors.As(err, &failure) || !failure.CompletionUnknown {
			t.Fatal("failed read erased prior commit uncertainty")
		}
		_, err = s.Advance(1, cursor(10), false)
		code(t, err, "mutation-poisoned")
		if !errors.As(err, &failure) || !failure.CompletionUnknown {
			t.Fatal("failed read cleared mutation uncertainty")
		}
	})

}

func TestCloseFailureStickyAndSafe(t *testing.T) {
	s, initErr := Init(t.TempDir(), installationID, consumerID)
	if initErr != nil {
		if s != nil {
			_ = s.Close()
		}
		t.Fatal("fixture init failed")
	}
	calls := 0
	s.closeFault = func() error { calls++; return errors.New("private error path") }
	e := s.Close()
	code(t, e, "close-uncertain")
	if e.Error() != "event-checkpoint-close-uncertain" {
		t.Fatal("unsafe close error")
	}
	e = s.Close()
	code(t, e, "close-uncertain")
	if calls != 1 {
		t.Fatal("close retried uncertainty")
	}
	// The real resources were closed before the synthetic error. No automatic
	// fixture helper may relabel the intentionally sticky result as success.
}

func TestCanonicalUUIDAndRecordPrivacy(t *testing.T) {
	for _, s := range []string{installationID, consumerID, streamID} {
		if !validUUID(s) {
			t.Fatal("canonical UUID refused")
		}
	}
	for _, s := range []string{"12345678-1234-4234-C234-123456789abc", "12345678-1234-4234-8234-123456789abC", "12345678-1234-4234-8234-123456789abcx"} {
		if validUUID(s) {
			t.Fatal("invalid UUID admitted")
		}
	}
	_, s := fixture(t)
	_, e := s.Advance(0, cursor(1), true)
	if e != nil {
		t.Fatal("fixture advance failed")
	}
	e = s.db.View(func(tx *bolt.Tx) error {
		data := tx.Bucket(bucketName).Get(recordKey)
		if bytes.Contains(data, []byte("stdout")) || bytes.Contains(data, []byte("password")) {
			return errors.New("unexpected data")
		}
		return nil
	})
	if e != nil {
		t.Fatal("unexpected retained data")
	}
}
