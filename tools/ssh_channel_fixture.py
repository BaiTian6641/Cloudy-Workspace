#!/usr/bin/env python3
"""Restricted P0 SSH ForceCommand fixture, never a production shell or agent.

Launch through a fixed absolute ``python -I`` command with ``--audit-dir``;
the existing audit directory must be owned by this user and mode 0700.
SSH_ORIGINAL_COMMAND selects only the fixed exec token or an empty-command
PTY handshake. No command, environment value or terminal input is evaluated
or copied into evidence. Use a separate sshd policy with forwarding disabled.

Python's public termios/fcntl APIs supply actual terminal flags and dimensions:
https://docs.python.org/3/library/termios.html
https://docs.python.org/3/library/fcntl.html
https://man.openbsd.org/sshd_config#ForceCommand
"""

from datetime import datetime, timezone
import errno
import fcntl
import json
import os
from pathlib import Path
import select
import signal
import stat
import struct
import sys
import termios
import time
import uuid

EXEC_TOKEN = "cloudy-exec-probe-v1"
EXEC_STDOUT = b"Cloudy exec stdout\n"
EXEC_STDERR = b"Cloudy exec stderr\n"
PTY_READY = b"CLOUDY_PTY_READY_V1\n"
PTY_DONE = b"CLOUDY_PTY_DONE_V1\n"
STEP_TIMEOUT_SECONDS = 120.0
OVERALL_TIMEOUT_SECONDS = 180.0
LINE_LIMIT = 32
MAX_AUDIT_EVENTS = 12
MAX_EVENT_BYTES = 1024


class FixtureFailure(Exception):
    """Only source-defined codes cross the audit/stderr boundary."""

    def __init__(self, code, exit_code=64):
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code


def _private_directory(path):
    value = Path(path)
    if not value.is_absolute() or ".." in value.parts:
        raise FixtureFailure("audit-directory-invalid", 74)
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
    directory = os.open("/", flags)
    try:
        # Walk by open directory descriptors: no component may be a symlink.
        for part in value.parts[1:]:
            child = os.open(part, flags, dir_fd=directory)
            os.close(directory)
            directory = child
        info = os.fstat(directory)
        if info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != 0o700:
            raise FixtureFailure("audit-directory-not-private", 74)
        return directory
    except BaseException:
        os.close(directory)
        raise


class Audit:
    """One exclusive private JSONL file; every bounded event is fsynced.

    An abrupt process/filesystem failure can leave an incomplete record. A
    missing terminal event must never be interpreted as successful cleanup.
    """

    def __init__(self, directory):
        self.fd = None
        self.sequence = 0
        self.broken = False
        self.started = time.monotonic()
        self.run_id = str(uuid.uuid4())
        descriptor = _private_directory(directory)
        try:
            self.fd = os.open(
                "channel-" + self.run_id + ".jsonl",
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC,
                0o600,
                dir_fd=descriptor,
            )
            os.fchmod(self.fd, 0o600)
            # Bind the new directory entry too; failures prevent protocol start.
            os.fsync(descriptor)
        except BaseException:
            self.close()
            raise
        finally:
            os.close(descriptor)

    def event(self, event, **fields):
        if self.broken:
            raise FixtureFailure("audit-io-failure", 74)
        if self.sequence >= MAX_AUDIT_EVENTS:
            raise FixtureFailure("audit-event-limit", 74)
        entry = {
            "schemaVersion": 1,
            "runId": self.run_id,
            "sequence": self.sequence + 1,
            "event": event,
            "atUtc": datetime.now(timezone.utc).isoformat(),
            "elapsedMs": round((time.monotonic() - self.started) * 1000, 3),
            **fields,
        }
        data = (json.dumps(entry, separators=(",", ":")) + "\n").encode("ascii")
        if len(data) > MAX_EVENT_BYTES:
            raise FixtureFailure("audit-byte-limit", 74)
        try:
            remaining = memoryview(data)
            while remaining:
                count = os.write(self.fd, remaining)
                if count <= 0:
                    raise OSError(errno.EIO, "audit-no-progress")
                remaining = remaining[count:]
            os.fsync(self.fd)
        except OSError as error:
            # A partial write or failed sync leaves durability unknown. Never
            # append a clean-looking terminal record after an uncertain write.
            self.broken = True
            raise FixtureFailure("audit-io-failure", 74) from error
        except FixtureFailure:
            self.broken = True
            raise
        self.sequence += 1

    def close(self):
        if self.fd is not None:
            descriptor, self.fd = self.fd, None
            os.close(descriptor)


