//go:build !windows

package installation

import (
	"bytes"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"testing"
)

const fixtureID = "12345678-1234-4234-8234-123456789abc"

func fixtureRecord() []byte {
	return []byte(`{"identityRecordVersion":1,"installationId":"` + fixtureID + `"}` + "\n")
}
func requireCode(t *testing.T, err error, code string) *Failure {
	t.Helper()
	var failure *Failure
	if !errors.As(err, &failure) || failure.Code != code {
		t.Fatalf("want %s, got %v", code, err)
	}
	return failure
}

// probeDirectorySync isolates the exact production handle operation before any
// Init result can be skipped. A successfully opened owned directory is required;
// no generic permission or initialization failure establishes this limitation.
func probeDirectorySync(t *testing.T) bool {
	t.Helper()
	root, err := os.OpenRoot(t.TempDir())
	if err != nil {
		t.Fatalf("probe root open: %v", err)
	}
	dir, err := root.Open(".")
	if err != nil {
		_ = root.Close()
		t.Fatalf("probe directory open: %v", err)
	}
	syncErr := dir.Sync()
	closeErr := dir.Close()
	rootCloseErr := root.Close()
	if closeErr != nil || rootCloseErr != nil {
		t.Fatalf("probe close failed: %v / %v", closeErr, rootCloseErr)
	}
	if syncErr == nil {
		return false
	}
	if !unsupportedDirectoryHandleSync(syncErr) {
		t.Fatalf("unexpected directory Sync failure: %v", syncErr)
	}
	return true
}

// The isolated probe is necessary but insufficient: the actual initializer's
// failure must carry this exact unsupported operation/cause too. An EIO at the
// same stage must remain a failure even when a separate probe observed EACCES.
func provenClaimSyncLimitation(err error, stages []string) bool {
	var f *Failure
	return errors.As(err, &f) && f.Code == "identity-initialization-incomplete" && f.Claimed && !f.Published &&
		len(stages) == 1 && stages[0] == "claim-parent-sync" && unsupportedDirectoryHandleSync(f.cause)
}

func assertClaimedDirectorySyncBoundary(t *testing.T, dir string, err error, stages []string) {
	t.Helper()
	if !provenClaimSyncLimitation(err, stages) {
		t.Fatalf("actual failure does not prove the exact directory-Sync handle limitation: %v / %v", err, stages)
	}
	f := requireCode(t, err, "identity-initialization-incomplete")
	if !f.Claimed || f.Published {
		t.Fatalf("not the claimed/unpublished boundary: %+v", f)
	}
	anchor := filepath.Join(dir, AnchorName)
	info, e := os.Lstat(anchor)
	if e != nil || !info.IsDir() {
		t.Fatalf("claim anchor missing/invalid: %v", e)
	}
	entries, e := os.ReadDir(anchor)
	if e != nil || len(entries) != 0 {
		t.Fatalf("expected untouched empty claim: %v / %d entries", e, len(entries))
	}
	_, e = Init(dir)
	requireCode(t, e, "identity-already-claimed")
	_, e = Read(dir)
	requireCode(t, e, "identity-record-invalid")
}

