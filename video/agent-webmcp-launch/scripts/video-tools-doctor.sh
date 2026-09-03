#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export PATH="$ROOT/node_modules/.bin:$PATH"
fail=0

check_bin() {
  local label="$1"
  local path="$2"
  if [[ -x "$path" ]]; then
    printf '[ok] %s\n' "$label"
  else
    printf '[missing] %s\n' "$label" >&2
    fail=1
  fi
}

check_bin "Animotion MCP" "node_modules/.bin/animotion-mcp"
check_bin "PinePaper MCP" "node_modules/.bin/pinepaper-mcp"
check_bin "HyperFrames CLI" "node_modules/.bin/hyperframes"

if npm run --silent browser:smoke; then
  printf '[ok] project-local browser\n'
else
  printf '[missing] project-local browser failed\n' >&2
  fail=1
fi

FFMPEG_BIN="$(node -e "process.stdout.write(require('ffmpeg-static'))" 2>/dev/null || true)"
FFPROBE_BIN="$(node -e "process.stdout.write(require('@ffprobe-installer/ffprobe').path)" 2>/dev/null || true)"
if [[ -n "$FFMPEG_BIN" && -x "$FFMPEG_BIN" && -n "$FFPROBE_BIN" && -x "$FFPROBE_BIN" ]]; then
  printf '[ok] ffmpeg-static %s\n' "$FFMPEG_BIN"
  printf '[ok] ffprobe %s\n' "$FFPROBE_BIN"
  if scripts/kinocut-mcp.sh doctor --json | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const j=JSON.parse(s);process.exit(j.summary?.required_ok?0:1)})"; then
    printf '[ok] Kinocut 1.15.1 doctor (offline)\n'
  else
    printf '[missing] Kinocut doctor failed\n' >&2
    fail=1
  fi
else
  printf '[missing] project-local ffmpeg/ffprobe; run npm run tools:bootstrap\n' >&2
  fail=1
fi

exit "$fail"
