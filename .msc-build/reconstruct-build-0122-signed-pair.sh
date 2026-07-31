#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/reconstruct-build-0121-signed-pair.sh').read_text(encoding='utf-8')
old = 'bash .msc-build/reconstruct-build-0120.sh\n'
new = 'bash .msc-build/reconstruct-build-0122.sh\n'
if source.count(old) != 1:
    raise SystemExit('Expected one hardened build invocation in signed-pair script.')
source = source.replace(old, new, 1)
source = source.replace('0.12.1 phone APK', '0.12.2 phone APK')
source = source.replace('0.12.1 phone and Wear packages', '0.12.2 phone and Wear packages')
Path('/tmp/reconstruct-build-0122-signed-pair-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash /tmp/reconstruct-build-0122-signed-pair-generated.sh
