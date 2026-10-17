//go:build linux || darwin

// Package eventcheckpoint stores one consumer's explicit processed-event cursor.
// It neither reads the journal nor authorizes acknowledgement, replay or jobs.
package eventcheckpoint

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"math"
	"os"
	"sync"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	bolt "go.etcd.io/bbolt"
	"golang.org/x/sys/unix"
)

const AnchorName = "consumer-checkpoint-v1"
const DatabaseName = "checkpoint.db"
const MaxFileBytes = 1024 * 1024
const maxRecordBytes = 1024

// Failure exposes no underlying OS/path/engine error. A nonnil owner returned
// with a failure still requires checked Close; Claimed never permits reset.
type Failure struct {
	Code                       string
	Claimed, CompletionUnknown bool
}

func (f *Failure) Error() string { return "event-checkpoint-" + f.Code }

type State struct {
	Generation        uint64
	HasCursor         bool
	Cursor            jobevents.Cursor
	CompletionUnknown bool
}

type record struct {
	Version        int    `json:"version"`
	InstallationID string `json:"installationId"`
	ConsumerID     string `json:"consumerId"`
	Generation     uint64 `json:"generation"`
	HasCursor      bool   `json:"hasCursor"`
	StreamID       string `json:"streamId"`
	Sequence       uint64 `json:"sequence"`
}

// Store owns one bbolt engine and rooted handles. All methods serialize; no
// mmap-backed slices escape. Trusted same-user state mutation is outside scope.
type Store struct {
	mu                                        sync.Mutex
	parent, child                             *os.Root
	db                                        *bolt.DB
	installation, consumer                    string
	claimed, ready, poisoned, unknown, closed bool
	engineCloseAttempted, closeUncertain      bool
	// Per-owner seams are set only by package tests, after real operations.
	commitFault, closeFault func() error
}

var bucketName = []byte("checkpoint")
var recordKey = []byte("state")

type refusal struct{ code string }

func (r *refusal) Error() string { return "event-checkpoint-" + r.code }
func reject(code string) error   { return &refusal{code} }

func validUUID(s string) bool {
	if len(s) != 36 || s[14] != '4' || (s[19] != '8' && s[19] != '9' && s[19] != 'a' && s[19] != 'b') {
		return false
	}
	for i, c := range s {
		if i == 8 || i == 13 || i == 18 || i == 23 {
			if c != '-' {
				return false
			}
			continue
		}
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return false
		}
	}
	return true
}
func syncDirectory(root *os.Root) error {
	f, err := root.Open(".")
	if err != nil {
		return err
	}
	return errors.Join(f.Sync(), f.Close())
}

// Init exclusively claims a permanent child in an existing trusted directory.
// Every later failure retains that claim. Existing state is never repaired.
func Init(dir, installationID, consumerID string) (*Store, error) {
	return openStore(dir, installationID, consumerID, true)
}

