#!/usr/bin/env sh
set -eu
HOST="${AGENT_WEBMCP_HOST:-127.0.0.1}"
PORT="${AGENT_WEBMCP_PORT:-7188}"
BASE="http://${HOST}:${PORT}"

printf '%s\n' '--- health ---'
curl -fsS "$BASE/health"
printf '\n%s\n' '--- MCP initialize ---'
headers=$(mktemp)
body=$(mktemp)
trap 'rm -f "$headers" "$body"' EXIT
curl -fsS -D "$headers" \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  -X POST "$BASE/mcp" \
  --data '{"jsonrpc":"2.0","id":"verify-init","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"agent-webmcp-verify","version":"1"}}}' \
  -o "$body"
cat "$body"
session=$(awk 'BEGIN{IGNORECASE=1} /^Mcp-Session-Id:/ {gsub("\\r", "", $2); print $2}' "$headers" | tail -n 1)
if [ -z "$session" ]; then
  echo "MCP initialize did not return Mcp-Session-Id" >&2
  exit 1
fi
printf '\n%s\n' '--- MCP tools/list ---'
curl -fsS \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  -H 'Mcp-Protocol-Version: 2025-06-18' \
  -H "Mcp-Session-Id: $session" \
  -X POST "$BASE/mcp" \
  --data '{"jsonrpc":"2.0","id":"verify-tools","method":"tools/list","params":{}}'
printf '\n'
