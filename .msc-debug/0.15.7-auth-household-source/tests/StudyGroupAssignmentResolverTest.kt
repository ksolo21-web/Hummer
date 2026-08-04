package com.mystudycompanion.app.companion

import com.mystudycompanion.app.auth.AccountAgeGroup
import com.mystudycompanion.app.auth.AccountAgeSource
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.HouseholdRole
import com.mystudycompanion.app.auth.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class StudyGroupAssignmentResolverTest {
    @Test
    fun googleIdentityLinksToExistingHouseholdProfileAndPreservesTeenGroup() {
        val stored = CompanionProfile(
            uid = "local-aniyah",
            displayName = "A'niyah Franklin",
            ageGroup = AgeGroup.TEEN,
            ageSource = ProfileAgeSource.USER_CONFIRMED,
        )
        val account = googleAccount(
            uid = "firebase-aniyah",
            displayName = "A'niyah Franklin",
            ageGroup = AccountAgeGroup.UNKNOWN,
        )

        val match = StudyGroupAssignmentResolver.matchStoredIdentity(
            account = account,
            localProfiles = listOf(stored),
            householdMembers = emptyList(),
        )

        assertNotNull(match)
        assertEquals("local-aniyah", match?.sourceUid)
        assertEquals(
            AgeGroup.TEEN to ProfileAgeSource.USER_CONFIRMED,
            StudyGroupAssignmentResolver.resolveAge(account, match?.profile),
        )
    }

    @Test
    fun householdAgeAssignmentWinsOverMissingGoogleAge() {
        val member = FamilyMemberProfile(
            uid = "household-preteen",
            displayName = "Aniyah",
            ageGroup = AgeGroup.PRETEEN,
            ageSource = ProfileAgeSource.HOUSEHOLD_PROFILE,
            role = FamilyBoardRole.MEMBER,
        )
        val account = googleAccount(
            uid = "firebase-preteen",
            displayName = "Aniyah Franklin",
            ageGroup = AccountAgeGroup.MINOR_UNKNOWN,
        )

        val match = StudyGroupAssignmentResolver.matchStoredIdentity(
            account = account,
            localProfiles = emptyList(),
            householdMembers = listOf(member),
        )

        assertEquals(
            AgeGroup.PRETEEN to ProfileAgeSource.HOUSEHOLD_PROFILE,
            StudyGroupAssignmentResolver.resolveAge(account, match?.profile),
        )
    }

    @Test
    fun signedInProfileNeverRequiresChildToChooseAStudyGroup() {
        val profile = CompanionProfile(
            uid = "new-google-user",
            displayName = "Family Member",
            ageGroup = AgeGroup.PRETEEN,
            ageSource = ProfileAgeSource.UNCONFIRMED,
        )
        assertFalse(profile.needsAgeConfirmation)
    }

    private fun googleAccount(
        uid: String,
        displayName: String,
        ageGroup: AccountAgeGroup,
    ) = UserAccount(
        uid = uid,
        displayName = displayName,
        email = "child@example.com",
        photoUrl = null,
        provider = AccountProvider.GOOGLE,
        householdId = "household",
        householdRole = HouseholdRole.MEMBER,
        ageGroup = ageGroup,
        ageSource = if (ageGroup == AccountAgeGroup.MINOR_UNKNOWN) {
            AccountAgeSource.GOOGLE_AGE_RANGE
        } else {
            AccountAgeSource.UNAVAILABLE
        },
    )
}
