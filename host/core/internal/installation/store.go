// Package installation owns explicit local installation self-identification.
// The ID is public metadata, never authentication, hardware, boot or logon identity.
package installation

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"os"
	"unicode/utf8"
)

const (
	AnchorName     = "installation-identity-v1"
	RecordName     = "installation-identity.json"
	MaxRecordBytes = 512
)

type Record struct {
	IdentityRecordVersion int    `json:"identityRecordVersion"`
	InstallationID        string `json:"installationId"`
}

// Failure exposes only a finite safe code. Claimed/Published describe effects
// already attempted successfully; neither field authorizes a retry or reset.
type Failure struct {
	Code               string
	Claimed, Published bool
	// cause is retained for internal precision without exposing path-bearing OS
	// errors through Error, CLI text, JSON, or a public Unwrap method.
	cause error
}

func (e *Failure) Error() string { return e.Code }

type Result struct {
	Record Record
	// Windows local-output v2 describes the parsed profile; it grants no authority.
	RecordVersion  int
	StorageProfile string
	// Sync-completed means the requested file/directory sync calls succeeded,
	// not a portable guarantee about storage hardware or recovery after power loss.
	Durability string
}

type fault func(stage string) error

func step(f fault, stage string, operation func() error) error {
	if f != nil {
		if err := f(stage); err != nil {
			return err
		}
	}
	return operation()
}

func openState(path string) (*os.Root, error) {
	if path == "" {
		return nil, errors.New("invalid-state-directory")
	}
	before, err := os.Lstat(path)
	if err != nil || !before.IsDir() || observedReparse(before) {
		return nil, errors.New("invalid-state-directory")
	}
	root, err := os.OpenRoot(path)
	if err != nil {
		return nil, errors.New("state-directory-unavailable")
	}
	after, err := root.Stat(".")
	if err != nil || !os.SameFile(before, after) {
		_ = root.Close()
		return nil, errors.New("state-directory-changed")
	}
	return root, nil
}

func openChild(root *os.Root) (*os.Root, error) {
	before, err := root.Lstat(AnchorName)
	if err != nil || !before.IsDir() || observedReparse(before) {
		return nil, errors.New("identity-state-incomplete")
	}
	child, err := root.OpenRoot(AnchorName)
	if err != nil {
		return nil, errors.New("identity-state-unavailable")
	}
	after, err := child.Stat(".")
	if err != nil || !os.SameFile(before, after) {
		_ = child.Close()
		return nil, errors.New("identity-state-changed")
	}
	return child, nil
}

func syncDirectory(root *os.Root) error {
	dir, err := root.Open(".")
	if err != nil {
		return err
	}
	syncErr := dir.Sync()
	closeErr := dir.Close()
	if syncErr != nil {
		return syncErr
	}
	return closeErr
}

