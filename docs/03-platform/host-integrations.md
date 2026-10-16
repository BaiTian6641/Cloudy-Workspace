# Host integration reference and qualification

Keep OS adapters behind the shared host interface. Advertise observed capabilities rather than one universal “host supported” flag.

| OS | Baseline remote work | Session-sensitive integration | Required evidence |
| --- | --- | --- | --- |
| Windows | OpenSSH/PTYS/SFTP; selected stream host | Interactive user helper, capture/input permissions, notification access | Console vs RDP session, lock/logout/reboot, GPU/driver, elevation boundary |
| macOS | SSH/tmux where installed; selected stream host | Capture/input permission, logged-in session, own-job events | Permission denial/revocation, lock/headless/display changes, restart |
| Linux | OpenSSH/tmux; compositor-qualified stream host | Portal/KWin/wlroots/KMS adapter as qualified, user session notifications | Compositor/version, Wayland/X11, capture/input method, privilege and lock behavior |

## Windows notifications

Microsoft documents `Windows.UI.Notifications.Management.UserNotificationListener`, a manifest capability, and user-granted access. Check access before use: revocation can appear as an empty result. Text/source metadata and dismissal are separate from action fidelity. Packaging, background task registration, and desktop-process interoperability need a P4 spike. [Official listener guide](https://learn.microsoft.com/en-us/windows/apps/develop/notifications/app-notifications/notification-listener).

## macOS notifications

The initial commitment is Cloudy-owned job events and supported app integrations. The public [UNUserNotificationCenter reference](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter) is an app notification-management entry point, not evidence for reading every other app's notifications. The page's dynamic rendering limited this review; record universal capture as unestablished rather than making a negative claim about every possible mechanism.

## Linux notifications

The [freedesktop notification specification](https://specifications.freedesktop.org/notification/latest-single/) defines client/server notification messaging and capabilities. It does not, by itself, establish a portable authorized observer-history API for Cloudy. Evaluate supported desktop integration separately; do not replace the user's notification daemon simply to intercept messages.

## Streaming and lifecycle

Native RDP/VNC clients connect to ordinary enabled compatible servers without the custom Cloudy agent. Record actual host edition/server product, user/session type, certificate/authentication/security, negotiated graphics/encoding/transport, monitor behavior and disconnect/reconnect policy. RemoteApp needs actual server-published applications and its own qualification; it is not X11 forwarding. See [desktop backend design](../02-architecture/remote-desktop-backends.md).

Record the exact [Sunshine release/setup instructions](https://github.com/LizardByte/Sunshine/blob/master/docs/getting_started.md), capture path, encoder, input path, and service/user-session topology. Test unlocked, locked, logged out, rebooted, sleeping, resumed, monitor-off, unplugged, and headless states independently. Backend release documentation may differ from moving `master`/`latest` references; qualification uses pinned versions.

SSH X11 needs permitted forwarding, xauth and X11 applications on the host; native Googlebook windows are the client's broker responsibility. USB plug-and-play needs an enrolled consumer with compatible drivers and a reachable provider. A host-side consumer-control adapter may use supported IPC through a narrow paired helper or authorized SSH path; standard desktop protocols do not thereby depend on it. Query real backend state before auto-attach and preserve programming protection.

## Files and Drive

Google's [Drive desktop requirements](https://support.google.com/drive/answer/2375082) are the support authority for host sync clients. Do not treat the Android provider or Linux rclone as the same implementation. When evaluating Linux [rclone mount/VFS](https://rclone.org/commands/rclone_mount/), explicitly test cache writeback, offline behavior, and conflict risks. Keep active repositories and build databases host-local.

## Adapter result format

Each adapter should report backend version, capability, observation time, session identity, required permission, last error, and supported recovery action. A helper process that starts successfully has not yet proved capture, input, notification fidelity, or physical device ownership.

## Parallel construction entry

[HOST-EXP01](host-core-entry.md) provides a finite Go local runtime diagnostic; it does not observe these session-sensitive APIs. The [parallel execution contract](../05-delivery/parallel-execution-contract.md) assigns disjoint host-core/OS work while Googlebook development proceeds. Keep authenticated integration gates, real OS observations and source/build/physical evidence separate.
