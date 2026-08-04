#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

path = Path('.msc-build/apply-0.15.9-adaptive-scroll.sh')
text = path.read_text(encoding='utf-8')
old = "assert 'modifier = Modifier.weight(1f),' not in family_text[family_text.index('FamilyHubSection.HOUSEHOLD'):]"
new = "assert 'layoutSpec = layoutSpec,\\n                    modifier = Modifier.weight(1f),' not in family_text"
if text.count(old) != 1:
    raise SystemExit('Broad household layout assertion was not found exactly once')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('Narrowed 0.15.9 layout gate to the exact removed HouseholdScreen weight call.')
PY
