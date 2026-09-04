#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
UPDATE=1
INSTALL_PLUGIN=0

usage() {
  cat <<'USAGE'
Usage: ./scripts/deploy-main.sh [--no-update] [--install-plugin] [-- installer-options...]

Deploy the current Agent WebMCP main release on this machine.

  --no-update       deploy the current checkout without fetching/checking out main
  --install-plugin  install/refresh the Agent WebMCP Codex plugin after runtime deploy
  --                forward all remaining arguments to scripts/install.sh

Examples:
  ./scripts/deploy-main.sh
  ./scripts/deploy-main.sh --install-plugin
  ./scripts/deploy-main.sh -- --no-service
  ./scripts/deploy-main.sh -- --system-service
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-update) UPDATE=0; shift ;;
    --install-plugin) INSTALL_PLUGIN=1; shift ;;
    --) shift; break ;;
    -h|--help) usage; exit 0 ;;
    *) break ;;
  esac
done
INSTALL_ARGS=("$@")

cd "$ROOT"

if [[ "$UPDATE" -eq 1 ]]; then
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "Refusing to update a dirty worktree. Commit/stash changes or use --no-update." >&2
    exit 1
  fi
  git fetch origin main
  git checkout main
  git merge --ff-only origin/main
fi

bash scripts/quality-preflight.sh --manifest
bash scripts/gradle clean installDist

DIST="$ROOT/build/install/agent-webmcp"
SYSTEM_SERVICE=0
VERIFY_HOST="127.0.0.1"
VERIFY_PORT="7188"
for ((i=0; i<${#INSTALL_ARGS[@]}; i++)); do
  case "${INSTALL_ARGS[$i]}" in
    --system-service) SYSTEM_SERVICE=1 ;;
    --host)
      if (( i + 1 < ${#INSTALL_ARGS[@]} )); then VERIFY_HOST="${INSTALL_ARGS[$((i+1))]}"; fi
      ;;
    --port)
      if (( i + 1 < ${#INSTALL_ARGS[@]} )); then VERIFY_PORT="${INSTALL_ARGS[$((i+1))]}"; fi
      ;;
  esac
done

if [[ "$SYSTEM_SERVICE" -eq 1 && "$(id -u)" -ne 0 ]]; then
  if ! command -v sudo >/dev/null 2>&1; then
    echo "--system-service needs root and sudo is unavailable." >&2
    exit 1
  fi
  sudo "$ROOT/scripts/install.sh" --dist "$DIST" "${INSTALL_ARGS[@]}"
else
  "$ROOT/scripts/install.sh" --dist "$DIST" "${INSTALL_ARGS[@]}"
fi

AGENT_WEBMCP_HOST="$VERIFY_HOST" AGENT_WEBMCP_PORT="$VERIFY_PORT" bash scripts/verify-install.sh

if [[ "$INSTALL_PLUGIN" -eq 1 ]]; then
  bash scripts/install-plugin.sh
fi

printf 'AGENT_WEBMCP_DEPLOY_STATUS=VERIFIED\n'
printf 'AGENT_WEBMCP_DEPLOY_COMMIT=%s\n' "$(git rev-parse HEAD)"
printf 'AGENT_WEBMCP_HEALTH_URL=http://%s:%s/health\n' "$VERIFY_HOST" "$VERIFY_PORT"
