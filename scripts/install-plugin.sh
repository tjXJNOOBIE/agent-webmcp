#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PLUGIN_REF="agent-webmcp@agent-webmcp"

if ! command -v codex >/dev/null 2>&1; then
  echo "Codex CLI is required to install the Agent WebMCP plugin." >&2
  exit 1
fi
codex --version >/dev/null 2>&1 || {
  echo "Codex CLI is installed but not usable." >&2
  exit 1
}

PLUGIN_VERSION=$(python3 - "$ROOT/plugins/agent-webmcp/.codex-plugin/plugin.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    print(json.load(handle)['version'])
PY
)

marketplace_log=$(mktemp)
install_log=$(mktemp)
installed_json=$(mktemp)
cleanup() {
  rm -f "$marketplace_log" "$install_log" "$installed_json"
}
trap cleanup EXIT

if ! codex plugin marketplace add "$ROOT" --json >"$marketplace_log" 2>&1; then
  echo "Marketplace add returned non-zero; continuing to verify/update the existing marketplace registration." >&2
  cat "$marketplace_log" >&2
fi

if ! codex plugin add "$PLUGIN_REF" --json >"$install_log" 2>&1; then
  echo "Plugin add returned non-zero; checking whether the requested version is already installed." >&2
  cat "$install_log" >&2
fi

codex plugin list --json >"$installed_json"
python3 - "$installed_json" "$PLUGIN_VERSION" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    data = json.load(handle)
version = sys.argv[2]
plugins = data.get('installed', [])
match = next((item for item in plugins if item.get('pluginId') == 'agent-webmcp@agent-webmcp'), None)
assert match is not None, 'Agent WebMCP plugin is not installed'
assert match.get('enabled') is True, match
assert match.get('version') == version, match
print('AGENT_WEBMCP_PLUGIN_STATUS=INSTALLED_AND_ENABLED')
print(f'AGENT_WEBMCP_PLUGIN_VERSION={version}')
PY
