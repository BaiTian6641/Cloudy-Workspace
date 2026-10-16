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


class PairingReplayChecks(unittest.TestCase):
    def fixture(self):
        name = "app/src/main/java/Example.java"
        before, after = b"before\n", b"after\n"
        raw = ("--- a/" + name + "\n+++ b/" + name +
               "\n@@ -1 +1 @@\n-before\n+after\n").encode()
        prefix = MODULE.PAIRING.relative_to(ROOT).as_posix() + "/"
        lock = {"patch": {"file": "finite-pairing.patch", "sha256": MODULE.digest(raw)},
                "authoredSources": {}, "files": {name: {
                    "beforeSha256": MODULE.digest(before), "afterSha256": MODULE.digest(after)}}}
        contents = {prefix + "finite-pairing.patch": raw,
                    prefix + "sources.lock.json": json.dumps(lock).encode()}
        return name, before, after, raw, prefix, lock, contents

    def test_exact_offline_replay(self):
        name, before, after, _, _, _, contents = self.fixture()
        files = {name: before, "unrelated-native.so": b"unchanged"}
        MODULE.apply_pairing(files, contents)
        self.assertEqual(files, {name: after, "unrelated-native.so": b"unchanged"})

    def test_changed_patch_before_after_and_extra_target_rejected(self):
        for case in ("patch", "before", "after", "extra"):
            with self.subTest(case=case):
                name, before, _, raw, prefix, lock, contents = self.fixture()
                files = {name: before}
                if case == "patch":
                    contents[prefix + "finite-pairing.patch"] += b"changed"
                elif case == "before":
                    files[name] = b"changed\n"
                elif case == "after":
                    lock["files"][name]["afterSha256"] = MODULE.digest(b"wrong")
                else:
                    raw += b"--- a/extra\n+++ b/extra\n@@ -1 +1 @@\n-x\n+y\n"
                    contents[prefix + "finite-pairing.patch"] = raw
                    lock["patch"]["sha256"] = MODULE.digest(raw)
                contents[prefix + "sources.lock.json"] = json.dumps(lock).encode()
                with self.assertRaises(ValueError):
                    MODULE.apply_pairing(files, contents)
                self.assertEqual(files[name], b"changed\n" if case == "before" else before)

    def test_authored_pairing_source_is_frozen(self):
        name, before, _, _, prefix, lock, contents = self.fixture()
        lock["authoredSources"] = {"owned.java": MODULE.file_record(b"accepted")}
        contents["owned.java"] = b"changed"
        contents[prefix + "sources.lock.json"] = json.dumps(lock).encode()
        with self.assertRaisesRegex(ValueError, "authored pairing source changed"):
            MODULE.apply_pairing({name: before}, contents)


class PlaybackReplayChecks(unittest.TestCase):
    def test_composed_replay_requires_exact_pairing_output_and_dependency(self):
        name, before, paired, _, _, _, contents = PairingReplayChecks().fixture()
        prefix = MODULE.PLAYBACK.relative_to(ROOT).as_posix() + "/"
        prerequisite = MODULE.PAIRING.relative_to(ROOT).as_posix() + "/finite-pairing.patch"
        raw = ("--- a/" + name + "\n+++ b/" + name +
               "\n@@ -1 +1 @@\n-after\n+playback\n").encode()
        lock = {"appliesAfter": {"path": prerequisite,
                                 "sha256": MODULE.digest(contents[prerequisite])},
                "patch": {"file": "finite-playback.patch", "sha256": MODULE.digest(raw)},
                "authoredSources": {}, "files": {name: {
                    "beforeSha256": MODULE.digest(paired),
                    "afterSha256": MODULE.digest(b"playback\n")}}}
        contents[prefix + "finite-playback.patch"] = raw
        contents[prefix + "sources.lock.json"] = json.dumps(lock).encode()
        # A patch may only consume the reviewed preceding transform, not the
        # original upstream source or a different revision with similar hunks.
        with self.assertRaisesRegex(ValueError, "before hash changed"):
            MODULE.apply_playback({name: before}, contents)
        files = {name: before}
        MODULE.apply_pairing(files, contents)
        MODULE.apply_playback(files, contents)
        self.assertEqual(files[name], b"playback\n")
        contents[prerequisite] += b"different prerequisite"
        with self.assertRaisesRegex(ValueError, "prerequisite pairing patch changed"):
            MODULE.apply_playback({name: paired}, contents)


