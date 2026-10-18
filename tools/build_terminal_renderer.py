#!/usr/bin/env python3
"""Build the reviewed xterm renderer from pinned TypeScript sources.

Build and check never use the network, install packages, or run package scripts.
They read the pinned @xterm/xterm archive, reviewed unified diff, and pinned
esbuild binary from the local cache. The explicit --fetch-compiler setup action
may fetch only the reviewed compiler archive; corrupt cached inputs are never
silently replaced. Sources are inspected with bounded, link-free extraction and
bundled as a browser IIFE bound to ``globalThis.Terminal``.

``--candidate`` compiles into the ignored source-build cache and prints the
exact output bytes/hash for review. Normal product preparation supplies the
reviewed output pin and fails closed on missing or stale inputs. ``--check``
verifies all pins and the generated bundle without replaying the patch,
compiling, writing, or using the network.
"""

import argparse
import base64
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import urllib.error


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))
import prepare_terminal_assets as ASSETS  # noqa: E402
import prepare_rdp_native as RDP  # noqa: E402


MANIFEST = ROOT / "shared/android/terminal/renderer-build.json"
CACHE_ROOT = ROOT / ".cache/terminal-assets"
PRODUCT_OUTPUT_ROOT = ROOT / "shared/android/terminal/build/generated/terminalAssets"
CANDIDATE_PATH = "source-build/candidate/xterm.js"
SOURCE_VERSION = "6.1.0-beta.315"
SOURCE_COMMIT = "991857a1b1cacf06f9531ba74a6e927a7a6398bb"
COMPILER_NAME = "@esbuild/linux-x64"
COMPILER_VERSION = "0.28.0"
ENTRY_SOURCE = (
    "import { Terminal } from './src/browser/public/Terminal';\n"
    "(globalThis as any).Terminal = Terminal;\n"
)
ENTRY_SHA256 = "9cbe7cc83383d4912fc3b0e69a2af09b18b5e83f4f007d2446a01e70b6528a02"
MANIFEST_LIMIT = ASSETS.MANIFEST_LIMIT
MAX_ARCHIVE_BYTES = ASSETS.MAX_ARCHIVE_BYTES
MAX_EXPANDED_BYTES = ASSETS.MAX_EXPANDED_BYTES
MAX_MEMBER_BYTES = ASSETS.MAX_MEMBER_BYTES
MAX_MEMBERS = ASSETS.MAX_MEMBERS
MAX_SOURCE_BYTES = 8 * 1024 * 1024
MAX_PATCH_BYTES = 256 * 1024
MAX_BUNDLE_BYTES = 4 * 1024 * 1024
MAX_BUILD_LOG_BYTES = 64 * 1024
MAX_COMPILER_EXPANDED_BYTES = 16 * 1024 * 1024
MAX_COMPILER_MEMBER_BYTES = 16 * 1024 * 1024
COMPILE_TIMEOUT_SECONDS = 120
COMPILER_MEMBERS = {
    "package/bin/esbuild",
    "package/package.json",
    "package/README.md",
}
SOURCE_ENTRY = "package/src/browser/public/Terminal.ts"
SOURCE_LICENSE = "package/LICENSE"
SOURCE_PACKAGE_JSON = "package/package.json"
TSCONFIG = {
    "compilerOptions": {
        "baseUrl": ".",
        "paths": {
            "common/*": ["src/common/*"],
            "browser/*": ["src/browser/*"],
        },
        "experimentalDecorators": True,
        "useDefineForClassFields": False,
    },
}
TSCONFIG_BYTES = (json.dumps(TSCONFIG, indent=2, sort_keys=True) + "\n").encode("utf-8")
BANNER = """/**
 * Cloudy source build of @xterm/xterm {version}.
 * Source archive SHA-256: {archive_sha256}
 * Authored patch: {patch_file} (SHA-256: {patch_sha256})
 * Compiler: {compiler_name} {compiler_version} (SHA-256: {binary_sha256})
 * Upstream license: terminal/notices/xterm-LICENSE.txt (MIT).
 */"""

# Reuse the reviewed terminal-asset validators and destination writer so the
# diagnostic and product paths cannot drift. The shared module is registered
# before this module is loaded, so AssetError identity is preserved.
BuildError = ASSETS.AssetError
require = ASSETS.require
integer = ASSETS.integer
sha256 = ASSETS.sha256
valid_digest = ASSETS.valid_digest
relative_path = ASSETS.relative_path
parse_json = ASSETS.parse_json
read_regular = ASSETS.read_regular
no_link_components = ASSETS.no_link_components
atomic_write = ASSETS.atomic_write
verify_blob = ASSETS.verify_blob


