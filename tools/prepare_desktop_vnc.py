#!/usr/bin/env python3
"""Stage the exact selected patched VNC imports and build only the JNI namespace port.

No acquisition, Gradle, device or upstream OpenSSL/JPEG/LibVNC compilation. --prepare
is a parent-scheduled native build; --stage only stages verified imports/recipe.
All output must be a fresh direct child of .cache/vnc-product-engine-01.
"""
import argparse
import hashlib
import json
import os
import stat
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
from prepare_rdp_native import elf_info  # noqa: E402
import prepare_vnc_native as selected_native  # noqa: E402

CACHE = ROOT / '.cache/vnc-product-engine-01'
SELECTED = ROOT / '.cache/native-vnc/adapter/auth-idle-01'
MODULE = ROOT / 'adapters/desktop/vnc/android'
ORIGINAL = ROOT / 'qualification/probes/native-vnc/adapter'
RECEIPT_SHA = '55a9d23cba883119fd45442a7a75895dfd1cc52ce56e8297417e4cf26515cd53'
PINS = {
    'adapter.c': 'e3de365df791628c4d6c88df6ecca97726aa12243eb36bbc2a109e701fda277c',
    'policy.c': '0a47ca5e817ca8c858c69bc43c01569e72b61b1931a07ad21cda6d767fca6096',
    'policy.h': 'aa380f6708f6e1f8eb16759d97ab8079ae1db9c655d241af1afcfbcf479d9e2b',
    'VncBridge.java': '4addfe369e1595ed8dffceb8260fbe8a8f2e59dd023ae9664d0c0a272f27b95a',
    'security-and-cancel.patch': 'e110e84f0c093375ac01f1b421afa6d5131fb9fdbdb7a5cf9348a24c40f476ff',
    'build.lock.json': '9ad4e50b04b25c33d6b7ebcb0ed6e2fcc9da33e81de5c69979044d8448c1f115',
}
PREFIX = 'Java_io_github_baitian6641_cloudy_desktop_vnc_VncBridge_'
METHODS = {'create', 'start', 'cancel', 'join', 'free', 'status', 'pointer', 'key', 'releaseInputs'}
LIBRARIES = {'libcloudy-vnc-jni.so', 'libssl.so', 'libcrypto.so'}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read(path):
    for entry in (path, *path.parents):
        if entry.is_symlink():
            raise ValueError('SYMLINK_INPUT')
    if not path.is_file() or path.stat().st_size > 128 * 1024 * 1024:
        raise ValueError('INPUT_BOUND')
    return path.read_bytes()


def relative(name):
    value = Path(name)
    if not name or value.is_absolute() or '..' in value.parts or str(value) != name:
        raise ValueError('RELATIVE_PATH')
    return value


def namespace_port(raw):
    old = b'Java_dev_cloudy_qualification_vnc_VncBridge_'
    if raw.count(old) != 1 or PREFIX.encode() in raw:
        raise ValueError('JNI_NAMESPACE_SOURCE')
    return raw.replace(old, PREFIX.encode())


def module_inputs():
    paths = [Path(__file__).resolve(), ROOT / 'tools/prepare_rdp_native.py',
             ROOT / 'tools/prepare_vnc_native.py', ROOT / 'tools/build_rdp_native.py',
             *sorted(MODULE.rglob('*'))]
    if any(p.is_symlink() for p in paths):
        raise ValueError('SYMLINK_MODULE_INPUT')
    return {str(p.relative_to(ROOT)): sha(read(p)) for p in paths
            if p.is_file() and 'build' not in p.relative_to(ROOT).parts}


