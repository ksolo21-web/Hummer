#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BUILD_SCRIPT=".msc-build/build-0.15.14-vote-persistence-fix.sh"
python3 - "$BUILD_SCRIPT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "grep -Fq 'The official-source Family Worship plan was created on this device' \"$FAMILY\""
new = "grep -Fq 'The official-source Family Worship plan was created and sent to your household' \"$FAMILY\""
if text.count(old) != 1:
    raise SystemExit(f"expected one stale 0.15.11 verification marker, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
PY

exec bash "$BUILD_SCRIPT"
