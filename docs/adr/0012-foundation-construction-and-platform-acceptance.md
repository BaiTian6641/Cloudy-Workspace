# ADR 0012 — Foundation construction and product platform acceptance

**Status:** adopted for scoped construction admission, subject to the evidence below. **Date:** 2026-10-15. **Authority:** the existing implementation mandate, Rule 0, independent releases and D12. This is an explicit engineering gate refinement; it changes no requested product behavior.

## Problem and decision

The original CAP-PLATFORM record combines native app construction primitives with complete product input, display, background-operation and distribution requirements. Only P1-S1, P1-S2 and P1-S3 consumed that record. Some of its unresolved requirements must be implemented and qualified in those foundations or in the actual feature integrations. Keeping one combined prerequisite would prevent that construction; simply deleting it would leave the later releases without a platform gate.

Add **CAP-FOUNDATION**, supported only for constructing native Android desktop packages on the demonstrated primitives. P1-S1, P1-S2 and P1-S3 require it. Preserve their existing dependencies, work and exit criteria. P1-S1 also requires the completed P0-S3 investigation decision; neither app is implemented merely because construction is admitted.

Keep **CAP-PLATFORM as experiment**, with all unresolved product requirements. Require it explicitly at **P2-S5/R1**, **P3-S5/R2** and **P5-S4/R4**. P4-S4 already inherits R1/R2 through P3-S9; P6 inherits R1/R2/R4. The validator enforces the three foundation and three acceptance mappings, in addition to checking supported outcomes and completed dependencies. Required-ancestor constraints preserve the R3/P6 release paths, including P6-S4's path through P6-S1. CAP-FOUNDATION cannot substitute at a protected product boundary. The [final review](../06-validation/2026-10-16-foundation-admission-review.md) records the resolved inheritance finding and mutation checks.

## Supported construction scope and evidence

The [foundation admission record](../../qualification/results/P0-S2/2026-10-15-foundation-admission.md) consolidates existing public device results without creating new test passes. Scope: **HP Googlebook 14, Android 17/API 37, arm64-v8a, build CL3B.260622.276.R2, incremental 16560795**. Relevant diagnostics demonstrate build/install/launch, two native document-task windows, independent local state, actual caption-driven resize/tile/close, same-process recreation, a separately observed background-process-loss restoration, and bounded font-scale reachability. The records retain each original source/APK identity and injected-input limits.

This supports creating the two package targets, their native window owners, native controls, local state and build/module boundaries. The API route is the public Android app/task/lifecycle model already mapped in the [desktop application contract](../03-platform/googlebook/desktop-application-contract.md) and [API tracker](../03-platform/googlebook/api-tracker.json). No ChromeOS/Crostini or private Googlebook API is introduced. The existing diagnostic toolchain is a reuse candidate; new product dependencies still require their own pin, license/build review and actual checks.

The later [finite retained SSH cases](../../qualification/results/P0-S3/2026-10-15-ssh-retention-cases.md) and [physical terminal samples](../../qualification/results/P0-S3/2026-10-15-terminal-physical-input.md) corroborate specific operation/input paths. They do not supply a universal service, full terminal input acceptance or continuous-product-session support.

## Obligations that remain

| Unresolved requirement | Deciding work and acceptance boundary |
| --- | --- |
| Native composition/preedit/exact committed bytes, clipboard/menu behavior, accessibility and complete keyboard/pointer/focus behavior | P1-S2 shell and P2-S2 terminal integration; CAP-TERMINAL remains unsupported. Actual per-app cases remain required at R1/R2. |
| Remaining fullscreen, display/scale, narrow-window and system-caption cases | P1-S2 and applicable Q-P02/Q-A02 checks on the real shell. A diagnostic geometry sample cannot freeze all product layouts. |
| Continuous SSH/session ownership and justified service/distribution classification | P1-S3 ownership implementation, P2-S1 session integration, current CAP-PLATFORM follow-up and release/channel review. The finite specialUse probe is not policy approval or an indefinite-lifetime promise. |
| RDP/VNC/stream/USB operation lifetimes and backend-specific permissions/recovery | Their existing engine capability and feature stages, with app-scoped release evidence. Success in one route cannot satisfy another. |
| Product credentials, protected IPC, durable operation metadata and no side-effect replay | P1-S3 and the existing security/denial/revocation tests; diagnostic plaintext credentials are not a product vault. |
| Exact engine/dependency/license adoption and distribution materials | P1-S4 for the named engines and P1-S5 for native streaming; no engine is adopted by this construction decision. |

CAP-PLATFORM's common platform decision and each app's actual acceptance cases remain distinct. An unavailable unrelated engine, optional host adapter or other app is not added as a dependency of a standalone release. Full Console X11/SFTP/drop and Desktop native RDP/VNC/stream/enrolled USB scope, per-save overwrite consent and independent credentials/releases remain unchanged.

## Execution boundary

Implement real foundation behavior and qualify it on the device. A title screen, fabricated profile/session, empty action or diagnostics-only wrapper is not completion of P1-S1. Real local window/state operations can establish the foundation while remote features remain honestly unavailable until their own prerequisites pass. Keep product shells distinct from the qualification packages and preserve all diagnostic identities and evidence.

Update this decision when new builds/backends change its applicability. A failed product check remains failed and can block the corresponding release even while construction continues. This refinement is not an alternate definition of the full project goal.
