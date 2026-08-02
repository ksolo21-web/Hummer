#!/usr/bin/env python3
from pathlib import Path

source_path = Path('.msc-build/reconstruct-build-0125.sh')
source = source_path.read_text(encoding='utf-8')

# Some extracted archives record root/group ownership from the build runner.
# Reconstructing in a restricted or rootless workspace must preserve bytes and
# modes without attempting to apply those host-specific owners.
source = source.replace('tar -xJf ', 'tar --no-same-owner -xJf ')

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
    raise SystemExit(f'Unexpected duplicate final identity gates: {replaced}.')

source = ''.join(lines)
final_exec = 'exec bash /tmp/reconstruct-build-0125-generated.sh\n'
replacement = r'''python3 - <<'PY_SOURCE_ONLY'
from pathlib import Path
generated = Path('/tmp/reconstruct-build-0125-generated.sh')
text = generated.read_text(encoding='utf-8')
text = text.replace('tar -xJf ', 'tar --no-same-owner -xJf ')
marker = '\ncd MyStudyCompanion\ngradle --no-daemon'
if marker not in text:
    raise SystemExit('Legacy Gradle build marker was not found.')
source_only = text.split(marker, 1)[0] + '\n'
output = Path('/tmp/reconstruct-build-0125-source-only.sh')
output.write_text(source_only, encoding='utf-8')
output.chmod(0o700)
PY_SOURCE_ONLY
bash /tmp/reconstruct-build-0125-source-only.sh
'''

if source.count(final_exec) != 1:
    raise SystemExit('Expected exactly one final reconstruction exec.')
source = source.replace(final_exec, replacement, 1)

driver = Path('/tmp/reconstruct-build-0125-source-driver.sh')
driver.write_text(source, encoding='utf-8')
driver.chmod(0o700)
print(
    'Prepared source-only 0.12.5 reconstruction driver; '
    f'normalized {replaced} legacy identity gate(s).'
)
