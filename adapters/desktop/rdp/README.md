# Native RDP client engine

**State:** planned adapter boundary; no implementation. **Stages:** P0-S4, P1-S4, P3-S7.

FreeRDP is the leading candidate, pending pinned Android/NDK build, actual dependency/license/security review and Googlebook qualification. Own protocol negotiation, server identity/authentication, native graphics/input callbacks and actual negotiated statistics. RemoteApp, gateway and redirection features need separate evidence.

Read the [backend design](../../../docs/02-architecture/remote-desktop-backends.md). Integrate through the shared native Desktop boundary; no unconditional certificate ignore, custom host-agent prerequisite or external-launch-only completion.
