//go:build linux || darwin

package jobevents

import (
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"os"
	"sync"
	"time"

	bolt "go.etcd.io/bbolt"
	"golang.org/x/sys/unix"
)

const AnchorName = "job-events-v1"
const DatabaseName = "events.db"

// Failure has fixed safe text and no Unwrap. Claimed and CompletionUnknown
// report effects/uncertainty, never permission to reset or retry a job.
type Failure struct {
	Code                       string
	Claimed, CompletionUnknown bool
	cause                      error
}

func (f *Failure) Error() string { return "job-events-" + f.Code }

type rejection struct{ code string }

func (r *rejection) Error() string { return "job-events-" + r.code }
func reject(code string) error     { return &rejection{code} }

// Store owns its engine and root handles. A nonnil owner returned with an error
// still requires Close. No transaction or mmap-backed byte slice escapes a call.
type Store struct {
	mu                      sync.Mutex
	parent, child           *os.Root
	db                      *bolt.DB
	engineCloseAttempted    bool
	closed, ready, poisoned bool
	closeErr                error
	claimed                 bool
	writableAttempt         bool
	installation, stream    string
	// Test-only per-handle seams exercise uncertainty glue, not native fsync proof.
	commitFault func() error
	closeFault  func() error
}

type metadata struct {
	Version        int    `json:"version"`
	InstallationID string `json:"installationId"`
	StreamID       string `json:"streamId"`
	Head           uint64 `json:"head"`
	Floor          uint64 `json:"floor"`
	Jobs           int    `json:"jobs"`
}

type state struct {
	meta   metadata
	events []storedRecord
	latest map[string]storedRecord
	byID   map[string]storedRecord
}

var metaBucket = []byte("meta")
var eventBucket = []byte("events")
var idBucket = []byte("ids")
var latestBucket = []byte("latest")
var headerKey = []byte("header")
var alreadyObserved = errors.New("already-observed-internal")

func seqKey(v uint64) []byte { var b [8]byte; binary.BigEndian.PutUint64(b[:], v); return b[:] }
func canonicalDecode(data []byte, value any, limit int) error {
	if len(data) == 0 || len(data) > limit {
		return reject("corrupt")
	}
	d := json.NewDecoder(bytes.NewReader(data))
	d.DisallowUnknownFields()
	if d.Decode(value) != nil {
		return reject("corrupt")
	}
	if d.Decode(new(any)) != io.EOF {
		return reject("corrupt")
	}
	canonical, err := json.Marshal(value)
	if err != nil || !bytes.Equal(canonical, data) {
		return reject("corrupt")
	}
	return nil
}
func freshUUID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	b[6] = b[6]&15 | 64
	b[8] = b[8]&63 | 128
	s := hex.EncodeToString(b[:])
	return s[:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:], nil
}

func directorySync(root *os.Root) error {
	f, err := root.Open(".")
	if err != nil {
		return err
	}
	return errors.Join(f.Sync(), f.Close())
}

// Init exclusively claims its permanent child. Every later failure retains that
// claim; an existing child is never initialized, repaired or removed.
func Init(dir, installationID string) (*Store, error) { return openStore(dir, installationID, true) }

// Open requires existing state. It first validates using a read-only engine,
// closes it, then reacquires writable ownership and validates again. A writable
// engine Open may update its freelist before the second application validation;
// rejection therefore is not a universal physically-read-only promise.
func Open(dir, installationID string) (*Store, error) { return openStore(dir, installationID, false) }

