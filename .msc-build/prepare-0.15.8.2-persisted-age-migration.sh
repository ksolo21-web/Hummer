#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

path = Path('.msc-build/apply-0.15.8-google-age-firestore-compat.sh')
text = path.read_text(encoding='utf-8')

old_resolver = '''        if (account.ageGroup == AccountAgeGroup.MINOR_UNKNOWN) {
            if (
                stored != null &&
                stored.ageSource != ProfileAgeSource.UNCONFIRMED &&
                stored.ageGroup != AgeGroup.ADULT
            ) {
                return stored.ageGroup to stored.ageSource
            }
            return AgeGroup.PRETEEN to ProfileAgeSource.UNCONFIRMED
        }

        if (stored != null && stored.ageSource != ProfileAgeSource.UNCONFIRMED) {
            return stored.ageGroup to stored.ageSource
        }

        // AgeGroup has no UNKNOWN enum. PRETEEN is only an internal placeholder;
        // UNCONFIRMED forces ProfileAgeSetupScreen before any app content appears.
        return AgeGroup.PRETEEN to ProfileAgeSource.UNCONFIRMED
'''
new_resolver = '''        val storedWasSelfVerified = stored?.ageSource in setOf(
            ProfileAgeSource.USER_CONFIRMED,
            ProfileAgeSource.GOOGLE_BIRTHDAY,
            ProfileAgeSource.GOOGLE_AGE_RANGE,
        )

        if (account.ageGroup == AccountAgeGroup.MINOR_UNKNOWN) {
            if (stored != null && storedWasSelfVerified && stored.ageGroup != AgeGroup.ADULT) {
                return stored.ageGroup to stored.ageSource
            }
            // A household-synchronized value may have been produced by the old
            // Unknown -> Preteen regression. Keep its restricted visual level only
            // as a placeholder and require verification before entering the app.
            return (stored?.ageGroup?.takeIf { it != AgeGroup.ADULT } ?: AgeGroup.PRETEEN) to
                ProfileAgeSource.UNCONFIRMED
        }

        if (stored != null && storedWasSelfVerified) {
            return stored.ageGroup to stored.ageSource
        }

        // Household-profile and legacy unresolved ages are not proof. Retain the
        // previous level only as a placeholder, but force Google/user verification.
        return (stored?.ageGroup ?: AgeGroup.PRETEEN) to ProfileAgeSource.UNCONFIRMED
'''
if old_resolver not in text:
    raise SystemExit('Persisted-age resolver target not found')
text = text.replace(old_resolver, new_resolver, 1)

old_test = '''    @Test
    fun householdAssignmentIsPreservedWhenGoogleSharesNoAge() {
        val stored = CompanionProfile(
            uid = "teen",
            displayName = "Teen Account",
            ageGroup = AgeGroup.TEEN,
            ageSource = ProfileAgeSource.HOUSEHOLD_PROFILE,
        )
        val account = googleAccount(
            uid = "teen",
            displayName = "Teen Account",
            ageGroup = AccountAgeGroup.UNKNOWN,
            ageSource = AccountAgeSource.UNAVAILABLE,
        )

        assertEquals(
            AgeGroup.TEEN to ProfileAgeSource.HOUSEHOLD_PROFILE,
            StudyGroupAssignmentResolver.resolveAge(account, stored),
        )
    }
'''
new_test = '''    @Test
    fun legacyHouseholdAgeMustBeReverifiedWhenGoogleSharesNoAge() {
        val stored = CompanionProfile(
            uid = "adult-owner",
            displayName = "Kaleb Franklin",
            ageGroup = AgeGroup.PRETEEN,
            ageSource = ProfileAgeSource.HOUSEHOLD_PROFILE,
        )
        val account = googleAccount(
            uid = "adult-owner",
            displayName = "Kaleb Franklin",
            ageGroup = AccountAgeGroup.UNKNOWN,
            ageSource = AccountAgeSource.UNAVAILABLE,
            role = HouseholdRole.OWNER,
        )

        val resolved = StudyGroupAssignmentResolver.resolveAge(account, stored)
        assertEquals(AgeGroup.PRETEEN, resolved.first)
        assertEquals(ProfileAgeSource.UNCONFIRMED, resolved.second)
        assertTrue(
            stored.copy(ageSource = resolved.second).needsAgeConfirmation,
        )
    }
'''
if old_test not in text:
    raise SystemExit('Persisted-age regression test target not found')
text = text.replace(old_test, new_test, 1)

old_gate = '''assert 'householdRole == HouseholdRole.OWNER' not in resolver_text[resolver_text.index('fun resolveAge'):]
family_text = family.read_text(encoding='utf-8')
'''
new_gate = '''assert 'householdRole == HouseholdRole.OWNER' not in resolver_text[resolver_text.index('fun resolveAge'):]
assert 'storedWasSelfVerified' in resolver_text
assert 'ProfileAgeSource.HOUSEHOLD_PROFILE' not in resolver_text[resolver_text.index('fun resolveAge'):resolver_text.index('private fun FamilyMemberProfile')]
family_text = family.read_text(encoding='utf-8')
'''
if old_gate not in text:
    raise SystemExit('Persisted-age static gate target not found')
text = text.replace(old_gate, new_gate, 1)

path.write_text(text, encoding='utf-8')
print('Prepared migration that rejects legacy household-derived age guesses until verified.')
PY
