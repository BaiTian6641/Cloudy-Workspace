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

## Experiment

State the hypothesis, prerequisites, exact stimulus, expected observations, and duration. Describe safe setup/recovery for a fault-injection run. Separate a configured setting from an observed measurement.

## Observed result

Record actual steps, timestamps, measurements/units/percentiles, error chain, state transitions, and deviations. Attach sanitized logs, screenshots/video where useful, and hashes/paths. State what could not be observed.

## Verdict

Choose **pass / fail / blocked / inconclusive**. Explain the criterion behind the verdict. List supported combination, exclusions, fallback tested, remaining defects, and next experiment. Update the compatibility matrix and relevant API record only when this evidence establishes their status.
