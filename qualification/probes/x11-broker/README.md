# Cloudy X11 broker probe (P0-S7 / M2)

**Scope:** a private diagnostic Android app that proves the architecture required by
[`x11-windows.md`](../../../docs/02-architecture/x11-windows.md): one isolated local X display,
one native Android window per managed top-level XID, owned transient presentation, real pointer
and key input, `WM_DELETE_WINDOW`, and ordinary-SSH X11 forwarding through
`shared/android/ssh`. It is **not** product code and creates no Console capability claim.

Result record: [`2026-10-17-x11-broker-device.md`](../../results/P0-S7/2026-10-17-x11-broker-device.md).

## What is Cloudy-owned here

| File | Role |
| --- | --- |
| `app/src/main/cpp/cloudy_x11_broker.cpp` | Composite redirect, per-XID named pixmap capture, window enumeration, transient/override metadata, `WM_DELETE_WINDOW`, XTEST pointer, two-window demo clients |
| `…/x11broker/X11Environment.java` | Process-local engine owner: private `TMPDIR`/Xauthority/cookie, in-process `CmdEntryPoint`, input adapter, display lifetime |
| `…/x11broker/XWindowView.java` | One XID per view, per-view broker connection, render loop, pointer/key/IME bridge, close request |
| `…/x11broker/XWindowActivity.java` | One native document task per top-level XID; owned `Dialog` for `WM_TRANSIENT_FOR` |
| `…/x11broker/BrokerActivity.java` | Bounded diagnostic controls; no product navigation |
| `…/x11broker/SshX11Controller.java` | One key-only ordinary SSH session through the fixture; no saved credential or reconnect |
| `…/x11broker/AndroidLocalSocketEndpoint.java` | Android filesystem-socket endpoint for the shared X11 forwarder |
| `…/com/termux/x11/*.java` | Minimal public-API JNI link classes required by the pinned `libXlorie.so`; no upstream activity/shell loader |
| `app/src/main/java/.../NativeBroker.java` | Narrow JNI declaration only |

## Required ignored staging (not in Git)

The build reads two static source directories from `.cache/x11-broker-01/`:

```
.cache/x11-broker-01/
  jniLibs/arm64-v8a/libXlorie.so             # pinned engine build, SHA-256 632a8b93…63
  jniLibs/arm64-v8a/libc++_shared.so         # NDK r29 arm64 libc++_shared.so
  jniLibs/arm64-v8a/libcloudy_x11_broker.so  # build-native.sh output
  assets/runtime/usr/share/X11/xkb           # XKB data required by libXlorie
  assets/runtime/usr/share/fonts             # engine font data
  assets/ssh-fixture/fixture.properties      # private fixture output (run-* path)
  assets/ssh-fixture/user_ecdsa              # fixture public-key identity
```

`assets/runtime` is copied from the pinned engine's install tree; the engine itself is staged
under `.cache/x11-engine-01/` and is not redistributable as part of this probe. The `ssh-fixture`
assets come from a running private OpenSSH/Xvfb fixture (see
`qualification/results/P0-S7/2026-10-17-x11-forwarding-host.md`); never commit keys or properties.

## Native broker

`build-native.sh` cross-compiles the broker against the NDK and the statically built libxcb tree:

```bash
./qualification/probes/x11-broker/build-native.sh
```

It expects `.cache/x11-broker-01/android-arm64/include` and `/lib` from the libxcb 1.17.0 +
xcb-proto 1.17.0 cross-build and NDK `29.0.14206865`. The linked output has no libxcb dependency
(static archives); only Android system libraries remain.

## Build and install

```bash
export ANDROID_HOME="$PWD/.cache/android-sdk"
export ANDROID_USER_HOME="$PWD/.cache/android-user"
export GRADLE_USER_HOME="$PWD/.cache/gradle-home"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew -p qualification/probes/x11-broker :app:assembleDebug --offline --no-daemon
adb -s <serial> install -r qualification/probes/x11-broker/app/build/outputs/apk/debug/app-debug.apk
```

For the SSH path, run the private fixture, point `assets/ssh-fixture/` at its properties/key,
then `adb reverse tcp:<port> tcp:<port>` before `START SSH X11 APPS`. The probe starts the engine
in-process and exits the whole process on `STOP SERVER` (upstream `LorieNative` behavior).

## Explicit limits

Diagnostic-only UI; no product session model, X11 profile policy, restricted/SECURITY mode,
clipboard, XDND, IME acceptance, override-redirect popup presentation, or owner→window retirement
accounting. The device result records the exact tested scope and the remaining lifecycle defect.
