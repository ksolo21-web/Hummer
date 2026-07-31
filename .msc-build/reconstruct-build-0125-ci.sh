#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source_path = Path('.msc-build/reconstruct-build-0125.sh')
source = source_path.read_text(encoding='utf-8')
old = "grep -q 'jw.org' MyStudyCompanion/firestore.rules\n"
new = "grep -Fq 'jw\\\\.org' MyStudyCompanion/firestore.rules\n"
if source.count(old) != 1:
    raise SystemExit('Expected exactly one stale JW-domain verification gate.')
source = source.replace(old, new, 1)
output = Path('/tmp/reconstruct-build-0125-ci.sh')
output.write_text(source, encoding='utf-8')
output.chmod(0o700)
PY

exec bash /tmp/reconstruct-build-0125-ci.sh
