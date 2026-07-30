#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-wear-0120.sh').read_text(encoding='utf-8')
source = source.replace('installed-0120-evidence/wear', 'installed-0121-evidence/wear')
source = source.replace('MyStudyCompanion-wear-0.12.0-debug.apk', 'MyStudyCompanion-wear-0.12.1-debug.apk')
source = source.replace('PASS: 0.12.0 Wear APK', 'PASS: 0.12.1 Wear APK')
Path('/tmp/installed-wear-0121-generated.sh').write_text(source, encoding='utf-8')
PY
bash /tmp/installed-wear-0121-generated.sh