func initializeFixture(t *testing.T) (string, Result) {
	t.Helper()
	unsupported := probeDirectorySync(t)
	dir := t.TempDir()
	var stages []string
	result, err := initialize(dir, func(stage string) error { stages = append(stages, stage); return nil })
	if err != nil {
		if !unsupported {
			t.Fatal(err)
		}
		assertClaimedDirectorySyncBoundary(t, dir, err, stages)
		t.Skip("isolated directory Sync probe proves read-only handle access-right failure; actual init retained claimed/unpublished anchor")
	}
	if unsupported {
		t.Fatal("initialization unexpectedly passed after isolated Sync failure")
	}
	return dir, result
}
func TestExplicitInitReadAndNoRegeneration(t *testing.T) {
	dir, created := initializeFixture(t)
	if !validUUID(created.Record.InstallationID) || created.Durability != "sync-completed" {
		t.Fatal(created)
	}
	read, err := Read(dir)
	if err != nil || read.Record != created.Record || read.Durability != "not-assessed" {
		t.Fatalf("read=%+v err=%v", read, err)
	}
	requireCode(t, func() error { _, e := Init(dir); return e }(), "identity-already-claimed")
	record := filepath.Join(dir, AnchorName, RecordName)
	if err := os.Remove(record); err != nil {
		t.Fatal(err)
	}
	_, err = Read(dir)
	requireCode(t, err, "identity-record-invalid")
	_, err = Init(dir)
	requireCode(t, err, "identity-already-claimed")
	if _, err := os.Stat(record); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("record regenerated: %v", err)
	}
}
func TestMissingReadDoesNotCreateAndBadParentRejected(t *testing.T) {
	dir := t.TempDir()
	_, err := Read(dir)
	requireCode(t, err, "identity-state-incomplete")
	entries, err := os.ReadDir(dir)
	if err != nil || len(entries) != 0 {
		t.Fatal("read wrote state")
	}
	for _, path := range []string{"", filepath.Join(dir, "missing")} {
		_, err := Init(path)
		requireCode(t, err, "invalid-state-directory")
	}
}
func TestExistingAnchorNeverReinitialized(t *testing.T) {
	for _, kind := range []string{"empty", "corrupt", "file"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			anchor := filepath.Join(dir, AnchorName)
			if kind == "file" {
				if err := os.WriteFile(anchor, []byte("do not replace"), 0o600); err != nil {
					t.Fatal(err)
				}
			} else {
				if err := os.Mkdir(anchor, 0o700); err != nil {
					t.Fatal(err)
				}
				if kind == "corrupt" {
					if err := os.WriteFile(filepath.Join(anchor, RecordName), []byte("corrupt"), 0o600); err != nil {
						t.Fatal(err)
					}
				}
			}
			_, err := Init(dir)
			requireCode(t, err, "identity-already-claimed")
			if kind == "corrupt" {
				raw, e := os.ReadFile(filepath.Join(anchor, RecordName))
				if e != nil || string(raw) != "corrupt" {
					t.Fatal("corrupt bytes changed")
				}
			}
		})
	}
}
func TestConcurrentInitHasSingleClaimant(t *testing.T) {
	unsupported := probeDirectorySync(t)
	dir := t.TempDir()
	const workers = 12
	type attempt struct {
		err    error
		stages []string
	}
	results := make(chan attempt, workers)
	ids := make(chan string, workers)
	var wg sync.WaitGroup
	for range workers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			var stages []string
			r, e := initialize(dir, func(stage string) error { stages = append(stages, stage); return nil })
			if e == nil {
				ids <- r.Record.InstallationID
			}
			results <- attempt{e, stages}
		}()
	}
	wg.Wait()
	close(results)
	close(ids)
	success := 0
	incomplete := 0
	var boundaryFailure error
	var boundaryStages []string
	for result := range results {
		e := result.err
		if e == nil {
			success++
			continue
		}
		f := requireFailure(t, e)
		if f.Code != "identity-already-claimed" {
			if unsupported && f.Code == "identity-initialization-incomplete" && f.Claimed && !f.Published {
				incomplete++
				boundaryFailure = e
				boundaryStages = result.stages
				continue
			}
			t.Fatal(e)
		}
	}
	if unsupported {
		if success != 0 || incomplete != 1 {
			t.Fatalf("unexpected concurrent outcomes: successes%d incomplete%d", success, incomplete)
		}
		assertClaimedDirectorySyncBoundary(t, dir, boundaryFailure, boundaryStages)
		t.Skip("isolated directory Sync handle limitation; exactly one incomplete claim and no published identity")
	}
	if success != 1 {
		t.Fatalf("successful initializers=%d", success)
	}
	r, e := Read(dir)
	if e != nil {
		t.Fatal(e)
	}
	if r.Record.InstallationID != <-ids {
		t.Fatal("winner replaced")
	}
}
func requireFailure(t *testing.T, err error) *Failure {
	t.Helper()
	var f *Failure
	if !errors.As(err, &f) {
		t.Fatal(err)
	}
	return f
}
func TestStrictRecordParser(t *testing.T) {
	valid := fixtureRecord()
	r, e := parseRecord(valid)
	if e != nil || r.InstallationID != fixtureID {
		t.Fatal(r, e)
	}
	cases := [][]byte{
		nil, []byte("null"), []byte("[]"), []byte("{}"), append(valid, []byte("{}")...), bytes.Repeat([]byte(" "), MaxRecordBytes+1),
		[]byte(`{"identityRecordVersion":"1","installationId":"` + fixtureID + `"}`),
		[]byte(`{"identityRecordVersion":null,"installationId":"` + fixtureID + `"}`),
		[]byte(`{"identityRecordVersion":1.0,"installationId":"` + fixtureID + `"}`),
		[]byte(`{"identityRecordVersion":2,"installationId":"` + fixtureID + `"}`),
		[]byte(`{"identityRecordVersion":1,"identityRecordVersion":1,"installationId":"` + fixtureID + `"}`),
		[]byte(`{"identityRecordVersion":1,"installationId":"` + fixtureID + `","installationId":"` + fixtureID + `"}`),
		[]byte(`{"identityRecordVersion":1,"installationId":null}`),
		[]byte(`{"identityRecordVersion":1,"installationId":"` + fixtureID + `","extra":true}`),
		[]byte(strings.Replace(string(valid), fixtureID, strings.ToUpper(fixtureID), 1)),
		[]byte(strings.Replace(string(valid), "-4234-", "-1234-", 1)),
		[]byte(strings.Replace(string(valid), "-8234-", "-7234-", 1)),
		append([]byte{0xff}, valid...), valid[:len(valid)-3],
	}
	for i, data := range cases {
		if _, err := parseRecord(data); err == nil {
			t.Errorf("invalid case%d accepted: %q", i, data)
		}
	}
}
func TestRecordReadFailuresPreserveBytes(t *testing.T) {
	for _, data := range [][]byte{[]byte("broken"), bytes.Repeat([]byte("x"), MaxRecordBytes+1)} {
		dir := t.TempDir()
		if e := os.Mkdir(filepath.Join(dir, AnchorName), 0o700); e != nil {
			t.Fatal(e)
		}
		path := filepath.Join(dir, AnchorName, RecordName)
		if e := os.WriteFile(path, data, 0o600); e != nil {
			t.Fatal(e)
		}
		_, err := Read(dir)
		requireCode(t, err, "identity-record-invalid")
		_, err = Init(dir)
		requireCode(t, err, "identity-already-claimed")
		got, e := os.ReadFile(path)
		if e != nil || !bytes.Equal(got, data) {
			t.Fatal("invalid record changed")
		}
	}
}
func TestObservedSymlinkAndNonregularRejected(t *testing.T) {
	for _, target := range []string{"anchor", "record", "parent"} {
		t.Run(target, func(t *testing.T) {
			dir := t.TempDir()
			other := t.TempDir()
			link := filepath.Join(dir, AnchorName)
			if target == "record" {
				if e := os.Mkdir(link, 0o700); e != nil {
					t.Fatal(e)
				}
				link = filepath.Join(link, RecordName)
			}
			if target == "parent" {
				link = filepath.Join(dir, "alias")
			}
			if e := os.Symlink(other, link); e != nil {
				if unsupportedSymlinkFixture(e) {
					t.Skip("Windows symlink fixture requires an unavailable privilege")
				}
				t.Fatalf("unexpected symlink fixture failure: %v", e)
			}
			selected := dir
			if target == "parent" {
				selected = link
			}
			_, err := Read(selected)
			if err == nil {
				t.Fatal("symlink accepted")
			}
			_, err = Init(selected)
			if err == nil {
				t.Fatal("symlink replaced")
			}
		})
	}
	dir := t.TempDir()
	if e := os.MkdirAll(filepath.Join(dir, AnchorName, RecordName), 0o700); e != nil {
		t.Fatal(e)
	}
	_, err := Read(dir)
	requireCode(t, err, "identity-record-invalid")
}
func TestInjectedFailureKeepsClaimAndPublicationBoundary(t *testing.T) {
	unsupported := probeDirectorySync(t)
	stages := []string{"claim-parent-sync", "temp-create", "temp-write", "temp-sync", "temp-close", "publish-link", "publish-dir-sync", "temp-remove", "cleanup-dir-sync"}
	for _, stage := range stages {
		t.Run(stage, func(t *testing.T) {
			dir := t.TempDir()
			hit := false
			var stages []string
			_, err := initialize(dir, func(at string) error {
				stages = append(stages, at)
				if at == stage {
					hit = true
					return errors.New("private-path token")
				}
				return nil
			})
			f := requireFailure(t, err)
			if !hit {
				if !unsupported {
					t.Fatal("injected boundary not reached")
				}
				assertClaimedDirectorySyncBoundary(t, dir, err, stages)
				t.Skip("isolated directory Sync handle limitation prevents reaching this later injected boundary")
			}
			if !f.Claimed {
				t.Fatal("claim not retained")
			}
			if strings.Contains(f.Error(), "private") {
				t.Fatal("unsafe error")
			}
			if _, e := os.Stat(filepath.Join(dir, AnchorName)); e != nil {
				t.Fatal("anchor removed")
			}
			_, e := Init(dir)
			requireCode(t, e, "identity-already-claimed")
			published := stage == "publish-dir-sync" || stage == "temp-remove" || stage == "cleanup-dir-sync"
			if f.Published != published {
				t.Fatalf("published=%v want%v", f.Published, published)
			}
			_, e = Read(dir)
			if published {
				if e != nil {
					t.Fatalf("published ID unreadable: %v", e)
				}
			} else if e == nil {
				t.Fatal("prepublication failure exposes ID")
			}
		})
	}
}

