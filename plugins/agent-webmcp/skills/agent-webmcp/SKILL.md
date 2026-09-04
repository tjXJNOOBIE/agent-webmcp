---
name: agent-webmcp
description: Operate Agent WebMCP's canonical service, agent, discovery, and durable-job workflows while respecting its WebMCP and remote MCP authority boundaries.
---

# Agent WebMCP

Use Agent WebMCP as a constrained operations surface. Never replace a rejected operation with raw shell, filesystem mutation, arbitrary working-directory execution, or arbitrary process execution.

## Product model

The shipping runtime has **23 canonical operations**, including **9 mutating operations**. Those canonical operations are the product authority and are reused by CLI/HTTP/browser surfaces rather than reimplemented per transport.

The projections are intentionally different:

- **18 browser-native WebMCP tools** are exposed through `document.modelContext`. They include managed-service add/remove but intentionally omit target operations, service discovery, job creation, and job cancellation.
- **16 bounded remote MCP tools** expose system/metrics, Agent Registry inspection, managed-service observation/lifecycle, and read-only durable-job evidence. They intentionally omit service add/remove/discovery, target workflows, job creation/cancellation, and generic shell behavior.
- The human Fleet Cockpit / canonical HTTP operator surface can use all approved canonical workflows, including discovery and durable job authoring.

Do not widen a narrower projection merely because the canonical operation exists.

## Canonical operation families

The canonical runtime includes:

- `system.status`, `metrics.snapshot`
- `target.list`, `target.inspect`
- `agent.list`, `agent.inspect`
- `service.list`, `service.add`, `service.remove`, `service.discover`, `service.inspect`, `service.status`, `service.logs`, `service.diagnostics`, `service.start`, `service.stop`, `service.restart`, `service.reload`
- `job.list`, `job.inspect`, `job.logs`, `job.execute`, `job.cancel`

Treat the live canonical catalog as the final source of operation schemas and access metadata. Do not maintain a second handwritten execution model in the plugin.

## Connection mode

Use the direct Agent WebMCP app connected through its dedicated Secure MCP Tunnel. The tunnel targets the private loopback MCP endpoint, normally `http://127.0.0.1:7188/mcp`; Agent WebMCP itself must remain loopback-only while `NO_AUTH` is active. The installer can provision the pinned official OpenAI `tunnel-client` companion and `agent-webmcp-tunnel.service` when given a tunnel ID and runtime API key. Tavall Cloud is the development/control environment for this repository, not the runtime federation layer for the shipping Agent WebMCP app.

Direct MCP tool names remain the canonical operation IDs, such as `system.status`, `agent.list`, `service.inspect`, `service.logs`, and `service.restart`. Do not create prefixed aliases or a second execution model in the plugin.

## Managed services

Managed-service enrollment is the lifecycle authority boundary. Guessed or unenrolled service IDs must be rejected before provider mutation. Service identifiers are passed as validated process arguments, never interpolated into shell commands.

For lifecycle requests:

1. Inspect the managed service and current provider-observed state.
2. Execute only the requested start, stop, restart, or reload operation.
3. Inspect state again and use bounded logs or diagnostics when useful.
4. Report provider or authorization rejection exactly. Do not claim success merely because a request was accepted.

Treat `service.logs` as bounded cursor-based journal access, not an unbounded terminal stream. `service.remove` removes Agent WebMCP enrollment; it does not delete the provider service.

## Discovery

`service.discover` belongs to the canonical operator surface, not the 18-tool WebMCP or 16-tool remote MCP projection. Deterministic/provider-backed discovery is the default.

Optional AI-assisted discovery uses the user's existing Codex CLI only when explicitly requested. It is a read-only candidate finder: every returned service ID must be re-inspected through the real provider before registration, and hallucinated/unobservable IDs must be rejected. Agent WebMCP does not install Codex.

## Durable jobs

`job.execute` creates service-bound durable work through the canonical operator surface. An empty prompt means an explicit deterministic service operation. A prompt means a one-shot job using the user's existing Codex CLI.

Deterministic jobs may run immediately, at a future time, or recur when the current schema allows it. Recurring AI prompt jobs are intentionally rejected. Jobs persist durable state, logs, execution evidence, schedule, result/failure, and optional agent attribution. Interrupted RUNNING jobs must recover truthfully rather than pretending they completed.

`job.cancel` may cancel queued/scheduled work. Running cancellation must be refused unless process ownership makes cancellation provably safe. Remote MCP exposes only `job.list`, `job.inspect`, and `job.logs`; do not bypass that boundary with direct HTTP calls from an MCP App.

## Agent Registry

Agent monitoring ships. Use `agent.list` and `agent.inspect` to observe installed runtime agents such as the local Codex CLI when it is actually available. Expected evidence includes stable identity (for example `codex:local`), runtime kind/version, target binding, observed heartbeat/probe timestamp, capabilities such as service-job prompting/read-only discovery, and job attribution when present.

Do not manufacture background heartbeat history. If Codex is not installed or observable, report it as an optional capability being unavailable.

## MCP App boundary

The bounded MCP surface can render the Fleet Cockpit MCP App from `ui://agent-webmcp/fleet-cockpit-v1`. The app may call the 16 tools made visible to the app, but it must not call the canonical HTTP API directly to reach hidden operations. Operator-only discovery, enrollment mutation, target workflows, and job authoring/cancellation stay operator-only.

## Failure handling

Preserve typed Agent WebMCP/provider errors. Never substitute shell commands, guessed filesystem paths, fabricated service state, or a parallel service/job implementation when an operation is unavailable or rejected.

If the Agent WebMCP app is unavailable, verify the private loopback runtime first and then the dedicated Agent WebMCP Secure MCP Tunnel connection. Keep the Agent WebMCP runtime loopback-only; do not expose its private MCP/HTTP port directly to the public network.
