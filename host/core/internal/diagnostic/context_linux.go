//go:build linux

package diagnostic

import (
	"encoding/json"
	"io"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/installation"
)

// This local record is deliberately separate from the capabilities wire schema.
// The IDs are observations, not command authority or a coherent atomic snapshot.
type contextObservation struct {
	LocalContextVersion    int     `json:"localContextVersion"`
	AgentVersion           string  `json:"agentVersion"`
	ObservedAt             string  `json:"observedAt"`
	Runtime                Runtime `json:"runtime"`
	InstallationID         string  `json:"installationId"`
	BootID                 string  `json:"bootId"`
	IdentityDurability     string  `json:"identityDurability"`
	ObservationConsistency string  `json:"observationConsistency"`
}

func runContextRead(dir string, out, errOut io.Writer, now func() time.Time, facts func() Runtime) int {
	return observeContext(dir, out, errOut, now, facts, installation.Read, bootidentity.Read)
}

// Reader injection is private and per-call for composition tests. Production uses
// the immutable readers above; there are no global hooks or alternate ID sources.
func observeContext(dir string, out, errOut io.Writer, now func() time.Time, facts func() Runtime,
	readInstallation func(string) (installation.Result, error), readBoot func() (bootidentity.ID, error)) int {
	installationResult, err := readInstallation(dir)
	if err != nil {
		_, _ = io.WriteString(errOut, "cloudy-host: context installation read failed\n")
		return 1
	}
	boot, err := readBoot()
	if err != nil {
		_, _ = io.WriteString(errOut, "cloudy-host: context boot read failed\n")
		return 1
	}
	// Take observedAt only after both reads. It is neither a boot timestamp nor
	// proof that the two observations were atomic or unchanged across these calls.
	response := contextObservation{1, AgentVersion, now().UTC().Format(time.RFC3339Nano), facts(),
		installationResult.Record.InstallationID, boot.String(), installationResult.Durability, "sequential-not-atomic"}
	if err := json.NewEncoder(checkedWriter{out}).Encode(response); err != nil {
		// Output may be partial. The operation has no repair/rollback side effect;
		// callers require both successful exit and a complete decodable record.
		_, _ = io.WriteString(errOut, "cloudy-host: context output failed\n")
		return 1
	}
	return 0
}