def validate_manifest(manifest):
    require(isinstance(manifest, dict) and type(manifest.get("schemaVersion")) is int
            and manifest["schemaVersion"] == 1, "Unsupported renderer build manifest")
    require(set(manifest) <= {"schemaVersion", "source", "patch", "compiler", "build", "output"},
            "Unapproved renderer build manifest field")
    require({"schemaVersion", "source", "patch", "compiler", "build"} <= set(manifest),
            "Renderer build manifest is missing a required section")

    source = manifest["source"]
    require(isinstance(source, dict) and set(source) == {
        "name", "version", "upstreamCommit", "license", "cacheFile", "archiveUrl",
        "archiveBytes", "archiveSha256", "integrity", "packageJsonSha256",
        "licensePath", "licenseOutputPath", "licenseBytes", "licenseSha256",
    }, "Invalid renderer source record")
    require(source.get("name") == "@xterm/xterm" and source.get("version") == SOURCE_VERSION
            and source.get("upstreamCommit") == SOURCE_COMMIT and source.get("license") == "MIT",
            "Renderer source pin changed")
    require(source.get("cacheFile") == f"xterm-{SOURCE_VERSION}.tgz", "Invalid renderer cache filename")
    require(source.get("archiveUrl") ==
            f"https://registry.npmjs.org/@xterm/xterm/-/xterm-{SOURCE_VERSION}.tgz",
            "Unapproved renderer archive URL")
    require(integer(source.get("archiveBytes"), 1, MAX_ARCHIVE_BYTES), "Renderer archive size is unbounded")
    require(valid_digest(source.get("archiveSha256")) and valid_digest(source.get("packageJsonSha256")),
            "Invalid renderer archive/package metadata digest")
    integrity = source.get("integrity")
    require(isinstance(integrity, str) and integrity.startswith("sha512-"), "Missing renderer SHA-512 pin")
    try:
        digest = base64.b64decode(integrity[7:], validate=True)
    except ValueError as failure:
        raise BuildError("Malformed renderer SHA-512 pin") from failure
    require(len(digest) == 64 and base64.b64encode(digest).decode("ascii") == integrity[7:],
            "Invalid renderer SHA-512 pin")
    require(source.get("licensePath") == SOURCE_LICENSE, "Invalid renderer license path")
    require(source.get("licenseOutputPath") == "terminal/notices/xterm-LICENSE.txt",
            "Renderer license output path changed")
    require(integer(source.get("licenseBytes"), 1, MAX_MEMBER_BYTES)
            and valid_digest(source.get("licenseSha256")), "Invalid renderer license pin")

    patch = manifest["patch"]
    require(isinstance(patch, dict) and set(patch) == {"file", "sha256", "files"},
            "Invalid renderer patch record")
    patch_file = relative_path(patch.get("file"))
    require(patch_file.startswith("tools/patches/") and patch_file.endswith(".patch"),
            "Renderer patch is outside tools/patches")
    require(valid_digest(patch.get("sha256")), "Invalid renderer patch digest")
    patch_files = patch.get("files")
    require(isinstance(patch_files, list) and 0 < len(patch_files) <= 8, "Invalid renderer patch file list")
    seen_patch_files = set()
    for value in patch_files:
        require(isinstance(value, str), "Invalid renderer patch source path")
        relative_path(value)
        require(value.startswith("package/src/") and value not in seen_patch_files,
                "Renderer patch targets a non-source or duplicate path")
        seen_patch_files.add(value)

    compiler = manifest["compiler"]
    require(isinstance(compiler, dict) and set(compiler) == {
        "name", "version", "cacheFile", "archiveUrl", "archiveBytes", "archiveSha256",
        "integrity", "binaryMember", "binaryBytes", "binarySha256",
    }, "Invalid renderer compiler record")
    require(compiler.get("name") == COMPILER_NAME and compiler.get("version") == COMPILER_VERSION,
            "Renderer compiler pin changed")
    require(compiler.get("cacheFile") == f"source-build/linux-x64-{COMPILER_VERSION}.tgz",
            "Invalid compiler cache filename")
    require(compiler.get("archiveUrl") ==
            f"https://registry.npmjs.org/@esbuild/linux-x64/-/linux-x64-{COMPILER_VERSION}.tgz",
            "Unapproved compiler archive URL")
    require(integer(compiler.get("archiveBytes"), 1, MAX_ARCHIVE_BYTES), "Compiler archive size is unbounded")
    require(valid_digest(compiler.get("archiveSha256")), "Invalid compiler archive digest")
    compiler_integrity = compiler.get("integrity")
    require(isinstance(compiler_integrity, str) and compiler_integrity.startswith("sha512-"),
            "Missing compiler SHA-512 pin")
    try:
        compiler_digest = base64.b64decode(compiler_integrity[7:], validate=True)
    except ValueError as failure:
        raise BuildError("Malformed compiler SHA-512 pin") from failure
    require(len(compiler_digest) == 64
            and base64.b64encode(compiler_digest).decode("ascii") == compiler_integrity[7:],
            "Invalid compiler SHA-512 pin")
    require(compiler.get("binaryMember") == "package/bin/esbuild", "Invalid compiler binary path")
    require(integer(compiler.get("binaryBytes"), 1, MAX_COMPILER_MEMBER_BYTES)
            and valid_digest(compiler.get("binarySha256")), "Invalid compiler binary pin")

    build = manifest["build"]
    require(isinstance(build, dict) and set(build) ==
            {"entry", "entrySha256", "target", "format", "platform"}, "Invalid renderer build record")
    require(build.get("entry") == ENTRY_SOURCE and build.get("entrySha256") == ENTRY_SHA256
            and sha256(build["entry"].encode("utf-8")) == ENTRY_SHA256,
            "Renderer entry source changed")
    require(build.get("target") == "es2020" and build.get("format") == "iife"
            and build.get("platform") == "browser", "Renderer build target changed")

    if "output" in manifest:
        output = manifest["output"]
        require(isinstance(output, dict) and set(output) == {"path", "bytes", "sha256"},
                "Invalid reviewed renderer output record")
        require(relative_path(output.get("path")) == "terminal/vendor/xterm.js",
                "Reviewed renderer output path changed")
        require(integer(output.get("bytes"), 1, MAX_BUNDLE_BYTES)
                and valid_digest(output.get("sha256")), "Invalid reviewed renderer output pin")
    return manifest