class Deadline:
    def __init__(self, step_seconds, overall_seconds):
        if not (0 < step_seconds <= STEP_TIMEOUT_SECONDS and
                0 < overall_seconds <= OVERALL_TIMEOUT_SECONDS):
            raise FixtureFailure("invalid-timeout")
        self.step_seconds = step_seconds
        self.end = time.monotonic() + overall_seconds

    def step(self):
        return min(self.end, time.monotonic() + self.step_seconds)

    def wait(self, descriptor, end, *, writing=False):
        while True:
            remaining = min(end, self.end) - time.monotonic()
            if remaining <= 0:
                raise FixtureFailure("deadline-exceeded", 124)
            try:
                readable, writable, _ = select.select(
                    [] if writing else [descriptor], [descriptor] if writing else [], [], remaining
                )
            except InterruptedError:
                continue
            if readable or writable:
                return


def _write(descriptor, data, deadline):
    end = deadline.step()
    remaining = memoryview(data)
    while remaining:
        deadline.wait(descriptor, end, writing=True)
        try:
            count = os.write(descriptor, remaining)
        except BlockingIOError:
            continue
        if count <= 0:
            raise FixtureFailure("output-no-progress", 74)
        remaining = remaining[count:]


def _terminal(descriptor):
    attrs = termios.tcgetattr(descriptor)
    flags = {
        "echo": bool(attrs[3] & termios.ECHO),
        "echoNewline": bool(attrs[3] & termios.ECHONL),
        "canonical": bool(attrs[3] & termios.ICANON),
        "outputCrLf": bool(attrs[1] & termios.ONLCR),
    }
    rows, columns, _, _ = struct.unpack("HHHH", fcntl.ioctl(
        descriptor, termios.TIOCGWINSZ, struct.pack("HHHH", 0, 0, 0, 0)
    ))
    if not 1 <= rows <= 1000 or not 1 <= columns <= 1000:
        raise FixtureFailure("terminal-size-out-of-range", 65)
    return flags, columns, rows


def _read_token(descriptor, token, deadline):
    end = deadline.step()
    while True:
        deadline.wait(descriptor, end)
        try:
            line = os.read(descriptor, LINE_LIMIT + 1)
        except BlockingIOError:
            continue
        if not line:
            # Linux can return an empty read for both canonical VEOF and a
            # closed PTY master. Preserve the kernel hangup signal separately.
            poller = select.poll()
            poller.register(descriptor, select.POLLHUP | select.POLLERR)
            if any(flags & select.POLLHUP for _, flags in poller.poll(0)):
                raise FixtureFailure("terminal-hangup", 129)
            raise FixtureFailure("input-eof", 66)
        if len(line) > LINE_LIMIT or not line.endswith(b"\n"):
            raise FixtureFailure("input-line-invalid")
        if line != token:
            raise FixtureFailure("input-token-rejected")
        return


def _pty(stdin, stdout, audit, deadline):
    flags, columns, rows = _terminal(stdin)
    audit.event("terminal-observed", phase="initial", columns=columns, rows=rows, flags=flags)
    if flags != {"echo": False, "echoNewline": False, "canonical": True, "outputCrLf": False}:
        raise FixtureFailure("terminal-modes-rejected", 65)
    if (columns, rows) != (80, 24):
        raise FixtureFailure("initial-size-rejected", 65)
    _write(stdout, PTY_READY + f"SIZE {columns} {rows}\n".encode("ascii"), deadline)
    audit.event("ready-written")
    _read_token(stdin, b"SIZE\n", deadline)
    flags, columns, rows = _terminal(stdin)
    audit.event("terminal-observed", phase="query", columns=columns, rows=rows, flags=flags)
    if flags != {"echo": False, "echoNewline": False, "canonical": True, "outputCrLf": False}:
        raise FixtureFailure("terminal-modes-changed", 65)
    _write(stdout, f"SIZE {columns} {rows}\n".encode("ascii"), deadline)
    audit.event("size-written", columns=columns, rows=rows)
    _read_token(stdin, b"FINISH\n", deadline)
    # Canonical input exposes completed lines. Reject extra readable data now;
    # future bytes after exit and an uncompleted terminal line are not observed.
    if select.select([stdin], [], [], 0)[0]:
        extra = os.read(stdin, LINE_LIMIT + 1)
        if extra:
            raise FixtureFailure("extra-input-rejected")
    _write(stdout, PTY_DONE, deadline)
    audit.event("done-written")
    return 0


