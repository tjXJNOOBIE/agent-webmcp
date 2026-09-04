# Agent WebMCP plugin package

This plugin packages the Agent WebMCP operating workflow for ChatGPT and Codex. It deliberately does **not** declare `mcp.json` or `.mcp.json`; imported plugins that declare MCP servers can be treated as Desktop only, while the Agent WebMCP runtime is already reachable through an approved app/tunnel boundary.

## Tavall Cloud shared-tunnel mode

This is the intended mode for the Tavall workspace. Keep the existing OpenAI Secure MCP Tunnel attached to the Tavall Cloud ChatGPT adapter at `127.0.0.1:7445/mcp`. Tavall Cloud federates Agent WebMCP's private loopback endpoint at `127.0.0.1:7188/mcp` and publishes the downstream tools with the `agent_webmcp_` prefix.

The plugin remains skill-only in this mode. It does not need `.app.json` because the already-enabled Tavall Cloud app owns the ChatGPT connection. Installing the plugin adds the workflow guidance; it does not create another tunnel, another MCP server, or another source of operation schemas.

The bridge configuration is documented in `docs/integrations/TAVALL_CLOUD_SHARED_TUNNEL.md`.

## Standalone Agent WebMCP app mode

For a workspace that does not use Tavall Cloud, create a custom Agent WebMCP app through an OpenAI Secure MCP Tunnel targeting `127.0.0.1:7188/mcp`. ChatGPT assigns that app a workspace app ID. Then:

1. Copy `app-binding.example.json` to `.app.json`.
2. Replace `REPLACE_WITH_AGENT_WEBMCP_APP_ID` with the real app ID from the same workspace.
3. Add `"apps": "./.app.json"` to `.codex-plugin/plugin.json`.
4. Commit/push and sync the GitHub marketplace.

Do not commit another workspace's private app ID unless its portability is explicitly documented.

## Marketplace import

The repository root contains `.agents/plugins/marketplace.json`. Workspace admins can import the GitHub repository as a marketplace and select the branch containing this package while it is under review. Workspace installation and authentication policy remain workspace-controlled; repository metadata does not bypass those controls.
