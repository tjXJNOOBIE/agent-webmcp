#!/usr/bin/env sh
set -eu

HOST="${AGENT_WEBMCP_HOST:-127.0.0.1}"
PORT="${AGENT_WEBMCP_PORT:-7188}"
URL_HOST="$HOST"
case "$URL_HOST" in
  \[*\]) ;;
  *:*) URL_HOST="[$URL_HOST]" ;;
esac
BASE="http://${URL_HOST}:${PORT}"

headers=$(mktemp)
body=$(mktemp)
health=$(mktemp)
tools=$(mktemp)
cleanup() {
  rm -f "$headers" "$body" "$health" "$tools"
}
trap cleanup EXIT HUP INT TERM

printf '%s\n' '--- health ---'
ready=0
attempt=0
while [ "$attempt" -lt 40 ]; do
  if curl -fsS "$BASE/health" -o "$health" 2>/dev/null; then
    ready=1
    break
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done
if [ "$ready" -ne 1 ]; then
  echo "Agent WebMCP did not become healthy at $BASE/health" >&2
  exit 1
fi
cat "$health"

printf '\n%s\n' '--- MCP initialize ---'
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
  --data '{"jsonrpc":"2.0","id":"verify-tools","method":"tools/list","params":{}}' \
  -o "$tools"
cat "$tools"
if ! grep -Fq '"tools":[' "$tools" || ! grep -Fq '"system.status"' "$tools"; then
  echo "MCP tools/list did not return the Agent WebMCP tool projection" >&2
  exit 1
fi
printf '\n'
