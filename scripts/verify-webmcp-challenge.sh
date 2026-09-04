#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

fail() {
  echo "WebMCP Challenge verification failed: $*" >&2
  exit 1
}

[ -s LICENSE ] || fail "root LICENSE is missing"
[ -s README.md ] || fail "README is missing"
[ -s docs/hackathon/WEBMCP_CHALLENGE.md ] || fail "hackathon evidence document is missing"
WEBMCP_JS=src/main/resources/web/agent-webmcp-webmcp.js
[ -s "$WEBMCP_JS" ] || fail "production WebMCP script is missing"

grep -Fq 'document.modelContext.registerTool' "$WEBMCP_JS" || fail "production code does not register WebMCP tools"
grep -Fq 'inputSchema: operation.inputSchema' "$WEBMCP_JS" || fail "WebMCP tools are not using the canonical JSON Schema"
grep -Fq "readOnlyHint: operation.access === 'READ_ONLY'" "$WEBMCP_JS" || fail "WebMCP read-only metadata is not derived from canonical access metadata"
grep -Fq 'signal' "$WEBMCP_JS" || fail "WebMCP execution cancellation is not forwarded"
grep -Fq '/api/v1/operations' "$WEBMCP_JS" || fail "WebMCP surface is not projected from the canonical operation endpoint"

registrations=$(grep -RIl --include='*.js' 'document.modelContext.registerTool' src/main/resources/web || true)
[ "$registrations" = "$WEBMCP_JS" ] || fail "production WebMCP registration must have exactly one owning script"

echo "WebMCP Challenge static compliance checks passed."
