#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source_path = Path('.msc-build/reconstruct-build-0125.sh')
source = source_path.read_text(encoding='utf-8')

jw_gate = "grep -Fq 'jw\\\\.org' MyStudyCompanion/firestore.rules\n"
if source.count(jw_gate) != 1:
    raise SystemExit('Expected exactly one corrected JW-domain fixed-string verification gate.')
if "grep -q 'jw.org' MyStudyCompanion/firestore.rules\n" in source:
    raise SystemExit('Stale unescaped JW-domain verification gate is still present.')

old_lookup = 'git rev-parse HEAD:.msc-build/patch-0.12.2-final-identities.py'
new_lookup = 'git hash-object .msc-build/patch-0.12.2-final-identities.py'
if source.count(old_lookup) != 1:
    raise SystemExit(
        f'Expected exactly one legacy final-identities tree lookup; found {source.count(old_lookup)}.'
    )
source = source.replace(old_lookup, new_lookup, 1)
if old_lookup in source or source.count(new_lookup) != 1:
    raise SystemExit('Failed to replace the fragile final-identities tree lookup exactly once.')

output = Path('/tmp/reconstruct-build-0125-ci-generated.sh')
output.write_text(source, encoding='utf-8')
output.chmod(0o700)
print('Verified JW-domain gate and replaced the fragile tree lookup with a direct blob hash check.')
PY

exec bash /tmp/reconstruct-build-0125-ci-generated.sh
