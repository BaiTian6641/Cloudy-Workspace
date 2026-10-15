# P0-S2: transfer preflight, permissions and queued cancellation

**Run:** 2026-10-15, approximately 08:27–08:31 UTC for app actions; later host-fixture cleanup is timestamped separately in the manifest. **Verdict:** bounded configuration, permission and queued-job observations were recorded. The first UIDT attempt remained queued, was never claimed by a worker, and transferred **zero bytes** before cancellation. **P0-S2 remains in progress; CAP-PLATFORM remains pending.**

## Source, device and fixture

The installed diagnostic was `dev.cloudy.qualification.baseline`, **0.4.0 / code 4**, from source [2bdaf423fb280630fb38c103a7427f4d31805b72](https://github.com/BaiTian6641/Cloudy-Workspace/commit/2bdaf423fb280630fb38c103a7427f4d31805b72). Its 7,113,925-byte debug APK has SHA-256 `134cb6bc0d54eb69a3ba9956d6820c07913344022dd164977cf8453837683686`. Installation and launch returned exit code 0. The [source review at that commit](https://github.com/BaiTian6641/Cloudy-Workspace/blob/2bdaf423fb280630fb38c103a7427f4d31805b72/docs/06-validation/2026-10-15-transfer-probe-review.md) and [package audit](../../../docs/06-validation/2026-10-15-transfer-package-audit.json) record the separate compiler/lint/package checks and 61 passing JVM tests. Those tests do not substitute for the device observations below.

The target remained **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**. Model/build provenance is retained in the [baseline](../P0-S1/2026-10-15-usb-baseline.md). The transfer screen and journal share process-start timestamp `2026-10-15T08:27:29.964684Z`.

The primary agent prepared and started a temporary HTTPS fixture on the selected Linux LAN host. It generated **67,108,864 bytes**, with a **524,288-byte/s** throttle and expected SHA-256 `5302fb4146dff35d2ed3784bc3c8f005e2d89c19c2c722387a1f265a8236ee1f`. These are fixture inputs, not measured Android throughput or a received-file result. The private configuration digest was checked against its import record; endpoint, certificate and key contents remain private.

The [sanitized manifest](2026-10-15-transfer-preflight-manifest.json) binds **41 selected private artifacts**, four journal exports, seven own-window UI projections and twelve action records. It retains hashes and selected observations without publishing raw screenshots, UI trees, diagnostic dumps, addresses, usernames, transport serials or certificates. Host and device clocks remain separate; no timing difference is reported as latency.

## Configuration and permission observations

| Case | Observed result | Acceptance boundary |
| --- | --- | --- |
| Missing private configuration | The screen reported `unavailable-FileNotFoundException`, zero attempts and journal revision/saved revision 0. | Honest unavailable state; no transfer was started. |
| Malformed configuration | Explicit Refresh produced `unavailable-JSONException`; the screen remained usable in the same observed process with zero attempts. | Covers this malformed input and refresh path, not every JSON/certificate failure. |
| Valid configuration | A successful import record and later UI readback showed `validated-private-fixture`. | Config parsing/validation was observed; it does not establish a TLS handshake. |
| Local-network denial | The native permission flow was denied. The inspected screenshot showed unavailable Download/Cancel controls; a tap at Download retained zero attempts and revision 0. | This is the app's denied-access admission behavior, not an attempted socket denied by the OS. The layout export does not expose an enabled-state field. |
| Local-network grant | The later screen showed access granted, with valid configuration and still zero attempts. | Grant did not start work automatically; app-route connectivity and revocation remain untested. |
| Notification denial | Notification permission remained not granted and package notifications disabled. An explicit Download action could still queue the attempt. | Notification denial was separate from scheduling. No running-job notification was delivered or assessed. |

The first attempted malformed-config import used an `exec-out`/`tee` command that did not terminate and was interrupted by the primary agent. It is **not counted as a successful import**. The later explicit refresh and successful valid import/readback support the observations above. No app crash was observed in that sequence.

`valid-refresh-layout.json` is the layout inspected **before** its Refresh tap and still contains the malformed-config state. The later denied-download and permission-ready layouts supply the valid readback. Filename wording is not used as proof of an outcome.

## Queued attempt, recreation and cancellation

An explicit Download action created attempt `b2d397ab-be93-4e41-bb99-fa14eac31a82` for job **7352**. The initial, progress-named and still-queued exports were byte-identical **revision 1** snapshots: status `queued`, `claimed=false`, zero bytes written and no verified length/hash. Their export records have different host timestamps; the unchanged journal timestamp is retained rather than represented as fresh progress.

The scheduler diagnostic reported required **CONNECTIVITY** unsatisfied. Its request advertised **WIFI or ETHERNET** and included **NOT_VPN** among its capabilities. The OS-selected network advertised **WIFI and VPN**, without NOT_VPN. This capability mismatch is consistent with the pending connectivity constraint. It establishes neither the physical packet path nor failure of the host's TLS endpoint. No underlying network was selected manually and no device VPN setting was changed by this evidence author.

The primary agent invoked **Recreate transfer window** while the attempt remained queued. The task stayed **1000153**, the process identity stayed the same, and the Activity instance changed from `295a1a0f-5668-4930-b6f3-d23bcc408d74` to `49e0985c-55be-4b6e-8eee-d7cc532e7c0b`. The new screen showed the same single queued attempt and saved revision 1. This is Activity recreation with pending work; it is not evidence that an active transfer survives recreation or process death.

The native Cancel action then persisted **revision 3**, with the event sequence `queued` → `cancel-requested` → `cancelled` and reason `user-cancelled`. The attempt remained unclaimed with zero bytes and no verified digest. Subsequent UI readback showed revision/saved revision **3/3**, one retained canceled attempt and no journal/control error. A later scheduler diagnostic contained no block for the probe's job. This is **queued-job cancellation**, not cancellation of an executing or blocked network worker.

The host fixture log contains its startup message only, with **no HTTP request events**. Together with the unclaimed journal, this provides no evidence of an Android HTTPS connection, received payload or file verification.

## Cleanup and version-label limits

The initial terminal Ctrl-C command returned exit code 1 while the fixture listener remained open. The primary agent then identified the exact fixture process, sent that process SIGINT, and explicitly verified that the listener was closed. The final private stop record reports `fixturePortListening=false`; its hash and check time are retained in the manifest. Terminal-command exit alone was insufficient cleanup evidence. This host harness issue is separate from Android job behavior.

A separately exported baseline report from the 0.4.0 installation still labeled itself **0.3.0** because of a stale hardcoded baseline-recorder field. The transfer journal correctly reports **0.4.0**. This report binds deployment to the audited APK and source commit, not to that stale field. The separate 0.4.1 version-label/network-request correction does not rewrite this run's source identity or supply a successful transfer result for it.

## Review and remaining work

The evidence author checked the selected journal/UI projections, matched the relevant own-control tap coordinates to their inspected bounds, independently inspected the denied-ready screenshot, and hashed the 41 supporting private artifacts. The primary agent performed all device and real-host actions. The primary agent inspected this report and independently verified all 41 private hash/size pairs. A separate reviewer checked every selected journal/UI projection, action record, six control-coordinate matches, source/config/fixture binding, scheduler/network projection and cleanup record. Both accepted the bounded evidence with no actionable finding; the review excludes later 0.4.1 payload results.

API41 now has bounded permission-flow observations; actual app TCP/TLS access and permission revocation remain unqualified. API39 has bounded queue/recreation/cancellation observations; worker execution, notification behavior during work, byte/hash verification, active cancellation, Task Manager stop and process-loss recovery remain pending. The next run must use its own corrected source/APK identity, satisfy the actual app-network constraint, and demonstrate a real transfer before any of those outcomes can be claimed. SFTP, continuous SSH, USB, remote desktop protocols and product readiness are outside this run.
