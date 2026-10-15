# P0-S2: existing-task restoration after background-process death

**Run:** 2026-10-15, approximately 06:27–06:37 UTC. **Verdict:** pass for restoration of this diagnostic task after an ADB-injected background-process kill on the identified Googlebook. **P0-S2 remains in progress and CAP-PLATFORM remains pending.** The first kill request did not terminate the process and is retained as an unsuccessful attempt.

## Source and device identity

The installed debug probe is `dev.cloudy.qualification.baseline`, version **0.2.0 / 2**, report schema **2**, built from [13dce23dd4052163f22703752b16438ec81c8306](https://github.com/BaiTian6641/Cloudy-Workspace/commit/13dce23dd4052163f22703752b16438ec81c8306). APK SHA-256: `4a7a47df18b8d48541ce7f1d7ed347c0d6e48c2f79898c8e65064199d1596b32`.

Target: **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**. This is the same installed source/APK as the earlier [window run](2026-10-15-window-probe.md), which links the successful compiler, lint, 17 JVM tests and both hosted workflows. No code or dependency changed for this experiment.

The [public manifest](2026-10-15-process-restoration.json) records exact allowlisted before/after projections, eight selected old-process events, eight new-process events and **27 private artifact hashes**. Raw command output, process/user identifiers, layouts, screenshots and unrelated desktop content remain private. Host capture and device timestamps are reported clocks, not synchronized performance measurements.

## Attempts and observed outcome

The primary agent controlled only the selected probe package through ADB and the official Android CLI. Before interruption, window B had an existing document task and saved marker/counter state. Stop/save callbacks and the private report were retained before the next process could replace the diagnostic file.

| Attempt | Observed state and action | Result |
| --- | --- | --- |
| Immediate request after HOME | Package-scoped background kill at approximately 06:27:02 UTC returned exit 0. Five subsequent process queries still found the original process. Later diagnostics showed `IMPORTANT_BACKGROUND` (7), adjustment 225, `cached=false`. | **Death not established.** Command return alone cannot satisfy this test. |
| Delayed request after native minimize | Minimize was recorded at 06:28:46 UTC. At approximately 06:36:28, diagnostics showed `LAST_ACTIVITY` (15), adjustment 700, still `cached=false`. One scoped background kill followed at approximately 06:36:44. | The process query returned exit 1 with empty output, establishing absence before restoration. |
| Existing-task restoration | The primary agent opened Recents, inspected the current probe card bounds and selected that existing task. | A different process identifier and start timestamp, new Activity generation, `restored-bundle` and unchanged task/logical identity/C2 state were observed. |

The roughly 462-second spacing between the minimize and later diagnostic records does not measure a required wait or an exact eligibility threshold. The [method and pinned source investigation](../../../docs/03-platform/googlebook/p0-s2-process-lifecycle-method.md) explain why a stopped process can remain protected. Android 16 upstream internals provide diagnostic context; they are not asserted to be this HP Android 17 build's implementation. Neither attempt observed `cached=true`.

## Restoration identity

| Observation | Before the successful kill | After selecting the existing Recents task |
| --- | --- | --- |
| Logical window ID | `778b3054-7854-4046-afcb-a5afb8edbed3` | Same |
| Observed task | `1000150` | Same |
| Marker / change count | C / 2 | C / 2 |
| Activity generation | `ba5ed942-401d-4433-9854-b81b5d25cabd` | `46262590-7f70-41b8-a421-ab99c4e15ba1` |
| Process start, device UTC | `2026-10-15T06:15:28.034452Z` | `2026-10-15T06:37:12.976557Z` |
| Report revision | 168 | 8 |
| State source | `restored-bundle` | `restored-bundle` |

An intervening same-process recreation occurred after the earlier window run; the exact pre-kill generation above comes from the retained pre-kill report, not that earlier run's final snapshot. The new process starts a fresh bounded recorder. Its revision reset and absence of the previous closed-window history are expected diagnostic behavior, not loss of the restored Activity's state.

The primary agent immediately inspected the restored screenshot. The visible task/logical identity, C/2 state, `restored-bundle` and revision **8 / savedRevision 8** matched the exported report. Input was injected; no physical keyboard or pointer transition is claimed.

## Review and limits

The evidence author projected the private records into the manifest. The primary agent verified the identities and all 27 hashes. A separate reviewer independently checked the manifest against the retained artifacts, including the initial five unchanged-process queries, later process absence, new process identity, actual Recents selection and selected lifecycle events. The method's pinned upstream source hashes were independently verified as well.

This result establishes saved UI state restoration for **one existing diagnostic document task after a verified, injected background-process kill**. It does not establish natural low-memory killing, cached-process behavior, force-stop or task-dismissal restoration, reboot/power-loss durability, SSH/SFTP/stream continuity, USB ownership recovery, service permissions/timeouts or production-app support. Those require their own implementations and experiments; this result does not close P0-S2 or any product capability gate.
