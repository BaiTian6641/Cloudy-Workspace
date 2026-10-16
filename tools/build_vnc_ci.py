#!/usr/bin/env python3
"""Build a fresh pinned VNC CI chain; offline verification never implies device acceptance."""

import argparse
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
from types import SimpleNamespace
import xml.etree.ElementTree as ET
import zipfile

import prepare_rdp_native as rdp
import prepare_vnc_native as native
import prepare_vnc_adapter as adapter
import prepare_vnc_android as android

ROOT = native.ROOT
CACHE = ROOT / '.cache/native-vnc-ci'
MODE = 'portable-source-build'
SCOPE = 'hosted-build-only-no-device'
SSL_LIBS = {'libssl.so', 'libcrypto.so'}


def run_name(value):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,48}', value):
        raise ValueError('Invalid portable run name')
    return value


def record(raw):
    return {'bytes': len(raw), 'sha256': native.digest(raw)}


def recipe_inputs():
    """Bind the OpenSSL issuer and reused reviewed RDP recipe, without a full-RDP claim."""
    lock, pins = rdp.current_inputs()
    names = ['tools/build_vnc_ci.py', 'tools/setup_rdp_native.py', 'tools/prepare_vnc_native.py', *native.SHARED,
             'qualification/probes/native-rdp/source-lock.json',
             'qualification/probes/native-vnc/sources.lock.json']
    names += ['qualification/probes/native-rdp/' + p['path'] for p in pins['patches']]
    return lock, {name: record(native.read(ROOT / name)) for name in sorted(set(names))}


def ssl_source_policy(lock):
    vnc = json.loads(native.read(native.LOCK))
    pin = vnc['openssl']['source']
    candidate = next((item for item in lock['dependencyArchives'] if item['archive'] == pin['archive']), None)
    if candidate is None or any(candidate[key] != pin[key] for key in ('bytes', 'sha256')):
        raise ValueError('RDP/VNC OpenSSL source policy differs')
    if any(lock['toolchain'][key] != vnc['toolchain'][key] for key in ('ndk', 'cmake', 'abi', 'androidPlatform')):
        raise ValueError('RDP/VNC toolchain policy differs')
    return {key: pin[key] for key in ('archive', 'bytes', 'sha256')}


def predecessor(path):
    path = native.safe(path).relative_to(ROOT)
    return {'path': path.as_posix(), **record(native.read(ROOT / path))}


def predecessor_path(item, parent):
    path = native.safe(ROOT / native.relative(item['path']))
    if path.parent.parent != parent or path.name != 'receipt.json':
        raise ValueError('Predecessor outside expected cache')
    native.verified(native.read(path), item)
    return path.parent


def validate_ssl_files(files):
    expected_libs = {'lib/' + name for name in SSL_LIBS}
    if not expected_libs <= files.keys() or 'include/openssl/ssl.h' not in files:
        raise ValueError('Incomplete portable OpenSSL closure')
    if any(name not in expected_libs and not (name.startswith('include/openssl/') and name.endswith('.h'))
           for name in files):
        raise ValueError('Unexpected portable OpenSSL file')
    for name in expected_libs:
        info = rdp.elf_info(files[name])
        if info['soname'] != Path(name).name or not set(info['needed']) <= SSL_LIBS | {'libc.so', 'libdl.so', 'libm.so'}:
            raise ValueError('Portable OpenSSL ELF closure differs')


def check_openssl(run, receipt=None):
    if receipt is None:
        receipt = json.loads(native.read(run / 'receipt.json'))
    lock, inputs = recipe_inputs()
    if (receipt.get('schemaVersion') != 1 or receipt.get('mode') != MODE or receipt.get('scope') != 'openssl-target-only'
            or receipt.get('exitCode') != 0 or receipt.get('inputs') != inputs
            or receipt.get('toolchainPolicy') != lock['toolchain']):
        raise ValueError('Portable OpenSSL provenance differs')
    if receipt.get('opensslSource') != ssl_source_policy(lock):
        raise ValueError('Portable OpenSSL source policy differs')
    archive = run / 'FreeRDP.tar.gz'
    native.verified(native.read(archive), lock['source'])
    for name, pin in receipt['evidence'].items():
        native.verified(native.read(run / native.relative(name)), pin)
    result = json.loads(native.read(run / 'openssl-build.json'))
    command = result['command']
    if result.get('exitCode') != 0 or command[-4:] != ['--target', 'openssl', '--parallel', '4']:
        raise ValueError('Not a successful bounded OpenSSL-only target')
    files = {name: native.read(run / 'prepared' / name) for name in native.hashes(run / 'prepared')}
    if {name: record(raw) for name, raw in files.items()} != receipt['files']:
        raise ValueError('Portable OpenSSL prepared bytes differ')
    validate_ssl_files(files)
    return receipt


