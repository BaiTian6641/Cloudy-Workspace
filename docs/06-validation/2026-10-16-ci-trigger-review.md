# Scoped Android push triggers

**2026-10-16; configuration/source review passed, hosted behavior for this change pending.** This P1-S4 infrastructure increment applies the established-library reuse policy to repeated branch pushes. It changes triggers in the six Android workflows only. Existing compiler, dependency verification, native build, Lint, JVM, artifact and signature jobs/steps/permissions remain byte-for-byte equivalent after YAML parsing. Repository checks remain unconditional; the existing host workflow keeps its own path policy.

## Trigger boundary

Android push workflows now ignore a push only when every changed path is under `docs/`, `qualification/results/`, `host/`, `skills/`, or is the root `README.md` or `AGENTS.md`. These roots contain no current Android Gradle or preparation input. Android source, root/module build files, shared contracts/tokens, tools, dependency locks, workflows and `qualification/probes/` are outside the ignore set. A mixed source/documentation push still builds. Changes to build inputs under a new root must revise this mapping.

Every Android `pull_request` event remains unfiltered, and each workflow now has explicit manual dispatch for relevant advisories or deliberate unchanged-source rechecks. No build/test step is replaced with a successful no-op. A suppressed push produces no new compiler/test evidence and cannot inherit another commit's test result.

GitHub documents both the all-paths exclusion rule and the pending-check consequence for filtered required workflows in [R359 / workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onpushpull_requestpull_request_targetpathspaths-ignore). Actual repository rulesets were not queried or changed. A direct-push policy requiring a filtered workflow may still require the normal PR route or an explicit manual run; the unfiltered PR trigger is retained for that reason. No claim is made that a filtered required check automatically succeeds.

## Review and checks

The parent inspected current workflow commands, Gradle modules/source sets, design generation and native/probe preparation inputs. System Python PyYAML6.0.3 BaseLoader parses all six before/after files without treating `on` as a YAML1.1 boolean. Parsed equality checks establish that every top-level field except `on`, including all jobs and permissions, is unchanged. Pull-request triggers remain exact and unfiltered; the push patterns and added manual trigger match the reviewed set.

Parent receipt is `.cache/ci-triggers-01/review-01.json`. A separate direct Codex GPT-6.1 Sol reviewer inspected those bindings and source closures without rerunning builds, and found no coverage blocker. Its receipt is `.cache/ci-triggers-01/independent-review-01.json`, SHA256 `04d26e990726f73df227eb00853819cacb471d35fd5398498d743fd663fd25ca`.

No local APK/native rebuild or device rerun is needed for this trigger-only change. Its own push changes workflow files, so all six Android workflows should run; that hosted result remains separate. A later documentation/host-only push should show the intended exclusion, while repository/host checks retain their own scopes. PR and manual execution are available configuration paths, not newly executed test results. No phase, dependency, capability or release gate changes.
