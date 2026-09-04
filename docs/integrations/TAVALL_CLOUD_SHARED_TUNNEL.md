# Tavall Cloud shared Secure MCP Tunnel integration

> **Status:** Implemented integration design; Tavall Cloud bridge source is committed separately.

Agent WebMCP can reuse the Tavall workspace's existing OpenAI Secure MCP Tunnel instead of creating a second tunnel identity. Tavall Cloud remains the ChatGPT ingress/authentication boundary while Agent WebMCP remains authoritative for its MCP tool schemas and operation execution.

## Topology

```text
ChatGPT
  -> existing OpenAI Secure MCP Tunnel
  -> Tavall Cloud ChatGPT adapter 127.0.0.1:7445/mcp
  -> loopback downstream MCP federation
  -> Agent WebMCP 127.0.0.1:7188/mcp
  -> canonical Agent WebMCP OperationExecutor/providers
```

The bridge does not copy the 16 Agent WebMCP schemas into Tavall Cloud. It negotiates the downstream MCP server, preserves each downstream schema/result/annotation, prefixes the public tool name, and proxies the call back to the original downstream tool.

The Tavall Cloud bridge implementation is checkpointed at commit:

```text
4d9234bc2739d8dce67c4572b655a3382553028a
```

## Adapter configuration

Configure the existing Tavall Cloud ChatGPT adapter with:

```text
TAVALL_CHATGPT_WEB_DOWNSTREAM_MCP_ENDPOINT=http://127.0.0.1:7188/mcp
TAVALL_CHATGPT_WEB_DOWNSTREAM_MCP_ID=agent-webmcp
TAVALL_CHATGPT_WEB_DOWNSTREAM_MCP_PREFIX=agent_webmcp_
TAVALL_CHATGPT_WEB_DOWNSTREAM_MCP_TIMEOUT_SECONDS=10
```

The downstream endpoint is deliberately restricted to explicit loopback HTTP. The normal Tavall Cloud listener and Secure MCP Tunnel remain unchanged at `127.0.0.1:7445/mcp`; routine adapter deployment must not recreate or repoint the tunnel.

With the default prefix, examples include:

```text
service.inspect  -> agent_webmcp_service_inspect
service.logs     -> agent_webmcp_service_logs
service.restart  -> agent_webmcp_service_restart
system.status    -> agent_webmcp_system_status
```

## Failure behavior

Federation is opt-in. Without the downstream environment variable, Tavall Cloud publishes its normal catalog unchanged. When federation is configured, startup must successfully negotiate a non-empty downstream tool catalog; the adapter does not silently publish stale copied Agent WebMCP tools when the downstream is unavailable.

Agent WebMCP remains independently loopback-bound and keeps its own managed-service, input, provider, MCP session, and operation policies. Tavall Cloud federation is transport composition, not authority escalation.

## Plugin relationship

The Agent WebMCP plugin is skill-only in Tavall shared-tunnel mode. It relies on the already-connected Tavall Cloud app to supply the `agent_webmcp_*` tools, avoiding both a second tunnel and the ChatGPT Desktop-only behavior that can result from plugin-local MCP declarations. A standalone workspace may instead bind the plugin to its own existing Agent WebMCP custom app with `.app.json`.

## Verification

Before treating the shared-tunnel mode as live:

1. Verify Agent WebMCP health on `127.0.0.1:7188/health`.
2. Initialize its `/mcp` endpoint and prove the direct downstream exposes exactly 16 bounded tools.
3. Start/restart the Tavall Cloud adapter with the downstream environment above while leaving the persistent Secure MCP Tunnel process untouched.
4. Verify Tavall Cloud's MCP catalog contains the 16 `agent_webmcp_*` projections in addition to its native tools.
5. Invoke a read operation such as `agent_webmcp_system_status` through ChatGPT/Tavall Cloud and confirm the payload came from Agent WebMCP.
6. Exercise write operations only against explicitly safe managed services and verify observed post-state; authorization rejection is evidence of the boundary, not permission to bypass it.
