// Package diagnostic describes a finite local construction experiment. Its
// snapshot is not the host capability wire manifest and grants no authority.
package diagnostic

import (
	"encoding/json"
	"fmt"
	"io"
	"runtime"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/installation"
)

const AgentVersion = "0.1.1"

const usage = "usage: cloudy-host inspect | version | identity init|read --state-dir <existing-directory>\n"

type Runtime struct {
	OS        string `json:"os"`
	Arch      string `json:"arch"`
	GoVersion string `json:"goVersion"`
}

type State struct {
	State  string `json:"state"`
	Reason string `json:"reason"`
}

type Features struct {
	Pairing         State `json:"pairing"`
	RemoteControl   State `json:"remoteControl"`
	Jobs            State `json:"jobs"`
	Events          State `json:"events"`
	SessionAdapters State `json:"sessionAdapters"`
}

type Backends struct {
	RemoteDesktop State `json:"remoteDesktop"`
	USB           State `json:"usb"`
	Notifications State `json:"notifications"`
}

type Snapshot struct {
	LocalDiagnosticVersion int      `json:"localDiagnosticVersion"`
	AgentVersion           string   `json:"agentVersion"`
	ObservedAt             string   `json:"observedAt"`
	Runtime                Runtime  `json:"runtime"`
	Features               Features `json:"features"`
	Backends               Backends `json:"backends"`
}

// CurrentRuntime reads only the running binary's platform and toolchain facts.
func CurrentRuntime() Runtime {
	return Runtime{OS: runtime.GOOS, Arch: runtime.GOARCH, GoVersion: runtime.Version()}
}

func snapshot(now time.Time, facts Runtime) Snapshot {
	unimplemented := State{State: "unavailable", Reason: "not-implemented"}
	uninspected := State{State: "unknown", Reason: "not-inspected"}
	return Snapshot{
		LocalDiagnosticVersion: 1,
		AgentVersion:           AgentVersion,
		ObservedAt:             now.UTC().Format(time.RFC3339Nano),
		Runtime:                facts,
		Features: Features{
			Pairing: unimplemented, RemoteControl: unimplemented,
			Jobs: unimplemented, Events: unimplemented, SessionAdapters: unimplemented,
		},
		Backends: Backends{RemoteDesktop: uninspected, USB: uninspected, Notifications: uninspected},
	}
}

// Run executes exactly one explicit local command. Sources are injected for
// deterministic tests; the executable supplies actual clock/runtime readers.
// Writer errors are never displayed because their text may contain private data.
func Run(args []string, out, errOut io.Writer, now func() time.Time, facts func() Runtime) int {
	if len(args) > 0 && args[0] == "identity" {
		return runIdentity(args, out, errOut)
	}
	if len(args) != 1 || (args[0] != "inspect" && args[0] != "version") {
		_, _ = io.WriteString(errOut, usage)
		return 2
	}
	var err error
	if args[0] == "version" {
		_, err = fmt.Fprintln(checkedWriter{out}, AgentVersion)
	} else {
		err = json.NewEncoder(checkedWriter{out}).Encode(snapshot(now(), facts()))
	}
	if err != nil {
		_, _ = io.WriteString(errOut, "cloudy-host: output failed\n")
		return 1
	}
	return 0
}

// Convert a writer contract violation into failure instead of accepting truncated
// output as a complete diagnostic record.
type checkedWriter struct{ io.Writer }

func (w checkedWriter) Write(p []byte) (int, error) {
	n, err := w.Writer.Write(p)
	if err == nil && n != len(p) {
		err = io.ErrShortWrite
	}
	return n, err
}

// identity is a separate explicit local profile; inspect never initializes or
// advertises it as authenticated remote capability metadata.
func runIdentity(args []string, out, errOut io.Writer) int {
	if len(args) != 4 || (args[1] != "init" && args[1] != "read") || args[2] != "--state-dir" || args[3] == "" {
		_, _ = io.WriteString(errOut, usage)
		return 2
	}
	var result installation.Result
	var err error
	if args[1] == "init" {
		result, err = installation.Init(args[3])
	} else {
		result, err = installation.Read(args[3])
	}
	if err != nil {
		// Installation errors are finite safe codes, never raw path-bearing errors.
		_, _ = io.WriteString(errOut, "cloudy-host: "+err.Error()+"\n")
		return 1
	}
	response := struct {
		LocalIdentityVersion int    `json:"localIdentityVersion"`
		AgentVersion         string `json:"agentVersion"`
		Operation            string `json:"operation"`
		InstallationID       string `json:"installationId"`
		Durability           string `json:"durability"`
	}{1, AgentVersion, args[1], result.Record.InstallationID, result.Durability}
	if err := json.NewEncoder(checkedWriter{out}).Encode(response); err != nil {
		// Init effects are retained despite response loss. Neither this message nor
		// a later read can certify power-loss durability or authorize regeneration.
		_, _ = io.WriteString(errOut, "cloudy-host: identity output failed; existing state retained\n")
		return 1
	}
	return 0
}
