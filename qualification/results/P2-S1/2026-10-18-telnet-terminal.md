# Console Telnet terminal: engine, product route and emulator login

**Date/stage:** 2026-10-18, Console P2-S1/P2-S2 extension for newly explicit Telnet product scope.
**Outcome:** real Telnet engine and emulator login/command/disconnect PASS at the boundary below.
This is not a real-server matrix, physical-keyboard, NAWS-device, X11-over-Telnet or release result.

## Implemented boundary

- New shared module `:telnet` (`shared/android/telnet`) with a real TCP NVT client. It supports
  IAC escaping, DO/DONT/WILL/WONT negotiation, TTYPE (`xterm-256color`), NAWS resize, SGA,
  BINARY and server ECHO; unsupported options are refused. Input is bounded to 64 KiB / 64
  entries and converted to NVT CR LF; output is delivered synchronously so renderer backpressure
  applies rather than silently dropping bytes.
- `ConnectionProtocol.TELNET` is allowed for Console profiles. It cannot carry SSH jump or key
  references. The profile editor offers SSH/Telnet for Console and selects port 23 when switching
  from the SSH default.
- The terminal route uses the existing renderer/input bridge and PTY-independent lifecycle. Telnet
  has no authentication handshake, host-key pin, stored credential, SFTP or X11 claim. Login is
  typed in-band. The purpose-built warning states that all traffic, including credentials, is
  unencrypted and that Cloudy does not store Telnet credentials.
- Home Connect routes Telnet to the terminal; Desktop without a terminal reports an honest
  unavailable state rather than opening the SSH file browser.

## Engine verification

`shared/android/telnet/src/test/.../TelnetSessionTest.kt` uses a real loopback `ServerSocket`:

| Case | Observed |
| --- | --- |
| Negotiation and data | TTYPE request answered with `xterm-256color`, NAWS present, server text delivered, IAC IAC delivered as byte 0xFF |
| Input | `user\r` delivered on the wire as `user\r\n` |
| Bounds/close | Invalid resize and empty input refused; post-close input `STALE`; closure joins reader/writer |
| Result | **2/2 pass**, no skips/failures |

## Emulator workflow

API 37 emulator, debug APK SHA-256
`258b71af4afb367a1da8462908a28da2ef69cc72a55aaeaf7cfdab9613f82f9e`; seeded test-only profile
`127.0.0.1:2323` (protocol `telnet`) and `adb reverse`. A private Python TCP/NVT fixture served a
login prompt, echoed data and answered commands; its log recorded `LINE 'user'`, `LINE 'secret'`
and `LINE 'CLOUDYOK'` after the actual terminal input.

1. Home showed the profile with a **Telnet** badge; Connect opened the terminal window.
2. The window showed the unencrypted-Telnet warning and no SSH username/password/key/trust controls.
3. Open terminal reached `Shell open`; typing the login and `CLOUDYOK` reached the fixture as
   the exact CRLF-terminated lines above.
4. Disconnect returned to the connect surface with `Session ended: USER_DISCONNECTED`.

The product window is appropriately `FLAG_SECURE`; `screencap` produced a nearly black image, so
this run has no pixel screenshot. The engine/server effect and the product lifecycle state are the
evidence; the terminal pixel path is not re-qualified here. The final APK was rechecked for the hidden trust control and a second login/`CLOUDYOK` run. The private fixture and reverse mapping were stopped/removed after the run.

## Remaining gates

Seeded-profile setup, not the profile-creation editor journey, was used for this run. Real
third-party Telnet servers, DNS/IPv6, failures after negotiation, NAWS resize presentation,
binary/8-bit payloads, LINEMODE servers, physical keyboard/IME/accessibility and sustained use are
unqualified. Telnet intentionally has no SFTP or X11 forwarding; those remain SSH features. C21 is
partial, and no X11 product acceptance follows from this work.
