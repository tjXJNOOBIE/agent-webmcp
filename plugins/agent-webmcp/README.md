# Agent WebMCP plugin package

This plugin packages the Agent WebMCP operating workflow for ChatGPT/Codex. The live MCP connection is intentionally not declared through a plugin-local `.mcp.json`, because imported plugins that declare MCP servers can be treated as Desktop only.

For ChatGPT web, first create the workspace custom Agent WebMCP app through the OpenAI Secure MCP Tunnel. ChatGPT assigns that app a workspace app ID. Then:

1. Copy `app-binding.example.json` to `.app.json`.
2. Replace `REPLACE_WITH_AGENT_WEBMCP_APP_ID` with the real app ID from the same workspace.
3. Add `"apps": "./.app.json"` to `.codex-plugin/plugin.json`.
4. Commit/push and sync the GitHub marketplace.

Do not commit another workspace's private app ID unless its portability is explicitly documented.
