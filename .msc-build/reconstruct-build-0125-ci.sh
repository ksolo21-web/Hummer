#!/usr/bin/env bash
set -euo pipefail

EXPECTED_FINAL_IDENTITIES_BLOB='d24c65668c3747bc99d6d2553cb4c4c4d4dc975b'
ACTUAL_FINAL_IDENTITIES_BLOB="$(git hash-object .msc-build/patch-0.12.2-final-identities.py)"
test "$ACTUAL_FINAL_IDENTITIES_BLOB" = "$EXPECTED_FINAL_IDENTITIES_BLOB"

python3 - <<'PY'
from pathlib import Path

source_path = Path('.msc-build/reconstruct-build-0125.sh')
source = source_path.read_text(encoding='utf-8')

jw_gate = "grep -Fq 'jw\\\\.org' MyStudyCompanion/firestore.rules\n"
if source.count(jw_gate) != 1:
    raise SystemExit('Expected exactly one corrected JW-domain fixed-string verification gate.')
if "grep -q 'jw.org' MyStudyCompanion/firestore.rules\n" in source:
    raise SystemExit('Stale unescaped JW-domain verification gate is still present.')

lines = source.splitlines(keepends=True)
replaced = 0
for index, line in enumerate(lines):
    if (
        'patch-0.12.2-final-identities.py' in line
        and line.lstrip().startswith('test ')
    ):
        lines[index] = (
            "test \"$(git hash-object .msc-build/patch-0.12.2-final-identities.py)\" "
            "= 'd24c65668c3747bc99d6d2553cb4c4c4d4dc975b'\n"
        )
        replaced += 1

if replaced > 1:
    raise SystemExit(f'Unexpected duplicate final-identities hash gates: {replaced}.')

output = Path('/tmp/reconstruct-build-0125-ci-generated.sh')
output.write_text(''.join(lines), encoding='utf-8')
output.chmod(0o700)
print(
    'Verified the legacy identity patch directly and normalized '
    f'{replaced} generated tree-hash gate(s).'
)
PY

bash /tmp/reconstruct-build-0125-ci-generated.sh

python3 - <<'PY'
from pathlib import Path

build_file = Path('MyStudyCompanion/build.gradle.kts')
source = build_file.read_text(encoding='utf-8')
anchor = '            "HomeScreen.kt",\n'
if source.count(anchor) != 1:
    raise SystemExit('Expected exactly one HomeScreen compatibility-list anchor.')
for filename in ('DailyFieldServicePointerCard.kt', 'EventNotebooksSection.kt'):
    if f'            "{filename}",\n' in source:
        raise SystemExit(f'{filename} is already present in the compatibility list.')
source = source.replace(
    anchor,
    '            "DailyFieldServicePointerCard.kt",\n'
    '            "EventNotebooksSection.kt",\n'
    + anchor,
    1,
)
build_file.write_text(source, encoding='utf-8')
print('Added the two new Compose screens to the existing import-compatibility task.')
PY
