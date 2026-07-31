#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0125.sh').read_text(encoding='utf-8')
expected = "grep -Fq 'jw\\\\.org' MyStudyCompanion/firestore.rules\n"
if source.count(expected) != 1:
    raise SystemExit('Expected exactly one corrected JW-domain fixed-string verification gate.')
if "grep -q 'jw.org' MyStudyCompanion/firestore.rules\n" in source:
    raise SystemExit('Stale unescaped JW-domain verification gate is still present.')
print('Verified corrected 0.12.5 JW-domain integrity marker.')
PY

exec bash .msc-build/reconstruct-build-0125.sh
