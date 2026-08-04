#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WRAPPER="$ROOT/.msc-build/.build-0.15.9-wrapper.sh"
rm -f "$WRAPPER"
cp .msc-build/build-0.15.9-adaptive-scroll.sh "$WRAPPER"
python3 - "$WRAPPER" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')
old = 'DRIVER="$(mktemp)"'
new = 'DRIVER="$ROOT/.msc-build/.build-0.15.9-runtime.sh"\nrm -f "$DRIVER"'
if text.count(old) != 1:
    raise SystemExit('0.15.9 runtime driver assignment not found exactly once')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
PY
chmod +x "$WRAPPER"
bash "$WRAPPER"
rm -f "$WRAPPER" "$ROOT/.msc-build/.build-0.15.9-runtime.sh"
