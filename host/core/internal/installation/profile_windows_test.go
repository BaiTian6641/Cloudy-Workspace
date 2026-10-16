//go:build windows

package installation

import (
	"bytes"
	"context"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

func windowsFailure(t *testing.T, err error) *Failure {
	t.Helper()
	var f *Failure
	if !errors.As(err, &f) {
		t.Fatalf("want safe failure: %v", err)
	}
	return f
}
func TestWindowsActualFixedNTFSInitReadAndRetainedClaim(t *testing.T) {
	dir := t.TempDir()
	var stages []string
	created, err := initialize(dir, func(stage string) error { stages = append(stages, stage); return nil })
	if err != nil {
		t.Fatalf("actual fixed-NTFS profile: %v", err)
	}
	if !validUUID(created.Record.InstallationID) || created.RecordVersion != 2 || created.StorageProfile != WindowsStorageProfile || created.Durability != "write-through-file-flush-completed" {
		t.Fatal("invalid v2 result")
	}
	if strings.Join(stages, ",") != "storage-eligibility,anchor-create,anchor-check,anchor-write,anchor-sync,anchor-postcheck,anchor-close" {
		t.Fatal("native boundary not exercised", stages)
	}
	path := filepath.Join(dir, AnchorName)
	before, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	observed, err := Read(dir)
	if err != nil || observed.Record != created.Record || observed.Durability != "not-assessed" {
		t.Fatal("readback failed", err)
	}
	if f := windowsFailure(t, func() error { _, e := Init(dir); return e }()); f.Claimed || f.Code != "identity-already-claimed" {
		t.Fatal("existing claim altered")
	}
	after, err := os.ReadFile(path)
	if err != nil || !bytes.Equal(before, after) {
		t.Fatal("existing anchor modified")
	}
}
func TestWindowsLegacyReadOnlyAndFileVersionDispatch(t *testing.T) {
	id := "12345678-1234-4234-8234-123456789abc"
	for _, kind := range []string{"legacy", "empty-legacy", "partial-file", "v1-file", "unknown-profile", "v2"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			anchor := filepath.Join(dir, AnchorName)
			var file string
			var data []byte
			switch kind {
			case "legacy", "empty-legacy":
				if err := os.Mkdir(anchor, 0700); err != nil {
					t.Fatal(err)
				}
				file = filepath.Join(anchor, RecordName)
				data = []byte(`{"identityRecordVersion":1,"installationId":"` + id + `"}`)
				if kind == "empty-legacy" {
					file = ""
				}
			case "partial-file":
				file = anchor
				data = []byte(`{"identityRecordVersion":2`)
			case "v1-file":
				file = anchor
				data = []byte(`{"identityRecordVersion":1,"installationId":"` + id + `"}`)
			case "unknown-profile":
				file = anchor
				data = []byte(`{"identityRecordVersion":2,"storageProfile":"unknown","installationId":"` + id + `"}`)
			case "v2":
				file = anchor
				data = []byte(`{"identityRecordVersion":2,"storageProfile":"windows-ntfs-anchor-file-v1","installationId":"` + id + `"}`)
			}
			if file != "" {
				if err := os.WriteFile(file, data, 0600); err != nil {
					t.Fatal(err)
				}
			}
			result, err := Read(dir)
			if kind == "legacy" || kind == "v2" {
				if err != nil || result.Record.InstallationID != id {
					t.Fatal("valid read failed", err)
				}
				if kind == "legacy" && (result.RecordVersion != 1 || result.StorageProfile != LegacyStorageProfile) {
					t.Fatal("legacy migrated")
				}
			} else if err == nil {
				t.Fatal("invalid anchor accepted")
			}
			if _, err := Init(dir); err == nil {
				t.Fatal("anchor reinitialized")
			}
			if file != "" {
				after, err := os.ReadFile(file)
				if err != nil || !bytes.Equal(after, data) {
					t.Fatal("read/init changed bytes")
				}
			}
		})
	}
}
func TestWindowsFailuresRetainPermanentAnchor(t *testing.T) {
	for _, stage := range []string{"anchor-check", "anchor-write", "anchor-sync", "anchor-postcheck", "anchor-close"} {
		t.Run(stage, func(t *testing.T) {
			dir := t.TempDir()
			hit := false
			cause := errors.New("private-path secret")
			_, err := initialize(dir, func(at string) error {
				if at == stage {
					hit = true
					return cause
				}
				return nil
			})
			f := windowsFailure(t, err)
			if !hit || !f.Claimed || !errors.Is(f.cause, cause) || strings.Contains(f.Error(), "private") {
				t.Fatal("wrong retained failure boundary")
			}
			info, err := os.Lstat(filepath.Join(dir, AnchorName))
			if err != nil || !info.Mode().IsRegular() {
				t.Fatal("claim lost", err)
			}
			if _, err := Init(dir); err == nil {
				t.Fatal("failed claim replaced")
			}
		})
	}
	dir := t.TempDir()
	_, err := initialize(dir, func(stage string) error {
		if stage == "storage-eligibility" {
			return errors.New("query unavailable")
		}
		return nil
	})
	if f := windowsFailure(t, err); f.Claimed || f.Code != "identity-storage-query-failed" {
		t.Fatal("bad preclaim failure")
	}
	if _, err := os.Lstat(filepath.Join(dir, AnchorName)); !errors.Is(err, os.ErrNotExist) {
		t.Fatal("unsupported gate claimed state")
	}
}
func TestWindowsConcurrentCandidatesAndLegacyShareSingleLeaf(t *testing.T) {
	for _, mixed := range []bool{false, true} {
		t.Run(map[bool]string{false: "candidate", true: "legacy-mixed"}[mixed], func(t *testing.T) {
			dir := t.TempDir()
			start := make(chan struct{})
			attempts := make(chan error, 12)
			var wg sync.WaitGroup
			for i := range 12 {
				wg.Add(1)
				go func(index int) {
					defer wg.Done()
					<-start
					var err error
					if mixed && index%2 == 0 {
						_, err = initializeDirectory(dir, nil)
					} else {
						_, err = Init(dir)
					}
					attempts <- err
				}(i)
			}
			close(start)
			wg.Wait()
			close(attempts)
			count := 0
			for err := range attempts {
				if err == nil {
					count++
					continue
				}
				f := windowsFailure(t, err)
				if f.Claimed {
					count++
				} else if f.Code != "identity-already-claimed" && f.Code != "identity-claim-failed" {
					t.Fatalf("unrelated concurrent failure: %v", err)
				}
			}
			if count != 1 {
				t.Fatalf("namespace claimants%d", count)
			}
			if _, err := os.Lstat(filepath.Join(dir, AnchorName)); err != nil {
				t.Fatal("permanent anchor absent")
			}
		})
	}
	// Ordered old winner may retain an incomplete directory and no usable ID.
	dir := t.TempDir()
	_, err := initializeDirectory(dir, func(stage string) error {
		if stage == "claim-parent-sync" {
			return errors.New("controlled legacy boundary")
		}
		return nil
	})
	if f := windowsFailure(t, err); !f.Claimed {
		t.Fatal("legacy did not claim")
	}
	if f := windowsFailure(t, func() error { _, e := Init(dir); return e }()); f.Claimed {
		t.Fatal("new profile won existing legacy leaf")
	}
	// New winner blocks the old directory algorithm before it generates an ID.
	dir = t.TempDir()
	if _, err := Init(dir); err != nil {
		t.Fatal(err)
	}
	if f := windowsFailure(t, func() error { _, e := initializeDirectory(dir, nil); return e }()); f.Claimed {
		t.Fatal("old profile won existing file leaf")
	}
}

