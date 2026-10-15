"""Bounded new preparation-adapter checks; no Android/JNI/network behavior simulated."""

import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
SPEC = importlib.util.spec_from_file_location(
    "stream_prepare", ROOT / "tools/prepare_native_stream.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PreparationChecks(unittest.TestCase):
    def test_changed_archive_bytes_rejected(self):
        raw = b"known"
        pin = {"bytes": len(raw), "sha256": MODULE.sha(raw)}
        MODULE.verified(raw, pin)
        with self.assertRaises(ValueError):
            MODULE.verified(b"other", pin)

    def test_archive_traversal_rejected(self):
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode="w:gz") as bundle:
            item = tarfile.TarInfo("source/../outside")
            item.size = 1
            bundle.addfile(item, io.BytesIO(b"x"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                MODULE.extract_source(
                    stream.getvalue(), {"root": "source"}, root / "source"
                )
            self.assertFalse((root / "outside").exists())

    def test_changed_authored_input_refuses_publication(self):
        with patch.object(
            MODULE, "tracked_snapshot", return_value=({"source": "new"}, {})
        ):
            with self.assertRaisesRegex(ValueError, "refusing output publication"):
                MODULE.unchanged({"source": "old"})

    def test_retained_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "actual").write_text("value")
            (root / "alias").symlink_to(root / "actual")
            with self.assertRaises(ValueError):
                MODULE.hashes(root)

    def test_fast_successful_process_cannot_bypass_final_log_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "log limit"):
                MODULE.bounded_build(
                    [sys.executable, "-c", 'print("x"*1000)'],
                    Path(directory) / "log",
                    {"timeoutSeconds": 5, "maximumLogBytes": 100},
                )

    def test_native_build_deadline_stops_real_child_process(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(subprocess.TimeoutExpired):
                MODULE.bounded_build(
                    [sys.executable, "-c", "import time; time.sleep(60)"],
                    Path(directory) / "log",
                    {"timeoutSeconds": 0.2, "maximumLogBytes": 100},
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