func openStore(dir, installationID string, create bool) (*Store, error) {
	if !validUUID(installationID) {
		return nil, &Failure{Code: "invalid-input"}
	}
	before, err := os.Lstat(dir)
	if err != nil || !before.IsDir() || before.Mode()&os.ModeSymlink != 0 {
		return nil, &Failure{Code: "state-directory", cause: err}
	}
	root, err := os.OpenRoot(dir)
	if err != nil {
		return nil, &Failure{Code: "state-directory", cause: err}
	}
	s := &Store{parent: root, installation: installationID}
	fail := func(code string, cause error) (*Store, error) {
		s.poisoned = true
		return s, &Failure{Code: code, Claimed: s.claimed, CompletionUnknown: s.claimed || s.writableAttempt || code == "close-uncertain", cause: cause}
	}
	opened, err := root.Stat(".")
	if err != nil || !os.SameFile(before, opened) {
		return fail("state-directory", err)
	}
	if create {
		if err = root.Mkdir(AnchorName, 0700); err != nil {
			return fail("claim", err)
		}
		s.claimed = true
		if err = directorySync(root); err != nil {
			return fail("initialization-uncertain", err)
		}
	}
	anchor, err := root.Lstat(AnchorName)
	if err != nil || !anchor.IsDir() || anchor.Mode()&os.ModeSymlink != 0 {
		return fail("anchor", err)
	}
	s.child, err = root.OpenRoot(AnchorName)
	if err != nil {
		return fail("anchor", err)
	}
	childInfo, err := s.child.Stat(".")
	if err != nil || !os.SameFile(anchor, childInfo) {
		return fail("anchor", err)
	}
	if create {
		s.stream, err = freshUUID()
		if err != nil {
			return fail("initialization-uncertain", err)
		}
		s.db, err = s.openEngine(false, true)
		if err != nil {
			return fail("initialization-uncertain", err)
		}
		err = s.db.Update(func(tx *bolt.Tx) error {
			for _, name := range [][]byte{metaBucket, eventBucket, idBucket, latestBucket} {
				if _, e := tx.CreateBucket(name); e != nil {
					return e
				}
			}
			data, e := json.Marshal(metadata{1, installationID, s.stream, 0, 1, 0})
			if e != nil {
				return e
			}
			return tx.Bucket(metaBucket).Put(headerKey, data)
		})
		if err != nil {
			return fail("initialization-uncertain", err)
		}
		if err = directorySync(s.child); err != nil {
			return fail("initialization-uncertain", err)
		}
	} else {
		s.db, err = s.openEngine(true, false)
		if err != nil {
			return fail("open", err)
		}
		var checked state
		err = s.db.View(func(tx *bolt.Tx) error { var e error; checked, e = s.load(tx); return e })
		if err != nil {
			return fail("metadata", err)
		}
		s.stream = checked.meta.StreamID
		err = s.db.Close()
		s.engineCloseAttempted = true
		if err != nil {
			s.closeErr = err
			return fail("close-uncertain", err)
		}
		s.db = nil
		s.engineCloseAttempted = false
		s.db, err = s.openEngine(false, false)
		if err != nil {
			return fail("open", err)
		}
	}
	err = s.db.View(func(tx *bolt.Tx) error { _, e := s.load(tx); return e })
	if err != nil {
		return fail("metadata", err)
	}
	s.ready = true
	return s, nil
}

func (s *Store) openEngine(readOnly, create bool) (*bolt.DB, error) {
	if !readOnly {
		s.writableAttempt = true
	}
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
		bad := func(cause error) (*os.File, error) { return nil, errors.Join(cause, f.Close()) }
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
	// Nil Logger uses the selected upstream discard logger. Do not install a
	// logger that formats file paths/options or suppress returned engine errors.
	return bolt.Open(DatabaseName, 0600, &bolt.Options{ReadOnly: readOnly, Timeout: 250 * time.Millisecond, MaxSize: MaxFileBytes, OpenFile: openFile})
}