def load_manifest(path=MANIFEST, *, require_output=False):
    manifest = validate_manifest(parse_json(read_regular(path, MANIFEST_LIMIT)))
    if require_output:
        require("output" in manifest, "Reviewed renderer output pin is missing")
    return manifest


def read_cached_archive(record, cache, label):
    path = cache / record["cacheFile"]
    relative_path(record["cacheFile"])
    no_link_components(path.parent)
    data = read_regular(path, MAX_ARCHIVE_BYTES)
    verify_blob(data, record["archiveBytes"], record["archiveSha256"], label)
    integrity = "sha512-" + base64.b64encode(hashlib.sha512(data).digest()).decode("ascii")
    require(integrity == record["integrity"], f"{label} SHA-512 mismatch")
    return data


def decompress(archive, limit, label):
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(archive)) as compressed:
            expanded = compressed.read(limit + 1)
    except (OSError, EOFError) as failure:
        raise BuildError(f"Invalid compressed {label}") from failure
    require(len(expanded) <= limit, f"{label} decompression exceeds bound")
    return expanded


def read_tar_member(archive_file, member):
    stream = archive_file.extractfile(member)
    require(stream is not None, "Archive member has no payload")
    with stream:
        data = stream.read(member.size + 1)
    require(len(data) == member.size, "Truncated archive member")
    return data


