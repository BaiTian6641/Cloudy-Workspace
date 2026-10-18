# Declared SSH-config preview and profile import

**Date/stage:** 2026-10-18, C15/P2-S5 follow-up. **Outcome:** parser and real app-private import
creation PASS; the final UI Import tap was not observed because the emulator switched apps during
that step, so the screen's complete click-through remains open.

## Implemented boundary

- `SshConfigImporter` parses a declared subset: `Host` aliases (literal only), `HostName`, `Port`;
  first-read values, comments, quoted values, multiple aliases, duplicate/existing-alias
  detection and a 64 KiB/64-entry bound.
- Everything else is reported and never imported: `User`, `IdentityFile`, `ProxyJump`,
  `ProxyCommand`, `Include`, `Match`, forwards, `SetEnv`, unknown directives and host patterns.
  Values for unsupported directives are never echoed into issues; no key bytes, key paths or
  credentials enter a profile.
- The import batch reloads the current profile generation for each create, preserves existing
  profiles, tags created profiles `imported`, and stops with a typed failure.
- Console profile management exposes **Import SSH config**. The screen accepts a pasted config or a
  document-grant file, shows the parsed entries and unsupported lines, and imports only after a
  preview. Desktop does not show the Console-only action.
- The feature does not connect, execute a command, resolve a host, authorise trust or store a
  credential.

## Verification

| Check | Result |
| --- | --- |
| `SshConfigImporterTest` (JVM) | **6/6**: Host/HostName/Port/quotes/comments, defaults, wildcard/existing/duplicates, unsupported directives without value leakage, malformed entries, bounds |
| `SshConfigImportStoreTest` (Android instrumentation, real SQLite) | **2/2**: two profiles created from parser output with expected labels/hosts/ports/tags and no key/jump reference; existing label skipped and existing profile preserved |
| UI | Import screen, file picker, loaded config text, entry/unsupported preview rendering and action enablement compile and were observed up to the preview. The final Import button tap was not observed because the emulator foregrounded an unrelated app; the same batch logic is the instrumented path above. |
| Build/Lint | Console debug assembly and platform/Console Lint pass with zero findings |

## Remaining

The complete UI click-through on a stable device remains; the activity's final import result was
not visually observed. Importing `User`, IdentityFile or jumps would need new profile fields and
their own contracts; none are guessed. C15 remains the named R1 follow-up and is partially
implemented, not a full R1 completion claim.
