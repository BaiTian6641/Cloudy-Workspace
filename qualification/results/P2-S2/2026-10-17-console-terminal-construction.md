# Historical Console terminal construction — output passed, input failed

**Date:** 2026-10-17, America/Chicago. **Scope:** first M3/P2-S2 construction, C04/C05; not Q-C02, CAP-TERMINAL or R1 acceptance. **Source:** renderer adoption `3f9ce70` plus the inherited uncommitted Console Activity/session draft. This record preserves that attempt; [the input repair](2026-10-17-terminal-input-repair.md) supersedes its input diagnosis without converting the original failure into a pass.

## Original source and checks

The draft connected saved-profile selection/current trust and transient password admission through `SshAuthenticationCoordinator`, `SshAuthenticatedTransportFactory` and `SshOwnedPty` to the packaged xterm renderer. Console SSH Connect opened the private Activity; Desktop kept its files route. It implemented no key authentication, jumps, background persistence, reconnect, tabs, session-linked files, transfers or X11 product integration.

| Historical check | Observed result |
| --- | --- |
| `:terminal:testDebugUnitTest` | 15/15 frame/flow tests passed |
| `:console:assembleDebug :console:lintDebug` | Build succeeded, Lint no errors for that source state |
| Packaged assets | Authored terminal page, pinned xterm/fit and notices present |
| Construction APK | SHA-256 `3ebaebcb2e55eeb808a275a31f8fa9b8da366aaa4aec51881eb61f7adead831e`; not a release candidate or attribution for later edits |

WebKit1.17.1, Core1.18.0 and profileinstaller1.4.0 were selected through strict offline dependency resolution. Vendor assets were generated from the pinned cache, not downloaded by the build.

## Original device observation

HP Googlebook14, Android17/API37, build `HP/quartz/quartz:17/CL3B.260622.276.R2/16560795:user/release-keys`. Private inventory retains the raw device identifier. Ubuntu26.04 hosted a loopback Apache MINA SSHD2.20.0 password fixture reached through `adb reverse`.

1. The debug APK was upgrade-installed over retained application data. A fixture profile was opened from the workspace home and required fresh native credential entry.
2. The trust screen observed and explicitly saved the exact EC P256 fingerprint. A later fixture restart produced a changed-key dialog, resolved through its explicit save action, not an automatic trust fallback.
3. Logs showed preparation, resolution, authenticated transport, renderer READY148×28 and SSH PTY-channel/shell admission148×28.
4. The real remote interactive process produced `/bin/sh: 0: can't access tty; job control turned off` and a `$` prompt, visible in xterm. MINA's shell used pipes, **not a kernel PTY**. This establishes a real password SSH/renderer output slice, not remote terminal resize/job control or OpenSSH interoperability.
5. Closing/hiding the Activity closed its renderer and remote shell. The inherited source did not yet prove complete helper retirement, stale-attempt fencing or configuration-change reattachment; those required parent review and repair.

Private captures remain `qualification/results/private/console-terminal-01/terminal-output.png` (SHA-256 `8b959e67073092a93c694de28680c5f7152a7c62a21d094a0ec34be551f3be39`) and `ime-key-event.png` (`0ea0b7d0b5cb7d105d2c368a8ec64452635df8ad1fea011c1520d161f1acf5d6`). The latter contains a temporary test diagnostic, subsequently removed.

**Privacy correction:** that draft allowed screenshots of the debuggable terminal Activity, including credential entry. This was not an accepted product policy. The current Activity restores `FLAG_SECURE` for debug and release, excludes autofill and clears credential drafts across pause/profile/permission/recreation. A protected screenshot being black is not evidence of a rendering/compositor defect.

## Original input failure and corrected diagnosis

ADB text/key injection yielded `key=Unidentified code=229`; no intended command text reached the shell. Disabling installed IMEs also failed to establish accepted input; their original enabled/default state was restored. The original hypothesis that only unavailable physical hardware prevented further diagnosis was incorrect.

An isolated real WebView/InputConnection check later reproduced `commitText` accepted but zero native bytes, without SSH or physical injection. Parent traced this to Cloudy's forced `screenReaderMode:true`: the pinned xterm handler excludes Android `insertText` in that mode. Commit `625796e` selects mode from actual spoken-feedback/touch-exploration state and passes three real Googlebook committed-input/control-byte cases. See [the source-bound repair result](2026-10-17-terminal-input-repair.md); system IME/physical input/TalkBack are still not qualified.

## Remaining product gates

M3 remains in progress. Full Q-C02 needs physical keyboard/pointer, installed-system-IME composition/candidate behavior, clipboard/paste/menus, sustained output/backpressure, actual remote resize, alternate screen and accessibility evidence. M1's OpenSSH PTY/resize/SFTP backend result remains separate. Layouts/tmux, session-linked files/edit/drop, forwards, product X11/remote VS Code and release gates remain unfinished. No earlier failure or construction check is relabeled as full Console functionality.