def inspect_source_archive(manifest, archive):
    """Inspect the complete archive and materialize only package source bytes."""
    source = manifest["source"]
    expanded = decompress(archive, MAX_EXPANDED_BYTES, "renderer source archive")
    sources = {}
    license_data = None
    package_json = None
    seen = set()
    file_bytes = 0
    source_bytes = 0
    try:
        with tarfile.open(fileobj=io.BytesIO(expanded), mode="r:") as archive_file:
            for member in archive_file:
                name = member.name.rstrip("/") if member.isdir() else member.name
                require(name == "package" or name.startswith("package/"), "Archive path outside package")
                relative_path(name)
                require(name not in seen, "Duplicate archive member")
                seen.add(name)
                require(len(seen) <= MAX_MEMBERS, "Too many archive members")
                require(member.type in (tarfile.REGTYPE, tarfile.AREGTYPE, tarfile.DIRTYPE)
                        and not member.pax_headers, "Links/special/PAX archive members are forbidden")
                require(integer(member.size, 0, MAX_MEMBER_BYTES), "Archive member exceeds bound")
                require(not member.isdir() or member.size == 0, "Directory carries unexpected payload")
                file_bytes += member.size
                require(file_bytes <= MAX_EXPANDED_BYTES, "Archive file bytes exceed bound")
                if name == SOURCE_PACKAGE_JSON or name == SOURCE_LICENSE or name.startswith("package/src/"):
                    require(member.isreg(), "Selected archive member is not a regular file")
                    data = read_tar_member(archive_file, member)
                    if name == SOURCE_PACKAGE_JSON:
                        require(len(data) <= MANIFEST_LIMIT, "Package metadata exceeds bound")
                        require(sha256(data) == source["packageJsonSha256"],
                                "Package metadata hash mismatch")
                        package_json = parse_json(data)
                    elif name == SOURCE_LICENSE:
                        verify_blob(data, source["licenseBytes"], source["licenseSha256"],
                                    "renderer license")
                        license_data = data
                    else:
                        source_bytes += len(data)
                        require(source_bytes <= MAX_SOURCE_BYTES, "Source bytes exceed bound")
                        sources[name] = data
    except (OSError, EOFError, tarfile.TarError) as failure:
        raise BuildError("Invalid renderer source archive") from failure
    require(package_json is not None and license_data is not None, "Pinned archive metadata is missing")
    require(isinstance(package_json, dict) and package_json.get("name") == source["name"]
            and package_json.get("version") == source["version"]
            and package_json.get("license") == "MIT"
            and package_json.get("dependencies", {}) == {},
            "Package metadata differs from inspected policy")
    require(SOURCE_ENTRY in sources, "Renderer public Terminal source is missing")
    for path in manifest["patch"]["files"]:
        require(path in sources, f"Patch target is missing: {path}")
    return sources, license_data


def inspect_compiler_archive(manifest, archive):
    compiler = manifest["compiler"]
    expanded = decompress(archive, MAX_COMPILER_EXPANDED_BYTES, "renderer compiler archive")
    names = set()
    binary = None
    package_json = None
    try:
        with tarfile.open(fileobj=io.BytesIO(expanded), mode="r:") as archive_file:
            for member in archive_file:
                name = member.name.rstrip("/") if member.isdir() else member.name
                relative_path(name)
                require(name in COMPILER_MEMBERS and name not in names,
                        "Unexpected compiler archive member")
                names.add(name)
                require(member.type in (tarfile.REGTYPE, tarfile.AREGTYPE) and not member.pax_headers,
                        "Links/special/PAX compiler archive members are forbidden")
                require(integer(member.size, 0, MAX_COMPILER_MEMBER_BYTES),
                        "Compiler archive member exceeds bound")
                data = read_tar_member(archive_file, member)
                if name == compiler["binaryMember"]:
                    verify_blob(data, compiler["binaryBytes"], compiler["binarySha256"],
                                "renderer compiler binary")
                    binary = data
                elif name == "package/package.json":
                    package_json = parse_json(data)
    except (OSError, EOFError, tarfile.TarError) as failure:
        raise BuildError("Invalid renderer compiler archive") from failure
    require(names == COMPILER_MEMBERS, "Compiler archive inventory differs from inspection")
    require(binary is not None and package_json is not None, "Compiler archive is incomplete")
    require(isinstance(package_json, dict) and package_json.get("name") == COMPILER_NAME
            and package_json.get("version") == COMPILER_VERSION,
            "Compiler package metadata differs from its pin")
    return binary


def fetch_compiler(manifest, cache, *, offline=False):
    """Explicit setup only; verify the complete compiler archive before caching it."""
    validate_manifest(manifest)
    record = manifest["compiler"]
    path = cache / record["cacheFile"]
    no_link_components(path.parent)
    try:
        archive = read_cached_archive(record, cache, "renderer compiler archive")
    except FileNotFoundError:
        require(not offline, "Pinned compiler archive is missing in offline mode")
        archive = ASSETS.fetch_archive(record)
        verify_blob(archive, record["archiveBytes"], record["archiveSha256"], "renderer compiler archive")
        integrity = "sha512-" + base64.b64encode(hashlib.sha512(archive).digest()).decode("ascii")
        require(integrity == record["integrity"], "Compiler archive SHA-512 mismatch")
        inspect_compiler_archive(manifest, archive)
        atomic_write(path, archive)
    else:
        inspect_compiler_archive(manifest, archive)
    return {"path": str(path), "bytes": len(archive), "sha256": sha256(archive)}


