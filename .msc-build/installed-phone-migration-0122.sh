#!/usr/bin/env bash
set -euo pipefail

SOURCE='.msc-build/installed-phone-migration-0121.sh'
WRAPPER='/tmp/installed-phone-migration-0122-wrapper.sh'
GENERATED='/tmp/installed-phone-migration-0121-generated.sh'

python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-phone-migration-0121.sh').read_text(encoding='utf-8')
old = 'exec bash "$GENERATED"\n'
if source.count(old) != 1:
    raise SystemExit('Expected one 0.12.1 migration execution point.')
Path('/tmp/installed-phone-migration-0122-wrapper.sh').write_text(
    source.replace(old, ':\n', 1),
    encoding='utf-8',
)
PY
bash "$WRAPPER"
test -s "$GENERATED"
python3 - <<'PY'
from pathlib import Path
path = Path('/tmp/installed-phone-migration-0121-generated.sh')
source = path.read_text(encoding='utf-8')
replacements = {
    'installed-0121-evidence/migration': 'installed-0122-evidence/migration',
    'MyStudyCompanion-phone-0.12.1-debug.apk': 'MyStudyCompanion-phone-0.12.2-debug.apk',
    'upgrade-0121': 'upgrade-0122',
    'versionCode=25': 'versionCode=26',
    'versionName=0.12.1-private-alpha-grounded-links-debug': 'versionName=0.12.2-private-alpha-complete-jw-links-debug',
    'same-signer 0.12.1 APK': 'same-signer 0.12.2 APK',
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f'Missing migration 0.12.2 replacement anchor: {old}')
    source = source.replace(old, new)
path.write_text(source, encoding='utf-8')
PY
bash -n "$GENERATED"
exec bash "$GENERATED"