def portable_ssl_import(receipt_path, files):
    run = native.safe(receipt_path).parent
    if run.parent != CACHE or not run.name.startswith('openssl-'):
        raise ValueError('Portable OpenSSL receipt outside CI cache')
    receipt = check_openssl(run)
    if {name: record(raw) for name, raw in files.items()} != receipt['files']:
        raise ValueError('Imported OpenSSL bytes differ from source build')
    return predecessor(receipt_path)


def tool_records(sdk, lock):
    from setup_rdp_native import properties
    for kind in ('ndk', 'cmake'):
        if properties(sdk / kind / lock['toolchain'][kind] / 'source.properties').get('Pkg.Revision') != lock['toolchain'][kind]:
            raise ValueError('VNC SDK toolchain revision differs')
    cmake = sdk / 'cmake' / lock['toolchain']['cmake'] / 'bin/cmake'
    return {str(p.relative_to(sdk)): record(native.read(p.resolve(strict=True))) for p in (
        sdk / 'ndk' / lock['toolchain']['ndk'] / 'source.properties',
        sdk / 'cmake' / lock['toolchain']['cmake'] / 'source.properties', cmake,
        cmake.parent / 'ninja',
        sdk / 'ndk' / lock['toolchain']['ndk'] / 'toolchains/llvm/prebuilt/linux-x86_64/bin/clang')}


def build_openssl(args, run):
    lock, inputs = recipe_inputs()
    run.mkdir(parents=True, exist_ok=False)
    limits = {'timeoutSeconds': 1800, 'maximumLogBytes': 16 * 1024 * 1024}
    try:
        tools = tool_records(args.sdk, lock)
        native.bounded([sys.executable, str(ROOT / 'tools/setup_rdp_native.py'), '--sdk', str(args.sdk),
                        '--stage', str(args.rdp_stage)], run, 'setup', limits)
        source = args.rdp_stage / ('FreeRDP-' + lock['source']['sourceCommit'])
        build = args.rdp_stage / 'build'
        archive = ROOT / '.cache/native-rdp/downloads' / (source.name + '.tar.gz')
        rdp.verify_source(source, archive, lock)
        rdp.build_identity(build, source, lock, require_inner=False)
        cmake = args.sdk / 'cmake' / lock['toolchain']['cmake'] / 'bin/cmake'
        # The upstream external recipe is patched to make -j4; the outer target also has four jobs.
        native.bounded([str(cmake), '--build', str(build), '--target', 'openssl', '--parallel', '4'],
                       run, 'openssl-build', limits)
        rdp.verify_source(source, archive, lock)
        rdp.build_identity(build, source, lock, require_inner=False)
        if recipe_inputs()[1] != inputs:
            raise ValueError('OpenSSL recipe changed during build')
        prefix = source / 'client/Android/Studio/freeRDPCore/src/main/jniLibs/arm64-v8a'
        files = {'lib/' + name: native.read(prefix / 'lib' / name) for name in SSL_LIBS}
        for path in sorted((prefix / 'include/openssl').rglob('*.h')):
            files[path.relative_to(prefix).as_posix()] = native.read(path)
        validate_ssl_files(files)
        for name, raw in files.items():
            target = run / 'prepared' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        shutil.copyfile(archive, run / 'FreeRDP.tar.gz')
        if tool_records(args.sdk, lock) != tools:
            raise ValueError('OpenSSL toolchain changed during build')
        result = {'schemaVersion': 1, 'mode': MODE, 'scope': 'openssl-target-only', 'exitCode': 0,
                  'inputs': inputs, 'toolchainPolicy': lock['toolchain'], 'toolBinaries': tools,
                  'opensslSource': ssl_source_policy(lock),
                  'files': {name: record(raw) for name, raw in files.items()},
                  'evidence': {name: record(native.read(run / name)) for name in
                               ('setup.json', 'setup.log', 'openssl-build.json', 'openssl-build.log')}}
        check_openssl(run, result)
        native.save(run / 'receipt.json', result)
        return result
    except BaseException as error:
        native.save(run / 'failure.json', {'scope': 'incomplete-openssl-target-only', 'class': type(error).__name__})
        raise


