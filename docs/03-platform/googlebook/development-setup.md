# Development setup and qualification record

This is an operational checklist for P0/P1. Connection observations belong in a dated qualification record with the selected device/build; this checklist does not itself qualify an application or capability. Follow the [engineering workflow](../../05-delivery/engineering-workflow.md) and [reviewed skill boundaries](../../07-reference/development-skills.md).

## 1. Capture inventory

Copy [inventory.example.json](../../../qualification/inventory.example.json) to the ignored `qualification/inventory.local.json`. Fill exact Googlebook SKU, OS build/API level, ABI, RAM, display/scaling, input devices, WebView, decoders, tailnet client, and backend versions. For hosts include installed OS/build, GPU/driver, user-session identity, compositor, permissions, and attached peripherals.

Keep serial numbers, tailnet names/addresses, personal usernames, and credential references out of public run artifacts. Sanitized evidence still needs enough version/topology detail to reproduce a result.

## 2. Establish ADB safely

Follow [Google's Googlebook ADB guide](https://developer.android.com/develop/adaptive-apps/guides/googlebook/adb-debugging). Wi-Fi debugging is documented across models. HP Googlebook 14 currently lists USB debugging on its left port. Recheck that table for the actual SKU/build and use a suitable cable. This concerns ADB debugging transport, not arbitrary Linux VM USB passthrough.

On a development workstation, inspect first. Keep the selected serial in the shell/private inventory and use it on every device command:

```bash
adb version
adb devices -l
read -r -p 'Authorized device serial: ' cloudy_device_serial
adb -s "$cloudy_device_serial" shell getprop ro.build.version.release
adb -s "$cloudy_device_serial" shell getprop ro.build.version.sdk
adb -s "$cloudy_device_serial" shell getprop ro.build.fingerprint
adb -s "$cloudy_device_serial" shell getprop ro.product.cpu.abilist
adb -s "$cloudy_device_serial" shell wm size
adb -s "$cloudy_device_serial" shell wm density
```

These commands require a paired/authorized device and installed platform tools. Never assume `adb devices` success from a tool being installed. Wireless pairing details are intentionally not stored in the repository.

## 3. Select the toolchain

Record the Android Studio channel/build, SDK platform/build tools, JDK, AGP, Kotlin, Compose BOM, AndroidX WindowManager/adaptive versions, and NDK if needed. Record dependency locks and an update policy. Test debug install and launch separately for each app target.

### Verified candidate compatibility, 2026-10-15

The following is a researched candidate, not a selected or installed Cloudy build. The official [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) list this compatibility table (G35):

| Component | Documented value | Qualification decision |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.0; maximum supported API 37 | Candidate to test before version freeze |
| Gradle | Minimum/default 9.6.0 | Pin a matching wrapper and verify its distribution checksum |
| JDK for AGP | Minimum/default 17 | Select and record the actual Gradle runtime; this is not a claim that only JDK 17 works |
| SDK Build Tools | Minimum/default 36.0.0 | Resolve the selected package with the chosen SDK/build before freeze |
| NDK | Default 28.2.13676358 | Relevant only to a selected native probe/backend; do not install merely because it appears in the table |

The [Android 17 setup guide](https://developer.android.com/about/versions/17/setup-sdk) specifies `compileSdk = 37` for Android 17 APIs and a separate, deliberate `targetSdk = 37` change for target-dependent behavior (G36). Its manual package instructions still mention a preview label and Build Tools 37, while the versioned AGP table lists 36.0.0 as minimum/default. Record the actual resolved SDK packages and successful build; do not turn either example into an untested requirement. This documentation update does not choose `minSdk`, `targetSdk`, Kotlin, Compose, or an NDK for the product.

An API-37 device does not establish that the workstation has the API-37 SDK. Conversely, an older compile SDK does not by itself prevent an otherwise compatible APK from running on a newer device. Separate API symbol compilation, target-dependent behavior and runtime qualification. Inspect installed SDK packages and executable locations before installing anything; no `sdkmanager` or `gradle` on `PATH` is a discovery/setup issue, not proof that no SDK exists.

Use [Google's Java build guidance](https://developer.android.com/build/jdks) to distinguish the IDE runtime, Gradle runtime, Java toolchain, language/bytecode target and Android API surface (G37). Keep SDK and JDK locations in local configuration or environment settings rather than committed machine paths. A newer workstation JDK is not proof that every selected plugin supports it; verify the complete combination. Once a reviewed wrapper exists, use that wrapper rather than an unrelated global Gradle.

### Load reviewed skills

Resolve the skill root from the agent environment; the conventional Codex fallback is shown below. Read project `AGENTS.md` and the [skill boundaries](../../07-reference/development-skills.md) first, then only the skill required for the task:

```bash
cloudy_skills_dir="${CODEX_HOME:-$HOME/.codex}/skills"
cat "$cloudy_skills_dir/android-cli/SKILL.md"
cat "$cloudy_skills_dir/testing-setup/SKILL.md"
cat "$cloudy_skills_dir/edge-to-edge/SKILL.md"
```

The paths are generic configuration instructions, not a workstation inventory. Parent and child agents must explicitly load the relevant body/reference even if a fresh turn lists the skill in its catalog. `android-cli` is a guidance package; its presence does not install `android`, SDK components, or Gradle. Use the pinned provenance and license records described in the [skill register](../../07-reference/development-skills.md), and do not run the skill's automatic latest-installer command.

Use the desktop emulator to probe resizing/focus/task behavior, then repeat on physical hardware. Emulator success does not establish trackpad relative input, USB reset support, battery behavior, or hardware decode.

## 4. Run focused probes

| Probe | Inputs | Evidence |
| --- | --- | --- |
| Window/caption | Resize, maximize/fullscreen, new window, external display | Insets and control overlap, task IDs, focus trace |
| Keyboard/IME | English/CJK, combinations, alternate-screen TUI, selection | Exact committed bytes, composition video/log with no secrets |
| Pointer | Physical trackpad, external mouse, capture/release, focus change | Event sources/deltas and CAD behavior |
| Lifecycle | Background, close view, process recreation, sleep/resume | Persisted state and reconnect behavior |
| Decoder | Enumerated codecs plus selected stream profile | Actual chosen decoder, frame pacing, CPU/battery |
| USB | Probe normal/bootloader/reset identities, attach/release | Backend ownership and program/verify log |

## 5. Record and decide

Use the [run template](../../06-validation/templates/run.md). Attach logs and hashes to the run, link API IDs and stage IDs, and update the matrix. A failed or inconclusive probe is useful evidence; do not turn it into a silent assumption. Record a separate capability readiness outcome using the [gate protocol](../../05-delivery/feasibility-gates.md). A failed investigation may finish with a blocked feature decision; a fallback requires its own evidence and any necessary scope decision before feature promotion.
