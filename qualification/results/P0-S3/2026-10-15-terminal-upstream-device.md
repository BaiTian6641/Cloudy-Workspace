# P0-S3: upstream terminal candidate on the Googlebook

**Run:** 2026-10-15, installation approximately 15:59 UTC; terminal session 16:01–16:12 UTC; final preservation check 16:13 UTC. **Observed result:** the installed upstream candidate delivered exactly four host bytes for both the bounded bulk and paced ADB `stty` samples. The accepted 0.7.1 baseline delivered ten bytes for bulk injection. This is evidence that the sampled rapid-input defect is corrected on this device/backend; it does not establish general keyboard/IME correctness or complete P0-S3, CAP-SSH or CAP-TERMINAL. **Review:** accepted by the parent and an independent GPT6.1-Sol Codex reviewer, limited to the recorded scope.

## Application, device and carried evidence

The actual installed diagnostic is **0.7.2 / code 11**, package `dev.cloudy.qualification.baseline`, build reference **`475bcf30-ce99-4995-a549-7fcbb1cfd31d`**, execution source **`b26ab2481ce9e822de2c3f170bbcddc1be50fe48`**. Its **15,976,273-byte** APK has SHA-256 **`c6f52b83d66846c528f867f3f450e66e32c8add0e14e717d8c8820b6fda9e949`**; the source binding verified **121 files**. The package observation and final app journal establish the installed version. The preserved build/preinstall binding's `deviceInstalled:false` describes that earlier binding and does not itself certify installation.

The candidate uses official xterm core **6.1.0-beta.315**, fit **0.12.0-beta.311**, AndroidX WebKit **1.17.1** and SSHJ **0.41.2**. The [source/API and browser review](../../../docs/06-validation/2026-10-15-terminal-upstream-review.md) and [input-repair investigation](../../../docs/07-reference/2026-10-15-xterm-input-fix-candidate.md) carry the upstream contract, package/build and browser evidence, including two retained synthetic editing limits. They are separate software checks, not additional device observations.

The [accepted preceding trace](2026-10-15-terminal-input-trace.md) supplies the unchanged device/backend identities: **HP Googlebook 14**, Android **17 / API 37**, **arm64-v8a**, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**; Ubuntu **26.04.1 LTS**, Linux **7.0.0-38-generic**, x86_64, KDE/Wayland, **OpenSSH_10.2p1 Ubuntu-2ubuntu3.8 / OpenSSL 3.5.5**. This run's journal records **`com.google.android.webview` 149.0.7827.5**, message-listener and array-buffer support true. The ordinary Bash fixture runs as the operator's user and is not a sandbox. No new broad inventory is claimed.

The leader performed device/fixture operations and visually inspected the private ANSI screenshot. This report author read frozen evidence without device control. The [manifest](2026-10-15-terminal-upstream-device-manifest.json) hashes selected actual private artifacts and provides sanitized capture, stimulus, session-event, resize/output and cleanup projections. Raw logs, screenshots and identity-bearing records remain private; no unrelated background application screenshot is published.

## Accepted host-byte comparison

The actual host capture helper allowed **30 seconds / 64 bytes** per sample. Both helpers terminated at capture timeout with intended exit zero and successful pending-input discard on restoration. Timeout defines the collection boundary. It is not the reason for accepting byte integrity.

| Sample | Actual host capture window, UTC | Recorded stimulus start–finish, UTC | Host result |
| --- | --- | --- | --- |
| Candidate bulk4 | 16:09:31.413594–16:10:01.439128 | 16:09:34.607257–16:09:34.762249 | 4 bytes `stty`, hex `73747479` |
| Candidate paced1 | 16:10:24.664328–16:10:54.690594 | 16:10:27.845193–16:10:28.716840 | 4 bytes `stty`, hex `73747479` |
| Accepted 0.7.1 bulk baseline | Earlier accepted trace, separate run | Traced and fresh untraced bulk | 10 bytes `sttyttytyy`, hex `73747479747479747979` |

