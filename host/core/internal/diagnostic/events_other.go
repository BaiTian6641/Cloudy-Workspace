//go:build !linux && !darwin

package diagnostic

import (
	"io"
	"time"
)

// Syntax is already validated. No installation/store/time source is inspected.
func runEventsRead(_ eventsRequest, _ io.Writer, errOut io.Writer, _ func() time.Time, _ func() Runtime) int {
	if errOut != nil {
		_, _ = io.WriteString(errOut, "cloudy-host: events unsupported on this platform\n")
	}
	return 1
}
