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

old_hash_gate = (
    "test \"$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-identities.py)\" "
    "= 'd24c65668c3747bc99d6d2553cb4c4c4d4dc975b'\n"
)
new_hash_gate = (
    "test \"$(git hash-object .msc-build/patch-0.12.2-final-identities.py)\" "
    "= 'd24c65668c3747bc99d6d2553cb4c4c4d4dc975b'\n"
)
if source.count(old_hash_gate) != 1:
    raise SystemExit('Expected exactly one legacy final-identities Git tree hash gate.')
source = source.replace(old_hash_gate, new_hash_gate, 1)

output = Path('/tmp/reconstruct-build-0125-ci-generated.sh')
output.write_text(source, encoding='utf-8')
output.chmod(0o700)
print('Verified JW-domain gate and replaced the fragile tree lookup with a direct blob hash check.')
PY

exec bash /tmp/reconstruct-build-0125-ci-generated.sh
