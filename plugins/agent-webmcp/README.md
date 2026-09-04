# Agent WebMCP plugin

This package teaches ChatGPT/Codex how to use Agent WebMCP without creating another execution authority. The shipping application owns **23 canonical operations**, the browser-native WebMCP projection exposes **18 tools**, and the intentionally narrower remote MCP projection exposes **16 tools**.

The plugin covers managed-service authority, service discovery, runtime agent monitoring, durable jobs, and the boundaries between the human operator surface, browser-native WebMCP, and remote MCP.

## Install locally with Codex

From the Agent WebMCP repository root:

```bash
./scripts/install-plugin.sh
```

Equivalent commands:

```bash
codex plugin marketplace add "$PWD" --json
codex plugin add agent-webmcp@agent-webmcp --json
codex plugin list --json
```

The package is skills-only. It does not declare its own MCP server, does not embed a workspace-specific app ID, and does not duplicate the runtime's operation schemas.

## Import into a ChatGPT workspace

In **Workspace settings → Plugins → Add → Import marketplace** use:

- **Source:** `https://github.com/tjXJNOOBIE/agent-webmcp`
- **Path:** empty
- **Branch:** `main`

Then make Agent WebMCP available to the appropriate workspace roles according to the controls exposed by that workspace.

## Runtime

The plugin is not the server. Install Agent WebMCP itself from the repository root with:

```bash
./scripts/install.sh
./scripts/verify-install.sh
```

Browser-native WebMCP is registered by the real Fleet Cockpit page with `document.modelContext.registerTool(...)` and uses the same canonical backend operations as the human interface.

## Validate the plugin package

```bash
bash scripts/verify-codex-plugin.sh
bash scripts/package-plugin.sh
```

The standalone ZIP is written to `dist/agent-webmcp-plugin.zip`.