def read_patch(manifest, repository):
    record = manifest["patch"]
    path = repository / record["file"]
    no_link_components(path.parent)
    data = read_regular(path, MAX_PATCH_BYTES)
    require(sha256(data) == record["sha256"], "Renderer patch hash mismatch")
    return data


def validate_patch_scope(raw, allowed_paths):
    """Validate only the reviewed file headers; replay handles hunk semantics."""
    try:
        text = raw.decode("utf-8")
    except UnicodeError as failure:
        raise BuildError("Renderer patch is not UTF-8") from failure
    require("\r" not in text and "\0" not in text and text.endswith("\n"),
            "Renderer patch is not canonical LF text")
    lines = text.splitlines(keepends=True)
    seen = set()
    for index, line in enumerate(lines):
        if line.startswith(("new file mode", "deleted file mode", "rename from", "rename to",
                            "Binary files")):
            raise BuildError("Unsupported renderer patch directive")
        if line.startswith("diff --git "):
            fields = line.rstrip("\n").split(" ")
            require(len(fields) == 4 and fields[2].startswith("a/") and fields[3].startswith("b/")
                    and fields[2][2:] == fields[3][2:], "Invalid git diff header")
        if line.startswith("--- a/"):
            old_label = line[6:].rstrip("\n")
            require("\t" not in old_label, "Renderer patch headers must not carry timestamps")
            require(index + 1 < len(lines) and lines[index + 1].startswith("+++ b/"),
                    "Missing new-file header")
            new_label = lines[index + 1][6:].rstrip("\n")
            require("\t" not in new_label, "Renderer patch headers must not carry timestamps")
            require(old_label == new_label, "Renderer patch renames are forbidden")
            relative_path(old_label)
            require(old_label in allowed_paths, "Renderer patch targets an unapproved file")
            require(old_label not in seen, "Duplicate renderer patch file")
            seen.add(old_label)
    require(seen == set(allowed_paths), "Renderer patch file set differs from its review record")


def load_inputs(manifest, cache, repository=ROOT):
    """Read and verify all pinned inputs without extracting or writing files."""
    source_archive = read_cached_archive(manifest["source"], cache, "renderer source archive")
    sources, _license = inspect_source_archive(manifest, source_archive)
    patch_raw = read_patch(manifest, repository)
    validate_patch_scope(patch_raw, set(manifest["patch"]["files"]))
    compiler_archive = read_cached_archive(manifest["compiler"], cache, "renderer compiler archive")
    binary = inspect_compiler_archive(manifest, compiler_archive)
    return sources, patch_raw, binary


def verify_inputs(manifest, cache, repository=ROOT):
    """Verify all pins without replaying the patch, compiling, or writing."""
    validate_manifest(manifest)
    require("output" in manifest, "Reviewed renderer output pin is missing")
    load_inputs(manifest, cache, repository)


def renderer_banner(manifest):
    return BANNER.format(
        version=manifest["source"]["version"],
        archive_sha256=manifest["source"]["archiveSha256"],
        patch_file=manifest["patch"]["file"],
        patch_sha256=manifest["patch"]["sha256"],
        compiler_name=manifest["compiler"]["name"],
        compiler_version=manifest["compiler"]["version"],
        binary_sha256=manifest["compiler"]["binarySha256"],
    )


def compile_sources(manifest, source_root, compiler_binary):
    entry_path = source_root / "entry.ts"
    atomic_write(entry_path, manifest["build"]["entry"].encode("utf-8"))
    atomic_write(source_root / "tsconfig.json", TSCONFIG_BYTES)
    binary_path = source_root / "bin/esbuild"
    binary_path.parent.mkdir(parents=True, exist_ok=True)
    atomic_write(binary_path, compiler_binary)
    os.chmod(binary_path, 0o700)
    outfile = source_root / "xterm.js"
    arguments = [
        str(binary_path),
        str(entry_path),
        "--bundle",
        "--platform=browser",
        "--format=iife",
        "--target=es2020",
        f"--outfile={outfile}",
        f"--tsconfig={source_root / 'tsconfig.json'}",
        "--legal-comments=inline",
        "--tree-shaking=true",
        "--minify=true",
        "--log-level=warning",
        "--color=false",
        f"--banner:js={renderer_banner(manifest)}",
    ]
    # No NODE_PATH, user config, shell, stdin, or network-capable helper is
    # inherited. The compiler is the hash-verified pinned native binary.
    environment = {
        "PATH": os.defpath,
        "HOME": str(source_root),
        "TMPDIR": str(source_root),
        "LANG": "C",
        "LC_ALL": "C",
        "TZ": "UTC",
    }
    try:
        result = subprocess.run(
            arguments,
            cwd=source_root,
            env=environment,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=COMPILE_TIMEOUT_SECONDS,
            check=False,
        )
    except subprocess.TimeoutExpired as failure:
        raise BuildError("Renderer compile exceeded its finite deadline") from failure
    require(len(result.stdout) + len(result.stderr) <= MAX_BUILD_LOG_BYTES,
            "Renderer compile output exceeds bound")
    require(result.returncode == 0,
            "Renderer compile failed: " + result.stderr.decode("utf-8", "replace").strip()[:2000])
    bundle = read_regular(outfile, MAX_BUNDLE_BYTES)
    require(len(bundle) > 0, "Renderer compile produced an empty bundle")
    require(b"sourceMappingURL" not in bundle, "Renderer bundle includes a source map reference")
    require(str(source_root).encode("utf-8") not in bundle,
            "Renderer bundle exposes an isolated build path")
    return bundle


