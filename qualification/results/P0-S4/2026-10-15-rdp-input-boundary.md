# Native RDP input-boundary attempt

**Date:** 2026-10-15, America/Chicago. **Stage:** P0-S4, still in progress. **Result:** the real diagnostic continues displaying the KDE desktop, but this attempt does **not** qualify remote input. Its client counter records **79 accepted JNI input submissions**, while the controlled host target observes **zero input events**. Client queue acceptance is not host delivery or execution. CAP-RDP remains pending.

The adjacent [manifest](2026-10-15-rdp-input-boundary.json) binds the exact APK, 46 source inputs, native payload/asset identities, target compiler/source/binary identities and 176 private evidence files. The [framebuffer result](2026-10-15-rdp-framebuffer.md), [earlier four sessions](2026-10-15-rdp-first-sessions.md) and their immutable evidence remain historical. The final seven-attempt journal preserves all six preceding attempt objects exactly.

## Exact source and route

The separate `dev.cloudy.qualification.rdp` diagnostic is **0.1.3 / code4**, build reference `c4b326e5-ba79-4e1d-8380-e84c729ea365`. Its APK is **12,689,374 bytes**, SHA256 `5d37ba3dd1b499d33d9d78e5e4926b1b6e6021489a0a457cdeb1a5837baef0aa`. The retained source snapshot contains 46 exact inputs based on `d43a7058766d0de8f1b9344202c609af7b8e7c62` plus the recorded input changes; that base commit alone does not identify the new source. Local compiler, Android Lint and **31 JVM tests** pass, with zero test failures/errors/skips. Signature verification and ZIP alignment checks exit zero.

The final eight native libraries and 21 notice/source assets are byte-identical to the accepted 0.1.2 APK. FreeRDP remains 3.32.2 at the accepted exact revision and four patches; no native rebuild or codec change was needed. The source change gates new key-downs on window focus while permitting tracked releases first, uses explicit mouse-press ownership rather than adopting held physical button state, and rejects malformed or oversized UTF-16 before enqueueing. Four pointer-policy and three text-policy tests cover these authored boundaries; they do not establish physical Android event or server behavior.

The same HP Googlebook14, build **CL3B.260622.276.R2 / API37 / arm64-v8a**, connects over the owned USB ADB reverse to the finite loopback-only, privately extracted **KRdp6.4.5-0ubuntu1 / FreeRDP server3.32.1+dfsg-0ubuntu0.26.04.1** fixture on Ubuntu26.04/KDE Wayland, KWin/KPipeWire6.5.5. Normal temporary desktop registration and scoped runtime writes are retained. `--monitor 0` uses this version's workspace path. No host package upgrade, new login, virtual monitor or compositor permission bypass is part of the result.

## Controlled target and actual observation

[rdp_input_target.cpp](../../../tools/rdp_input_target.cpp) is a real Qt Widgets Wayland window with an empty native editor, event-observation canvas and changing color/grid display. It observes only its own widgets, does not open a network listener or global input hook, and excludes clipboard operations. Its strict C++17 compile uses `-Wall -Wextra -Wpedantic -Werror`, GNU C++ 15.2.0 and Qt 6.9.2; compile exit is zero. Source SHA256 is `2918d3fd63021bbde0a994b0193b7121774e51683427391b280759839bdf07f4`; binary SHA256 is `b6217c76b4112a59cc438b1c7c0ef488441d1aa9e565c2e10a6a235b128d3eb5`.

The parent immediately inspected three private screenshots covering the installed/visible diagnostic and target and the no-input observation; screenshots and personal desktop content are not published. The target was shown with a 1200-second finite limit. Controlled pointer movement, clicks and drag were attempted toward the host canvas/editor through the diagnostic. Client input counters increased, but no corresponding target pointer/key/IME/text event was observed. No actual remote key, text, drag, wheel or focus-release correctness is claimed.

Attempt seven (`af11665b-b1e1-4445-9173-4ae8ee366bd3`) records:

| Observation | Actual result |
| --- | --- |
| Certificate / authentication | One accepted certificate, zero rejected certificates, one authentication callback |
| Framebuffer | 2880 × 1800; 5,331 updates |
| Client input counter | 79 successful JNI input submissions; not a wire or host acknowledgment |
| First failure | Absent |
| Stop | Explicit operator disconnect; native thread joined and instance freed |
| Prior evidence | Six earlier attempt objects unchanged; all seven attempts closed |

The target JSONL contains **five records only**: one provenance, three geometry and one window-activation record. There are zero mouse, wheel, key, input-method or text-change records. This is an observed lack of target input, not proof of the exact point at which input was lost. There was **no positive local-input control** establishing that this running target logged a known local event, and **no wire trace** demonstrating RDP input packets. The target's event filter is scoped to its own widgets; events outside them would not appear.

After RDP disconnect, the operator terminated the target with SIGTERM; its process result is **−15** and its log has **no completion record**. Do not interpret this as an orderly target completion, a balanced-release pass or an expired finite deadline. Its pretermination records remain useful bounded observations. The diagnostic's own explicit disconnect and native cleanup are separately recorded.

## Source-supported host prerequisite, not an applied repair

Official [KRdp6.4.5 Plasma source](https://github.com/KDE/krdp/blob/v6.4.5/src/PlasmaScreencastV1Session.cpp) constructs its FakeInput object without calling `authenticate`. Upstream [commit `c6b32c1d5c1ddec42af58464e1d8b889892da2e3`](https://github.com/KDE/krdp/commit/c6b32c1d5c1ddec42af58464e1d8b889892da2e3) adds `d->remoteInterface->authenticate(QStringLiteral("krdpserver"), QString())` and explains that the request is required for KWin to permit input. This authenticates the compositor input object; it is separate from successful RDP NLA credentials.

The inspected [KWin6.5.5 fake-input backend](https://github.com/KDE/kwin/blob/v6.5.5/src/backends/fakeinput/fakeinputbackend.cpp) sets the device's authenticated state on that protocol request and returns without delivering motion, button, axis or keyboard events while it is unauthenticated. This establishes a concrete source-supported host compatibility prerequisite. It does **not** demonstrate that this was the sole cause of the run's missing target events: this attempt lacks the local control and wire evidence above. No backport or host input fix was applied or tested, and the Android client must not be declared correct solely from the server source finding.

The next bounded route is normal portal-based capture/input with explicit existing-session user consent, or an isolated, reviewed upstream backport to the fixture. Add a known local target event before interpreting target silence, then repeat a finite controlled input case and observe actual resulting text/coordinates/release behavior. Do not disable KWin permission checks, switch off NLA or upgrade the host desktop by implication. Physical keyboard/IME/accessibility, broad layouts, color/crop qualification, cancellation races and sustained memory/latency remain separate requirements.

## Preservation, cleanup and scope

The host server exited zero; its listener closed. The temporary desktop registration and cache entry were removed, SAM files were absent, and the fresh host credential/config and key files were deleted. The owned ADB reverse and private device configuration were removed. The diagnostic task is closed, its process is absent, and the final journal remains unchanged after task closure. The original six attempts, including the accepted framebuffer result, remain intact. Baseline0.7.2 and its records were not part of this operation.

The earlier `deef390` framebuffer checkpoint and `d43a705` documentation checkpoint have their own passing hosted checks. Those runs do not validate this later 0.1.3 input edit. The current record's compiler/Lint/JVM and APK checks are actual local results; the native/build and device claims remain distinct. This result neither completes P0-S4 nor promotes CAP-RDP, remote input, focus release, performance, broader servers or Windows to supported.
