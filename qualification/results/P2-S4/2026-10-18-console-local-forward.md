# Console managed loopback SSH local forwards

**Date/stage:** 2026-10-18, M5/P2-S4. **Outcome:** scoped engine and emulator forwarding PASS.
C09 remains partial: external browser preview, persistent forward records after process loss, a
real HTTP service preview and the full Q-C05 matrix are not qualified.

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
| Emulator, APK `d249d4da63bb29d102d57f3cfb1704fb24a25597036bd04b4f74e078647b3df4` | SSH terminal `Shell open`; Forwards panel with no focus failure; started `127.0.0.1:18080 → 127.0.0.1:9099`; a held `adb shell toybox nc` connection through the phone loopback echoed `finalforward`; Stop/disconnect closed the listener (`nc: connect: Connection refused`); Disconnect with the forward still active ended as `USER_DISCONNECTED` |
| Earlier same-source check | active-connection status reached `1`; snapshots update on accept/close. The final dump happened after the short nc client exited, so it showed 0 |

The SSH MINA fixture and host echo listener were stopped and the reverse mapping removed after the
test. No credentials or private inventories are in this record.

## Remaining

The browser action launches the external `http://127.0.0.1:<port>/` URL but was not exercised
against a real HTTP service in this run. A forward is session-bound and not persisted/recovered
after process death; destination allow-lists, port collision UX breadth, remote-listen (not
advertised), and the Q-C05 matrix remain. C09 is partial, not verified.
