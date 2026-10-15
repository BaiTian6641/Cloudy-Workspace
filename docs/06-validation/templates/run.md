# Qualification run template

## Identity

- Run ID and date/time/timezone:
- Stage IDs / case IDs / responsible role:
- Commit, APK/agent hashes and versions:
- Client SKU / OS build / API / ABI / WebView:
- Host model / OS build / user session / compositor / GPU / driver:
- Backend versions and configuration:
- Input device / display mode / scaling / power state:
- Probe / tool / firmware / approved reset identities:
- Topology / direct-or-relay / measured counter boundaries:
- Applicable official desktop-quality test IDs and justified not-applicable features:
- Android package IDs / signature / SDK/ABI / install or publishing channel:
- Protocol / negotiated transport, encoding or codec / server security / session identity:
- X11 engine/display/window generation or USB enrollment/connection generation where relevant:

## Experiment

State the hypothesis, prerequisites, exact stimulus, expected observations, and duration. Describe safe setup/recovery for a fault-injection run. Separate a configured setting from an observed measurement.

For performance use the [measurement protocol](../desktop-performance.md): matched comparison conditions, input-to-photon method/uncertainty, warmup/run/sample counts and raw measurement path. For USB hotplug record cycle count and permission/provider/consumer-ready boundaries. Phone/tablet launch is not Googlebook desktop qualification.

## Observed result

Record actual steps, timestamps, measurements/units/percentiles, error chain, state transitions, and deviations. Attach sanitized logs, screenshots/video where useful, and hashes/paths. State what could not be observed.

## Verdict

Choose **pass / fail / blocked / inconclusive**. Explain the criterion behind the verdict. List supported combination, exclusions, fallback tested, remaining defects, and next experiment. Update the compatibility matrix and relevant API record only when this evidence establishes their status.

## Capability decision and readiness

When this run resolves a [feasibility gate](../../05-delivery/feasibility-gates.md), record its gate ID and separate outcome: `pending`, `supported`, `experiment` or `blocked`. Name the exact supported scope and linked dependent stages. For a further experiment, include hypothesis, owner, active-effort cap, stop condition and expected evidence. For a blocker, identify the unmet requirement and any proposed scope decision. Completing the investigation does not automatically make its implementation stage ready.
