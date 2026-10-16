//go:build !windows

package installation

// No other platform-specific unsupported Sync outcome is accepted by these
// tests. A new exception requires exact official semantics and a real probe.
func unsupportedDirectoryHandleSync(error) bool { return false }

func unsupportedSymlinkFixture(error) bool { return false }
