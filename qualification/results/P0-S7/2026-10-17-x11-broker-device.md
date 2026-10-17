# P0-S7 device run — two XIDs to two native windows, real SSH xterms

**Date:** 2026-10-17, America/Chicago. **Stage:** P0-S7 experiment packet ([broker experiment](../../../docs/03-platform/googlebook/p0-s7-x11-broker-experiment.md)); Console R1 milestone **M2** (two-XID native-window architecture proof). **Verdict:** **pass for the bounded M2 exit**; P0-S7 and C17 remain partial. **No product or capability promotion follows.**

## Identity

| Item | Value |
| --- | --- |
| Branch / base | `codex/console-r1-implementation`; latest commit `e372bd7`, M2 source uncommitted at run time |
| Probe source | `qualification/probes/x11-broker` (this commit adds it); `cloudy_x11_broker.cpp` SHA-256 `94287960d140d933aaf092263e7c8478fd667780bb23f0ed6d5e6e1ab4505d98`; `X11Environment.java` `d0de888c…c2fc24`; `SshX11Controller.java` `6d34c8a8…da32a`; `XWindowView.java` `91b0a4fb…1d35` |
| Shared source | `SshX11Forwarding.kt` SHA-256 `4d1112fb…0034`, adding the client factory, channel error callback and Android-safe thread construction |
| APK | version `0.0.1` (code 1), minSdk 35 / targetSdk 37, debug; SHA-256 `1eebed3b8e2c22db3763bd3ee34f751e0a73fdff95b3d22fa177223b5e297188`, byte-identical to installed `/data/app/…/base.apk`; last update 2026-10-17 08:02:54 |
| Device | HP Googlebook 14 (`quartz`), Android 17 / API 37, build `HP/quartz/quartz:17/CL3B.260622.276.R2/16560795:user/release-keys`, arm64-v8a, serial `C1L63708PZ`, freeform desktop 2880×1800, 260 dpi, security patch 2026-09-01 |
| X server engine | pinned Termux:X11/Lorie `libXlorie.so`, SHA-256 `632a8b933290197a2eb1228a1a60f7f2928255fe3d242d41d58016df43807a63`; public-API in-process start (`CmdEntryPoint`, no hidden-API shell loader) |
| Broker native lib | `libcloudy_x11_broker.so`, arm64-v8a, SHA-256 `10ac67f917c6b1813aef17693ca62a8d51cb7ebefe08e2d04de649837c2e017a`; NDK r29 `29.0.14206865`, API 35, libxcb 1.17.0 + xcb-proto 1.17.0 statically linked |
| SSH host | extracted OpenSSH `10.2p1 Ubuntu-2ubuntu3.8` (fixture `run-jqowaS`, loopback `127.0.0.1:18163`, ECDSA host key `SHA256:7SlJLo5Ij5fHw12rrXiVu+A6Wyb26GJxCecSBeuVGO0`, key-only); reached from the device through `adb reverse tcp:18163 tcp:18163` |
| Remote client | xterm 407 |

## What was exercised

The probe starts the engine in-process, creates a 128-bit cookie and a private `TMPDIR`/Xauthority, connects a **Composite-redirecting broker** over the local X socket, and opens one native `XWindowActivity` per managed top-level XID. Each view owns a separate broker connection and renders its own XID through `xcb_composite_name_window_pixmap`; input is forwarded through the engine input adapter (pointer/keys) and window-control requests use ICCCM `WM_PROTOCOLS`/`WM_DELETE_WINDOW`. All runs below are on the identified USB device; no emulator or host rendering is used.

### 1. Engine + demo two-XID presentation

1. `START SERVER` → status `Server ready with authenticated Composite broker and engine input`; Lorie start logged a 1280×1024 shared buffer and XCB connection.
2. `START TWO-WINDOW DEMO` created three real X clients on the private display: top-level XIDs `4194304`, `6291456`, and transient dialog `8388608` with `WM_TRANSIENT_FOR 4194304`.
3. The broker listed all three; Android created two independent `XWindowActivity` tasks plus an owned `Dialog` (`Owned X11 dialog 8388608 • parent 4194304`).
4. `am task resize` to `[20,120–1400,1000]` and `[1500,120–2860,1000]` produced two visible, independently focusable native windows side by side. The left window rendered the blue XCB client; the owned dialog rendered inside it as an Android `Dialog`; the right window rendered the purple client. A pointer tap on the dialog reached root `(290,239)` and the X client changed its fill from orange to cyan; the broker connection and per-XID mapping therefore carried real pointer input.

Evidence: `qualification/results/private/x11-broker-01/device-01/two-xid-demo.png` (SHA-256 `27e113f5…601b7d`) and `owned-dialog-pointer.png` (`c014e673…f3f080`).

