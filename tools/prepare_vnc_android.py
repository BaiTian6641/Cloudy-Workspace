#!/usr/bin/env python3
"""Import the exact accepted VNC JNI closure into a fresh finite diagnostic APK stage."""

import argparse
import hashlib
import json
from pathlib import Path
import re

import prepare_vnc_adapter as native

ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / 'qualification/probes/native-vnc/android'
CACHE = ROOT / '.cache/native-vnc-android'
WRAPPER = ROOT / 'qualification/probes/device-baseline'
GENERATED = 'app/build/generated/cloudy/'
LIBRARIES = {'libcloudy-vnc-jni.so', 'libssl.so', 'libcrypto.so'}
BRIDGE = 'dev/cloudy/qualification/vnc/VncBridge.java'


def record(raw):
    return {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}


def inputs():
    paths = [Path(__file__).resolve(), WRAPPER / 'gradlew', WRAPPER / 'gradle/wrapper/gradle-wrapper.jar']
    for path in sorted(OVERLAY.rglob('*')):
        native.base.safe(path)
        if path.is_file():
            paths.append(path)
    contents = {path.relative_to(ROOT).as_posix(): native.base.read(path) for path in paths}
    required = ('build.gradle', 'settings.gradle', 'gradle.properties', 'packaging.lock.json',
                'app/build.gradle', 'app/src/main/AndroidManifest.xml',
                'app/src/main/java/dev/cloudy/qualification/vnc/VncActivity.java',
                'app/src/main/res/values/cloudy_strings.xml')
    if any(str(OVERLAY.relative_to(ROOT) / name) not in contents for name in required):
        raise ValueError('Required real Android application source/configuration missing')
    if any(name.endswith('/VncBridge.java') or name.endswith('.so') for name in contents):
        raise ValueError('JNI bridge and libraries must come only from verified adapter receipt')
    return contents


def validate_libraries(lock, receipt, payload):
    if set(payload) != LIBRARIES or set(lock['nativeLibraries']) != LIBRARIES or set(receipt['libraries']) != LIBRARIES:
        raise ValueError('Exact patched JNI/SSL/crypto closure required')
    for name, raw in payload.items():
        if record(raw) != lock['nativeLibraries'][name]:
            raise ValueError('Pinned native library changed: ' + name)
        item = receipt['libraries'][name]
        if record(raw) != {key: item[key] for key in ('bytes', 'sha256')}:
            raise ValueError('Native receipt library mismatch: ' + name)


def property_value(value):
    # Internal spaces are literal property values; escaping them trips Android Lint.
    return str(value).replace('\\', '\\\\').replace(':', '\\:')


def expected_files(contents):
    prefix = OVERLAY.relative_to(ROOT).as_posix() + '/'
    lock = json.loads(contents[prefix + 'packaging.lock.json'])
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', lock['nativeRun']):
        raise ValueError('Invalid native run name')
    run = native.CACHE / lock['nativeRun']
    if record(native.base.read(run / 'receipt.json')) != lock['nativeReceipt']:
        raise ValueError('Pinned accepted adapter receipt changed')
    receipt = native.check(run)
    if receipt['buildRef'] != lock['nativeBuildRef']:
        raise ValueError('Native build reference mismatch')
    payload = {name: native.base.read(run / 'prepared/nativeLibraries/arm64-v8a' / name) for name in LIBRARIES}
    validate_libraries(lock, receipt, payload)
    files = {}
    for name, raw in contents.items():
        if name.startswith(prefix):
            target = name[len(prefix):]
            if target == 'gradle-wrapper.properties':
                target = 'gradle/wrapper/gradle-wrapper.properties'
            files[target] = raw
    files['gradle.properties'] += ('\ncloudyRepositoryRoot=' + property_value(ROOT) + '\n').encode()
    for name, pin in lock['wrapper'].items():
        raw = contents[(WRAPPER.relative_to(ROOT) / name).as_posix()]
        if record(raw) != pin:
            raise ValueError('Pinned Gradle wrapper changed')
        files[name] = raw
    for name, raw in payload.items():
        files[GENERATED + 'nativeLibraries/arm64-v8a/' + name] = raw
    bridge = native.base.read(run / 'prepared' / native.base.relative(lock['bridge']['source']))
    if record(bridge) != {key: lock['bridge'][key] for key in ('bytes', 'sha256')}:
        raise ValueError('Accepted JNI bridge changed')
    files[GENERATED + 'java/' + BRIDGE] = bridge
    for item in lock['notices']:
        raw = native.base.read(run / 'prepared' / native.base.relative(item['source']))
        if record(raw) != {key: item[key] for key in ('bytes', 'sha256')}:
            raise ValueError('Accepted native notice changed')
        files[GENERATED + 'nativeNotices/' + native.base.relative(item['asset']).as_posix()] = raw
    metadata = {'schemaVersion': 1, 'buildRef': lock['buildRef'], 'nativeBuildRef': receipt['buildRef'],
                'nativeReceipt': lock['nativeReceipt'], 'libraries': receipt['libraries'],
                'scope': 'Private diagnostic import; no server, APK runtime or final source-distribution qualification'}
    files[GENERATED + 'nativeNotices/native-build.json'] = (json.dumps(metadata, indent=2) + '\n').encode()
    return files, lock


