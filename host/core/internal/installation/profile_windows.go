//go:build windows

package installation

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"os"
	"syscall"

	"golang.org/x/sys/windows"
)

// Go1.27.2 maps O_EXCL creation and Mkdir to FILE_CREATE at this SAME leaf.
// Content appearance is not atomic: an empty/partial anchor is a retained claim.
func initialize(path string, inject fault) (result Result, err error) {
	claimed, published := false, false
	fail := func(code string, cause error) error {
		return &Failure{Code: code, Claimed: claimed, Published: published, cause: cause}
	}
	root, e := openState(path)
	if e != nil {
		return result, fail(e.Error(), e)
	}
	defer func() {
		if e := root.Close(); e != nil && err == nil {
			err = fail("identity-commit-uncertain", e)
		}
	}()
	if e := step(inject, "storage-eligibility", func() error { return eligibleWindowsRoot(root) }); e != nil {
		var storage *storageFailure
		if errors.As(e, &storage) {
			return result, fail(storage.code, storage.cause)
		}
		return result, fail("identity-storage-query-failed", e)
	}
	var file *os.File
	e = step(inject, "anchor-create", func() error {
		var e error
		file, e = root.OpenFile(AnchorName, os.O_WRONLY|os.O_CREATE|os.O_EXCL|os.O_SYNC, 0o600)
		return e
	})
	if e != nil {
		// File-vs-directory collisions can carry a different native error. Observation
		// classifies only; there is never a retry, replacement, alternate name or ID.
		if _, observed := root.Lstat(AnchorName); observed == nil {
			return result, fail("identity-already-claimed", e)
		}
		return result, fail("identity-claim-failed", e)
	}
	claimed = true
	defer func() {
		if file != nil {
			_ = file.Close()
		}
	}()
	if e := step(inject, "anchor-check", func() error { return consistentAnchor(root, file) }); e != nil {
		return result, fail("identity-initialization-incomplete", e)
	}
	var id [16]byte
	if _, e := rand.Read(id[:]); e != nil {
		return result, fail("identity-initialization-incomplete", e)
	}
	id[6] = (id[6] & 0x0f) | 0x40
	id[8] = (id[8] & 0x3f) | 0x80
	h := hex.EncodeToString(id[:])
	record := windowsRecord{2, WindowsStorageProfile, h[:8] + "-" + h[8:12] + "-" + h[12:16] + "-" + h[16:20] + "-" + h[20:]}
	data, e := json.Marshal(record)
	if e != nil {
		return result, fail("identity-initialization-incomplete", e)
	}
	data = append(data, '\n')
	if e := step(inject, "anchor-write", func() error { return writeRecord(file, data) }); e != nil {
		return result, fail("identity-initialization-incomplete", e)
	}
	published = true // Complete bytes returned from Write; not atomic appearance/durability.
	if e := step(inject, "anchor-sync", file.Sync); e != nil {
		return result, fail("identity-commit-uncertain", e)
	}
	if e := step(inject, "anchor-postcheck", func() error { return consistentAnchor(root, file) }); e != nil {
		return result, fail("identity-commit-uncertain", e)
	}
	if e := step(inject, "anchor-close", file.Close); e != nil {
		return result, fail("identity-commit-uncertain", e)
	}
	file = nil
	return Result{Record: Record{2, record.InstallationID}, RecordVersion: 2, StorageProfile: WindowsStorageProfile, Durability: "write-through-file-flush-completed"}, nil
}

type storageFailure struct {
	code  string
	cause error
}

func (e *storageFailure) Error() string { return e.code }

// Query the retained opened directory, not a guessed path. The selected wrapper
// API availability is distinct from a successful actual fixed-NTFS observation.
func eligibleWindowsRoot(root *os.Root) (err error) {
	fail := func(code string, cause error) error { return &storageFailure{code, cause} }
	file, e := root.Open(".")
	if e != nil {
		return fail("identity-storage-query-failed", e)
	}
	defer func() {
		if e := file.Close(); e != nil && err == nil {
			err = fail("identity-storage-query-failed", e)
		}
	}()
	before, e := file.Stat()
	if e != nil || !before.IsDir() {
		return fail("identity-storage-query-failed", e)
	}
	rootInfo, e := root.Stat(".")
	if e != nil || !os.SameFile(before, rootInfo) {
		return fail("identity-storage-query-failed", e)
	}
	handle := windows.Handle(file.Fd())
	var info windows.ByHandleFileInformation
	if e := windows.GetFileInformationByHandle(handle, &info); e != nil {
		return fail("identity-storage-query-failed", e)
	}
	if info.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
		return fail("identity-storage-unsupported", nil)
	}
	var fs [64]uint16
	if e := windows.GetVolumeInformationByHandle(handle, nil, 0, nil, nil, nil, &fs[0], uint32(len(fs))); e != nil {
		return fail("identity-storage-query-failed", e)
	}
	if windows.UTF16ToString(fs[:]) != "NTFS" {
		return fail("identity-storage-unsupported", nil)
	}
	// VOLUME_NAME_GUID=0x1, FILE_NAME_NORMALIZED=0 per GetFinalPathNameByHandleW.
	var finalPath [32768]uint16
	n, e := windows.GetFinalPathNameByHandle(handle, &finalPath[0], uint32(len(finalPath)), 1)
	if e != nil || n == 0 || n >= uint32(len(finalPath)) {
		return fail("identity-storage-query-failed", e)
	}
	for _, c := range finalPath[:n] {
		if c == 0 {
			return fail("identity-storage-query-failed", nil)
		}
	}
	volume, e := windowsVolumeRoot(windows.UTF16ToString(finalPath[:n]))
	if e != nil {
		return fail("identity-storage-unsupported", e)
	}
	volumeUTF16, e := windows.UTF16PtrFromString(volume)
	if e != nil {
		return fail("identity-storage-query-failed", e)
	}
	switch windows.GetDriveType(volumeUTF16) {
	case windows.DRIVE_FIXED:
	case windows.DRIVE_REMOVABLE, windows.DRIVE_REMOTE, windows.DRIVE_CDROM, windows.DRIVE_RAMDISK:
		return fail("identity-storage-unsupported", nil)
	default:
		return fail("identity-storage-query-failed", nil)
	}
	after, e := file.Stat()
	if e != nil || !os.SameFile(before, after) {
		return fail("identity-storage-query-failed", e)
	}
	current, e := root.Stat(".")
	if e != nil || !os.SameFile(before, current) {
		return fail("identity-storage-query-failed", e)
	}
	return nil
}

