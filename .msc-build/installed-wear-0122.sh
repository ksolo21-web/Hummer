#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-wear-0121.sh').read_text(encoding='utf-8')
replacements = {
    'installed-0121-evidence/wear': 'installed-0122-evidence/wear',
    'MyStudyCompanion-wear-0.12.1-debug.apk': 'MyStudyCompanion-wear-0.12.2-debug.apk',
    'PASS: 0.12.1 Wear APK': 'PASS: 0.12.2 Wear APK',
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f'Missing Wear 0.12.2 replacement anchor: {old}')
    source = source.replace(old, new)
Path('/tmp/installed-wear-0122-generated.sh').write_text(source, encoding='utf-8')
PY
bash -n /tmp/installed-wear-0122-generated.sh
exec bash /tmp/installed-wear-0122-generated.sh