### 2. Real ordinary-SSH X11 forwarding

1. The host fixture was the tracked P0-S7 OpenSSH/Xvfb configuration, reached over `adb reverse`; the app authenticated with the fixture key and opened an Android local-socket endpoint plus an SSHJ X11 forwarder.
2. `START SSH X11 APPS` executed `xterm -geometry 80x24+40+40 & xterm -geometry 80x24+700+40 & wait` on the remote host.
3. Two real remote xterm windows appeared as XIDs `4194316` (root 700,40) and `6291468` (root 40,40) and were presented in two independent native windows (`Active channels=2, brokerRecords=2`). Each showed the remote shell prompt `polar@…:~$`.
4. A tap in the left window delivered a pointer event at root `(809,142)`. `adb shell input text CLOUDYOK` plus `Enter` reached the remote PTY through X11; the xterm echoed it, the remote shell executed it, printed `CLOUDYOK: command not found`, and returned to the prompt. This is a real remote round trip, not local echo.
5. `WM CLOSE` on the windows sent `WM_DELETE_WINDOW`; the window records disappeared and the activity ended. `STOP SSH` then closed both active channels (the new error callback logged `CHANNEL_FAILED` for each closure), and the remote command ended with exit status 0.

Evidence: `ssh-xterm-pair.png` (`ed0752b0…e387ab`) and `ssh-xterm-keyboard.png` (`5642945a…93842b`). The screenshots contain the private hostname/account and are retained only in the ignored private directory.

## Checks executed

| Check | Result |
| --- | --- |
| `./gradlew -p qualification/probes/x11-broker :app:assembleDebug --offline --no-daemon` with the workspace SDK/cache route | **BUILD SUCCESSFUL**; APK `1eebed…7188`; installed APK is byte-identical |
| Manual device sequence above (server, demo, focus/resize, dialog pointer, real SSH xterms, keyboard, close, SSH stop) | Observed as recorded |
| `SshX11ForwardingFixtureTest` against the live `run-jqowaS` fixture | **5/5 pass** after binding the cookie check to the sshd-allocated display (`localhost:10/11/…`) and asserting the fake-cookie binding; the first isolated run failed only because a live app session occupied display :10 (preserved below) |
| Full `:ssh:test` | 73 recorded / **47 executed / 26 skipped / 0 failed** (opt-in fixtures skip without configuration) |
| `:console:assembleDebug :console:lintDebug` | **BUILD SUCCESSFUL**; Lint no issues |
| `:desktop:compileDebugKotlin -x :vnc:verifyNative` | **BUILD SUCCESSFUL**. The full Desktop APK task remains blocked by the pre-existing, unrelated `:vnc:verifyNative` `UNACCEPTED_PRODUCT_NATIVE` gate and is not claimed as a pass |

The first fixture rerun failed `forwardedX11ReachesTheLocalServerWithSubstitutedCookie` at the hard-coded `localhost:10.0` assertion while the app's live SSH session held display :10. After the app session was stopped, the fixture passed; the test now accepts the sshd-assigned display and *asserts* the stored fake-cookie binding (previously only reported in the failure message), and the isolated rerun passes 5/5. This is a test-robustness correction, not a product regression.

## Boundaries and defects retained

- **No product integration.** This is a diagnostic probe: no Console profile flag, no session coordinator, no window list in the product UI, no clipboard/XDND, no IME commit test, no accessibility or font-scale acceptance.
- **Popup/override-redirect and two isolated host contexts** required by the full experiment packet were not exercised; the demo's owned dialog is a normal transient, not an override-redirect popup. SECURITY/restricted forwarding remains unqualified and is not offered.
- **Lifecycle defect observed:** after one demo/xterm window's native task finished, its XID could remain viewable in the broker list until the forwarding owner was stopped; the list then emptied. This indicates the broker has no product-grade owner→window retirement/accounting yet. It is recorded as P2-S6 broker work, not attributed to an engine root cause.
- Activity/task resize preserved rendering; a forced Activity recreation and process-death restart were not run. The engine's `STOP SERVER` ends the host process (`LorieNative: Server stopped`), so a fresh app start is required after engine stop; this is upstream behavior, not a probe crash.
- GPU/GLX, audio, DPI changes, pointer capture semantics, host loss/reconnect, multiple apps with owned dialogs, and remote VS Code remain unqualified. A single renderer screenshot is not pixel acceptance.

## Capability and next step

CAP-X11 stays **pending/experimental**; no stage promotion is claimed. The M2 architecture gate is satisfied for the bounded two-XID/native-window result, so the next Console R1 milestone is **M3** (formal terminal adapter over the retained PTY), while P2-S6 broker work inherits the lifecycle, popup, IME and security rows above.
