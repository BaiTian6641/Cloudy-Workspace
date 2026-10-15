# Desktop clients, latency, and backend boundaries

**Status:** required product lanes, proposed integration. **Stages:** P0-S4/S6, P3-S2/S7/S8, P5-S1/S2/S4. Googlebook is the desktop client; each host exposes the appropriate server. Standard RDP/VNC must work without Cloudy's custom host agent or Console installed.

## Three explicit lanes

| Lane | Intended workload | Client delivery | Host prerequisite / key limit |
| --- | --- | --- | --- |
| High-performance stream | CAD, high-motion desktop, visual tools, low-latency interaction | Qualified external Moonlight first; native integrated surface at P5-S2 after distribution/maintenance gate | Sunshine or another explicitly qualified streaming server; capture/encode/input and user-session permissions |
| RDP | General Windows/compatible-server productivity and administrative sessions | Native integrated RDP client at P3-S7; FreeRDP is the leading engine candidate | Enabled compatible RDP server, supported host edition/session policy, valid credentials and server identity |
| VNC / RFB | Cross-platform desktop compatibility, existing lab desktops and appliances | Native integrated VNC client at P3-S8; maintained RFB client engine selected in P0/P1 | Compatible VNC server, negotiated encoding/security, appropriate route and desktop session |

These are selectable profiles with honest capability differences. RDP can create or reconnect a different user session; VNC can expose an existing or virtual display; streaming commonly captures a host desktop. The app must explain the selected server's actual behavior. Never silently switch protocols after failure, reuse trust records between protocols, or claim pre-boot/secure-desktop/lock access without a passing case.

Parsec and GeForce NOW are experience/performance references. No public embedding/control SDK or reuse right for either is assumed. Compare with Parsec only where its supported client/host combination is available. GeForce NOW is a managed service with a different host/network/workload; its measurements provide context, not proof Cloudy matches a same-host baseline.

## Native client interface

Keep backend-specific engines behind a typed interface: discover supported capabilities; validate profile; connect/cancel; attach native rendering surface; deliver keyboard/text/pointer input; release held input; resize/select monitor where supported; expose measured statistics with provenance; disconnect and report reconnect semantics. These are proposed Cloudy interfaces, not asserted upstream function names.

Profiles identify protocol, endpoint, environment/session, credential handle, server identity policy, input mode, resolution/scale, codec/encoding preferences, audio/clipboard/redirection capabilities, and network policy. Do not flatten unsupported options into switches that appear to work. Keep credentials and protocol data inside the native engine/service boundary; UI observes bounded state and diagnostics.

## RDP integration

