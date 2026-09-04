---
name: agent-webmcp
description: Inspect system health and metrics, inspect managed service state and logs, and perform bounded service lifecycle actions through Agent WebMCP.
---

# Agent WebMCP

Use Agent WebMCP as a constrained operations surface. Never replace a rejected operation with raw shell, filesystem mutation, or arbitrary process execution.

## Connection modes

Prefer the already-connected Tavall Cloud app when it exposes tools beginning with `agent_webmcp_`. That is the shared-tunnel mode used by the Tavall workspace: ChatGPT reaches Tavall Cloud through the existing Secure MCP Tunnel, and Tavall Cloud proxies the private Agent WebMCP MCP endpoint without copying its schemas or execution behavior. Do not create or request a second tunnel when this federated surface is available.

In standalone workspaces, use the direct Agent WebMCP app if one is explicitly connected. The operation semantics are the same; only the external tool names differ.

## Scope

The app-facing runtime provides system health/metrics, runtime-agent inspection, managed-service list/inspect/status/logs/diagnostics/lifecycle, and read-only durable job state. Discovery, generic shell execution, arbitrary filesystem/process access, and remote job submission are intentionally outside the 16-tool MCP app surface.

Federated Tavall Cloud names are produced by prefixing `agent_webmcp_` and replacing punctuation with underscores. Examples include `agent_webmcp_system_status`, `agent_webmcp_service_inspect`, `agent_webmcp_service_logs`, and `agent_webmcp_service_restart`.

## Safe operating workflow

Read current state before mutating a service. For start, stop, restart, or reload requests:

1. Inspect the managed service and current observed state.
2. Execute only the requested lifecycle operation.
3. Inspect state again and, when available, read bounded logs or diagnostics to verify the observed result.
4. Report provider or authorization rejection exactly. Do not fall back to shell commands or claim a mutation succeeded because a request was accepted.

Treat `service.logs` as bounded cursor-based near-live journal access, not an unbounded terminal stream. Managed-service enrollment is the lifecycle authority boundary; guessed or unenrolled service IDs should be rejected before provider mutation.

If the Agent WebMCP downstream is unavailable in shared-tunnel mode, verify the private runtime at `127.0.0.1:7188` and the Tavall Cloud downstream-federation configuration. Keep Agent WebMCP loopback-only; do not expose port 7188 publicly.