func observedReparse(info os.FileInfo) bool {
	attributes, ok := info.Sys().(*syscall.Win32FileAttributeData)
	return !ok || info.Mode()&os.ModeSymlink != 0 || attributes.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0
}
func consistentAnchor(root *os.Root, file *os.File) error {
	before, e := root.Lstat(AnchorName)
	if e != nil || !before.Mode().IsRegular() || observedReparse(before) {
		return errors.New("identity-record-changed")
	}
	return consistentObservedAnchor(before, file)
}

// Bind the opened handle to the original observation, not a later path sample.
func consistentObservedAnchor(observed os.FileInfo, file *os.File) error {
	opened, e := file.Stat()
	if e != nil || !observed.Mode().IsRegular() || observedReparse(observed) || !opened.Mode().IsRegular() || observedReparse(opened) || !os.SameFile(observed, opened) {
		return errors.New("identity-record-changed")
	}
	var info windows.ByHandleFileInformation
	if e := windows.GetFileInformationByHandle(windows.Handle(file.Fd()), &info); e != nil {
		return e
	}
	if info.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
		return errors.New("identity-record-changed")
	}
	return nil
}
func readIdentity(path string) (result Result, err error) {
	root, e := openState(path)
	if e != nil {
		return result, &Failure{Code: e.Error(), cause: e}
	}
	defer func() {
		if e := root.Close(); e != nil && err == nil {
			err = &Failure{Code: "identity-read-failed", cause: e}
		}
	}()
	observed, e := root.Lstat(AnchorName)
	if e != nil {
		return result, &Failure{Code: "identity-state-incomplete", cause: e}
	}
	if observedReparse(observed) {
		return result, &Failure{Code: "identity-record-invalid"}
	}
	if observed.IsDir() {
		// Read-only legacy route: required Sync is not skipped or migrated. Root's
		// object identity is retained under the trusted-root/no-hostile-mutation rule.
		result, e = readDirectoryRoot(root)
		if e != nil {
			return result, e
		}
		after, e := root.Lstat(AnchorName)
		if e != nil || observedReparse(after) || !os.SameFile(observed, after) {
			return Result{}, &Failure{Code: "identity-record-changed", cause: e}
		}
		result.RecordVersion = 1
		result.StorageProfile = LegacyStorageProfile
		return result, nil
	}
	if !observed.Mode().IsRegular() || observed.Size() > MaxRecordBytes {
		return result, &Failure{Code: "identity-record-invalid"}
	}
	file, e := root.Open(AnchorName)
	if e != nil {
		return result, &Failure{Code: "identity-read-failed", cause: e}
	}
	defer func() {
		if e := file.Close(); e != nil && err == nil {
			err = &Failure{Code: "identity-read-failed", cause: e}
		}
	}()
	if e := consistentObservedAnchor(observed, file); e != nil {
		return result, &Failure{Code: "identity-record-changed", cause: e}
	}
	if e := consistentAnchor(root, file); e != nil {
		return result, &Failure{Code: "identity-record-changed", cause: e}
	}
	data, e := io.ReadAll(io.LimitReader(file, MaxRecordBytes+1))
	if e != nil {
		return result, &Failure{Code: "identity-read-failed", cause: e}
	}
	if e := consistentAnchor(root, file); e != nil {
		return result, &Failure{Code: "identity-record-changed", cause: e}
	}
	record, e := parseWindowsRecord(data)
	if e != nil {
		return result, &Failure{Code: "identity-record-invalid", cause: e}
	}
	return Result{Record: Record{2, record.InstallationID}, RecordVersion: 2, StorageProfile: WindowsStorageProfile, Durability: "not-assessed"}, nil
}