def check_chain(run):
    receipt = json.loads(native.read(run / 'receipt.json'))
    if receipt.get('mode') != MODE or receipt.get('scope') != SCOPE or receipt.get('exitCode') != 0:
        raise ValueError('Invalid CI orchestration receipt')
    name = run_name(run.name)
    expected = {'openssl': CACHE / ('openssl-' + name), 'native': native.CACHE / ('ci-' + name),
                'adapter': adapter.CACHE / ('ci-' + name), 'android': android.CACHE / name}
    for key, path in expected.items():
        file = 'cloudy-preparation.json' if key == 'android' else 'receipt.json'
        if receipt['chain'][key] != predecessor(path / file):
            raise ValueError('CI chain receipt changed')
    check_openssl(expected['openssl'])
    native.check(expected['native'])
    adapter.check(expected['adapter'])
    android.check(expected['android'])
    return receipt


def build(args):
    name = run_name(args.run)
    run = native.safe(CACHE / name)
    run.mkdir(parents=True, exist_ok=False)
    try:
        ssl = CACHE / ('openssl-' + name)
        build_openssl(args, ssl)
        base_run = native.CACHE / ('ci-' + name)
        native.build(SimpleNamespace(sdk=args.sdk, openssl_prefix=ssl / 'prepared',
                                    openssl_receipt=ssl / 'receipt.json', source_build=True), base_run)
        adapter_run = adapter.CACHE / ('ci-' + name)
        adapter.build(SimpleNamespace(sdk=args.sdk, base_run=base_run, source_build=True), adapter_run)
        android_stage = android.CACHE / name
        android.prepare(android_stage, args.sdk, adapter_run=adapter_run)
        receipt = {'schemaVersion': 1, 'mode': MODE, 'scope': SCOPE, 'exitCode': 0,
                   'chain': {key: predecessor(path / filename) for key, path, filename in (
                       ('openssl', ssl, 'receipt.json'), ('native', base_run, 'receipt.json'),
                       ('adapter', adapter_run, 'receipt.json'), ('android', android_stage, 'cloudy-preparation.json'))}}
        native.save(run / 'receipt.json', receipt)
        check_chain(run)
    except BaseException as error:
        (run / 'receipt.json').unlink(missing_ok=True)
        native.save(run / 'failure.json', {'scope': 'incomplete-no-accepted-ci-receipt', 'class': type(error).__name__})
        raise
    return receipt


def asset_pins(expected):
    assets = {}
    for name, pin in expected.items():
        for prefix in (android.GENERATED + 'nativeNotices/', 'app/src/main/assets/'):
            if name.startswith(prefix):
                target = 'assets/' + name.removeprefix(prefix)
                if target in assets:
                    raise ValueError('Duplicate authored/generated asset')
                assets[target] = pin
    return assets


def audit_assets(bundle, expected):
    assets = asset_pins(expected)
    actual = {name for name in bundle.namelist() if name.startswith('assets/') and not name.endswith('/')}
    if actual != set(assets):
        raise ValueError('APK asset closure differs')
    for name, pin in assets.items():
        native.verified(bundle.read(name), pin)


def audit_manifest(raw):
    """Merged XML policy only; caller must not label it binary APK manifest decoding."""
    element = ET.fromstring(raw)
    ns = '{http://schemas.android.com/apk/res/android}'
    lock = json.loads(native.read(android.OVERLAY / 'packaging.lock.json'))['application']
    if (element.get('package') != lock['id'] or element.get(ns + 'versionCode') != str(lock['versionCode'])
            or element.get(ns + 'versionName') != lock['versionName']):
        raise ValueError('Merged package/version differs')
    sdk = element.find('uses-sdk')
    if sdk is None or sdk.get(ns + 'minSdkVersion') != str(lock['minSdk']) or sdk.get(ns + 'targetSdkVersion') != str(lock['targetSdk']):
        raise ValueError('Merged SDK policy differs')
    permissions = [item for item in element if item.tag.startswith('uses-permission')]
    if (len(permissions) != 2 or any(item.tag != 'uses-permission' for item in permissions)
            or {item.get(ns + 'name') for item in permissions} != {
                'android.permission.INTERNET', 'android.permission.ACCESS_LOCAL_NETWORK'}):
        raise ValueError('Merged permission closure differs')
    features = element.findall('uses-feature')
    if (len(features) != 2 or {item.get(ns + 'name') for item in features} != {
            'android.hardware.touchscreen', 'android.hardware.faketouch'}
            or any(item.get(ns + 'required') != 'false' for item in features)):
        raise ValueError('Merged desktop feature policy differs')
    apps = element.findall('application')
    if len(apps) != 1:
        raise ValueError('Merged application closure differs')
    app = apps[0]
    flags = {'allowBackup': 'false', 'usesCleartextTraffic': 'false', 'extractNativeLibs': 'false',
             'dataExtractionRules': '@xml/data_extraction_rules'}
    if any(app.get(ns + name) != value for name, value in flags.items()) or app.get(ns + 'networkSecurityConfig') is not None:
        raise ValueError('Merged security/packaging flags differ')
    components = [item for item in app if item.tag in {'activity', 'activity-alias', 'service', 'provider', 'receiver'}]
    if (len(components) != 1 or components[0].tag != 'activity'
            or components[0].get(ns + 'name') != 'dev.cloudy.qualification.vnc.VncActivity'
            or components[0].get(ns + 'exported') != 'true'
            or components[0].get(ns + 'resizeableActivity') != 'true'):
        raise ValueError('Merged component closure differs')
    return {'permissions': sorted(item.get(ns + 'name') for item in permissions),
            'components': ['dev.cloudy.qualification.vnc.VncActivity'], 'applicationFlags': flags}


