"""Real file-boundary checks; no Android, SSH or cleanup qualification is inferred."""

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/run_console_terminal_smoke.py"
SPEC = importlib.util.spec_from_file_location("console_terminal_controller", SCRIPT)
CONTROLLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONTROLLER)


class ConsoleEvidenceReadTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="cloudy-console-evidence-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def proof(self, name, body, mode=0o600):
        path = self.root / name
        with path.open("xb") as stream:
            os.fchmod(stream.fileno(), mode)
            stream.write(body)
        return path

    def test_exact_limit_preserves_binary_bytes(self):
        body = b"\x00\xffproof\r\n"
        path = self.proof("exact", body)
        self.assertEqual(CONTROLLER._read_owned_regular(path, len(body), private=True), body)

    def test_extra_byte_is_refused(self):
        path = self.proof("oversized", b"expected!")
        with self.assertRaises(ValueError):
            CONTROLLER._read_owned_regular(path, len(b"expected"), private=True)

    def test_symlink_to_valid_proof_is_refused(self):
        target = self.proof("target", b"expected")
        link = self.root / "link"
        link.symlink_to(target)
        with self.assertRaises(OSError):
            CONTROLLER._read_owned_regular(link, 64, private=True)

    def test_publicly_readable_private_proof_is_refused(self):
        path = self.proof("public-mode", b"expected", mode=0o644)
        with self.assertRaises(ValueError):
            CONTROLLER._read_owned_regular(path, 64, private=True)

    def test_fifo_is_refused_without_waiting_for_a_writer(self):
        path = self.root / "fifo"
        os.mkfifo(path, mode=0o600)
        # A bad blocking open must not hang the full suite; the child has only this owned FIFO.
        code = """
import runpy
import sys
from pathlib import Path
module = runpy.run_path(sys.argv[2])
try:
    module['_read_owned_regular'](Path(sys.argv[1]), 64, private=True)
except ValueError:
    sys.exit(17)
sys.exit(0)
"""
        result = subprocess.run([sys.executable, "-c", code, str(path), str(SCRIPT)],
                                capture_output=True, timeout=5, check=False)
        self.assertEqual(result.returncode, 17, result.stderr.decode(errors="replace"))


class ImeAuditBoundaryTests(unittest.TestCase):
    RUN = "12345678-1234-4234-8234-123456789abc"
    REQUEST = "23456789-1234-4234-8234-123456789abc"

    def record(self):
        return dict(schema=1, run=self.RUN, component=CONTROLLER.IME_COMPONENT,
                    requests=[self.REQUEST])

    def test_setting_restoration_distinguishes_absent_empty_and_whitespace(self):
        self.assertIsNone(CONTROLLER._parse_ime_setting(b"null\n"))
        self.assertEqual(CONTROLLER._parse_ime_setting(b"\n"), "")
        self.assertEqual(CONTROLLER._parse_ime_setting(b"component  \r\n"), "component  ")

    def test_incomplete_or_unbounded_setting_is_not_a_baseline(self):
        for body in (b"component", b"first\nsecond\n", b"\xff\n", b"a\x00\n",
                     b"x" * CONTROLLER.IME_SETTING_LIMIT + b"\n"):
            with self.subTest(body_length=len(body)), self.assertRaises(ValueError):
                CONTROLLER._parse_ime_setting(body)

    def test_owned_audit_includes_unpublished_input_and_lease_residue(self):
        paths = CONTROLLER._ime_record_paths(self.record(), self.RUN)
        root = CONTROLLER.IME_PRIVATE
        prefix = f"{root}/{self.RUN}-{self.REQUEST}"
        self.assertEqual(set(paths), {
            f"{root}/lease.json", f"{root}/lease.json.new", f"{root}/lease.lock",
            f"{root}/state.json.new", f"{root}/session-result.json.new",
            f"{prefix}.request.json", f"{prefix}.request.json.new",
            f"{prefix}.result.json", f"{prefix}.result.json.new",
        })

    def test_foreign_run_or_component_cannot_authorize_cleanup(self):
        for key, value in (("run", self.REQUEST), ("component", "foreign/Keyboard"), ("schema", True)):
            record = self.record()
            record[key] = value
            with self.subTest(field=key), self.assertRaises(ValueError):
                CONTROLLER._ime_record_paths(record, self.RUN)

    def test_request_ids_cannot_escape_or_expand_the_owned_namespace(self):
        for requests in (["../foreign"], [17], [self.REQUEST.upper()],
                         [self.REQUEST, self.REQUEST], [self.REQUEST] * 65):
            record = self.record()
            record["requests"] = requests
            with self.subTest(requests=requests), self.assertRaises(ValueError):
                CONTROLLER._ime_record_paths(record, self.RUN)


if __name__ == "__main__":
    unittest.main()
