//go:build linux

package diagnostic

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"reflect"
	"testing"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/installation"
)

func contextFixture(t *testing.T) (string, installation.Result, []byte) {
	t.Helper()
	dir := t.TempDir()
	result, err := installation.Init(dir)
	if err != nil {
		t.Fatal("owned fixture initialization failed")
	}
	record, err := os.ReadFile(filepath.Join(dir, installation.AnchorName, installation.RecordName))
	if err != nil {
		t.Fatal("owned fixture record unavailable")
	}
	return dir, result, record
}

func assertContextPreserved(t *testing.T, dir string, before []byte) {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(dir, installation.AnchorName, installation.RecordName))
	if err != nil || !bytes.Equal(data, before) {
		t.Fatal("context operation changed identity record")
	}
	root, err := os.ReadDir(dir)
	if err != nil || len(root) != 1 || root[0].Name() != installation.AnchorName {
		t.Fatal("context operation changed owned root")
	}
	anchor, err := os.ReadDir(filepath.Join(dir, installation.AnchorName))
	if err != nil || len(anchor) != 1 || anchor[0].Name() != installation.RecordName {
		t.Fatal("context operation changed anchor contents")
	}
}

func TestContextActualReadAndOutputPreservation(t *testing.T) {
	dir, initialized, record := contextFixture(t)
	prior, err := installation.Read(dir)
	if err != nil {
		t.Fatal("fixture read failed")
	}
	boot, err := bootidentity.Read()
	if err != nil {
		t.Fatal("actual boot read failed")
	}
	now := time.Date(2026, 10, 16, 17, 0, 0, 123, time.FixedZone("fixture", 3600))
	var first contextObservation
	for i := 0; i < 2; i++ {
		var out, errOut bytes.Buffer
		code := Run([]string{"context", "read", "--state-dir", dir}, &out, &errOut, func() time.Time { return now }, CurrentRuntime)
		if code != 0 || errOut.Len() != 0 {
			t.Fatal("actual context read failed")
		}
		var observed contextObservation
		if err := json.Unmarshal(out.Bytes(), &observed); err != nil {
			t.Fatal("context output not JSON")
		}
		want := contextObservation{1, "0.1.3", "2026-10-16T16:00:00.000000123Z", CurrentRuntime(), initialized.Record.InstallationID, boot.String(), prior.Durability, "sequential-not-atomic"}
		if observed != want {
			t.Fatal("actual context contract mismatch")
		}
		var fields map[string]json.RawMessage
		if err := json.Unmarshal(out.Bytes(), &fields); err != nil || len(fields) != 8 {
			t.Fatal("unexpected context field set")
		}
		if i == 0 {
			first = observed
		} else if observed != first {
			t.Fatal("finite context reads unequal")
		}
		assertContextPreserved(t, dir, record)
	}
	for _, writer := range []io.Writer{failingWriter{}, shortWriter{}} {
		var errOut bytes.Buffer
		if code := Run([]string{"context", "read", "--state-dir", dir}, writer, &errOut, time.Now, CurrentRuntime); code != 1 || errOut.String() != "cloudy-host: context output failed\n" {
			t.Fatal("context output failure not safe")
		}
		assertContextPreserved(t, dir, record)
	}
	if code := Run([]string{"context", "read", "--state-dir", dir}, failingWriter{}, failingWriter{}, time.Now, CurrentRuntime); code != 1 {
		t.Fatal("stderr failure changed output-failure exit")
	}
	assertContextPreserved(t, dir, record)
}

func TestContextMissingAndIncompleteNeverInitialize(t *testing.T) {
	for _, anchor := range []bool{false, true} {
		dir := t.TempDir()
		if anchor {
			if err := os.Mkdir(filepath.Join(dir, installation.AnchorName), 0700); err != nil {
				t.Fatal("fixture anchor failed")
			}
		}
		var out, errOut bytes.Buffer
		if code := Run([]string{"context", "read", "--state-dir", dir}, &out, &errOut, forbiddenClock, forbiddenRuntime); code != 1 || out.Len() != 0 || errOut.String() != "cloudy-host: context installation read failed\n" {
			t.Fatal("missing identity did not refuse safely")
		}
		entries, err := os.ReadDir(dir)
		want := 0
		if anchor {
			want = 1
			inside, err := os.ReadDir(filepath.Join(dir, installation.AnchorName))
			if err != nil || len(inside) != 0 {
				t.Fatal("incomplete anchor modified")
			}
		}
		if err != nil || len(entries) != want {
			t.Fatal("missing state was implicitly initialized")
		}
	}
}

func TestContextFailureOrderAndPrivacy(t *testing.T) {
	private := errors.New("private state path and identifier")
	for _, stage := range []string{"installation", "boot"} {
		t.Run(stage, func(t *testing.T) {
			var order []string
			readInstallation := func(string) (installation.Result, error) {
				order = append(order, "installation")
				if stage == "installation" {
					return installation.Result{}, private
				}
				return installation.Result{}, nil
			}
			readBoot := func() (bootidentity.ID, error) {
				order = append(order, "boot")
				return bootidentity.ID{}, private
			}
			var out, errOut bytes.Buffer
			code := observeContext("not opened by test readers", &out, &errOut, forbiddenClock, forbiddenRuntime, readInstallation, readBoot)
			if code != 1 || out.Len() != 0 || errOut.String() != "cloudy-host: context "+stage+" read failed\n" {
				t.Fatal("private reader failure escaped boundary")
			}
			want := []string{"installation"}
			if stage == "boot" {
				want = append(want, "boot")
			}
			if !reflect.DeepEqual(order, want) {
				t.Fatal("reader failure order incorrect")
			}
		})
	}
}

func TestContextTimestampAfterReadersAndDurabilityPassthrough(t *testing.T) {
	boot, err := bootidentity.Read()
	if err != nil {
		t.Fatal("actual boot unavailable")
	}
	var order []string
	var out, errOut bytes.Buffer
	code := observeContext("test-only readers", &out, &errOut,
		func() time.Time { order = append(order, "clock"); return time.Unix(0, 0) },
		func() Runtime { order = append(order, "runtime"); return CurrentRuntime() },
		func(string) (installation.Result, error) {
			order = append(order, "installation")
			return installation.Result{Durability: "test-only-durability"}, nil
		},
		func() (bootidentity.ID, error) { order = append(order, "boot"); return boot, nil })
	if code != 0 || errOut.Len() != 0 || !reflect.DeepEqual(order, []string{"installation", "boot", "clock", "runtime"}) {
		t.Fatal("context observation order incorrect")
	}
	var observed contextObservation
	if err := json.Unmarshal(out.Bytes(), &observed); err != nil || observed.IdentityDurability != "test-only-durability" {
		t.Fatal("reader durability not passed through")
	}
}