func TestActualDirectorySyncSkipRejectsOtherCauses(t *testing.T) {
	for _, cause := range []error{
		&os.PathError{Op: "sync", Err: syscall.EIO},
		&os.PathError{Op: "open", Err: syscall.EACCES},
		errors.New("Access is denied"), nil,
	} {
		failure := &Failure{Code: "identity-initialization-incomplete", Claimed: true, Published: false, cause: cause}
		if provenClaimSyncLimitation(failure, []string{"claim-parent-sync"}) {
			t.Fatalf("unrelated actual cause permits skip: %v", cause)
		}
		if failure.Error() != "identity-initialization-incomplete" {
			t.Fatal("public error leaks cause")
		}
	}
	// The actual operation's cause must survive the state-machine boundary;
	// a matching stage alone must not permit a failed kernel operation to skip.
	dir := t.TempDir()
	var stages []string
	_, err := initialize(dir, func(stage string) error {
		stages = append(stages, stage)
		if stage == "claim-parent-sync" {
			return &os.PathError{Op: "sync", Path: "private-path", Err: syscall.EIO}
		}
		return nil
	})
	f := requireCode(t, err, "identity-initialization-incomplete")
	if !errors.Is(f.cause, syscall.EIO) {
		t.Fatal("actual cause was erased")
	}
	if provenClaimSyncLimitation(err, stages) {
		t.Fatal("actual sync EIO permits skip")
	}
	if err.Error() != "identity-initialization-incomplete" {
		t.Fatal("actual cause leaked publicly")
	}
}