// Init never repairs an existing anchor, even if its record is absent. Each OS
// profile uses one exclusive claim at AnchorName before generating an identity;
// every later failure retains that claim for separately designed recovery.
func Init(path string) (Result, error) { return initialize(path, nil) }
func Read(path string) (Result, error) { return readIdentity(path) }
func initializeDirectory(path string, inject fault) (result Result, err error) {
	claimed, published := false, false
	failure := func(code string) error { return &Failure{Code: code, Claimed: claimed, Published: published} }
	root, e := openState(path)
	if e != nil {
		return result, failure(e.Error())
	}
	defer func() {
		if e := root.Close(); e != nil && err == nil {
			err = failure("identity-commit-uncertain")
		}
	}()
	if e := root.Mkdir(AnchorName, 0o700); e != nil {
		if errors.Is(e, os.ErrExist) {
			return result, failure("identity-already-claimed")
		}
		return result, failure("identity-claim-failed")
	}
	claimed = true
	if e := step(inject, "claim-parent-sync", func() error { return syncDirectory(root) }); e != nil {
		return result, &Failure{Code: "identity-initialization-incomplete", Claimed: claimed, Published: published, cause: e}
	}
	child, e := openChild(root)
	if e != nil {
		return result, failure("identity-initialization-incomplete")
	}
	defer func() {
		if e := child.Close(); e != nil && err == nil {
			err = failure("identity-commit-uncertain")
		}
	}()
	var id [16]byte
	_, _ = rand.Read(id[:])
	id[6] = (id[6] & 0x0f) | 0x40
	id[8] = (id[8] & 0x3f) | 0x80
	h := hex.EncodeToString(id[:])
	record := Record{1, h[:8] + "-" + h[8:12] + "-" + h[12:16] + "-" + h[16:20] + "-" + h[20:]}
	data, _ := json.Marshal(record)
	data = append(data, '\n')
	var suffix [16]byte
	_, _ = rand.Read(suffix[:])
	temp := ".identity-" + hex.EncodeToString(suffix[:]) + ".tmp"
	var file *os.File
	e = step(inject, "temp-create", func() error {
		var createErr error
		file, createErr = child.OpenFile(temp, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
		return createErr
	})
	if e != nil {
		return result, failure("identity-initialization-incomplete")
	}
	// A failed attempt never removes the claim or an uncertain published record.
	// Its temporary file may remain; no scan/recovery of other attempts is implicit.
	defer func() {
		if file != nil {
			_ = file.Close()
		}
	}()
	if e := step(inject, "temp-write", func() error {
		n, e := file.Write(data)
		if e == nil && n != len(data) {
			return io.ErrShortWrite
		}
		return e
	}); e != nil {
		return result, failure("identity-initialization-incomplete")
	}
	if e := step(inject, "temp-sync", file.Sync); e != nil {
		return result, failure("identity-initialization-incomplete")
	}
	if e := step(inject, "temp-close", file.Close); e != nil {
		return result, failure("identity-initialization-incomplete")
	}
	file = nil
	if e := step(inject, "publish-link", func() error { return child.Link(temp, RecordName) }); e != nil {
		return result, failure("identity-initialization-incomplete")
	}
	published = true
	if e := step(inject, "publish-dir-sync", func() error { return syncDirectory(child) }); e != nil {
		return result, failure("identity-commit-uncertain")
	}
	if e := step(inject, "temp-remove", func() error { return child.Remove(temp) }); e != nil {
		return result, failure("identity-cleanup-uncertain")
	}
	if e := step(inject, "cleanup-dir-sync", func() error { return syncDirectory(child) }); e != nil {
		return result, failure("identity-commit-uncertain")
	}
	return Result{Record: record, Durability: "sync-completed"}, nil
}

// Read only observes an existing record. Readback never certifies that an earlier
// uncertain publication survives a future power loss, and never completes init.
func readDirectory(path string) (result Result, err error) {
	failure := func(code string) error { return &Failure{Code: code} }
	root, e := openState(path)
	if e != nil {
		return result, failure(e.Error())
	}
	defer func() {
		if e := root.Close(); e != nil && err == nil {
			err = failure("identity-read-failed")
		}
	}()
	return readDirectoryRoot(root)
}

// The caller retains the already checked root; Windows legacy reads never reopen
// a string path as a second storage authority.
func readDirectoryRoot(root *os.Root) (result Result, err error) {
	failure := func(code string) error { return &Failure{Code: code} }
	child, e := openChild(root)
	if e != nil {
		return result, failure(e.Error())
	}
	defer func() {
		if e := child.Close(); e != nil && err == nil {
			err = failure("identity-read-failed")
		}
	}()
	before, e := child.Lstat(RecordName)
	if e != nil || !before.Mode().IsRegular() || observedReparse(before) || before.Size() > MaxRecordBytes {
		return result, failure("identity-record-invalid")
	}
	file, e := child.Open(RecordName)
	if e != nil {
		return result, failure("identity-read-failed")
	}
	defer func() {
		if e := file.Close(); e != nil && err == nil {
			err = failure("identity-read-failed")
		}
	}()
	opened, e := file.Stat()
	if e != nil || !opened.Mode().IsRegular() || observedReparse(opened) || !os.SameFile(before, opened) {
		return result, failure("identity-record-changed")
	}
	data, e := io.ReadAll(io.LimitReader(file, MaxRecordBytes+1))
	if e != nil {
		return result, failure("identity-read-failed")
	}
	after, e := child.Lstat(RecordName)
	if e != nil || !after.Mode().IsRegular() || observedReparse(after) || !os.SameFile(opened, after) {
		return result, failure("identity-record-changed")
	}
	record, e := parseRecord(data)
	if e != nil {
		return result, failure("identity-record-invalid")
	}
	return Result{Record: record, Durability: "not-assessed"}, nil
}

func parseRecord(data []byte) (Record, error) {
	var record Record
	invalid := errors.New("invalid identity record")
	if len(data) == 0 || len(data) > MaxRecordBytes || !utf8.Valid(data) {
		return record, invalid
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	token, e := decoder.Token()
	if e != nil || token != json.Delim('{') {
		return record, invalid
	}
	seen := map[string]bool{}
	for decoder.More() {
		key, e := decoder.Token()
		if e != nil {
			return record, invalid
		}
		name, ok := key.(string)
		if !ok || seen[name] {
			return record, invalid
		}
		seen[name] = true
		switch name {
		case "identityRecordVersion":
			value, e := decoder.Token()
			number, ok := value.(json.Number)
			if e != nil || !ok || number.String() != "1" {
				return record, invalid
			}
			record.IdentityRecordVersion = 1
		case "installationId":
			var value any
			if e := decoder.Decode(&value); e != nil {
				return record, invalid
			}
			id, ok := value.(string)
			if !ok || !validUUID(id) {
				return record, invalid
			}
			record.InstallationID = id
		default:
			return record, invalid
		}
	}
	token, e = decoder.Token()
	if e != nil || token != json.Delim('}') || len(seen) != 2 {
		return record, invalid
	}
	if _, e = decoder.Token(); e != io.EOF {
		return record, invalid
	}
	return record, nil
}
func validUUID(id string) bool {
	if len(id) != 36 || id[8] != '-' || id[13] != '-' || id[18] != '-' || id[23] != '-' || id[14] != '4' {
		return false
	}
	if id[19] != '8' && id[19] != '9' && id[19] != 'a' && id[19] != 'b' {
		return false
	}
	for i, c := range id {
		if i == 8 || i == 13 || i == 18 || i == 23 {
			continue
		}
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return false
		}
	}
	return true
}
