// cloudy-host runs a finite local diagnostic, not a paired host service.
package main

import (
	"os"
	"time"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/diagnostic"
)

func main() {
	os.Exit(diagnostic.Run(os.Args[1:], os.Stdout, os.Stderr, time.Now, diagnostic.CurrentRuntime))
}
