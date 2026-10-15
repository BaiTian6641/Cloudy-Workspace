"""Android staging integrity checks; no compiler, network or native runtime."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
SPEC = importlib.util.spec_from_file_location(
    "stream_android_prepare", ROOT / "tools/prepare_stream_android.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
REAL_INPUTS = MODULE.inputs


class AndroidPreparationChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.stage = root / "stage"
        self.stage.mkdir()
        native_cache = root / "native"
        native_run = native_cache / "accepted"
        native_run.mkdir(parents=True)
        native_receipt = b'{"acceptedFixture": true}\n'
        (native_run / "receipt.json").write_bytes(native_receipt)
        self.files = {"app/src/main/java/Probe.java": b"class Probe {}\n"}
        for name, raw in self.files.items():
            path = self.stage / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        self.receipt = {
            "inputs": {},
            "nativeRun": "accepted",
            "nativeReceipt": MODULE.file_record(native_receipt),
            "stagedFiles": {
                name: MODULE.file_record(raw) for name, raw in self.files.items()
            },
        }
        self.save_receipt()
        # The accepted upstream reconstruction is a fixture here. Actual source
        # reconstruction/ELF checks belong to the native preparation tests/run.
        for context in (
            patch.object(MODULE.native, "CACHE", native_cache),
            patch.object(MODULE, "inputs", return_value={}),
            patch.object(MODULE, "expected_files", return_value=(self.files, {})),
        ):
            context.start()
            self.addCleanup(context.stop)

    def save_receipt(self):
        (self.stage / "cloudy-preparation.json").write_text(json.dumps(self.receipt))

    def test_missing_required_overlay_rejects_upstream_fallback(self):
        overlay = self.stage / "overlay"
        overlay.mkdir()
        # Invoke the real function while the remaining tests isolate authored
        # inputs; the missing-manifest check runs before wrapper reads.
        with patch.object(MODULE, "OVERLAY", overlay):
            with self.assertRaisesRegex(
                ValueError, "Required owned Android overlay missing"
            ):
                REAL_INPUTS()

    def test_changed_staged_source_rejected(self):
        (self.stage / next(iter(self.files))).write_bytes(b"class Changed {}\n")
        with self.assertRaisesRegex(
            ValueError, "Retained Android source/native input changed"
        ):
            MODULE.check(self.stage)

    def test_unrecorded_build_logic_and_source_rejected(self):
        for name in (
            "buildSrc/build.gradle",
            "app/src/main/java/Extra.java",
            "app/build/generated/cloudy/nativeLibraries/unreviewed.so",
        ):
            with self.subTest(name=name):
                path = self.stage / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"unreviewed")
                try:
                    with self.assertRaisesRegex(
                        ValueError, "Unreviewed Android source"
                    ):
                        MODULE.check(self.stage)
                finally:
                    path.unlink()

    def test_normal_gradle_outputs_allowed(self):
        for name in (
            ".gradle/cache.bin",
            "build/reports/report.txt",
            "app/build/outputs/apk/debug/app.apk",
        ):
            path = self.stage / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"generated")
        self.assertEqual(MODULE.check(self.stage), self.receipt)

    def test_unsafe_receipt_relative_path_rejected(self):
        self.receipt["stagedFiles"]["../outside"] = MODULE.file_record(b"outside")
        self.save_receipt()
        with self.assertRaises(ValueError):
            MODULE.check(self.stage)


if __name__ == "__main__":
    unittest.main()
