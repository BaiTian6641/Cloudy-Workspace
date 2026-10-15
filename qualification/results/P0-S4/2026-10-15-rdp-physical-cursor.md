# P0-S4 — Physical cursor observation on the isolated GNOME host

**Date:** 2026-10-15. **Outcome:** the user reports movement working with noticeable lag before capture, then **captured movement failing and local release working**. The client records relative submissions and the target records later position-changing events, but the release edge is not timestamped. Visible captured-motion acceptance fails on this run; relative wire delivery and the cause of that failure remain unresolved. No relative-delivery, 3D or performance pass is claimed. P0-S4 remains active and CAP-RDP remains pending.

The [manifest](2026-10-15-rdp-physical-cursor.json) binds frozen private run-10 evidence: the final journal, user observations, operator limits, host target/fixture logs and cleanup. The [preceding isolated-host result](2026-10-15-rdp-isolated-host.md) remains the record of fixture readiness and controlled editor delivery; its physical stimulus was still pending. This later run supplies the user's actual observation without rewriting that earlier boundary.

## Exact unchanged client and fixture

The installed diagnostic remains `dev.cloudy.qualification.rdp` **0.1.4 / code5**, build reference `dc833678-46b0-47e3-bb4e-1a9cc804cbf5`. APK size is **12,850,147 bytes**, SHA256 `764113366cb2d27d259094833358220d158b2d2b0013050418b8bba0e79b499a`. Device identity remains **HP Googlebook 14**, build **CL3B.260622.276.R2**, Android **API 37**, **arm64-v8a**, security patch **2026-09-01**. No client repair or new APK is part of this observation.

The Ubuntu 26.04 host uses extracted **GNOME Remote Desktop 50.2-0ubuntu0.1**, **Mutter/libmutter 50.1-0ubuntu2.4** and **FreeRDP server 3.32.1+dfsg-0ubuntu0.26.04.1**. Runtime packages remain **PipeWire 1.6.2-1ubuntu1.2**, **WirePlumber 0.5.13-1ubuntu1**, **libei/libeis 1.5.0-3**, **D-Bus 1.16.2-2ubuntu4**, **libapparmor 5.0.2-0ubuntu1~26.04.1** and **Bubblewrap 0.11.1-1ubuntu0.3**. The controlled target uses **Qt 6.9.2 / Wayland**.

The [reviewed finite fixture](../../../docs/03-platform/googlebook/rdp-isolated-host-fixture.md) retains private namespaces, session bus and PipeWire, a surfaceless software compositor, the client-created output and post-connection target startup. The route is namespace loopback through a private pathname UNIX relay to host loopback and the owned USB ADB reverse. This isolates the fixture from the user's KDE session. Software rendering and relays remain part of this exact functional test route; they do not establish general latency or throughput.

## User observation and client journal

| Stage | Retained observation | Interpretation |
| --- | --- | --- |
| Before parent enabled capture | “Movement working but pretty laggy” | Ordinary movement was reported usable with qualitative lag. This is not a captured-relative observation or a latency measurement. |
| After framework capture was confirmed | “Captured movement fails; release works” | Preserve the visible captured-motion failure and reported successful local release. The mouse/trackpad model was not supplied. |

New Android journal **attempt 12**, `74195f6d-f237-42ac-ad1e-b9dccfe9c742`, records pinned NLA, one certificate acceptance, zero certificate rejection, one authentication callback, negotiated standard relative capability and **1280×720** output. Final counters are **398 frame updates, 2,196 JNI input submissions and 873 relative JNI submissions**. JNI acceptance remains queue acceptance, not a delivery acknowledgement.

The parent requested Disconnect. The journal retains terminal event **`native-failure`**, native detail **“The connection was cancelled.”**, and operator stop reason. Native thread join and instance free are both explicitly recorded. `journal-preservation.json` verifies **all twelve attempts closed and the first eleven unchanged**. The cancellation category is retained alongside cleanup facts rather than relabelled as a successful protocol disconnect.

## Target correlation and its limits

