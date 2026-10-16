# ADR 0011 — Separate SSH transport and terminal renderer investigation outcomes

**Status:** accepted for P0-S3 investigation closeout; product adoption pending. **Date:** 2026-10-15, America/Chicago. **Owners:** Android/SSH developer for transport; Terminal/Android developer for renderer; maintainer/build owner for P1-S4 adoption. The parent integration leader accepts evidence and stage/capability changes. This decision does not change CAP-PLATFORM or activate a dependent product stage.

## Context and decision

[P0-S3](../05-delivery/phases/p0-qualification.md#p0-s3--ssh-engine-and-terminal-renderer-qualification) requires an engine/renderer decision, independent transport and input results, and separate feasibility outcomes. The [feasibility rules](../05-delivery/feasibility-gates.md) allow an investigation to complete with an experimental or blocked feature. Its completion does not imply every acceptance case passed. The [closeout](../../qualification/results/P0-S3/2026-10-15-investigation-closeout.md) summarizes the actual source/build/device evidence; original reports retain their exact historical identities and limitations.

**SSH transport feasibility is positive for the recorded finite ordinary-SSH route.** Retain SSHJ0.41.2 and the reviewed diagnostic graph below as the selected candidate. Actual target-device trust, public-key authentication, independent SFTP integrity/no-clobber, exec/PTY/resize and finite lifecycle/retention observations support that conclusion. Keep CAP-SSH an **experiment**, not supported: final P1-S4 adoption, the named missing trust/authentication/jump routes and consuming-product contracts remain unresolved. This is not a general SSH/server compatibility or continuous-session guarantee.

**The terminal renderer remains an experiment, not adopted.** Retain the exact repaired local xterm/WebView candidate for the bounded packet below. Real shell/TUI/resize and sampled input results are useful evidence, but cannot establish native composition, accessibility, clipboard/menu integration, sustained operation or latency. The missing or unsuccessful renderer route cannot inherit the transport result. CAP-TERMINAL remains unsupported; P2-S2 remains gated. The full desktop interaction requirements remain in force.

## Exact selections and source/build provenance

| Component | Selected diagnostic pin | Reviewed scope |
| --- | --- | --- |
| SSH engine | `com.hierynomus:sshj:0.41.2`, upstream `6fad160fe36605a68847ac8a527750c615b4bd04` | Native trust/authentication, SFTP and SSH channel candidate; exact source/artifact/provider policy in the [engine investigation](../03-platform/googlebook/p0-s3-ssh-engine-qualification.md). |
| Crypto/key parsing | `bcprov-jdk18on`, `bcpkix-jdk18on`, `bcutil-jdk18on` **1.86**, upstream `eacda831fbe15b272933e40841d8b0827b556fae` | Aligned reviewed replacement for requested1.84; bounded fixture key format, no global provider replacement or EST/BCJSSE use. The [dependency decision](../06-validation/2026-10-15-ssh-dependency-adoption.md) retains the exact artifact-scoped BC Lint exception and invalidation triggers. |
| ASN.1 dependency | `com.hierynomus:asn-one:0.6.0`, upstream `2faafc6ebb3978f56d877dcb80e5773d6e11ffb7` | Existing SSHJ dependency; preserve exact resolved graph and notices. |
| Logging | `slf4j-api` and `slf4j-nop` **2.0.20**, upstream `58d80a4fe8f2e811707fcfba4d292b5d62fc2fe9` | One aligned API/NOP provider; no raw SSH transcript logging. |
| Terminal assets | `@xterm/xterm` **6.1.0-beta.315**, `@xterm/addon-fit` **0.12.0-beta.311** | Official prerelease repair candidate, not a new stable-release claim; exact archive integrity/source/asset bindings in [terminal-assets.json](../../qualification/probes/device-baseline/terminal-assets.json) and the [upstream review](../06-validation/2026-10-15-terminal-upstream-review.md). |
| WebView adapter | `androidx.webkit:webkit:1.17.1` | Packaged allowlisted local assets, exact-origin/main-frame/attachment binary messages, bounded queues and independent native SSH cancellation. Actual WebView provider149.0.7827.5 is a recorded device observation, not a permanently pinned platform component. |

The current installed diagnostic is0.8.0/code12, build reference `7aab1ccd-e90b-41e1-bd51-1183042c134a`, application source `d9518d18390e2a8cca7752a0546f89d8b4e7a9a2`, APK SHA256 `fc2cc377cb09aaf9ad571489a09854719e84de3a9a3a03b131fabab01fa46e09`. Its [accepted build/package review](../06-validation/2026-10-15-ssh-retention-build.md) records206JVM tests and zero Android Lint. Earlier0.5.0/0.6.0/0.7.x source/APK identities remain bound in their own reports. Do not repin old device results to0.8.0 or treat a later documentation CI run as another APK/device result.

## Alternatives and rejected approaches

| Alternative | Decision and evidence boundary |
| --- | --- |
| mwiede JSch **2.28.8** | Not selected for this route. Inspected global X11 destination/cookie state and absent public SFTP exclusive-create flags conflict with the intended isolation/no-clobber boundary. This is the recorded source comparison, not an Android failure test or permanent rejection. |
| libssh2 **1.11.1** | Unqualified native fallback. A material JVM blocker could justify selected crypto, reproducible NDK/JNI/ABI builds and handle/cancellation ownership; no measured advantage currently justifies replacing the working route. |
| Apache MINA SSHD **2.20.0** | Not selected for the first route because the recorded Android support/testing limitations and broader integration surface offer no demonstrated benefit. Not independently device-tested here. |
| xterm core **6.0.0** / fit **0.11.0** | Superseded as the current diagnostic input candidate after reproduced rapid-input duplication. Preserve historical results; repaired beta315/beta311 has the bounded host-byte regression pass. |
| Native/Termux terminal renderer | Unqualified fallback direction, with no exact adopted/tested renderer candidate in this record. Do not invent a comparative failure or treat mobile defaults as the desktop contract. |
| Termius | User-selected SSH/SFTP product and UX reference, not an assumed implementation dependency or embedding API. |
| Full React/Carbon shell or broad generic bridge | Not selected as a consequence of terminal renderer feasibility. Native shell/platform decisions remain in [ADR0002](0002-native-shell-and-terminal-surface.md) and their own stages; SSH credentials/data do not enter a general UI bridge. |

The exact transport comparison is retained in the engine investigation. D12 favors carrying accepted integration evidence forward and reopening comparisons only for a concrete defect, advisory, maintenance or distribution trigger. No new upstream survey or exhaustive protocol conformance suite is required by this decision.

## Transport adoption gaps

The observed route is deliberately narrower than Q-C01/P2-S1/P2-S8 product acceptance. P1-S4 must resolve the exact artifact/dependency closure, source/notices and independent distribution/update ownership before support. Preserve the bounded fixture-key, modern algorithm and trust-before-authentication policy; no permissive verifier, SHA-1/DSA/CBC fallback or global security-provider mutation is authorized.

Remaining routes include password/keyboard-interactive and encrypted-key/native prompt handling, independently verified/authenticated jump hops and wrong-hop identity rejection, and broader selected modern key/algorithm/server combinations. The first/changed-key tests are new-connection tests, not authenticated rekey qualification. Actual X11 request/channel/cookie isolation belongs to P0-S7 and cannot inherit an engine-hook review. Shared transport and transfer/publication products still require their independent app credential/job ownership and actual URI/publication/recovery cases; ordinary SSH must remain useful without a renderer, Console process or custom host agent. These gaps are named adoption/implementation work, not a reason to repeat the accepted finite trust/SFTP/PTY cases without changed inputs.

The transport experiment's hypothesis is that the reviewed native candidate can supply those typed shared transport/authentication/jump contracts while preserving exact peer identity and independent ownership. The Android/SSH owner must define the smallest actual-server check for each changed route, stop at a trust/cleanup failure, and supply the route's evidence to the separate P1-S4 decision. No supported scope is granted by this closeout.

## Bounded renderer follow-up

**Hypothesis:** the retained repaired xterm/WebView candidate can meet Cloudy's desktop committed-input/accessibility boundary through a bounded authored adapter and actual platform qualification, without replacing the working SSH transport. **Owner:** Terminal/Android developer with device/accessibility operator; parent owns integration and acceptance. **Effort cap:** two engineer-days of active work for this packet. Preparation, implementation, review and checks count toward the cap. An expired cap is not a pass.

Prerequisites are a preserved-capacity terminal evidence batch, exact candidate/source/APK/provider identity, an owned real ordinary-shell/byte-capture fixture, and available actual physical input and accessibility routes. The user reports **no composition IME configured**; composition is not currently executable and no new physical question or silent IME installation/configuration follows from this record. Native clipboard/menu integration is absent; do not ask the user to operate nonexistent controls as though implemented. Establish an authorized real composition/accessibility route and an implemented reviewed clipboard/menu adapter before their corresponding checks.

The packet targets actual preedit/commit/cancel with exact host bytes, focused physical editing/selection and clipboard/menu behavior, Android screen-reader reading/navigation/focus, and a finite sustained-output/resource/input-latency baseline. Preserve Ctrl+C terminal semantics and native field editing. Existing parser ACKs establish parser completion, not painted pixels or latency. Carry the accepted shell/TUI/resize/rapid-byte tests forward instead of repeating them merely after a handoff. Investigate the two retained synthetic edit limits only through a concrete relevant real event/byte discrepancy; do not add an exhaustive upstream event matrix.

**Stop conditions:** stop the affected route on a concrete correctness/security/ownership defect, unavailable required prerequisite or the effort cap. Record actual pass/fail/blocked/inconclusive observations and unmet requirements. If a correction cannot be implemented/reviewed/verified within this packet, renderer adoption remains blocked; choose a further bounded proposal or native fallback only from the specific evidence. No automatic engine switch, widened testing quota, input-integrity waiver or feature-scope reduction follows. The transport conclusion remains independently useful.

## Consequences

P0-S3 investigation deliverables are complete once this ADR, closeout and separate capability records are integrated and reviewed. Q-C01/Q-C02/Q-A01/Q-F02 remain partial in their full product scopes. P1-S4 remains a separate adoption decision; capability records must not become supported because the investigation is done. This ADR narrows the older renderer proposal's implication that every input/adoption result must precede an investigation decision: missing evidence now produces the explicit experiment/adoption blocker above, not a fabricated pass. Full desktop requirements and independent release gates remain unchanged.
