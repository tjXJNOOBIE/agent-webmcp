#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

PLUGIN_VERSION=$(python3 - <<'PY'
import json
from pathlib import Path
print(json.loads(Path('plugins/agent-webmcp/.codex-plugin/plugin.json').read_text())['version'])
PY
)

python3 - "$PLUGIN_VERSION" <<'PY'
import json
import sys
from pathlib import Path

version = sys.argv[1]
root = Path('.')
marketplace = json.loads((root / '.agents/plugins/marketplace.json').read_text())
plugin = json.loads((root / 'plugins/agent-webmcp/.codex-plugin/plugin.json').read_text())
entry = next((item for item in marketplace.get('plugins', []) if item.get('name') == 'agent-webmcp'), None)
assert marketplace.get('name') == 'agent-webmcp', marketplace
assert entry is not None, 'agent-webmcp marketplace entry missing'
assert entry.get('source') == {'source': 'local', 'path': './plugins/agent-webmcp'}, entry
assert entry.get('policy', {}).get('installation') == 'AVAILABLE', entry
assert entry.get('policy', {}).get('authentication') in {'ON_INSTALL', 'ON_USE'}, entry
assert plugin.get('name') == 'agent-webmcp', plugin
assert plugin.get('version') == version, plugin
assert plugin.get('license') == 'MIT', plugin
assert plugin.get('skills') == './skills/', plugin
assert isinstance(plugin.get('author'), dict) and plugin['author'].get('name'), plugin
interface = plugin.get('interface') or {}
for field in ('displayName', 'shortDescription', 'longDescription', 'developerName', 'category', 'capabilities', 'websiteURL', 'defaultPrompt'):
    assert interface.get(field), f'missing interface.{field}'
assert (root / 'plugins/agent-webmcp/skills/agent-webmcp/SKILL.md').is_file()
assert not (root / 'plugins/agent-webmcp/app-binding.example.json').exists(), 'workspace-specific app binding example must not ship in the portable plugin'
app_manifest = root / 'plugins/agent-webmcp/.app.json'
if app_manifest.exists():
    assert plugin.get('apps') == './.app.json', 'existing .app.json must be declared by plugin.json'
else:
    assert 'apps' not in plugin, 'plugin.json must not declare apps before a real .app.json exists'
assert 'mcpServers' not in plugin, 'direct MCP declaration would create a second execution/configuration model'
print('PLUGIN_STATIC_PACKAGE=PASS')
print(f'PLUGIN_STATIC_VERSION={version}')
PY

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
available_json=$(mktemp)
installed_json=$(mktemp)
cleanup() {
  rm -rf "$CODEX_HOME"
  rm -f "$available_json" "$installed_json"
}
trap cleanup EXIT

codex plugin marketplace add "$ROOT" --json >/dev/null
codex plugin list --available --json >"$available_json"
python3 - "$available_json" "$PLUGIN_VERSION" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    data = json.load(handle)
version = sys.argv[2]
plugins = data.get('available', [])
match = next((item for item in plugins if item.get('pluginId') == 'agent-webmcp@agent-webmcp'), None)
assert match is not None, 'Agent WebMCP plugin was not discovered from the local marketplace'
assert match.get('version') == version, match
PY

codex plugin add agent-webmcp@agent-webmcp --json >/dev/null
codex plugin list --json >"$installed_json"
python3 - "$installed_json" "$PLUGIN_VERSION" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    data = json.load(handle)
version = sys.argv[2]
plugins = data.get('installed', [])
match = next((item for item in plugins if item.get('pluginId') == 'agent-webmcp@agent-webmcp'), None)
assert match is not None, 'Agent WebMCP plugin was not installed'
assert match.get('enabled') is True, match
assert match.get('version') == version, match
PY

skill=$(find "$CODEX_HOME/plugins/cache/agent-webmcp/agent-webmcp/$PLUGIN_VERSION" -path '*/skills/agent-webmcp/SKILL.md' -print -quit)
test -n "$skill"
grep -Fq '23 canonical operations' "$skill"
grep -Fq '18 browser-native WebMCP tools' "$skill"
grep -Fq '16 bounded remote MCP tools' "$skill"
grep -Fq 'Never replace a rejected operation with raw shell' "$skill"
grep -Fq 'document.modelContext.registerTool' "$skill"
grep -Fq 'service.discover' "$skill"
grep -Fq 'job.execute' "$skill"
grep -Fq 'agent.list' "$skill"

echo "CODEX_PLUGIN_STATUS=INSTALLED_AND_ENABLED"
echo "CODEX_PLUGIN_VERSION=$PLUGIN_VERSION"
