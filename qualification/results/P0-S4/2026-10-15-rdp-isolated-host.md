# P0-S4 — Isolated GNOME host readiness and controlled input

**Date:** 2026-10-15. **Outcome:** the real isolated GNOME Remote Desktop/Mutter fixture presents the controlled target and receives the exact injected editor marker. Standard relative capability and Android framework capture are observed, but no physical relative stimulus was observed during the finite window. The user's later response supplied no movement or release observation. Physical relative movement, release/focus cases, 3D and performance remain pending. P0-S4 stays active and CAP-RDP stays pending.

The [manifest](2026-10-15-rdp-isolated-host.json) binds the unchanged Android build, host packages, eleven-attempt journal, target completion and private artifacts. The [fixture method](../../../docs/03-platform/googlebook/rdp-isolated-host-fixture.md) records namespace, authentication, renderer and display-order corrections. This is qualification evidence for a separate diagnostic, not Cloudy Desktop.

## Exact unchanged client and selected host

Android package `dev.cloudy.qualification.rdp` remains **0.1.4 / code5**, build reference `dc833678-46b0-47e3-bb4e-1a9cc804cbf5`. The APK is **12,850,147 bytes**, SHA256 `764113366cb2d27d259094833358220d158b2d2b0013050418b8bba0e79b499a`, running on **HP Googlebook 14**, build **CL3B.260622.276.R2**, Android **API 37**, **arm64-v8a**, security patch **2026-09-01**. The manifest records all 49 prior source inputs unchanged from the [accepted relative-adapter result](2026-10-15-rdp-relative-portal.md). This host experiment makes no new client compiler or performance claim; it reuses that build's native/Android compilation, Android Lint, 36 JVM tests and actual native queue evidence.

The host is **Ubuntu 26.04**, using extracted **GNOME Remote Desktop 50.2-0ubuntu0.1**, **Mutter/libmutter 50.1-0ubuntu2.4** and **FreeRDP server 3.32.1+dfsg-0ubuntu0.26.04.1**. Existing runtime packages include **PipeWire 1.6.2-1ubuntu1.2**, **WirePlumber 0.5.13-1ubuntu1**, **libei/libeis 1.5.0-3**, **D-Bus 1.16.2-2ubuntu4**, **libapparmor 5.0.2-0ubuntu1~26.04.1** and **Bubblewrap 0.11.1-1ubuntu0.3**. The controlled target reports **Qt 6.9.2** compile/runtime and the Wayland platform.

The parent-owned finite fixture runs bare Mutter, GRD, PipeWire and WirePlumber in private namespaces and a private session bus. Mutter records a **surfaceless renderer without a GPU** and the virtual monitor created for the RDP connection. A private pathname UNIX relay joins namespace loopback to an owned host 127.0.0.1 listener; one owned USB ADB reverse supplies the client route. The current KDE session is not replaced, and no GNOME system/user service is enabled. The software compositor and relay are functional fixture choices, not a latency or throughput benchmark.

## Fixture corrections and two real sessions

Six fixture launches are retained: four failures before readiness, one ready background-only run and one ready run with the correct target. The failures remain evidence: loopback setup after capability restriction, nested argument parsing, credential-prompt input and AppArmor query access, then the compiled default plugin path. The working method sets up namespace loopback before Bubblewrap, permits only the normal AppArmor decision-query endpoint, uses the extracted absolute plugin path and supplies the nonsecret fixture username through the normal command argument with password-only stdin. It does not disable AppArmor or D-Bus policy.

GRD headless uses EXTEND, so a bootstrap monitor and its target are separate from the client-created output. The corrected launch omits that monitor, waits for the real client connection and then starts the controlled target. These method details and official sources are in the [fixture readiness document](../../../docs/03-platform/googlebook/rdp-isolated-host-fixture.md).

