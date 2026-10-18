#!/usr/bin/env python3
"""Finite real terminal exercise; generated output is fixture data, not input proof.

Run inside the ordinary fixture shell using an absolute Python -I/script path.
Evidence is exclusive mode-0600 JSON in the existing private --workdir only.
Captured bytes/keys are actual reads, not inferred from displayed output. Local
PTY tests establish helper semantics, never physical/IME/Android qualification.
"""

import argparse
import curses
from datetime import datetime, timezone
import errno
import hashlib
import json
import locale
import os
from pathlib import Path
import select
import signal
import stat
import termios
import time
import tty
import uuid

MAX_CAPTURE_BYTES = 65536
MAX_TUI_EVENTS = 64
MAX_RECORD_BYTES = 262144


class ExerciseError(Exception):
    pass


def private_directory(path):
    value = Path(path)
    if not value.is_absolute() or '..' in value.parts:
        raise ExerciseError('workdir-invalid')
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
    directory = os.open('/', flags)
    try:
        for part in value.parts[1:]:
            child = os.open(part, flags, dir_fd=directory)
            os.close(directory)
            directory = child
        info = os.fstat(directory)
        if info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != 0o700:
            raise ExerciseError('workdir-not-private')
        return directory
    except BaseException:
        os.close(directory)
        raise


def save_record(directory, record):
    data = (json.dumps(record, ensure_ascii=True, separators=(',', ':')) + '\n').encode('ascii')
    if len(data) > MAX_RECORD_BYTES:
        raise ExerciseError('record-too-large')
    fd = os.open('exercise-' + record['runId'] + '.json',
                 os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC,
                 0o600, dir_fd=directory)
    try:
        os.fchmod(fd, 0o600)
        remaining = memoryview(data)
        while remaining:
            count = os.write(fd, remaining)
            if count <= 0:
                raise ExerciseError('record-write-failed')
            remaining = remaining[count:]
        os.fsync(fd)
        os.fsync(directory)
    finally:
        os.close(fd)


def ansi_payload():
    palette = ''.join(f'\x1b[{30 + n}m FG{n} \x1b[0m' for n in range(8))
    bright = ''.join(f'\x1b[{90 + n}m BR{n} \x1b[0m' for n in range(8))
    return ('\x1b[1mCloudy ANSI / UTF-8 fixture\x1b[0m\n' + palette + '\n' + bright + '\n'
            'ASCII: abc XYZ 0123456789\n'
            'CJK: 中文 日本語 한국어\n'
            'Combining: e\u0301 a\u0308 | precomposed: é ä\n'
            'Emoji examples: 😀 🚀 👩\u200d💻\n'
            '\x1b[4munderline\x1b[0m \x1b[7minverse\x1b[0m '
            '\x1b[38;2;147;197;253mtruecolor\x1b[0m\n'
            'CLOUDY_ANSI_OUTPUT_END\n').encode('utf-8')


def emit_ansi(record, deadline):
    data = ansi_payload()
    record.update({'expectedOutputBytes': len(data), 'expectedOutputSha256': hashlib.sha256(data).hexdigest(),
                   'writtenBytes': 0, 'scope': 'Generated output; rendering and glyph quality require independent observation.'})
    # Fixed small writes can split a UTF-8 sequence here, but SSH may coalesce
    # them. This does not claim a particular remote packet or bridge boundary.
    previous = os.get_blocking(1)
    os.set_blocking(1, False)
    try:
        while record['writtenBytes'] < len(data):
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not select.select([], [1], [], remaining)[1]:
                raise ExerciseError('output-timeout')
            try:
                start = record['writtenBytes']
                count = os.write(1, data[start:start + 7])
            except BlockingIOError:
                continue
            if count <= 0:
                raise ExerciseError('output-no-progress')
            record['writtenBytes'] += count
    finally:
        os.set_blocking(1, previous)
    return 'output-written'


def capture(record, deadline, max_bytes):
    original = termios.tcgetattr(0)
    captured = bytearray()
    record['maxBytes'] = max_bytes
    record['inputOrigin'] = 'Unclassified: operator must separately identify physical, IME or injected stimulus.'
    try:
        tty.setraw(0, when=termios.TCSANOW)
        os.write(1, ('CLOUDY_CAPTURE_READY_' + record['runId'] + '\r\n').encode('ascii'))
        while len(captured) < max_bytes:
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not select.select([0], [], [], remaining)[0]:
                return 'capture-timeout'
            chunk = os.read(0, min(512, max_bytes - len(captured)))
            if not chunk:
                return 'input-eof'
            captured.extend(chunk)
        return 'capture-byte-limit'
    finally:
        record.update({'capturedBytes': len(captured), 'capturedHex': captured.hex(),
                       'capturedSha256': hashlib.sha256(captured).hexdigest()})
        # A bounded capture must not hand excess queued paste to the surrounding
        # shell as a command. Input-only flush avoids TCSAFLUSH's output-drain
        # wait, so a stalled reader does not delay termios restoration. Later
        # bytes remain ordinary shell input, not captured proof.
        try:
            termios.tcflush(0, termios.TCIFLUSH)
        finally:
            termios.tcsetattr(0, termios.TCSANOW, original)
        record['pendingInputDiscardedOnRestore'] = True


