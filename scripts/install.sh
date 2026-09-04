#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
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
WITH_TUNNEL=0
TUNNEL_ID="${CONTROL_PLANE_TUNNEL_ID:-}"
TUNNEL_KEY_FILE=""

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
  --with-tunnel       install OpenAI Secure MCP tunnel-client v0.0.14 as a companion
  --tunnel-id ID      Secure MCP Tunnel ID (or CONTROL_PLANE_TUNNEL_ID env)
  --tunnel-key-file PATH
                      file containing runtime API key (or CONTROL_PLANE_API_KEY env)
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
    --with-tunnel) WITH_TUNNEL=1; shift ;;
    --tunnel-id) TUNNEL_ID=$2; shift 2 ;;
    --tunnel-key-file) TUNNEL_KEY_FILE=$2; shift 2 ;;
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

TUNNEL_API_KEY="${CONTROL_PLANE_API_KEY:-}"
if [ -n "$TUNNEL_KEY_FILE" ]; then
  if [ ! -f "$TUNNEL_KEY_FILE" ]; then
    echo "tunnel runtime API key file does not exist: $TUNNEL_KEY_FILE" >&2
    exit 2
  fi
  IFS= read -r TUNNEL_API_KEY < "$TUNNEL_KEY_FILE" || true
fi
if [ "$WITH_TUNNEL" -eq 1 ]; then
  case "$TUNNEL_ID" in
    tunnel_*) ;;
    *) echo "--with-tunnel requires a tunnel ID via --tunnel-id or CONTROL_PLANE_TUNNEL_ID" >&2; exit 2 ;;
  esac
  case "$TUNNEL_ID" in
    *[!A-Za-z0-9_]*) echo "tunnel ID contains unsupported characters" >&2; exit 2 ;;
  esac
  case "$TUNNEL_API_KEY" in
    ''|*[!A-Za-z0-9_.-]*) echo "--with-tunnel requires CONTROL_PLANE_API_KEY or --tunnel-key-file with a runtime API key" >&2; exit 2 ;;
  esac
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
chmod 700 "$CONFIG_DIR" "$STATE_DIR"
rm -rf "$PREFIX/bin" "$PREFIX/lib"
cp -R "$DIST/bin" "$DIST/lib" "$PREFIX/"
chmod +x "$PREFIX/bin/agent-webmcp"

cat > "$CONFIG_DIR/agent-webmcp.env" <<ENV
AGENT_WEBMCP_HOST=$HOST
AGENT_WEBMCP_PORT=$PORT
AGENT_WEBMCP_DATA_DIR=$STATE_DIR
ENV
chmod 600 "$CONFIG_DIR/agent-webmcp.env"

TUNNEL_STATUS="not installed"
if [ "$WITH_TUNNEL" -eq 1 ]; then
  TUNNEL_DIR="$PREFIX/tunnel"
  sh "$SCRIPT_DIR/install-openai-tunnel-client.sh" "$TUNNEL_DIR"
  URL_HOST="$HOST"
  case "$URL_HOST" in
    *:*) URL_HOST="[$URL_HOST]" ;;
  esac
  cat > "$CONFIG_DIR/agent-webmcp-tunnel.env" <<ENV
CONTROL_PLANE_TUNNEL_ID=$TUNNEL_ID
CONTROL_PLANE_API_KEY=$TUNNEL_API_KEY
MCP_SERVER_URL=http://$URL_HOST:$PORT/mcp
ENV
  chmod 600 "$CONFIG_DIR/agent-webmcp-tunnel.env"
  TUNNEL_STATUS="installed: OpenAI tunnel-client v0.0.14"
fi

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
    if [ "$WITH_TUNNEL" -eq 1 ]; then
      cat > "$SERVICE_DIR/agent-webmcp-tunnel.service" <<SERVICE
[Unit]
Description=Agent WebMCP OpenAI Secure MCP Tunnel
Requires=agent-webmcp.service
After=agent-webmcp.service network-online.target
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=$CONFIG_DIR/agent-webmcp-tunnel.env
ExecStart=$PREFIX/tunnel/tunnel-client run
Restart=on-failure
RestartSec=2
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict

[Install]
WantedBy=multi-user.target
SERVICE
    fi
    systemctl daemon-reload
    systemctl enable --now agent-webmcp.service
    if [ "$WITH_TUNNEL" -eq 1 ]; then
      systemctl enable --now agent-webmcp-tunnel.service
      TUNNEL_STATUS="running: agent-webmcp-tunnel.service"
    fi
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
    if [ "$WITH_TUNNEL" -eq 1 ]; then
      cat > "$SERVICE_DIR/agent-webmcp-tunnel.service" <<SERVICE
[Unit]
Description=Agent WebMCP OpenAI Secure MCP Tunnel
Requires=agent-webmcp.service
After=agent-webmcp.service network-online.target
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=$CONFIG_DIR/agent-webmcp-tunnel.env
ExecStart=$PREFIX/tunnel/tunnel-client run
Restart=on-failure
RestartSec=2
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=default.target
SERVICE
    fi
    systemctl --user daemon-reload
    systemctl --user enable --now agent-webmcp.service
    if [ "$WITH_TUNNEL" -eq 1 ]; then
      systemctl --user enable --now agent-webmcp-tunnel.service
      TUNNEL_STATUS="running: agent-webmcp-tunnel.service"
    fi
  fi
fi

cat <<DONE
Agent WebMCP installed.
  app:       $PREFIX
  config:    $CONFIG_DIR/agent-webmcp.env
  data:      $STATE_DIR
  mode:      $SERVICE_MODE
  discovery: $DISCOVERY_STATUS
  tunnel:    $TUNNEL_STATUS
  health:    http://$HOST:$PORT/health
  MCP:       http://$HOST:$PORT/mcp
DONE

if [ "$WITH_TUNNEL" -eq 1 ] && [ "$SERVICE_MODE" = "none" ]; then
  echo "Tunnel config: $CONFIG_DIR/agent-webmcp-tunnel.env"
  echo "Start manually with: set -a; . '$CONFIG_DIR/agent-webmcp-tunnel.env'; set +a; '$PREFIX/tunnel/tunnel-client' run"
fi

if [ "$SERVICE_MODE" = "user" ]; then
  cat <<'NOTICE'
Note: the user-service install can read systemd state where the account is permitted, but system service
start/stop/restart/reload usually requires PolicyKit/root authority. Use --system-service for the full
system-service control path, while keeping Agent WebMCP bound to loopback and reaching ChatGPT through
the dedicated OpenAI Secure MCP Tunnel companion.
NOTICE
fi