[FreeRDP](https://github.com/FreeRDP/FreeRDP) is a candidate with an Android client and an [Apache-2.0 root license](https://github.com/FreeRDP/FreeRDP/blob/master/LICENSE). Dependencies/codecs/build options still need their own license and security review at P1-S4, before P3 embedding.

P3-S7 includes server identity/certificate handling, NLA/CredSSP where required and supported, password/domain prompts, desktop rendering, exact keyboard layouts/IME, absolute pointer, wheel, resize/scaling, clipboard policy, input release, and reconnect/session distinction. Validate certificate changes deliberately; do not ship an unconditional certificate-ignore path or silently downgrade authentication. RD Gateway, smartcards, drive/printer redirection, and multi-monitor extensions enter the published matrix only after separate testing; baseline SFTP remains independent.

Microsoft's [MS-RDPEMT](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-rdpemt/5aa61c5c-e864-4ec6-bd80-e1df51555a48) describes secured multitransport connections over UDP variants. Support depends on both server and selected client build/network. Record actual negotiated transport, graphics pipeline, encoding, and fallback; a preference for UDP is not evidence it is in use. Test blocked UDP and TCP behavior without treating RDP as a game-video codec.

[FreeRDP 3.27.1](https://github.com/FreeRDP/FreeRDP/releases/tag/3.27.1) added Android RAIL/RemoteApp window support. The moving [Android implementation](https://github.com/FreeRDP/FreeRDP/blob/master/client/Android/Studio/freeRDPCore/src/main/cpp/android_freerdp.c) also exposes an experimental-feature callback in this path. Pin a release/commit and qualify it with an actual published RemoteApp before making support claims. RemoteApp is a later optional per-application capability of RDP, distinct from required SSH X11 windows; it does not remove P2-S6.

## VNC integration

Use [RFC 6143](https://www.rfc-editor.org/rfc/rfc6143.html) for baseline RFB negotiation and frame/input behavior. Client/server encodings, desktop resize, cursor, clipboard, and authentication extensions differ. Start with a maintained native client engine and a published server matrix. [LibVNCClient's public header](https://github.com/LibVNC/libvncserver/blob/master/include/rfb/rfbclient.h) is a concrete candidate API; its GPL licensing and the project's [security-maintenance policy](https://github.com/LibVNC/libvncserver/security) must be resolved before embedding. TigerVNC is an interoperability/security reference, not a promise to embed its desktop viewer unchanged on Android.

Prefer a verified encrypted VNC security type supported by the selected build/server, or an authenticated SSH tunnel with the server's listener restricted appropriately. The [TigerVNC manual](https://tigervnc.org/doc/vncviewer.html) names multiple TLS/X509 and authentication variants; not every option verifies server identity or exists in every engine. Record the exact negotiated type and trust policy. A legacy VncAuth-only server needs an explicit protected route/profile; basic VNC password auth alone is not stream encryption. Do not silently accept None/plaintext as a universal fallback.

P3-S8 includes native framebuffer updates, actual negotiated encoding, cursor/keyboard/pointer mapping, resize if supported, bounded decode/work queues, clipboard controls, reconnect, and security errors. Do not discard arbitrary VNC rectangle updates: later updates may depend on earlier framebuffer state. File transfer is not a baseline RFB promise; the integrated SFTP pane works separately when SSH/SFTP is available. Commercial vendor extensions require their own API/license evidence.

## High-performance media path

Retain a maintained native streaming stack rather than designing a new protocol or codec. Proposed path: host capture → hardware encoder → backend transport → bounded native receive/decode queue → hardware decoder → native Surface → display. Send input on the backend's supported low-latency path, independent of heavy file/UI work. Keep audio clocking, frame pacing, color/quality, and thermal/power effects visible in diagnostics.

Query actual codec/profile/level support. Android's [low-latency decoder key](https://developer.android.com/reference/android/media/MediaFormat#KEY_LOW_LATENCY) and [capability](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#FEATURE_LowLatency) are optional API-30 features. Use them only when reported/supported, with a normal-decoder fallback and measured comparison. A hardware decoder or enabled flag alone does not prove end-to-end latency.

Bound queue age. Adapt bitrate first under congestion, then explicitly qualified resolution/frame-rate changes with hysteresis. Stale complete video frames can be skipped only through backend-supported recovery/decoder semantics; never drop reference packets arbitrarily, RDP/VNC state updates, or key/button transitions. Recovery requests/keyframes/FEC are backend capabilities, not generic switches added by Cloudy. Pause bulk transfers when requested to protect interaction, without breaking a protected USB operation.

## Latency and stability gates

Use the [performance protocol](../06-validation/desktop-performance.md): input-to-photon p50/p95/p99, frame pacing, frozen time, connection/recovery behavior, memory, thermal load, and readable output. Match the host/route/codec/profile when comparing standalone Moonlight or Parsec. Proposed Cloudy overhead is at most one requested frame interval at p95 relative to the qualified native baseline; absolute budgets remain hypotheses until P0.

Required failure cases include Wi-Fi roam, route change/direct-to-relay, loss/jitter/bandwidth reduction, decoder reset, focus loss, host sleep/lock, permission revocation, and app process death. Recovery releases held input and tells the truth about the remote session. A launcher-only R2 stream lane is an early milestone; the full integrated high-performance goal remains P5 work. Native RDP/VNC are explicit R2 gates, not external-launch placeholders.
