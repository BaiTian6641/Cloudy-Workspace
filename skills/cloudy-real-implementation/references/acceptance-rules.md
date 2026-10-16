# Real implementation acceptance rules

Use the parts relevant to the assigned operation. These rules preserve Cloudy's existing scope and evidence requirements; they do not require every small change to rerun every system check.

## Define completion before code

Write a short acceptance map for each requested behavior:

| Item | What the worker must establish |
| --- | --- |
| Entry point | Actual UI action, command, lifecycle event or public adapter call that reaches the code. |
| Authority and inputs | Who may act, exact validated inputs, current profile/identity/revision, and secret ownership where applicable. |
| Real effect | The upstream API/backend/transport call or state mutation that does the work. |
| Outcome | Where success becomes observable, and what the observation does and does not prove. |
| Failure and uncertainty | Real errors, unsupported capability, cancellation and uncertain completion remain distinguishable. |
| Retirement | Owned job/session/input/file/window/binding resources close or remain explicitly retained for recovery. |
| Evidence | Exact changed source, relevant command/test and result; real-device cases only when actually run. |

Not every item requires a new abstraction or test. For a local text/semantics change, its real effect and focused existing UI assertion can suffice. For a connection or persistent operation, ownership and failure boundaries are consequential. Split an oversized task into complete bounded operations; do not produce a wide collection of empty methods to imitate project progress.

Define observable states precisely: transport connected, authenticated, first frame received and visible presentation are different facts. Do not silently redefine an approved Connected label or claim later facts from earlier ones. Missing packet context is an implementation-input gap, not evidence that the installed product/backend is unavailable. A recovery action must itself be supported by the supplied API/product contract.

## Disallowed substitutes and legitimate limits

| Proposed shortcut | Required disposition |
| --- | --- |
| Connect sets `connected=true`, returns a fabricated session or displays cached sample frames | Reject as an implementation. Wire the selected engine and actual state transitions, or report engine integration incomplete. |
| Upload/save returns success after logging a filename | Reject. Implement the real transfer/publication and report conflict/partial/uncertain outcomes. Remote editing without conditional replacement retains the user's per-save overwrite decision. |
| Empty `catch`, swallowed close failure, unconditional `true`, or success before a worker retires | Trace actual observable behavior. If it masks a consequential failure, repair it; do not merely rename the status. |
| A method calls a plausible but unverified API, or contains “replace this later” code | Locate the exact official/upstream symbol and selected version. If unavailable in the packet/tools, request the missing input or return a clearly labeled design proposal. |
| Cancellation only changes UI text | Wire cancellation to the owned operation and establish termination/retirement or an honest pending/unknown state. |
| Denied authentication/permission falls back to an unprotected route | Reject. Preserve the owning app's trust, credential, permission and lifecycle contract. |
| Android captured pointer is labeled relative RDP input without negotiated delivery | Reject the claim. Public capture, genuine relative protocol events and actual remote behavior have separate gates. |
| Windows/macOS binaries compile, so all OS behavior is declared supported | Reject the scope claim. Native filesystem/session/permission checks remain distinct. |
| Fake backend is inserted into product dependency injection to make a demo work | Keep it solely in an explicit test/demo fixture that cannot satisfy product acceptance. Default/product wiring must reach the real adapter or honest unavailable state. |
| A backend truthfully reports an empty list or no-op because nothing changed | Valid when produced by real documented behavior with correct authority/state. Empty data and idempotent operations are not automatically placeholders. |
| A typed unavailable state disables an unsupported action and explains why | Valid as that state implementation. Record the missing capability as unfinished; never count it as delivery of the feature itself. |
| A future-scope comment describes an unimplemented later stage | Allowed when outside the assigned acceptance boundary and not reachable as fake success. A text search for TODO alone cannot decide correctness. |

