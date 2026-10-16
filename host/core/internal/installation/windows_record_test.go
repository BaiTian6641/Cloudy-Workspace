package installation

import (
	"bytes"
	"errors"
	"io"
	"strings"
	"testing"
)

func TestWindowsRecordStrictProfile(t *testing.T) {
	id := "12345678-1234-4234-8234-123456789abc"
	valid := []byte(`{"identityRecordVersion":2,"storageProfile":"windows-ntfs-anchor-file-v1","installationId":"` + id + `"}`)
	got, err := parseWindowsRecord(valid)
	if err != nil || got.InstallationID != id || got.IdentityRecordVersion != 2 || got.StorageProfile != WindowsStorageProfile {
		t.Fatal("valid v2 rejected", err)
	}
	cases := [][]byte{nil, []byte("null"), []byte("{}"), append(append([]byte{}, valid...), []byte("{}")...), bytes.Repeat([]byte("x"), 513), append([]byte{0xff}, valid...),
		[]byte(strings.Replace(string(valid), `:2,`, `:2.0,`, 1)), []byte(strings.Replace(string(valid), `:2,`, `:2e0,`, 1)), []byte(strings.Replace(string(valid), `:2,`, `:"2",`, 1)),
		[]byte(strings.Replace(string(valid), `:2,`, `:null,`, 1)), []byte(strings.Replace(string(valid), `:2,`, `:1,`, 1)),
		[]byte(strings.Replace(string(valid), WindowsStorageProfile, "other", 1)), []byte(strings.Replace(string(valid), `"storageProfile":"`+WindowsStorageProfile+`",`, "", 1)),
		[]byte(strings.Replace(string(valid), `"installationId":`, `"storageProfile":"`+WindowsStorageProfile+`","installationId":`, 1)),
		[]byte(strings.Replace(string(valid), id, strings.ToUpper(id), 1)), []byte(strings.Replace(string(valid), id, "invalid", 1))}
	for i, data := range cases {
		if _, err := parseWindowsRecord(data); err == nil {
			t.Errorf("invalid v2 fixture%d accepted", i)
		}
	}
	if _, err := parseRecord(valid); err == nil {
		t.Fatal("legacy v1 parser accepted v2")
	}
}
func TestVolumeRootRequiresActualGUIDNamespace(t *testing.T) {
	root := `\\?\Volume{12345678-1234-4234-8234-123456789abc}\`
	for _, path := range []string{root, root + `owned\state`} {
		if got, err := windowsVolumeRoot(path); err != nil || got != root {
			t.Fatal("valid volume root rejected")
		}
	}
	for _, path := range []string{`C:\state`, `\\server\share\state`, `\\?\UNC\server\share\`, `\\?\Volume{not-a-guid}\`, strings.TrimSuffix(root, `\`), ""} {
		if _, err := windowsVolumeRoot(path); err == nil {
			t.Fatal("non-GUID path accepted")
		}
	}
}

type profileShortWriter struct{}

func (profileShortWriter) Write(data []byte) (int, error) { return len(data) / 2, nil }
func TestWindowsProfileShortWriteIsFailure(t *testing.T) {
	if err := writeRecord(profileShortWriter{}, []byte("record")); !errors.Is(err, io.ErrShortWrite) {
		t.Fatal("short write accepted")
	}
}
