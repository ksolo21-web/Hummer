#!/usr/bin/env python3
from pathlib import Path

path = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = path.read_text(encoding='utf-8')

replacements = {
    "grep -q 'versionCode = 32'": "grep -q 'versionCode = 33'",
    "grep -q '0.14.0-private-alpha-interactive-workbooks'": "grep -q '0.14.1-private-alpha-unified-study-reader'",
    "grep -q 'versionCode = 360140001'": "grep -q 'versionCode = 360141001'",
    "grep -q '0.14.0-wear-private-alpha-interactive-workbooks'": "grep -q '0.14.1-wear-private-alpha-unified-study-reader'",
    "versionCode='32'": "versionCode='33'",
    "versionName='0.14.0-private-alpha-interactive-workbooks'": "versionName='0.14.1-private-alpha-unified-study-reader'",
    "versionCode='360140001'": "versionCode='360141001'",
    "versionName='0.14.0-wear-private-alpha-interactive-workbooks'": "versionName='0.14.1-wear-private-alpha-unified-study-reader'",
    "MyStudyCompanion-phone-0.14.0": "MyStudyCompanion-phone-0.14.1",
    "MyStudyCompanion-wear-0.14.0": "MyStudyCompanion-wear-0.14.1",
    "MyStudyCompanion-Web-0.14.0-PWA.zip": "MyStudyCompanion-Web-0.14.1-PWA.zip",
    "My Study Companion 0.14.0 Interactive Workbooks": "My Study Companion 0.14.1 Unified Study Reader",
}

changed = 0
for old, new in replacements.items():
    count = source.count(old)
    if count:
        source = source.replace(old, new)
        changed += count

required = (
    "grep -q 'versionCode = 33'",
    "0.14.1-private-alpha-unified-study-reader",
    "grep -q 'versionCode = 360141001'",
    "0.14.1-wear-private-alpha-unified-study-reader",
    "MyStudyCompanion-phone-0.14.1-canonical-temporary-signed.apk",
    "MyStudyCompanion-wear-0.14.1-canonical-temporary-signed.apk",
    "MyStudyCompanion-Web-0.14.1-PWA.zip",
)
for marker in required:
    if marker not in source:
        raise SystemExit(f'Missing corrected 0.14.1 CI marker: {marker}')

stale = (
    "grep -q 'versionCode = 32'",
    "versionCode='32'",
    "0.14.0-private-alpha-interactive-workbooks",
    "versionCode='360140001'",
    "0.14.0-wear-private-alpha-interactive-workbooks",
    "MyStudyCompanion-phone-0.14.0",
    "MyStudyCompanion-wear-0.14.0",
    "MyStudyCompanion-Web-0.14.0-PWA.zip",
)
for marker in stale:
    if marker in source:
        raise SystemExit(f'Stale 0.14.0 CI marker remains: {marker}')

path.write_text(source, encoding='utf-8')
print(f'Updated {changed} final build identity and artifact marker occurrence(s) for 0.14.1.')
