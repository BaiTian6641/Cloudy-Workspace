//go:build windows && amd64

package jobs

import (
	"errors"
	"reflect"
	"strings"
	"testing"
	"unsafe"

	"golang.org/x/sys/windows"
)

func TestWindowsEncodingExactEmptyEnvironmentAndArguments(t *testing.T) {
	s := validSpec(t)
	s.Args = []string{"", `space here`, `quote"here`, `end\`, "雪🙂"}
	e, err := encodeWindows(s)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(e.environment, []uint16{0, 0}) {
		t.Fatal("empty environment became ambient")
	}
	got, err := windows.DecomposeCommandLine(windows.UTF16ToString(e.command))
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(got, append([]string{s.Executable}, s.Args...)) {
		t.Fatal("argument encoding changed")
	}
	s.Env = []string{"z=last", "A0=prefix-child", "A=first"}
	e, err = encodeWindows(s)
	if err != nil {
		t.Fatal(err)
	}
	a, _ := windows.UTF16FromString("A=first")
	z, _ := windows.UTF16FromString("z=last")
	a0, _ := windows.UTF16FromString("A0=prefix-child")
	if !reflect.DeepEqual(e.environment, append(append(append(a, a0...), z...), 0)) {
		t.Fatal("environment ordering/termination changed")
	}
}
func TestWindowsEncodingRejectsAmbiguousOrOversizeInput(t *testing.T) {
	cases := map[string]func(*Spec){
		"case-duplicate": func(s *Spec) { s.Env = []string{"Path=a", "PATH=b"} },
		"invalid-arg":    func(s *Spec) { s.Args = []string{string([]byte{0xff})} },
		"invalid-env":    func(s *Spec) { s.Env = []string{"A=" + string([]byte{0xff})} },
		"nul":            func(s *Spec) { s.Args = []string{"x\x00"} },
		"shell":          func(s *Spec) { s.Executable = `C:\safe\task.cmd` },
		"escaped-total": func(s *Spec) {
			s.Args = make([]string, 8)
			for i := range s.Args {
				s.Args[i] = strings.Repeat(`"`, 4096)
			}
		},
	}
	for name, change := range cases {
		t.Run(name, func(t *testing.T) {
			s := validSpec(t)
			change(&s)
			if _, err := encodeWindows(s); err == nil {
				t.Fatal("invalid encoding admitted")
			}
		})
	}
}
func TestWindowsHandleCloseIsSingleAttemptAndSticky(t *testing.T) {
	for _, bad := range []bool{false, true} {
		h := winHandle{value: 42}
		calls := 0
		cause := errors.New("close uncertain")
		closeFn := func(windows.Handle) error {
			calls++
			if bad {
				return cause
			}
			return nil
		}
		first := h.closeWith(closeFn)
		second := h.closeWith(closeFn)
		if calls != 1 || first != second || h.attempted != true || bad != (h.value != 0) || bad != (first != nil) {
			t.Fatal("close ownership changed")
		}
	}
}
func TestWindowsAccountingSDKLayoutAndPumpAssessment(t *testing.T) {
	if procThreadAttributeJobList != 0x2000d || unsafe.Sizeof(jobAccounting{}) != 48 || unsafe.Offsetof(jobAccounting{}.ActiveProcesses) != 40 {
		t.Fatal("SDK ABI changed")
	}
	p := &winPumps{done: make(chan struct{})}
	if p.assessment() != "not-assessed" {
		t.Fatal("pending pump called complete")
	}
	close(p.done)
	if p.assessment() != "complete" {
		t.Fatal("joined pump not complete")
	}
	p.errs[1] = errors.New("read failed")
	if p.assessment() != "incomplete" {
		t.Fatal("read failure hidden")
	}
}

// These completed/pending channels model the cleanup state machine only. They
// neither open OS handles nor establish real Windows pipe cancellation behavior.
func TestWindowsCleanupRequiresActualCloseAndPumpJoins(t *testing.T) {
	p := &winPipe{closed: make(chan struct{})}
	p.once.Do(func() {})
	pumps := &winPumps{done: make(chan struct{})}
	c := &windowsContainer{pipes: [3]*winPipe{p}, pumps: pumps}
	if e := c.cleanup(); e == nil {
		t.Fatal("pending close accepted")
	}
	close(p.closed)
	if e := c.cleanup(); e == nil {
		t.Fatal("pending pump accepted")
	}
	close(pumps.done)
	if e := c.cleanup(); e != nil {
		t.Fatal("actual joins did not settle", e)
	}
}
func TestWindowsCleanupCloseErrorCannotBecomeRetired(t *testing.T) {
	cause := errors.New("close failed")
	p := &winPipe{closed: make(chan struct{}), closeErr: cause}
	p.once.Do(func() {})
	close(p.closed)
	c := &windowsContainer{pipes: [3]*winPipe{p}}
	if e := c.cleanup(); e != cause {
		t.Fatal("lost close error", e)
	}
	if e := c.cleanup(); e != cause {
		t.Fatal("failed close became retired", e)
	}
}
