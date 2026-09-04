#!/usr/bin/env sh
set -eu

VERSION="v0.0.14"
DESTINATION=${1:-}

if [ -z "$DESTINATION" ]; then
  echo "Usage: $0 DESTINATION" >&2
  exit 2
fi

for command in curl unzip sha256sum; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "$command is required to install OpenAI tunnel-client" >&2
    exit 1
  fi
done

case "$(uname -s)" in
  Linux) OS=linux ;;
  *) echo "OpenAI tunnel-client install currently supports Linux only in Agent WebMCP" >&2; exit 1 ;;
esac

case "$(uname -m)" in
  x86_64|amd64)
    ARCH=amd64
    SHA256=15bd17e805cad39d412199115bb9e10a978dd35258a114cdf25dd2ae6681c7d3
    ;;
  aarch64|arm64)
    ARCH=arm64
    SHA256=2de3fb879a18edb847e0313592c912f1983685488290a7fdba7ac403e6a4fb0a
    ;;
  *) echo "Unsupported architecture for OpenAI tunnel-client: $(uname -m)" >&2; exit 1 ;;
esac

ASSET="tunnel-client-${VERSION}-${OS}-${ARCH}.zip"
URL="https://github.com/openai/tunnel-client/releases/download/${VERSION}/${ASSET}"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/agent-webmcp-tunnel-client.XXXXXX")
trap 'rm -rf "$TMP"' EXIT INT TERM
ARCHIVE="$TMP/$ASSET"
UNPACKED="$TMP/unpacked"

curl -fL --retry 3 --retry-delay 1 "$URL" -o "$ARCHIVE"
printf '%s  %s\n' "$SHA256" "$ARCHIVE" | sha256sum -c - >/dev/null
mkdir -p "$UNPACKED" "$DESTINATION"
unzip -q "$ARCHIVE" -d "$UNPACKED"

for required in tunnel-client cloudflared LICENSE NOTICE; do
  if [ ! -e "$UNPACKED/$required" ]; then
    echo "OpenAI tunnel-client archive is missing $required" >&2
    exit 1
  fi
done

install -m 0755 "$UNPACKED/tunnel-client" "$DESTINATION/tunnel-client"
install -m 0755 "$UNPACKED/cloudflared" "$DESTINATION/cloudflared"
install -m 0644 "$UNPACKED/LICENSE" "$DESTINATION/LICENSE.openai-tunnel-client"
install -m 0644 "$UNPACKED/NOTICE" "$DESTINATION/NOTICE.openai-tunnel-client"
for metadata in "$UNPACKED"/*-licenses.txt "$UNPACKED"/*.spdx.json "$UNPACKED"/cloudflared-manifest.json; do
  [ -f "$metadata" ] || continue
  install -m 0644 "$metadata" "$DESTINATION/$(basename "$metadata")"
done

VERSION_OUTPUT=$("$DESTINATION/tunnel-client" --version)
case "$VERSION_OUTPUT" in
  0.0.14*) ;;
  *) echo "Unexpected OpenAI tunnel-client version: $VERSION_OUTPUT" >&2; exit 1 ;;
esac

echo "OPENAI_TUNNEL_CLIENT_VERSION=$VERSION_OUTPUT"
echo "OPENAI_TUNNEL_CLIENT_PATH=$DESTINATION/tunnel-client"
