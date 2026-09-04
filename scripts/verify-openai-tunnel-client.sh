#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/agent-webmcp-tunnel-verify.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

sh "$ROOT/scripts/install-openai-tunnel-client.sh" "$TMP/runtime" >"$TMP/install.out"
grep -Fq 'OPENAI_TUNNEL_CLIENT_VERSION=0.0.14' "$TMP/install.out"
test -x "$TMP/runtime/tunnel-client"
test -x "$TMP/runtime/cloudflared"
test -f "$TMP/runtime/LICENSE.openai-tunnel-client"
test -f "$TMP/runtime/NOTICE.openai-tunnel-client"
"$TMP/runtime/tunnel-client" help quickstart >/dev/null

echo 'OPENAI_TUNNEL_CLIENT_BUNDLE=PASS'