func (s *Store) load(tx *bolt.Tx) (state, error) {
	st := state{latest: make(map[string]storedRecord), byID: make(map[string]storedRecord)}
	bad := func() (state, error) { return state{}, reject("corrupt") }
	count := 0
	if err := tx.ForEach(func(name []byte, _ *bolt.Bucket) error {
		count++
		if !member(string(name), "meta", "events", "ids", "latest") {
			return reject("corrupt")
		}
		return nil
	}); err != nil || count != 4 {
		return bad()
	}
	m, e, ids, latest := tx.Bucket(metaBucket), tx.Bucket(eventBucket), tx.Bucket(idBucket), tx.Bucket(latestBucket)
	if canonicalDecode(m.Get(headerKey), &st.meta, 512) != nil || m.Stats().KeyN != 1 {
		return bad()
	}
	h := st.meta
	if h.Version != 1 || h.InstallationID != s.installation || !validUUID(h.StreamID) || (s.stream != "" && h.StreamID != s.stream) || h.Head > MaxSequence || h.Floor < 1 || h.Floor > h.Head+1 || h.Jobs < 0 || h.Jobs > MaxJobs {
		return bad()
	}
	floor := uint64(1)
	if h.Head > MaxEvents {
		floor = h.Head - MaxEvents + 1
	}
	if h.Floor != floor {
		return bad()
	}
	register := func(r storedRecord) error {
		if prior, ok := st.byID[r.Event.EventID]; ok && (prior.Digest != r.Digest || prior.Event.Sequence != r.Event.Sequence) {
			return reject("corrupt")
		}
		st.byID[r.Event.EventID] = r
		return nil
	}
	valid := func(r storedRecord) bool {
		return validateRecord(r) && r.Event.StreamID == h.StreamID && r.Event.InstallationID == h.InstallationID && r.Event.Sequence <= h.Head
	}
	next := h.Floor
	if err := e.ForEach(func(k, v []byte) error {
		if len(st.events) >= MaxEvents || len(k) != 8 || v == nil || binary.BigEndian.Uint64(k) != next {
			return reject("corrupt")
		}
		var r storedRecord
		if canonicalDecode(v, &r, MaxRecordBytes) != nil || !valid(r) || r.Event.Sequence != next {
			return reject("corrupt")
		}
		if !bytes.Equal(ids.Get([]byte(r.Event.EventID)), k) || register(r) != nil {
			return reject("corrupt")
		}
		st.events = append(st.events, r)
		next++
		return nil
	}); err != nil || next != h.Head+1 || ids.Stats().KeyN != len(st.events) {
		return bad()
	}
	latestSequences := make(map[uint64]bool)
	if err := latest.ForEach(func(k, v []byte) error {
		if len(st.latest) >= MaxJobs || !validUUID(string(k)) || v == nil {
			return reject("corrupt")
		}
		var r storedRecord
		if canonicalDecode(v, &r, MaxRecordBytes) != nil || !valid(r) || r.Event.ResourceID != string(k) || register(r) != nil {
			return reject("corrupt")
		}
		// Each append owns one unique stream sequence. Retained latest records
		// must be the exact event at that sequence, not a plausible invented row.
		if latestSequences[r.Event.Sequence] || (r.Event.Sequence >= h.Floor && !bytes.Equal(v, e.Get(seqKey(r.Event.Sequence)))) {
			return reject("corrupt")
		}
		latestSequences[r.Event.Sequence] = true
		st.latest[string(k)] = r
		return nil
	}); err != nil || len(st.latest) != h.Jobs {
		return bad()
	}
	previousRevision := make(map[string]uint64)
	for _, r := range st.events {
		if prior := previousRevision[r.Event.ResourceID]; prior >= r.Event.Payload.ObservationRevision {
			return bad()
		}
		previousRevision[r.Event.ResourceID] = r.Event.Payload.ObservationRevision
		last, ok := st.latest[r.Event.ResourceID]
		if !ok || last.Event.Sequence < r.Event.Sequence || last.Event.Payload.ObservationRevision < r.Event.Payload.ObservationRevision || (last.Event.Sequence == r.Event.Sequence && last.Digest != r.Digest) || (last.Event.Sequence > r.Event.Sequence && last.Event.Payload.ObservationRevision == r.Event.Payload.ObservationRevision) {
			return bad()
		}
	}
	return st, nil
}

type Receipt struct {
	EventID         string
	Sequence        uint64
	AlreadyObserved bool
	Durability      string
}

func receipt(r storedRecord, existing bool) Receipt {
	durability := "sync-completed"
	if existing {
		durability = "not-reassessed"
	}
	return Receipt{r.Event.EventID, r.Event.Sequence, existing, durability}
}

