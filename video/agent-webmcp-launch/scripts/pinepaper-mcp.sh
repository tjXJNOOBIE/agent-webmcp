#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export PATH="$ROOT/node_modules/.bin:$PATH"
export PLAYWRIGHT_BROWSERS_PATH="${PLAYWRIGHT_BROWSERS_PATH:-$ROOT/.browser-cache/playwright}"
BROWSER_PATH="$(node scripts/local-browser-path.mjs)"
if [[ ! -x "$BROWSER_PATH" ]]; then
  echo "Project-local browser is missing or not executable: $BROWSER_PATH" >&2
  exit 1
fi
export PUPPETEER_EXECUTABLE_PATH="$BROWSER_PATH"
export PINEPAPER_EXECUTION_MODE="puppeteer"
exec pinepaper-mcp "$@"