def verify_tree(stage, expected):
    for name, pin in expected.items():
        if record(native.base.read(stage / native.base.relative(name))) != pin:
            raise ValueError('Staged input changed: ' + name)
    for path in stage.rglob('*'):
        name = path.relative_to(stage).as_posix()
        native.base.safe(path)
        if name.startswith(('.gradle/', 'build/', 'app/build/')) and not name.startswith(GENERATED):
            continue
        if path.is_file() and name not in expected and name not in {'cloudy-preparation.json', 'cloudy-failure.json'}:
            raise ValueError('Unreviewed Android source/build logic/payload: ' + name)


def unchanged(frozen):
    if inputs() != frozen:
        raise ValueError('Android inputs changed during preparation; publication rejected')


def check(stage):
    saved = json.loads(native.base.read(stage / 'cloudy-preparation.json'))
    frozen = inputs()
    if saved['inputs'] != {name: record(raw) for name, raw in frozen.items()}:
        raise ValueError('Current Android source differs from preparation receipt')
    files, lock = expected_files(frozen)
    if saved['nativeReceipt'] != lock['nativeReceipt']:
        raise ValueError('Native receipt binding changed')
    expected = {name: record(raw) for name, raw in files.items()}
    # SDK path is local operator configuration, not a portable public artifact.
    sdk_raw = native.base.read(stage / 'local.properties')
    expected['local.properties'] = record(sdk_raw)
    if saved['stagedFiles'] != expected:
        raise ValueError('Staged manifest differs from reconstructed reviewed inputs')
    verify_tree(stage, expected)
    unchanged(frozen)
    return saved


def prepare(stage, sdk):
    frozen = inputs()
    files, lock = expected_files(frozen)
    sdk = native.base.safe(sdk.absolute())
    if not (sdk / 'platforms/android-37.0/android.jar').is_file():
        raise ValueError('Existing Android SDK37 required; no installation is performed')
    files['local.properties'] = ('sdk.dir=' + property_value(sdk) + '\n').encode()
    stage.mkdir(parents=True, exist_ok=False)
    try:
        for name, raw in files.items():
            target = native.base.safe(stage / native.base.relative(name))
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        (stage / 'gradlew').chmod(0o755)
        unchanged(frozen)
        receipt = {'schemaVersion': 1, 'buildRef': lock['buildRef'], 'nativeReceipt': lock['nativeReceipt'],
                   'inputs': {name: record(raw) for name, raw in frozen.items()},
                   'stagedFiles': {name: record(raw) for name, raw in files.items()}}
        (stage / 'cloudy-preparation.json').write_text(json.dumps(receipt, indent=2) + '\n')
        check(stage)
    except BaseException as error:
        (stage / 'cloudy-failure.json').write_text(json.dumps({'failureClass': type(error).__name__}) + '\n')
        raise
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--prepare', action='store_true')
    group.add_argument('--check', action='store_true')
    parser.add_argument('--stage', required=True)
    parser.add_argument('--sdk', type=Path, default=ROOT / '.cache/android-sdk')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', args.stage):
        parser.error('Stage must be a simple lowercase fresh name')
    try:
        stage = native.base.safe(CACHE / args.stage)
        result = prepare(stage, args.sdk) if args.prepare else check(stage)
        print(json.dumps({'status': 'prepared' if args.prepare else 'verified', 'files': len(result['stagedFiles'])}))
    except (OSError, ValueError, KeyError) as error:
        parser.exit(1, f'VNC Android preparation failed: {error}\n')


if __name__ == '__main__':
    main()
