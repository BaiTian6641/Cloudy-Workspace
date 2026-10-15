# P0-S2 — Existing-task restoration after process loss

**Checked:** 2026-10-15. **Owner:** Android/platform developer and device/QA owner. **Status:** diagnostic method and upstream explanation, linked to the independently reviewed [restoration run](../../../qualification/results/P0-S2/2026-10-15-process-restoration.md). The run has a bounded pass; natural low-memory behavior, product operations and CAP-PLATFORM remain unqualified.

Use the [window probe contract](p0-s2-window-probe-contract.md) and [operation-lifetime note](p0-s2-operation-lifetimes.md). The existing document task must survive the experiment so Android can restore its Activity from saved state. Activity recreation, task dismissal, force-stop and actual process death are different stimuli.

## First attempt and its limit

The retained run records that the first package-scoped `am kill --user current` returned zero, but the original process remained present through five bounded checks. B had reported stop/save callbacks. Package-scoped diagnostics showed `IMPORTANT_BACKGROUND` state **7**, `cached=false`, adjustment **225**, and reason `perceptible-act`. Returning B through Recents and then minimizing it again did not immediately change that priority. **Process death was not established.** This is an observed outcome, not evidence that the kernel or OEM followed a particular upstream branch.

The later bounded retry succeeded: B was minimized at `06:28:46.584793Z`, and a later observation around `06:36Z` showed `LAST_ACTIVITY` state **15**, still `cached=false`. The scoped kill then removed the original process. Selecting existing Recents task **1000150** restored B's same logical ID and **C/2** marker/counter through its Bundle, with a new Activity generation and process-run timestamp `06:37:12.976557Z`. The visible report showed revision 8 saved. This establishes an injected **background-process** kill, not a cached-process or natural low-memory kill; the observation interval cannot establish an exact five-minute OEM transition.

The [dedicated run](../../../qualification/results/P0-S2/2026-10-15-process-restoration.md) and [JSON manifest](../../../qualification/results/P0-S2/2026-10-15-process-restoration.json) bind the exact source/APK/build, before/after identities and private artifact hashes. The primary agent and an independent reviewer checked the retained evidence. This method supplies diagnostic context; the run defines the accepted scope.

## Why a stopped task can remain protected

G69–G71 pin Android's `android16-release` source to commit **`99b01a65cc4c104933788b3143285ab6bae65827`**, committed **2025-05-07**. These are inspected upstream implementation details, not a public SDK guarantee or the HP Android 17 build's source.

| ID / pinned source | Inspected behavior | Git blob SHA-1 |
| --- | --- | --- |
| G69 — [OomAdjuster.java](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/am/OomAdjuster.java) | `PERCEPTIBLE_TASK_TIMEOUT_MILLIS` is five minutes measured using uptime. Under `Flags.perceptibleTasks()`, a qualifying recently stopped task can receive `perceptible-act`, adjustment 225 and `IMPORTANT_BACKGROUND`. Expiry can produce `stale-perceptible-act`, adjustment 700 and `LAST_ACTIVITY`; it does not guarantee cached state. | `115ae41d12ff441036e224ca22fcfdd70f528046` |
| G70 — [ActivityManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/am/ActivityManagerService.java) | `killBackgroundProcesses()` supplies `ProcessList.SERVICE_ADJ` as the package-kill minimum adjustment. | `59c5e0e7fc2c4fe7f492a1018ee8819202836dfe` |
| G71 — [ProcessList.java](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/am/ProcessList.java) | `SERVICE_ADJ=500`; `PERCEPTIBLE_MEDIUM_APP_ADJ=225`; `PREVIOUS_APP_ADJ=700`. The kill routine skips a process whose applied adjustment is below the minimum. | `ad47e67b93327b2ee04a190a2ad09da199eb8f7a` |

This code offers a plausible explanation for a successful shell return with an unchanged PID. Device diagnostics and actual PID disappearance decide the experiment. Other components/dependencies can retain higher priority, and five wall-clock minutes need not equal five uptime minutes. G72's [process lifecycle guide](https://developer.android.com/guide/components/activities/process-lifecycle), updated **2025-02-10**, explains that component/dependency importance governs process state and that destruction callbacks are not guaranteed before process loss.

## Bounded test method

1. Preserve a private pre-interruption report for B: current task ID, logical window ID, Activity generation, marker/counter, PID and `processStartedAtDeviceUtc`. Keep the APK/source/build identities with the run. The next process can overwrite `baseline.json`.
2. Hide every probe window without finishing or dismissing a task. Confirm the stopped/saved transition and inspect the actual process importance. HOME alone does not prove that every desktop window became invisible. Do not assume that an asynchronous final report write can finish once the process becomes cached.
3. Leave B hidden while performing independent work. Use a bounded wait extending slightly beyond the upstream five-minute interval and a few package-scoped state observations. Avoid reopening B during that interval. Do not alter flags, timeouts or process priority to manufacture eligibility.
4. If the observed state becomes eligible, perform one package- and current-user-scoped retry with `adb -s "$CLOUDY_DEVICE_SERIAL" shell am kill --user current dev.cloudy.qualification.baseline`. The [official ADB guide](https://developer.android.com/tools/adb) documents that this command kills only safe background processes. Its exit code is not death evidence. If the PID remains, record that result and retain the task; do not substitute force-stop, broad `kill-all`, raw signals or a changed global process limit.
5. After proving the old process is absent, select **B's existing card in Recents** using fresh UI inspection. Confirm its actual task ID. Do not start the launcher component or press New window: either could test a fresh task instead. The [Recents guide](https://developer.android.com/guide/components/activities/recents) documents selecting a task to resume; this target's inspected shell help did not advertise `task focus`.
6. Require a new PID/process-run timestamp and Activity generation, `restored-bundle`, unchanged B task/logical identity and marker/counter, and matching visible/exported state. Capture a settled new report. The recorder's old process event ring is not expected to survive. Missing final callbacks remain missing observations.

Call a successful result **saved-state restoration of the existing document task after an ADB-injected background-process kill**. Use *cached-process* only if cached classification was actually observed. It is not naturally occurring low-memory death, reboot recovery, storage durability or remote-operation survival. A task removed through Recents/finish or force-stopped has different saved-state expectations; the [saved-state guide](https://developer.android.com/topic/libraries/architecture/saving-states) explains that distinction.

The source register holds G69–G72 provenance. API partial observations link the accepted run; broad API qualifications and capability outcomes remain unchanged.
