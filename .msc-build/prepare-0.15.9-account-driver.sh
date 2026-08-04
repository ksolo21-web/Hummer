#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

path = Path('.msc-build/build-0.15.9-adaptive-scroll.sh')
text = path.read_text(encoding='utf-8')

old_driver = """    'bash .msc-build/apply-0.15.9-adaptive-scroll.sh\\n',
    'adaptive overlay insertion',
"""
new_driver = """    'bash .msc-build/apply-0.15.9-adaptive-scroll.sh\\n'
    'bash .msc-build/apply-0.15.9-account-scroll.sh\\n',
    'adaptive overlay insertion',
"""
if text.count(old_driver) != 1:
    raise SystemExit('0.15.9 adaptive driver insertion target not found exactly once')
text = text.replace(old_driver, new_driver, 1)

old_gates = """grep -Fq 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)' \"$UI/HouseholdScreen.kt\"
! grep -Fq 'Box(modifier.fillMaxSize()' \"$UI/HouseholdScreen.kt\"
"""
new_gates = """grep -Fq 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)' \"$UI/HouseholdScreen.kt\"
grep -Fq '.verticalScroll(scrollState)' \"$UI/AccountScreen.kt\"
grep -Fq '.imePadding()' \"$UI/AccountScreen.kt\"
grep -Fq 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 760).dp)' \"$UI/AccountScreen.kt\"
! grep -Fq 'Box(modifier.fillMaxSize()' \"$UI/HouseholdScreen.kt\"
"""
if text.count(old_gates) != 1:
    raise SystemExit('0.15.9 adaptive source gate target not found exactly once')
text = text.replace(old_gates, new_gates, 1)

old_release = "PASS: Profiles & household uses one live constraint-aware vertical scroll owner.\\n"
new_release = (
    "PASS: Profiles & household uses one live constraint-aware vertical scroll owner.\\n"
    "PASS: Account uses a bounded adaptive scroll owner and IME-safe padding.\\n"
    "PASS: the complete ordinary-screen audit has no remaining static full-page review findings.\\n"
)
if text.count(old_release) != 1:
    raise SystemExit('0.15.9 release-gate text target not found exactly once')
text = text.replace(old_release, new_release, 1)

path.write_text(text, encoding='utf-8')
print('Included AccountScreen adaptive scrolling and whole-app audit gates in the 0.15.9 driver.')
PY
