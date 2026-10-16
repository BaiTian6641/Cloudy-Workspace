//go:build !linux

package diagnostic

import (
	"io"
	"time"
)

// Recognizing the local command does not claim a platform boot reader exists.
// Refuse before opening installation state, sampling time or inspecting runtime.
func runContextRead(_ string, _ io.Writer, errOut io.Writer, _ func() time.Time, _ func() Runtime) int {
	_, _ = io.WriteString(errOut, "cloudy-host: context unsupported on this platform\n")
	return 1
}
