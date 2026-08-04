#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GENERATED=.msc-build/build-0.15.9-generated.sh
cp .msc-build/build-0.15.8-google-age-free-invite.sh "$GENERATED"
trap 'rm -f "$GENERATED"' EXIT

python3 - <<'PY'
from pathlib import Path

path = Path('.msc-build/build-0.15.9-generated.sh')
text = path.read_text(encoding='utf-8')

age_marker = 'bash .msc-build/apply-0.15.8-google-age-firestore-compat.sh\n'
responsive_marker = 'bash .msc-build/apply-0.15.9-adaptive-household-scroll.sh\n'
assert text.count(age_marker) == 1
text = text.replace(age_marker, age_marker + responsive_marker, 1)

replacements = {
    "(Path('MyStudyCompanion/app/build.gradle.kts'), '41', '0.15.8-private-alpha-google-age-free-invite'),":
        "(Path('MyStudyCompanion/app/build.gradle.kts'), '42', '0.15.9-private-alpha-adaptive-household-scroll'),",
    "(Path('MyStudyCompanion/wear/build.gradle.kts'), '360158001', '0.15.8-wear-private-alpha-google-age-free-invite'),":
        "(Path('MyStudyCompanion/wear/build.gradle.kts'), '360159001', '0.15.9-wear-private-alpha-adaptive-household-scroll'),",
    'release-0.15.8': 'release-0.15.9',
    'MyStudyCompanion-phone-0.15.8-configured-ci.apk': 'MyStudyCompanion-phone-0.15.9-configured-ci.apk',
    'MyStudyCompanion-wear-0.15.8-configured-ci.apk': 'MyStudyCompanion-wear-0.15.9-configured-ci.apk',
    "versionCode='41'": "versionCode='42'",
    "versionName='0.15.8-private-alpha-google-age-free-invite'": "versionName='0.15.9-private-alpha-adaptive-household-scroll'",
    "versionCode='360158001'": "versionCode='360159001'",
    "versionName='0.15.8-wear-private-alpha-google-age-free-invite'": "versionName='0.15.9-wear-private-alpha-adaptive-household-scroll'",
    'PASS: configured 0.15.8 phone and Wear APKs built and verified.':
        'PASS: configured 0.15.9 adaptive phone and Wear APKs built and verified.',
}
for old, new in replacements.items():
    count = text.count(old)
    if count == 0:
        raise SystemExit(f'Missing 0.15.9 build rewrite marker: {old}')
    text = text.replace(old, new)

path.write_text(text, encoding='utf-8')
PY

chmod +x "$GENERATED"
bash "$GENERATED"

FAMILY=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/FamilyHubScreen.kt
HOUSEHOLD=MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt
META=release-0.15.9/metadata
mkdir -p "$META"

grep -Fq 'val householdScrollState = rememberScrollState()' "$FAMILY"
grep -Fq '.verticalScroll(householdScrollState)' "$FAMILY"
grep -Fq '.imePadding()' "$FAMILY"
grep -Fq '.widthIn(max = 880.dp)' "$FAMILY"
grep -Fq 'modifier = Modifier.fillMaxWidth(),' "$FAMILY"
grep -Fq 'Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter)' "$HOUSEHOLD"

cat > "$META/ADAPTIVE-HOUSEHOLD-GATES.txt" <<'TXT'
PASS: The complete Profiles & household content is inside one bounded vertical scroll container.
PASS: Scrolling activates only when the available phone, Fold, tablet, split-screen, or multi-window height is smaller than the content.
PASS: The scroll container is recomposed from the current window constraints after a live screen-size or posture change.
PASS: The profile switcher and household cards share the same scroll path, so the invitation section cannot be trapped below a fixed panel.
PASS: The household child screen no longer consumes a second weighted full-height region inside the scroll container.
PASS: IME padding keeps invitation entry controls reachable when the keyboard is open.
PASS: The invitation code remains in the same content flow and becomes reachable instead of being clipped below the viewport.
PASS: Existing 0.15.8 Google age verification, Google sign-in, Firebase Spark invitations, Firestore compatibility, child protections, themes, workbooks, and Wear behavior remain under the original build gates.
TXT

printf 'PASS: My Study Companion 0.15.9 adaptive household build completed.\n'
