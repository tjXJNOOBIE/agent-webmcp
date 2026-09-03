---
name: agent-webmcp
description: Inspect system health and metrics, inspect service state and logs, and perform bounded service lifecycle actions through Agent WebMCP.
---

# Agent WebMCP

Use the Agent WebMCP tools as a constrained operations surface.

## Scope

Use the app for system health/metrics and service list, inspect, status, logs, start, stop, restart, and reload operations. Do not look for shell execution, filesystem mutation, arbitrary process launch, or durable job submission because those concerns are intentionally outside the app-facing MCP surface.

## Workflow

Read current state before mutating a service. For restart/reload/start/stop requests, inspect the target service first when practical, execute the requested lifecycle action, then inspect or read logs afterward to verify observed state. Treat `service.logs` as bounded cursor-based near-live journal access, not an unbounded terminal stream.

If the MCP connection is missing locally, verify `http://127.0.0.1:7188/health` and `http://127.0.0.1:7188/mcp`. For ChatGPT, use OpenAI Secure MCP Tunnel rather than exposing the local port publicly.
