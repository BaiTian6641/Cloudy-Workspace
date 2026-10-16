//go:build windows && amd64

// Independent native-test qualification safety fixture, not a product entry point.
package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

// Official public ABI: win32metadata 0f012441d654d3bb7de898fdc664b5fcad52bdcc.
const jobListAttribute = 0x2000d
const logCap = 1024 * 1024
const runBudget = 45 * time.Second
const cleanupBudget = 5 * time.Second

type accounting struct {
	Times                             [4]int64
	Faults, Total, Active, Terminated uint32
}

var _ [48 - unsafe.Sizeof(accounting{})]byte
var _ [unsafe.Sizeof(accounting{}) - 48]byte
var _ [40 - unsafe.Offsetof(accounting{}.Active)]byte
var _ [unsafe.Offsetof(accounting{}.Active) - 40]byte

type receipt struct {
	Schema                                                         int
	StartedUTC, EndedUTC                                           string
	SourceHashes                                                   map[string]string
	ManifestSHA256, TestSHA256, WrapperSHA256                      string
	GoVersion, GOOS, GOARCH                                        string
	WindowsMajor, WindowsMinor, WindowsBuild                       uint32
	Failures                                                       []string
	Created, SuspendedMembershipVerified, Resumed, ProcessTerminal bool
	ProcessID                                                      uint32
	CreationTime                                                   windows.Filetime
	ExitCode                                                       uint32
	TimedOut, OuterTerminationRequested                            bool
	EmptyBeforeSafetyCleanup, EmptyAfterSafetyCleanup              bool
	HandlesClosed, Passed                                          bool
	StdoutSHA256, StderrSHA256                                     string
	SourceCommit                                                   string
}
type supervisor struct {
	result               *receipt
	job, process, thread windows.Handle
	inherited            [3]windows.Handle
	files                []*os.File
	stdout, stderr       *os.File
	attempted            map[windows.Handle]bool
}

