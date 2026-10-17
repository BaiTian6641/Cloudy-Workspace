# Workspace home 0.1.17/0.1.21 — Googlebook device run

**Date:** 2026-10-16, America/Chicago. **Scope:** the Termius-style workspace home (S01/S06), profile organization metadata with schema v1→v2 migration, editor tags/favorite fields, and the persisted terminal right-click toggle. **Lane:** user-resumed workspace-UX implementation on branch `codex/workspace-home-ux`.

## Construction and installation

- Console **0.1.17 / code 18** and Desktop **0.1.21 / code 22**, debug builds signed with the established debug identity (certificate SHA-256 `ce641362…2807`, matching the previously installed 0.1.16/0.1.19 apps).
- An earlier build attempt produced APKs under a session-generated debug keystore (`1a86c395…7852`) because this AGP version resolves `$ANDROID_USER_HOME/debug.keystore` while the historical key lived at `$ANDROID_USER_HOME/.android/debug.keystore`; the mismatch was detected by a real `INSTALL_FAILED_UPDATE_INCOMPATIBLE` refusal, the historical key was restored to the resolution path (session key retained as `.bak`), and rebuilt APKs verified via `apksigner --print-certs` before installation. No device data was uninstalled or cleared.
- Upgrade installs (`install -r`) over the retained 0.1.16 Console and 0.1.19 Desktop data succeeded; the real profile store migrated schema v1→v2 on first access.
- Builds used the reviewed explicit VNC native reuse binding (receipt `96ed1ef2…cebc7`, declaration `startup-error-reuse-01.json` `75d123f3…c8ed`, prepared directory `user-ended-01`); no native rebuild occurred.

## Device results (HP Googlebook 14, API 37, Android 17, USB)

All results are `am instrument` runs against the upgraded apps; every listed class passed.

| Package | Classes | Tests | Result |
| --- | --- | ---: | --- |
| platform.test | full package, incl. `ConnectionProfilesMigrationTest` (hand-built v1 fixture migrates; CRUD/recordUse on migrated store), `ConnectionProfilesTest` (v2 fail-closed), `ProfileEditorStateTest` (schema v2 round-trip + schema-1 tolerant restore) | 203 | OK |
| console.test | all 11 classes, incl. `ConsoleWindowActivityTest` (Advanced-housed rename/host-keys contracts), profile editor and geometry suites | 39 | OK |
| desktop.test | all 12 classes, incl. VNC admission/operation-failure suites | 43 | OK |

## Boundaries

- Events are injected by the test framework: this run does not claim physical keyboard, trackpad, IME composition or TalkBack acceptance. Narrow/short tiled geometry is covered by the geometry contracts; caption pixels and external displays remain separately scoped.
- No pixel visual acceptance is claimed; home screen captures and light/dark × density review against the design tokens remain open P1-S2 work with their own evidence.
- The vault keychain list, terminal input adapter (xterm bridge), RDP/stream routes and the X11 broker prototype are not implemented by this change; their UI states are honest unavailability, not passes.
- The 2026-10-16 `vnc-error-settlement-01` preserved-state authority remains a historical file record; the device's installed-state authority is now 0.1.17/0.1.21. The owned profile deletion and test-package removal from that settlement are unchanged.
