# Development setup and qualification record

This is an operational checklist for P0/P1. It records the setup to perform; it does not claim a Googlebook is connected to this checkout.

## 1. Capture inventory

Copy [inventory.example.json](../../../qualification/inventory.example.json) to the ignored `qualification/inventory.local.json`. Fill exact Googlebook SKU, OS build/API level, ABI, RAM, display/scaling, input devices, WebView, decoders, tailnet client, and backend versions. For hosts include installed OS/build, GPU/driver, user-session identity, compositor, permissions, and attached peripherals.

Keep serial numbers, tailnet names/addresses, personal usernames, and credential references out of public run artifacts. Sanitized evidence still needs enough version/topology detail to reproduce a result.

## 2. Establish ADB safely

Follow [Google's Googlebook ADB guide](https://developer.android.com/develop/adaptive-apps/guides/googlebook/adb-debugging). Wi-Fi debugging is documented across models. HP Googlebook 14 currently lists USB debugging on its left port. Recheck that table for the actual SKU/build and use a suitable cable. This concerns ADB debugging transport, not arbitrary Linux VM USB passthrough.

On a development workstation, inspect rather than mutate first:

```bash
adb version
adb devices -l
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
adb shell getprop ro.product.cpu.abilist
adb shell wm size
adb shell wm density
```

These commands require a paired/authorized device and installed platform tools. Never assume `adb devices` success from a tool being installed. Wireless pairing details are intentionally not stored in the repository.

## 3. Select the toolchain

Record the Android Studio channel/build, SDK platform/build tools, JDK, AGP, Kotlin, Compose BOM, AndroidX WindowManager/adaptive versions, and NDK if needed. Use official compatibility/release documentation when P1 creates Gradle files. Record dependency locks and an update policy. Test debug install and launch separately for each app target.

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

Use the [run template](../../06-validation/templates/run.md). Attach logs and hashes to the run, link API IDs and stage IDs, and update the matrix. A failed or inconclusive probe is useful evidence; do not turn it into a silent assumption. Document the selected fallback and rerun its end-to-end workflow before passing the phase gate.
