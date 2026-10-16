//go:build linux

package jobs

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"io"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
	"time"

	"golang.org/x/sys/unix"
)

// Start accepts only a trusted retained delegation handle. It creates and owns
// one fresh child; the delegation itself may contain the supervisor and is NEVER
// a kill target. The returned owner exists before the actual asynchronous Start.
// Neither this API nor deadlines can guarantee termination of a stalled kernel.
func Start(lifetime context.Context, delegation *os.File, spec Spec) (*Job, error) {
	if lifetime == nil || delegation == nil {
		return nil, failure("invalid-input", "admission", nil)
	}
	if err := lifetime.Err(); err != nil {
		return nil, failure("rejected", "pre-canceled", err)
	}
	admitted, err := validate(spec)
	if err != nil {
		return nil, err
	}
	c, err := prepare(delegation)
	if c == nil {
		return nil, failure("rejected", "delegation", err)
	}
	j := newJob(c)
	if err != nil {
		j.mu.Lock()
		j.state.StartResolved = true
		j.state.Failure = failure("rejected", "container", err)
		j.mu.Unlock()
		j.commit("rejected")
		close(j.startDone)
		close(j.waitDone)
		go func() { _, _ = j.Settle(context.Background()); j.finishReport() }()
		return j, failure("rejected", "container", err)
	}
	j.mu.Lock()
	j.releaseContext = context.AfterFunc(lifetime, func() {
		if errors.Is(lifetime.Err(), context.DeadlineExceeded) {
			j.commit("timed-out")
		} else {
			j.Cancel()
		}
	})
	j.runtimeTimer = time.AfterFunc(admitted.Runtime, func() { j.commit("timed-out") })
	j.mu.Unlock()
	go j.run(admitted, spawn)
	return j, nil
}

type execProcess struct{ cmd *exec.Cmd }

func spawn(s Spec, c containment, out, errOut io.Writer) (process, error) {
	cmd := exec.Command(s.Executable, s.Args...)
	cmd.Dir = s.Cwd
	cmd.Env = s.Env
	cmd.Stdin = bytes.NewReader(s.Input)
	cmd.Stdout = out
	cmd.Stderr = errOut
	cmd.SysProcAttr = &syscall.SysProcAttr{UseCgroupFD: true, CgroupFD: c.fd()}
	// Cancellation is coordinated by Job and the owned cgroup, not WaitDelay.
	// This bound only retires os/exec-owned copies after direct child exit.
	cmd.WaitDelay = 250 * time.Millisecond
	if err := cmd.Start(); err != nil {
		return nil, err
	}
	return &execProcess{cmd}, nil
}
func (p *execProcess) wait() waitResult {
	err := p.cmd.Wait() // Exactly once; includes os/exec-owned copy goroutine shutdown.
	r := waitResult{exitCode: -1, ioCompletion: assessIO(err)}
	if state := p.cmd.ProcessState; state != nil {
		r.reaped = true
		r.exitCode = state.ExitCode()
		if status, ok := state.Sys().(syscall.WaitStatus); ok && status.Signaled() {
			r.signal = int(status.Signal())
		}
	}
	var exit *exec.ExitError
	if err != nil && !errors.As(err, &exit) {
		r.err = err
	}
	return r
}

// Go Wait can prefer ExitError over an internal copy/WaitDelay error. Nonzero
// exit therefore proves neither failed I/O nor complete drain. Keep that unknown
// assessment distinct from an exposed forced-close result and actual exit/reap.
func assessIO(err error) string {
	if err == nil {
		return "complete"
	}
	var exited *exec.ExitError
	if errors.As(err, &exited) {
		return "not-assessed"
	}
	if errors.Is(err, exec.ErrWaitDelay) {
		return "incomplete"
	}
	return "unknown"
}

type childGroup struct {
	parent, child       *os.File
	name                string
	identity            unix.Stat_t
	identityKnown       bool
	childCloseAttempted bool
	closeError          error
	removed             bool
}