def selected_inputs():
    raw = read(SELECTED / 'receipt.json')
    if sha(raw) != RECEIPT_SHA:
        raise ValueError('SELECTED_RECEIPT')
    receipt = json.loads(raw)
    if receipt['exitCode'] != 0:
        raise ValueError('SELECTED_FAILED')
    for name, pin in PINS.items():
        if sha(read(ORIGINAL / name)) != pin:
            raise ValueError('SELECTED_AUTHORED_INPUT')
    prepared = SELECTED / 'prepared'
    manifest = receipt['prepared']
    # Only inputs consumed by this port: no unchanged upstream conformance/rebuild.
    names = {name for name in manifest if name.startswith('nativeIncludes/rfb/') or
             name.startswith('nativeNotices/openssl-import/include/openssl/')}
    names |= {'nativeNotices/patched-libvncclient.a', 'nativeNotices/libjpeg-android-pic.a',
              'nativeNotices/vnc-rfbconfig.h', 'nativeNotices/LibVNC-GPL2.txt',
              'nativeNotices/libjpeg-turbo-LICENSE.md', 'nativeNotices/libjpeg-turbo-README.ijg',
              'nativeNotices/OpenSSL-LICENSE.txt', 'nativeNotices/NOTICE.txt', 'nativeNotices/BASE-NOTICE.txt',
              'nativeNotices/libvncserver-98aaee0560baa0332339b2ef464ea96ed9b9b28c.tar.gz',
              'nativeNotices/libjpeg-turbo-3.2.0.tar.gz', 'nativeNotices/openssl-4.0.1.tar.gz',
              'nativeNotices/openssl-import/accepted-receipt.json'}
    names |= {'nativeLibraries/arm64-v8a/' + name for name in LIBRARIES}
    output = {}
    for name in sorted(names):
        raw = read(prepared / relative(name))
        if sha(raw) != manifest[name]:
            raise ValueError('SELECTED_IMPORT')
        output[name] = raw
    if not any(name.startswith('nativeIncludes/rfb/') for name in names) or not any(
            name.startswith('nativeNotices/openssl-import/include/openssl/') for name in names):
        raise ValueError('MATCHING_HEADERS_MISSING')
    return receipt, output


def destination(path):
    for entry in (path, *path.parents):
        if entry.is_symlink():
            raise ValueError('OUTPUT_SYMLINK')
    if path.absolute().parent != CACHE.absolute() or path.name in ('', '.', '..'):
        raise ValueError('OUTPUT_SCOPE')
    if path.is_symlink():
        raise ValueError('OUTPUT_SYMLINK')
    return path


def save(root, name, raw):
    target = root / relative(name)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open('xb') as stream:
        stream.write(raw)


def stage(output):
    destination(output)
    sources = module_inputs()
    receipt, imports = selected_inputs()
    output.mkdir(parents=True, exist_ok=False)
    save(output, 'input/adapter.c', namespace_port(read(ORIGINAL / 'adapter.c')))
    for name in ('policy.c', 'policy.h'):
        save(output, 'input/' + name, read(ORIGINAL / name))
    for name, raw in imports.items():
        if name.startswith('nativeIncludes/'):
            target = 'input/include/' + name.removeprefix('nativeIncludes/')
        elif '/include/openssl/' in name:
            target = 'input/ssl/include/openssl/' + name.split('/include/openssl/')[1]
        elif name.endswith('patched-libvncclient.a'):
            target = 'input/libvncclient.a'
        elif name.endswith('libjpeg-android-pic.a'):
            target = 'input/libjpeg.a'
        elif name.startswith('nativeLibraries/') and not name.endswith('libcloudy-vnc-jni.so'):
            target = 'input/ssl/lib/' + Path(name).name
        elif name.endswith('libcloudy-vnc-jni.so'):
            continue  # Never package or relabel the old namespace library.
        else:
            target = 'assets/cloudy-vnc-notices/' + Path(name).name
        save(output, target, raw)
    for name in PINS:
        save(output, 'assets/cloudy-vnc-notices/authored/' + name, read(ORIGINAL / name))
    record = {'schema': 1, 'status': 'STAGED_NOT_COMPILED', 'selectedReceiptSha256': RECEIPT_SHA,
              'sources': sources, 'imports': {n: sha(b) for n, b in imports.items()},
              'toolchain': receipt['baseToolchain'], 'nativeBuildExecuted': False,
              'jniNamespaceOnly': True, 'releaseClosureApproved': False}
    record['staged'] = {str(p.relative_to(output)): sha(read(p)) for p in sorted(output.rglob('*')) if p.is_file()}
    save(output, 'stage.json', (json.dumps(record, indent=2) + '\n').encode())
    return record