The frozen target contains **853 records**, including **833 mouse moves**, **2 mouse presses/2 releases** and focus/window events. The capture-confirmation layout records **Captured · standard relative route**. The request-command record begins at **20:19:59.970948 UTC**; the capture-confirmation command begins at **20:20:03.097461 UTC**. These are host command timestamps, not exact device callback times.

Target provenance records `startedUtc=20:19:00.956 UTC` and `elapsedNanoseconds=3721166`. A coarse host-time mapping uses provenance UTC plus the difference between each event's monotonic elapsed value and the provenance elapsed value. Using a conservative confirmation threshold, **504 mouse moves** occur afterward, spanning sequences **337–850**, with **502 distinct canvas positions**. Their target elapsed values run from approximately **80.598 to 108.083 seconds**; the first is about **18.45 seconds after confirmation-command start**. This separates that target activity from the earlier ordinary movement, without creating a cross-clock latency measurement.

There is **no exact timestamped physical release edge**. The post-confirmation interval may include activity after the user released capture. Therefore neither the 504 target events nor the 873 client relative submissions prove that every target move was delivered while capture remained held, or that a particular move came from a particular relative wire event. No decrypted wire trace exists. The counts support client relative admission and later host target activity; they do not override the user's visible-motion failure or qualify relative delivery.

## Discovered cursor-presentation gap

Read-only source inspection found a concrete presentation gap in the unchanged adapter. The absolute path updates `frame.move(x, y)`, while the [relative submission path](../../probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpSession.kt) queues deltas without updating framebuffer cursor position. The pinned Android native bridge's [`android_Pointer_SetPosition`](https://github.com/FreeRDP/FreeRDP/blob/97eff6ddfbbfaa8e42a126d362686bd001ff8bdc/client/Android/Studio/freeRDPCore/src/main/cpp/android_freerdp.c) returns success without forwarding a position update. The [framebuffer view's cursor state](../../probes/native-rdp/app/src/main/java/dev/cloudy/qualification/rdp/RdpFrameBuffer.kt) also clears its software shape for the default pointer; it has no default software cursor to display during capture.

Those gaps can leave a custom cursor at stale coordinates or leave a default cursor without a visible software representation while the local pointer is captured. This is a **source-supported hypothesis for the visible failure**, not an established explanation of this run and not a repaired implementation. Further work must distinguish presentation, event delivery and application response using a bounded capture-held segment and observed stimulus; it must preserve genuine relative events rather than substitute accumulated absolute motion.

The [earlier KRdp result](2026-10-15-rdp-relative-portal.md) retains its own captured-motion failure and missing server callback finding. This GNOME experiment uses a different server with an inspected relative consumer; it does not erase that result or prove that the newly identified presentation gap explains both runs.

## Operator termination and final cleanup

The parent stopped this fixture deliberately. The target exits **SIGTERM (-15)** and emits **no normal completion record**. Its last focus-out event records zero held buttons, zero last-observed buttons and zero observed held keys. This is a last observed state, not a deadline-completion or complete physical held-input-release pass.

The fixture cleanup records `operator-stop`, **255.453 seconds total / 254.081 seconds active**, and no cleanup error. GRD, Mutter, PipeWire and WirePlumber exit 0; the namespace exits 0. Namespace and host-loopback relays end 143 after termination. Both owned outer process groups are confirmed absent. Generated private key, client configuration and GRD credential directory are absent, with no secret-cleanup errors.

The final observation confirms **RDP process absent, owned host listener absent, owned reverse route absent and device fixture configuration absent** after successful force-stop. The retained operator limit also records a duplicate output-label rejection: subsequent diagnostic reads used unique labels and did not overwrite earlier evidence. Raw logs, layouts, screenshots and transcripts remain private; the public manifest binds their identities.

Visible captured cursor behavior remains a concrete defect to investigate. Physical device identity, timestamped capture-held movement/release, repeated focus/chord and held-input cases, real 3D interaction, sustained resources and performance remain unqualified. Product Desktop, saved credentials and other host lanes retain their separate gates.
