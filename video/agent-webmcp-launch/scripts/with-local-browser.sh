#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export PATH="$ROOT/node_modules/.bin:$PATH"
FFMPEG_BIN="$(node -e "process.stdout.write(require('ffmpeg-static'))")"
FFPROBE_BIN="$(node -e "process.stdout.write(require('@ffprobe-installer/ffprobe').path)")"
if [[ ! -x "$FFMPEG_BIN" || ! -x "$FFPROBE_BIN" ]]; then
  echo "Project-local FFmpeg/FFprobe is missing. Run npm run tools:bootstrap first." >&2
  exit 1
fi
export PATH="$(dirname "$FFMPEG_BIN"):$(dirname "$FFPROBE_BIN"):$PATH"
export PLAYWRIGHT_BROWSERS_PATH="${PLAYWRIGHT_BROWSERS_PATH:-$ROOT/.browser-cache/playwright}"
BROWSER_PATH="$(node scripts/local-browser-path.mjs)"
if [[ ! -x "$BROWSER_PATH" ]]; then
  echo "Project-local browser is missing or not executable: $BROWSER_PATH" >&2
  exit 1
fi
export PUPPETEER_EXECUTABLE_PATH="$BROWSER_PATH"
export HYPERFRAMES_BROWSER_PATH="$BROWSER_PATH"
exec "$@"
