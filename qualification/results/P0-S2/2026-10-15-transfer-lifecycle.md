# P0-S2: real HTTPS transfer and lifecycle observations

**Run:** 2026-10-15, approximately **08:37–08:49 UTC**. **Verdict:** two finite HTTPS downloads completed with independently matching bytes and SHA-256; active recreation/minimize, two cancellation paths, wrong-CA rejection, host interruption and injected process-loss reconciliation were observed. **P0-S2 remains in progress; CAP-PLATFORM remains pending.**

## Source, device and scope

The installed diagnostic was `dev.cloudy.qualification.baseline`, **0.4.1 / code 5**, from source [a2b3e33d3ba4dabf40bdd1730e1eaf582c84527d](https://github.com/BaiTian6641/Cloudy-Workspace/commit/a2b3e33d3ba4dabf40bdd1730e1eaf582c84527d). Its **6,858,313-byte** debug APK has SHA-256 `704546e5d6a24167c7c982cf3be0e4ab27e2a1fe86221e69aa68f7a1f42d2396`. The deployment record binds that source and APK. The separately exported schema-3 baseline now reports **0.4.1**; the schema-1 transfer journal also reports 0.4.1.

The [source review at that commit](https://github.com/BaiTian6641/Cloudy-Workspace/blob/a2b3e33d3ba4dabf40bdd1730e1eaf582c84527d/docs/06-validation/2026-10-15-transfer-probe-review.md) records the separately completed compiler/lint gate and **61 passing JVM tests**. Hosted checks [38707024426](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38707024426) and [38707024363](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38707024363) passed for this source. The separate [0.4.1 package audit](../../../docs/06-validation/2026-10-15-transfer-route-package-audit.json) binds the merged manifest and packaged notices to this APK. These checks do not substitute for device evidence.

The device remained **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**. The [baseline](../P0-S1/2026-10-15-usb-baseline.md) records its identity. The primary agent performed all device and real-host actions; the evidence author read existing artifacts and independently checked their projections and exported payloads.

The [earlier 0.4.0 preflight](2026-10-15-transfer-preflight.md) remains a separate run. Its retained attempt 1 was queued and canceled without a worker or payload. The attempt ordinals here start at **2**, because the private journal was retained through the 0.4.1 update. A changed app version does not turn the earlier queue-only case into a successful transfer.

The 0.4.1 request removes the default NOT_VPN requirement while retaining the other request defaults and WIFI-or-ETHERNET advertised transports. Connections continue to use only the network assigned by JobScheduler. The evidence establishes an actual app connection to the selected host fixture; it does not establish a direct physical LAN route, bypass a VPN, or qualify every network backend.

The generated fixture is **67,108,864 bytes** with expected SHA-256 `5302fb4146dff35d2ed3784bc3c8f005e2d89c19c2c722387a1f265a8236ee1f` and an intentional **524,288-byte/s** host throttle. This is a correctness/lifecycle experiment, not a throughput or latency benchmark. Host and device timestamps are separate clocks. Input actions were injected through the device-control harness; physical keyboard, pointer and touch behavior were not tested.

## Completed transfer through recreation and minimize/return

Attempt 2 was claimed by the worker and progressed from **4,456,448 bytes** to completion. During actual work, the Recreate transfer window action changed the Activity instance from `75f124d0-8cb3-4140-a70f-7aa4e01ee67d` to `a7ec48e7-11f6-40da-b55f-67fe7fd4378c`, while task **1000153**, process identity and attempt identity stayed the same. The inspected before/after layouts show running bytes **25,886,720 → 26,083,328**. This is same-process Activity recreation, not process death.

The native caption minimize action then hid the window. A private journal export while minimized showed **36,700,160 bytes**; return showed **39,583,744 bytes** in the same recreated Activity and process. The frozen fixture log contains one request for this complete download, supporting continuity through these actions without another HTTP request.

The terminal journal at revision **8196** records `completed`, length **67,108,864** and the expected SHA-256. An independent export of the actual app-private payload has the same length and SHA-256. The terminal states were `queued → running → verifying → completed`. Notification permission was not granted during this transfer; successful work with that permission state does not establish a delivered notification.

Some layout observations during active byte updates show an in-memory revision ahead of the last saved revision. They are reported as distinct fields, not proof that every UI tick was already durable. The completed file and terminal journal are the completion evidence.

## Active cancellation from the window and native notification

| Attempt | Stimulus and result | Independently checked retained artifact |
| --- | --- | --- |
| 3 | Injected tap on the app's Cancel transfer control while running. The journal persisted `cancel-requested → cancelled`, reason `user-cancelled`, at revision **8792**. | **4,849,664 bytes**, SHA-256 `d369753ba1c6350c583d2d990232d05789e7250f33909f1a11f299fc4978709d`. Two exports match byte-for-byte, with the same terminal journal revision across **23.660491 seconds** of host observations. |
| 4 | After notification permission was granted, the window was minimized. The primary agent expanded the actual Cloudy notification and tapped its **Cancel transfer** action. The journal persisted `cancel-requested → cancelled`, reason `user-cancelled`, at revision **12700**. | **31,981,568 bytes**, SHA-256 `b800edac9b0e8cdc93d13f3b436a1d80e01a399da10c568fd556f4a2271047e9`. The later export and journal are unchanged. |

These observations go beyond persistence of a cancellation request: each attempt reached terminal `cancelled`, retained a stable partial, and produced a host connection-interruption event. They do not directly instrument worker-thread exit or thread-pool ownership. Neither canceled attempt has a verified-completion length or digest. Their retained partial hashes establish stability of the sampled artifacts, not complete fixture integrity. The server logged connection interruption for both. Server bytes submitted to TLS can exceed app bytes retained on disk, so those counters are not treated as interchangeable.

Only the Cloudy notification row was projected from the private notification layout: title **Cloudy test transfer**, generated-fixture text, and the native Cancel transfer action. Its actual tap fell within the inspected action bounds; the Cloudy row was absent in the subsequent notification layout. The restored app screenshot shows canceled state with Download available and Cancel disabled. Other notification contents remain private.

## TLS trust rejection and explicit host interruption

For attempt 5, the private configuration changed **only `caCertificatePem`** to a distinct CA. The worker was claimed and failed with **`SSLHandshakeException`**, zero bytes and no payload file. The server logged a TLS `SSLError` without an HTTP request for that attempt. This is an actual wrong-trust-anchor rejection on the device. It does not cover wrong-hostname/IP-SAN rejection, expired certificates, all certificate chains or every network route.

For attempt 6, the primary agent stopped the dedicated fixture after actual progress. The last pre-stop exported journal showed **3,932,160 bytes**. The job failed with **`ProtocolException`**, retaining **4,128,768 bytes** with SHA-256 `91664bf35bdfb03fa0fb8881389c5b801047d45b37ca174b56dc6656d901177a`; it did not mark the truncated payload complete. The fixture controller exited cleanly and a separate check confirmed the dedicated listener port was closed.

The failed journal remained at revision **13210** across subsequent observations. A second partial export matched its bytes and digest. After the fixture was restarted, an observation still showed six retained attempts and the same failed journal; the server had **zero HTTP requests** before the later explicit Download tap. This establishes bounded no-automatic-retry behavior for this interruption. Host shutdown is distinct from Android job stop callbacks, connectivity loss imposed by the OS, permission revocation and process death.

## Explicit retry and injected process loss

An explicit Download action after the restored-server observation created **attempt 7** with a new attempt identity. It completed at revision **21406**. Its final app-private export independently matches **67,108,864 bytes** and the expected SHA-256. The server logged one complete request. This is a new full download, not continuation of attempt 6's retained partial.

The files named `attempt-07-completed-*` were an early sample: that journal was still `running`, and its exported file was only **59,768,832 bytes**. They are not accepted as completion evidence. The later `attempt-07-final-*` journal, observation and payload supply the terminal proof.

Attempt 8 was actively running before the primary agent minimized the window and injected **SIGKILL into the exact validated process under the probe's own package identity**. The signal command returned zero; four bounded process queries found no matching process. This was an injected process loss, not force-stop, task removal, Task Manager stop, natural low-memory termination or a successful job-stop callback.

The saved pre-loss journal was revision **22008**, status `running`, with **4,915,200 bytes** recorded. A concurrently exported pre-loss file contained **5,046,272 bytes**; the post-loss export contained **5,177,344 bytes**. Journal persistence and a live file export are separate samples, so those values are preserved without inventing one atomic snapshot. The unchanged on-disk journal immediately after process loss was historical state, not proof that a worker still existed.

Explicit relaunch returned the existing transfer task **1000153** in a new process, `8dc3c9fc-386f-4063-b4e9-ab2b8d91e106`, with process-start timestamp `2026-10-15T08:48:20.755823Z`. Startup reconciliation changed attempt 8 to **`interrupted`**, reason **`new-process-unfinished-attempt`**. The settled new-process journal was revision **3**; revision counters are scoped to a process and are not compared numerically with revision 22008 as a global sequence. The recorded byte count remains the last saved **4,915,200**, not a completed-file claim.

The post-loss, recovered and later stable exports are byte-identical: **5,177,344 bytes**, SHA-256 `e8cdd3a7b9868d4cbded3d7cd571b5e965f94b3ab2fd489e92a30605f038b549`. The recovered and stable host observations are **64.581215 seconds** apart, with unchanged interrupted state and journal revision. The final journal retains all eight attempts; it separately re-verifies completed attempts 2 and 7 with reason `new-process-file-reverified`. The capacity limit now prevents a ninth Download; that admission limit is not evidence of cancellation cleanup.

A package-scoped scheduler check found no probe-job block after recovery. The frozen second fixture-server log contains exactly two requests, for attempts 7 and 8, and no later request before shutdown. Together with the stable journal/file samples, this establishes bounded recovery without automatic replay. It does not establish indefinite absence of future work, resumability, natural memory-pressure behavior or reboot recovery.

Relaunch already displayed the transfer Activity, so a harness attempt to locate an extra launcher control failed. The successful relaunch, actual transfer-window readback and recovered journal supply the recovery evidence; the failed extra helper action is not counted as an app failure or a successful tap. Both dedicated fixture servers exited through their controllers with zero exit code, and separate port checks confirmed closure.

## Acceptance boundaries and remaining work

The current observations establish a real finite HTTPS transfer and the specific lifecycle/cancellation/trust/interruption paths described above. They do not qualify SFTP, interactive SSH, USB forwarding, RDP/VNC, sustained media sessions or product readiness. The diagnostic holds at most eight attempts and retains partials; it is not a production transfer manager.

Still unqualified: Android wrong-hostname rejection, local-network permission revocation during work, Task Manager stop and OS stop-reason behavior, system timeout/quota behavior, natural low-memory termination, reboot recovery, packet-path identity, resumable transfer, production credential handling and physical input. Wrong-hostname testing was not performed within this eight-attempt run. No capability gate is promoted by this report.

## Evidence review

The [sanitized manifest](2026-10-15-transfer-lifecycle-manifest.json) binds **93 selected private artifacts**, including seventeen latest-attempt journal projections, eight own-window UI projections, twelve action records and twelve independently hashed payload exports. The evidence author checked the actual notification action bounds, relevant native/app controls, the restored canceled-state screenshot, journal identity/state transitions, server events and payload digests. The primary agent performed the actual host/device actions, inspected the report and checked all 93 hash/size pairs. A separate reviewer checked all 93 artifact hashes, seventeen journal projections, fourteen host observations, eight own-app UI projections, twelve action records including nine control-bound matches, twelve payload hashes/sizes, thirteen fixture events and two request counts. The reviewer also checked the actual APK/source binding, wrong-CA-only delta, four process-absence observations, recovery duration, capacity caveat and public privacy boundaries, and visually inspected the own-app cancellation/recovery screenshots. Both reviews accepted these bounded results with no remaining actionable finding.

The public files omit raw screenshots, UI trees, shell dumps, network addresses, hostnames, transport serials, operating-system process IDs, certificate/key contents and other apps' notification contents. Random probe process/Activity/attempt/request identities and task/job IDs remain only as scoped observation keys. No capability gate is promoted.