func (s *supervisor) fail(stage string) { s.result.Failures = append(s.result.Failures, stage) }
func (s *supervisor) closeHandle(h *windows.Handle) bool {
	if *h == 0 {
		return true
	}
	if s.attempted[*h] {
		return false
	}
	s.attempted[*h] = true
	if windows.CloseHandle(*h) != nil {
		s.fail("handle-close")
		return false
	}
	*h = 0
	return true
}
func (s *supervisor) empty() (bool, error) {
	var a accounting
	var n uint32
	err := windows.QueryInformationJobObject(s.job, windows.JobObjectBasicAccountingInformation, uintptr(unsafe.Pointer(&a)), uint32(unsafe.Sizeof(a)), &n)
	if err != nil {
		return false, err
	}
	if n != uint32(unsafe.Sizeof(a)) {
		return false, fmt.Errorf("accounting-size")
	}
	return a.Active == 0, nil
}
func (s *supervisor) waitProcess(ms uint32) bool {
	status, err := windows.WaitForSingleObject(s.process, ms)
	if err != nil || status != windows.WAIT_OBJECT_0 && status != uint32(windows.WAIT_TIMEOUT) {
		s.fail("process-wait")
		return false
	}
	if status == uint32(windows.WAIT_TIMEOUT) {
		return false
	}
	var code uint32
	if windows.GetExitCodeProcess(s.process, &code) != nil {
		s.fail("exit-code")
		return false
	}
	s.result.ProcessTerminal = true
	s.result.ExitCode = code
	return true
}
func (s *supervisor) observeEmpty(until time.Time) bool {
	for {
		empty, err := s.empty()
		if err != nil {
			s.fail("job-accounting")
			return false
		}
		if empty {
			return true
		}
		if !time.Now().Before(until) {
			return false
		}
		time.Sleep(20 * time.Millisecond)
	}
}
func (s *supervisor) finish(out string) {
	r := s.result
	if s.job != 0 {
		// Preserve natural inner-test retirement before safety cleanup. A successful
		// outer kill must never turn missing inner retirement into a passing result.
		if r.ProcessTerminal {
			r.EmptyBeforeSafetyCleanup = s.observeEmpty(time.Now().Add(500 * time.Millisecond))
		}
		if !r.EmptyBeforeSafetyCleanup {
			r.OuterTerminationRequested = true
			if windows.TerminateJobObject(s.job, 1) != nil {
				s.fail("outer-terminate")
			}
		}
		until := time.Now().Add(cleanupBudget)
		if s.process != 0 && !r.ProcessTerminal {
			s.waitProcess(uint32(cleanupBudget / time.Millisecond))
		}
		r.EmptyAfterSafetyCleanup = s.observeEmpty(until)
		if !r.EmptyAfterSafetyCleanup {
			s.fail("outer-retirement-uncertain")
		}
	}
	closed := true
	for i := range s.inherited {
		closed = s.closeHandle(&s.inherited[i]) && closed
	}
	closed = s.closeHandle(&s.thread) && closed
	closed = s.closeHandle(&s.process) && closed
	closed = s.closeHandle(&s.job) && closed
	for _, f := range s.files {
		if f.Close() != nil {
			s.fail("file-close")
			closed = false
		}
	}
	r.HandlesClosed = closed
	// Logs use files, so no outer drain goroutine can hide an inner pipe leak.
	data, err := readBounded(filepath.Join(out, "native.stdout"))
	if err != nil {
		s.fail("stdout-read")
	} else if verifyTerminals(data) != nil {
		s.fail("test-terminals")
	}
	if data, err = readBounded(filepath.Join(out, "native.stderr")); err != nil {
		s.fail("stderr-read")
	} else if len(data) != 0 {
		s.fail("nonempty-stderr")
	}
	r.StdoutSHA256, err = digestPath(filepath.Join(out, "native.stdout"))
	if err != nil {
		s.fail("stdout-digest")
	}
	r.StderrSHA256, err = digestPath(filepath.Join(out, "native.stderr"))
	if err != nil {
		s.fail("stderr-digest")
	}
	if !r.Created || !r.SuspendedMembershipVerified || !r.Resumed || !r.ProcessTerminal || r.ExitCode != 0 || !r.EmptyBeforeSafetyCleanup || r.OuterTerminationRequested || !r.EmptyAfterSafetyCleanup || !closed {
		s.fail("native-pass-boundary")
	}
	r.EndedUTC = time.Now().UTC().Format(time.RFC3339Nano)
	r.Passed = len(r.Failures) == 0
}
func readBounded(path string) ([]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	data, e := io.ReadAll(io.LimitReader(f, logCap+1))
	closeErr := f.Close()
	if e != nil {
		return nil, e
	}
	if closeErr != nil {
		return nil, closeErr
	}
	if len(data) > logCap {
		return nil, fmt.Errorf("log-cap")
	}
	return data, nil
}
func (s *supervisor) openLog(path string) (*os.File, error) {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_RDWR, 0600)
	if err == nil {
		s.files = append(s.files, f)
	}
	return f, err
}
func (s *supervisor) duplicate(f *os.File, index int) error {
	return windows.DuplicateHandle(windows.CurrentProcess(), windows.Handle(f.Fd()), windows.CurrentProcess(), &s.inherited[index], 0, true, windows.DUPLICATE_SAME_ACCESS)
}
func (s *supervisor) launch(exe, out string, deadline time.Time) bool {
	h, err := windows.CreateJobObject(nil, nil)
	if err != nil {
		s.fail("job-create")
		return false
	}
	s.job = h
	var limit windows.JOBOBJECT_EXTENDED_LIMIT_INFORMATION
	limit.BasicLimitInformation.LimitFlags = windows.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
	if _, err = windows.SetInformationJobObject(h, windows.JobObjectExtendedLimitInformation, uintptr(unsafe.Pointer(&limit)), uint32(unsafe.Sizeof(limit))); err != nil {
		s.fail("job-limit")
		return false
	}
	input, err := os.Open(os.DevNull)
	if err != nil {
		s.fail("stdin-open")
		return false
	}
	s.files = append(s.files, input)
	if s.stdout, err = s.openLog(filepath.Join(out, "native.stdout")); err != nil {
		s.fail("stdout-open")
		return false
	}
	if s.stderr, err = s.openLog(filepath.Join(out, "native.stderr")); err != nil {
		s.fail("stderr-open")
		return false
	}
	for i, f := range []*os.File{input, s.stdout, s.stderr} {
		if s.duplicate(f, i) != nil {
			s.fail("stdio-duplicate")
			return false
		}
	}
	attrs, err := windows.NewProcThreadAttributeList(2)
	if err != nil {
		s.fail("attributes-create")
		return false
	}
	jobs := [1]windows.Handle{h}
	handles := s.inherited
	release := func() {
		if attrs != nil {
			attrs.Delete()
			attrs = nil
		}
		runtime.KeepAlive(jobs)
		runtime.KeepAlive(handles)
	}
	defer release()
	if attrs.Update(jobListAttribute, unsafe.Pointer(&jobs[0]), unsafe.Sizeof(jobs)) != nil || attrs.Update(windows.PROC_THREAD_ATTRIBUTE_HANDLE_LIST, unsafe.Pointer(&handles[0]), unsafe.Sizeof(handles)) != nil {
		s.fail("attributes-update")
		return false
	}
	var si windows.StartupInfoEx
	si.Cb = uint32(unsafe.Sizeof(si))
	si.Flags = windows.STARTF_USESTDHANDLES
	si.StdInput = handles[0]
	si.StdOutput = handles[1]
	si.StdErr = handles[2]
	si.ProcThreadAttributeList = attrs.List()
	app, err := windows.UTF16FromString(exe)
	if err != nil {
		s.fail("test-path-encoding")
		return false
	}
	cwd, _ := os.Getwd()
	dir, err := windows.UTF16FromString(cwd)
	if err != nil {
		s.fail("cwd-encoding")
		return false
	}
	command, err := windows.UTF16FromString(windows.ComposeCommandLine([]string{exe, "-test.v", "-test.count=1", "-test.timeout=35s", "-test.run=^TestNativeWindowsOwnedJobs$"}))
	if err != nil {
		s.fail("command-encoding")
		return false
	}
	var random [16]byte
	if _, err = rand.Read(random[:]); err != nil {
		s.fail("owner-entropy")
		return false
	}
	// Only these explicit test inputs are inherited; no credentials or ambient
	// CLOUDY fixture role can enter the suite. TEMP is required for Go t.TempDir.
	entries := []string{"CLOUDY_NATIVE_WINDOWS_JOBS=1", "CLOUDY_NATIVE_WINDOWS_OWNER=" + hex.EncodeToString(random[:]), "SYSTEMROOT=" + os.Getenv("SYSTEMROOT"), "TEMP=" + out, "TMP=" + out}
	sort.Strings(entries)
	var env []uint16
	for _, entry := range entries {
		u, e := windows.UTF16FromString(entry)
		if e != nil {
			s.fail("environment-encoding")
			return false
		}
		env = append(env, u...)
	}
	env = append(env, 0)
	var pi windows.ProcessInformation
	err = windows.CreateProcess(&app[0], &command[0], nil, nil, true, windows.EXTENDED_STARTUPINFO_PRESENT|windows.CREATE_UNICODE_ENVIRONMENT|windows.CREATE_SUSPENDED, &env[0], &dir[0], &si.StartupInfo, &pi)
	runtime.KeepAlive(app)
	runtime.KeepAlive(command)
	runtime.KeepAlive(env)
	runtime.KeepAlive(dir)
	if err != nil {
		s.fail("contained-create")
		return false
	}
	s.process = pi.Process
	s.thread = pi.Thread
	s.result.Created = true
	s.result.ProcessID = pi.ProcessId
	release()
	for i := range s.inherited {
		if !s.closeHandle(&s.inherited[i]) {
			return false
		}
	}
	// The child cannot execute user code yet. Corroborate this exact new outer
	// job, not any runner job; hProcess prevents PID reuse during the query.
	var list struct {
		Assigned, Listed uint32
		IDs              [2]uintptr
	}
	var n uint32
	if windows.QueryInformationJobObject(h, windows.JobObjectBasicProcessIdList, uintptr(unsafe.Pointer(&list)), uint32(unsafe.Sizeof(list)), &n) != nil || list.Assigned != 1 || list.Listed != 1 || list.IDs[0] != uintptr(pi.ProcessId) {
		s.fail("suspended-membership")
		return false
	}
	var exit, kernel, user windows.Filetime
	if windows.GetProcessTimes(s.process, &s.result.CreationTime, &exit, &kernel, &user) != nil {
		s.fail("creation-time")
		return false
	}
	s.result.SuspendedMembershipVerified = true
	if !time.Now().Before(deadline) {
		s.fail("pre-resume-deadline")
		return false
	}
	if checkSources(s.result.SourceHashes) != nil {
		s.fail("source-pre-resume")
		return false
	}
	prior, err := windows.ResumeThread(s.thread)
	if err != nil || prior != 1 {
		s.fail("resume-gate")
		return false
	}
	s.result.Resumed = true
	return true
}
func run() int {
	exe := flag.String("test-binary", "", "exact test executable")
	testSHA := flag.String("test-sha256", "", "expected test executable SHA256")
	wrapperSHA := flag.String("wrapper-sha256", "", "expected supervisor executable SHA256")
	sourceCommit := flag.String("source-commit", "", "exact checkout commit")
	manifest := flag.String("source-manifest", "", "ten-source JSON hash map, relative to host/core")
	output := flag.String("out-dir", "", "new exclusive evidence directory")
	flag.Parse()
	commitBytes, commitErr := hex.DecodeString(*sourceCommit)
	if runtime.Version() != "go1.27.2" || commitErr != nil || len(commitBytes) != 20 || flag.NArg() != 0 || !filepath.IsAbs(*exe) || !filepath.IsAbs(*output) || !filepath.IsAbs(*manifest) || !validDigest(*testSHA) || !validDigest(*wrapperSHA) {
		fmt.Fprintln(os.Stderr, "native-supervisor: invalid-input")
		return 1
	}
	sources, err := loadSources(*manifest)
	if err != nil || checkSources(sources) != nil {
		fmt.Fprintln(os.Stderr, "native-supervisor: source-preflight")
		return 1
	}
	self, err := os.Executable()
	if err != nil {
		return 1
	}
	actual, err := digestPath(self)
	if err != nil || actual != *wrapperSHA {
		return 1
	}
	// Retain a read-only non-write/non-delete-sharing binary handle through create.
	// Trusted checkout ancestors remain a prerequisite; this is not hostile-path confinement.
	name, err := windows.UTF16PtrFromString(*exe)
	if err != nil {
		return 1
	}
	raw, err := windows.CreateFile(name, windows.GENERIC_READ, windows.FILE_SHARE_READ, nil, windows.OPEN_EXISTING, windows.FILE_FLAG_OPEN_REPARSE_POINT, 0)
	if err != nil {
		return 1
	}
	binary := os.NewFile(uintptr(raw), "locked-native-test")
	var attributes windows.ByHandleFileInformation
	if windows.GetFileInformationByHandle(raw, &attributes) != nil || attributes.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
		binary.Close()
		return 1
	}
	info, err := binary.Stat()
	if err != nil || !info.Mode().IsRegular() {
		binary.Close()
		return 1
	}
	actual, err = digestFile(binary)
	if err != nil || actual != *testSHA {
		binary.Close()
		return 1
	}
	if os.Mkdir(*output, 0700) != nil {
		binary.Close()
		return 1
	}
	r := &receipt{Schema: 1, StartedUTC: time.Now().UTC().Format(time.RFC3339Nano), SourceHashes: sources, TestSHA256: *testSHA, WrapperSHA256: *wrapperSHA, GoVersion: runtime.Version(), GOOS: runtime.GOOS, GOARCH: runtime.GOARCH}
	r.SourceCommit = *sourceCommit
	r.ManifestSHA256, err = digestPath(*manifest)
	if err != nil {
		binary.Close()
		return 1
	}
	v := windows.RtlGetVersion()
	r.WindowsMajor = v.MajorVersion
	r.WindowsMinor = v.MinorVersion
	r.WindowsBuild = v.BuildNumber
	s := &supervisor{result: r, files: []*os.File{binary}, attempted: map[windows.Handle]bool{}}
	deadline := time.Now().Add(runBudget)
	if s.launch(*exe, *output, deadline) {
		for {
			if s.waitProcess(100) {
				break
			}
			if len(r.Failures) > 0 {
				break
			}
			if !time.Now().Before(deadline) {
				r.TimedOut = true
				s.fail("outer-deadline")
				break
			}
			over := false
			for _, f := range []*os.File{s.stdout, s.stderr} {
				info, e := f.Stat()
				if e != nil || info.Size() > logCap {
					over = true
				}
			}
			if over {
				s.fail("log-cap")
				break
			}
		}
	}
	s.finish(*output)
	if checkSources(sources) != nil {
		s.fail("source-postflight")
		r.Passed = false
	}
	data, err := json.MarshalIndent(r, "", "  ")
	if err != nil {
		return 1
	}
	f, err := os.OpenFile(filepath.Join(*output, "result.json"), os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return 1
	}
	n, writeErr := f.Write(append(data, '\n'))
	closeErr := f.Close()
	if writeErr != nil || n != len(data)+1 || closeErr != nil || !r.Passed {
		return 1
	}
	return 0
}
func main() { os.Exit(run()) }
