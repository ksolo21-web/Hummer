#!/usr/bin/env bash
set -euo pipefail

# Keep the existing verifier and its assertions unchanged. Only move the
# Terms-of-Use scroll gesture into the WebView's empty right gutter so the
# gesture cannot activate linked paragraph text.
SOURCE='.msc-build/installed-phone-jw-0121.sh'
PATCHED='/tmp/installed-phone-jw-0121-safe-scroll.sh'
python3 - <<'PY'
from pathlib import Path
source = Path('.msc-build/installed-phone-jw-0121.sh').read_text(encoding='utf-8')
old = """        if (( swipe % 2 == 1 )); then
          adb shell input swipe 350 1440 350 820 220 >/dev/null 2>&1 || true
        else
          adb shell input swipe 730 1440 730 820 220 >/dev/null 2>&1 || true
        fi
"""
new = """        adb shell input swipe 880 1440 880 820 420 >/dev/null 2>&1 || true
"""
if source.count(old) != 1:
    raise SystemExit('Expected one alternating JW legal-text swipe block.')
Path('/tmp/installed-phone-jw-0121-safe-scroll.sh').write_text(
    source.replace(old, new, 1), encoding='utf-8'
)
PY
bash -n "$PATCHED"
exec bash "$PATCHED" "$@"
