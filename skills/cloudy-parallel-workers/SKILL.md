---
name: cloudy-parallel-workers
description: Delegate bounded Cloudy Workspace tasks through the locally configured oh-my-pi CLI, with explicit context, file ownership, model routing and leader review. Use for project worker dispatch and recovery.
---

# Cloudy parallel workers

Read [AGENTS](../../AGENTS.md), the [engineering workflow](../../docs/05-delivery/engineering-workflow.md), [current state](../../docs/05-delivery/execution-state.md) and [library reuse policy](../../docs/05-delivery/library-reuse-policy.md). The user authorized local `omp` delegation and separately authorized **GPT6.1-Sol (`gpt-6.1-sol`) as a Codex subagent model**. The parent Codex agent remains leader and owns integration, shared resources and acceptance. Preserve this decision in every task packet and handoff; compression does not change it. The active OMP allowlist is **Kimi K3 only**. Use GPT6.1-Sol directly through Codex collaboration tools, never through OMP. GLM remains inactive until user configuration and an updated routing decision.

## Route and scope

The inspected CLI is `omp 18.7.0`. Resolve exact selectors with `omp models find <pattern> --no-extensions --json`; a fuzzy model match is insufficient for a recorded assignment. Current verified catalog selector: **`kimi-code/k3`**. The user explicitly chose Kimi first while they configure GLM. GLM5.3-Flash and GLM5.3 remain requested future routes, not available or tested workers. Do not substitute other models automatically.

Begin with context-only review in a fresh temporary working directory and `--no-tools --no-extensions --no-skills --no-rules --no-lsp --no-pty --no-session --no-title --no-prewalk`. Select the model explicitly, use `--print --mode json`, and bound the run with `--max-time` plus a leader-side timeout. Disable configured model fallback and auxiliary model work through a per-run overlay after checking the installed keys. Do not change global settings or authentication.

These flags are tool controls, not an OS sandbox. `--no-tools` disables built-ins; upstream documents separate custom-tool discovery. Check applicable custom tools/MCP/plugin discovery before treating a run as tool-free. Use only reviewed context files, inspect actual model/tool events, and reject unexpected tools or routing. For tasks needing filesystem access, enable only the necessary tools in an isolated checkout; a working directory or prompt restriction alone does not constrain filesystem access.

For the tested 18.7.0 context-only route, the per-run `--config` overlay disables the following discovery-source IDs, leaving the `kimi-code` model provider available:

```yaml
enabledProviders: []
disabledProviders: [native, omp-plugins, claude, agent-plugins, codex, agents, claude-plugins, gemini, opencode, cursor, windsurf, cline, github, vscode, agents-md, claude-md, mcp-json, ssh-json, managed-skills, builtin-defaults]
autoResume: false
includeWorkspaceTree: false
retry: {enabled: false, modelFallback: false, usageAwareFallback: false}
advisor: {enabled: false}
compaction: {enabled: false, asyncEnabled: false, midTurnEnabled: false}
autolearn: {enabled: false, autoContinue: false}
memory: {backend: off}
recap: {enabled: false}
telemetry: {otlpExportEnabled: false}
startup: {checkUpdate: false}
mcp: {enableProjectConfig: false}
collab: {autoStart: off}
```

The CLI trial also used an explicit `--system-prompt` and empty `--append-system-prompt`. Its fresh temporary cwd had no project plugin/config root, and the native user plugin `node_modules` root was absent. The [custom-tool loader](https://github.com/can1357/oh-my-pi/blob/v18.7.0/packages/coding-agent/src/extensibility/custom-tools/loader.ts) separately enumerates [plugin tool paths](https://github.com/can1357/oh-my-pi/blob/v18.7.0/packages/coding-agent/src/extensibility/plugins/loader.ts), so `disabledProviders` alone is insufficient. Recheck those roots if the installation changes; do not claim discovery is globally disabled. Keep the actual assignment/config/event evidence together. No API key belongs in the command, packet or overlay.

## Context packet and ownership ledger

For every assignment record locally: task ID, exact model, base commit, supplied file hashes, intended result, writable paths (or none), deadline/status and review outcome. Keep prompts, raw results and runtime metadata in ignored `.cache/omp-workers/` or a private temporary directory. Publish only sanitized decisions and useful project artifacts.

The packet must include the finite question, relevant stage, accepted user decisions, applicable project/skill instructions, source/reference excerpts, known facts versus unknowns, acceptance checks and a stop condition. Supply enough source to make the answer grounded. Never send credentials, private inventory, raw device/UI transcripts or unrelated workspace material. If the task outgrows its packet, the worker reports the missing context to the leader rather than guessing.

Return format: findings or proposed patch; referenced file/line or supplied evidence; changed paths; checks actually run (or none); limitations; unresolved decisions. Workers must not claim unavailable tests, fabricate successful operations or describe scaffolds as implemented apps.

Assign disjoint writable files for straightforward parallel work. Use isolated checkouts for overlapping modules and accept patches sequentially against their recorded base. Workers do not commit/push, run ADB, start listeners, change dependencies, access credentials or spawn further workers unless the leader specifically delegates that operation in the packet and ledger with exclusive ownership of the affected resource. On a collision, stop affected writes, retain both diffs, reconcile ownership and review the merged result; never overwrite one worker's work silently.

## Accept and continue

Inspect the actual output/diff and compare it with the supplied evidence. A clean process exit or worker success statement is not acceptance. Run checks appropriate to changed code; reuse reliable unchanged results under the library policy. Security conclusions need the same evidence and leader review as other findings.

The accepted context-only Kimi trial is sufficient to start useful independent assignments. Increase concurrency when context and review quality are working and provider capacity permits; no repeated trial quota or benchmark is required. Introduce tool-enabled editing through an isolated bounded task when needed. Prefer useful parallel investigation or implementation over duplicated audits. Keep one writer per owned path and leave enough leader capacity for integration. On timeout, rate limit, provider failure or lost context, record the outcome and stop that assignment; choose any retry or reassignment explicitly. Resume with a fresh packet containing accepted progress, outstanding work and the current base revision.

The official [v18.7.0 overview](https://github.com/can1357/oh-my-pi/blob/v18.7.0/README.md) and [configuration discovery guide](https://github.com/can1357/oh-my-pi/blob/v18.7.0/docs/config-usage.md) explain the relevant CLI/discovery boundaries. Prefer installed command help when flags differ. This is a repository-local skill, not a globally installed or upstream-published package.

The first accepted trial is recorded in the [workflow checkpoint](../../docs/05-delivery/engineering-workflow.md#local-worker-trial-checkpoint). Kimi returned the requested review with no observed tool calls; no editing, parallel-throughput or GLM qualification follows from that result.
