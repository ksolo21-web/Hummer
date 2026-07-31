#!/usr/bin/env bash
set -euo pipefail

# Generate from the last complete retry-safe verifier, then make navigation
# assertions adaptive: compact layouts use More; expanded layouts expose
# AI Study directly in the permanent navigation rail.
PINNED_BASE='1761f2b654cbc3e96a5735dec752ef3032e8abdd'
BASE_PATH='.msc-build/installed-phone-migration-0121.sh'
GENERATED='/tmp/installed-phone-migration-0121-generated.sh'

git fetch --no-tags --depth=1 origin "$PINNED_BASE" >/dev/null 2>&1
git show "${PINNED_BASE}:${BASE_PATH}" > /tmp/installed-phone-migration-0121-base.sh

python3 - <<'PY'
from pathlib import Path

source = Path('/tmp/installed-phone-migration-0121-base.sh').read_text(encoding='utf-8')
old_home = """    if python3 /tmp/msc-ui.py exists 'More'; then
      printf 'PASS: home navigation available after UI check %d.\\n' "$attempt" | tee -a "$EVIDENCE/home-navigation.txt"
      return 0
    fi
"""
new_home = """    if python3 /tmp/msc-ui.py exists 'More' || python3 /tmp/msc-ui.py exists 'AI Study'; then
      printf 'PASS: adaptive home navigation available after UI check %d.\\n' "$attempt" | tee -a "$EVIDENCE/home-navigation.txt"
      return 0
    fi
"""
if source.count(old_home) != 1:
    raise SystemExit('Expected one compact-only home-navigation assertion.')
source = source.replace(old_home, new_home, 1)

old_tablet = """launch_package
ensure_home_navigation
tap_with_retry 'More' 'tablet More navigation'
tap_with_retry 'AI Study Assistant' 'tablet AI Study Assistant'
python3 /tmp/msc-ui.py assert 'Source protection'
"""
new_tablet = """launch_package
ensure_home_navigation
if python3 /tmp/msc-ui.py exists 'AI Study'; then
  tap_with_retry 'AI Study' 'tablet AI Study rail navigation'
else
  tap_with_retry 'More' 'tablet More navigation fallback'
  tap_with_retry 'AI Study Assistant' 'tablet AI Study Assistant fallback'
fi
python3 /tmp/msc-ui.py assert 'Source protection'
"""
if source.count(old_tablet) != 1:
    raise SystemExit('Expected one tablet compact-navigation sequence.')
source = source.replace(old_tablet, new_tablet, 1)

old_result = "launched on phone and tablet geometry, displayed the hardened AI source-protection UI"
new_result = "launched on phone and tablet geometry, used compact bottom navigation on phone and the expanded AI Study rail on tablet, displayed the hardened AI source-protection UI"
if source.count(old_result) != 1:
    raise SystemExit('Expected migration result statement.')
source = source.replace(old_result, new_result, 1)

Path('/tmp/installed-phone-migration-0121-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash "$GENERATED"
