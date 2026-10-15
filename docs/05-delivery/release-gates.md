# Independent app releases and the suite milestone

**Authority:** the user's question-box decision in [Revision 02](../00-project/revision-02-final-review.md). Console and Desktop have independent release gates. Their combined journey has a separate milestone. Runtime independence and release independence both apply.

## Gate map

| Gate | Required scope | Explicit dependencies |
| --- | --- | --- |
| R1 / P2-S5 | Full Console: trusted SSH/PTY, windows/layout, separate X11 apps, session SFTP/edit/drop, tunnels, qualified CLIs and app-scoped release evidence | Console feature stages and their capability decisions; Desktop and host adapters absent |
| R2 / P3-S5 | Full Desktop companion: native RDP/VNC, qualified external performance-stream handoff, files, enrolled USB and app-scoped release evidence | Desktop feature stages, reusable P2-S1/P2-S8 transport/transfer source and scoped network evidence; Console renderer, PTY, tabs/panes, attachment/SFTP UI and X11 not required |
| Suite / P3-S9 | Console build/upload → Desktop handoff → program/inspect with independent identity and correct resource context | Passing R1 and R2 candidates; not a prerequisite for either standalone release |
| R3 / P4-S4 | Optional host adapters and integrated activity with declared per-OS fidelity | Host adapters and the suite context; no dependency back into standalone or native-stream gates |
| R4 / P5-S4 | Native performance streaming, remote document provider and measured metered/contention behavior | Early P1-S5 adoption, native app/profile/media readiness, file/lifecycle and qualified USB orchestration; P4 not required |
| R5 / P6 | Supported full-suite candidate, combined failure/migration/distribution and dogfood evidence | Suite, R3 and R4; reuse standalone evidence only where exact versions/cases still apply |

Release labels identify scope, not an unconditional chronological sequence. P2-S1 qualifies reusable SSH transport/authentication and P2-S8 implements and qualifies shared SFTP transfer/staging/publication. Those source stages use CAP-SSH, independently of the terminal renderer's CAP-TERMINAL. Console attachments and the session SFTP pane consume the core in P2-S4/P2-S7; Desktop's cross-root files consume it directly in P3-S3. Each app repeats its own integration cases with its own credentials, grants and jobs. Shared source does not require Console UI to be complete or a Console package to be installed/released.

## App-scoped release checklist

Each R1/R2 gate includes Q-R02 and the applicable Q-R01 checks before a standalone package is promoted for use:

1. Install and run the signed candidate with the other app absent. Verify package identity, supported OS/ABI and required backend dependencies; each app owns credentials, permissions and operational data.
2. Test candidate update/restart and applicable data migration, recovery and uninstall/reinstall behavior. Record unsupported downgrade paths instead of promising rollback that Android or the selected channel cannot perform.
3. Verify trust prompts, input release, file publication consent, permission denial, process/network loss and the app's actual primary workflow. The Console candidate includes X11; the Desktop candidate includes enrolled USB and native RDP/VNC.
4. Provide setup, known limitations, required third-party installations, recovery/diagnostic instructions, notices and source obligations for that package. Distribution/channel decisions must be resolved for the actual release.
5. Observe at least ten working days on the candidate's supported workflow and exact build, with no unresolved release-blocking data-loss, trust, input/replay or ownership defect. Record active QA effort separately from elapsed observation.
6. Publish the app-specific evidence/support decision. An unrelated feature blocker in the other app is not a release dependency.

P1 provides reusable build and validation infrastructure. P2-S5 and P3-S5 apply this checklist separately; the added 4–6 active person-day allowance covers the two release reviews. Re-estimate from actual build/migration and sustained-use results. P6 retains full-suite checks and newly introduced R3/R4 behavior; it is not permission to skip standalone release hygiene.

## Integration failure and evidence reuse

A failed cross-app handoff blocks the corresponding suite integration claim. Continue to publish accurate standalone support where its tests still pass. If the failure is in shared code used by both apps, assess the affected standalone cases and rerun them before promotion; independence never waives a real shared defect.

The suite milestone tests missing counterpart, different app versions, resource resolution through the receiving app's own credentials, and interruption without replay. Optional host activity remains separately scoped. Observation periods can overlap when the same exact build and required workflows were actually used and recorded; one app's ten days does not automatically qualify another app or a changed full-suite candidate.
