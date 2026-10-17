//go:build linux || darwin

package jobevents

import (
	"crypto/sha256"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"testing"

	bolt "go.etcd.io/bbolt"
	"golang.org/x/sys/unix"
)

func TestReadOnlyStoreCopiesAndRefusesMutation(t *testing.T) {
	dir, writer := fixture(t)
	in := input(t, 810, 820, 1)
	if _, err := writer.AppendJobStatus(in); err != nil {
		t.Fatal("fixture append failed")
	}
	if err := writer.Close(); err != nil {
		t.Fatal("fixture writer close failed")
	}
	path := filepath.Join(dir, AnchorName, DatabaseName)
	before := digestFile(t, path)
	reader, err := OpenReadOnly(dir, installationID)
	own(t, reader)
	if err != nil || !reader.db.IsReadOnly() || reader.writableAttempt {
		t.Fatal("read-only engine not retained")
	}
	reset, err := reader.ReadAfter(Cursor{}, 1)
	if err != nil || !reset.ResetNeeded || len(reset.Snapshot) != 1 {
		t.Fatal("initial snapshot unavailable")
	}
	cursor := Cursor{reset.Next.StreamID, 0}
	batch, err := reader.ReadAfter(cursor, 1)
	if err != nil || len(batch.Events) != 1 || batch.Next.AfterSequence != 1 {
		t.Fatal("read-only batch failed")
	}
	*batch.Events[0].Payload.ExitCode = 99
	_, err = reader.AppendJobStatus(StatusInput{}) // Refuse before validation/transaction, without poisoning reads.
	failCode(t, err, "read-only")
	if reader.poisoned {
		t.Fatal("read-only refusal poisoned owner")
	}
	batch, err = reader.ReadAfter(cursor, 1)
	if err != nil || *batch.Events[0].Payload.ExitCode != 0 {
		t.Fatal("read-only copies or continued read failed")
	}
	found, err := reader.Lookup(in.EventID)
	if err != nil || !found.Found || found.Receipt.Sequence != 1 {
		t.Fatal("read-only lookup failed")
	}
	if err = reader.Close(); err != nil {
		t.Fatal("read-only close failed")
	}
	if digestFile(t, path) != before {
		t.Fatal("read-only operations changed database bytes")
	}
}

// Snapshot types/content without following links or opening a FIFO. Exact private
// fixture paths and contents never enter test failure output.
func readonlyTree(t *testing.T, dir string) map[string]string {
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
		} else if info.Mode()&os.ModeSymlink != 0 {
			target, err := os.Readlink(path)
			if err != nil {
				return err
			}
			value += ":" + target
		}
		values[rel] = value
		return nil
	})
	if err != nil {
		t.Fatal("fixture inventory failed")
	}
	return values
}
func sameReadonlyTree(a, b map[string]string) bool {
	if len(a) != len(b) {
		return false
	}
	for key, value := range a {
		if b[key] != value {
			return false
		}
	}
	return true
}

func TestReadOnlyRefusalPreservesState(t *testing.T) {
	for _, kind := range []string{"missing-anchor", "missing-file", "empty", "oversize", "symlink", "fifo", "corrupt-engine"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			anchor := filepath.Join(dir, AnchorName)
			if kind != "missing-anchor" && os.Mkdir(anchor, 0700) != nil {
				t.Fatal("anchor fixture failed")
			}
			file := filepath.Join(anchor, DatabaseName)
			switch kind {
			case "empty":
				if os.WriteFile(file, nil, 0600) != nil {
					t.Fatal("empty fixture failed")
				}
			case "oversize":
				f, err := os.OpenFile(file, os.O_CREATE|os.O_EXCL|os.O_RDWR, 0600)
				if err != nil {
					t.Fatal("oversize fixture failed")
				}
				err, closeErr := f.Truncate(MaxFileBytes+1), f.Close()
				if err != nil || closeErr != nil {
					t.Fatal("oversize fixture failed")
				}
			case "symlink":
				if os.WriteFile(filepath.Join(dir, "target"), []byte("preserve"), 0600) != nil || os.Symlink("../target", file) != nil {
					t.Fatal("link fixture failed")
				}
			case "fifo":
				if unix.Mkfifo(file, 0600) != nil {
					t.Fatal("fifo fixture failed")
				}
			case "corrupt-engine":
				if os.WriteFile(file, []byte("invalid engine"), 0600) != nil {
					t.Fatal("corrupt fixture failed")
				}
			}
			before := readonlyTree(t, dir)
			reader, err := OpenReadOnly(dir, installationID)
			own(t, reader)
			if err == nil {
				t.Fatal("invalid state admitted")
			}
			if reader != nil && (reader.writableAttempt || reader.Close() != nil) {
				t.Fatal("refused read-only owner changed mode or failed close")
			}
			if !sameReadonlyTree(before, readonlyTree(t, dir)) {
				t.Fatal("refusal changed state")
			}
		})
	}
}

func TestReadOnlyMetadataRefusalCannotConvertFreelist(t *testing.T) {
	dir, writer := fixture(t)
	if writer.Close() != nil {
		t.Fatal("fixture writer close failed")
	}
	path := filepath.Join(dir, AnchorName, DatabaseName)
	raw, err := bolt.Open(path, 0600, &bolt.Options{NoFreelistSync: true})
	if err != nil {
		t.Fatal("raw fixture open failed")
	}
	err = raw.Update(func(tx *bolt.Tx) error { return tx.Bucket(metaBucket).Put(headerKey, []byte(`{"version":999}`)) })
	closeErr := raw.Close()
	if err != nil || closeErr != nil {
		t.Fatal("raw fixture mutation failed")
	}
	before := digestFile(t, path)
	reader, err := OpenReadOnly(dir, installationID)
	own(t, reader)
	failCode(t, err, "metadata")
	if reader == nil || reader.writableAttempt || reader.Close() != nil {
		t.Fatal("invalid metadata owner boundary failed")
	}
	if digestFile(t, path) != before {
		t.Fatal("read-only metadata refusal changed database")
	}
}

func TestReadOnlyCloseFailureRemainsUncertain(t *testing.T) {
	dir, writer := fixture(t)
	if writer.Close() != nil {
		t.Fatal("fixture writer close failed")
	}
	reader, err := OpenReadOnly(dir, installationID)
	if reader != nil {
		t.Cleanup(func() {
			if reader.Close() == nil {
				t.Error("sticky close uncertainty erased")
			}
		})
	}
	if err != nil {
		t.Fatal("read-only owner open failed")
	}
	// Test-only post-close fault; actual engine/roots close before this hook.
	reader.closeFault = func() error { return errors.New("private close cause") }
	err = reader.Close()
	failCode(t, err, "close-uncertain")
	err = reader.Close()
	failCode(t, err, "close-uncertain")
	_, err = reader.ReadAfter(Cursor{}, 1)
	failCode(t, err, "closed-or-unready")
}
