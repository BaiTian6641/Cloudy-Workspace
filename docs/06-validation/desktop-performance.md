# Desktop latency, stability, and comparative qualification

**Status:** proposed measurement procedure and budgets; no benchmark results. **Owners:** Desktop/network/QA. **Stages:** P0-S4/S6, P3-S7/S8, P5-S2/S4, P6-S1/S4. **Cases:** Q-D02/Q-D07/Q-D08/Q-N02/Q-N03.

The experience goal is responsive, stable remote work approaching the best qualified Moonlight/Sunshine and Parsec combinations. NVIDIA GeForce NOW supplies a high-performance experience reference, not a reusable Cloudy backend. Claims about parity require named measurements; similarity of codec or displayed RTT is insufficient.

## Measurement boundaries

| Metric | Definition / instrumentation | Interpretation |
| --- | --- | --- |
| Input-to-photon | Physical input stimulus to visible changed pixel; high-speed camera/photodiode or validated equivalent | Primary responsiveness measure; report stimulus, camera/sample rate, uncertainty, sample count |
| Capture-to-present | Timestamped host capture to displayed frame, with measured clock synchronization/uncertainty | Useful pipeline measure, not automatically input-to-photon |
| Network RTT | Backend ping or specified probe on the actual route | Round trip; do not equate it with one-way media delay or total interaction latency |
| Encode/decode/queue | Per-stage backend instrumentation with documented start/end | Identify bottlenecks; missing stages remain unknown |
| Pacing | Presented-frame intervals, p95/p99, late frames, freeze durations | Configured fps does not establish displayed fps |
| Recovery | Link usable → first valid frame and usable input; session identity preserved or changed | No repeated click/command, stuck modifier, or silent protocol change |
| Resource cost | Warm/steady RSS, CPU/GPU, decoder name, temperature/throttling, battery/power | Needed for sustained laptop behavior, not only a short LAN demo |
| Visual usability | Text/diagram reading and CAD manipulation at fixed quality settings | Low latency achieved through unreadable text does not pass |

Record each metric's source, resolution and units. Use monotonic durations. Never sum unsynchronized host/client timestamps or add two overlapping backend counters. Keep raw redacted measurements and the analysis script/version with the run; report p50/p95/p99 and outliers instead of a single best number.

## Comparative method

1. Freeze client Googlebook/build, host OS/GPU/driver/capture session, display refresh and scaling, network path, power/thermal state, application scene, resolution, requested fps, bitrate, codec/profile, color settings, and input device.
2. Establish standalone Moonlight/Sunshine on that same combination. Measure Cloudy's native stream path after it exists; isolate external launch overhead from steady-state media latency. Compare Parsec only on a supported equivalent combination and state every unmatched parameter. No assumed Android/Googlebook support or proprietary SDK access.
3. Use randomized/alternating A/B order after warmup, at least three runs and 100 input samples per condition. Record high-speed capture uncertainty; increase sampling when frame-time uncertainty could mask the proposed budget.
4. Separate LAN direct, real remote direct, and relayed paths. Test 60 Hz and higher refresh only where hardware/backend qualifies. Keep 1080p60 as an initial comparison profile; native-resolution text/CAD profiles get separate runs.
5. For GeForce NOW, report service tier/region, game/scene, network and measurement boundary. Host/encoder/workload are controlled by the service, so label it contextual rather than same-host parity evidence. NVIDIA's [requirements](https://www.nvidia.com/en-us/geforce-now/system-reqs/) describe service/network prerequisites, not a universal Cloudy latency budget.

[Parsec's architecture overview](https://support.parsec.app/hc/en-us/articles/32361354307348-Overview) identifies host capture/encode, network and client decode as distinct pipeline elements. Cloudy's comparison method is its own proposed procedure. Upstream marketing performance is not copied into the support matrix.

## Proposed budgets to ratify after P0

| Workload | Initial acceptance target | Decision if missed |
| --- | --- | --- |
| Integrated streaming overhead | Cloudy p95 input-to-photon no more than one requested frame interval above qualified standalone Moonlight, with comparable readable quality | Diagnose copy/queue/input/presentation overhead; remeasure before parity language |
| Primary LAN stream profile | Aim p95 input-to-photon ≤50 ms at qualified 1080p60, with uncertainty reported | Hypothesis only; revise based on host/display floor and P0 measurement |
| Steady stream pacing | Aim fewer than 1% late/missing presentations over 30 min, no unexplained freeze over 250 ms on controlled healthy LAN | Define counter/denominator and investigate periodic stalls/thermal load |
| RDP/VNC productivity | Aim p95 visible typing/pointer response ≤100 ms on the primary LAN text/desktop workload | Ratify per protocol/server; do not use a VNC case as proof of gaming parity |
| Recovery | Aim usable frame/input within 5 s after connectivity is confirmed usable; report authentication/server/session limitations | Keep accurate state and recovery action even if latency target fails |
| Long use | 8 h mixed work with bounded queues/memory and no recurring crash, input replay, or stuck capture | Correctness defects block release; document performance misses and rerun affected cases |

The one-frame overhead target is 16.7 ms at 60 fps and 8.3 ms at 120 fps; the test must be precise enough to resolve it. These budgets do not promise Parsec/GeForce NOW parity on every host. Protect input/data correctness and intelligible quality when adapting settings.

## Degraded-link and stability matrix

Use a controlled lab route outside valuable programming operations. Suggested independent profiles: added RTT 20/50/100 ms; jitter 5/20 ms; loss 0.1/1/3%; available bandwidth 1.2×/0.8× configured media rate; a 2/10 s outage. Record the actual direction, baseline, shaping tool/config, and achieved impairment. Add Wi-Fi roam, direct↔relay, host sleep/lock/resume, decoder fault, permission revocation and app eviction separately.

For each lane record freezes, queue age, bitrate/quality changes, reconnect attempts and backoff, session continuity, text/input integrity, and required user action. Treat a TCP fallback as a changed path, not a silent successful UDP result. RDP/VNC maintain protocol state; arbitrary frame/rectangle skipping cannot be used as a generic latency fix. No automatic protocol switch to another desktop/session.

Run simultaneous SFTP transfer and idle/protected USB workloads to find contention; do not induce outages during a real production flash. User-selected bulk priority can yield to interactive work. Show network/backend errors with bounded diagnostics. All published results name exact hardware/build/backend, support exclusions and evidence paths using the [run template](templates/run.md).
