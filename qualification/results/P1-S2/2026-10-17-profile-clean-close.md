# Unchanged saved-profile closure — 2026-10-17

**Status:** verified bounded Console emulator repair, not full C01/P1-S2 or physical-input acceptance. **Base:** `c1d3b983dce3fc09e6f161d5b6cc5c90a6171d54` plus the four profile source/test changes committed with this record. A guided DeepSeek Flash worker supplied the change; the parent reviewed actual source, corrected invalid Compose imports, ran the real app and accepted only the scope below.

## Behavior

`CloudyProfilesActivity.leaveEditor` previously required discard confirmation whenever any draft existed, including immediately after successful Save. It now closes directly only for a known saved draft whose owner, generation, ID, revision and every editable value match the loaded saved row. Cursor/selection movement is not a content edit. Raw tag text is compared without trimming/deduplication, so a real unsaved text change is not hidden by Save's normalization.

New drafts, missing/stale catalog or version, changed values and active/restored uncertain mutations retain explicit discard. Both confirmed discard and clean close clear local editor/review/message state through the existing navigation action; neither path writes a profile or replays a mutation. No store/schema/authentication policy changed.

## Executed evidence

- Owned API37/Android17 x86_64 emulator, Android Emulator37.2.12/build16428233; Google APIs37.0 revision6, 2880×1800 at240dpi. Fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
- Console **0.1.18/code19** app APK SHA-256 **`045da757abec1391bcb96f98195c834e207984573cb6e1bd718fbfd4592cd399`**; test APK **`0b5a98dc4028218322a39fb61e639a16742d29d2139182c79bae8743aa447b94`**. Frozen APKs and per-source hashes: `.cache/console-interaction-2026-10-17/candidate01/manifest.json`.
- Strict offline Console app/test assembly, Lint and relevant JVM tasks passed (240 tasks,41 executed). The earlier test-import and unrelated interaction-preview pluralization failures were repaired, not suppressed. Java-only domain/SSH modules remain outside Android Lint analysis.
- **10 actual Android methods passed:** eight `ConsoleProfileActivityTest` and two `ConsoleProfileRecreationTest` methods. New checks cover clean saved/reopened closure without confirmation or revision mutation, cursor-only movement, raw-tag Cancel preservation and restored uncertain create/update discard consent. Existing conflict, second edit after review, external deletion, selection recreation and once-only mutation checks remain passing. Fixtures retire their own Activities and delete only their own created rows.
- A separate native-pointer workflow opened Home → Add host → New profile, saved an owned profile, then closed its unchanged editor with **no discard dialog**. The saved revision remained1. Parent inspected the actual post-close pixels, reopened that exact profile, confirmed its deletion, preserved the preexisting review profile and closed only the owned window.

Manual evidence: `.cache/console-interaction-2026-10-17/profile-close-e63cbc75-dc5e-4e35-b4a2-1fc357bb49ae/`. The APK also contained uncommitted terminal-interaction work; those paths are not qualified by this profile result.

## Preserved input observation

The manual ADB typing stimulus requested `CloseReview-e63cbc75`, but the settled saved editor contained `CloseReview-e63cbc`; the following host and tag fields were complete. The operator moved to another field immediately after injection and had not established a committed-text barrier. No cause is assigned to the app, IME or injection timing, and no full native-typing pass is inferred. The actual observed saved identity was used for close/deletion ownership. Physical typing/IME and this rapid injected focus-transition boundary remain separate validation work; no delay workaround was added.

The earlier redundant-confirmation observation remains in [the home UX record](2026-10-17-console-home-emulator.md). This result repairs that specific behavior without changing full Console requirements or declaring the broader profile/input workflow complete.

## Reproduce

```bash
adb -s emulator-5580 shell am instrument -w -r \
  -e class io.github.baitian6641.cloudy.console.ConsoleProfileActivityTest,io.github.baitian6641.cloudy.console.ConsoleProfileRecreationTest \
  io.github.baitian6641.cloudy.console.test/androidx.test.runner.AndroidJUnitRunner
```

Build/install the matching app and test artifacts first using the documented project-local SDK/JDK/Gradle environment. Check actual JUnit output (`OK (10 tests)` in this run), not merely adb's exit status. Do not apply this receipt to a different APK or silently reset user data.
