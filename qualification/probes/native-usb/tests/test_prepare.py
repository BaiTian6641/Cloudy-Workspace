"""Focused integrity-boundary checks; no native/device behavior is simulated."""

import importlib.util
from pathlib import Path
import struct
import sys
import os
import subprocess
import time
from unittest.mock import patch
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "prepare_usb_native", Path(__file__).resolve().parents[4] / "tools/prepare_usb_native.py")
sys.path.insert(0, str(Path(__file__).resolve().parents[4] / "tools"))
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)


class PreparationTest(unittest.TestCase):
    def test_changed_inputs_refuse_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "authored.c"
            path.write_text("compiled source")
            original = {"authored.c": NATIVE.digest(path)}
            path.write_text("new source after compile")
            with patch.object(NATIVE, "inputs", return_value={"authored.c": NATIVE.digest(path)}):
                with self.assertRaisesRegex(ValueError, "refusing publication"):
                    NATIVE.require_unchanged(original)

    def test_timeout_stops_descendant_after_leader_exits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pidfile = root / "child.pid"
            child = "import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); time.sleep(60)"
            leader = ("import subprocess,sys,time; "
                      f"child=subprocess.Popen([sys.executable,'-c',{child!r}]); "
                      f"open({str(pidfile)!r},'w').write(str(child.pid)); time.sleep(60)")
            with self.assertRaises(subprocess.TimeoutExpired):
                NATIVE.run_build([sys.executable, "-c", leader], root / "build.log", timeout=0.5)
            pid = int(pidfile.read_text())
            for _ in range(50):
                status = Path(f"/proc/{pid}/stat")
                if not status.exists() or status.read_text().split()[2] == "Z":
                    break
                time.sleep(0.02)
            else:
                os.kill(pid, 9)
                self.fail("compiler descendant survived timeout cleanup")

    def test_cached_archive_tamper_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "archive"
            path.write_bytes(b"expected")
            pin = {"bytes": 8, "sha256": NATIVE.digest(path)}
            NATIVE.verify_archive(path, pin)
            path.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "tracked source pin"):
                NATIVE.verify_archive(path, pin)

    def test_symlink_is_not_a_source_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "actual"
            path.write_bytes(b"source")
            alias = Path(directory) / "alias"
            alias.symlink_to(path)
            with self.assertRaisesRegex(ValueError, "Symlink input"):
                NATIVE.regular(alias)

    def test_wrong_architecture_is_rejected_before_packaging(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "binary"
            raw = bytearray(120)
            struct.pack_into("<16sHHIQQQIHHHHHH", raw, 0, b"\x7fELF\x02\x01\x01", 3, 62, 1, 0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
            path.write_bytes(raw)
            with self.assertRaisesRegex(ValueError, "AArch64"):
                NATIVE.elf_check(path)

    def test_four_kib_load_alignment_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "binary"
            raw = bytearray(120)
            struct.pack_into("<16sHHIQQQIHHHHHH", raw, 0, b"\x7fELF\x02\x01\x01", 3, 183, 1, 0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
            struct.pack_into("<IIQQQQQQ", raw, 64, 1, 5, 0, 0, 0, 120, 120, 4096)
            path.write_bytes(raw)
            with self.assertRaisesRegex(ValueError, "16KiB"):
                NATIVE.elf_check(path)


if __name__ == "__main__":
    unittest.main()
