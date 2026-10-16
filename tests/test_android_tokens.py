"""The checked-in Android constants must track the canonical token source."""
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("android_tokens", ROOT / "tools/generate_android_tokens.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class AndroidTokensTest(unittest.TestCase):
    def test_checked_in_output_matches_canonical_source(self):
        self.assertEqual(MODULE.OUTPUT.read_text(), MODULE.render(json.loads(MODULE.SOURCE.read_text())))

    def test_changed_semantic_token_changes_generated_output(self):
        source = json.loads(MODULE.SOURCE.read_text())
        before = MODULE.render(source)
        source["themes"]["dark"]["primary"] = "#123456"
        self.assertNotEqual(before, MODULE.render(source))
        self.assertIn("const val Primary = 0xFF123456L", MODULE.render(source))