| Android journal attempt | Observed result | Limit or termination |
| --- | --- | --- |
| 10, `d446f21b-586a-40a6-86e7-7f4c4ac25a91` | Pinned NLA connection; negotiated standard relative capability; 1280×720; **1 frame update,0 JNI input submissions, 0 relative submissions**. | Background-only output placement. Parent requested disconnect. Native thread joined and instance freed. Journal retains `native-failure` with connection-cancelled detail and operator stop reason. |
| 11, `600a08a9-44c7-45a9-a997-72a3c19167e3` | Correct controlled target; pinned NLA connection; negotiated standard relative capability; 1280×720; **1,107 frame updates, 68 JNI input submissions, 0 relative submissions**. Exact editor marker `CLOUDY_GRD_PROOF` received. Framework capture status observed. | Controlled absolute taps/text reached the target. The physical relative question received no answer during the finite window, and no physical stimulus was observed. The host fixture ended after target completion. Native thread joined and instance freed; server-logoff `native-failure` detail remains in the journal. |

`journal-preservation.json` verifies the **first nine attempt objects unchanged** and **all eleven attempts closed**. Both new attempts record one certificate acceptance, zero certificate rejection and one authentication callback. Their native errors are preserved rather than relabelled as a generic successful disconnect. Attempt 11 specifically retains the server-session-logoff category after fixture termination; this does not negate the separate observed join/free and host cleanup facts.

## Controlled input and capture boundary

The parent's recorded ADB commands inject absolute taps and the nonsecret marker into the diagnostic. The target's 94 records include **2 mouse presses/2 releases, 32 key presses/32 releases, 16 text changes and 1 mouse move**. Its final editor text is exactly `CLOUDY_GRD_PROOF`. The one canvas move occurred **before** capture was requested. These controlled stimuli and the isolated target support bounded absolute-pointer/keyboard delivery; the target observes its own widgets, not decrypted wire events or complete transport identity.

The actual Android layout shows **Captured · standard relative route**. That confirms framework capture and is separate from an input-delivery result. The client journal contains **zero relative JNI submissions** in both new attempts. No physical movement stimulus was observed after capture, so this run provides neither a new relative-motion failure nor a relative-motion pass. After the finite session ended, the user selected **“Another result / unable to test now”**, without supplying a movement or release observation. The physical test remains pending; the response does not justify reopening a session without a new testing opportunity. No actual 3D application was exercised.

The earlier [KRdp relative-motion failure](2026-10-15-rdp-relative-portal.md) remains valid for its exact server/backend and user report. This GRD readiness experiment does not overwrite it, repair KRdp or turn capability negotiation into movement evidence. Physical mouse-versus-trackpad behavior, local release chord, focus/capture loss, held-button/modifier release, wheel and repeated recovery still require their own finite cases.

## Completion and final cleanup

The controlled target reached its **540-second deadline**, emitted a completion record and exited 0. Completion records **zero held buttons, no held keys and zero last-observed buttons**. This balances the observed controlled target sequence; it does not qualify a physical held-input release case.

The fixture records `target-completed`, no cleanup error, **570.867 seconds total / 569.443 seconds active**. Its direct-child receipt records target, GRD, Mutter, PipeWire and WirePlumber exit 0; the namespace relay ends 143 after termination. The outer namespace exits0 and host-loopback relay ends 143. Both owned outer process groups are confirmed absent. Generated TLS private key, client configuration and GRD credential directory are confirmed absent, with no secret-cleanup errors.

The final device cleanup commands remove the owned ADB reverse route and private RDP configuration. The retained route listing is empty; the configuration-removal command exits0. `final-cleanup-observation.json` records a successful final force-stop, **RDP process absent** and **owned host listener absent**. These are explicit final observations, independent of the client's server-logoff error category. Raw logs, layouts, screenshots and private run transcripts remain outside public artifacts; the public manifest records their identities and hashes.

## Remaining acceptance work

Resume with an observed physical relative-motion stimulus against this suitable server and controlled target, then release/focus/chord and held-input cases. Preserve exact client/server/input-device identities, evidence and final cleanup. The new fixture establishes a real rendering/input test lane; physical relative movement and 3D remain unqualified. Sustained resource use, latency, arbitrary keyboard layouts/native IME, accessibility, Windows/macOS host lanes and the product credential vault remain separate work.
