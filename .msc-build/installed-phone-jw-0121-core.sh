#!/usr/bin/env bash
set -euo pipefail

# Generate the complete strict JW Library verifier from the last known source,
# then fix Bash nounset ordering in both foreground-evidence functions. In a
# single `local` command Bash expands window_file before evidence_file has been
# assigned, which stopped the verifier immediately after the first real Finder
# launch even though the intent itself started successfully.
PINNED_BASE='ffa565c8242ca868233b647da68b8f23315ed743'
BASE_PATH='.msc-build/installed-phone-jw-0121-core.sh'
GENERATED='/tmp/installed-phone-jw-0121-core-generated.sh'

git fetch --no-tags --depth=1 origin "$PINNED_BASE" >/dev/null 2>&1
git show "${PINNED_BASE}:${BASE_PATH}" > /tmp/installed-phone-jw-0121-core-base.sh

python3 - <<'PY'
from pathlib import Path

path = Path('/tmp/installed-phone-jw-0121-core-base.sh')
source = path.read_text(encoding='utf-8')
old = '  local evidence_file="$1" label="$2" attempt stable=0 window_file="${evidence_file%.txt}-window.txt"\n'
new = (
    '  local evidence_file="$1" label="$2" attempt stable=0\n'
    '  local window_file="${evidence_file%.txt}-window.txt"\n'
)
count = source.count(old)
if count != 2:
    raise SystemExit(f'Expected two strict-shell foreground declarations, found {count}.')
source = source.replace(old, new)
Path('/tmp/installed-phone-jw-0121-core-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash "$GENERATED" "$@"
