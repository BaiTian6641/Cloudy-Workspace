//go:build windows

package installation

import (
	"errors"
	"os"
	"syscall"
	"testing"
)

// Go1.27.2 File.Sync calls FlushFileBuffers. This probe uses the same
// read-only directory handle as production; Microsoft requires GENERIC_WRITE:
// https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-flushfilebuffers
// Only a failed Sync with the exact access-right error can classify this handle
// limitation. It does not excuse open/Mkdir/ACL/Link failures or infer OS support.
func unsupportedDirectoryHandleSync(err error) bool {
	var pathError *os.PathError
	return errors.As(err, &pathError) && pathError.Op == "sync" && errors.Is(pathError.Err, syscall.ERROR_ACCESS_DENIED)
}
func TestUnsupportedDirectorySyncErrorClassifier(t *testing.T) {
	if !unsupportedDirectoryHandleSync(&os.PathError{Op: "sync", Err: syscall.ERROR_ACCESS_DENIED}) {
		t.Fatal("documented handle error not recognized")
	}
	for _, err := range []error{
		errors.New("Access is denied"), os.ErrPermission,
		&os.PathError{Op: "open", Err: syscall.ERROR_ACCESS_DENIED},
		&os.PathError{Op: "sync", Err: syscall.ERROR_PRIVILEGE_NOT_HELD},
	} {
		if unsupportedDirectoryHandleSync(err) {
			t.Fatalf("unrelated failure classified: %v", err)
		}
	}
}

// Creating a symlink fixture needs this Windows privilege unless an applicable
// developer-mode route is available. Any other fixture failure is a test failure.
func unsupportedSymlinkFixture(err error) bool {
	var linkError *os.LinkError
	return errors.As(err, &linkError) && linkError.Op == "symlink" && errors.Is(linkError.Err, syscall.ERROR_PRIVILEGE_NOT_HELD)
}
