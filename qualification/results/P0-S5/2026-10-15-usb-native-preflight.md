# Native USB access preflight on HP Googlebook

**Date:** 2026-10-15. **Stage:** P0-S5, in progress. **Outcome:** the real 0.1.0 diagnostic installed and loaded libusb 1.0.30 on the HP Googlebook. Explicit no-discovery context initialization/exit passed; public Android enumeration returned **two devices**, neither selected or opened. This is a native-loading/enumeration result, not a USB forwarding or physical peripheral-access pass. CAP-USB remains pending.

The [evidence manifest](2026-10-15-usb-native-preflight.json) binds the reviewed package, current source files and private build/device records. Raw UI/device inventories, logs and the APK remain private. See the [source/build review](../../../docs/06-validation/2026-10-15-native-usb-review.md) and [provider boundary](../../../docs/03-platform/googlebook/p0-s5-usb-provider-entry.md).

## Exact target and package

| Identity | Observed value |
| --- | --- |
| Hardware | HP Googlebook 14 |
| Build/API/ABI | CL3B.260622.276.R2 / 37 / arm64-v8a |
| Security patch | 2026-09-01 |
| Android package | `dev.cloudy.qualification.usb` 0.1.0 / code1 |
| Build reference | `d207f066-517d-4a2f-8897-f347998696da` |
| APK | 3,399,686 bytes; SHA-256 `90f8b8f581a6b3e901f02446ffd019cd7e8401a16d81b884dead97531a158a90` |
| Native engine | Actual libusb 1.0.30, separate dynamic JNI boundary |

The package is independently installed alongside the terminal and RDP diagnostics. No terminal/RDP package data was cleared or replaced by this installation. The host supplied the existing SDK/JDK/NDK, USB ADB and build tools; no physical forwarding consumer was started.

## Actual sequence and observations

1. The leader independently matched the final APK, all 20 packaged native/source/notice files and all 27 native-receipt inputs. The source/build candidate had passed strict native compilation, Android compiler/Lint, 20 JVM tests and six preparation-helper tests. Repository AST/Ruff and 67 existing behavior tests also passed.
2. The Android CLI installed/launched the exact APK while preserving the other diagnostics. The actual desktop-window layout and screenshot show `libusb 1.0.30; no-discovery init/exit passed`, USB host feature=true, observed devices=2 and native ready=true, with the expected build reference.
3. Both enumerated devices were left unselected. Their identities and physical roles were not qualified. **No zero-device result is claimed.** No permission request, `openDevice`, native wrap, interface claim, endpoint transfer or reset was attempted.
4. Activating Inspect without a selection returned `Select an identified peripheral first`. This checks the actual UI admission guard without opening unrelated or built-in hardware.
5. Close/Cancel reported cleanup complete for this empty access state. This does not exercise cleanup of a real granted connection. Bounded package/app logs and a memory observation were retained privately; no performance conclusion follows from them.
6. The leader stopped the diagnostic and observed its process absent. The reviewed APK remains installed for later selected-peripheral work. No Android USB grant was requested or changed by this run.

The source/API path uses [`UsbDeviceConnection.getFileDescriptor`](https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection#getFileDescriptor()) and libusb's borrowed-FD wrapper. Compilation and initialization do not establish that a particular granted device can be opened, claimed or forwarded. The app requests no manifest permissions or background service, and USB/touchscreen/faketouch are optional installation features.

## Limits and next step

A representative peripheral/probe remains deliberately unspecified; do not ask the user to pick unavailable hardware again. Continue the reviewed provider/consumer investigation and preserve full enrolled plug-and-play scope. Actual permission delivery/denial, granted-FD inspection, physical detach/replug/cancel, reset/re-enumeration, authenticated network export, workstation import and program/verify remain pending. External VirtualHere and guest-oriented usbredir remain separate candidate topologies, not capabilities supplied by libusb alone.

The new [native USB CI workflow](../../../.github/workflows/android-native-usb.yml) covers a real pinned library/JNI build, packaging, compiler/Lint and authored boundary tests. Its first hosted run is separate from these executed local/device checks. Product distribution/license adoption remains P1-S4 work. No product, general USB class support or CAP-USB readiness is promoted.
