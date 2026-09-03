#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export PUPPETEER_SKIP_DOWNLOAD=true
npm install --no-audit --no-fund

export PLAYWRIGHT_BROWSERS_PATH="$ROOT/.browser-cache/playwright"
./node_modules/.bin/playwright install chromium

export UV_CACHE_DIR="$ROOT/.video-tools/uv-cache"
export UV_PYTHON_INSTALL_DIR="$ROOT/.video-tools/python"
mkdir -p "$UV_CACHE_DIR" "$UV_PYTHON_INSTALL_DIR"
uv python install 3.11

FFMPEG_BIN="$(node -e "process.stdout.write(require('ffmpeg-static'))")"
FFPROBE_BIN="$(node -e "process.stdout.write(require('@ffprobe-installer/ffprobe').path)")"
export PATH="$(dirname "$FFMPEG_BIN"):$(dirname "$FFPROBE_BIN"):$PATH"
uvx --python 3.11 --from 'kinocut==1.15.1' kino --version >/dev/null

printf '[ok] video tool bootstrap complete\n'
