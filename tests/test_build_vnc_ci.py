"""Portable provenance boundary tests; synthetic records are never native-build evidence."""
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1] / 'tools'
sys.path.insert(0, str(TOOLS))
SPEC = importlib.util.spec_from_file_location('vnc_ci', TOOLS / 'build_vnc_ci.py')
ci = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ci)


class PortableTests(unittest.TestCase):
    def fixture(self, directory):
        run = Path(directory) / 'openssl-ci'
        run.mkdir()
        archive = b'pinned-source-test-fixture'
        (run / 'FreeRDP.tar.gz').write_bytes(archive)
        (run / 'prepared').mkdir()
        (run / 'prepared/value').write_bytes(b'compiled-fixture')
        ci.native.save(run / 'openssl-build.json', {
            'command': ['cmake', '--build', 'build', '--target', 'openssl', '--parallel', '4'], 'exitCode': 0})
        lock = {'source': ci.record(archive), 'toolchain': {'ndk': '29', 'cmake': '4'}}
        inputs = {'recipe.py': ci.record(b'reviewed recipe')}
        receipt = {'schemaVersion': 1, 'mode': ci.MODE, 'scope': 'openssl-target-only', 'exitCode': 0,
                   'inputs': inputs, 'toolchainPolicy': lock['toolchain'], 'opensslSource': 'fixture-policy',
                   'files': {'value': ci.record(b'compiled-fixture')},
                   'evidence': {'openssl-build.json': ci.record(ci.native.read(run / 'openssl-build.json'))}}
        ci.native.save(run / 'receipt.json', receipt)
        return run, lock, inputs, receipt

    def test_offline_openssl_receipt_rejects_changed_output_recipe_and_failed_result(self):
        with tempfile.TemporaryDirectory() as directory:
            run, lock, inputs, receipt = self.fixture(directory)
            # ELF validation is separately reused; this fixture tests provenance, not a fake compiler.
            with patch.object(ci, 'recipe_inputs', return_value=(lock, inputs)), patch.object(ci, 'validate_ssl_files'), patch.object(ci, 'ssl_source_policy', return_value='fixture-policy'):
                self.assertEqual(receipt, ci.check_openssl(run))
                (run / 'prepared/value').write_bytes(b'tampered')
                with self.assertRaisesRegex(ValueError, 'prepared bytes'):
                    ci.check_openssl(run)
                (run / 'prepared/value').write_bytes(b'compiled-fixture')
                with patch.object(ci, 'recipe_inputs', return_value=(lock, {'recipe.py': ci.record(b'changed')})):
                    with self.assertRaisesRegex(ValueError, 'provenance'):
                        ci.check_openssl(run)
                for changes in ({'exitCode': 1}, {'scope': 'full-rdp-build'}, {'mode': 'skip'}):
                    with self.assertRaisesRegex(ValueError, 'provenance'):
                        ci.check_openssl(run, receipt | changes)

    def test_successful_process_must_be_exact_openssl_target(self):
        with tempfile.TemporaryDirectory() as directory:
            run, lock, inputs, receipt = self.fixture(directory)
            for target, code in (('freerdp-android', 0), ('openssl', 1)):
                ci.native.save(run / 'openssl-build.json', {
                    'command': ['cmake', '--build', 'build', '--target', target, '--parallel', '4'], 'exitCode': code})
                receipt['evidence']['openssl-build.json'] = ci.record(ci.native.read(run / 'openssl-build.json'))
                with patch.object(ci, 'recipe_inputs', return_value=(lock, inputs)), patch.object(ci, 'validate_ssl_files'), patch.object(ci, 'ssl_source_policy', return_value='fixture-policy'):
                    with self.assertRaisesRegex(ValueError, 'OpenSSL-only'):
                        ci.check_openssl(run, receipt)

    def test_predecessor_path_rejects_tamper_and_outside_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cache = root / 'cache'
            run = cache / 'ci'
            run.mkdir(parents=True)
            (run / 'receipt.json').write_bytes(b'original')
            with patch.object(ci, 'ROOT', root):
                pin = ci.predecessor(run / 'receipt.json')
                self.assertEqual(run, ci.predecessor_path(pin, cache))
                (run / 'receipt.json').write_bytes(b'tamper')
                with self.assertRaisesRegex(ValueError, 'mismatch'):
                    ci.predecessor_path(pin, cache)
                with self.assertRaisesRegex(ValueError, 'outside'):
                    ci.predecessor_path(pin, root / 'other')
                with self.assertRaisesRegex(ValueError, 'Unsafe'):
                    ci.predecessor_path(pin | {'path': '../receipt.json'}, cache)

    def test_openssl_import_rejects_changed_or_missing_closure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = root / 'openssl-ci'
            run.mkdir()
            (run / 'receipt.json').write_bytes(b'success')
            files = {'lib/libssl.so': b'ssl', 'lib/libcrypto.so': b'crypto'}
            receipt = {'files': {name: ci.record(raw) for name, raw in files.items()}}
            with patch.object(ci, 'CACHE', root), patch.object(ci, 'ROOT', root), patch.object(ci, 'check_openssl', return_value=receipt):
                ci.portable_ssl_import(run / 'receipt.json', files)
                for changed in ({'lib/libssl.so': b'changed', 'lib/libcrypto.so': b'crypto'}, {'lib/libssl.so': b'ssl'}):
                    with self.assertRaisesRegex(ValueError, 'Imported OpenSSL'):
                        ci.portable_ssl_import(run / 'receipt.json', changed)

    def test_adapter_portable_route_rejects_nonportable_base(self):
        with patch.object(ci.adapter.base, 'check', return_value={'mode': 'historical'}):
            with self.assertRaisesRegex(ValueError, 'source-built base'):
                ci.adapter.patched_source(Path('/unused'), Path('/unused-target'), {}, source_build=True)

    def test_android_requires_portable_predecessor_and_default_pin_remains_strict(self):
        contents = ci.android.inputs()
        prefix = ci.android.OVERLAY.relative_to(ci.android.ROOT).as_posix() + '/'
        lock = json.loads(contents[prefix + 'packaging.lock.json'])
        with patch.object(ci.adapter, 'check', return_value={'mode': 'historical'}):
            with self.assertRaisesRegex(ValueError, 'source-built adapter'):
                ci.android.expected_files(contents, Path('/unused'))
        with patch.object(ci.adapter.base, 'read', return_value=b'not-pinned'):
            with self.assertRaisesRegex(ValueError, 'Pinned accepted adapter receipt changed'):
                ci.android.expected_files(contents)
        self.assertEqual('auth-idle-01', lock['nativeRun'])

    def test_source_policy_rejects_different_tls_or_abi(self):
        lock, _ = ci.recipe_inputs()
        self.assertIn('sha256', ci.ssl_source_policy(lock))
        changed = json.loads(json.dumps(lock))
        for item in changed['dependencyArchives']:
            if item['archive'] == 'openssl-4.0.1.tar.gz':
                item['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'source policy'):
            ci.ssl_source_policy(changed)
        changed = json.loads(json.dumps(lock))
        changed['toolchain']['abi'] = 'x86_64'
        with self.assertRaisesRegex(ValueError, 'toolchain policy'):
            ci.ssl_source_policy(changed)

    def test_chain_recurses_all_predecessors_and_rejects_swapped_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cache = root / 'ci'
            run = cache / 'test'
            run.mkdir(parents=True)
            paths = {'openssl': cache / 'openssl-test', 'native': root / 'native/ci-test',
                     'adapter': root / 'adapter/ci-test', 'android': root / 'android/test'}
            for key, path in paths.items():
                path.mkdir(parents=True)
                (path / ('cloudy-preparation.json' if key == 'android' else 'receipt.json')).write_bytes(key.encode())
            with patch.object(ci, 'ROOT', root), patch.object(ci, 'CACHE', cache), \
                    patch.object(ci.native, 'CACHE', root / 'native'), patch.object(ci.adapter, 'CACHE', root / 'adapter'), \
                    patch.object(ci.android, 'CACHE', root / 'android'):
                receipt = {'mode': ci.MODE, 'scope': ci.SCOPE, 'exitCode': 0,
                           'chain': {key: ci.predecessor(path / ('cloudy-preparation.json' if key == 'android' else 'receipt.json'))
                                     for key, path in paths.items()}}
                ci.native.save(run / 'receipt.json', receipt)
                with patch.object(ci, 'check_openssl') as ssl, patch.object(ci.native, 'check') as base, \
                        patch.object(ci.adapter, 'check') as adapter, patch.object(ci.android, 'check') as android:
                    ci.check_chain(run)
                    ssl.assert_called_once_with(paths['openssl'])
                    base.assert_called_once_with(paths['native'])
                    adapter.assert_called_once_with(paths['adapter'])
                    android.assert_called_once_with(paths['android'])
                    (paths['native'] / 'receipt.json').write_bytes(b'swapped')
                    with self.assertRaisesRegex(ValueError, 'chain receipt changed'):
                        ci.check_chain(run)

    def merged_fixture(self):
        lock = json.loads(ci.native.read(ci.android.OVERLAY / 'packaging.lock.json'))['application']
        return ET.fromstring(f"""<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="dev.cloudy.qualification.vnc" android:versionCode="{lock['versionCode']}" android:versionName="{lock['versionName']}">
            <uses-sdk android:minSdkVersion="35" android:targetSdkVersion="37"/>
            <uses-permission android:name="android.permission.INTERNET"/>
            <uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK"/>
            <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
            <uses-feature android:name="android.hardware.faketouch" android:required="false"/>
            <application android:allowBackup="false" android:usesCleartextTraffic="false" android:extractNativeLibs="false"
                android:dataExtractionRules="@xml/data_extraction_rules">
                <activity android:name="dev.cloudy.qualification.vnc.VncActivity" android:exported="true" android:resizeableActivity="true"/>
            </application></manifest>""")

    def test_merged_manifest_rejects_extra_permission_component_and_backup(self):
        ns = '{http://schemas.android.com/apk/res/android}'
        self.assertEqual(2, len(ci.audit_manifest(ET.tostring(self.merged_fixture()))['permissions']))
        for extra in ('service', 'provider', 'receiver', 'activity-alias'):
            xml = self.merged_fixture()
            ET.SubElement(xml.find('application'), extra, {ns + 'name': 'Unexpected'})
            with self.assertRaisesRegex(ValueError, 'component closure'):
                ci.audit_manifest(ET.tostring(xml))
        xml = self.merged_fixture()
        ET.SubElement(xml, 'uses-permission', {ns + 'name': 'android.permission.RECORD_AUDIO'})
        with self.assertRaisesRegex(ValueError, 'permission closure'):
            ci.audit_manifest(ET.tostring(xml))
        for flag in ('allowBackup', 'usesCleartextTraffic', 'extractNativeLibs'):
            xml = self.merged_fixture()
            xml.find('application').set(ns + flag, 'true')
            with self.assertRaisesRegex(ValueError, 'flags differ'):
                ci.audit_manifest(ET.tostring(xml))

    def test_actual_apk_asset_gate_rejects_added_or_changed_payload(self):
        name = ci.android.GENERATED + 'nativeNotices/notice.txt'
        expected = {name: ci.record(b'reviewed')}
        for files, accepted in (({'notice.txt': b'reviewed'}, True),
                                ({'notice.txt': b'reviewed', 'extra.bin': b'unreviewed'}, False),
                                ({'notice.txt': b'changed'}, False), ({}, False)):
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w') as bundle:
                for asset, raw in files.items():
                    bundle.writestr('assets/' + asset, raw)
            with zipfile.ZipFile(io.BytesIO(stream.getvalue())) as bundle:
                if accepted:
                    ci.audit_assets(bundle, expected)
                else:
                    with self.assertRaises(ValueError):
                        ci.audit_assets(bundle, expected)

    def test_openssl_issuer_binds_direct_build_helper(self):
        _, inputs = ci.recipe_inputs()
        self.assertEqual(ci.record(ci.native.read(ci.ROOT / 'tools/prepare_vnc_native.py')),
                         inputs['tools/prepare_vnc_native.py'])

    def test_ssl_closure_rejects_missing_header_and_unrelated_headers(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            ci.validate_ssl_files({'lib/libssl.so': b'x'})
        files = {'lib/libssl.so': b'x', 'lib/libcrypto.so': b'y', 'include/openssl/ssl.h': b'h', 'include/winpr3/unrelated.h': b'z'}
        with self.assertRaisesRegex(ValueError, 'Unexpected'):
            ci.validate_ssl_files(files)


if __name__ == '__main__':
    unittest.main()
