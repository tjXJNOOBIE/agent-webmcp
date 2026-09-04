# Agent WebMCP Codex plugin

This plugin packages the operating model for Agent WebMCP without creating another execution authority. The runtime owns **23 canonical operations**, the browser-native WebMCP projection intentionally exposes **18 tools**, and the bounded remote MCP projection intentionally exposes **16 tools**.

The plugin teaches Codex the differences among those surfaces, managed-service authority, deterministic and optional Codex-assisted discovery, durable jobs, Agent Registry monitoring, and the Fleet Cockpit MCP App. It does not declare a generic shell and does not copy operation schemas or business logic.

## Agent WebMCP app connection

Use a dedicated Agent WebMCP Secure MCP Tunnel targeting the runtime's private loopback MCP endpoint, normally `http://127.0.0.1:7188/mcp`. The Agent WebMCP process remains loopback-only; the tunnel is the public transport boundary. Tavall Cloud remains the development/control environment and is not required to federate Agent WebMCP tools for this app.

After the custom Agent WebMCP app exists in the workspace:

1. Copy `app-binding.example.json` to `.app.json`.
2. Replace `REPLACE_WITH_AGENT_WEBMCP_APP_ID` with the real Agent WebMCP app ID from that workspace.
3. Add `"apps": "./.app.json"` to `.codex-plugin/plugin.json` only when packaging a workspace-bound plugin build.
4. Validate the app connection against `/mcp`, including `tools/list`, `resources/list`, `resources/read`, and Fleet Cockpit rendering.

Do not commit another workspace's private app ID unless its portability is explicitly documented.

## Codex marketplace installation

The repository root contains `.agents/plugins/marketplace.json`. A local validation/install looks like:

```bash
codex plugin marketplace add /path/to/agent-webmcp
codex plugin add agent-webmcp@agent-webmcp
codex plugin list --json
```

Repository CI uses `scripts/verify-codex-plugin.sh` when Codex is installed to verify marketplace discovery, installation, enablement, version, and the cached skill contract. Codex remains optional for installing/running Agent WebMCP itself.