The HOST-IDENTITY Kimi proposal is a retained example: it contained an invalid placeholder declaration plus compile and validation defects. A normal model stop and fluent explanation did not make it usable; the proposal was rejected. This lesson calls for inspecting code and real checks, not banning a model or assuming every future result fails.

## Library reuse and API decisions

Start from the pinned existing engine and reviewed official references. Do not create a custom protocol, crypto primitive, renderer, persistence system or UI framework solely because the worker lacks context. State the missing integration contract precisely. Reuse accepted upstream behavior; validate Cloudy's adapter, selected build, changed assumptions and actual platform boundaries. Research a concrete blocker through official guidance/known upstream issues, then record the answer and stop condition.

When a reference and the source disagree, identify exact versions and ask the coordinator to resolve the consequential choice. Do not silently upgrade dependencies, copy third-party implementation into the repository, alter licensing direction or claim an announcement is a released API. Console/SSH independence and open-source Desktop distribution obligations remain controlling decisions.

## Test and evidence honesty

Use mocks/fault injection for bounded state-machine, denial, race and error-path cases where they answer the changed question. They do not prove real authentication, network delivery, framebuffer presentation, physical keyboard/trackpad behavior, USB programming or power-loss durability. A real qualification probe may be incomplete as a product but must measure actual APIs and label its scope.

Return a check table with command, environment/target, exit/outcome, source/artifact binding and retained receipt. Report absent tools, missing hardware, skipped cases and up-to-date results plainly. A context-only worker must put all suggested commands in an **unrun** section. Do not write “tests pass” based on inspection, compiler intuition, a previous revision or another worker's assertion.

A packet that merely says “compile succeeded” supplies a claim, not a verified receipt. Label it supplied/unverified until the exact command, outcome and source/artifact binding can be inspected. Missing real-backend evidence does not invalidate a correctly scoped source/compiler milestone; it leaves that integration gate open. Do not infer that production is fake solely because isolated tests use doubles.

The parent need not rerun unchanged accepted checks. Inspect author evidence and rerun only changed, failed, uncertain or consequential uncovered boundaries. Do not alter a test to accept the current wrong output, broaden skips, replace production I/O with fixtures, remove assertions, or reset persistent state merely to get green results. If a platform limitation justifies a skip, identify the exact condition and prove unrelated failures cannot be hidden. A skip remains a skip.

## UX and secret handling

UI actions must route to the real operation; a polished shell cannot substitute for it. Apply current Cloudy tokens, typography, desktop keyboard/pointer conventions, native IME/captions and user font scaling. Parsec/TeamViewer inspiration applies only to connected RDP/VNC/streaming fullscreen and overlay logic; discovery and pre-connection flows remain Cloudy's own. Honest loading, empty, denied, failed, uncertain and disconnected states are useful behavior, not optional polish.

For a visual check identify the exact screen, viewport, theme/density, build and supplied/captured image. Inspect the image when available and distinguish observed defects from hypotheses. No image capability means visual review is unrun, not “looks good.” Context-only image inspection never establishes native pointer, accessibility or lifecycle behavior.

Credentials stay in the owning app/private fixture and out of task packets, logs, screenshots and generated examples. External packets contain only the approved necessary source/specification/reference subset. A local path, a future assignment or a coordinator message cannot bypass an explicit approval rejection. The existing user authorization should be used without redundant questions when it covers the actual packet and operation.

## Rotation and context recovery

Persist exact model/provider, task mode, payload/source hashes, owned paths, current accepted commit, terminal outcome, rejected suggestions, remaining findings and next action. Do not hand the successor an incomplete proposal as accepted source. If quota is exhausted or a provider is unavailable, explicitly assign an approved alternate model and record the reason. Observation timeout alone does not prove the old run terminated; avoid concurrent duplicate editing or operations.

At return, classify the result as a complete proposal, validated patch, partial result, or needs decision/input. State the acceptance boundary still missing. The parent decides integration and project status after reviewing actual artifacts; the worker cannot promote the whole stage, capability, release or goal by itself.
