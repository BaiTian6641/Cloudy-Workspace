# P0-S3: real SSH/PTY observer and permission lifecycle

**Run:** 2026-10-15, approximately **11:55–12:07 UTC**. **Observed result:** four finite SSH exec/PTY attempts exercised hiding the last SSH observer, closing the sole app task, revoking the selected user's LAN permission, and explicitly starting a successful exchange after restoring permission. **Public report review:** primary and independent review accepted; all 135 selected artifact hashes/sizes and the attempt, journal, host, control, permission and process projections were checked. P0-S3 remains in progress; CAP-SSH and CAP-TERMINAL remain pending.

## Identity, starting state and evidence

The installed application remained **0.6.0 / code 8**, package `dev.cloudy.qualification.baseline`, execution source **`af9deaed594fe53fbe52c4825a2e3e96a0e8fd83`**, build reference **`49607be8-a053-4f7f-8d22-3764b7e63136`**. The preserved APK is **15,827,911 bytes**, SHA-256 **`4f673be2ca5c0f2f4dcf9659645c827885f3aa4ee0952405d64d5f4c3d1a4f40`**. The [accepted batch maintenance](2026-10-15-ssh-batch-rotation.md) checked that installed package and established the zero-attempt starting journal with retained trust. Its archive/restore actions are a prerequisite, excluded from this lifecycle run.

The device is the previously identified **HP Googlebook 14**, Android **17 / API 37**, `arm64-v8a`, build **CL3B.260622.276.R2**, incremental **16560795**, security patch **2026-09-01**. The host is the previously identified Ubuntu **26.04.1 LTS**, Linux **7.0.0-38-generic**, x86_64, KDE/Wayland workstation, using **OpenSSH_10.2p1 Ubuntu-2ubuntu3.8 / OpenSSL 3.5.5**. These hardware/backend identities are carried from the [earlier device manifest](2026-10-15-ssh-pty-probe-manifest.json), not represented as newly collected inventory.

The exact tested fixed dispatcher was preserved as `host-04/tested-fixture-af9deae.py`: **13,666 bytes**, SHA-256 **`e771898736e4bc9ef99a866ac4dea2c2fe3a5eec8a906fcd7a2c583c0019436f`**. It permits the dedicated finite exec/PTY protocol with public-key authentication and `restrict,pty`; it does not offer an arbitrary shell or forwarding. This is a temporary non-root fixture, with no general account-isolation claim.

The [manifest](2026-10-15-ssh-lifecycle-manifest.json) binds **135 private artifacts**, **27 journal checkpoints**, **26 host observations**, **eight channel audits**, **12 control actions checked against captured bounds**, and **eight own-window projections**. It retains the four attempt identities and all **39** attempt events while excluding endpoint, account, numeric PID/user, key material, private-key digest and unrelated UI content. The evidence author read the frozen records and inspected the two relevant screenshots; only the primary operator controlled the device and fixture.

## Four actual attempts

All four attempts used the retained, matching host-key trust and actually authenticated. Each verified the fixed exec stdout and stderr at **19 bytes each**, with exit status **17**, then opened a separate PTY with actual initial size **80 × 24**. The three non-completing exchanges retained **31 received PTY bytes** and **zero sent PTY input bytes**; the restored run received **62 bytes**, sent **12 bytes**, verified **132 × 43**, and received the completion marker. Exact byte hashes and negotiation fields are retained in the manifest.

| Case and attempt | Observed app result | Independent host result |
| --- | --- | --- |
| Hide last observer — `64f958bf-f0b1-499b-832c-d364c065cbf2` | Native Minimize while awaiting resize; `cancelled`, revision 10. Returning the same task retained the same Activity generation and process, without replay. | One authentication/two sessions. PTY audit ended `signal-hup`, intended exit 129; subsequent samples had zero helpers. |
| Close sole app task — `ed7fa8ed-203a-4cae-bc62-7442a7efc887` | Native Close removed the only own task and killed its process. On reopening SSH, a new process changed the unfinished attempt to `interrupted / new-process-unfinished-attempt`. | Two authentications/four sessions cumulatively. PTY audit ended `signal-hup`, intended exit 129; first post-close sample still had one helper, later samples had zero. |
| Revoke LAN permission — `2da0355d-b7d2-4123-a062-39cc76db8ec9` | Exact package/current-user permission revoke killed the old process. The OS already had a replacement process by the later observation. Opening SSH recovered the retained attempt as interrupted. | Three authentications/six sessions cumulatively. PTY audit ended `signal-hup`, intended exit 129; post-revoke samples had zero helpers. |
| Restore permission and explicitly run — `0d067172-889a-46a4-9bbe-0822ba3652ea` | Native Allow caused no new attempt. Separate Run, Resize and Finish controls completed the exchange; final revision 13 retained all four rows. | Four authentications/eight sessions cumulatively. Host ioctl observed 132 × 43 and the expected terminal modes; PTY audit ended completed, intended exit 0. |

