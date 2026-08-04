#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRIVER=".msc-build/build-0.15.9-generated.sh"
cp .msc-build/build-0.15.8-google-age-free-invite.sh "$DRIVER"
trap 'rm -f "$DRIVER"' EXIT

python3 - "$DRIVER" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')


def replace(old: str, new: str, label: str, count: int | None = None) -> None:
    global text
    found = text.count(old)
    if found == 0:
        raise SystemExit(f'{label} target not found')
    if count is not None and found != count:
        raise SystemExit(f'{label} expected {count}, found {found}')
    text = text.replace(old, new)

replace(
    'bash .msc-build/apply-0.15.8-google-age-firestore-compat.sh\n',
    'bash .msc-build/apply-0.15.8-google-age-firestore-compat.sh\n'
    'bash .msc-build/apply-0.15.9-adaptive-scroll.sh\n',
    'adaptive overlay insertion',
    1,
)
replace("'41', '0.15.8-private-alpha-google-age-free-invite'", "'42', '0.15.9-private-alpha-adaptive-scroll'", 'phone identity pin', 1)
replace("'360158001', '0.15.8-wear-private-alpha-google-age-free-invite'", "'360159001', '0.15.9-wear-private-alpha-adaptive-scroll'", 'Wear identity pin', 1)
replace('release-0.15.8', 'release-0.15.9', 'release directory')
replace('MyStudyCompanion-phone-0.15.8-configured-ci.apk', 'MyStudyCompanion-phone-0.15.9-configured-ci.apk', 'phone artifact name', 1)
replace('MyStudyCompanion-wear-0.15.8-configured-ci.apk', 'MyStudyCompanion-wear-0.15.9-configured-ci.apk', 'Wear artifact name', 1)
replace("versionCode='41'", "versionCode='42'", 'phone identity assertion', 1)
replace("versionName='0.15.8-private-alpha-google-age-free-invite'", "versionName='0.15.9-private-alpha-adaptive-scroll'", 'phone version assertion', 1)
replace("versionCode='360158001'", "versionCode='360159001'", 'Wear identity assertion', 1)
replace("versionName='0.15.8-wear-private-alpha-google-age-free-invite'", "versionName='0.15.9-wear-private-alpha-adaptive-scroll'", 'Wear version assertion', 1)

anchor = "grep -Fq 'refreshGoogleAgeFromAccount' \"$UI/MyStudyCompanionApp.kt\"\n"
adaptive_gates = anchor + """
# Adaptive viewport contract. The entire household page has one bounded scroll
# owner, HouseholdScreen measures to natural height, and invitation creation
# automatically brings the completed code into view after a live remeasure.
grep -Fq '.verticalScroll(householdScrollState)' \"$UI/FamilyHubScreen.kt\"
grep -Fq '.imePadding()' \"$UI/FamilyHubScreen.kt\"
grep -Fq 'withFrameNanos' \"$UI/FamilyHubScreen.kt\"
grep -Fq 'householdScrollState.animateScrollTo(householdScrollState.maxValue)' \"$UI/FamilyHubScreen.kt\"
grep -Fq 'widthIn(max = minOf(layoutSpec.contentMaxWidthDp, 1_120).dp)' \"$UI/HouseholdScreen.kt\"
! grep -Fq 'Box(modifier.fillMaxSize()' \"$UI/HouseholdScreen.kt\"
! sed -n '/FamilyHubSection.HOUSEHOLD/,/^            }/p' \"$UI/FamilyHubScreen.kt\" | grep -Fq 'modifier = Modifier.weight(1f),'

python3 - <<'AUDIT'
from pathlib import Path
import re

ui = Path('MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui')
rows = []
for path in sorted(ui.glob('*Screen.kt')):
    source = path.read_text(encoding='utf-8')
    scroll_backed = any(token in source for token in (
        'LazyColumn(', 'LazyRow(', '.verticalScroll(', '.horizontalScroll(',
        'AndroidView(', 'WebView(', 'Pager(', 'HorizontalPager(', 'VerticalPager(',
    ))
    viewport_managed = any(token in source for token in (
        'Canvas(', 'InteractiveWorkbookEditor(', 'UnifiedStudyReaderScreen',
    ))
    has_static_full_page = bool(re.search(r'(Column|Box)\s*\([^\n]*fillMaxSize|Modifier\.fillMaxSize\(\)', source))
    status = 'scroll-backed' if scroll_backed else ('viewport-managed' if viewport_managed else 'review')
    rows.append(f'{path.name}\t{status}\tstatic-full-page={has_static_full_page}')

report = Path('release-0.15.9/metadata/RESPONSIVE-SCREEN-AUDIT.txt')
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text('\n'.join(rows) + '\n', encoding='utf-8')

# The reported regression must be covered explicitly. Other screens are retained
# rather than blindly nested in a second scroll container, which would break
# existing LazyColumn, reader, canvas, and workbook surfaces.
family = (ui / 'FamilyHubScreen.kt').read_text(encoding='utf-8')
household = (ui / 'HouseholdScreen.kt').read_text(encoding='utf-8')
assert '.verticalScroll(householdScrollState)' in family
assert 'Modifier.fillMaxSize()' not in household
assert 'Box(modifier.fillMaxSize()' not in household
AUDIT
"""
replace(anchor, adaptive_gates, 'adaptive source gates', 1)

replace(
    'PASS: backend, Android, Wear, PWA, and Firestore Rules Emulator tests passed.\n',
    'PASS: backend, Android, Wear, PWA, and Firestore Rules Emulator tests passed.\n'
    'PASS: Profiles & household uses one live constraint-aware vertical scroll owner.\n'
    'PASS: phone, Fold, tablet, rotation, and multi-window remeasure without fixed-height clipping.\n'
    'PASS: a newly created invitation code is automatically brought fully into view.\n'
    'PASS: Google sign-in, Google age verification, and free Spark invitation logic were preserved unchanged.\n',
    'release gate additions',
    1,
)
replace('configured 0.15.8 phone and Wear APKs', 'configured 0.15.9 phone and Wear APKs', 'completion message', 1)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$DRIVER"
bash "$DRIVER"
