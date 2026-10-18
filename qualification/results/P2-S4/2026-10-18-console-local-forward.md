# Console managed loopback SSH local forwards

**Date/stage:** 2026-10-18, M5/P2-S4. **Outcome:** loopback forwarding, real external HTTP browser preview, collision status and
independent stop PASS. C09 remains partial: product host-loss UX, persisted forward state and the
full Q-C05 matrix are not qualified.

## Implemented

- `SshAuthenticatedTransport.openForward` opens concurrent direct-tcpip stream pairs without
  replacing the retained single-operation gate. Channels are tracked with their transport and
  closed during actual retirement; a channel-open failure does not stop the retained transport.
- `SshLocalForward` binds **127.0.0.1 only** (requested port or ephemeral), accepts at most 16
  active connections, bounds each direction to a 32 KiB buffer, and reports local port,
  destination, active/total connections and a typed last error. It never accepts remote listeners.
- Console exposes a Forwards panel in an SSH terminal only: local port, destination host/port,
  Start, active status, Stop, and an external-browser action for the local URL. Telnet hides it.
  Closing/disconnecting the terminal closes the listener and its channels; no forward is replayed.
- A **bounded browser handoff** preserves the live terminal/forward while the external browser is
  foreground. Without it, the Activity stop retired the session before Chrome's request
  (`ERR_CONNECTION_REFUSED`). The handoff is explicit, in-process and limited to 120 seconds; on
  return the terminal resumes, otherwise it retires. Every other Activity hide still retires.
- **Lifecycle repair found in the actual flow:** the first disconnect with a forward produced
  `CLEANUP_UNCERTAIN` because the UI closed local forward channels concurrently with transport
  retirement. Local manager cleanup now runs after the engine's retirement joins. The final
  session disconnects as `USER_DISCONNECTED`, and the local port refuses new connections.
- **Focus repair:** the forwards overlay initially reported that it could not take keyboard focus
  because its focus requester was not attached. Attaching it to the first field removed the notice.

## Real checks

| Check | Result |
| --- | --- |
| `SshLocalForwardHostTest` (real MINA SSH fixture `run-ZEkKm8`, direct-tcpip to a loopback echo server) | **4/4**: two concurrent clients echo; transport close with an active connection retires; PTY + active forward retire together; occupied port/invalid destination rejected without stopping the transport |
| Emulator forwarding/lifecycle, APK `d249d4da…` | SSH terminal `Shell open`; Forwards panel with no focus failure; started `127.0.0.1:18080 → 127.0.0.1:9099`; a held `adb shell toybox nc` connection through the phone loopback echoed `finalforward`; Stop/disconnect closed the listener (`nc: connect: Connection refused`); Disconnect with the forward still active ended as `USER_DISCONNECTED` |
| Earlier same-source check | active-connection status reached `1`; snapshots update on accept/close. The final dump happened after the short nc client exited, so it showed 0 |

| External HTTP preview, APK `e684c65a23e02d5472fd77625c16c724933037e2c9b47204398dbcfa3a6872f5` | Forward `127.0.0.1:18081 → 127.0.0.1:8081`; the external-browser action resumed Chrome over the forward and loaded **Cloudy Forward Preview / CLOUDY_FORWARD_PREVIEW** (screenshot [here](2026-10-18-forward-browser-preview.png)). Returning showed `Shell open` and active connection 1. A second Start on 18081 reported `Connection failed: ADDRESS_IN_USE`; Stop refused new local connections while the terminal stayed open. |

The SSH MINA fixture, HTTP/echo listeners and reverse mappings were stopped and removed after the
tests. No credentials or private inventories are in this record.

## Remaining

The external browser preview now loads a real HTTP page. Product host-loss UX after the SSH server
dies, persisted/recovered forward records after process death, destination allow-lists, remote
listeners (not advertised) and the full Q-C05 matrix remain. The browser handoff keeps the SSH
session alive in-process for at most 120 seconds; it is not a background-service lifetime claim.
C09 remains partial.
