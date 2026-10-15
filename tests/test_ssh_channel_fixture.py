"""Real local pipe/PTY processes; no SSH listener, Android or physical-input claim."""

import contextlib
import errno
import fcntl
import importlib.util
import json
import os
from pathlib import Path
import pty
import select
import signal
import stat
import struct
import subprocess
import sys
import tempfile
import termios
import time
import unittest
from unittest.mock import patch
import uuid

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/ssh_channel_fixture.py"
SPEC = importlib.util.spec_from_file_location("ssh_channel_fixture", SCRIPT)
FIXTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIXTURE)

# Only this test launcher shortens deadlines or injects storage faults. The
# production CLI accepts no such arguments or environment overrides.
TEST_LAUNCHER = """
import os, runpy, sys
m = runpy.run_path(sys.argv[1])
if sys.argv[5] == 'fail-fsync':
    def fail(fd):
        raise OSError('secret-path-must-not-be-logged')
    m['os'].fsync = fail
elif sys.argv[5] == 'fail-event-fsync':
    original = m['os'].fsync
    calls = 0
    def fail_event(fd):
        global calls
        calls += 1
        if calls == 3:
            raise OSError('secret-event-failure-must-not-be-logged')
        original(fd)
    m['os'].fsync = fail_event
sys.exit(m['run'](os.environ.get('SSH_ORIGINAL_COMMAND', ''), sys.argv[2],
                 step_seconds=float(sys.argv[3]), overall_seconds=float(sys.argv[4])))
"""


def resize(descriptor, columns, rows):
    fcntl.ioctl(descriptor, termios.TIOCSWINSZ, struct.pack("HHHH", rows, columns, 0, 0))


class PtyChild:
    def __init__(self, process, master):
        self.process = process
        self.master = master
        self.pending = bytearray()

    def send(self, data):
        self.assert_open()
        os.write(self.master, data)

    def assert_open(self):
        if self.master is None:
            raise AssertionError("test master already closed")

    def read_exact(self, expected, seconds=3):
        self.assert_open()
        end = time.monotonic() + seconds
        while len(self.pending) < len(expected):
            remaining = end - time.monotonic()
            if remaining <= 0 or not select.select([self.master], [], [], remaining)[0]:
                raise AssertionError("PTY output did not arrive before test deadline")
            try:
                data = os.read(self.master, 4096)
            except OSError as error:
                if error.errno == errno.EIO:
                    raise AssertionError("PTY hung up before expected bytes") from error
                raise
            if not data:
                raise AssertionError("PTY EOF before expected bytes")
            self.pending.extend(data)
        actual = bytes(self.pending[:len(expected)])
        del self.pending[:len(expected)]
        if actual != expected:
            raise AssertionError(f"Unexpected fixed protocol bytes: {actual!r}")

    def close_master(self):
        if self.master is not None:
            os.close(self.master)
            self.master = None


class SshChannelFixtureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="cloudy-channel-test-")
        self.addCleanup(self.temp.cleanup)
        self.audit_dir = Path(self.temp.name) / "audit"
        self.audit_dir.mkdir(mode=0o700)

    def command(self, *, step=2, overall=4, fault="none"):
        return [sys.executable, "-I", "-c", TEST_LAUNCHER, str(SCRIPT), str(self.audit_dir),
                str(step), str(overall), fault]

    def environment(self, command):
        return {"PATH": os.defpath, "SSH_ORIGINAL_COMMAND": command,
                "UNRELATED_PRIVATE_VALUE": "secret-environment-must-not-be-logged"}

    def exec_process(self, command=FIXTURE.EXEC_TOKEN, *, fault="none"):
        return subprocess.run(self.command(fault=fault), env=self.environment(command),
                              input=b"", capture_output=True, timeout=5, check=False)

    def audits(self):
        records = []
        for path in sorted(self.audit_dir.glob("*.jsonl")):
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            data = path.read_bytes()
            self.assertLessEqual(len(data), FIXTURE.MAX_AUDIT_EVENTS * FIXTURE.MAX_EVENT_BYTES)
            if data:
                rows = [json.loads(line) for line in data.splitlines()]
                self.assertEqual([row["sequence"] for row in rows], list(range(1, len(rows) + 1)))
                self.assertEqual(len({row["runId"] for row in rows}), 1)
                self.assertTrue(all(row["elapsedMs"] >= 0 and row["atUtc"].endswith("+00:00") for row in rows))
                self.assertNotIn("secret-environment", data.decode())
                self.assertNotIn(str(self.audit_dir), data.decode())
                records.append(rows)
        return records

    @contextlib.contextmanager
    def terminal(self, *, command="", columns=80, rows=24, change=None, step=2, overall=4):
        master, slave = pty.openpty()
        process = None
        child = None
        try:
            attrs = termios.tcgetattr(slave)
            attrs[3] = (attrs[3] | termios.ICANON) & ~(termios.ECHO | termios.ECHONL)
            attrs[1] &= ~termios.ONLCR
            attrs[6][termios.VEOF] = b"\x04"
            if change is not None:
                change(attrs)
            termios.tcsetattr(slave, termios.TCSANOW, attrs)
            resize(slave, columns, rows)
            process = subprocess.Popen(self.command(step=step, overall=overall),
                                       env=self.environment(command), stdin=slave, stdout=slave,
                                       stderr=slave, start_new_session=True, close_fds=True)
            os.close(slave)
            slave = None
            child = PtyChild(process, master)
            yield child
        finally:
            if child is not None:
                child.close_master()
            else:
                os.close(master)
            if slave is not None:
                os.close(slave)
            if process is not None:
                if process.poll() is None:
                    process.terminate()
                try:
                    process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=2)
                    raise AssertionError("fixture failed to terminate during test cleanup")

    def ready(self, child):
        child.read_exact(b"CLOUDY_PTY_READY_V1\nSIZE 80 24\n")

    def test_exact_exec_streams_exit_and_private_audit(self):
        result = self.exec_process()
        self.assertEqual((result.stdout, result.stderr, result.returncode),
                         (b"Cloudy exec stdout\n", b"Cloudy exec stderr\n", 17))
        events = self.audits()[0]
        self.assertEqual([e["event"] for e in events], ["started", "exec-output-written", "finished"])
        self.assertEqual(events[-1]["intendedExitCode"], 17)
        self.assertEqual(events[-1]["reason"], "completed")

    def test_production_cli_matches_exec_without_timeout_override(self):
        result = subprocess.run([sys.executable, "-I", str(SCRIPT), "--audit-dir", str(self.audit_dir)],
                                env=self.environment(FIXTURE.EXEC_TOKEN), input=b"", capture_output=True,
                                timeout=5, check=False)
        self.assertEqual(result.returncode, 17)
        self.assertEqual(result.stdout, b"Cloudy exec stdout\n")
        self.assertEqual(result.stderr, b"Cloudy exec stderr\n")

    def test_unknown_commands_are_rejected_without_executing_or_logging_them(self):
        marker = Path(self.temp.name) / "must-not-exist"
        for command in ["id", "cloudy-exec-probe-v1 ", "cloudy-exec-probe-v1;id",
                        f"touch {marker}", "secret-command\n", "sftp"]:
            with self.subTest(command=command):
                result = self.exec_process(command)
                self.assertEqual(result.returncode, 64)
                self.assertEqual(result.stdout, b"")
                self.assertEqual(result.stderr, b"CLOUDY_FIXTURE_ERROR command-rejected\n")
        self.assertFalse(marker.exists())
        self.assertTrue(all(events[0]["mode"] == "rejected" for events in self.audits()))
        self.assertNotIn("secret-command", "".join(p.read_text() for p in self.audit_dir.iterdir()))

    def test_empty_command_requires_a_real_tty_and_exec_forbids_one(self):
        result = self.exec_process("")
        self.assertEqual((result.returncode, result.stdout), (65, b""))
        self.assertEqual(result.stderr, b"CLOUDY_FIXTURE_ERROR pty-required\n")
        with self.terminal(command=FIXTURE.EXEC_TOKEN) as child:
            child.read_exact(b"CLOUDY_FIXTURE_ERROR exec-tty-rejected\n")
            self.assertEqual(child.process.wait(timeout=2), 65)

    def test_real_pty_reports_ioctl_resize_and_exact_unmodified_bytes(self):
        with self.terminal() as child:
            self.ready(child)
            resize(child.master, 120, 40)
            child.send(b"SIZE\n")
            child.read_exact(b"SIZE 120 40\n")
            child.send(b"FINISH\n")
            child.read_exact(b"CLOUDY_PTY_DONE_V1\n")
            self.assertEqual(child.process.wait(timeout=2), 0)
            self.assertEqual(child.pending, b"")
        events = self.audits()[0]
        observed = [e for e in events if e["event"] == "terminal-observed"]
        self.assertEqual([(e["columns"], e["rows"]) for e in observed], [(80, 24), (120, 40)])
        self.assertTrue(all(e["flags"] == {"echo": False, "echoNewline": False,
                                         "canonical": True, "outputCrLf": False} for e in observed))
        self.assertEqual(events[-1]["intendedExitCode"], 0)

    def test_every_required_terminal_flag_is_checked_without_changing_it(self):
        changes = [(3, termios.ECHO, True), (3, termios.ECHONL, True),
                   (3, termios.ICANON, False), (1, termios.ONLCR, True)]
        for index, bit, enabled in changes:
            def change(attrs):
                attrs[index] = attrs[index] | bit if enabled else attrs[index] & ~bit
            with self.subTest(flag=bit), self.terminal(change=change) as child:
                self.assertEqual(child.process.wait(timeout=2), 65)
                self.assertEqual(bool(termios.tcgetattr(child.master)[index] & bit), enabled)
        self.assertTrue(all(events[-1]["reason"] == "terminal-modes-rejected" for events in self.audits()))

    def test_initial_wrong_or_zero_dimensions_cannot_pass(self):
        for columns, rows in [(81, 24), (80, 25), (0, 24)]:
            with self.subTest(size=(columns, rows)), self.terminal(columns=columns, rows=rows) as child:
                self.assertEqual(child.process.wait(timeout=2), 65)

    def test_query_rechecks_actual_terminal_modes_and_dimensions(self):
        with self.terminal() as child:
            self.ready(child)
            resize(child.master, 1001, 40)
            child.send(b"SIZE\n")
            child.read_exact(b"CLOUDY_FIXTURE_ERROR terminal-size-out-of-range\n")
            self.assertEqual(child.process.wait(timeout=2), 65)
        with self.terminal() as child:
            self.ready(child)
            attrs = termios.tcgetattr(child.master)
            attrs[3] |= termios.ECHO
            termios.tcsetattr(child.master, termios.TCSANOW, attrs)
            child.send(b"SIZE\n")
            self.assertEqual(child.process.wait(timeout=2), 65)
        self.assertEqual(self.audits()[-1][-1]["intendedExitCode"], 65)

    def test_wrong_or_overlong_lines_fail_the_real_reader(self):
        for token in [b"FINISH\n", b"SIZE extra\n", b"\0\n", b"A" * 100 + b"\n"]:
            with self.subTest(token=token), self.terminal() as child:
                self.ready(child)
                child.send(token)
                self.assertEqual(child.process.wait(timeout=2), 64)

    def test_repeated_query_and_extra_complete_line_are_rejected(self):
        for ending in [b"SIZE\n", b"FINISH\nEXTRA\n"]:
            with self.subTest(ending=ending), self.terminal() as child:
                self.ready(child)
                child.send(b"SIZE\n")
                child.read_exact(b"SIZE 80 24\n")
                child.send(ending)
                self.assertEqual(child.process.wait(timeout=2), 64)
        reasons = {events[-1]["reason"] for events in self.audits()}
        self.assertEqual(reasons, {"input-token-rejected", "extra-input-rejected"})

    def test_incomplete_canonical_line_waits_until_real_step_deadline(self):
        with self.terminal(step=0.2, overall=1) as child:
            self.ready(child)
            started = time.monotonic()
            child.send(b"SIZ")
            child.read_exact(b"CLOUDY_FIXTURE_ERROR deadline-exceeded\n")
            self.assertEqual(child.process.wait(timeout=2), 124)
            self.assertGreater(time.monotonic() - started, 0.1)
        self.assertEqual(self.audits()[0][-1]["reason"], "deadline-exceeded")

    def test_overall_deadline_is_shorter_than_individual_wait(self):
        with self.terminal(step=1, overall=0.2) as child:
            self.ready(child)
            child.read_exact(b"CLOUDY_FIXTURE_ERROR deadline-exceeded\n")
            self.assertEqual(child.process.wait(timeout=2), 124)
        self.assertLess(self.audits()[0][-1]["elapsedMs"], 1000)

    def test_real_canonical_eof_is_distinct_from_success(self):
        with self.terminal() as child:
            self.ready(child)
            child.send(b"\x04")
            child.read_exact(b"CLOUDY_FIXTURE_ERROR input-eof\n")
            self.assertEqual(child.process.wait(timeout=2), 66)
        self.assertEqual(self.audits()[0][-1]["reason"], "input-eof")

    def test_master_hangup_and_process_signal_have_distinct_audit_reasons(self):
        with self.terminal() as child:
            self.ready(child)
            child.close_master()
            self.assertEqual(child.process.wait(timeout=2), 129)
        with self.terminal() as child:
            self.ready(child)
            child.process.send_signal(signal.SIGHUP)
            self.assertEqual(child.process.wait(timeout=2), 129)
        self.assertEqual({events[-1]["reason"] for events in self.audits()},
                         {"terminal-hangup", "signal-hup"})

    def test_private_directory_symlinks_and_nonprivate_modes_are_rejected(self):
        self.audit_dir.chmod(0o755)
        result = self.exec_process()
        self.assertEqual(result.returncode, 74)
        self.assertEqual(list(self.audit_dir.iterdir()), [])
        self.audit_dir.chmod(0o700)
        link = Path(self.temp.name) / "link"
        link.symlink_to(self.audit_dir, target_is_directory=True)
        with self.assertRaises(OSError):
            FIXTURE.Audit(link)
        (self.audit_dir / "nested").mkdir(mode=0o700)
        with self.assertRaises(OSError):
            FIXTURE.Audit(link / "nested")
        with self.assertRaises(FIXTURE.FixtureFailure):
            FIXTURE.Audit("relative/path")

    def test_exclusive_audit_creation_does_not_replace_an_existing_file(self):
        identity = uuid.UUID("7fcd607e-0b13-408b-9c5d-7221be4174ce")
        with patch.object(FIXTURE.uuid, "uuid4", return_value=identity):
            audit = FIXTURE.Audit(self.audit_dir)
            audit.event("test-marker")
            audit.close()
            path = next(self.audit_dir.iterdir())
            before = path.read_bytes()
            with self.assertRaises(FileExistsError):
                FIXTURE.Audit(self.audit_dir)
            self.assertEqual(path.read_bytes(), before)

    def test_fsync_failure_is_not_reported_as_success_and_does_not_leak_messages(self):
        result = self.exec_process(fault="fail-fsync")
        self.assertEqual(result.returncode, 74)
        self.assertEqual(result.stdout, b"")
        self.assertEqual(result.stderr, b"CLOUDY_FIXTURE_ERROR audit-open-failed\n")
        self.assertNotIn(b"secret-path", result.stderr)
        self.assertEqual(len(list(self.audit_dir.iterdir())), 1)

    def test_event_fsync_failure_after_output_overrides_the_expected_exec_exit(self):
        result = self.exec_process(fault="fail-event-fsync")
        self.assertEqual(result.returncode, 74)
        # Bytes already written cannot be retracted, but the operation must not
        # return the expected 17 when its durable output observation failed.
        self.assertEqual(result.stdout, b"Cloudy exec stdout\n")
        self.assertEqual(result.stderr,
                         b"Cloudy exec stderr\nCLOUDY_FIXTURE_ERROR audit-io-failure\n")
        # No final event is appended after durability becomes unknown. The
        # observed subprocess status, not a synthetic cleanup event, is evidence.
        self.assertEqual([event["event"] for event in self.audits()[0]],
                         ["started", "exec-output-written"])

    def test_audit_event_and_byte_caps_are_enforced_before_unbounded_append(self):
        audit = FIXTURE.Audit(self.audit_dir)
        try:
            with self.assertRaises(FIXTURE.FixtureFailure):
                audit.event("oversized-test", fixed="x" * FIXTURE.MAX_EVENT_BYTES)
            for _ in range(FIXTURE.MAX_AUDIT_EVENTS):
                audit.event("bounded-test")
            with self.assertRaises(FIXTURE.FixtureFailure):
                audit.event("one-too-many")
        finally:
            audit.close()
        self.assertEqual(len(self.audits()[0]), FIXTURE.MAX_AUDIT_EVENTS)


if __name__ == "__main__":
    unittest.main()
