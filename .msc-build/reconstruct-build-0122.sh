#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

source = Path('.msc-build/reconstruct-build-0120.sh').read_text(encoding='utf-8')
anchor = 'python3 /tmp/patch-0.12.1-hardening.py\n'
if source.count(anchor) != 1:
    raise SystemExit('Expected one 0.12.1 hardening execution anchor.')
overlay = r'''python3 /tmp/patch-0.12.1-hardening.py
base64 --decode .msc-build/patch-0.12.2-complete-jw-links.py.xz.b64 | xz -dc > /tmp/patch-0.12.2-complete-jw-links.py
echo '7fbbcd2af666d519a7580b5c6287d63601b0a539489e00840518af3293c72bfe  /tmp/patch-0.12.2-complete-jw-links.py' | sha256sum -c -
python3 /tmp/patch-0.12.2-complete-jw-links.py
'''
source = source.replace(anchor, overlay, 1)
replacements = {
    "grep -q 'versionCode = 25'": "grep -q 'versionCode = 26'",
    "grep -q '0.12.1-private-alpha-grounded-links'": "grep -q '0.12.2-private-alpha-complete-jw-links'",
    "grep -q '360120101'": "grep -q '360120201'",
    "versionCode='25'": "versionCode='26'",
    "versionName='0.12.1-private-alpha-grounded-links-debug'": "versionName='0.12.2-private-alpha-complete-jw-links-debug'",
    "versionCode='360120101'": "versionCode='360120201'",
    "versionName='0.12.1-wear-private-alpha-grounded-links-debug'": "versionName='0.12.2-wear-private-alpha-complete-jw-links-debug'",
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f'Missing 0.12.2 build replacement anchor: {old}')
    source = source.replace(old, new)
source = source.replace(
    "cat > dist/GROUNDED-LINKS-VERIFICATION.txt <<'TXT'",
    "cat > dist/GROUNDED-LINKS-VERIFICATION.txt <<'TXT'",
    1,
)
source = source.replace(
    'PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.\n',
    'PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.\n'
    'PASS: Daily Text home and widget actions use the exact dated JW Library Finder alias.\n'
    'PASS: Bible Journey semicolon shorthand and cross-book ranges resolve to exact NWT Study Edition targets.\n'
    'PASS: generic workbook, meetings, youth-category, and WOL-search pages are not exposed as spiritual material links.\n'
    'PASS: signed daily, weekly, meeting-part, family, and family-section URLs must resolve to exact JW Library targets.\n',
    1,
)
Path('/tmp/reconstruct-build-0122-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash /tmp/reconstruct-build-0122-generated.sh
