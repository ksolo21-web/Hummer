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
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-link-policy-compile.py)" = 'fd11806ab632ee69694a682ce96869f53439c57a'
python3 .msc-build/patch-0.12.2-link-policy-compile.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-cloud-family-client.py)" = 'ab1fafb30fe06e82919f5d20e0ec012cb9895db7'
python3 .msc-build/patch-0.12.2-cloud-family-client.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-identities.py)" = 'd24c65668c3747bc99d6d2553cb4c4d4dc975b'
python3 .msc-build/patch-0.12.2-final-identities.py
test "$(git rev-parse HEAD:.msc-build/patch-0.12.2-final-test-imports.py)" = '877f7a74f0ce329d4e8e99ac9d87abb65780e33e'
python3 .msc-build/patch-0.12.2-final-test-imports.py

cat .msc-build/firebase-family-0.12.3.part*.b64 | base64 --decode > /tmp/firebase-family-0.12.3-overlay.tar.xz
echo 'fc5d7909d3f739e6eb33d95f56c17e14013f0ffb6f685f7b78a9cea59a3cc8a2  /tmp/firebase-family-0.12.3-overlay.tar.xz' | sha256sum -c -
tar -xJf /tmp/firebase-family-0.12.3-overlay.tar.xz -C MyStudyCompanion

cat .msc-build/firebase-family-0.12.4.part*.b64 | base64 --decode > /tmp/firebase-family-0.12.4-overlay.tar.xz
echo '787dce372ae4ea6179b0310bd4831e427d9dc182d0d625b76747e8eb6e2f944a  /tmp/firebase-family-0.12.4-overlay.tar.xz' | sha256sum -c -
tar -xJf /tmp/firebase-family-0.12.4-overlay.tar.xz -C MyStudyCompanion

cat .msc-build/firebase-integrity-0.12.5.part*.b64 | base64 --decode > /tmp/firebase-integrity-0.12.5-overlay.tar.xz
echo '8915fcd1e78a698c8528d4ccf0c06cace33251ee35e85b4a523a9c0af60db37f  /tmp/firebase-integrity-0.12.5-overlay.tar.xz' | sha256sum -c -
tar -xJf /tmp/firebase-integrity-0.12.5-overlay.tar.xz -C .

grep -q 'versionCode = 29' MyStudyCompanion/app/build.gradle.kts
grep -q '0.12.5-private-alpha-firebase-integrity' MyStudyCompanion/app/build.gradle.kts
grep -q 'firebase-firestore' MyStudyCompanion/gradle/libs.versions.toml
grep -q 'FirebaseFirestore' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
! grep -q 'BackendApi' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -q 'noOtherHousehold' MyStudyCompanion/firestore.rules
grep -q 'vote-' MyStudyCompanion/firestore.rules
grep -Fq 'jw\\.org' MyStudyCompanion/firestore.rules
grep -q 'allow delete: if false' MyStudyCompanion/firestore.rules
test -s .msc-build/firebase-rules-tests/rules.test.cjs
'''
source = source.replace(anchor, overlay, 1)

replacements = {
    "grep -q 'versionCode = 25'": "grep -q 'versionCode = 29'",
    "grep -q '0.12.1-private-alpha-grounded-links'": "grep -q '0.12.5-private-alpha-firebase-integrity'",
    "grep -q '360120101'": "grep -q '360120201'",
    "versionCode='25'": "versionCode='29'",
    "versionName='0.12.1-private-alpha-grounded-links-debug'": "versionName='0.12.5-private-alpha-firebase-integrity-debug'",
    "versionCode='360120101'": "versionCode='360120201'",
    "versionName='0.12.1-wear-private-alpha-grounded-links-debug'": "versionName='0.12.2-wear-private-alpha-complete-jw-links-debug'",
    "MyStudyCompanion-phone-0.12.1-debug.apk": "MyStudyCompanion-phone-0.12.5-debug.apk",
    "MyStudyCompanion-wear-0.12.1-debug.apk": "MyStudyCompanion-wear-0.12.2-debug.apk",
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f'Missing 0.12.5 build replacement anchor: {old}')
    source = source.replace(old, new)

source = source.replace(
    'set -euo pipefail\n',
    'set -euo pipefail\ntrap \'rc=$?; printf "FAILED line %s: %s\\n" "$LINENO" "$BASH_COMMAND" >&2; exit "$rc"\' ERR\n',
    1,
)
source = source.replace(
    'PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.\n',
    'PASS: AI citation cards route through JwLibraryLinkResolver instead of a direct browser intent.\n'
    'PASS: Firebase Authentication and structured Cloud Firestore family synchronization compile directly without a private HTTPS backend.\n'
    'PASS: one-household-per-account integrity, deterministic one-vote-per-member IDs, organizer permissions, member progress isolation, and JW-only family links are enforced.\n'
    'PASS: Firestore rules and twenty emulator authorization, integrity, and abuse tests are bundled for independent verification.\n',
    1,
)
Path('/tmp/reconstruct-build-0125-generated.sh').write_text(source, encoding='utf-8')
PY

exec bash /tmp/reconstruct-build-0125-generated.sh
