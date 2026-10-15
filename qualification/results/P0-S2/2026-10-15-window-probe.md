# P0-S2: two real Googlebook windows and lifecycle evidence

**Run:** 2026-10-15, approximately 06:15:28–06:23:17 UTC (01:15–01:23 America/Chicago). **Verdict:** the bounded two-window, Activity-recreation, native resize/tile/focus/close and save-error/recovery experiments passed within the coverage below. **P0-S2 remains in progress; CAP-PLATFORM and all product capability outcomes remain pending.**

The primary agent operated the explicitly selected USB target and inspected the private screenshots. Input was injected through ADB/the official Android CLI, including interactions with native caption controls. These are real device observations, not a physical keyboard/trackpad/mouse qualification. The evidence author read the saved artifacts and did not repeat device operations.

## Exact identity and build checks

Source: [`13dce23dd4052163f22703752b16438ec81c8306`](https://github.com/BaiTian6641/Cloudy-Workspace/commit/13dce23dd4052163f22703752b16438ec81c8306). Package `dev.cloudy.qualification.baseline`, debug version **0.2.0 / 2**, report schema **2**. APK SHA-256: `4a7a47df18b8d48541ce7f1d7ed347c0d6e48c2f79898c8e65064199d1596b32`.

The target reports **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build `CL3B.260622.276.R2`, incremental `16560795`, security patch `2026-09-01`. This run observed density **1.625** and font scale **1.0**. Device wall-clock timestamps are reported readings, not independently synchronized timing measurements.

The parent-run Kotlin compile/assemble, Android Lint and **17 JVM tests** passed for this source. The existing lint exception remains limited to the pinned Gradle wrapper version notice. Hosted [Repository checks](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38691345990) and [Android qualification probe](https://github.com/BaiTian6641/Cloudy-Workspace/actions/runs/38691346070) both completed successfully at the exact source SHA above. These checks do not substitute for the device observations. See the [toolchain](../../../docs/03-platform/googlebook/probe-toolchain.md) and [independent code review](../../../docs/06-validation/2026-10-15-window-probe-review.md).

The [public evidence manifest](2026-10-15-window-probe.json) contains allowlisted build/identity/state/window observations, selected correlated events, revisions and hashes of private evidence. It publishes neither raw commands nor UI layouts/screenshots. Its geometry references deduplicate equal recorded values; they are report aliases rather than additional measurements.

## Window identity and state isolation

The A/B labels below identify windows, while the separate marker values are the probe's A–D state values. Task IDs are opaque observations, not permanent document identities.

| Window | Logical window ID | Observed task | Initial Activity generation |
| --- | --- | ---: | --- |
| A | `56d75add-e286-415a-a006-017c4f903127` | 1000144 | `49d5326a-d84c-4ac2-a2be-0af450df4178` |
| B | `778b3054-7854-4046-afcb-a5afb8edbed3` | 1000150 | `f456a968-b115-4725-a2d9-10202ee2b7b2` |

The primary agent observed both native windows; both reported `taskRootObserved=true`. The second was launched through the actual **New window** control. Event 11 records the request, event 12 its return, and event 14 B's actual creation. The verdict uses the observed second task/window and state, not merely a successful launch return.

| Saved revision | Window A marker / counter | Window B marker / counter | Observation |
| ---: | --- | --- | --- |
| 9 | A / 0 | Not created | Initial A |
| 21 | B / 1 | A / 0 | A changed; newly created B starts independently |
| 23 | B / 1 | C / 2 | Two changes to B leave A unchanged |
| 40 | B / 1 | C / 2 | A's explicit Activity recreation preserves both states |
| 44 | C / 2 | C / 2 | A changed during the save fault; recovery publishes its state |
| 112 | D / 3 | C / 2 | A changes again while B retains its own state |
| 136, 139 | D / 3, historical closed record | C / 2, live | Closing A leaves B usable |

At revision 40, A's generation changed to `7a591608-b446-4c66-b7bf-1d87a5f24918` while its logical ID, task and B/1 state survived. Events 30–40 include the explicit recreate request, stop, actual save-instance-state callback, destruction with `changingConfigurations=true;finishing=false`, creation with `restored-bundle`, and a fresh geometry observation. B retained its generation and C/2 state. Every selected report has the same process start, `2026-10-15T06:15:28.034452Z`: this demonstrates **same-process Activity recreation**, not system-initiated process-death restoration.

## Native geometry, captions and interaction

The run used native maximize/restore controls, a corner resize, native tiling and the divider. Android created further Activity generations during resizing/tiling; the manifest preserves all observed generation IDs. Logical IDs and marker/counter state survived these observed transitions.

| Checkpoint | Revision | Observed bounds in pixels, left/top/right/bottom | Size |
| --- | ---: | --- | --- |
| Initial A | 9 | 501 / 289 / 2574 / 1585 | 2073 × 1296 |
| Maximized A | 46 | 0 / 59 / 2880 / 1709 | 2880 × 1650 |
| Short, narrow A | 58 | 501 / 289 / 1139 / 901 | 638 × 612 |
| A left tile | 71 | 0 / 59 / 1432 / 1709 | 1432 × 1650 |
| B right tile | 87 | 1448 / 59 / 2880 / 1709 | 1432 × 1650 |
| A after divider | 105 | 0 / 59 / 653 / 1709 | 653 × 1650 |
| B after divider | 105 | 669 / 59 / 2880 / 1709 | 2211 × 1650 |

The primary agent visually inspected native captions and the narrow/short layouts, scrolled the probe to reach its controls, and exercised Refresh at the narrow size. This establishes reachability for the observed layouts at font scale 1.0; larger text scales were **not run**.

Keep the inset sources distinct: the attached decor reported a visible **65 px caption** and two occupied caption rectangles. `currentWindowMetrics.windowInsets` reported **zero caption inset and an empty caption-rectangle list** at the same retained observations. For the 638 px window, the decor rectangles were `[0,0,130,65]` and `[318,0,638,65]`; at 653 px they were `[0,0,130,65]` and `[333,0,653,65]`. These are inset-frame coordinates, not global screen coordinates. The dimensions are observations on this build, not values to hard-code into product layout.

Injected focus alternation produced correctly attributed focus and top-resumed events. Before HOME, revision 112 held A D/3 and B C/2. Revision 121 recorded both windows paused/stopped with saved-state callbacks; revision 128 recorded start/resume and focus return without replacing either generation or mixing state. No universal callback-order guarantee is inferred.

The native close control for A produced event 135 with `changingConfigurations=false;finishing=true`. At revision 136 A was marked not alive; B retained its identity/generation and C/2 state. B's actual Refresh then produced events 137–139 and saved revision 139. A's retained geometry and its last `windowFocus=true` value are **historical observations from before closure**, not evidence of a live or focused closed window.

## Bounded recording, quiet period and write fault

The final report records six superseded generations, two retained logical-window records, one observed live Activity, a 128-event limit and **11 dropped events** after sequence 139. No window-record eviction, registration rejection or stale-callback rejection occurred in this two-window device run. Those policies are covered by deterministic JVM tests; **a runtime ninth-window capacity test was not run**.

The first quiet sample was taken before the last marker write settled: revisions were **111, 112, 112, 112**. It is not an all-stable pass. After HOME/return settled, four later reads all remained at **128**, over approximately **4.127 seconds** of host capture time. This found no continuing revision churn during that short interval; it is not a sustained soak or a proof that no framework callbacks will arrive later.

For a real save failure, the harness changed only the probe-private files directory from mode **771 to 551**, then triggered A's marker change. The UI correctly reported an `IOException`: current revision advanced **40 → 41**, while `savedRevision` stayed **40**. The original and retained report bytes matched exactly, SHA-256 `1b8136243d1db39e2f03ee374fd219444a905546af0b484760752009ff77249a`. The app did not report revision 41 as saved.

The harness assertion incorrectly expected the word “failed”; the actual UI said “could not be saved.” That assertion mismatch interrupted the host script, not the app. Cleanup restored mode **771**, which was verified. Restoring permissions alone still left revision 41/saved 40 and the error visible. The primary agent explicitly pressed Refresh; the UI and exported report then agreed on **revision 44 / savedRevision 44**. This establishes recovery from the tested permission fault. Power loss, out-of-space and a forced AtomicFile rename failure were not tested.

Other bounded harness notes: `s2-a-marker.json` was an early pre-save read still containing revision 9, so the isolation verdict uses later revisions 21/23. A preliminary UI-helper layout/visibility issue was resolved by adapting flat/nested layout handling and reacquiring visible bounds before the real recreate action. The actual Bundle evidence is the later event sequence, not the helper attempt.

## Case verdicts and remaining scope

| Case | Verdict | Exact coverage |
| --- | --- | --- |
| W01 | Pass | Two actual native task windows and bidirectional state isolation |
| W02 | Pass | Explicit Activity recreation, actual Bundle callbacks and restored state in the same process |
| W03 | Pass, bounded | Native maximize/restore, short/narrow resize, tile/divider and scroll reachability at font scale 1.0 |
| W04 | Pass, bounded | Injected focus alternation and HOME/return with correlated lifecycle events |
| W05 | Pass | Native close of A; B unchanged and able to perform a real Refresh |
| W06 | Pass, bounded | JVM capacity/stale-generation policies, observed event-ring retention, short settled sampling and real permission-fault recovery; runtime overflow not exercised |

These local cases refine Q-P01/Q-P02 under the [window contract](../../../docs/03-platform/googlebook/p0-s2-window-probe-contract.md). They do not close P0-S2 or activate a capability gate. System-initiated process-death restoration, operation-specific service/timeout strategies, engagement and the final platform decision remain pending. Physical input/IME, text scaling above 100%, external displays and the optional SystemUI New window affordance remain untested. No SSH/SFTP, RDP/VNC, X11, streaming, USB, decoder-performance, latency, suspend or production-app lifetime support follows from this probe run.

## Evidence retention and privacy

Selected raw reports and harness records remain under ignored `qualification/results/private/window-probe/`; their exact paths and SHA-256 hashes are in the public manifest. The author verified matching repeated events across overlapping reports and retained only selected milestones publicly. Screenshots and raw UI layouts remain private because they include unrelated desktop content; the primary agent inspected them for this run. No ADB serial, account, machine inventory, command arguments/output, input-device identifier or unrelated application content is published here.
