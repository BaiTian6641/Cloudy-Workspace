"""Real temporary archives exercise asset policy; no network or Android claims."""

import base64
import copy
import gzip
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("prepare_terminal_assets", ROOT / "tools/prepare_terminal_assets.py")
ASSETS = importlib.util.module_from_spec(SPEC)
# The renderer build module imports these reviewed helpers by module name.
sys.modules["prepare_terminal_assets"] = ASSETS
SPEC.loader.exec_module(ASSETS)


def digest(data):
    return hashlib.sha256(data).hexdigest()


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


class TerminalAssetTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="cloudy-terminal-assets-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.cache = self.root / "cache"
        self.output = self.root / "generated"
        self.cache.mkdir()
        self.manifest = {"schemaVersion": 1, "packages": [], "additionalNotices": []}
        self.entries = {}
        self.expected = {
            "terminal/vendor/xterm.js": b"window.Terminal = 'fixture';\n\x00\xff",
            "terminal/vendor/xterm.css": b".xterm { color: white; }\r\n",
            "terminal/vendor/addon-fit.js": b"window.FitAddon = 'fixture';\n",
            "terminal/notices/xterm-LICENSE.txt": b"Core test license\r\n",
            "terminal/notices/addon-fit-LICENSE.txt": b"Fit test license\n",
        }
        # The small archives use production package identities and paths, with
        # independent content/digests. They never stand in for an upstream audit.
        selections = [
            ("xterm", "6.1.0-beta.315", [
                ("package/lib/xterm.js", "terminal/vendor/xterm.js"),
                ("package/css/xterm.css", "terminal/vendor/xterm.css"),
                ("package/LICENSE", "terminal/notices/xterm-LICENSE.txt"),
            ]),
            ("addon-fit", "0.12.0-beta.311", [
                ("package/lib/addon-fit.js", "terminal/vendor/addon-fit.js"),
                ("package/LICENSE", "terminal/notices/addon-fit-LICENSE.txt"),
            ]),
        ]
        for name, version, files in selections:
            metadata = json.dumps({"name": f"@xterm/{name}", "version": version, "license": "MIT"}).encode()
            entries = [("package/package.json", metadata, tarfile.REGTYPE)]
            package = {
                "name": f"@xterm/{name}", "version": version, "license": "MIT",
                "cacheFile": f"{name}-{version}.tgz",
                "archiveUrl": f"https://registry.npmjs.org/@xterm/{name}/-/{name}-{version}.tgz",
                "packageJsonSha256": digest(metadata), "files": [],
            }
            for source, destination in files:
                body = self.expected[destination]
                entries.append((source, body, tarfile.REGTYPE))
                package["files"].append({"archivePath": source, "outputPath": destination,
                                         "bytes": len(body), "sha256": digest(body)})
            entries.append(("package/src/not-selected.js", b"throw 'never execute package content';", tarfile.REGTYPE))
            self.manifest["packages"].append(package)
            self.entries[name] = entries
            self.replace_archive(name, entries)

    def package(self, name="xterm"):
        return next(p for p in self.manifest["packages"] if p["name"] == f"@xterm/{name}")

    def replace_archive(self, name, entries):
        package = self.package(name)
        archive = make_archive(entries)
        package.update({
            "archiveBytes": len(archive), "archiveSha256": digest(archive),
            "integrity": "sha512-" + base64.b64encode(hashlib.sha512(archive).digest()).decode(),
            "archiveMemberCount": len(entries), "archiveFileBytes": sum(len(body) for _, body, _ in entries),
        })
        (self.cache / package["cacheFile"]).write_bytes(archive)

    def prepare(self, **options):
        return ASSETS.prepare_assets(self.manifest, self.cache, self.output, repository=self.root, **options)

    def renderer_build(self):
        return ASSETS._renderer_build()

    def renderer_manifest(self, bundle=b"reviewed source-built bundle\n"):
        zero = "0" * 64
        empty_integrity = "sha512-" + base64.b64encode(bytes(64)).decode()
        build = self.renderer_build()
        return {
            "schemaVersion": 1,
            "source": {
                "name": "@xterm/xterm",
                "version": build.SOURCE_VERSION,
                "upstreamCommit": build.SOURCE_COMMIT,
                "license": "MIT",
                "cacheFile": "xterm-6.1.0-beta.315.tgz",
                "archiveUrl": "https://registry.npmjs.org/@xterm/xterm/-/xterm-6.1.0-beta.315.tgz",
                "archiveBytes": 1,
                "archiveSha256": zero,
                "integrity": empty_integrity,
                "packageJsonSha256": zero,
                "licensePath": "package/LICENSE",
                "licenseOutputPath": "terminal/notices/xterm-LICENSE.txt",
                "licenseBytes": 1,
                "licenseSha256": zero,
            },
            "patch": {
                "file": "tools/patches/test.patch",
                "sha256": zero,
                "files": ["package/src/browser/input/CompositionHelper.ts"],
            },
            "compiler": {
                "name": "@esbuild/linux-x64",
                "version": "0.28.0",
                "cacheFile": "source-build/linux-x64-0.28.0.tgz",
                "archiveUrl": "https://registry.npmjs.org/@esbuild/linux-x64/-/linux-x64-0.28.0.tgz",
                "archiveBytes": 1,
                "archiveSha256": zero,
                "integrity": empty_integrity,
                "binaryMember": "package/bin/esbuild",
                "binaryBytes": 1,
                "binarySha256": zero,
            },
            "build": {
                "entry": build.ENTRY_SOURCE,
                "entrySha256": build.ENTRY_SHA256,
                "target": "es2020",
                "format": "iife",
                "platform": "browser",
            },
            "output": {
                "path": "terminal/vendor/xterm.js",
                "bytes": len(bundle),
                "sha256": digest(bundle),
            },
        }

    def test_exact_bytes_and_only_allowlisted_members_are_generated(self):
        with patch.object(ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")):
            result = self.prepare(offline=True)
        expected_paths = set(self.expected) | {"terminal/notices/provenance.json"}
        self.assertEqual(set(result), expected_paths)
        self.assertEqual({p.relative_to(self.output).as_posix() for p in self.output.rglob("*") if p.is_file()}, expected_paths)
        for name, body in self.expected.items():
            self.assertEqual((self.output / name).read_bytes(), body)
        self.assertEqual(json.loads((self.output / "terminal/notices/provenance.json").read_bytes()), self.manifest)

    def test_check_is_offline_and_does_not_rewrite_any_file(self):
        self.prepare(offline=True)
        paths = [p for p in self.root.rglob("*") if p.is_file()]
        before = {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in paths}
        with patch.object(ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")), \
                patch.object(ASSETS, "atomic_write", side_effect=AssertionError("write forbidden")):
            self.prepare(check=True)
        self.assertEqual(before, {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in paths})

    def test_missing_archive_cannot_be_fetched_in_check_or_offline_mode(self):
        (self.cache / self.package()["cacheFile"]).unlink()
        with patch.object(ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")):
            for mode in ("check", "offline"):
                with self.subTest(mode=mode), self.assertRaisesRegex(ASSETS.AssetError, "missing"):
                    self.prepare(**{mode: True})
        self.assertFalse(self.output.exists())

    def test_check_requires_outputs_even_with_valid_cache(self):
        with self.assertRaises(FileNotFoundError):
            self.prepare(check=True)
        self.assertFalse(self.output.exists())

    def test_cache_hash_mismatch_is_not_automatically_refetched_or_replaced(self):
        path = self.cache / self.package()["cacheFile"]
        corrupt = b"wrong archive"
        path.write_bytes(corrupt)
        with patch.object(ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")), \
                self.assertRaisesRegex(ASSETS.AssetError, "Byte/hash mismatch"):
            self.prepare()
        self.assertEqual(path.read_bytes(), corrupt)
        self.assertFalse(self.output.exists())

    def test_published_sha512_is_checked_independently_of_sha256(self):
        self.package()["integrity"] = "sha512-" + base64.b64encode(bytes(64)).decode()
        with self.assertRaisesRegex(ASSETS.AssetError, "SHA-512 mismatch"):
            self.prepare(offline=True)

    def test_mismatched_download_is_never_committed_to_cache(self):
        path = self.cache / self.package()["cacheFile"]
        path.unlink()
        with patch.object(ASSETS, "fetch_archive", return_value=b"untrusted error body"), \
                self.assertRaises(ASSETS.AssetError):
            self.prepare()
        self.assertFalse(path.exists())
        self.assertFalse(self.output.exists())

    def test_asset_hash_mismatch_fails_before_any_output_is_written(self):
        self.package("addon-fit")["files"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ASSETS.AssetError, "Byte/hash mismatch"):
            self.prepare(offline=True)
        self.assertFalse(self.output.exists())

    def test_unsafe_member_paths_fail_even_when_archive_hash_is_valid(self):
        for name in ("../outside", "/tmp/outside", "package/../../outside", "package/./x", "package//x", "package\\x"):
            with self.subTest(name=name):
                self.replace_archive("xterm", self.entries["xterm"] + [(name, b"bad", tarfile.REGTYPE)])
                with self.assertRaises(ASSETS.AssetError):
                    self.prepare(offline=True)
                self.assertFalse(self.output.exists())
                self.assertFalse((self.root / "outside").exists())

    def test_links_and_special_entries_are_rejected_even_if_not_selected(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.FIFOTYPE, tarfile.CHRTYPE):
            with self.subTest(kind=kind):
                self.replace_archive("xterm", self.entries["xterm"] + [("package/extra", b"", kind)])
                with self.assertRaisesRegex(ASSETS.AssetError, "Links/special"):
                    self.prepare(offline=True)
        self.assertFalse(self.output.exists())

    def test_duplicate_member_cannot_shadow_a_selected_file(self):
        entries = self.entries["xterm"] + [("package/lib/xterm.js", b"replacement", tarfile.REGTYPE)]
        self.replace_archive("xterm", entries)
        with self.assertRaisesRegex(ASSETS.AssetError, "Duplicate archive"):
            self.prepare(offline=True)

    def test_missing_selected_member_fails_without_extracting_other_files(self):
        self.replace_archive("xterm", [entry for entry in self.entries["xterm"] if entry[0] != "package/LICENSE"])
        with self.assertRaisesRegex(ASSETS.AssetError, "Selected asset missing"):
            self.prepare(offline=True)
        self.assertFalse(self.output.exists())

    def test_unapproved_output_destination_is_not_a_write_primitive(self):
        self.package()["files"][0]["outputPath"] = "../../outside"
        with self.assertRaisesRegex(ASSETS.AssetError, "Unapproved asset destination"):
            self.prepare(offline=True)
        self.assertFalse((self.root / "outside").exists())

    def test_expansion_and_member_and_count_limits_are_enforced(self):
        archive = (self.cache / self.package()["cacheFile"]).read_bytes()
        for constant, limit, reason in (("MAX_EXPANDED_BYTES", 512, "decompression"),
                                       ("MAX_MEMBER_BYTES", 16, "member exceeds"),
                                       ("MAX_MEMBERS", 1, "Too many")):
            with self.subTest(constant=constant), patch.object(ASSETS, constant, limit), \
                    self.assertRaisesRegex(ASSETS.AssetError, reason):
                ASSETS.extract_assets(self.package(), archive)

    def test_declared_archive_bounds_cannot_be_booleans_or_unbounded(self):
        for value in (True, -1, 0, ASSETS.MAX_ARCHIVE_BYTES + 1, "100"):
            manifest = copy.deepcopy(self.manifest)
            manifest["packages"][0]["archiveBytes"] = value
            with self.subTest(value=value), self.assertRaisesRegex(ASSETS.AssetError, "Archive size"):
                ASSETS.validate_manifest(manifest)

    def test_package_metadata_cannot_introduce_runtime_dependencies(self):
        metadata = json.dumps({"name": "@xterm/xterm", "version": "6.1.0-beta.315", "license": "MIT",
                               "dependencies": {"unreviewed": "*"}}).encode()
        entries = [(name, metadata if name == "package/package.json" else body, kind)
                   for name, body, kind in self.entries["xterm"]]
        self.package()["packageJsonSha256"] = digest(metadata)
        self.replace_archive("xterm", entries)
        with self.assertRaisesRegex(ASSETS.AssetError, "Package metadata differs"):
            self.prepare(offline=True)

    def test_duplicate_json_fields_and_nonfinite_values_fail(self):
        for data in (b'{"schemaVersion": 1, "schemaVersion": 2}', b'{"value": NaN}'):
            with self.subTest(data=data), self.assertRaises(ASSETS.AssetError):
                ASSETS.parse_json(data)

    def test_unexpected_output_is_preserved_and_blocks_packaging(self):
        self.prepare(offline=True)
        extra = self.output / "unrelated.txt"
        extra.write_bytes(b"preserve this file")
        for mode in ("check", "offline"):
            with self.subTest(mode=mode), self.assertRaisesRegex(ASSETS.AssetError, "Unexpected generated"):
                self.prepare(**{mode: True})
        self.assertEqual(extra.read_bytes(), b"preserve this file")

    def test_output_symlink_never_redirects_a_repair_write(self):
        self.prepare(offline=True)
        outside = self.root / "outside"
        outside.write_bytes(b"must survive")
        selected = self.output / "terminal/vendor/xterm.js"
        selected.unlink()
        selected.symlink_to(outside)
        with self.assertRaisesRegex(ASSETS.AssetError, "symlink"):
            self.prepare(offline=True)
        self.assertEqual(outside.read_bytes(), b"must survive")

    def test_unreadable_output_directory_cannot_hide_unreviewed_assets(self):
        self.prepare(offline=True)
        real_scandir = os.scandir

        def denied_scandir(path):
            if Path(path) == self.output / "terminal/vendor":
                raise PermissionError("test directory cannot be enumerated")
            return real_scandir(path)

        with patch.object(ASSETS.os, "scandir", side_effect=denied_scandir), self.assertRaises(PermissionError):
            self.prepare(check=True)

    def test_cache_fifo_and_symlink_are_rejected_without_blocking(self):
        path = self.cache / self.package()["cacheFile"]
        path.unlink()
        os.mkfifo(path)
        with self.assertRaisesRegex(ASSETS.AssetError, "regular file"):
            self.prepare(offline=True)
        path.unlink()
        path.symlink_to(self.cache / self.package("addon-fit")["cacheFile"])
        with self.assertRaises(OSError):
            self.prepare(offline=True)

    def test_check_detects_corruption_and_explicit_preparation_can_repair_it(self):
        self.prepare(offline=True)
        selected = self.output / "terminal/vendor/xterm.js"
        selected.write_bytes(b"corrupt")
        with self.assertRaisesRegex(ASSETS.AssetError, "Generated asset mismatch"):
            self.prepare(check=True)
        self.assertEqual(selected.read_bytes(), b"corrupt")
        self.prepare(offline=True)
        self.assertEqual(selected.read_bytes(), self.expected["terminal/vendor/xterm.js"])
        self.prepare(check=True)

    def test_cli_guard_rejects_outside_and_symlinked_roots(self):
        allowed = self.root / "allowed"
        allowed.mkdir()
        self.assertEqual(ASSETS.cli_path(allowed / "child", allowed), allowed / "child")
        with self.assertRaisesRegex(ASSETS.AssetError, "outside"):
            ASSETS.cli_path(self.root / "other", allowed)
        (allowed / "redirect").symlink_to(self.cache, target_is_directory=True)
        with self.assertRaisesRegex(ASSETS.AssetError, "Symlink"):
            ASSETS.cli_path(allowed / "redirect" / "child", allowed)

    def test_product_check_does_not_compile_or_write(self):
        bundle = b"reviewed source-built bundle\n"
        renderer = self.renderer_manifest(bundle)
        renderer_build = self.renderer_build()
        with patch.object(renderer_build, "build_bundle", return_value=bundle):
            self.prepare(offline=True, product=True, renderer_manifest=renderer)
        before = {path: (path.read_bytes(), path.stat().st_mtime_ns)
                  for path in self.root.rglob("*") if path.is_file()}
        with patch.object(renderer_build, "build_bundle",
                          side_effect=AssertionError("compile forbidden")), \
                patch.object(renderer_build, "verify_inputs"), \
                patch.object(ASSETS, "atomic_write", side_effect=AssertionError("write forbidden")):
            self.prepare(check=True, product=True, renderer_manifest=renderer)
        self.assertEqual(before, {path: (path.read_bytes(), path.stat().st_mtime_ns)
                                  for path in self.root.rglob("*") if path.is_file()})

    def test_product_check_rejects_missing_or_wrong_generated_bundle(self):
        bundle = b"reviewed source-built bundle\n"
        renderer = self.renderer_manifest(bundle)
        renderer_build = self.renderer_build()
        with patch.object(renderer_build, "verify_inputs"), \
                self.assertRaises(FileNotFoundError):
            self.prepare(check=True, product=True, renderer_manifest=renderer)
        selected = self.output / "terminal/vendor/xterm.js"
        selected.parent.mkdir(parents=True)
        selected.write_bytes(b"wrong bundle\n")
        with patch.object(renderer_build, "verify_inputs"), \
                self.assertRaisesRegex(renderer_build.BuildError, "Byte/hash mismatch"):
            self.prepare(check=True, product=True, renderer_manifest=renderer)
        self.assertEqual(selected.read_bytes(), b"wrong bundle\n")

    def test_product_mode_never_fetches_a_missing_archive(self):
        (self.cache / self.package("addon-fit")["cacheFile"]).unlink()
        renderer = self.renderer_manifest()
        with patch.object(self.renderer_build(), "build_bundle", return_value=b"bundle\n"), \
                patch.object(ASSETS, "fetch_archive", side_effect=AssertionError("network forbidden")), \
                self.assertRaisesRegex(ASSETS.AssetError, "missing"):
            self.prepare(product=True, renderer_manifest=renderer)
        self.assertFalse(self.output.exists())

    def test_supplementary_notice_is_integrity_checked_before_generation(self):
        asset = "third_party_notices/terminal/test-MIT.txt"
        relative = "qualification/probes/device-baseline/app/src/main/assets/" + asset
        body = b"Actual temporary notice bytes\n"
        path = self.root / relative
        path.parent.mkdir(parents=True)
        path.write_bytes(body)
        self.manifest["additionalNotices"] = [{"sourceFile": relative, "assetPath": asset,
                                               "bytes": len(body), "sha256": digest(body)}]
        self.prepare(offline=True)
        path.write_bytes(b"changed")
        with self.assertRaisesRegex(ASSETS.AssetError, "Byte/hash mismatch"):
            self.prepare(check=True)

    def test_redirect_is_rejected_without_following_other_authority(self):
        with self.assertRaisesRegex(ASSETS.AssetError, "redirect refused"):
            ASSETS.NoRedirect().redirect_request(None, None, 302, "Found", {}, "https://other.invalid/archive")


if __name__ == "__main__":
    unittest.main()
