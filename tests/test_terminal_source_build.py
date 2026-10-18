"""Real temporary pins exercise the renderer source-build boundary; no compiler runs."""

import base64
import copy
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("build_terminal_renderer",
                                              ROOT / "tools/build_terminal_renderer.py")
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)

COMPOSITION_PATH = "package/src/browser/input/CompositionHelper.ts"
TERMINAL_PATH = "package/src/browser/public/Terminal.ts"
SOURCE_JSON = json.dumps({
    "name": "@xterm/xterm", "version": "6.1.0-beta.315", "license": "MIT",
}).encode()
LICENSE = b"MIT fixture license\n"
TERMINAL = b"export class Terminal {}\n"
COMPOSITION = (
    b"export class CompositionHelper {\n"
    b"  public value: number = 0;\n"
    b"}\n"
)
PATCH = (
    "--- a/package/src/browser/input/CompositionHelper.ts\n"
    "+++ b/package/src/browser/input/CompositionHelper.ts\n"
    "@@ -1,3 +1,3 @@\n"
    " export class CompositionHelper {\n"
    "-  public value: number = 0;\n"
    "+  public value: number = 1;\n"
    " }\n"
)
COMPILER_BINARY = b"#!/bin/false\n"
COMPILER_JSON = json.dumps({
    "name": "@esbuild/linux-x64", "version": "0.28.0", "license": "MIT",
}).encode()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def integrity(data):
    return "sha512-" + base64.b64encode(hashlib.sha512(data).digest()).decode()


def make_archive(entries):
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode="w", format=tarfile.PAX_FORMAT) as archive:
        for name, body, kind in entries:
            member = tarfile.TarInfo(name)
            member.type = kind
            if kind in (tarfile.SYMTYPE, tarfile.LNKTYPE):
                member.linkname = "../../outside"
                member.size = 0
            else:
                member.size = len(body)
            archive.addfile(member, io.BytesIO(body))
    return gzip.compress(stream.getvalue(), mtime=0)


class CompilerStub:
    def __init__(self, output):
        self.output = output

    def __call__(self, arguments, **options):
        outfile = next(argument.split("=", 1)[1] for argument in arguments
                       if argument.startswith("--outfile="))
        Path(outfile).write_bytes(self.output)
        return subprocess.CompletedProcess(arguments, 0, b"", b"")


class TerminalSourceBuildTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="cloudy-terminal-source-build-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.repository = self.root / "repository"
        self.cache = self.root / "cache"
        self.product = self.root / "product"
        self.patch_path = self.repository / "tools/patches/test.patch"
        self.patch_path.parent.mkdir(parents=True)
        self.patch_path.write_text(PATCH)
        (self.cache / "source-build").mkdir(parents=True)

        self.source_entries = [
            ("package/package.json", SOURCE_JSON, tarfile.REGTYPE),
            ("package/LICENSE", LICENSE, tarfile.REGTYPE),
            (TERMINAL_PATH, TERMINAL, tarfile.REGTYPE),
            (COMPOSITION_PATH, COMPOSITION, tarfile.REGTYPE),
        ]
        self.source_archive = make_archive(self.source_entries)
        (self.cache / "xterm-6.1.0-beta.315.tgz").write_bytes(self.source_archive)
        self.source_record = {
            "name": "@xterm/xterm",
            "version": "6.1.0-beta.315",
            "upstreamCommit": BUILD.SOURCE_COMMIT,
            "license": "MIT",
            "cacheFile": "xterm-6.1.0-beta.315.tgz",
            "archiveUrl": "https://registry.npmjs.org/@xterm/xterm/-/xterm-6.1.0-beta.315.tgz",
            "archiveBytes": len(self.source_archive),
            "archiveSha256": digest(self.source_archive),
            "integrity": integrity(self.source_archive),
            "packageJsonSha256": digest(SOURCE_JSON),
            "licensePath": "package/LICENSE",
            "licenseOutputPath": "terminal/notices/xterm-LICENSE.txt",
            "licenseBytes": len(LICENSE),
            "licenseSha256": digest(LICENSE),
        }

        self.compiler_entries = [
            ("package/bin/esbuild", COMPILER_BINARY, tarfile.REGTYPE),
            ("package/package.json", COMPILER_JSON, tarfile.REGTYPE),
            ("package/README.md", b"fixture readme\n", tarfile.REGTYPE),
        ]
        self.compiler_archive = make_archive(self.compiler_entries)
        (self.cache / "source-build/linux-x64-0.28.0.tgz").write_bytes(self.compiler_archive)
        self.compiler_record = {
            "name": "@esbuild/linux-x64",
            "version": "0.28.0",
            "cacheFile": "source-build/linux-x64-0.28.0.tgz",
            "archiveUrl": "https://registry.npmjs.org/@esbuild/linux-x64/-/linux-x64-0.28.0.tgz",
            "archiveBytes": len(self.compiler_archive),
            "archiveSha256": digest(self.compiler_archive),
            "integrity": integrity(self.compiler_archive),
            "binaryMember": "package/bin/esbuild",
            "binaryBytes": len(COMPILER_BINARY),
            "binarySha256": digest(COMPILER_BINARY),
        }

    def manifest(self, output=None):
        manifest = {
            "schemaVersion": 1,
            "source": copy.deepcopy(self.source_record),
            "patch": {
                "file": "tools/patches/test.patch",
                "sha256": digest(PATCH.encode()),
                "files": [COMPOSITION_PATH],
            },
            "compiler": copy.deepcopy(self.compiler_record),
            "build": {
                "entry": BUILD.ENTRY_SOURCE,
                "entrySha256": BUILD.ENTRY_SHA256,
                "target": "es2020",
                "format": "iife",
                "platform": "browser",
            },
        }
        if output is not None:
            manifest["output"] = output
        return manifest

    def reviewed_manifest(self, bundle):
        return self.manifest({
            "path": "terminal/vendor/xterm.js",
            "bytes": len(bundle),
            "sha256": digest(bundle),
        })

    def test_reviewed_output_mismatch_fails_closed(self):
        manifest = self.reviewed_manifest(b"reviewed bundle\n")
        with patch.object(BUILD.subprocess, "run", side_effect=CompilerStub(b"different bundle\n")), \
                self.assertRaisesRegex(BUILD.BuildError, "Byte/hash mismatch"):
            BUILD.build_bundle(manifest, self.cache, self.repository)
        self.assertFalse((self.cache / BUILD.CANDIDATE_PATH).exists())

    def test_normal_build_requires_a_reviewed_output_pin(self):
        with self.assertRaisesRegex(BUILD.BuildError, "output pin"):
            BUILD.build_bundle(self.manifest(), self.cache, self.repository)
        with self.assertRaisesRegex(BUILD.BuildError, "output pin"):
            BUILD.verify_inputs(self.manifest(), self.cache, self.repository)

    def test_missing_offline_compiler_is_not_downloaded(self):
        target = self.cache / self.compiler_record["cacheFile"]
        target.unlink()
        with patch.object(BUILD.ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")), \
                self.assertRaises(BUILD.BuildError):
            BUILD.fetch_compiler(self.manifest(), self.cache, offline=True)
        self.assertFalse(target.exists())

    def test_unverified_download_never_enters_compiler_cache(self):
        target = self.cache / self.compiler_record["cacheFile"]
        target.unlink()
        with patch.object(BUILD.ASSETS, "fetch_archive", return_value=b"untrusted download"), \
                self.assertRaises(BUILD.BuildError):
            BUILD.fetch_compiler(self.manifest(), self.cache)
        self.assertFalse(target.exists())

    def test_corrupt_cached_compiler_is_not_silently_replaced(self):
        target = self.cache / self.compiler_record["cacheFile"]
        target.write_bytes(b"corrupt existing compiler")
        with patch.object(BUILD.ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")), \
                self.assertRaises(BUILD.BuildError):
            BUILD.fetch_compiler(self.manifest(), self.cache)
        self.assertEqual(target.read_bytes(), b"corrupt existing compiler")

    def test_check_verifies_inputs_and_bundle_without_compiling_or_writing(self):
        bundle = b"reviewed product bundle\n"
        manifest = self.reviewed_manifest(bundle)
        destination = self.product / "terminal/vendor/xterm.js"
        destination.parent.mkdir(parents=True)
        destination.write_bytes(bundle)
        before = {path: (path.read_bytes(), path.stat().st_mtime_ns)
                  for path in self.root.rglob("*") if path.is_file()}
        with patch.object(BUILD.subprocess, "run", side_effect=AssertionError("compiler forbidden")), \
                patch.object(BUILD.RDP, "replay_patch", side_effect=AssertionError("replay forbidden")), \
                patch.object(BUILD, "atomic_write", side_effect=AssertionError("write forbidden")):
            result = BUILD.check_bundle(manifest, self.cache, self.repository, self.product)
        self.assertEqual(before, {path: (path.read_bytes(), path.stat().st_mtime_ns)
                                  for path in self.root.rglob("*") if path.is_file()})
        self.assertEqual(result["sha256"], digest(bundle))
        self.assertEqual(result["bytes"], len(bundle))

    def test_patch_scope_accepts_reviewed_headers_and_rejects_deviations(self):
        allowed = {COMPOSITION_PATH}
        BUILD.validate_patch_scope(PATCH.encode(), allowed)

        with self.assertRaisesRegex(BUILD.BuildError, "unapproved file"):
            BUILD.validate_patch_scope(PATCH.encode(), set())

        traversal = PATCH.replace("package/src/browser/input/CompositionHelper.ts",
                                  "package/../outside.ts")
        with self.assertRaises(BUILD.BuildError):
            BUILD.validate_patch_scope(traversal.encode(), allowed)

        timestamp = PATCH.replace("+++ b/package/src/browser/input/CompositionHelper.ts",
                                  "+++ b/package/src/browser/input/CompositionHelper.ts\t2026-10-17")
        with self.assertRaisesRegex(BUILD.BuildError, "timestamps"):
            BUILD.validate_patch_scope(timestamp.encode(), allowed)

        duplicate = PATCH + PATCH
        with self.assertRaisesRegex(BUILD.BuildError, "Duplicate renderer patch file"):
            BUILD.validate_patch_scope(duplicate.encode(), allowed)

        rename = PATCH.replace("+++ b/package/src/browser/input/CompositionHelper.ts",
                               "+++ b/package/src/browser/input/Other.ts")
        with self.assertRaisesRegex(BUILD.BuildError, "renames"):
            BUILD.validate_patch_scope(rename.encode(), allowed)

        with self.assertRaisesRegex(BUILD.BuildError, "file set differs"):
            BUILD.validate_patch_scope(PATCH.encode(), allowed | {"package/src/browser/Other.ts"})

    def test_source_archive_rejects_unsafe_duplicate_or_inconsistent_members(self):
        cases = [
            ([("package/src/link.ts", b"", tarfile.SYMTYPE)], "Links/special"),
            ([("package/src/link.ts", b"", tarfile.LNKTYPE)], "Links/special"),
            ([("../outside", b"bad", tarfile.REGTYPE)], "outside package"),
            ([("package/src/browser/input/CompositionHelper.ts", b"duplicate", tarfile.REGTYPE)],
             "Duplicate archive member"),
        ]
        for extra, reason in cases:
            with self.subTest(reason=reason):
                archive = make_archive(self.source_entries + extra)
                with self.assertRaisesRegex(BUILD.BuildError, reason):
                    BUILD.inspect_source_archive(self.manifest(), archive)

        missing_terminal = make_archive([entry for entry in self.source_entries
                                         if entry[0] != TERMINAL_PATH])
        with self.assertRaisesRegex(BUILD.BuildError, "public Terminal source"):
            BUILD.inspect_source_archive(self.manifest(), missing_terminal)

        manifest = self.manifest()
        manifest["source"]["packageJsonSha256"] = "0" * 64
        with self.assertRaisesRegex(BUILD.BuildError, "Package metadata hash mismatch"):
            BUILD.inspect_source_archive(manifest, self.source_archive)

        manifest = self.manifest()
        manifest["source"]["licenseSha256"] = "0" * 64
        with self.assertRaisesRegex(BUILD.BuildError, "Byte/hash mismatch: renderer license"):
            BUILD.inspect_source_archive(manifest, self.source_archive)

    def test_source_and_compiler_archive_bounds_are_enforced(self):
        for constant, limit, reason in (
            ("MAX_EXPANDED_BYTES", 512, "decompression"),
            ("MAX_MEMBER_BYTES", 16, "member exceeds"),
            ("MAX_MEMBERS", 1, "Too many"),
            ("MAX_SOURCE_BYTES", 16, "Source bytes"),
        ):
            with self.subTest(constant=constant), patch.object(BUILD, constant, limit), \
                    self.assertRaisesRegex(BUILD.BuildError, reason):
                BUILD.inspect_source_archive(self.manifest(), self.source_archive)
        for constant, limit, reason in (
            ("MAX_COMPILER_EXPANDED_BYTES", 512, "decompression"),
            ("MAX_COMPILER_MEMBER_BYTES", 4, "member exceeds"),
        ):
            with self.subTest(constant=constant), patch.object(BUILD, constant, limit), \
                    self.assertRaisesRegex(BUILD.BuildError, reason):
                BUILD.inspect_compiler_archive(self.manifest(), self.compiler_archive)

    def test_patch_and_archive_hash_mismatches_fail_before_compile(self):
        manifest = self.manifest()
        manifest["patch"]["sha256"] = "0" * 64
        with patch.object(BUILD.subprocess, "run", side_effect=AssertionError("compile forbidden")), \
                self.assertRaisesRegex(BUILD.BuildError, "patch hash"):
            BUILD.load_inputs(manifest, self.cache, self.repository)

        manifest = self.manifest()
        manifest["source"]["archiveSha256"] = "0" * 64
        with self.assertRaisesRegex(BUILD.BuildError, "Byte/hash mismatch: renderer source archive"):
            BUILD.load_inputs(manifest, self.cache, self.repository)

        manifest = self.manifest()
        manifest["compiler"]["archiveSha256"] = "0" * 64
        with self.assertRaisesRegex(BUILD.BuildError, "Byte/hash mismatch: renderer compiler archive"):
            BUILD.load_inputs(manifest, self.cache, self.repository)

    def test_compiler_archive_rejects_unexpected_members_and_wrong_binary(self):
        extra = make_archive(self.compiler_entries + [("package/extra", b"x", tarfile.REGTYPE)])
        with self.assertRaisesRegex(BUILD.BuildError, "Unexpected compiler archive member"):
            BUILD.inspect_compiler_archive(self.manifest(), extra)

        link = make_archive(self.compiler_entries + [("package/bin/link", b"", tarfile.SYMTYPE)])
        with self.assertRaisesRegex(BUILD.BuildError, "Unexpected compiler archive member"):
            BUILD.inspect_compiler_archive(self.manifest(), link)

        manifest = self.manifest()
        manifest["compiler"]["binarySha256"] = "0" * 64
        with self.assertRaisesRegex(BUILD.BuildError, "Byte/hash mismatch: renderer compiler binary"):
            BUILD.inspect_compiler_archive(manifest, self.compiler_archive)

        wrong_metadata = make_archive([
            ("package/bin/esbuild", COMPILER_BINARY, tarfile.REGTYPE),
            ("package/package.json", json.dumps({"name": "@esbuild/linux-x64", "version": "0.27.0"}).encode(),
             tarfile.REGTYPE),
            ("package/README.md", b"fixture readme\n", tarfile.REGTYPE),
        ])
        with self.assertRaisesRegex(BUILD.BuildError, "Compiler package metadata differs"):
            BUILD.inspect_compiler_archive(self.manifest(), wrong_metadata)

    def test_manifest_validation_rejects_unknown_fields_and_changed_pins(self):
        mutations = [
            (lambda manifest: manifest.update(extra=True), "Unapproved"),
            (lambda manifest: manifest["source"].update(version="6.1.0"), "source pin changed"),
            (lambda manifest: manifest["compiler"].update(version="0.27.0"), "compiler pin changed"),
            (lambda manifest: manifest["build"].update(entrySha256="0" * 64), "entry source changed"),
            (lambda manifest: manifest["patch"].update(file="tools/other.patch"), "outside tools/patches"),
            (lambda manifest: manifest["source"].update(archiveBytes=True), "size is unbounded"),
            (lambda manifest: manifest.update(output={"path": "terminal/vendor/other.js",
                                                      "bytes": 1, "sha256": "0" * 64}),
             "output path changed"),
        ]
        for mutate, reason in mutations:
            with self.subTest(reason=reason):
                manifest = self.manifest()
                mutate(manifest)
                with self.assertRaisesRegex(BUILD.BuildError, reason):
                    BUILD.validate_manifest(manifest)


if __name__ == "__main__":
    unittest.main()