def tui(record, deadline):
    record['events'] = []
    record['inputOrigin'] = 'Unclassified: curses events are actual reads, not physical-input proof.'

    def screen(window):
        window.keypad(True)
        window.timeout(100)
        x = y = 0
        reason = 'tui-timeout'
        while time.monotonic() < deadline:
            rows, columns = window.getmaxyx()
            # Independent ioctl observation distinguishes requested sizes from
            # the actual kernel PTY size seen by this process.
            actual = os.get_terminal_size(0)
            record['lastSize'] = {'columns': actual.columns, 'rows': actual.lines}
            window.erase()
            lines = ['Cloudy real curses alternate-screen fixture',
                     f'PTY {actual.columns} columns x {actual.lines} rows',
                     'Arrow keys move; q exits; other keys are recorded.',
                     'CJK: 中文 日本語 | combining: e\u0301 | emoji: 😀',
                     f'Position x={x} y={y}; events={len(record["events"])}']
            for row, line in enumerate(lines[:max(0, rows - 1)]):
                try:
                    window.addnstr(row, 0, line, max(0, columns - 1))
                except curses.error:
                    # A wide glyph at the edge is a display limitation, not an
                    # observed input. Retain the narrow-layout condition.
                    record['drawClipped'] = True
            window.refresh()
            try:
                key = window.get_wch()
            except curses.error:
                continue
            event = {'kind': 'key-code', 'code': key} if isinstance(key, int) else {'kind': 'unicode', 'value': key}
            record['events'].append(event)
            if key == 'q':
                reason = 'tui-quit'
                break
            if key == curses.KEY_LEFT:
                x = max(-1000, x - 1)
            elif key == curses.KEY_RIGHT:
                x = min(1000, x + 1)
            elif key == curses.KEY_UP:
                y = max(-1000, y - 1)
            elif key == curses.KEY_DOWN:
                y = min(1000, y + 1)
            if len(record['events']) >= MAX_TUI_EVENTS:
                reason = 'tui-event-limit'
                break
        record['position'] = {'x': x, 'y': y}
        return reason

    return curses.wrapper(screen)


def run(args):
    if not all(os.isatty(fd) for fd in (0, 1, 2)):
        raise ExerciseError('tty-required')
    if len({os.fstat(fd).st_rdev for fd in (0, 1, 2)}) != 1:
        raise ExerciseError('stdio-tty-mismatch')
    if os.environ.get('TERM') != 'xterm-256color':
        raise ExerciseError('term-rejected')
    locale.setlocale(locale.LC_ALL, 'C.UTF-8')
    if locale.nl_langinfo(locale.CODESET).upper().replace('-', '') != 'UTF8':
        raise ExerciseError('utf8-locale-unavailable')
    directory = private_directory(args.workdir)
    record = {'schemaVersion': 1, 'runId': args.run or str(uuid.uuid4()), 'mode': args.mode,
              'startedAtUtc': datetime.now(timezone.utc).isoformat(), 'timeoutSeconds': args.timeout}
    started = time.monotonic()
    previous_handlers = {}
    previous_timer = None
    exit_code = 0

    def stop(signum, _frame):
        names = {signal.SIGALRM: 'overall-timeout', signal.SIGINT: 'signal-int',
                 signal.SIGTERM: 'signal-term', signal.SIGHUP: 'signal-hup'}
        raise ExerciseError(names[signum])

    try:
        for signum in (signal.SIGALRM, signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
            previous_handlers[signum] = signal.signal(signum, stop)
        # The ordinary read deadline is earlier, allowing curses to restore the
        # terminal before the hard bound interrupts a blocked write or library.
        previous_timer = signal.setitimer(signal.ITIMER_REAL, args.timeout + 2)
        deadline = started + args.timeout
        if args.mode == 'ansi':
            record['termination'] = emit_ansi(record, deadline)
        elif args.mode == 'capture':
            record['termination'] = capture(record, deadline, args.max_bytes)
        else:
            record['termination'] = tui(record, deadline)
    except (ExerciseError, OSError, curses.error, termios.error) as error:
        if isinstance(error, ExerciseError):
            record['termination'] = str(error)
        elif isinstance(error, OSError) and error.errno == errno.EIO:
            record['termination'] = 'terminal-io-failure'
        else:
            record['termination'] = 'terminal-operation-failed'
        exit_code = 74
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        for signum, handler in previous_handlers.items():
            signal.signal(signum, handler)
        if previous_timer is not None:
            signal.setitimer(signal.ITIMER_REAL, *previous_timer)
        record.update({'finishedAtUtc': datetime.now(timezone.utc).isoformat(),
                       'elapsedMs': round((time.monotonic() - started) * 1000, 3),
                       'intendedExitCode': exit_code})
        try:
            save_record(directory, record)
        finally:
            os.close(directory)
    return exit_code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workdir', required=True)
    parser.add_argument('--mode', choices=('ansi', 'tui', 'capture'), required=True)
    parser.add_argument('--timeout', type=float, default=30)
    parser.add_argument('--max-bytes', type=int, default=256)
    parser.add_argument('--run', help='Optional canonical UUID4 for one correlated owned exercise')
    args = parser.parse_args()
    if not 1 <= args.timeout <= 120 or not 1 <= args.max_bytes <= MAX_CAPTURE_BYTES:
        raise ExerciseError('bounds-rejected')
    if args.run is not None:
        try:
            parsed = uuid.UUID(args.run)
        except ValueError as error:
            raise ExerciseError('run-invalid') from error
        if parsed.version != 4 or str(parsed) != args.run:
            raise ExerciseError('run-invalid')
    return run(args)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ExerciseError, OSError, locale.Error, curses.error, termios.error) as error:
        reason = str(error) if isinstance(error, ExerciseError) else 'startup-or-evidence-failure'
        os.write(2, ('CLOUDY_TERMINAL_EXERCISE_ERROR ' + reason + '\n').encode('ascii'))
        raise SystemExit(74)
