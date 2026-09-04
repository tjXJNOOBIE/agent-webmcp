#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

if ! command -v codex >/dev/null 2>&1; then
  echo "CODEX_PLUGIN_STATUS=SKIPPED_CODEX_NOT_INSTALLED"
  exit 0
fi
if ! codex --version >/dev/null 2>&1; then
  echo "CODEX_PLUGIN_STATUS=SKIPPED_CODEX_UNUSABLE"
  exit 0
fi

CODEX_HOME=$(mktemp -d "$ROOT/.codex-plugin-test.XXXXXX")
export CODEX_HOME
cleanup() {
  rm -rf "$CODEX_HOME"
}
trap cleanup EXIT

available_json=$(mktemp)
installed_json=$(mktemp)
trap 'rm -f "$available_json" "$installed_json"; cleanup' EXIT

codex plugin marketplace add "$ROOT" --json >/dev/null
codex plugin list --available --json >"$available_json"
python3 - "$available_json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    data = json.load(handle)
plugins = data.get('available', [])
match = next((item for item in plugins if item.get('pluginId') == 'agent-webmcp@agent-webmcp'), None)
assert match is not None, 'Agent WebMCP plugin was not discovered from the local marketplace'
assert match.get('version') == '0.3.0', match
PY

codex plugin add agent-webmcp@agent-webmcp --json >/dev/null
codex plugin list --json >"$installed_json"
python3 - "$installed_json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    data = json.load(handle)
plugins = data.get('installed', [])
match = next((item for item in plugins if item.get('pluginId') == 'agent-webmcp@agent-webmcp'), None)
assert match is not None, 'Agent WebMCP plugin was not installed'
assert match.get('enabled') is True, match
assert match.get('version') == '0.3.0', match
PY

skill=$(find "$CODEX_HOME/plugins/cache/agent-webmcp/agent-webmcp/0.3.0" -path '*/skills/agent-webmcp/SKILL.md' -print -quit)
test -n "$skill"
grep -Fq '23 canonical operations' "$skill"
grep -Fq '18 browser-native WebMCP tools' "$skill"
grep -Fq '16 bounded remote MCP tools' "$skill"
grep -Fq 'Never replace a rejected operation with raw shell' "$skill"
grep -Fq 'service.discover' "$skill"
grep -Fq 'job.execute' "$skill"
grep -Fq 'agent.list' "$skill"

echo "CODEX_PLUGIN_STATUS=INSTALLED_AND_ENABLED"
echo "CODEX_PLUGIN_VERSION=0.3.0"
