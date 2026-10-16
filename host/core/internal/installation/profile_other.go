//go:build !windows

package installation

import "os"

// Linux/macOS retain v1 directory publication and every required Sync barrier.
func initialize(path string, inject fault) (Result, error) { return initializeDirectory(path, inject) }
func readIdentity(path string) (Result, error)             { return readDirectory(path) }

func observedReparse(info os.FileInfo) bool { return info.Mode()&os.ModeSymlink != 0 }
