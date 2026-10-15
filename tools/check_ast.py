#!/usr/bin/env python3
"""Parse authored Python with Python 3.10 grammar without executing source files."""

import ast
import json
import subprocess
import sys
import tokenize
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PYTHON_GRAMMAR = (3, 10)
# Git omits ignored files; this second boundary also excludes accidentally
# tracked dependency, private, and generated trees. Keep Ruff's exclusions aligned.
EXCLUDED_DIRECTORIES = {
    ".agents", ".aws", ".cache", ".codex", ".git", ".gradle",
    ".mypy_cache", ".pytest_cache", ".ruff_cache", ".venv",
    "__pycache__", "artifacts", "build", "dist", "downloads", "external",
    "node_modules", "private", "site-packages", "third-party", "third_party",
    "vendor", "venv",
}


def source_files(root):
    """Include new working files as well as tracked files, respecting Git ignores."""
    result = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard",
         "--", "*.py", "*.pyi"],
        cwd=root,
        check=True,
        capture_output=True,
        timeout=10,
    )
    candidates = {Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value}
    return sorted(
        path for path in candidates
        if not EXCLUDED_DIRECTORIES.intersection(path.parts[:-1])
        and ((root / path).exists() or (root / path).is_symlink())
    )


def check_sources(root, paths):
    """Return diagnostics without importing modules or writing bytecode caches."""
    errors = []
    for relative in paths:
        source_path = root / relative
        if source_path.is_symlink():
            errors.append({"file": relative.as_posix(), "message": "Authored Python must not be a symlink."})
            continue
        try:
            # tokenize.open honors Python encoding cookies without importing code.
            with tokenize.open(source_path) as source:
                tree = ast.parse(
                    source.read(), filename=relative.as_posix(),
                    feature_version=PYTHON_GRAMMAR, type_comments=True,
                )
            # Parsing alone accepts constructs such as a top-level return. Compile
            # the AST to check those contexts, but never execute the result.
            compile(tree, relative.as_posix(), "exec", dont_inherit=True)
        except SyntaxError as exc:
            errors.append({
                "file": relative.as_posix(), "line": exc.lineno,
                "column": exc.offset, "message": exc.msg,
            })
        except (OSError, UnicodeError, ValueError) as exc:
            errors.append({"file": relative.as_posix(), "message": str(exc)})
    return errors


def main():
    try:
        paths = source_files(ROOT)
        errors = check_sources(ROOT, paths)
        if not paths:
            errors.append({"message": "No authored Python sources were found."})
    except (OSError, UnicodeError, subprocess.SubprocessError) as exc:
        paths = []
        errors = [{"message": f"Source discovery failed: {type(exc).__name__}"}]
    report = {
        "scope": "authored-python-syntax-only",
        "pythonGrammar": ".".join(map(str, PYTHON_GRAMMAR)),
        "checkedFiles": len(paths),
        "files": [path.as_posix() for path in paths],
        "errors": errors,
        "passed": not errors,
    }
    print(json.dumps(report, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
