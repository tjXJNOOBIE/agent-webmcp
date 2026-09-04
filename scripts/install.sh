#!/usr/bin/env sh
set -eu

PREFIX="${HOME}/.local/share/agent-webmcp"
CONFIG_DIR="${HOME}/.config/agent-webmcp"
STATE_DIR="${HOME}/.local/state/agent-webmcp"
HOST="127.0.0.1"
PORT="7188"
DIST=""
SERVICE_MODE="user"
PREFIX_SET=0
CONFIG_SET=0
STATE_SET=0

usage() {
  cat <<USAGE
Usage: ./scripts/install.sh [options]
  --prefix PATH       install application files here
  --config-dir PATH   write agent-webmcp.env here
  --state-dir PATH    durable runtime state directory
  --host HOST         bind host (default: 127.0.0.1)
  --port PORT         bind port (default: 7188)
  --dist PATH         install an existing application distribution instead of building
  --system-service    install a root system service for systemd lifecycle mutations
  --no-service        do not create/start a systemd service
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX=$2; PREFIX_SET=1; shift 2 ;;
    --config-dir) CONFIG_DIR=$2; CONFIG_SET=1; shift 2 ;;
    --state-dir) STATE_DIR=$2; STATE_SET=1; shift 2 ;;
    --host) HOST=$2; shift 2 ;;
    --port) PORT=$2; shift 2 ;;
    --dist) DIST=$2; shift 2 ;;
    --system-service) SERVICE_MODE="system"; shift ;;
    --no-service) SERVICE_MODE="none"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ "$SERVICE_MODE" = "system" ]; then
  if [ -z "$DIST" ]; then
    echo "--system-service requires --dist. Build installDist as your normal user with scripts/gradle, then install that distribution with sudo." >&2
    exit 1
  fi
  if [ "$(id -u)" -ne 0 ]; then
    echo "--system-service requires root. Build first, then run this installer with sudo and --dist." >&2
    exit 1
  fi
  [ "$PREFIX_SET" -eq 1 ] || PREFIX="/opt/agent-webmcp"
  [ "$CONFIG_SET" -eq 1 ] || CONFIG_DIR="/etc/agent-webmcp"
  [ "$STATE_SET" -eq 1 ] || STATE_DIR="/var/lib/agent-webmcp"
fi

case "$PORT" in
  ''|*[!0-9]*) echo "port must be numeric" >&2; exit 2 ;;
esac
if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "port must be between 1 and 65535" >&2
  exit 2
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 25 or newer is required." >&2
  exit 1
fi
JAVA_MAJOR=$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 25 ]; then
  echo "Java 25 or newer is required; found major version ${JAVA_MAJOR:-unknown}." >&2
  exit 1
fi

if [ -z "$DIST" ]; then
  if [ ! -x ./gradlew ]; then
    echo "Run this installer from the Agent WebMCP repository root." >&2
    exit 1
  fi
  if [ -f ./scripts/ci/prepare-tavall-sources ]; then
    bash ./scripts/ci/prepare-tavall-sources
  fi
  bash ./scripts/gradle installDist
  DIST="build/install/agent-webmcp"
fi

if [ ! -x "$DIST/bin/agent-webmcp" ] || [ ! -d "$DIST/lib" ]; then
  echo "Invalid Agent WebMCP distribution: $DIST" >&2
  exit 1
fi

mkdir -p "$PREFIX" "$CONFIG_DIR" "$STATE_DIR"
chmod 700 "$STATE_DIR"
rm -rf "$PREFIX/bin" "$PREFIX/lib"
cp -R "$DIST/bin" "$DIST/lib" "$PREFIX/"
chmod +x "$PREFIX/bin/agent-webmcp"

cat > "$CONFIG_DIR/agent-webmcp.env" <<ENV
AGENT_WEBMCP_HOST=$HOST
AGENT_WEBMCP_PORT=$PORT
AGENT_WEBMCP_DATA_DIR=$STATE_DIR
ENV
chmod 600 "$CONFIG_DIR/agent-webmcp.env"

DISCOVERY_STATUS="skipped: systemctl unavailable"
if command -v systemctl >/dev/null 2>&1; then
  if AGENT_WEBMCP_DATA_DIR="$STATE_DIR" "$PREFIX/bin/agent-webmcp" discover-services; then
    DISCOVERY_STATUS="completed (deterministic only)"
  else
    DISCOVERY_STATUS="attempted but provider discovery failed; use Discover Services after startup"
    echo "Agent WebMCP deterministic service discovery could not complete during install; continuing without AI discovery." >&2
  fi
fi

if [ "$SERVICE_MODE" != "none" ]; then
  if ! command -v systemctl >/dev/null 2>&1; then
    echo "systemctl is unavailable; rerun with --no-service or install systemd." >&2
    exit 1
  fi

  if [ "$SERVICE_MODE" = "system" ]; then
    SERVICE_DIR="${AGENT_WEBMCP_SYSTEMD_SYSTEM_DIR:-/etc/systemd/system}"
    SERVICE_FILE="$SERVICE_DIR/agent-webmcp.service"
    mkdir -p "$SERVICE_DIR"
    cat > "$SERVICE_FILE" <<SERVICE
[Unit]
Description=Agent WebMCP system operations server
After=network.target

[Service]
Type=simple
EnvironmentFile=$CONFIG_DIR/agent-webmcp.env
ExecStart=$PREFIX/bin/agent-webmcp serve
Restart=on-failure
RestartSec=2
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=$STATE_DIR

[Install]
WantedBy=multi-user.target
SERVICE
    systemctl daemon-reload
    systemctl enable --now agent-webmcp.service
  else
    SERVICE_DIR="${HOME}/.config/systemd/user"
    SERVICE_FILE="$SERVICE_DIR/agent-webmcp.service"
    mkdir -p "$SERVICE_DIR"
    cat > "$SERVICE_FILE" <<SERVICE
[Unit]
Description=Agent WebMCP local operations server
After=network.target

[Service]
Type=simple
EnvironmentFile=$CONFIG_DIR/agent-webmcp.env
ExecStart=$PREFIX/bin/agent-webmcp serve
Restart=on-failure
RestartSec=2
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=default.target
SERVICE
    systemctl --user daemon-reload
    systemctl --user enable --now agent-webmcp.service
  fi
fi

cat <<DONE
Agent WebMCP installed.
  app:      $PREFIX
  config:   $CONFIG_DIR/agent-webmcp.env
  data:     $STATE_DIR
  mode:     $SERVICE_MODE
  discovery: $DISCOVERY_STATUS
  health:   http://$HOST:$PORT/health
  MCP:      http://$HOST:$PORT/mcp
DONE

if [ "$SERVICE_MODE" = "user" ]; then
  cat <<'NOTICE'
Note: the user-service install can read systemd state where the account is permitted, but system service
start/stop/restart/reload usually requires PolicyKit/root authority. Use --system-service for the full
system-service control path, while keeping Agent WebMCP bound to loopback/private tunnel access.
NOTICE
fi