class CatalogReplayChecks(unittest.TestCase):
    def test_catalog_requires_exact_playback_output_and_patch(self):
        name = "app/src/main/java/Example.java"
        prefix = MODULE.CATALOG.relative_to(ROOT).as_posix() + "/"
        prerequisite = MODULE.PLAYBACK.relative_to(ROOT).as_posix() + "/finite-playback.patch"
        raw = ("--- a/" + name + "\n+++ b/" + name +
               "\n@@ -1 +1 @@\n-playback\n+catalog\n").encode()
        contents = {prerequisite: b"reviewed playback patch"}
        lock = {"appliesAfter": {"path": prerequisite,
                                 "sha256": MODULE.digest(contents[prerequisite])},
                "patch": {"file": "finite-catalog.patch", "sha256": MODULE.digest(raw)},
                "authoredSources": {}, "files": {name: {
                    "beforeSha256": MODULE.digest(b"playback\n"),
                    "afterSha256": MODULE.digest(b"catalog\n")}}}
        contents[prefix + "finite-catalog.patch"] = raw
        contents[prefix + "sources.lock.json"] = json.dumps(lock).encode()
        # Catalog cannot bypass either accepted predecessor by replaying against
        # a similar earlier source. Unrelated native bytes remain untouched.
        with self.assertRaisesRegex(ValueError, "before hash changed"):
            MODULE.apply_catalog({name: b"pairing\n"}, contents)
        files = {name: b"playback\n", "native.so": b"accepted"}
        MODULE.apply_catalog(files, contents)
        self.assertEqual(files, {name: b"catalog\n", "native.so": b"accepted"})
        contents[prerequisite] += b"changed"
        with self.assertRaisesRegex(ValueError, "prerequisite playback patch changed"):
            MODULE.apply_catalog({name: b"playback\n"}, contents)


class CompatibilityReplayChecks(unittest.TestCase):
    def test_compatibility_requires_exact_owned_source_and_catalog_patch(self):
        name = "app/src/main/java/Example.java"
        prefix = MODULE.COMPATIBILITY.relative_to(ROOT).as_posix() + "/"
        prerequisite = MODULE.CATALOG.relative_to(ROOT).as_posix() + "/finite-catalog.patch"
        raw = ("--- a/" + name + "\n+++ b/" + name +
               "\n@@ -1 +1 @@\n-playback\n+catalog\n").encode()
        contents = {prerequisite: b"reviewed playback patch"}
        lock = {"appliesAfter": {"path": prerequisite,
                                 "sha256": MODULE.digest(contents[prerequisite])},
                "patch": {"file": "finite-compatibility.patch", "sha256": MODULE.digest(raw)},
                "authoredSources": {}, "files": {name: {
                    "beforeSha256": MODULE.digest(b"playback\n"),
                    "afterSha256": MODULE.digest(b"catalog\n")}}}
        contents[prefix + "finite-compatibility.patch"] = raw
        contents[prefix + "sources.lock.json"] = json.dumps(lock).encode()
        # The fourth overlay must consume exact staged owned bytes and the exact
        # catalog predecessor. Native bytes remain outside the patch target.
        with self.assertRaisesRegex(ValueError, "before hash changed"):
            MODULE.apply_compatibility({name: b"pairing\n"}, contents)
        files = {name: b"playback\n", "native.so": b"accepted"}
        MODULE.apply_compatibility(files, contents)
        self.assertEqual(files, {name: b"catalog\n", "native.so": b"accepted"})
        contents[prerequisite] += b"changed"
        with self.assertRaisesRegex(ValueError, "prerequisite catalog patch changed"):
            MODULE.apply_compatibility({name: b"playback\n"}, contents)


if __name__ == "__main__":
    unittest.main()
