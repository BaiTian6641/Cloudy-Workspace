package installation

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"regexp"
	"unicode/utf8"
)

const WindowsStorageProfile = "windows-ntfs-anchor-file-v1"
const LegacyStorageProfile = "legacy-directory-v1"

type windowsRecord struct {
	IdentityRecordVersion int    `json:"identityRecordVersion"`
	StorageProfile        string `json:"storageProfile"`
	InstallationID        string `json:"installationId"`
}

// Separate strict DTO: v1 readers never acquire optional v2 fields or coercions.
func parseWindowsRecord(data []byte) (windowsRecord, error) {
	var record windowsRecord
	invalid := errors.New("invalid-windows-identity-record")
	if len(data) == 0 || len(data) > MaxRecordBytes || !utf8.Valid(data) {
		return record, invalid
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	token, err := decoder.Token()
	if err != nil || token != json.Delim('{') {
		return record, invalid
	}
	seen := map[string]bool{}
	for decoder.More() {
		token, err := decoder.Token()
		key, ok := token.(string)
		if err != nil || !ok || seen[key] {
			return record, invalid
		}
		seen[key] = true
		value, err := decoder.Token()
		if err != nil {
			return record, invalid
		}
		switch key {
		case "identityRecordVersion":
			version, ok := value.(json.Number)
			if !ok || version.String() != "2" {
				return record, invalid
			}
			record.IdentityRecordVersion = 2
		case "storageProfile":
			profile, ok := value.(string)
			if !ok || profile != WindowsStorageProfile {
				return record, invalid
			}
			record.StorageProfile = profile
		case "installationId":
			id, ok := value.(string)
			if !ok || !validUUID(id) {
				return record, invalid
			}
			record.InstallationID = id
		default:
			return record, invalid
		}
	}
	token, err = decoder.Token()
	if err != nil || token != json.Delim('}') || len(seen) != 3 {
		return record, invalid
	}
	if _, err = decoder.Token(); err != io.EOF {
		return record, invalid
	}
	return record, nil
}

// Only a GUID volume-root returned for the actual retained handle may reach the
// drive-type query. No drive-letter/UNC/environment guess supplies eligibility.
var guidVolumeRoot = regexp.MustCompile(`^\\\\\?\\Volume\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\}\\`)

func windowsVolumeRoot(finalPath string) (string, error) {
	root := guidVolumeRoot.FindString(finalPath)
	if root == "" {
		return "", errors.New("identity-storage-unsupported")
	}
	return root, nil
}

// Detect a short nil-error write instead of treating a partial visible anchor as
// a complete record. Tests use writer fixtures; production passes its real file.
func writeRecord(writer io.Writer, data []byte) error {
	n, err := writer.Write(data)
	if err == nil && n != len(data) {
		return io.ErrShortWrite
	}
	return err
}
