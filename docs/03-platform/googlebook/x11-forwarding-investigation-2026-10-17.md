# X11 forwarding investigation and first implementation — 17 October 2026

**Request:** user-directed X11-forwarding research and implementation toward MobaXterm-style support (R03). **Stage:** P0-S7, cases Q-C07/Q-C08; design contract in [x11-windows.md](../../02-architecture/x11-windows.md) and S14 in [screen-specs.md](../../04-design/screen-specs.md). **This document:** research findings, the implemented SSH leg, its host qualification, and the bounded remaining work. Console remains independently useful with ordinary SSH; no custom host agent is involved anywhere in this design.

## 1. How SSH X11 forwarding actually works (verified model)

Per [RFC 4254 §6.3](https://www.rfc-editor.org/rfc/rfc4254.html#section-6.3) (R19) and OpenSSH's configuration manuals (R17/R18):

1. The client sends a channel request `x11-req` on its session channel carrying an authorization protocol name (`MIT-MAGIC-COOKIE-1`), a **random fake cookie** (hex), and a screen number. `single connection` is false for multi-application use.
2. The server binds a proxy listener (default display offset 10, loopback when `X11UseLocalhost yes`), sets the session's `DISPLAY`, and registers the fake cookie in the session's Xauthority file using the real `xauth` (`XAuthLocation`).
3. Each remote X application connection to the proxy becomes an incoming `x11` channel to the client; its payload starts with the X11 client setup block presenting the fake cookie.
4. The **client** replaces the fake cookie with the real local cookie and forwards to its local X server. Cookie substitution is the client's duty — the server proxies bytes only.

Setup-request byte layout (verified against a live OpenSSH/Xvfb exchange, and the trap of getting it wrong is in §4): byte 0 order (`l`/`B`), byte 1 unused, u16 major, u16 minor, u16 name length (bytes 6–7), u16 data length (bytes 8–9), u16 unused (10–11), then 4-padded name and data in the declared byte order.

Trusted vs untrusted forwarding (`ForwardX11`/`ForwardX11Trusted`, R17) governs X SECURITY extension behavior on the client side; it does not change the wire flow above. Restricted/untrusted mode needs real SECURITY-extension support in the *local* X server and must be qualified on the chosen engine before being offered; it is never silently upgraded (unchanged from the architecture contract).

## 2. SSHJ 0.41.2 capability findings (primary evidence, this turn)

- **Present and usable:** `SSHClient.registerX11Forwarder(ConnectListener)`, `SessionChannel.reqX11Forwarding(String authProto, String cookieHex, int screen)`, `X11Forwarder` accepting incoming `x11` channels. Verified in the pinned artifact and by live fixture run (R370, R371; R59 refreshed in substance).
- **Ordering trap (found by fixture failure):** a forwarded channel delivers **no bytes until `confirm()`** is called by the listener, and the documented pattern connects the local endpoint *before* confirming (R371/R372). Cloudy resolves authority → connects locally → confirms → substitutes → splices.
- **Defect (found by fixture failure, confirmed against pinned source):** `ConnectionImpl.sendOpenFailure` writes CHANNEL_OPEN_FAILURE with recipient, reason and message but **omits the mandatory language string** of RFC 4254 §5.1 (R373). OpenSSH 10.2 answers the malformed packet by disconnecting the transport (`channel_input_open_failure: parse msg/lang: incomplete message` in the server log, then `Disconnecting … Invalid open failure message`). **Consequence for Cloudy:** never reject an x11 channel; denials confirm-then-close, which OpenSSH handles cleanly. This is adapter containment, not an upstream patch; a report candidate is noted in the register.
- One X11 listener per connection (upstream documentation); per-trust-context isolation therefore uses separate connections/owners, matching the architecture's isolation rule.

## 3. Implemented leg (shared/android/ssh, engine-level, product-neutral)

- `SshX11Cookie` — 128-bit SecureRandom fake cookie, lowercase-hex wire form; issued per owner, never logged.
- `SshXauthorityCodec` — strict whole-file Xauthority parsing (family/address/display/name/data), local/wildcard cookie lookup; malformed files reject completely.
- `SshX11Handshake` — exact setup-block framing for both byte orders, cookie verification with constant-time comparison, in-place substitution (both cookies are 16 bytes, so lengths are unchanged), with hand-built reference vectors in tests.
- `SshX11Forwarding` — `attach(SSHClient, endpoint, authority, maxChannels=16, handshakeTimeout=10s)`, `enableOn(session, screen)`, `stop()`; bounded channels, setup watchdog, deny-by-confirm-then-close, daemon worker pools, no persistence and no global state.
- Endpoints: `SshX11UnixEndpoint` (AF_UNIX path; Android 35 supports `UnixDomainSocketAddress`) and `SshX11TcpEndpoint`. The Android local endpoint will later connect to the in-process server; the interface already abstracts it.

No product UI, no profile flag and no fake capability were added; Console's terminal does not exist yet (P2 lane), so nothing user-facing changes. Unit tests cover cookies, authority parsing, both byte orders, substitution, malformed input and policy rejections.

## 4. Qualification (real host, real OpenSSH, real X server)

Run record: [2026-10-17-x11-forwarding-host.md](../../../qualification/results/P0-S7/2026-10-17-x11-forwarding-host.md). Fixture: private extracted OpenSSH 10.2p1-2ubuntu3.8 and Xvfb 21.1.22, loopback, pinned ECDSA policy, fixture-owned authority files, displays :98/:99. **5/5 fixture cases pass** (substituted happy path, two-owner display isolation, wrong-cookie rejection, missing-authority refusal without local contact, owner-stop retirement), the OpenSSH reference client cross-checks through the same fixture, and the full module suite is 65 tests / 0 failures.

Two environment traps are documented for the next runner: stale `/tmp/.XNN-lock`/socket files make Xvfb silently fail to take a display (the bring-up script now guards this), and detached background Xvfb processes die with their shell session (the fixture owns its children's lifetime).

## 5. Android display/broker side — current evidence (unchanged risk)

The [engine entry](native-x11-engine-entry.md) stands. This turn staged the pinned Termux:X11 tree (`fa3a8b430e…`, X.Org submodule `65d790bd2…` = xorg-server-21.1.24) with all submodules in ignored storage (`.cache/x11-engine-01/`, not tracked). Its build system is self-contained CMake recipes per dependency (pixman, xorgproto, libxkbfile, libxfont, libepoxy, xkbcomp, bzip2, libtirpc, libxau/libxdmcp, libxcvt…) targeting `libXlorie.so`, needing NDK + CMake + Ninja + Python + Bison. The missing per-XID surface bridge and the hidden-API shell startup exclusion remain exactly as recorded there, and the GPLv3 license constraint (R242) still blocks Console distribution decisions — a private feasibility build precedes that question, as the entry specifies.

### 5.1 Engine build attempt (this turn, real)

`libXlorie.so` compiled and linked from the staged pins on the first attempt: 535 build steps, NDK r29 (`29.0.14206865`), CMake 4.1.2, Ninja, system Bison/Python3, `arm64-v8a`, `minApi 24`, `RelWithDebInfo`. The artifact is ELF aarch64, 21,107,096 bytes unstripped, SHA-256 `632a8b933290197a2eb1228a1a60f7f2928255fe3d242d41d58016df43807a63`, preserved in ignored storage (`.cache/x11-engine-01/build-artifacts/`). It links only public NDK libraries (`libGLESv2`, `libandroid`, `libmediandk`, `liblog`, `libm`, `libz`, `libEGL`, `libc`) and exports `JNI_OnLoad` among 2,355 dynamic symbols.

Two build notes, both about our tree rather than the engine: the recipe's `patch -d` calls do not quote paths, so the build ran from a byte-identical space-free mirror (the workspace path contains a space); and `TMPDIR`-relative xkb output paths are compiled in via generated config. A compile is packet step 1 only — no load, window, or device behavior is claimed.

## 6. Product design implications (spec-level, no implementation yet)

- S14's window list and per-window identity model is unaffected; the SSH leg feeds it `{app session, SSH connection generation, display, XID}` records once the broker exists.
- The Console profile gains an X11-forwarding toggle only at P2-S6 (default off, per host, untrusted-by-default policy with exact unavailability reasons); nothing in this leg waives that gate.
- Retirement: X11 traffic stops when the owning session ends (RFC 4254 §6.3), and Cloudy additionally retires channels on owner stop — both are now covered by fixture evidence.
- MobaXterm parity target (R03): automatic X server availability + per-application multiwindow. The first half is this leg plus the in-process engine; the second is the broker experiment's remaining steps.

## 7. Next bounded steps (packet order)

1. Timeboxed `libXlorie.so` NDK build attempt from the staged pins, recording the exact toolchain/ABI/errors (no product integration from a compile alone).
2. The two-XID broker proof against Composite-redirected pixmaps into two native surfaces (packet steps 2–4).
3. SSH-leg reuse on the Googlebook with the Android endpoint, once the terminal PTY lane (handoff item 2) lands.
4. SECURITY/restricted-mode qualification or an honest unavailable state; clipboard/XDND stay separate capabilities.

## 8. Register additions

R370 SSHJ pinned `registerX11Forwarder` API; R371 SSHJ forwarded-channel `confirm()` ordering; R372 SSHJ `SocketForwardingConnectListener` confirm-then-pump pattern; R373 SSHJ `sendOpenFailure` missing-language-field defect evidence (upstream report candidate). All pinned to sshj commit `6fad160fe36605a68847ac8a527750c615b4bd04` and checked 2026-10-17.