// Actual process exit on Windows leaves the permanent claim at each boundary;
// this is process-loss evidence when run, never power-loss durability evidence.
func TestWindowsClaimSurvivesProcessExit(t *testing.T) {
	if stage := os.Getenv("CLOUDY_OWNED_IDENTITY_TEST_EXIT_STAGE"); stage != "" {
		_, err := initialize(os.Getenv("CLOUDY_OWNED_IDENTITY_TEST_ROOT"), func(at string) error {
			if at == stage {
				os.Exit(23)
			}
			return nil
		})
		t.Fatalf("controlled stage not reached: %v", err)
	}
	for _, stage := range []string{"anchor-check", "anchor-write", "anchor-sync", "anchor-close"} {
		t.Run(stage, func(t *testing.T) {
			dir := t.TempDir()
			ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
			defer cancel()
			command := exec.CommandContext(ctx, os.Args[0], "-test.run=^TestWindowsClaimSurvivesProcessExit$")
			command.Env = append(os.Environ(), "CLOUDY_OWNED_IDENTITY_TEST_EXIT_STAGE="+stage, "CLOUDY_OWNED_IDENTITY_TEST_ROOT="+dir)
			output, err := command.CombinedOutput()
			var exit *exec.ExitError
			if !errors.As(err, &exit) || exit.ExitCode() != 23 || len(output) != 0 {
				t.Fatal("controlled child boundary failed")
			}
			info, err := os.Lstat(filepath.Join(dir, AnchorName))
			if err != nil || !info.Mode().IsRegular() {
				t.Fatal("process loss removed claim")
			}
			if _, err := Init(dir); err == nil {
				t.Fatal("process loss allowed regeneration")
			}
		})
	}
}

