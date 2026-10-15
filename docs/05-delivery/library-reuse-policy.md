# Established libraries and proportionate verification

**Decision D12 — user directed, 2026-10-15.** Prefer established libraries and backends for existing subsystem functionality. Spend Cloudy's engineering effort on integration and the desktop experience. This policy applies to the primary agent and subagents across P0–P6 and refines the [engineering workflow](engineering-workflow.md) and [validation strategy](../06-validation/strategy.md).

## What to reuse

Use maintained upstream engines through their documented interfaces. Prefer an appropriate released artifact; a prerelease or narrow upstream backport needs a specific reason, an identified change and an update owner. A working selected library is the default to carry forward. Reopen candidate comparisons only for an unmet requirement, relevant defect, maintenance/security concern or distribution constraint.

The table carries forward existing selections and candidates from the [dependency register](../03-platform/dependency-licensing.md). It does not newly adopt a library or certify an untested platform.

| Subsystem | Existing reuse direction | Cloudy's remaining responsibility |
| --- | --- | --- |
| SSH/SFTP | SSHJ 0.41.2 is used by the real diagnostic; product adoption remains gated | Trust/authentication UI, credential ownership, PTY/channel lifecycle, cancellation, file integrity, overwrite consent and app integration |
| Terminal | Bundled xterm.js 6.0.0 with fit 0.11.0 is used by the diagnostic; an upstream input fix is under consideration | Native/WebView byte boundary, focus, Googlebook input/IME/accessibility, resizing and bounded queues |
| Android shell | Android/AndroidX APIs; Compose/Material 3 remain the product shell direction | Cloudy desktop layouts/tokens, independent packages, native windows, permissions and operation lifetime |
| RDP | FreeRDP remains the leading candidate | Selected Android build, server identity/authentication, native surface/input, lifecycle and a representative server connection |
| VNC/RFB | A maintained client engine, with LibVNCClient a candidate | Selected license/build, negotiated security and encoding, framebuffer/input integration and representative interoperability |
| High-performance streaming | Existing Moonlight/Sunshine path; external access first, native adoption separately decided | App handoff or native integration, permissions, focus/input release, measured latency/pacing and failure recovery |
| X11 | Maintained Android X server candidate | SSH forwarding/trust isolation and mapping separate application/dialog windows to native Googlebook windows |
| USB | Existing provider/consumer backend; VirtualHere is the first candidate | Enrollment, permission and ownership reconciliation, hotplug orchestration and honest hardware coverage |
| Private routing | Existing Tailscale app/route where qualified | Respect the selected network/VPN, report route failures and recover operations correctly |

Do not implement replacement SSH cryptography, RDP/RFB stacks, video codecs, network tunnels or USB virtualization merely to obtain control over internals. Custom code should address a demonstrated integration gap. Upstream fixes are preferred to compensating content deduplication or parallel input paths.

## Sufficient adoption evidence

Use one concise adoption record per selected version/build, linking existing evidence:

1. Record the official artifact/revision, supported API/platform assumptions, relevant maintenance/advisory information, license/notices and update owner. Verify downloaded artifact integrity with the existing dependency tooling. Reuse the recorded dependency graph and notices unless their inputs change.
2. Build the selected package/ABI and exercise a representative real end-to-end operation through Cloudy's actual adapter. Include applicable trust/authentication rejection, cleanup, cancellation and data/input ownership behavior.
3. Test Cloudy-authored state machines, glue and configuration. Use focused regressions for changed upstream paths or an observed defect. Rely on upstream tests and documented behavior for unchanged library internals; do not recreate a general protocol, parser, cryptographic or codec conformance suite.
4. Record the observed result, remaining platform/device gaps and a proceed, bounded-remediation or blocked decision. A successful scoped result ends that investigation. Move to the next integration deliverable; reopen it only on a concrete trigger.

An accepted upstream test result supports upstream behavior. A Cloudy integration result supports the exact integrated route. Neither replaces the other, and upstream maturity alone does not establish Googlebook input, Android lifecycle, backend permissions or physical USB behavior.

## When to investigate further

Expand verification only for a reproduced integration failure, changed relevant dependency/adapter/platform behavior, applicable advisory, undocumented assumption, or a high-impact path not yet covered. Name the question, smallest discriminating check and stop condition before adding another experiment. A local patch justifies tests of its delta and affected integration, not a new audit of the entire upstream project.

Keep existing compiler/AST, lint, authored-code tests and required release cases. Do not rebuild an unchanged APK, rerun a completed device drill, rehash an unchanged evidence collection, or repeat an independent review solely because another handoff occurred. Link the accepted record and verify the changed inputs. Reviewers inspect the important diff and evidence; replay a result when its reliability is in question, not as an automatic second execution of every author check.

Documentation-only work normally needs documentation/schema validation, a diff review and the configured CI. Do not add code tests or local Android/device runs for prose changes. Existing CI remains authoritative for the commit it checks; a documentation commit does not create a new device result.

## Immediate application to P0

The [terminal input defect](../../qualification/results/P0-S3/2026-10-15-terminal-input-trace.md) is an observed integration failure, so a focused fix remains necessary. The [upstream comparison](../07-reference/2026-10-15-xterm-input-fix-candidate.md) is sufficient to move from source exploration to candidate selection. Compare the official artifact/API/fit and distribution delta, choose the smallest maintainable route, then check the affected input paths using the actual library and repeat the device bulk/paced byte comparison. Cover rapid/repeated text, deletion/replacement, composition commit/cancel and key controls implicated by the change. Do not build an exhaustive upstream event-order matrix or continue method-harness expansion without a new finding. Physical keyboard, native IME and accessibility acceptance remain explicit product requirements.

Retain accepted SSH/SFTP, transfer and lifecycle evidence; repeat only affected cases. P0-S4 desktop-engine and P0-S5 USB-provider investigation may proceed on their recorded prerequisites while terminal remediation continues. Missing USB hardware leaves physical attachment/reset/programming evidence pending, with full generic software scope retained. This policy changes verification effort, not the [stage dependency graph](backlog.json), supported-capability gates or release scope. Re-estimate remaining work after selecting engines; no numerical schedule saving is asserted yet.
