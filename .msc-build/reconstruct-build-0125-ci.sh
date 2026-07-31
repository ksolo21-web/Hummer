#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0125.sh').read_text(encoding='utf-8')
required = [
    "grep -Fq 'jw\\\\.org' MyStudyCompanion/firestore.rules\n",
    "echo '7b29ec25bafd570b6de021a2825fd91118731d4cf74502ee0965250420cd13ea  .msc-build/patch-0.12.2-final-identities.py' | sha256sum -c -\n",
    "echo '8915fcd1e78a698c8528d4ccf0c06cace33251ee35e85b4a523a9c0af60db37f  /tmp/firebase-integrity-0.12.5-overlay.tar.xz' | sha256sum -c -\n",
]
for marker in required:
    if source.count(marker) != 1:
        raise SystemExit(f'Expected exactly one frozen 0.12.5 integrity marker: {marker.strip()}')
if "grep -q 'jw.org' MyStudyCompanion/firestore.rules\n" in source:
    raise SystemExit('Stale unescaped JW-domain verification gate is present.')
if 'git rev-parse HEAD:.msc-build/patch-0.12.2-final-identities.py' in source:
    raise SystemExit('Fragile Git tree lookup for final identities is still present.')
print('Verified frozen 0.12.5 reconstruction integrity markers.')
PY

exec bash .msc-build/reconstruct-build-0125.sh
