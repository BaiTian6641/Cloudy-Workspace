# VNC host X11 canvas source and build review

**2026-10-15:** [tools/vnc_input_target.cpp](../../tools/vnc_input_target.cpp), revision `xcb-input-1`, compiles with the existing host Qt 6.9.2 and GCC 15.2.0 toolchain. At this source/build checkpoint the binary had **not been launched**. The later [finite fixture result](../../qualification/results/P0-S4/2026-10-15-vnc-first-trust.md) records its actual isolated Xcb execution and time-limit cleanup. This is an authored finite qualification target for a parent-owned isolated Xtigervnc fixture, not evidence of a VNC connection, actual input delivery or framebuffer capture.

The new file reuses the public Qt widget/event approach of the existing RDP target without changing that Wayland-only source. It explicitly selects `QT_QPA_PLATFORM=xcb`, strips all arbitrary Qt arguments, and rejects a platform name other than `xcb`. Its requested window is 1280 × 720 with a 640 × 360 minimum. The paint contains color bars, a grid and an alternating marker. A 250 ms timer increments a separately labelled timer tick; paint callbacks have their own counter. These counters cannot be mistaken for received input.

Only the canvas's own Qt mouse, button, wheel and key event handlers increment input counters. Actual mouse positions populate a fixed 64-point ring and painted crosshair. Neither timers nor pointer queries insert motion. Qt's [`QWidget`](https://doc.qt.io/qt-6/qwidget.html), [`QMouseEvent::position()`](https://doc.qt.io/qt-6/qmouseevent.html#position), [`QKeyEvent`](https://doc.qt.io/qt-6/qkeyevent.html) and [`QGuiApplication::platformName()`](https://doc.qt.io/qt-6/qguiapplication.html#platformName-prop) provide the public interfaces. The target cannot identify the transport or distinguish locally delivered input from a protocol driver's delivery.

The journal contains counters, coordinates, button masks and controlled numeric marker codes only. F8, F9, Shift, Control and Alt have numeric codes; other keys are recorded as code zero, with no `text()`, native scan code, editor content or arbitrary printable key code. There is no editor, clipboard action, network operation or global interception. Use only controlled markers during qualification. Held marker state changes only on actual non-repeat press/release events; focus changes do not fabricate releases.

Invocation requires an absolute existing `--output-dir` owned by the current user with no group/other permission bits, and optional `--seconds 1..600` (default 600). Duplicate/unknown arguments and malformed numeric values fail before GUI initialization. The final directory is opened with `O_DIRECTORY|O_NOFOLLOW`; `openat()` exclusively creates `events.jsonl` with `O_EXCL|O_NOFOLLOW`, mode 0600, bound to that directory descriptor. Existing output files are rejected. Parent directory traversal remains the operator's scoped fixture-path responsibility; this is not a sandbox for untrusted paths. Journaling is bounded to 4,000 records and 2 MiB, reserving space for completion. I/O failure exits unsuccessfully, and an unavailable completion record is not a pass. Timer expiration, operator close or journal exhaustion terminates the fixture; the normal Qt event-loop deadline does not forcibly preempt a blocked process, so the parent fixture must also retain its independent process-group lifetime bound.

The exact build command, compiler identity, source hash, binary hash and exit status are retained in `.cache/native-vnc-host/canvas-02/build.json`; compiler output is `compile.log`. The equivalent command is:

```sh
c++ -std=c++17 -fPIC -Wall -Wextra -Wpedantic -Werror \
  tools/vnc_input_target.cpp $(pkg-config --cflags --libs Qt6Widgets) \
  -o .cache/native-vnc-host/canvas-02/vnc_input_target
```

The actual invocation uses an argument array, with compiler flags from `pkg-config`; no shell interpretation of source/path content occurs. Source bytes were captured before compilation and checked unchanged afterward. The initial canvas-01 compile failed on a missing explicit `QWidget` include; its log and receipt remain in `.cache/native-vnc-host/canvas-01/` as `initial-compile-failure.{log,json}`. Its corrected compiled binary and receipt are preserved too. The scoped graceful-stop increment is compiled separately in canvas-02. The corrected compile returned zero with no warnings. No new dependency or global install was used.

| Frozen artifact | Identity |
| --- | --- |
| Source SHA256 | `e4db543cf4169dcb5db5bfa348c1c8ecefdb32fc36ab26f960f20a6a543061a3` |
| Binary path | `.cache/native-vnc-host/canvas-02/vnc_input_target` |
| Binary bytes | 156,472 |
| Binary SHA256 | `112b670652213c700bd0baee568dee1a67e8baf47cd90bac793fc0a2371beb1a` |
| Compiler | `c++ (Ubuntu 15.2.0-16ubuntu1) 15.2.0` |
| Qt | 6.9.2 |

The graceful-stop increment retains a duplicated validated directory descriptor until canvas destruction. Every 250 ms, the existing timer checks only the fixed basename `stop` through `fstatat(..., AT_SYMLINK_NOFOLLOW)`. It accepts only a regular file owned by the current user with no group/other permission bits. It neither reads the marker content nor follows a symlink, and accepts no arbitrary command or path. A valid marker invokes `finish("operator-stop", 0)` to persist actual accumulated counters before exit. Invalid/missing markers do not alter the lifetime/event/byte bounds. The marker is not deleted or modified by the fixture. The build receipt labels this variant `xcb-input-1-private-stop`; the window/journal base revision remains `xcb-input-1`, so the receipt and exact source/binary hashes identify the accepted variant.

The parent may later launch this binary with the isolated fixture's scoped `DISPLAY` and `XAUTHORITY`, a fresh private output directory, and an external finite supervisor. That execution was pending at the source/build review; its later result is linked above. No X server, window, listener, device, installation or Git operation was performed for this source/build checkpoint. No synthetic input test is presented as actual VNC evidence. Project documentation validation is recorded at worker handoff.