func (c *childGroup) fd() int {
	if c.child == nil {
		return -1
	}
	return int(c.child.Fd())
}
func prepare(source *os.File) (*childGroup, error) {
	raw, err := source.SyscallConn()
	if err != nil {
		return nil, err
	}
	fd := -1
	var dupErr error
	err = raw.Control(func(original uintptr) { fd, dupErr = unix.FcntlInt(original, unix.F_DUPFD_CLOEXEC, 0) })
	if err != nil {
		return nil, err
	}
	if dupErr != nil {
		return nil, dupErr
	}
	c := &childGroup{parent: os.NewFile(uintptr(fd), "owned-delegation")}
	var fs unix.Statfs_t
	var stat unix.Stat_t
	if err = unix.Fstat(fd, &stat); err != nil {
		return c, err
	}
	if stat.Mode&unix.S_IFMT != unix.S_IFDIR {
		return c, errors.New("not-directory")
	}
	if err = unix.Fstatfs(fd, &fs); err != nil {
		return c, err
	}
	if fs.Type != unix.CGROUP2_SUPER_MAGIC {
		return c, errors.New("not-cgroup-v2")
	}
	typ, err := c.readControl(fd, "cgroup.type")
	if err != nil {
		return c, err
	}
	if strings.TrimSpace(string(typ)) != "domain" {
		return c, errors.New("not-domain")
	}
	var random [16]byte
	if _, err = rand.Read(random[:]); err != nil {
		return c, err
	}
	name := "cloudy-job-" + hex.EncodeToString(random[:])
	if err = unix.Mkdirat(fd, name, 0o700); err != nil {
		return c, err
	}
	c.name = name
	if err = unix.Fstatat(fd, name, &c.identity, unix.AT_SYMLINK_NOFOLLOW); err != nil {
		return c, err
	}
	c.identityKnown = true
	childFD, err := unix.Openat(fd, name, unix.O_RDONLY|unix.O_DIRECTORY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
	if err != nil {
		return c, err
	}
	c.child = os.NewFile(uintptr(childFD), "owned-job-cgroup")
	var opened unix.Stat_t
	if err = unix.Fstat(childFD, &opened); err != nil {
		return c, err
	}
	if opened.Dev != c.identity.Dev || opened.Ino != c.identity.Ino {
		return c, errors.New("changed-child")
	}
	typ, err = c.readControl(childFD, "cgroup.type")
	if err != nil {
		return c, err
	}
	if strings.TrimSpace(string(typ)) != "domain" {
		return c, errors.New("not-domain")
	}
	empty, err := c.empty()
	if err != nil {
		return c, err
	}
	if !empty {
		return c, errors.New("not-empty")
	}
	// Open-only access gate does not mutate membership or certify a future write.
	killFD, err := unix.Openat(childFD, "cgroup.kill", unix.O_WRONLY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
	if err != nil {
		return c, err
	}
	if err = unix.Close(killFD); err != nil {
		c.closeError = err
		return c, err
	}
	return c, nil
}
func readAt(fd int, name string) ([]byte, error) {
	opened, err := unix.Openat(fd, name, unix.O_RDONLY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
	if err != nil {
		return nil, err
	}
	f := os.NewFile(uintptr(opened), "cgroup-control")
	data, readErr := io.ReadAll(io.LimitReader(f, 4097))
	closeErr := f.Close()
	if closeErr != nil {
		return nil, &controlCloseFailure{closeErr}
	}
	if readErr != nil {
		return nil, readErr
	}
	if len(data) > 4096 {
		return nil, errors.New("oversize-control")
	}
	return data, nil
}

// A failed control-handle close is sticky: later successful queries cannot
// upgrade uncertain handle retirement to confirmed cleanup.
type controlCloseFailure struct{ cause error }

func (e *controlCloseFailure) Error() string { return "control-close-failed" }
func (c *childGroup) readControl(fd int, name string) ([]byte, error) {
	data, err := readAt(fd, name)
	var closeFailure *controlCloseFailure
	if errors.As(err, &closeFailure) {
		c.closeError = err
	}
	return data, err
}

func parsePopulated(data []byte) (bool, error) {
	seen := map[string]bool{}
	found := false
	populated := true
	for _, line := range strings.Split(strings.TrimSpace(string(data)), "\n") {
		fields := strings.Fields(line)
		if len(fields) != 2 || seen[fields[0]] {
			return false, errors.New("invalid-events")
		}
		seen[fields[0]] = true
		if fields[0] == "populated" {
			n, err := strconv.Atoi(fields[1])
			if err != nil || (fields[1] != "0" && fields[1] != "1") {
				return false, errors.New("invalid-populated")
			}
			populated = n == 1
			found = true
		}
	}
	if !found {
		return false, errors.New("missing-populated")
	}
	return !populated, nil
}
func (c *childGroup) kill() error {
	if c.name == "" {
		return nil
	} // No child claimed; only the duplicated parent handle is owned.
	if c.child == nil {
		return errors.New("child-handle-unavailable")
	}
	fd, err := unix.Openat(c.fd(), "cgroup.kill", unix.O_WRONLY|unix.O_NOFOLLOW|unix.O_CLOEXEC, 0)
	if err != nil {
		return err
	}
	f := os.NewFile(uintptr(fd), "owned-job-kill")
	n, writeErr := f.Write([]byte("1"))
	closeErr := f.Close()
	if writeErr != nil {
		return writeErr
	}
	if n != 1 {
		return io.ErrShortWrite
	}
	return closeErr
}
func (c *childGroup) empty() (bool, error) {
	if c.name == "" {
		return true, nil
	}
	if c.child == nil {
		return false, errors.New("child-handle-unavailable")
	}
	data, err := c.readControl(c.fd(), "cgroup.events")
	if err != nil {
		return false, err
	}
	return parsePopulated(data)
}
func (c *childGroup) cleanup() error {
	if c.closeError != nil {
		return c.closeError
	}
	if c.name != "" && !c.removed {
		if !c.identityKnown {
			return errors.New("child-identity-unknown")
		}
		var current unix.Stat_t
		if err := unix.Fstatat(int(c.parent.Fd()), c.name, &current, unix.AT_SYMLINK_NOFOLLOW); err != nil {
			return err
		}
		if current.Mode&unix.S_IFMT != unix.S_IFDIR || current.Dev != c.identity.Dev || current.Ino != c.identity.Ino {
			return errors.New("changed-child")
		}
		if !c.childCloseAttempted && c.child != nil {
			c.childCloseAttempted = true
			c.closeError = c.child.Close()
			c.child = nil
			if c.closeError != nil {
				return c.closeError
			}
		}
		if err := unix.Unlinkat(int(c.parent.Fd()), c.name, unix.AT_REMOVEDIR); err != nil {
			return err
		}
		c.removed = true
	}
	if c.parent != nil {
		c.closeError = c.parent.Close()
		c.parent = nil
		return c.closeError
	}
	return nil
}
