#!/usr/bin/env node
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import path from "node:path";
import process from "node:process";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDir, "..");

const servers = {
  animotion: {
    command: path.join(projectRoot, "node_modules", ".bin", "animotion-mcp"),
    args: [],
  },
  pinepaper: {
    command: "bash",
    args: [path.join(projectRoot, "scripts", "pinepaper-mcp.sh")],
  },
};

function usage() {
  console.error(`Usage:
  node scripts/mcp-client.mjs <animotion|pinepaper> list-tools
  node scripts/mcp-client.mjs <animotion|pinepaper> call <tool-name> [json-arguments]

Examples:
  npm run mcp:list:animotion
  node scripts/mcp-client.mjs animotion call <tool> '{"query":"server"}'
`);
}

const [serverName, action, toolName, rawArgs = "{}"] = process.argv.slice(2);
const server = servers[serverName];

if (!server || !action) {
  usage();
  process.exit(2);
}

let toolArgs = {};
if (action === "call") {
  if (!toolName) {
    usage();
    process.exit(2);
  }
  try {
    toolArgs = JSON.parse(rawArgs);
  } catch (error) {
    console.error(`Invalid JSON arguments: ${error.message}`);
    process.exit(2);
  }
}

const client = new Client(
  { name: "agent-webmcp-video-mcp-client", version: "1.0.0" },
  { capabilities: {} },
);

const transport = new StdioClientTransport({
  command: server.command,
  args: server.args,
  cwd: projectRoot,
  env: process.env,
  stderr: "inherit",
});

try {
  await client.connect(transport);

  if (action === "list-tools") {
    const result = await client.listTools();
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } else if (action === "call") {
    const result = await client.callTool({
      name: toolName,
      arguments: toolArgs,
    });
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } else {
    usage();
    process.exitCode = 2;
  }
} finally {
  await client.close().catch(() => {});
}