func (s *Store) AppendJobStatus(input StatusInput) (Receipt, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || !s.ready {
		return Receipt{}, &Failure{Code: "closed-or-unready"}
	}
	if s.poisoned {
		return Receipt{}, &Failure{Code: "mutation-poisoned", CompletionUnknown: true}
	}
	// Validate caller fields before starting any transaction; no sequence is used.
	if _, err := makeRecord(input, s.stream, s.installation, 1); err != nil {
		return Receipt{}, &Failure{Code: "invalid-record", cause: err}
	}
	var result Receipt
	err := s.db.Update(func(tx *bolt.Tx) error {
		st, err := s.load(tx)
		if err != nil {
			return err
		}
		candidate, err := makeRecord(input, s.stream, s.installation, 1)
		if err != nil {
			return err
		}
		if prior, ok := st.byID[input.EventID]; ok && (prior.Event.ResourceID != input.JobID || prior.Digest != candidate.Digest) {
			return reject("conflict")
		}
		if last, ok := st.latest[input.JobID]; ok {
			if input.Revision < last.Event.Payload.ObservationRevision {
				return reject("stale")
			}
			if input.Revision == last.Event.Payload.ObservationRevision {
				if input.EventID != last.Event.EventID || candidate.Digest != last.Digest {
					return reject("conflict")
				}
				result = receipt(last, true)
				return alreadyObserved
			}
		} else if len(st.latest) == MaxJobs {
			return reject("job-quota")
		}
		if _, ok := st.byID[input.EventID]; ok {
			return reject("conflict")
		}
		if st.meta.Head == MaxSequence {
			return reject("sequence-exhausted")
		}
		sequence := st.meta.Head + 1
		candidate, err = makeRecord(input, s.stream, s.installation, sequence)
		if err != nil {
			return err
		}
		data, err := json.Marshal(candidate)
		if err != nil {
			return err
		}
		if err = tx.Bucket(eventBucket).Put(seqKey(sequence), data); err != nil {
			return err
		}
		if err = tx.Bucket(idBucket).Put([]byte(input.EventID), seqKey(sequence)); err != nil {
			return err
		}
		if _, ok := st.latest[input.JobID]; !ok {
			st.meta.Jobs++
		}
		if err = tx.Bucket(latestBucket).Put([]byte(input.JobID), data); err != nil {
			return err
		}
		st.meta.Head = sequence
		if sequence >= MaxEvents+1 {
			old := st.events[0]
			if err = tx.Bucket(eventBucket).Delete(seqKey(old.Event.Sequence)); err != nil {
				return err
			}
			if err = tx.Bucket(idBucket).Delete([]byte(old.Event.EventID)); err != nil {
				return err
			}
			st.meta.Floor = sequence - MaxEvents + 1
		}
		data, err = json.Marshal(st.meta)
		if err != nil {
			return err
		}
		if err = tx.Bucket(metaBucket).Put(headerKey, data); err != nil {
			return err
		}
		result = receipt(candidate, false)
		return nil
	})
	if errors.Is(err, alreadyObserved) {
		return result, nil
	}
	if err == nil && s.commitFault != nil {
		err = s.commitFault()
	}
	if err != nil {
		var known *rejection
		if errors.As(err, &known) {
			if known.code == "corrupt" {
				s.poisoned = true
			}
			return Receipt{}, &Failure{Code: known.code, cause: err}
		}
		s.poisoned = true
		return Receipt{}, &Failure{Code: "commit-unknown", CompletionUnknown: true, cause: err}
	}
	return result, nil
}

// Close serializes with all admitted operations and attempts every owned close.
// The first close failure is sticky: repeated Close never upgrades it to success.
func (s *Store) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return s.publicCloseError()
	}
	s.closed = true
	s.ready = false
	var errs []error
	if s.db != nil && !s.engineCloseAttempted {
		errs = append(errs, s.db.Close())
		s.engineCloseAttempted = true
	}
	if s.child != nil {
		errs = append(errs, s.child.Close())
		s.child = nil
	}
	if s.parent != nil {
		errs = append(errs, s.parent.Close())
		s.parent = nil
	}
	if s.closeFault != nil {
		errs = append(errs, s.closeFault())
	}
	s.closeErr = errors.Join(s.closeErr, errors.Join(errs...))
	return s.publicCloseError()
}
func (s *Store) publicCloseError() error {
	if s.closeErr == nil {
		return nil
	}
	return &Failure{Code: "close-uncertain", Claimed: s.claimed, CompletionUnknown: true, cause: s.closeErr}
}
