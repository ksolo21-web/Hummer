#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

path = Path('.msc-build/build-0.15.9-adaptive-scroll.sh')
text = path.read_text(encoding='utf-8')
old = "report.write_text('\\n'.join(rows) + '\\n', encoding='utf-8')"
new = "report.write_text(chr(10).join(rows) + chr(10), encoding='utf-8')"
if text.count(old) != 1:
    raise SystemExit('Responsive audit newline writer was not found exactly once')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('Removed nested newline escaping from the responsive screen audit.')
PY
