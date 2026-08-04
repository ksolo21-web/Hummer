package com.mystudycompanion.app.auth

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthModelsTest {
    @Test
    fun initialsAreStableAndLimited() {
        val account = UserAccount(
            uid = "uid",
            displayName = "Kaleb Example Person",
            email = null,
            photoUrl = null,
            provider = AccountProvider.PRIVATE_OWNER,
            householdId = "family",
            householdRole = HouseholdRole.OWNER,
        )
        assertEquals("KE", account.initials)
    }

    @Test
    fun googleBirthdayMapsToYouthStudyLevel() {
        val today = LocalDate.now()
        assertEquals(
            AccountAgeGroup.CHILD,
            GoogleProfileHints(birthDateIso = today.minusYears(9).toString()).ageGroup,
        )
        assertEquals(
            AccountAgeGroup.PRETEEN,
            GoogleProfileHints(birthDateIso = today.minusYears(11).toString()).ageGroup,
        )
        assertEquals(
            AccountAgeGroup.TEEN,
            GoogleProfileHints(birthDateIso = today.minusYears(15).toString()).ageGroup,
        )
        assertEquals(
            AccountAgeGroup.ADULT,
            GoogleProfileHints(birthDateIso = today.minusYears(18).toString()).ageGroup,
        )
    }

    @Test
    fun coarseGoogleMinorRangeNeverDefaultsToAdult() {
        val hints = GoogleProfileHints(googleAgeRange = "LESS_THAN_EIGHTEEN")
        assertEquals(AccountAgeGroup.MINOR_UNKNOWN, hints.ageGroup)
        assertEquals(AccountAgeSource.GOOGLE_AGE_RANGE, hints.ageSource)
    }
}
