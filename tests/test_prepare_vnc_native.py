"""Own helper trust/path guards only; these fixtures are not native qualification."""

import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch


TOOLS = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(TOOLS))
SPEC = importlib.util.spec_from_file_location("prepare_vnc_native", TOOLS / "prepare_vnc_native.py")
native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(native)


def pin(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def archive(entries):
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode="w:gz") as bundle:
        for name, kind, content in entries:
            member = tarfile.TarInfo(name)
            member.type = kind
            if kind == tarfile.REGTYPE:
                member.size = len(content)
                bundle.addfile(member, io.BytesIO(content))
            else:
                member.linkname = content.decode()
                bundle.addfile(member)
    return stream.getvalue()


class ArchiveBoundaryTests(unittest.TestCase):
    def extract(self, entries):
        raw = archive(entries)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        destination = Path(directory.name) / "source"
        native.extract(raw, pin(raw) | {"root": "source"}, destination)
        return destination

    def test_valid_archive_preserves_actual_bytes(self):
        source = self.extract([("source/include/a.h", tarfile.REGTYPE, b"header\n")])
        self.assertEqual((source / "include/a.h").read_bytes(), b"header\n")

    def test_traversal_absolute_and_wrong_root_are_rejected(self):
        for name in ("source/../escape", "/escape", "other/file"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.extract([(name, tarfile.REGTYPE, b"payload")])

    def test_links_and_duplicate_members_are_rejected(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.FIFOTYPE):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                self.extract([("source/link", kind, b"/outside")])
        with self.assertRaises(ValueError):
            self.extract([("source/file", tarfile.REGTYPE, b"a"),
                          ("source/file", tarfile.REGTYPE, b"b")])

    def test_hash_mismatch_fails_before_output_creation(self):
        raw = archive([("source/file", tarfile.REGTYPE, b"a")])
        with tempfile.TemporaryDirectory() as temporary:
            destination = Path(temporary) / "source"
            with self.assertRaises(ValueError):
                native.extract(raw + b"changed", pin(raw) | {"root": "source"}, destination)
            self.assertFalse(destination.exists())

    def test_existing_output_and_symlink_path_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "actual").mkdir()
            (root / "alias").symlink_to(root / "actual", target_is_directory=True)
            with self.assertRaises(ValueError):
                native.read(root / "alias/file")
            raw = archive([("source/file", tarfile.REGTYPE, b"a")])
            with self.assertRaises(FileExistsError):
                native.extract(raw, pin(raw) | {"root": "source"}, root / "actual")


class ImportBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.files = {"lib/libssl.so": b"unit-ssl", "lib/libcrypto.so": b"unit-crypto",
                      "include/openssl/ssl.h": b"unit-header"}
        self.receipt = {"exitCode": 0, "libraries": {
            name: pin(self.files[f"lib/{name}"])["sha256"] for name in ("libssl.so", "libcrypto.so")}}
        self.raw = json.dumps(self.receipt).encode()
        self.lock = {"openssl": {"receipt": pin(self.raw), "libraries": {
            name: pin(self.files[f"lib/{name}"]) for name in self.receipt["libraries"]},
            "headers": {"openssl/ssl.h": pin(self.files["include/openssl/ssl.h"])}}}

    def test_exact_import_passes_and_changed_or_missing_bytes_fail(self):
        native.validate_import(self.lock, self.raw, self.files)
        for changed in (self.files | {"lib/libssl.so": b"changed"},
                        {key: value for key, value in self.files.items() if key != "include/openssl/ssl.h"}):
            with self.subTest(files=list(changed)), self.assertRaises(ValueError):
                native.validate_import(self.lock, self.raw, changed)

    def test_failed_receipt_or_library_mismatch_is_not_accepted(self):
        for receipt in (self.receipt | {"exitCode": 1},
                        self.receipt | {"libraries": {"libssl.so": "wrong", "libcrypto.so": "wrong"}}):
            raw = json.dumps(receipt).encode()
            self.lock["openssl"]["receipt"] = pin(raw)
            with self.assertRaises(ValueError):
                native.validate_import(self.lock, raw, self.files)

    def test_authored_input_change_prevents_publication(self):
        with patch.object(native, "snapshot", return_value=({}, {"tool": "new"}, {})):
            with self.assertRaises(ValueError):
                native.unchanged({"tool": "old"})


class CompileContractTests(unittest.TestCase):
    def test_generated_spacing_is_accepted_but_tls_none_or_missing_jpeg_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            build = Path(temporary)
            header = build / "include/rfb/rfbconfig.h"
            header.parent.mkdir(parents=True)
            config = "\n".join(f"#define {name}  1 " for name in (
                "LIBVNCSERVER_HAVE_LIBSSL", "LIBVNCSERVER_HAVE_LIBZ", "LIBVNCSERVER_HAVE_LIBJPEG"))
            header.write_text(config + "\n")
            commands = [{"file": name} for name in (
                "tls_openssl.c", "crypto_openssl.c", "minilzo.c", "rfbclient.c")]
            native.save(build / "compile_commands.json", commands)
            native.compile_contract(build)
            native.save(build / "compile_commands.json", commands + [{"file": "tls_none.c"}])
            with self.assertRaises(ValueError):
                native.compile_contract(build)
            native.save(build / "compile_commands.json", commands)
            header.write_text(config.replace("#define LIBVNCSERVER_HAVE_LIBJPEG  1", "#undef LIBVNCSERVER_HAVE_LIBJPEG"))
            with self.assertRaises(ValueError):
                native.compile_contract(build)


class OfflineBoundaryTests(unittest.TestCase):
    def test_changed_prepared_bytes_fail_before_sdk_or_archive_access(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            prepared = run / "prepared"
            prepared.mkdir()
            (prepared / "notice").write_bytes(b"reviewed")
            receipt = {"exitCode": 0, "inputs": {"tool": "digest"}, "buildRef": "unit",
                       "prepared": native.hashes(prepared)}
            (prepared / "notice").write_bytes(b"tampered")
            with patch.object(native, "snapshot", return_value=({"buildRef": "unit"}, {"tool": "digest"}, {})):
                with self.assertRaisesRegex(ValueError, "Prepared output differs"):
                    native.check(run, receipt)


if __name__ == "__main__":
    unittest.main()