// Open never creates state. Read-only validation precedes writable reacquisition
// and revalidation. Writable bbolt Open can update freelist metadata before that
// second validation; refusal is not a universal no-physical-writes guarantee.
func Open(dir, installationID, consumerID string) (*Store, error) {
	return openStore(dir, installationID, consumerID, false)
}
func openStore(dir, installationID, consumerID string, create bool) (*Store, error) {
	if !validUUID(installationID) || !validUUID(consumerID) {
		return nil, &Failure{Code: "invalid-input"}
	}
	before, err := os.Lstat(dir)
	if err != nil || !before.IsDir() || before.Mode()&os.ModeSymlink != 0 {
		return nil, &Failure{Code: "state-directory"}
	}
	root, err := os.OpenRoot(dir)
	if err != nil {
		return nil, &Failure{Code: "state-directory"}
	}
	s := &Store{parent: root, installation: installationID, consumer: consumerID}
	fail := func(code string, uncertain bool) (*Store, error) {
		s.poisoned = true
		s.unknown = uncertain
		return s, &Failure{Code: code, Claimed: s.claimed, CompletionUnknown: uncertain}
	}
	opened, err := root.Stat(".")
	if err != nil || !os.SameFile(before, opened) {
		return fail("state-directory", false)
	}
	if create {
		if root.Mkdir(AnchorName, 0700) != nil {
			return fail("claim", false)
		}
		s.claimed = true
		if syncDirectory(root) != nil {
			return fail("initialization-uncertain", true)
		}
	}
	anchor, err := root.Lstat(AnchorName)
	if err != nil || !anchor.IsDir() || anchor.Mode()&os.ModeSymlink != 0 {
		return fail("anchor", s.claimed)
	}
	s.child, err = root.OpenRoot(AnchorName)
	if err != nil {
		return fail("anchor", s.claimed)
	}
	childInfo, err := s.child.Stat(".")
	if err != nil || !os.SameFile(anchor, childInfo) {
		return fail("anchor", s.claimed)
	}
	if create {
		s.db, err = s.openEngine(false, true)
		if err != nil {
			return fail("initialization-uncertain", true)
		}
		err = s.db.Update(func(tx *bolt.Tx) error {
			bucket, e := tx.CreateBucket(bucketName)
			if e != nil {
				return e
			}
			data, e := json.Marshal(record{Version: 1, InstallationID: installationID, ConsumerID: consumerID})
			if e != nil {
				return e
			}
			return bucket.Put(recordKey, data)
		})
		if err != nil || syncDirectory(s.child) != nil {
			return fail("initialization-uncertain", true)
		}
	} else {
		s.db, err = s.openEngine(true, false)
		if err != nil {
			return fail("open", false)
		}
		if err = s.db.View(func(tx *bolt.Tx) error { _, e := s.load(tx); return e }); err != nil {
			return fail("metadata", false)
		}
		s.engineCloseAttempted = true
		if s.db.Close() != nil {
			s.closeUncertain = true
			return fail("close-uncertain", true)
		}
		s.db = nil
		s.engineCloseAttempted = false
		s.db, err = s.openEngine(false, false)
		if err != nil {
			return fail("open", true)
		}
	}
	if err = s.db.View(func(tx *bolt.Tx) error { _, e := s.load(tx); return e }); err != nil {
		return fail("metadata", true)
	}
	s.ready = true
	return s, nil
}

// This narrow rooted open pattern follows internal/jobevents' accepted guards;
// its helpers are unexported and tied to that store's different layout/limits.
func (s *Store) openEngine(readOnly, create bool) (*bolt.DB, error) {
	openFile := func(_ string, _ int, _ os.FileMode) (*os.File, error) {
		before, err := s.child.Lstat(DatabaseName)
		if create {
			if !errors.Is(err, os.ErrNotExist) {
				return nil, reject("file-exists")
			}
		} else if err != nil || !before.Mode().IsRegular() || before.Mode()&os.ModeSymlink != 0 {
			return nil, reject("file")
		}
		flags := os.O_RDWR
		if readOnly {
			flags = os.O_RDONLY
		}
		if create {
			flags |= os.O_CREATE | os.O_EXCL
		}
		f, err := s.child.OpenFile(DatabaseName, flags|unix.O_NOFOLLOW|unix.O_NONBLOCK, 0600)
		if err != nil {
			return nil, err
		}
		bad := func(e error) (*os.File, error) { return nil, errors.Join(e, f.Close()) }
		info, err := f.Stat()
		if err != nil {
			return bad(err)
		}
		if !info.Mode().IsRegular() || info.Size() > MaxFileBytes || (!create && info.Size() == 0) {
			return bad(reject("file"))
		}
		after, err := s.child.Lstat(DatabaseName)
		if err != nil || after.Mode()&os.ModeSymlink != 0 || !os.SameFile(info, after) || (!create && !os.SameFile(before, info)) {
			return bad(reject("file-changed"))
		}
		return f, nil
	}
	// Selected bbolt's nil logger is discard; returned operational errors still fail.
	return bolt.Open(DatabaseName, 0600, &bolt.Options{ReadOnly: readOnly, Timeout: 250 * time.Millisecond, MaxSize: MaxFileBytes, OpenFile: openFile})
}
func (s *Store) load(tx *bolt.Tx) (record, error) {
	var r record
	count := 0
	if err := tx.ForEach(func(name []byte, _ *bolt.Bucket) error {
		count++
		if !bytes.Equal(name, bucketName) {
			return reject("corrupt")
		}
		return nil
	}); err != nil || count != 1 {
		return r, reject("corrupt")
	}
	b := tx.Bucket(bucketName)
	data := b.Get(recordKey)
	if b.Stats().KeyN != 1 || len(data) == 0 || len(data) > maxRecordBytes {
		return r, reject("corrupt")
	}
	d := json.NewDecoder(bytes.NewReader(data))
	d.DisallowUnknownFields()
	if d.Decode(&r) != nil || d.Decode(new(any)) != io.EOF {
		return record{}, reject("corrupt")
	}
	canonical, err := json.Marshal(r)
	if err != nil || !bytes.Equal(canonical, data) || r.Version != 1 || r.InstallationID != s.installation || r.ConsumerID != s.consumer {
		return record{}, reject("corrupt")
	}
	if r.HasCursor {
		if r.Generation == 0 || !validUUID(r.StreamID) || r.Sequence > jobevents.MaxSequence {
			return record{}, reject("corrupt")
		}
	} else if r.Generation != 0 || r.StreamID != "" || r.Sequence != 0 {
		return record{}, reject("corrupt")
	}
	return r, nil
}
func (s *Store) state(r record) State {
	return State{Generation: r.Generation, HasCursor: r.HasCursor, Cursor: jobevents.Cursor{StreamID: r.StreamID, AfterSequence: r.Sequence}, CompletionUnknown: s.unknown}
}

