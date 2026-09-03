#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

FFMPEG_BIN="$(node -e "process.stdout.write(require('ffmpeg-static'))" 2>/dev/null || true)"
FFPROBE_BIN="$(node -e "process.stdout.write(require('@ffprobe-installer/ffprobe').path)" 2>/dev/null || true)"
if [[ -z "$FFMPEG_BIN" || ! -x "$FFMPEG_BIN" ]]; then
  echo "ffmpeg-static is missing. Run npm run tools:bootstrap in $ROOT first." >&2
  exit 1
fi
if [[ -z "$FFPROBE_BIN" || ! -x "$FFPROBE_BIN" ]]; then
  echo "project-local ffprobe is missing. Run npm run tools:bootstrap in $ROOT first." >&2
  exit 1
fi

export PATH="$(dirname "$FFMPEG_BIN"):$(dirname "$FFPROBE_BIN"):$PATH"
export UV_CACHE_DIR="${UV_CACHE_DIR:-$ROOT/.video-tools/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${UV_PYTHON_INSTALL_DIR:-$ROOT/.video-tools/python}"

if ! uv python find 3.11 >/dev/null 2>&1; then
  echo "Project-local Python 3.11 for Kinocut is missing. Run npm run tools:bootstrap first." >&2
  exit 1
fi

exec uvx --offline --python 3.11 --from 'kinocut==1.15.1' kino "$@"
