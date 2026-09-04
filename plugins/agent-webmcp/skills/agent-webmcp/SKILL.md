---
name: agent-webmcp
description: Operate Agent WebMCP's canonical service, agent, discovery, and durable-job workflows while respecting its WebMCP and remote MCP authority boundaries.
---

# Agent WebMCP

Use Agent WebMCP as a constrained operations surface. Never replace a rejected operation with raw shell, filesystem mutation, arbitrary working-directory execution, or arbitrary process execution.

## Product model

The shipping runtime has **23 canonical operations**, including **9 mutating operations**. Those canonical operations are the product authority and are reused by CLI, HTTP, browser, and MCP surfaces rather than reimplemented per transport.

The projections are intentionally different:

- **18 browser-native WebMCP tools** are published by the Fleet Cockpit page through `document.modelContext`.
- **16 bounded remote MCP tools** expose system/metrics, Agent Registry inspection, managed-service observation/lifecycle, and read-only durable-job evidence.
- The human Fleet Cockpit and canonical operator surface can use approved operator-only workflows including discovery and durable job authoring.

Do not widen a narrower projection merely because the canonical operation exists.

## Canonical operation families

The canonical runtime includes:

- `system.status`, `metrics.snapshot`
- `target.list`, `target.inspect`
- `agent.list`, `agent.inspect`
- `service.list`, `service.add`, `service.remove`, `service.discover`, `service.inspect`, `service.status`, `service.logs`, `service.diagnostics`, `service.start`, `service.stop`, `service.restart`, `service.reload`
- `job.list`, `job.inspect`, `job.logs`, `job.execute`, `job.cancel`

Treat the live canonical catalog as the final source of operation schemas and access metadata. Do not maintain a second handwritten execution model in the plugin.

## WebMCP surface

Browser-native WebMCP is the primary agent-facing web integration. The production Fleet Cockpit registers approved operations directly from the canonical catalog with `document.modelContext.registerTool(...)` and forwards execution to the same backend used by the human UI.

WebMCP does not require a second AI-specific API, a generic shell, or a public control-plane listener. When the Fleet Cockpit is open in a WebMCP-capable browser, use the tools exposed by that page and preserve their declared access metadata and schemas.

The separate remote MCP projection is optional and intentionally narrower. Never use direct HTTP calls, shell commands, or hidden application behavior to bypass operations omitted from that projection.

## Managed services

Managed-service enrollment is the lifecycle authority boundary. Guessed or unenrolled service IDs must be rejected before provider mutation. Service identifiers are passed as validated process arguments, never interpolated into shell commands.

For lifecycle requests:

1. Inspect the managed service and current provider-observed state.
2. Execute only the requested start, stop, restart, or reload operation.
3. Inspect state again and use bounded logs or diagnostics when useful.
4. Report provider or authorization rejection exactly. Do not claim success merely because a request was accepted.

Treat `service.logs` as bounded cursor-based journal access, not an unbounded terminal stream. `service.remove` removes Agent WebMCP enrollment; it does not delete the provider service.

## Discovery

`service.discover` belongs to the canonical operator surface and is not permission to widen narrower agent projections. Deterministic/provider-backed discovery is the default.

Optional AI-assisted discovery uses the user's existing Codex CLI only when explicitly requested. It is a read-only candidate finder: every returned service ID must be re-inspected through the real provider before registration, and hallucinated or unobservable IDs must be rejected. Agent WebMCP does not install Codex.

## Durable jobs

`job.execute` creates service-bound durable work through the canonical operator surface. An empty prompt means an explicit deterministic service operation. A prompt means a one-shot job using the user's existing Codex CLI.

Deterministic jobs may run immediately, at a future time, or recur when the current schema allows it. Recurring AI prompt jobs are intentionally rejected. Jobs persist durable state, logs, execution evidence, schedule, result/failure, and optional agent attribution. Interrupted RUNNING jobs must recover truthfully rather than pretending they completed.

`job.cancel` may cancel queued or scheduled work. Running cancellation must be refused unless process ownership makes cancellation provably safe. Remote MCP exposes only its approved job-read tools; do not bypass that boundary with direct HTTP calls.

## Agent Registry

Use `agent.list` and `agent.inspect` to observe installed runtime agents such as the local Codex CLI when it is actually available. Expected evidence includes stable identity, runtime kind/version, target binding, observed probe timestamp, capabilities, and job attribution when present.

Do not manufacture background heartbeat history. If Codex is not installed or observable, report the optional capability as unavailable.

## Failure handling

Preserve typed Agent WebMCP and provider errors. Never substitute shell commands, guessed filesystem paths, fabricated service state, or a parallel service/job implementation when an operation is unavailable or rejected.

When Agent WebMCP is unavailable, verify the local runtime and Fleet Cockpit first: check `/health`, confirm the page loads, and inspect the canonical operation catalog. Keep the default runtime bind local unless the operator has explicitly designed a trusted network boundary.