The host `intendedExitCode` is the dispatcher's audit field, not an Android `Shell` exit-status getter or sufficient process-exit proof by itself. The separate observations establish helper disappearance. All four host exec audits recorded completed/intended exit 17. The fixed terminal modes were ECHO and ECHONL off, ICANON on, and ONLCR off.

## Hiding and sole-task close are different outcomes

Before Minimize, the first attempt had actually reached `pty-awaiting-resize`. Four post-hide journal exports and the export after returning the task are byte-identical at revision 10. The four post-hide host read-completion observations span **10.262816 seconds**; all show one authentication/two sessions and zero helpers. The later return observation, at **11:58:52.593983 UTC** on the host, has those same counts. The SSH Activity generation **`d644e7b2-cfd6-45e5-bc05-ea1e68b6cefa`**, task **1000164** and process identity **`09d58378-a668-44f7-bddb-813519b25fa7`** remained the same. This supports cancellation on losing the last visible SSH observer and no automatic replay over the observed interval.

For the close case, a separate baseline task was closed first. A subsequent own-package activity dump confirmed only task 1000164 remained, containing the SSH Activity and its baseline Activity. The next finite run reached the real PTY wait before the captured native Close control was tapped. The post-close runtime observation found no own process or own task; the latest `ApplicationExitInfo` matched the prior PID and recorded **reason 10 USER REQUESTED / subreason 22 REMOVE TASK**.

The pre-close revision-18 running journal and all four post-close exports are byte-identical. The host's first post-close sample still found one helper; the next and later samples found zero. Only after explicitly reopening SSH did the journal move to process **`7af4f86c-23e1-4f20-b6f7-6aa881796abd`**, revision 1, with the unfinished row marked interrupted. This case does not establish that an in-process cancellation callback completed before the OS removed the task.

## Revocation, asynchronous recovery and explicit grant

The revocation receipt targeted the exact package and the same actual user established by maintenance. The selected-user package record changed `ACCESS_LOCAL_NETWORK` from **granted** to **denied**, then back to **granted** after the native Allow action. Other users' permission lines were excluded from the comparison.

The latest exit record matched the old PID and reported **reason 8 PERMISSION CHANGE / subreason 0 UNKNOWN**. The post-revoke process observation already contained a **different PID**, and its own activity list contained the baseline Activity. It would be incorrect to report that no process existed until the later explicit launch.

The active revision-9 journal, four post-revoke exports, and the immediate `revoke-reopened` export are byte-identical and still describe the old process's unfinished operation. That immediate read preceded completion of asynchronous SSH runtime initialization. The later own UI and `revoke-recovered` export show `interrupted`, process **`56d3bf10-ad2d-4549-9704-68a2bdb4bdf0`**, revision 1. No new attempt or authentication/session increase appeared in those observations.

After native Allow, `restore-no-auto-run` remains byte-identical to that recovered journal, with three attempts; the host still reports three authentications/six sessions and zero helpers. Only the subsequent explicit Run created attempt four. Explicit Resize and Finish then completed it. The completed and final-cleanup journals are byte-identical. The final screenshot shows completed/revision 13 and the same recovered process; the primary operator and evidence author both inspected it.

This demonstrates the tested permission-change/process-recovery path and explicit restart behavior. Because the OS killed the prior process, it does not isolate enforcement against a still-running socket or demonstrate an asynchronous permission callback in the killed process. No packet capture was performed.

## Preserved operator failures, cleanup and remaining scope

The initial `close-run` request used a nonmatching control label. The helper returned “Expected one visible clickable match; found 0”; there is no tap receipt, and the following journal/host observation still contains only the first cancelled attempt and one authentication/two sessions. The correctly matched `close-run-retry` produced attempt two. Filenames containing “active” are not independently treated as proof of running state.

The own-active-window filter also rejected the native task picker and native permission dialog, as expected for system surfaces. Their raw layouts were retained. The picker screenshot and recorded card coordinate support selection of the existing SSH task; the later identity comparison proves which Activity returned. The Allow tap was independently checked against the actual native dialog's bounds. These are injected actions, not physical-input or accessibility qualification; clickability alone is not an enabled-state proof.

The fixture controller recorded **exit 0** and a closed selected listener. The subsequent final host observation contains exactly **four accepted public-key authentications**, **eight session starts** and **zero live fixture helpers**. Recorded provider names/order remained unchanged in the final application process; this does not identify provider implementation bytes.

Host command/read-completion times and device journal timestamps are separate clocks. Their differences are not cancellation or network-latency measurements. This run does not qualify indefinite background SSH, natural low-memory recovery, a general terminal renderer, CJK/physical IME, TalkBack, direct/jump channels or X11. The next terminal-renderer work and the remaining full Console/Desktop requirements retain their existing gates.
