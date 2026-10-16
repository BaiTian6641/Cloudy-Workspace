# Display outcome accessibility semantics

**Date:** 2026-10-16. **Outcome:** Console and Desktop **0.1.13/code14** each pass the focused actual Googlebook display-settings Activity test, including the new Saved text and Polite live-region assertion: **two cases passed, zero failed, zero skipped**. Two matching effective preference-restoration receipts and terminal instrumentation completions are retained. Both products are installed and stopped; all three instrumentation packages are absent. This is bounded semantics evidence, not physical screen-reader speech or complete accessibility qualification. P1-S2 remains in progress.

## Change and source boundary

The production change adds `LiveRegionMode.Polite` to the existing display outcome text in `DisplaySettingsScreen`. Loading/saving progress and the separate persistent recovery explanation do not gain competing live regions. The shared Activity test asserts the real completed write's Saved text and native live-region semantics before its existing controlled Activity recreation. The two app build configurations select the distinct candidate identity0.1.13/code14. No dependency/version lock, palette, token, visible layout, storage policy, credential or engine behavior changes.

The [sanitized manifest](2026-10-16-display-outcome-accessibility.json) binds the two changed Kotlin files, two product version configurations, all five frozen APK hashes, build/device runners and locally retained evidence hashes. Source base is `b9490d56d73590ced4df02272ced2086ea63b6d7` plus the reviewed uncommitted increment. An independent direct Codex reviewer checked all224 input hashes against current source and all five frozen APK hashes against build02; no mismatch was found. Public evidence uses role/hash records rather than device serials, private metadata or raw device-log paths.

## Build evidence and executed-test distinction

Frozen build02 passes strict dependency verification, Kotlin compilation, product/test APK assembly and four zero-issue Lint reports in27seconds:322actionable tasks,32executed and290up-to-date. The final candidate source set is unchanged throughout the build. All product/test APKs used in the actual run are bound to that build. The platform test APK is built and retained but is not installed for this focused run.

Build02's domain, platform and design-system JVM test tasks are **UP-TO-DATE**, not newly executed cases. Retained passing reports contain64domain,7platform and4design cases, with no failures/errors/skips. Build01 actually executes the7platform JVM cases; its domain/design tasks carry forward unchanged reports. Build01 compiles the same two Kotlin changes under the preceding product metadata; build02 supplies the distinct installed product identity. Earlier build evidence is preserved, not relabeled as new execution.

## Actual device execution

Target: **HP Googlebook14**, Android17/API37, arm64-v8a, build `CL3B.260622.276.R2`, incremental `16560795`. Actual parent-owned run: **17:48:47.435672Z–17:48:57.438388Z**. The independent reviewer performed no ADB operation and assessed retained output/snapshots after execution.

| Owning product | Actual method | Result |
| --- | --- | --- |
| Console | `ConsoleDisplaySettingsActivityTest#everyNativeChoicePersistsAndSurvivesActivityRecreation` | One passed method, zero skips/failures; Saved/Polite semantics; effective preference restoration; terminal runner completion |
| Desktop | `DesktopDisplaySettingsActivityTest#everyNativeChoicePersistsAndSurvivesActivityRecreation` | One passed method, zero skips/failures; Saved/Polite semantics; effective preference restoration; terminal runner completion |

The inherited test operates the real owning-app display preference owner and selectable UI. It visits each appearance and density choice, confirms completed writes and selection, checks the resulting message and semantics, then checks preference retention across Activity recreation. It restores the original effective preference values through explicit normal field commits and closes its ActivityScenario. Activity recreation is not process-death evidence. Restoration status messages are receipts, not additional test cases.

Both raw instrumentation streams contain the exact intended successful method, `OK (1 test)`, terminal `INSTRUMENTATION_CODE: -1` and exactly one matching settings-lane/package restoration receipt. Both instrumentation stderr files are empty. No run failure, timeout or cleanup recovery is present in this finite attempt.

## Preservation and retirement review

Products replace0.1.12/code13 using normal package replacement, without clearing app data. Installed candidate APK hashes are checked after upgrade and in settled/final observations. Read-only pre/upgraded/settled/final snapshots independently agree on boot/system/device state, complete typed display preference values and every inventoried other persistent-file hash. This includes the existing vault receipt, profiles, trust and resource/replay data; their contents and identifiers are not published. The inventory contains27Console and15Desktop persistent files, of which the display preference XML is compared by complete typed values because normal preference commits may reserialize it. Effective value restoration is not a claim of byte-identical preference XML serialization.

Actual terminal test completions and restoration receipts precede force-stop and test-package removal. Both test APKs have successful install/removal outputs; final package-path observations for Console.test, Desktop.test and platform.test are empty, and final product PID observations are empty. Reverse-route observations match before, settled and final. The runner checks these absence/retirement command outcomes before its passing result. No device font scale, display density or night-mode mutation, authentication request, host fixture or remote operation is introduced. The runner has no automatic cleanup/rollback after uncertainty; that recovery path was not needed in this successful attempt.

## Acceptance limits

The changed production modifier carries semantics only, so no new screenshot/render claim is made or needed to assess a changed visible layout. Historical [display settings evidence](2026-10-16-native-display-settings.md) retains its own six workspace captures and scope; those images do not become settings-screen visual evidence. No physical TalkBack/screen-reader speech, accessibility-service delivery, keyboard/pointer, narrow/short/scaled settings geometry, full component accessibility, process-death recovery or remote functionality is qualified by these two tests.

This finite result accepts the new outcome semantics and focused owning-app behavior. It does not complete P1-S2, change any capability gate or waive remaining Googlebook input/lifecycle, vault, presentation or performance requirements. Hosted CI for the eventual committed source remains a separate exact-SHA check.