// Read does not acknowledge anything. After a commit-unknown it can expose the
// currently visible record, but CompletionUnknown stays true for this owner.
func (s *Store) Read() (State, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || !s.ready {
		return State{}, &Failure{Code: "closed-or-unready", Claimed: s.claimed, CompletionUnknown: s.unknown}
	}
	var r record
	err := s.db.View(func(tx *bolt.Tx) error { var e error; r, e = s.load(tx); return e })
	if err != nil {
		s.poisoned = true
		return State{}, &Failure{Code: "read-failed", Claimed: s.claimed, CompletionUnknown: s.unknown}
	}
	return s.state(r), nil
}

// Advance records exactly the supplied processed cursor. First acknowledgement
// and stream changes require explicit reset acknowledgement. The expected
// generation is checked atomically; even equal cursors increment it once.
func (s *Store) Advance(expectedGeneration uint64, processed jobevents.Cursor, resetAcknowledged bool) (State, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || !s.ready {
		return State{}, &Failure{Code: "closed-or-unready", Claimed: s.claimed, CompletionUnknown: s.unknown}
	}
	if s.poisoned {
		return State{}, &Failure{Code: "mutation-poisoned", Claimed: s.claimed, CompletionUnknown: s.unknown}
	}
	if !validUUID(processed.StreamID) || processed.AfterSequence > jobevents.MaxSequence {
		return State{}, &Failure{Code: "invalid-cursor"}
	}
	var next record
	err := s.db.Update(func(tx *bolt.Tx) error {
		r, e := s.load(tx)
		if e != nil {
			return e
		}
		if r.Generation != expectedGeneration {
			return reject("stale-generation")
		}
		if r.Generation == math.MaxUint64 {
			return reject("generation-overflow")
		}
		if (!r.HasCursor || r.StreamID != processed.StreamID) && !resetAcknowledged {
			return reject("reset-required")
		}
		if r.HasCursor && r.StreamID == processed.StreamID && processed.AfterSequence < r.Sequence {
			return reject("sequence-regression")
		}
		r.Generation++
		r.HasCursor = true
		r.StreamID = processed.StreamID
		r.Sequence = processed.AfterSequence
		data, e := json.Marshal(r)
		if e != nil {
			return e
		}
		if e = tx.Bucket(bucketName).Put(recordKey, data); e != nil {
			return e
		}
		next = r
		return nil
	})
	if err == nil && s.commitFault != nil {
		err = s.commitFault()
	}
	if err != nil {
		var refusal *refusal
		if errors.As(err, &refusal) {
			return State{}, &Failure{Code: refusal.code, Claimed: s.claimed}
		}
		s.poisoned = true
		s.unknown = true
		return State{}, &Failure{Code: "commit-unknown", Claimed: s.claimed, CompletionUnknown: true}
	}
	return s.state(next), nil
}

// Close attempts every owned close once. Any close failure remains sticky;
// repeated Close cannot upgrade it to success or imply a durability guarantee.
func (s *Store) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.closed {
		s.closed = true
		s.ready = false
		if s.db != nil && !s.engineCloseAttempted {
			s.engineCloseAttempted = true
			if s.db.Close() != nil {
				s.closeUncertain = true
			}
		}
		if s.child != nil {
			if s.child.Close() != nil {
				s.closeUncertain = true
			}
			s.child = nil
		}
		if s.parent != nil {
			if s.parent.Close() != nil {
				s.closeUncertain = true
			}
			s.parent = nil
		}
		if s.closeFault != nil && s.closeFault() != nil {
			s.closeUncertain = true
		}
	}
	if s.closeUncertain {
		return &Failure{Code: "close-uncertain", Claimed: s.claimed, CompletionUnknown: true}
	}
	return nil
}
