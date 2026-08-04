#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

apply_path = Path('.msc-build/apply-0.15.8-google-age-firestore-compat.sh')
apply_text = apply_path.read_text(encoding='utf-8')
old_apply = '''# Use the current, non-deprecated Google email accessor for the authorization
# request. The People API lookup remains optional; an empty result now enters the
# explicit age-verification gate instead of becoming Preteen.
coordinator = app / 'auth/GoogleSignInCoordinator.kt'
replace_exact(
    coordinator,
    'val hints = runCatching { requestGoogleProfileHints(activity, credential.id) }\\n            .getOrDefault(GoogleProfileHints())',
    'val accountEmail = credential.email?.takeIf { it.isNotBlank() } ?: credential.id\\n        val hints = runCatching { requestGoogleProfileHints(activity, accountEmail) }\\n            .getOrDefault(GoogleProfileHints())',
    'Google account email lookup',
)
'''
new_apply = '''# This Credential Manager dependency exposes the selected Google account identifier
# through credential.id. Keep that proven API instead of referencing a newer email
# property that is unavailable in this build.
coordinator = app / 'auth/GoogleSignInCoordinator.kt'
replace_exact(
    coordinator,
    'val hints = runCatching { requestGoogleProfileHints(activity, credential.id) }\\n            .getOrDefault(GoogleProfileHints())',
    'val accountIdentifier = credential.id\\n        val hints = runCatching { requestGoogleProfileHints(activity, accountIdentifier) }\\n            .getOrDefault(GoogleProfileHints())',
    'Google account identifier lookup',
)
'''
if old_apply not in apply_text:
    raise SystemExit('0.15.8 credential compatibility target was not found')
apply_path.write_text(apply_text.replace(old_apply, new_apply, 1), encoding='utf-8')

build_path = Path('.msc-build/build-0.15.8-google-age-free-invite.sh')
build_text = build_path.read_text(encoding='utf-8')
old_gate = "grep -Fq 'credential.email?.takeIf' \"$AUTH/GoogleSignInCoordinator.kt\""
new_gate = "grep -Fq 'val accountIdentifier = credential.id' \"$AUTH/GoogleSignInCoordinator.kt\""
if old_gate not in build_text:
    raise SystemExit('0.15.8 credential source gate was not found')
build_text = build_text.replace(old_gate, new_gate, 1)

old_age_gate = "grep -Fq 'return AgeGroup.PRETEEN to ProfileAgeSource.UNCONFIRMED' \"$COMPANION/StudyGroupAssignmentResolver.kt\""
new_age_gate = """grep -Fq 'val storedWasSelfVerified' \"$COMPANION/StudyGroupAssignmentResolver.kt\"
grep -Fq 'return (stored?.ageGroup ?: AgeGroup.PRETEEN) to ProfileAgeSource.UNCONFIRMED' \"$COMPANION/StudyGroupAssignmentResolver.kt\"
! sed -n '/fun resolveAge(/,/private fun FamilyMemberProfile/p' \"$COMPANION/StudyGroupAssignmentResolver.kt\" | grep -Fq 'ProfileAgeSource.HOUSEHOLD_PROFILE'"""
if old_age_gate not in build_text:
    raise SystemExit('0.15.8 legacy age source gate was not found')
build_text = build_text.replace(old_age_gate, new_age_gate, 1)

old_dex = '''# Verify the finished APK contains the restored age-screen text, not just source.
unzip -p "$PHONE_APK" classes.dex > release-0.15.8/metadata/classes.dex
strings release-0.15.8/metadata/classes.dex > release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'ProfileAgeSetupScreen' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'Check Google age again' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'Google did not return a birthday or age range' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
rm release-0.15.8/metadata/classes.dex
'''
new_dex = '''# Verify the finished APK contains the restored age-screen text, not just source.
: > release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
while IFS= read -r dex_file; do
  unzip -p "$PHONE_APK" "$dex_file" | strings >> release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
done < <(unzip -Z1 "$PHONE_APK" | grep -E '^classes([0-9]+)?\\.dex$')
grep -Fq 'ProfileAgeSetupScreen' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'Check Google age again' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
grep -Fq 'Google did not return a birthday or age range' release-0.15.8/metadata/PHONE-DEX-STRINGS.txt
'''
if old_dex not in build_text:
    raise SystemExit('0.15.8 finished-APK DEX gate was not found')
build_path.write_text(build_text.replace(old_dex, new_dex, 1), encoding='utf-8')

print('Prepared 0.15.8 for the installed Google credential dependency, persisted-age migration, and multidex APK verification.')
PY