def run(original_command, audit_dir, *, stdin=0, stdout=1, stderr=2,
        step_seconds=STEP_TIMEOUT_SECONDS, overall_seconds=OVERALL_TIMEOUT_SECONDS):
    """Execute once; tests may shorten real deadlines, never the production CLI."""
    audit = None
    result = 74
    reason = "startup-failed"
    prior_handlers = {}
    prior_flags = {}
    previous_timer = None

    def stop(signum, _frame):
        names = {signal.SIGHUP: "signal-hup", signal.SIGTERM: "signal-term",
                 signal.SIGINT: "signal-int", signal.SIGALRM: "deadline-exceeded"}
        raise FixtureFailure(names[signum], 124 if signum == signal.SIGALRM else 128 + signum)

    try:
        deadline = Deadline(step_seconds, overall_seconds)
        for signum in (signal.SIGHUP, signal.SIGTERM, signal.SIGINT, signal.SIGALRM):
            prior_handlers[signum] = signal.signal(signum, stop)
        previous_timer = signal.setitimer(signal.ITIMER_REAL, overall_seconds)
        try:
            audit = Audit(audit_dir)
        except OSError as error:
            raise FixtureFailure("audit-open-failed", 74) from error
        mode = "exec" if original_command == EXEC_TOKEN else "pty" if original_command == "" else "rejected"
        audit.event("started", mode=mode)
        if mode == "rejected":
            raise FixtureFailure("command-rejected")
        tty = [os.isatty(fd) for fd in (stdin, stdout, stderr)]
        if mode == "exec" and any(tty):
            raise FixtureFailure("exec-tty-rejected", 65)
        if mode == "pty" and (not all(tty) or len({os.fstat(fd).st_rdev for fd in (stdin, stdout, stderr)}) != 1):
            raise FixtureFailure("pty-required", 65)
        # Nonblocking descriptors plus select keep output/input waits bounded.
        # Capture every original flag first because PTY stdio may share an open file.
        prior_flags = {fd: fcntl.fcntl(fd, fcntl.F_GETFL) for fd in (stdin, stdout, stderr)}
        for fd, flags in prior_flags.items():
            fcntl.fcntl(fd, fcntl.F_SETFL, flags | os.O_NONBLOCK)
        if mode == "exec":
            _write(stdout, EXEC_STDOUT, deadline)
            _write(stderr, EXEC_STDERR, deadline)
            audit.event("exec-output-written", stdoutBytes=len(EXEC_STDOUT), stderrBytes=len(EXEC_STDERR))
            result = 17
        else:
            result = _pty(stdin, stdout, audit, deadline)
        reason = "completed"
    except FixtureFailure as error:
        result, reason = error.exit_code, error.code
    except OSError as error:
        result, reason = (129, "terminal-hangup") if error.errno == errno.EIO else (74, "io-failure")
    finally:
        for fd, flags in prior_flags.items():
            try:
                fcntl.fcntl(fd, fcntl.F_SETFL, flags)
            except OSError:
                result, reason = 74, "descriptor-restore-failed"
        if audit is not None:
            try:
                # This is the intended exit, not independent proof of process
                # disappearance. The server controller must observe that too.
                audit.event("finished", intendedExitCode=result, reason=reason)
                audit.close()
            except (OSError, FixtureFailure):
                result, reason = 74, "audit-io-failure"
                try:
                    audit.close()
                except OSError:
                    pass
        signal.setitimer(signal.ITIMER_REAL, 0)
        for signum, handler in prior_handlers.items():
            signal.signal(signum, handler)
        if previous_timer is not None:
            signal.setitimer(signal.ITIMER_REAL, *previous_timer)
    if reason != "completed":
        # Error diagnostics are fixed and bounded, never exception messages or input.
        try:
            old = os.get_blocking(stderr)
            os.set_blocking(stderr, False)
            try:
                os.write(stderr, ("CLOUDY_FIXTURE_ERROR " + reason + "\n").encode("ascii"))
            finally:
                os.set_blocking(stderr, old)
        except OSError:
            pass
    return result


def main(argv=None):
    values = sys.argv[1:] if argv is None else argv
    if len(values) != 2 or values[0] != "--audit-dir":
        os.write(2, b"CLOUDY_FIXTURE_ERROR arguments-rejected\n")
        return 64
    return run(os.environ.get("SSH_ORIGINAL_COMMAND", ""), values[1])


if __name__ == "__main__":
    raise SystemExit(main())
