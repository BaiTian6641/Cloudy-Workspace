# Trust, permissions, and lifecycle design

This specification defines implementation boundaries for ordinary development workflows, not a claim of completed security review.

## Trust boundaries

1. Verify SSH host keys before authentication; a tailnet address does not establish server identity.
2. Enroll a distinct app identity and scope for each host agent. Use authenticated encrypted transport with explicit pairing and revocation.
3. Store credentials in the owning app. Keystore can protect wrapping/signing keys, but algorithm support and direct SSH signing must be qualified. See [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
4. Keep profiles exported between apps or files non-secret. Explicit import should show field changes and references to credentials that require separate enrollment.
5. Treat renderer output, filenames, notifications, host metadata, and repository-generated pages as untrusted display content.

## Renderer boundary

Bundle terminal assets locally, restrict navigations/origins, validate every bridge message, bound message sizes, and open external links/previews in the normal browser. Never give the renderer a generic `exec`, filesystem, credential-export, or host-RPC function. Native code owns the SSH engine and secrets. Google's [WebView bridge guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) motivates this boundary; the exact implementation is tested in P1/P2.

## Android lifecycle

Activities/windows can resize, lose focus, stop, or be recreated. The process can die. Persist view metadata and transfer journals transactionally; rely on remote sessions for process durability. Recover unknown operations by querying their authority instead of replaying them.

Select a valid foreground-service type for each actual operation and target OS. An SSH connection does not automatically justify any particular service type. Review [service types](https://developer.android.com/develop/background-work/services/fgs/service-types) and [timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout); dataSync has duration restrictions on relevant Android versions. P0/P1 must record an operation-by-operation strategy and fallback before background reliability claims.

Do not mislabel SSH as media playback. Bound wake locks to explicit work. Stop hidden video at its backend, release pointer capture on focus loss, clear held remote modifiers on disconnect, and expose background/USB power policy. Lid closure and sleep behavior need actual-device evidence.

## Permission and failure behavior

| Permission / access | Request point | On denial or revocation |
| --- | --- | --- |
| File URI | User selects/drops a file | Keep job blocked with reselect action; never substitute broad storage access |
| USB device | Explicit attach/setup | Show unavailable/permission-required; reconcile actual consumer state |
| Notifications | User enables alerts | Keep in-app activity and settings link; do not repeat-prompt |
| Host capture/input | Host setup within user session | Show exact host/backend reason and supported next step |
| Pairing / SSH key | Explicit enrollment/connect | Preserve profile; stop privileged operation |
| Local tunnel | Explicit start | Explain bind/destination error and leave other sessions running |
| X11 display | Per-host forwarding enrollment and isolated local X server | Show restricted/trusted capability truthfully; stop wrong-cookie/context connections |
| RDP server identity | Connect and certificate change | Show verified identity or deliberate trust flow; no unconditional ignore/downgrade |
| VNC security | Explicit profile and negotiated security type | Require supported protected route; no silent None/plaintext fallback |
| USB auto-attach | Reversible enrollment for device and one host | Pause on denied permission, unknown/competing/protected ownership; no broad automatic hub claim |

X11 app windows use a broker/process lifetime independent of the shell Activity but cannot promise GUI survival after X connection or app process loss. RDP/VNC session recovery follows the server's actual session model. An auto-attach intent is not permission or ownership evidence. [X11](x11-windows.md), [native desktop clients](remote-desktop-backends.md) and [USB plug-and-play](usb-plug-and-play.md) specify those boundaries.

## Logging, privacy, and diagnostics

Bound logs by size/age and include timestamps, stage/request IDs, app/backend versions, redacted error chain, capability snapshots, and measured counter provenance. Do not record terminal transcripts, passwords, tokens, private keys, OAuth caches, or clipboard contents by default. Diagnostic exports show a preview and exclude personal paths/hostnames where requested.

## Host separation

User-session helpers handle capture permission and desktop-specific integration. Privileged helpers expose narrow typed operations. Record the actual error/stdout/stderr of failed backend launch in a bounded diagnostic log. Synthetic CI launch tests do not certify physical permissions, secure desktop, capture, or USB behavior.