def audit_apk(run, apk):
    """Inspect real packaged bytes/reports; no APK execution or signature certification."""
    receipt = check_chain(run)
    stage = android.CACHE / run.name
    if apk.absolute() != stage / 'app/build/outputs/apk/debug/app-debug.apk':
        raise ValueError('Expected this run debug APK')
    prepared = android.check(stage)
    expected = prepared['stagedFiles']
    with zipfile.ZipFile(apk) as bundle:
        if len(bundle.namelist()) != len(set(bundle.namelist())):
            raise ValueError('Duplicate APK ZIP entry')
        libs = {name for name in bundle.namelist() if name.endswith('.so')}
        if libs != {'lib/arm64-v8a/' + name for name in android.LIBRARIES}:
            raise ValueError('APK native closure differs')
        for name in libs:
            raw = bundle.read(name)
            pin = expected[android.GENERATED + 'nativeLibraries/' + name.removeprefix('lib/')]
            native.verified(raw, pin)
            info = bundle.getinfo(name)
            with apk.open('rb') as stream:
                stream.seek(info.header_offset + 26)
                length, extra = struct.unpack('<HH', stream.read(4))
            if info.compress_type != zipfile.ZIP_STORED or (info.header_offset + 30 + length + extra) % 16384:
                raise ValueError('APK native ZIP alignment differs')
        audit_assets(bundle, expected)
    report = stage / 'app/build/reports/cloudy-runtime-dependencies.json'
    if json.loads(native.read(report)) != []:
        raise ValueError('Unexpected VNC Java runtime closure')
    lint = stage / 'app/build/reports/lint-results-debug.xml'
    issues = ET.fromstring(native.read(lint))
    if len(issues):
        raise ValueError('VNC Lint report has findings')
    totals = {name: 0 for name in ('tests', 'failures', 'errors', 'skipped')}
    reports = sorted((stage / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    for path in reports:
        element = ET.fromstring(native.read(path))
        for key in totals:
            totals[key] += int(element.get(key, '0'))
    if not reports or totals['tests'] < 10 or any(totals[key] for key in ('failures', 'errors', 'skipped')):
        raise ValueError('VNC JVM results absent or failed')
    manifest = stage / 'app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml'
    manifest_policy = audit_manifest(native.read(manifest))
    result = {'schemaVersion': 1, 'mode': MODE, 'scope': SCOPE, 'chainReceipt': record(native.read(run / 'receipt.json')),
              'apk': record(native.read(apk)), 'libraries': sorted(libs), 'jvm': totals,
              'runtimeDependencies': record(native.read(report)), 'lint': record(native.read(lint)),
              'mergedManifest': record(native.read(manifest)), 'mergedManifestPolicy': manifest_policy,
              'limitations': ['No device/server/physical acceptance', 'ZIP alignment is not loader/runtime evidence',
                              'Merged XML checked; compiled binary manifest not decoded by this audit',
                              'APK signature must be checked separately with selected SDK apksigner'],
              'chain': receipt['chain']}
    output = run / 'apk-audit.json'
    if output.exists() and json.loads(native.read(output)) != result:
        raise ValueError('Preserve existing differing APK audit')
    native.save(output, result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--build', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--run', required=True)
    parser.add_argument('--sdk', type=Path, default=ROOT / '.cache/android-sdk')
    parser.add_argument('--rdp-stage', type=Path)
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    try:
        run_name(args.run)
        if args.build:
            if args.rdp_stage is None or args.apk is not None:
                raise ValueError('Build requires fresh --rdp-stage and no --apk')
            args.sdk = args.sdk.resolve(strict=True)
            args.rdp_stage = native.safe(args.rdp_stage.absolute())
            build(args)
        else:
            run = native.safe(CACHE / args.run)
            check_chain(run)
            if args.apk:
                audit_apk(run, args.apk)
        print(json.dumps({'status': 'passed', 'mode': MODE, 'scope': SCOPE}))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        parser.exit(1, f'VNC CI preparation failed: {error}\n')


if __name__ == '__main__':
    main()