func TestWindowsObservedReparseRoutesAreRefused(t *testing.T) {
	for _, kind := range []string{"root", "anchor", "legacy-record"} {
		t.Run(kind, func(t *testing.T) {
			dir := t.TempDir()
			target := t.TempDir()
			link := filepath.Join(dir, AnchorName)
			if kind == "root" {
				link = filepath.Join(dir, "alias")
			}
			if kind == "legacy-record" {
				if err := os.Mkdir(link, 0700); err != nil {
					t.Fatal(err)
				}
				link = filepath.Join(link, RecordName)
			}
			if err := os.Symlink(target, link); err != nil {
				if unsupportedSymlinkFixture(err) {
					t.Skip("exact Windows symlink-fixture privilege unavailable")
				}
				t.Fatal("unexpected symlink fixture error", err)
			}
			selected := dir
			if kind == "root" {
				selected = link
			}
			if _, err := Read(selected); err == nil {
				t.Fatal("observed reparse accepted")
			}
			if _, err := Init(selected); err == nil {
				t.Fatal("observed reparse reinitialized")
			}
			info, err := os.Lstat(link)
			if err != nil || info.Mode()&os.ModeSymlink == 0 {
				t.Fatal("reparse fixture altered")
			}
		})
	}
}

// An unchanged later path/handle pair must not erase the original observation.
func TestWindowsObservedAnchorRejectsReplacementHandle(t *testing.T) {
	dir := t.TempDir()
	anchor := filepath.Join(dir, AnchorName)
	if err := os.WriteFile(anchor, []byte("original"), 0600); err != nil {
		t.Fatal(err)
	}
	root, err := os.OpenRoot(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer root.Close()
	observed, err := root.Lstat(AnchorName)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Rename(anchor, filepath.Join(dir, "retained-original")); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(anchor, []byte("replacement"), 0600); err != nil {
		t.Fatal(err)
	}
	file, err := os.Open(anchor)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	if err := consistentAnchor(root, file); err != nil {
		t.Fatal("replacement is internally consistent", err)
	}
	if err := consistentObservedAnchor(observed, file); err == nil {
		t.Fatal("replacement accepted against original observation")
	}
	current, err := root.Lstat(AnchorName)
	if err != nil {
		t.Fatal(err)
	}
	if err := consistentObservedAnchor(current, file); err != nil {
		t.Fatal("unchanged observation refused", err)
	}
}
