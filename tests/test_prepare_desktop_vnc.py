"""Authored namespace/import/publication controls only; no native build or device evidence."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
SPEC = importlib.util.spec_from_file_location('prepare_desktop_vnc', ROOT / 'tools/prepare_desktop_vnc.py')
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class PreparationTests(unittest.TestCase):
    def test_namespace_only_port(self):
        original = b'#define JNI_NAME(name) Java_dev_cloudy_qualification_vnc_VncBridge_##name\n'
        expected = b'#define JNI_NAME(name) ' + module.PREFIX.encode() + b'##name\n'
        self.assertEqual(module.namespace_port(original), expected)
        for invalid in (b'no JNI prefix', original * 2, expected):
            with self.assertRaisesRegex(ValueError, 'JNI_NAMESPACE_SOURCE'):
                module.namespace_port(invalid)

    def test_relative_path_and_symlink_refusal(self):
        for path in ('', '../outside', '/absolute', 'a/../b', 'a//b'):
            with self.assertRaises(ValueError):
                module.relative(path)
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-test-') as temporary:
            root = Path(temporary)
            (root / 'real').write_bytes(b'actual')
            (root / 'link').symlink_to(root / 'real')
            with self.assertRaisesRegex(ValueError, 'SYMLINK_INPUT'):
                module.read(root / 'link')

    def test_stage_refuses_existing_output_without_overwrite(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-test-') as temporary:
            root = Path(temporary)
            target = root / 'output'
            target.mkdir()
            (target / 'sentinel').write_bytes(b'preserved')
            with patch.object(module, 'CACHE', root), patch.object(module, 'module_inputs', return_value={}), patch.object(
                    module, 'selected_inputs', return_value=({'baseToolchain': {}}, {})):
                with self.assertRaises(FileExistsError):
                    module.stage(target)
            self.assertEqual((target / 'sentinel').read_bytes(), b'preserved')

    def test_stage_is_uncompiled_and_never_relabels_old_jni(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-test-') as temporary:
            root = Path(temporary)
            original = root / 'original'
            original.mkdir()
            for name in module.PINS:
                (original / name).write_bytes(b'fixture')
            (original / 'adapter.c').write_bytes(b'Java_dev_cloudy_qualification_vnc_VncBridge_')
            payload = {'nativeLibraries/arm64-v8a/libcloudy-vnc-jni.so': b'old JNI',
                       'nativeLibraries/arm64-v8a/libssl.so': b'ssl',
                       'nativeIncludes/rfb/rfbclient.h': b'exact header'}
            with patch.object(module, 'CACHE', root), patch.object(module, 'ORIGINAL', original), patch.object(
                    module, 'module_inputs', return_value={'source': 'digest'}), patch.object(
                    module, 'selected_inputs', return_value=({'baseToolchain': {}}, payload)), patch.object(
                    module.selected_native, 'bounded', side_effect=AssertionError('NO_COMPILATION')):
                result = module.stage(root / 'output')
            self.assertFalse(result['nativeBuildExecuted'])
            self.assertEqual(result['status'], 'STAGED_NOT_COMPILED')
            self.assertFalse((root / 'output/jniLibs').exists())
            self.assertFalse(any(p.name == 'libcloudy-vnc-jni.so' for p in (root / 'output').rglob('*')))
            self.assertEqual((root / 'output/input/ssl/lib/libssl.so').read_bytes(), b'ssl')

    def test_selected_receipt_and_authored_pin_fail_before_import(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-test-') as temporary:
            root = Path(temporary)
            (root / 'receipt.json').write_bytes(b'wrong receipt')
            with patch.object(module, 'SELECTED', root):
                with self.assertRaisesRegex(ValueError, 'SELECTED_RECEIPT'):
                    module.selected_inputs()
            raw = json.dumps({'exitCode': 0}).encode()
            (root / 'receipt.json').write_bytes(raw)
            (root / 'adapter.c').write_bytes(b'wrong source')
            with patch.object(module, 'SELECTED', root), patch.object(module, 'ORIGINAL', root), patch.object(
                    module, 'RECEIPT_SHA', module.sha(raw)):
                with self.assertRaisesRegex(ValueError, 'SELECTED_AUTHORED_INPUT'):
                    module.selected_inputs()

    def test_exact_native_closure_and_namespace_exports(self):
        with self.assertRaisesRegex(ValueError, 'NATIVE_CLOSURE'):
            module.verify_libraries({'libvncclient.so': b'wrong'})
        payload = {name: name.encode() for name in module.LIBRARIES}
        info = {'machine': 'AArch64', 'minimumLoadAlignment': 16384,
                'needed': ['libssl.so', 'libcrypto.so', 'libc.so']}
        with patch.object(module, 'elf_info', side_effect=lambda raw: {**info, 'soname': raw.decode()}), patch.object(module, 'jni_exports', return_value={
                module.PREFIX + name for name in module.METHODS}):
            module.verify_libraries(payload)
        with patch.object(module, 'elf_info', side_effect=lambda raw: {**info, 'soname': raw.decode()}), patch.object(module, 'jni_exports', return_value={
                'Java_dev_cloudy_qualification_vnc_VncBridge_create'}):
            with self.assertRaisesRegex(ValueError, 'NATIVE_EXPORTS'):
                module.verify_libraries(payload)
        with patch.object(module, 'elf_info', side_effect=lambda raw: {**info, 'soname': raw.decode(), 'needed': ['libvncclient.so']}):
            with self.assertRaisesRegex(ValueError, 'NATIVE_ELF'):
                module.verify_libraries(payload)

    def test_staged_drift_and_generated_port_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-test-') as temporary:
            root = Path(temporary)
            (root / 'input').mkdir()
            (root / 'original').mkdir()
            original = b'Java_dev_cloudy_qualification_vnc_VncBridge_'
            (root / 'original/adapter.c').write_bytes(original)
            expected = module.namespace_port(original)
            (root / 'input/adapter.c').write_bytes(expected)
            record = {'staged': {'input/adapter.c': module.sha(expected)}}
            with patch.object(module, 'ORIGINAL', root / 'original'):
                module.verify_staged(root, record)
                (root / 'input/adapter.c').write_bytes(expected + b'changed')
                with self.assertRaisesRegex(ValueError, 'STAGED_IMPORT_CHANGED'):
                    module.verify_staged(root, record)
                record['staged']['input/adapter.c'] = module.sha(expected + b'changed')
                with self.assertRaisesRegex(ValueError, 'GENERATED_PORT_CHANGED'):
                    module.verify_staged(root, record)

    def test_official_ndk_tool_link_must_resolve_inside_selected_root(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-tool-') as temporary:
            root = Path(temporary)
            ndk = root / 'ndk'
            binary_dir = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
            binary_dir.mkdir(parents=True)
            target = binary_dir / 'clang-real'
            target.write_bytes(b'pinned tool bytes')
            link = binary_dir / 'clang'
            link.symlink_to('clang-real')
            self.assertEqual(module.ndk_clang_digest(link, ndk), module.sha(b'pinned tool bytes'))
            # The same symlink is still forbidden to generic source/receipt/output reads.
            with self.assertRaisesRegex(ValueError, 'SYMLINK_INPUT'):
                module.read(link)
            link.unlink()
            outside = root / 'outside-tool'
            outside.write_bytes(b'untrusted')
            link.symlink_to(outside)
            with self.assertRaisesRegex(ValueError, 'NDK_TOOL_ESCAPE'):
                module.ndk_clang_digest(link, ndk)
            with self.assertRaisesRegex(ValueError, 'NDK_TOOL_PATH'):
                module.ndk_clang_digest(target, ndk)

    def test_tool_streaming_bound_and_growth_are_refused(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-tool-bound-') as temporary:
            ndk = Path(temporary) / 'ndk'
            directory = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
            directory.mkdir(parents=True)
            target = directory / 'clang-real'
            target.write_bytes(b'pinned')
            link = directory / 'clang'
            link.symlink_to('clang-real')
            with self.assertRaisesRegex(ValueError, 'NDK_TOOL_BOUND'):
                module.ndk_clang_digest(link, ndk, maximum_bytes=5)
            actual_read = module.os.read
            changed = False

            def growing_read(descriptor, size):
                nonlocal changed
                value = actual_read(descriptor, size)
                if value and not changed:
                    changed = True
                    with target.open('ab') as stream:
                        stream.write(b'growth')
                return value

            with patch.object(module.os, 'read', side_effect=growing_read):
                with self.assertRaisesRegex(ValueError, 'NDK_TOOL_GROWTH'):
                    module.ndk_clang_digest(link, ndk)

    def test_tool_streaming_replacement_is_refused(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-tool-replace-') as temporary:
            ndk = Path(temporary) / 'ndk'
            directory = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
            directory.mkdir(parents=True)
            target = directory / 'clang-real'
            target.write_bytes(b'pinned')
            link = directory / 'clang'
            link.symlink_to('clang-real')
            actual_read = module.os.read
            changed = False

            def replacing_read(descriptor, size):
                nonlocal changed
                value = actual_read(descriptor, size)
                if value and not changed:
                    changed = True
                    replacement = directory / 'replacement'
                    replacement.write_bytes(b'pinned')
                    replacement.replace(target)
                return value

            with patch.object(module.os, 'read', side_effect=replacing_read):
                with self.assertRaisesRegex(ValueError, 'NDK_TOOL_CHANGED'):
                    module.ndk_clang_digest(link, ndk)

    def test_existing_limiter_stops_owned_group_before_failed_receipt(self):
        # Actual retained limiter and stop_process_group functions; only process/time/kill
        # primitives are test doubles. No child process or signal is executed.
        import build_rdp_native
        import subprocess
        events = []

        class Process:
            pid = 123456
            returncode = None

            def poll(self):
                return None

            def wait(self, timeout=None):
                events.append(('wait', timeout))
                self.returncode = -9
                return self.returncode

        def popen(command, **kwargs):
            self.assertTrue(kwargs['start_new_session'])
            events.append(('new-session', tuple(command)))
            return Process()

        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-timeout-') as temporary:
            out = Path(temporary)
            with patch.object(module.selected_native.subprocess, 'Popen', side_effect=popen), patch.object(
                    module.selected_native.time, 'monotonic', side_effect=[0, 241]), patch.object(
                    build_rdp_native.os, 'killpg', side_effect=lambda pid, sig: events.append(('signal', sig))):
                with self.assertRaises(subprocess.TimeoutExpired):
                    module.selected_native.bounded(['fixture-no-exec'], out, 'compile',
                                                   {'timeoutSeconds': 240, 'maximumLogBytes': 1024})
            self.assertEqual(events, [('new-session', ('fixture-no-exec',)),
                                     ('signal', build_rdp_native.signal.SIGTERM), ('wait', 10),
                                     ('signal', build_rdp_native.signal.SIGKILL), ('wait', None)])
            receipt = json.loads((out / 'compile.json').read_text())
            self.assertTrue(receipt['incomplete'])
            self.assertEqual(receipt['exitCode'], -9)

    def test_staged_receipt_is_not_packageable(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-product-test-') as temporary:
            root = Path(temporary)
            target = root / 'prepared'
            target.mkdir()
            (target / 'receipt.json').write_text(json.dumps({'status': 'STAGED_NOT_COMPILED'}))
            with patch.object(module, 'CACHE', root):
                with self.assertRaisesRegex(ValueError, 'UNACCEPTED_PRODUCT_NATIVE'):
                    module.check(target)


    def test_explicit_java_reuse_policy_and_native_drift_refusal(self):
        # Pure metadata policy. No native import/ELF/platform evidence is supplied by these mocks.
        java = 'adapters/desktop/vnc/android/src/main/java/io/github/baitian6641/cloudy/desktop/vnc/VncEngine.java'
        cmake = 'adapters/desktop/vnc/android/src/main/cpp/CMakeLists.txt'
        bridge = 'adapters/desktop/vnc/android/src/main/java/io/github/baitian6641/cloudy/desktop/vnc/VncBridge.java'
        baseline = {java: 'old-java', cmake: 'same-cmake', bridge: 'same-bridge'}
        current = dict(baseline, **{java: 'reviewed-java'})
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-reuse-policy-') as temporary:
            root = Path(temporary)
            declaration = root / 'reuse.json'
            def declare(sources):
                raw = json.dumps({'schema': 1, 'scope': 'OWNER_JAVA_ONLY_NATIVE_REUSE',
                                  'originalReceiptSha256': 'bound-receipt', 'expectedCurrentSources': sources}).encode()
                declaration.write_bytes(raw)
                return module.sha(raw)
            digest = declare(current)
            with patch.object(module, 'CACHE', root), patch.object(module, 'module_inputs', return_value=current), patch.object(
                    module, 'toolchain') as toolchain:
                result = module.verify_reuse_sources({'sources': baseline}, 'bound-receipt', declaration, digest)
                self.assertFalse(result['wholeModuleUnchanged'])
                self.assertFalse(result['nativeRebuilt'])
                self.assertEqual(set(result['sourceChanges']), {java})
                toolchain.assert_called_once()
                with self.assertRaisesRegex(ValueError, 'REUSE_DECLARATION_BINDING'):
                    module.verify_reuse_sources({'sources': baseline}, 'bound-receipt', declaration, 'wrong')
                for native in (cmake, bridge):
                    changed = dict(current, **{native: 'drift'})
                    digest = declare(changed)
                    with patch.object(module, 'module_inputs', return_value=changed):
                        with self.assertRaisesRegex(ValueError, 'REUSE_NATIVE_OR_UNREVIEWED_SOURCE_DRIFT'):
                            module.verify_reuse_sources({'sources': baseline}, 'bound-receipt', declaration, digest)
                digest = declare(dict(current, **{'unexpected/new-file': 'new'}))
                with self.assertRaisesRegex(ValueError, 'REUSE_CURRENT_SOURCE_BINDING'):
                    module.verify_reuse_sources({'sources': baseline}, 'bound-receipt', declaration, digest)

    def test_explicit_reuse_does_not_bypass_original_receipt_pin(self):
        with tempfile.TemporaryDirectory(prefix='cloudy-vnc-reuse-receipt-') as temporary:
            root = Path(temporary)
            output = root / 'prepared'
            output.mkdir()
            sentinel = b'{"schema":1,"status":"NATIVE_NAMESPACE_PORT_COMPILED"}'
            (output / 'receipt.json').write_bytes(sentinel)
            with patch.object(module, 'CACHE', root):
                with self.assertRaisesRegex(ValueError, 'REUSE_ORIGINAL_RECEIPT_BINDING'):
                    module.check(output, ('wrong', root / 'missing.json', 'wrong'))
            self.assertEqual((output / 'receipt.json').read_bytes(), sentinel)



    def test_portable_selection_requires_exact_maintained_chain(self):
        import build_vnc_ci as ci
        import prepare_vnc_adapter as adapter
        with tempfile.TemporaryDirectory(prefix='cloudy-product-ci-policy-') as temporary:
            root = Path(temporary)
            chain, selected = root / 'ci', root / 'adapter/ci-ci'
            chain.mkdir()
            selected.mkdir(parents=True)
            (chain / 'receipt.json').write_bytes(b'chain')
            (selected / 'receipt.json').write_bytes(b'adapter')
            with patch.object(ci, 'CACHE', root), patch.object(adapter, 'CACHE', root / 'adapter'), patch.object(
                    ci, 'check_chain') as checked, patch.object(adapter, 'check', return_value={
                        'mode': ci.MODE, 'qualification': ci.SCOPE}) as admitted:
                path, _, selection = module.portable_selected('ci')
                self.assertEqual(path, selected)
                self.assertEqual(selection['adapterReceiptSha256'], module.sha(b'adapter'))
                checked.assert_called_once_with(chain)
                admitted.assert_called_once_with(selected)
                with self.assertRaises(ValueError):
                    module.portable_selected('../ci')
                checked.side_effect = ValueError('unaccepted-chain')
                admitted.reset_mock()
                with self.assertRaisesRegex(ValueError, 'unaccepted-chain'):
                    module.portable_selected('ci')
                admitted.assert_not_called()
                checked.side_effect = None
                admitted.return_value = {'mode': 'historical', 'qualification': ci.SCOPE}
                with self.assertRaisesRegex(ValueError, 'PORTABLE_SELECTED_MODE'):
                    module.portable_selected('ci')

    def test_portable_receipt_drift_and_import_drift_still_refuse(self):
        selection = {'mode': 'portable-source-build', 'chainRun': 'ci',
                     'chainReceiptSha256': 'chain-sha', 'adapterReceiptSha256': 'adapter-sha'}
        with tempfile.TemporaryDirectory(prefix='cloudy-product-ci-check-') as temporary:
            root = Path(temporary)
            output = root / 'prepared'
            output.mkdir()
            record = {'status': 'NATIVE_NAMESPACE_PORT_COMPILED', 'nativeBuildExecuted': True,
                      'selectedReceiptSha256': 'adapter-sha', 'sources': {}, 'imports': {},
                      'portableSelection': selection}
            (output / 'receipt.json').write_text(json.dumps(record))
            with patch.object(module, 'CACHE', root), patch.object(module, 'module_inputs', return_value={}), patch.object(
                    module, 'portable_selected', return_value=(root, {}, dict(selection, chainReceiptSha256='changed'))):
                with self.assertRaisesRegex(ValueError, 'PORTABLE_SELECTION_DRIFT'):
                    module.check(output)
            with patch.object(module, 'CACHE', root), patch.object(module, 'module_inputs', return_value={}), patch.object(
                    module, 'portable_selected', return_value=(root, {}, selection)), patch.object(
                    module, 'toolchain'), patch.object(module, 'selected_inputs', return_value=({}, {'input': b'drift'})):
                with self.assertRaisesRegex(ValueError, 'SELECTED_IMPORT_BINDING'):
                    module.check(output)


if __name__ == '__main__':
    unittest.main()
