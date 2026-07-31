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
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-exact-link-tests.py)" = 'd99c94f07cded5a3d91ed0ae89281ba1a131c145'
python3 .msc-build/patch-0.12.2-exact-link-tests.py
python3 .msc-build/patch-0.12.2-link-cloud-followup.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-link-gate-v2.py)" = '2312026e660380dfb4c79a619ee54b9839c1a0a0'
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-link-gate-v4.py)" = '9a54f4a367c809faaf816812cf0c1f885b4c91ed'
python3 .msc-build/patch-0.12.2-final-link-gate-v4.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-link-policy-compile.py)" = 'f38f52a2f6e9733471cecdac818d5b63c6ef9742'
python3 .msc-build/patch-0.12.2-link-policy-compile.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-cloud-family-client.py)" = 'ab1fafb30fe06e82919f5d20e0ec012cb9895db7'
python3 .msc-build/patch-0.12.2-cloud-family-client.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-identities.py)" = 'd24c65668c3747bc99d6d2553cb4c4c4d4dc975b'
python3 .msc-build/patch-0.12.2-final-identities.py
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
    "MyStudyCompanion-phone-0.12.1-debug.apk": "MyStudyCompanion-phone-0.12.2-debug.apk",
    "MyStudyCompanion-wear-0.12.1-debug.apk": "MyStudyCompanion-wear-0.12.2-debug.apk",
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f'Missing 0.12.2 build replacement anchor: {old}')
    source = source.replace(old, new)
source = source.replace(
    'set -euo pipefail\n',
    'set -euo pipefail\ntrap \'rc=$?; printf "FAILED line %s: %s\\n" "$LINENO" "$BASH_COMMAND" >&2; exit "$rc"\' ERR\n',
    1,
)
source = source.replace(
    'PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.\n',
    'PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.\n'
    'PASS: Daily Text home and widget actions use the exact dated JW Library Finder alias.\n'
    'PASS: Bible Journey semicolon shorthand, weekly multi-passage plans, and cross-book ranges resolve to exact NWT Study Edition targets.\n'
    'PASS: generic workbook, meetings, youth-category, and WOL-search pages are not exposed as spiritual material links.\n'
    'PASS: signed daily, weekly, meeting-part, family, and family-section URLs must resolve to exact JW Library targets.\n'
    'PASS: incomplete Finder targets and stale family-section links fail closed instead of opening generic material.\n'
    'PASS: exact-target classification and multi-passage expansion compile through an independent pure Kotlin policy object.\n'
    'PASS: authenticated household invitation creation and join-by-code client flows are compiled and contract-tested.\n'
    'STATUS: protected Firebase, OAuth, and HTTPS backend deployment still require live integration verification.\n',
    1,
)
Path('/tmp/reconstruct-build-0122-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash /tmp/reconstruct-build-0122-generated.sh