Both candidate payloads independently match SHA-256 **`d6e2a42f436d3f01aaff8a5602213b3ada0206721097c2d4591cc1ef6c752a0d`**. The manifest recalculates byte length/hash from actual captured hex and checks that stimulus start, completion and every event receipt fall inside the corresponding host window. Bulk4 binds host record `exercise-26ca2cca-a3da-40d7-be4b-6780c4a494ab.json`; paced1 binds `exercise-d3dd01da-c93c-4e70-b870-7eba1fcf4c38.json`.

For accepted samples the last visible terminal line was `CLOUDY_CAPTURE_READY` and the WebView editable node had **`FOCUSED` in its state**. Bulk stimulus began 3.194 seconds after host capture start; paced stimulus began 3.181 seconds after capture start, both below the controller's 20-second readiness bound. Actual host timestamps, rather than a stale readiness marker alone, establish correlation. Native focus was rechecked by the stimulus helper. These are ADB-injected samples, not physical typing, native IME composition or a new passive DOM trace.

## Resize, output and closure

Three actual `./s` executions read kernel PTY size through the fixture: **146 × 23** at **16:11:24.466092 UTC**, **205 × 32** after maximize at **16:11:49.053518 UTC**, and **146 × 23** after restore at **16:12:09.248918 UTC**. All reported `TERM=xterm-256color`, locale `C.UTF-8`. The observations demonstrate a real resize round trip in this session, with session continuity through maximize/restore; they do not qualify every window size or lifecycle transition.

The actual `./a` helper wrote **488 bytes** at **16:12:10.023029–16:12:10.023354 UTC**, expected-output SHA-256 **`760c9e66310026305b9434d85c974a4f701c63bb1c3eab2ece55ca7ab03effe9`**. The leader's private screenshot inspection observed the ANSI palette, CJK/combining/emoji output and end marker. This is bounded visual evidence, not comprehensive glyph, shaping, accessibility or colorimetry acceptance. Generated/written output bytes are distinct from painted pixels.

The final journal, revision **8**, snapshot **16:12:30.819615 device UTC**, contains **two attempts / fourteen events**, retaining the preceding attempt's seven events. The candidate authenticated and ended **`closed / remote-eof`**, with **59 input / 1,342 stdout / 0 stderr bytes**, zero dropped events and initial/final PTY **146 × 23**. An explicit exit stimulus is retained. Input counters describe successful native writes/flushes, not remote acknowledgments; output counters describe native reads, not rendering. Provider names/order were unchanged; implementation-byte identity was not measured.

## Retained operator failures and preservation

Earlier comparison attempts are excluded from the accepted pair. The first capture expired before bulk input and captured zero bytes. The second readiness assertion mistakenly searched `interactions` for `FOCUSED`, which is represented in `state`. A third `./b` command overlapped the preceding capture timeout: its first `.` was captured, `/b` reached the shell and failed, and later injected text reached the prompt; the operator cleared it with Ctrl-U. These timing/assertion failures do not establish app-input failures. The accepted procedure then required the last visible line to be READY, focused editable state, a recent capture and final timestamp correlation.

The historical empty exercise JSON was skipped without mutation. A maximized-journal label collision involved a read-only observation. An initial audit assumed top-level journal events and failed before writing; the corrected audit reads events within attempts. The initial preservation comparison incorrectly included mutable launcher `baseline.json`; the six protected journals/archive files matched. These failures remain visible as harness/accounting corrections rather than being relabeled passes.

All **six protected journals/archive files were unchanged across installation**. After the candidate session, **five protected files remained byte-identical**, while the terminal journal preserved the prior attempt/events/trust and appended the candidate attempt. The launcher baseline is explicitly mutable. The final check at **16:13:34.276019 UTC** records unchanged trust and **zero selected-device forwards**.

The controlled fixture controller (leader execution record **90362**) stopped at **16:12:38.222459 UTC**, exit **0**, listener connection refused (errno **111**), **zero remaining owned processes** and **zero forced kills**, across eight observed process identities. This is cleanup of the observed foreground fixture family, not arbitrary daemon containment.

Physical keyboard shortcuts/control keys, native CJK IME, TalkBack/accessibility, clipboard, direct pointer selection/context menus, sustained throughput/backpressure and full renderer acceptance remain pending. The two synthetic editing limits from the upstream review also remain. Console and Desktop product applications are not implemented by this diagnostic, and no capability gate is promoted.
