package com.mystudycompanion.app.auth

import java.time.LocalDate
import java.time.Period

enum class AccountProvider {
    PRIVATE_OWNER,
    GOOGLE,
}

enum class HouseholdRole {
    OWNER,
    ORGANIZER,
    MEMBER,
    NONE,
}

enum class AccountAgeGroup {
    CHILD,
    PRETEEN,
    TEEN,
    ADULT,
    MINOR_UNKNOWN,
    UNKNOWN,
}

enum class AccountAgeSource {
    GOOGLE_BIRTHDAY,
    GOOGLE_AGE_RANGE,
    USER_CONFIRMED,
    PRIVATE_OWNER,
    UNAVAILABLE,
}

data class GoogleProfileHints(
    val birthDateIso: String? = null,
    val googleAgeRange: String? = null,
) {
    val ageGroup: AccountAgeGroup
        get() {
            val rawBirthDate: String = birthDateIso?.trim().orEmpty()
            val birthDate: LocalDate? = if (rawBirthDate.isBlank()) {
                null
            } else {
                try {
                    LocalDate.parse(rawBirthDate)
                } catch (_: java.time.format.DateTimeParseException) {
                    null
                }
            }
            if (birthDate != null) {
                val age = Period.between(birthDate, LocalDate.now()).years
                return when {
                    age < 0 -> AccountAgeGroup.UNKNOWN
                    age < 10 -> AccountAgeGroup.CHILD
                    age < 13 -> AccountAgeGroup.PRETEEN
                    age < 18 -> AccountAgeGroup.TEEN
                    else -> AccountAgeGroup.ADULT
                }
            }
            return when (googleAgeRange?.uppercase()) {
                "LESS_THAN_EIGHTEEN" -> AccountAgeGroup.MINOR_UNKNOWN
                "EIGHTEEN_TO_TWENTY", "TWENTY_ONE_OR_OLDER" -> AccountAgeGroup.ADULT
                else -> AccountAgeGroup.UNKNOWN
            }
        }

    val ageSource: AccountAgeSource
        get() = when {
            birthDateIso != null -> AccountAgeSource.GOOGLE_BIRTHDAY
            googleAgeRange != null -> AccountAgeSource.GOOGLE_AGE_RANGE
            else -> AccountAgeSource.UNAVAILABLE
        }
}

data class GoogleSignInPayload(
    val idToken: String,
    val profileHints: GoogleProfileHints = GoogleProfileHints(),
)

data class UserAccount(
    val uid: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
    val provider: AccountProvider,
    val householdId: String?,
    val householdRole: HouseholdRole,
    val ageGroup: AccountAgeGroup = AccountAgeGroup.UNKNOWN,
    val ageSource: AccountAgeSource = AccountAgeSource.UNAVAILABLE,
) {
    val initials: String
        get() = displayName
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "MSC" }

    val greetingName: String
        get() = displayName.trim().substringBefore(' ').ifBlank { "Friend" }
}

sealed interface AuthState {
    data object Initializing : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val account: UserAccount) : AuthState
    data class Failure(val message: String) : AuthState
}

data class AuthCapabilities(
    val firebaseConfigured: Boolean,
    val googleWebClientConfigured: Boolean,
    val localOwnerModeAllowed: Boolean,
)
