//go:build windows && amd64

package jobs

import (
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
	"unsafe"

	"golang.org/x/sys/windows"
)

// These two definitions are absent from x/sys v0.49.0. They are the public
// WinBase.h/winnt.h ABI at Microsoft win32metadata commit
// 0f012441d654d3bb7de898fdc664b5fcad52bdcc, not an inferred private interface.
const procThreadAttributeJobList = 0x0002000d

type jobAccounting struct {
	TotalUserTime, TotalKernelTime, ThisPeriodTotalUserTime, ThisPeriodTotalKernelTime int64
	TotalPageFaultCount, TotalProcesses, ActiveProcesses, TotalTerminatedProcesses     uint32
}

// Fail compilation if the selected amd64 layout ceases to match the SDK ABI.
var _ [48 - unsafe.Sizeof(jobAccounting{})]byte
var _ [unsafe.Sizeof(jobAccounting{}) - 48]byte
var _ [40 - unsafe.Offsetof(jobAccounting{}.ActiveProcesses)]byte
var _ [unsafe.Offsetof(jobAccounting{}.ActiveProcesses) - 40]byte

// StartOwned is the Windows amd64 profile: each call owns one unnamed Job
// Object. JOB_LIST assigns at creation, before user code; unsupported/nesting
// failures never fall back to an uncontained CreateProcess. This is not a token,
// filesystem or same-user security sandbox. Callers must retain uncertain Jobs.
// Other uncoordinated broad-handle-inheriting spawns in this process must be
// excluded by the caller; HANDLE_LIST constrains this creation only.
// Only CRT/CommandLineToArgv-compatible executable argument parsing is supported.
func StartOwned(lifetime context.Context, spec Spec) (*Job, error) {
	if lifetime == nil {
		return nil, failure("invalid-input", "admission", nil)
	}
	if err := lifetime.Err(); err != nil {
		return nil, failure("rejected", "pre-canceled", err)
	}
	admitted, err := validate(spec)
	if err != nil {
		return nil, err
	}
	if _, err = encodeWindows(admitted); err != nil {
		return nil, failure("invalid-spec", "windows-encoding", err)
	}
	c, err := prepareWindows()
	if c == nil {
		return nil, failure("rejected", "container", err)
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
	go j.run(admitted, spawnWindows)
	return j, nil
}

type windowsEncoding struct{ app, command, cwd, environment []uint16 }

func encodeWindows(s Spec) (windowsEncoding, error) {
	var e windowsEncoding
	bad := func() (windowsEncoding, error) { return e, errors.New("invalid-windows-encoding") }
	// Shared admission caps bytes first. Validate UTF-8 before conversion so invalid
	// input cannot silently turn into U+FFFD, then bound the escaped UTF-16 result.
	for _, v := range append(append([]string{s.Executable, s.Cwd}, s.Args...), s.Env...) {
		if !utf8.ValidString(v) || strings.ContainsRune(v, 0) {
			return bad()
		}
	}
	ext := strings.ToLower(filepath.Ext(s.Executable))
	if ext != ".exe" {
		return bad()
	} // No cmd/bat shell expansion or ambiguous interpreter convention.
	var err error
	e.app, err = windows.UTF16FromString(s.Executable)
	if err != nil || len(e.app) > 32767 {
		return bad()
	}
	e.cwd, err = windows.UTF16FromString(s.Cwd)
	if err != nil || len(e.cwd) > 32767 {
		return bad()
	}
	command := windows.ComposeCommandLine(append([]string{s.Executable}, s.Args...))
	e.command, err = windows.UTF16FromString(command)
	if err != nil || len(e.command) > 32767 {
		return bad()
	}
	env := append([]string{}, s.Env...)
	seen := map[string]bool{}
	for _, v := range env {
		key, _, ok := strings.Cut(v, "=")
		key = strings.ToUpper(key)
		if !ok || seen[key] {
			return bad()
		}
		seen[key] = true
	}
	sort.Slice(env, func(i, j int) bool {
		left, _, _ := strings.Cut(env[i], "=")
		right, _, _ := strings.Cut(env[j], "=")
		return strings.ToUpper(left) < strings.ToUpper(right)
	})
	for _, v := range env {
		u, err := windows.UTF16FromString(v)
		if err != nil {
			return bad()
		}
		e.environment = append(e.environment, u...)
	}
	e.environment = append(e.environment, 0)
	if len(env) == 0 {
		e.environment = append(e.environment, 0)
	}
	if len(e.environment) > 32767 {
		return bad()
	}
	return e, nil
}

// Each slot attempts CloseHandle at most once. Failed close is sticky because
// blindly retrying a raw handle may close a later reused value.
type winHandle struct {
	value     windows.Handle
	attempted bool
	err       error
}

func (h *winHandle) close() error { return h.closeWith(windows.CloseHandle) }
func (h *winHandle) closeWith(closeHandle func(windows.Handle) error) error {
	if h.value != 0 && !h.attempted {
		h.attempted = true
		h.err = closeHandle(h.value)
		if h.err == nil {
			h.value = 0
		}
	}
	return h.err
}

// Pipe Close can wait on the kernel. One owned asynchronous close operation and
// its join remain reachable; a timer never substitutes for actual completion.
type winPipe struct {
	file     *os.File
	once     sync.Once
	closed   chan struct{}
	closeErr error
}

func pipeOwner(h windows.Handle) *winPipe {
	return &winPipe{file: os.NewFile(uintptr(h), "owned-job-pipe"), closed: make(chan struct{})}
}
func (p *winPipe) requestClose() {
	p.once.Do(func() { go func() { p.closeErr = p.file.Close(); close(p.closed) }() })
}
func (p *winPipe) closeResult() (bool, error) {
	select {
	case <-p.closed:
		return true, p.closeErr
	default:
		return false, nil
	}
}

type windowsContainer struct {
	job, primary, thread winHandle
	pid                  uint32
	leftovers            []winHandle
	child                [3]winHandle
	pipes                [3]*winPipe // stdin writer, stdout reader, stderr reader
	pumps                *winPumps
	cleanupErr           error
}

func prepareWindows() (*windowsContainer, error) {
	h, err := windows.CreateJobObject(nil, nil)
	if err != nil {
		return nil, err
	}
	c := &windowsContainer{job: winHandle{value: h}}
	var limits windows.JOBOBJECT_EXTENDED_LIMIT_INFORMATION
	limits.BasicLimitInformation.LimitFlags = windows.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
	_, err = windows.SetInformationJobObject(h, windows.JobObjectExtendedLimitInformation, uintptr(unsafe.Pointer(&limits)), uint32(unsafe.Sizeof(limits)))
	return c, err
}
func (c *windowsContainer) kill() error { return windows.TerminateJobObject(c.job.value, 1) }
func (c *windowsContainer) empty() (bool, error) {
	var a jobAccounting
	var n uint32
	err := windows.QueryInformationJobObject(c.job.value, windows.JobObjectBasicAccountingInformation, uintptr(unsafe.Pointer(&a)), uint32(unsafe.Sizeof(a)), &n)
	if err != nil {
		return false, err
	}
	if n != uint32(unsafe.Sizeof(a)) {
		return false, errors.New("accounting-size")
	}
	return a.ActiveProcesses == 0, nil
}
func (c *windowsContainer) cleanup() error {
	pending := false
	for _, p := range c.pipes {
		if p != nil {
			p.requestClose()
		}
	}
	// Join only within this bounded observation window; retain the same close
	// operations for a later Settle if a kernel call does not complete.
	timer := time.NewTimer(250 * time.Millisecond)
	defer timer.Stop()
	for _, p := range c.pipes {
		if p != nil {
			select {
			case <-p.closed:
			case <-timer.C:
				goto observed
			}
		}
	}
	if c.pumps != nil {
		select {
		case <-c.pumps.done:
		case <-timer.C:
		}
	}
observed:
	for _, p := range c.pipes {
		if p != nil {
			p.requestClose()
			done, err := p.closeResult()
			if err != nil && c.cleanupErr == nil {
				c.cleanupErr = err
			}
			pending = pending || !done
		}
	}
	for i := range c.child {
		if err := c.child[i].close(); err != nil && c.cleanupErr == nil {
			c.cleanupErr = err
		}
	}
	if c.pumps != nil {
		select {
		case <-c.pumps.done:
		default:
			pending = true
		}
	}
	if pending {
		if c.cleanupErr != nil {
			return c.cleanupErr
		}
		return errors.New("io-retirement-pending")
	}
	for _, h := range []*winHandle{&c.thread, &c.primary, &c.job} {
		if err := h.close(); err != nil && c.cleanupErr == nil {
			c.cleanupErr = err
		}
	}
	return c.cleanupErr
}

func (c *windowsContainer) makePipes() error {
	current := windows.CurrentProcess()
	for i := range 3 {
		var r, w windows.Handle
		if err := windows.CreatePipe(&r, &w, nil, 0); err != nil {
			return err
		}
		parent, child := w, r
		if i != 0 {
			parent, child = r, w
		}
		c.pipes[i] = pipeOwner(parent)
		c.child[i] = winHandle{value: child}
		var inherited windows.Handle
		if err := windows.DuplicateHandle(current, child, current, &inherited, 0, true, windows.DUPLICATE_SAME_ACCESS); err != nil {
			return err
		}
		// Retain both handles if closing the original reports uncertainty. There is
		// no lost slot, and no child is launched on this setup error.
		original := c.child[i]
		c.child[i] = winHandle{value: inherited}
		if err := original.close(); err != nil {
			c.leftovers = append(c.leftovers, original)
			c.cleanupErr = err
			return err
		}
	}
	return nil
}

type winPumps struct {
	done chan struct{}
	mu   sync.Mutex
	errs [3]error
}

func (c *windowsContainer) startPumps(input []byte, out, errOut io.Writer) {
	p := &winPumps{done: make(chan struct{})}
	c.pumps = p
	var wg sync.WaitGroup
	wg.Add(3)
	for i := range 3 {
		go func(i int) {
			defer wg.Done()
			var err error
			if i == 0 {
				_, err = io.Copy(c.pipes[i].file, bytes.NewReader(input))
				c.pipes[i].requestClose()
			} else {
				target := out
				if i == 2 {
					target = errOut
				}
				_, err = io.Copy(target, c.pipes[i].file)
			}
			p.mu.Lock()
			p.errs[i] = err
			p.mu.Unlock()
		}(i)
	}
	go func() { wg.Wait(); close(p.done) }()
}
func (p *winPumps) assessment() string {
	select {
	case <-p.done:
	default:
		return "not-assessed"
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	for _, e := range p.errs {
		if e != nil {
			return "incomplete"
		}
	}
	return "complete"
}

type windowsProcess struct{ container *windowsContainer }

func spawnWindows(s Spec, c containment, out, errOut io.Writer) (process, error) {
	owner, ok := c.(*windowsContainer)
	if !ok {
		return nil, errors.New("missing-job-object")
	}
	encoded, err := encodeWindows(s)
	if err != nil {
		return nil, err
	}
	if err = owner.makePipes(); err != nil {
		return nil, err
	}
	attrs, err := windows.NewProcThreadAttributeList(2)
	if err != nil {
		return nil, err
	}
	jobs := [1]windows.Handle{owner.job.value}
	handles := [3]windows.Handle{owner.child[0].value, owner.child[1].value, owner.child[2].value}
	releaseAttrs := func() {
		if attrs != nil {
			attrs.Delete()
			attrs = nil
		}
		runtime.KeepAlive(jobs)
		runtime.KeepAlive(handles)
	}
	defer releaseAttrs()
	if err = attrs.Update(procThreadAttributeJobList, unsafe.Pointer(&jobs[0]), unsafe.Sizeof(jobs)); err != nil {
		return nil, err
	}
	if err = attrs.Update(windows.PROC_THREAD_ATTRIBUTE_HANDLE_LIST, unsafe.Pointer(&handles[0]), unsafe.Sizeof(handles)); err != nil {
		return nil, err
	}
	var si windows.StartupInfoEx
	si.Cb = uint32(unsafe.Sizeof(si))
	si.Flags = windows.STARTF_USESTDHANDLES
	si.StdInput = handles[0]
	si.StdOutput = handles[1]
	si.StdErr = handles[2]
	si.ProcThreadAttributeList = attrs.List()
	var pi windows.ProcessInformation
	err = windows.CreateProcess(&encoded.app[0], &encoded.command[0], nil, nil, true, windows.EXTENDED_STARTUPINFO_PRESENT|windows.CREATE_UNICODE_ENVIRONMENT, &encoded.environment[0], &encoded.cwd[0], &si.StartupInfo, &pi)
	runtime.KeepAlive(encoded)
	if err != nil {
		return nil, err
	}
	// No fallible operation intervenes between CreateProcess success and ownership
	// transfer. Any later error returns this actual process alongside the error.
	owner.primary.value = pi.Process
	owner.thread.value = pi.Thread
	owner.pid = pi.ProcessId
	releaseAttrs() // Attribute values and child handles are live until Delete.
	p := &windowsProcess{owner}
	owner.startPumps(s.Input, out, errOut)
	for i := range owner.child {
		if e := owner.child[i].close(); e != nil && err == nil {
			err = e
		}
	}
	if e := owner.thread.close(); e != nil && err == nil {
		err = e
	}
	return p, err
}
func (p *windowsProcess) wait() waitResult {
	r := waitResult{exitCode: -1, ioCompletion: "unknown"}
	result, err := windows.WaitForSingleObject(p.container.primary.value, windows.INFINITE)
	if err != nil {
		r.err = err
		return r
	}
	if result != windows.WAIT_OBJECT_0 {
		r.err = errors.New("unexpected-process-wait")
		return r
	}
	var code uint32
	if err = windows.GetExitCodeProcess(p.container.primary.value, &code); err != nil {
		r.err = err
		return r
	}
	r.reaped = true
	r.exitCode = int(code)
	timer := time.NewTimer(250 * time.Millisecond)
	defer timer.Stop()
	select {
	case <-p.container.pumps.done:
	case <-timer.C:
	}
	r.ioCompletion = p.container.pumps.assessment()
	return r
}
