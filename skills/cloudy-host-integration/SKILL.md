---
name: cloudy-host-integration
description: Implement and qualify Cloudy Windows, Linux and macOS host-core or user-session adapters, with truthful capability reports, owned jobs and scoped events. Use for host-side work, not additional desktop clients.
---

# Cloudy host integration

Read [AGENTS](../../AGENTS.md), [current state](../../docs/05-delivery/execution-state.md), the [parallel mandate](../../docs/05-delivery/parallel-execution-contract.md), [P4](../../docs/05-delivery/phases/p4-host-integration.md) and the assigned [host entry](../../docs/03-platform/host-core-entry.md). Client platforms remain Googlebook Android; host targets are Windows/Linux/macOS. Ordinary SSH/RDP/VNC/media/USB paths remain independent of the optional host agent.

Go1.27.2 is selected. HOST-EXP01 began as a standard-library-only local diagnostic; the accepted [Windows0.1.2 identity profile](../../docs/03-platform/host-windows-identity-profile.md) adds only exact `golang.org/x/sys v0.49.0` with reviewed checksums/license/notices. Preserve the current dependency decision rather than treating the historical no-dependency profile as current. The subsequent [Linux local-jobs profile](../../docs/03-platform/host-local-jobs.md) reuses exact x/sys Unix wrappers and has bounded native fixture evidence. Preserve its explicit owned-child/uncertain-Job/observation-versus-cancellation and output-completeness rules; never substitute an arbitrary cgroup or process-group fallback. Remote jobs admission, other-OS backends, pairing/transport and service installation remain separate decisions. Do not add Rust/Tokio, GUI frameworks, listeners or a privileged service merely because an upstream example uses them. The finite local profile is not the production capabilities schema; never fabricate boot/session identifiers to populate it.

For a new adapter, use [reference research](../cloudy-reference-research/SKILL.md) to identify the exact official API, version, user/session authority and failure states. Separate service, interactive helper and any justified privileged operation. Windows Session0, macOS daemons and a lingering Linux user manager do not establish an interactive desktop. Grant, backend presence and working capability are different observations. Keep unavailable, unknown, permission-required and error distinct.

Implement a small real operation with bounded data, explicit ownership and useful failure behavior. Before job execution define executable/argv/cwd/environment authority, cancellation, descendant ownership, retirement and restart semantics. Do not replay jobs on repeated requests or infer stable event history from process-local counters. Authenticated remote transport and scoped request admission precede remote side effects.

Use the [worker workflow](../cloudy-parallel-workers/SKILL.md) for bounded packets and exact routing. Coordinator review does not expand source/image authorization or grant shared ADB/Git/host ownership. Return changed paths, actual commands/results, source pins, evidence, limitations and missing decisions.

Go acceptance uses the selected compiler, `gofmt`, `go vet`, meaningful tests and applicable race checks. Cross-builds and hosted execution are separate from real user-session/permission/installer tests. Reuse unchanged upstream validation; investigate only a concrete changed boundary. Any host UI additionally uses [Cloudy design implementation](../cloudy-design-implementation/SKILL.md).

This is an original repository-local workflow built from reviewed project decisions and linked official references. It is not an upstream skill, global installation or evidence that a host platform is qualified.