def verify_output(manifest, bundle):
    output = manifest["output"]
    verify_blob(bundle, output["bytes"], output["sha256"], "reviewed renderer output")


def build_bundle(manifest, cache, repository=ROOT, *, require_reviewed_output=True):
    validate_manifest(manifest)
    if require_reviewed_output:
        require("output" in manifest, "Reviewed renderer output pin is missing")
    sources, patch_raw, binary = load_inputs(manifest, cache, repository)
    with tempfile.TemporaryDirectory(prefix="cloudy-terminal-renderer-") as temporary:
        root = Path(temporary)
        for package_path, data in sources.items():
            require(package_path.startswith("package/src/"), "Unexpected source path")
            atomic_write(root / package_path, data)
        try:
            RDP.replay_patch(root, patch_raw)
        except (OSError, ValueError) as failure:
            raise BuildError(f"Renderer patch replay failed: {failure}") from failure
        bundle = compile_sources(manifest, root / "package", binary)
    if "output" in manifest and require_reviewed_output:
        verify_output(manifest, bundle)
    return bundle


def candidate_build(manifest, cache, repository=ROOT):
    validate_manifest(manifest)
    bundle = build_bundle(manifest, cache, repository, require_reviewed_output=False)
    path = cache / CANDIDATE_PATH
    no_link_components(path.parent)
    atomic_write(path, bundle)
    result = {"path": str(path), "bytes": len(bundle), "sha256": sha256(bundle)}
    if "output" in manifest:
        result["matchesReviewedOutput"] = (
            len(bundle) == manifest["output"]["bytes"]
            and sha256(bundle) == manifest["output"]["sha256"]
        )
    return result


def check_bundle(manifest, cache, repository=ROOT, output_root=PRODUCT_OUTPUT_ROOT):
    validate_manifest(manifest)
    require("output" in manifest, "Reviewed renderer output pin is missing")
    verify_inputs(manifest, cache, repository)
    bundle = read_regular(output_root / manifest["output"]["path"], MAX_BUNDLE_BYTES)
    verify_output(manifest, bundle)
    return {"path": str(output_root / manifest["output"]["path"]),
            "bytes": len(bundle), "sha256": sha256(bundle)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--candidate", action="store_true",
                       help="Compile to the ignored cache and print the exact output pin")
    modes.add_argument("--check", action="store_true",
                       help="Verify pins and the generated product bundle without compiling or writing")
    modes.add_argument("--fetch-compiler", action="store_true",
                       help="Explicitly fetch and verify only the pinned build compiler")
    parser.add_argument("--offline", action="store_true",
                        help="Forbid network access, including compiler setup")
    args = parser.parse_args(argv)
    try:
        manifest = load_manifest(MANIFEST, require_output=args.check)
        if args.fetch_compiler:
            result = fetch_compiler(manifest, CACHE_ROOT, offline=args.offline)
            print(json.dumps({"mode": "fetch-compiler", **result}, indent=2))
        elif args.check:
            result = check_bundle(manifest, CACHE_ROOT)
            print(json.dumps({"mode": "check", **result}, indent=2))
        else:
            result = candidate_build(manifest, CACHE_ROOT)
            print(json.dumps({"mode": "candidate", **result}, indent=2))
    except (BuildError, OSError, urllib.error.URLError) as failure:
        print(f"Terminal renderer build failed: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
