"""Owned import-boundary regressions; these do not simulate a VNC session."""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1] / 'tools'
sys.path.insert(0, str(TOOLS))
SPEC = importlib.util.spec_from_file_location('vnc_android', TOOLS / 'prepare_vnc_android.py')
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class PackagingTests(unittest.TestCase):
    def closure(self):
        payload = {name: name.encode() for name in module.LIBRARIES}
        pins = {name: module.record(raw) for name, raw in payload.items()}
        return payload, {'nativeLibraries': pins}, {'libraries': pins}

    def test_exact_closure_and_changed_input(self):
        payload, lock, receipt = self.closure()
        module.validate_libraries(lock, receipt, payload)
        payload['libssl.so'] += b'changed'
        with self.assertRaisesRegex(ValueError, 'changed'):
            module.validate_libraries(lock, receipt, payload)

    def test_missing_and_original_library_rejected(self):
        payload, lock, receipt = self.closure()
        del payload['libcloudy-vnc-jni.so']
        with self.assertRaisesRegex(ValueError, 'closure'):
            module.validate_libraries(lock, receipt, payload)
        payload['libvncclient.so'] = b'original'
        with self.assertRaisesRegex(ValueError, 'closure'):
            module.validate_libraries(lock, receipt, payload)

    def test_extra_compiler_source_and_generated_payload_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            (stage / 'build.gradle').write_bytes(b'reviewed')
            expected = {'build.gradle': module.record(b'reviewed')}
            module.verify_tree(stage, expected)
            output = stage / 'app/build/intermediates/result.txt'
            output.parent.mkdir(parents=True)
            output.write_text('normal Gradle output')
            module.verify_tree(stage, expected)
            extra = stage / (module.GENERATED + 'java/Shadow.java')
            extra.parent.mkdir(parents=True)
            extra.write_text('unreviewed')
            with self.assertRaisesRegex(ValueError, 'Unreviewed'):
                module.verify_tree(stage, expected)
            extra.unlink()
            extra = stage / 'app/src/main/java/Shadow.java'
            extra.parent.mkdir(parents=True)
            extra.write_text('unreviewed')
            with self.assertRaisesRegex(ValueError, 'Unreviewed'):
                module.verify_tree(stage, expected)

    def test_publication_rejects_changed_source(self):
        frozen = {'source.java': b'old'}
        with patch.object(module, 'inputs', return_value={'source.java': b'new'}):
            with self.assertRaisesRegex(ValueError, 'publication rejected'):
                module.unchanged(frozen)

    def test_symlink_is_not_an_import(self):
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            (stage / 'real').write_text('data')
            (stage / 'linked').symlink_to(stage / 'real')
            with self.assertRaisesRegex(ValueError, 'Symlink'):
                module.verify_tree(stage, {'real': module.record(b'data')})


if __name__ == '__main__':
    unittest.main()
