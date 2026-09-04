#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
OUTPUT="$ROOT/dist/agent-webmcp-plugin.zip"

cd "$ROOT"
mkdir -p dist

python3 - "$OUTPUT" <<'PY'
from pathlib import Path
import hashlib
import sys
import zipfile

root = Path.cwd()
output = Path(sys.argv[1])
files = [
    Path('.agents/plugins/marketplace.json'),
    Path('LICENSE'),
    Path('plugins/agent-webmcp/.codex-plugin/plugin.json'),
    Path('plugins/agent-webmcp/README.md'),
    Path('plugins/agent-webmcp/skills/agent-webmcp/SKILL.md'),
    Path('scripts/install-plugin.sh'),
]

missing = [str(path) for path in files if not (root / path).is_file()]
if missing:
    raise SystemExit(f'missing plugin package inputs: {missing}')

with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for relative in sorted(files, key=lambda value: value.as_posix()):
        data = (root / relative).read_bytes()
        info = zipfile.ZipInfo(relative.as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o100755 if relative == Path('scripts/install-plugin.sh') else 0o100644) << 16
        archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

data = output.read_bytes()
print(f'PLUGIN_PACKAGE={output}')
print(f'PLUGIN_PACKAGE_BYTES={len(data)}')
print(f'PLUGIN_PACKAGE_SHA256={hashlib.sha256(data).hexdigest()}')
PY
