#!/usr/bin/env bash
set -euo pipefail

# Reuse the complete verifier from the last tested commit. Only move its
# Terms-of-Use scroll gesture into the WebView's empty right gutter so linked
# paragraph text cannot launch Android's chooser or Chrome, and allow enough
# iterations to reach the official document's page-bottom marker.
PINNED_VERIFIER='dd6521ce39d56f620b3804cd9f24b95d8be69aa7'
SOURCE_PATH='.msc-build/installed-phone-jw-0121.sh'
SOURCE='/tmp/installed-phone-jw-0121-source.sh'
PATCHED='/tmp/installed-phone-jw-0121-safe-scroll.sh'

git fetch --no-tags --depth=1 origin "$PINNED_VERIFIER" >/dev/null 2>&1
git show "${PINNED_VERIFIER}:${SOURCE_PATH}" > "$SOURCE"
python3 - <<'PY'
from pathlib import Path
source = Path('/tmp/installed-phone-jw-0121-source.sh').read_text(encoding='utf-8')
old_swipe = """        if (( swipe % 2 == 1 )); then
          adb shell input swipe 350 1440 350 820 220 >/dev/null 2>&1 || true
        else
          adb shell input swipe 730 1440 730 820 220 >/dev/null 2>&1 || true
        fi
"""
new_swipe = """        adb shell input swipe 880 1440 880 820 420 >/dev/null 2>&1 || true
"""
old_limit = '  for step in $(seq 1 50); do\n'
new_limit = '  for step in $(seq 1 70); do\n'
if source.count(old_swipe) != 1:
    raise SystemExit('Expected one alternating JW legal-text swipe block.')
if source.count(old_limit) != 1:
    raise SystemExit('Expected one JW Terms iteration ceiling.')
source = source.replace(old_swipe, new_swipe, 1).replace(old_limit, new_limit, 1)
Path('/tmp/installed-phone-jw-0121-safe-scroll.sh').write_text(source, encoding='utf-8')
PY
bash -n "$PATCHED"
bash .msc-build/run-installed-phone-jw-core-return.sh --preflight
exec bash "$PATCHED" "$@"
