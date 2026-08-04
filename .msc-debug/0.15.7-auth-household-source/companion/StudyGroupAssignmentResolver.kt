package com.mystudycompanion.app.companion

import com.mystudycompanion.app.auth.AccountAgeGroup
import com.mystudycompanion.app.auth.AccountAgeSource
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.HouseholdRole
import com.mystudycompanion.app.auth.UserAccount
import java.util.Locale

internal data class StoredStudyIdentity(
    val profile: CompanionProfile,
    val sourceUid: String,
)

/**
 * Resolves the signed-in account to the study group already assigned by the
 * household. The child is never asked to choose their own age band.
 */
internal object StudyGroupAssignmentResolver {
    fun matchStoredIdentity(
        account: UserAccount,
        localProfiles: List<CompanionProfile>,
        householdMembers: List<FamilyMemberProfile>,
    ): StoredStudyIdentity? {
        localProfiles.firstOrNull { it.uid == account.uid }?.let {
            return StoredStudyIdentity(it, it.uid)
        }
        householdMembers.firstOrNull { it.uid == account.uid }?.let {
            return StoredStudyIdentity(it.toCompanionProfile(), it.uid)
        }

        val targetName = normalizeName(account.displayName)
        if (targetName.isBlank()) return null

        uniqueMatch(localProfiles, targetName) { normalizeName(it.displayName) }?.let {
            return StoredStudyIdentity(it, it.uid)
        }
        uniqueMatch(householdMembers, targetName) { normalizeName(it.displayName) }?.let {
            return StoredStudyIdentity(it.toCompanionProfile(), it.uid)
        }

        val targetFirstName = targetName.substringBefore(' ')
        if (targetFirstName.length < 2) return null
        uniqueMatch(localProfiles, targetFirstName) {
            normalizeName(it.displayName).substringBefore(' ')
        }?.let { return StoredStudyIdentity(it, it.uid) }
        uniqueMatch(householdMembers, targetFirstName) {
            normalizeName(it.displayName).substringBefore(' ')
        }?.let { return StoredStudyIdentity(it.toCompanionProfile(), it.uid) }
        return null
    }

    fun resolveAge(
        account: UserAccount,
        stored: CompanionProfile?,
    ): Pair<AgeGroup, ProfileAgeSource> {
        if (stored != null && stored.ageSource != ProfileAgeSource.UNCONFIRMED) {
            return stored.ageGroup to stored.ageSource
        }
        if (account.provider == AccountProvider.PRIVATE_OWNER) {
            return AgeGroup.ADULT to ProfileAgeSource.PRIVATE_OWNER
        }

        val ageGroup = when (account.ageGroup) {
            AccountAgeGroup.CHILD -> AgeGroup.CHILD
            AccountAgeGroup.PRETEEN -> AgeGroup.PRETEEN
            AccountAgeGroup.TEEN -> AgeGroup.TEEN
            AccountAgeGroup.ADULT -> AgeGroup.ADULT
            AccountAgeGroup.MINOR_UNKNOWN -> stored?.ageGroup?.takeIf { it != AgeGroup.ADULT } ?: AgeGroup.PRETEEN
            AccountAgeGroup.UNKNOWN -> when {
                stored != null -> stored.ageGroup
                account.householdRole == HouseholdRole.OWNER || account.householdRole == HouseholdRole.ORGANIZER -> AgeGroup.ADULT
                else -> AgeGroup.PRETEEN
            }
        }
        val source = when (account.ageSource) {
            AccountAgeSource.GOOGLE_BIRTHDAY -> ProfileAgeSource.GOOGLE_BIRTHDAY
            AccountAgeSource.GOOGLE_AGE_RANGE -> ProfileAgeSource.GOOGLE_AGE_RANGE
            AccountAgeSource.PRIVATE_OWNER -> ProfileAgeSource.PRIVATE_OWNER
            AccountAgeSource.USER_CONFIRMED -> ProfileAgeSource.USER_CONFIRMED
            AccountAgeSource.UNAVAILABLE -> stored?.ageSource ?: ProfileAgeSource.UNCONFIRMED
        }
        return ageGroup to source
    }

    private fun FamilyMemberProfile.toCompanionProfile(): CompanionProfile = CompanionProfile(
        uid = uid,
        displayName = displayName,
        ageGroup = ageGroup,
        ageSource = ageSource.takeUnless { it == ProfileAgeSource.UNCONFIRMED }
            ?: ProfileAgeSource.HOUSEHOLD_PROFILE,
        role = role,
        googleConnected = googleConnected,
    )

    private fun normalizeName(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[’']"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private inline fun <T> uniqueMatch(
        values: List<T>,
        target: String,
        crossinline selector: (T) -> String,
    ): T? = values.filter { selector(it) == target }.singleOrNull()
}