def ndk_clang_digest(path, ndk, maximum_bytes=256 * 1024 * 1024):
    # Only this official tool link may resolve. Generic sources/receipts/outputs remain no-follow.
    expected = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
    if path != expected or ndk.resolve(strict=True) != ndk.absolute():
        raise ValueError('NDK_TOOL_PATH')
    for parent in path.parents:
        if parent.is_symlink():
            raise ValueError('NDK_TOOL_PARENT')
    target = path.resolve(strict=True)
    if not target.is_relative_to(ndk):
        raise ValueError('NDK_TOOL_ESCAPE')
    def identity(value):
        return (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_ctime_ns)

    before = target.stat(follow_symlinks=False)
    if not stat.S_ISREG(before.st_mode) or not 0 < before.st_size <= maximum_bytes:
        raise ValueError('NDK_TOOL_BOUND')
    descriptor = os.open(target, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        if identity(os.fstat(descriptor)) != identity(before):
            raise ValueError('NDK_TOOL_CHANGED')
        digest = hashlib.sha256()
        count = 0
        while True:
            block = os.read(descriptor, min(1024 * 1024, before.st_size + 1 - count))
            if not block:
                break
            count += len(block)
            if count > before.st_size or count > maximum_bytes:
                raise ValueError('NDK_TOOL_GROWTH')
            digest.update(block)
        if count != before.st_size or identity(os.fstat(descriptor)) != identity(before) or identity(
                target.stat(follow_symlinks=False)) != identity(before):
            raise ValueError('NDK_TOOL_CHANGED')
        return digest.hexdigest()
    finally:
        os.close(descriptor)


def toolchain(record):
    # Existing selected SDK tools only; no acquisition or shell lookup fallback.
    sdk = ROOT / '.cache/android-sdk'
    ndk = sdk / 'ndk/29.0.14206865'
    cmake = sdk / 'cmake/4.1.2/bin/cmake'
    ninja = sdk / 'cmake/4.1.2/bin/ninja'
    clang = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
    for path, name in [(ndk / 'source.properties', 'ndk.properties'), (cmake, 'cmake'),
                       (ninja, 'ninja')]:
        if sha(read(path)) != record['toolchain'][name]:
            raise ValueError('SELECTED_TOOLCHAIN')
    if ndk_clang_digest(clang, ndk) != record['toolchain']['clang']:
        raise ValueError('SELECTED_CLANG')
    return ndk, cmake, ninja


def jni_exports(raw):
    # Same bounded ELF64 dynamic-symbol boundary as the existing RDP verifier,
    # returning Java exports for this namespace rather than its RDP-specific set.
    if len(raw) < 64:
        raise ValueError('ELF_HEADER')
    header = struct.unpack_from('<16sHHIQQQIHHHHHH', raw)
    offset, size, count = header[6], header[11], header[12]
    if size != 64 or not 0 < count <= 4096 or offset + size * count > len(raw):
        raise ValueError('ELF_SECTIONS')
    sections = [struct.unpack_from('<IIQQQQIIQQ', raw, offset + i * size) for i in range(count)]
    found = set()
    for section in sections:
        if section[1] != 11:
            continue
        start, length, link, step = section[4], section[5], section[6], section[9]
        if step != 24 or length % step or start + length > len(raw) or link >= count:
            raise ValueError('ELF_SYMBOLS')
        strings = sections[link]
        if strings[1] != 3 or strings[4] + strings[5] > len(raw):
            raise ValueError('ELF_STRINGS')
        table = raw[strings[4]:strings[4] + strings[5]]
        for position in range(start, start + length, step):
            name, info, visibility, defined, _, _ = struct.unpack_from('<IBBHQQ', raw, position)
            if not defined or info >> 4 not in (1, 2) or visibility & 3 not in (0, 3):
                continue
            end = table.find(b'\0', name)
            if name >= len(table) or end < 0:
                raise ValueError('ELF_SYMBOL_NAME')
            symbol = table[name:end].decode('ascii')
            if symbol.startswith('Java_'):
                found.add(symbol)
    return found


def verify_libraries(payload):
    if set(payload) != LIBRARIES:
        raise ValueError('NATIVE_CLOSURE')
    for name, raw in payload.items():
        info = elf_info(raw)
        if info['soname'] != name or info['machine'] != 'AArch64' or info['minimumLoadAlignment'] < 16384 or set(info['needed']) - {
                'libssl.so', 'libcrypto.so', 'libc.so', 'libdl.so', 'libm.so', 'libz.so'}:
            raise ValueError('NATIVE_ELF')
    jni = elf_info(payload['libcloudy-vnc-jni.so'])
    if jni_exports(payload['libcloudy-vnc-jni.so']) != {PREFIX + m for m in METHODS}:
        raise ValueError('NATIVE_EXPORTS')
    if set(jni['needed']) - {'libssl.so', 'libcrypto.so', 'libc.so', 'libdl.so', 'libm.so', 'libz.so'} or not {
            'libssl.so', 'libcrypto.so'} <= set(jni['needed']):
        raise ValueError('NATIVE_DEPENDENCIES')


def verify_staged(output, record):
    for name, digest in record['staged'].items():
        if sha(read(output / relative(name))) != digest:
            raise ValueError('STAGED_IMPORT_CHANGED')
    if read(output / 'input/adapter.c') != namespace_port(read(ORIGINAL / 'adapter.c')):
        raise ValueError('GENERATED_PORT_CHANGED')


def build(output):
    record = stage(output)
    verify_staged(output, record)
    ndk, cmake, ninja = toolchain(record)
    commands = [[str(cmake), '-S', str(MODULE / 'src/main/cpp'), '-B', str(output / 'native-build'),
                 '-G', 'Ninja', '-DCMAKE_MAKE_PROGRAM=' + str(ninja),
                 '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
                 '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-35',
                 '-DINPUT_ROOT=' + str(output / 'input')],
                [str(cmake), '--build', str(output / 'native-build'), '--parallel', '4']]
    for index, command in enumerate(commands):
        selected_native.bounded(command, output, f'compile-{index}',
                                {'timeoutSeconds': 240, 'maximumLogBytes': 8 * 1024 * 1024})
    payload = {'libcloudy-vnc-jni.so': read(output / 'native-build/libcloudy-vnc-jni.so')}
    for name in ('libssl.so', 'libcrypto.so'):
        payload[name] = read(output / 'input/ssl/lib' / name)
    verify_libraries(payload)
    verify_staged(output, record)
    if module_inputs() != record['sources']:
        raise ValueError('SOURCE_DRIFT')
    for name, raw in payload.items():
        save(output, 'jniLibs/arm64-v8a/' + name, raw)
    record.update(status='NATIVE_NAMESPACE_PORT_COMPILED', nativeBuildExecuted=True,
                  stageSha256=sha(read(output / 'stage.json')),
                  commands=commands, libraries={n: sha(b) for n, b in payload.items()})
    record['published'] = {str(p.relative_to(output)): sha(read(p)) for p in sorted(output.rglob('*'))
                           if p.is_file() and p.name not in ('stage.json', 'receipt.json')}
    save(output, 'receipt.json', (json.dumps(record, indent=2) + '\n').encode())


def verify_reuse_sources(record, receipt_sha256, declaration, declaration_sha256):
    # Explicit caller-pinned metadata; this is not a new compilation or a historical receipt edit.
    raw = read(declaration)
    if declaration.absolute().parent != CACHE.absolute() or len(raw) > 64 * 1024 or sha(raw) != declaration_sha256:
        raise ValueError('REUSE_DECLARATION_BINDING')
    def unique(pairs):
        value = {}
        for name, content in pairs:
            if name in value:
                raise ValueError('REUSE_DUPLICATE_FIELD')
            value[name] = content
        return value
    value = json.loads(raw, object_pairs_hook=unique)
    if set(value) != {'schema', 'scope', 'originalReceiptSha256', 'expectedCurrentSources'} or type(value['schema']) is not int or value['schema'] != 1 or value[
            'scope'] != 'OWNER_JAVA_ONLY_NATIVE_REUSE' or value['originalReceiptSha256'] != receipt_sha256:
        raise ValueError('REUSE_DECLARATION_SCHEMA')
    current = module_inputs()
    if value['expectedCurrentSources'] != current or set(current) != set(record['sources']):
        raise ValueError('REUSE_CURRENT_SOURCE_BINDING')
    allowed = {
        'tools/prepare_desktop_vnc.py',  # The current checker differs from its historical build implementation.
        'adapters/desktop/vnc/android/build.gradle.kts',  # Explicit check-entry selection, not a CMake input.
        'adapters/desktop/vnc/android/src/main/java/io/github/baitian6641/cloudy/desktop/vnc/VncEngine.java',
        'adapters/desktop/vnc/android/src/test/java/io/github/baitian6641/cloudy/desktop/vnc/VncEngineTest.java',
    }
    changes = {name: {'originalSha256': record['sources'][name], 'currentSha256': current[name]}
               for name in current if current[name] != record['sources'][name]}
    if not set(changes) <= allowed:
        raise ValueError('REUSE_NATIVE_OR_UNREVIEWED_SOURCE_DRIFT')
    # Toolchain is checked without invoking it. All original import/staged/library checks still follow.
    toolchain(record)
    return {'schema': 1, 'scope': 'OWNER_JAVA_ONLY_NATIVE_REUSE', 'originalReceiptSha256': receipt_sha256,
            'declarationSha256': declaration_sha256, 'currentSources': current, 'sourceChanges': changes,
            'nativeRebuilt': False, 'wholeModuleUnchanged': False, 'releaseClosureApproved': False}


def check(output, reuse=None):
    destination(output)
    receipt_raw = read(output / 'receipt.json')
    record = json.loads(receipt_raw)
    scope = None
    if reuse is not None:
        receipt_sha256, declaration, declaration_sha256 = reuse
        if sha(receipt_raw) != receipt_sha256:
            raise ValueError('REUSE_ORIGINAL_RECEIPT_BINDING')
        scope = verify_reuse_sources(record, receipt_sha256, declaration, declaration_sha256)
    if record['status'] != 'NATIVE_NAMESPACE_PORT_COMPILED' or record['nativeBuildExecuted'] is not True or record[
            'selectedReceiptSha256'] != RECEIPT_SHA or (reuse is None and record['sources'] != module_inputs()):
        raise ValueError('UNACCEPTED_PRODUCT_NATIVE')
    _, imports = selected_inputs()
    if {n: sha(b) for n, b in imports.items()} != record['imports']:
        raise ValueError('SELECTED_IMPORT_BINDING')
    verify_staged(output, record)
    if sha(read(output / 'stage.json')) != record['stageSha256']:
        raise ValueError('STAGE_RECEIPT_CHANGED')
    if any(p.is_symlink() for p in output.rglob('*')):
        raise ValueError('SYMLINK_PREPARED')
    actual = {str(p.relative_to(output)) for p in output.rglob('*') if p.is_file()}
    if actual != set(record['published']) | {'stage.json', 'receipt.json'}:
        raise ValueError('UNREVIEWED_PREPARED_FILE')
    for name, digest in record['published'].items():
        if sha(read(output / relative(name))) != digest:
            raise ValueError('PUBLISHED_NATIVE_CHANGED')
    if {p.name for p in (output / 'jniLibs/arm64-v8a').iterdir()} != LIBRARIES:
        raise ValueError('PACKAGED_NATIVE_CLOSURE')
    payload = {name: read(output / 'jniLibs/arm64-v8a' / name) for name in LIBRARIES}
    if {n: sha(b) for n, b in payload.items()} != record['libraries']:
        raise ValueError('PACKAGED_NATIVE_CHANGED')
    verify_libraries(payload)
    if scope is not None:
        if module_inputs() != scope['currentSources'] or sha(read(output / 'receipt.json')) != scope[
                'originalReceiptSha256'] or sha(read(reuse[1])) != scope['declarationSha256']:
            raise ValueError('REUSE_CHECK_INPUT_DRIFT')
        scope['originalLibraries'] = record['libraries']
        scope['stagedImportsAndPublishedBytesVerified'] = True
        return scope
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--stage', action='store_true')
    mode.add_argument('--prepare', action='store_true')
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--check-native-reuse', action='store_true')
    parser.add_argument('--receipt-sha256')
    parser.add_argument('--reuse-declaration', type=Path)
    parser.add_argument('--reuse-sha256')
    parser.add_argument('--output', type=Path, default=CACHE / 'prepared')
    args = parser.parse_args()
    output = args.output.absolute()
    if args.check_native_reuse:
        if not (args.receipt_sha256 and args.reuse_declaration and args.reuse_sha256):
            parser.error('native reuse requires exact receipt/declaration SHA256 and declaration path')
        print(json.dumps(check(output, (args.receipt_sha256, args.reuse_declaration, args.reuse_sha256)), sort_keys=True))
    elif args.receipt_sha256 or args.reuse_declaration or args.reuse_sha256:
        parser.error('reuse metadata is valid only with explicit native reuse mode')
    elif args.check:
        check(output)
    elif args.prepare:
        build(output)
    else:
        stage(output)


if __name__ == '__main__':
    main()
