#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import re

root = Path('MyStudyCompanion')
app = root / 'app/src/main/java/com/mystudycompanion/app'
tests = root / 'app/src/test/java/com/mystudycompanion/app'


def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match in {path}, found {count}')
    path.write_text(text.replace(old, new), encoding='utf-8')

# Restore the age-verification screen that was present in the last working 0.15.1 flow.
profile_screen = app / 'ui/ProfileAgeSetupScreen.kt'
profile_screen.write_text(r'''package com.mystudycompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mystudycompanion.app.companion.AgeGroup

@Composable
fun ProfileAgeSetupScreen(
    displayName: String,
    minorOnly: Boolean,
    checkingGoogle: Boolean,
    googleLookupError: String?,
    onRetryGoogle: () -> Unit,
    onSelected: (AgeGroup) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(30.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Set up $displayName’s study level",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (minorOnly) {
                                "Google confirmed this is a younger account, but did not provide the exact birthday. Choose the correct level so the app never treats this user as an adult."
                            } else {
                                "The app checked Google, but Google did not provide enough age information. Try Google again or choose the correct level once so the app never guesses."
                            },
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        )
                        OutlinedButton(
                            onClick = onRetryGoogle,
                            enabled = !checkingGoogle,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (checkingGoogle) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("  Checking Google…")
                            } else {
                                Text("Check Google age again")
                            }
                        }
                        googleLookupError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item { AgeChoice("Child", "Under 10", AgeGroup.CHILD, onSelected) }
            item { AgeChoice("Preteen", "Ages 10–12", AgeGroup.PRETEEN, onSelected) }
            item { AgeChoice("Teen", "Ages 13–17", AgeGroup.TEEN, onSelected) }
            if (!minorOnly) {
                item { AgeChoice("Adult", "Age 18 or older", AgeGroup.ADULT, onSelected) }
            }
        }
    }
}

@Composable
private fun AgeChoice(
    title: String,
    subtitle: String,
    ageGroup: AgeGroup,
    onSelected: (AgeGroup) -> Unit,
) {
    Button(
        onClick = { onSelected(ageGroup) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
''', encoding='utf-8')

# An unresolved age must remain unresolved. It may use a temporary internal
# layout placeholder, but the app must gate all signed-in content until verified.
models = app / 'companion/CompanionModels.kt'
text = models.read_text(encoding='utf-8')
text, count = re.subn(
    r'val needsAgeConfirmation: Boolean\s*\n\s*get\(\)\s*=\s*[^\n]+',
    'val needsAgeConfirmation: Boolean\n        get() = ageSource == ProfileAgeSource.UNCONFIRMED',
    text,
    count=1,
)
if count != 1:
    raise SystemExit('Could not restore CompanionProfile.needsAgeConfirmation')
models.write_text(text, encoding='utf-8')

# Fresh Google birthday/age-range information wins. A missing Google value never
# guesses from household role and never silently assigns Preteen or Adult.
resolver = app / 'companion/StudyGroupAssignmentResolver.kt'
text = resolver.read_text(encoding='utf-8')
start = text.index('    fun resolveAge(')
end = text.index('    private fun FamilyMemberProfile.toCompanionProfile', start)
new_resolver = r'''    fun resolveAge(
        account: UserAccount,
        stored: CompanionProfile?,
    ): Pair<AgeGroup, ProfileAgeSource> {
        if (account.provider == AccountProvider.PRIVATE_OWNER) {
            return AgeGroup.ADULT to ProfileAgeSource.PRIVATE_OWNER
        }

        val googleSource = when (account.ageSource) {
            AccountAgeSource.GOOGLE_BIRTHDAY -> ProfileAgeSource.GOOGLE_BIRTHDAY
            AccountAgeSource.GOOGLE_AGE_RANGE -> ProfileAgeSource.GOOGLE_AGE_RANGE
            AccountAgeSource.PRIVATE_OWNER -> ProfileAgeSource.PRIVATE_OWNER
            AccountAgeSource.USER_CONFIRMED -> ProfileAgeSource.USER_CONFIRMED
            AccountAgeSource.UNAVAILABLE -> ProfileAgeSource.UNCONFIRMED
        }
        val concreteGoogleAge = when (account.ageGroup) {
            AccountAgeGroup.CHILD -> AgeGroup.CHILD
            AccountAgeGroup.PRETEEN -> AgeGroup.PRETEEN
            AccountAgeGroup.TEEN -> AgeGroup.TEEN
            AccountAgeGroup.ADULT -> AgeGroup.ADULT
            AccountAgeGroup.MINOR_UNKNOWN, AccountAgeGroup.UNKNOWN -> null
        }
        if (concreteGoogleAge != null && googleSource != ProfileAgeSource.UNCONFIRMED) {
            return concreteGoogleAge to googleSource
        }

        if (account.ageGroup == AccountAgeGroup.MINOR_UNKNOWN) {
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
    }

'''
resolver.write_text(text[:start] + new_resolver + text[end:], encoding='utf-8')

# Use the current, non-deprecated Google email accessor for the authorization
# request. The People API lookup remains optional; an empty result now enters the
# explicit age-verification gate instead of becoming Preteen.
coordinator = app / 'auth/GoogleSignInCoordinator.kt'
replace_exact(
    coordinator,
    'val hints = runCatching { requestGoogleProfileHints(activity, credential.id) }\n            .getOrDefault(GoogleProfileHints())',
    'val accountEmail = credential.email?.takeIf { it.isNotBlank() } ?: credential.id\n        val hints = runCatching { requestGoogleProfileHints(activity, accountEmail) }\n            .getOrDefault(GoogleProfileHints())',
    'Google account email lookup',
)

# Restore the signed-in age gate and automatically re-check Google once when a
# persisted Firebase session has no cached age hints.
app_file = app / 'ui/MyStudyCompanionApp.kt'
text = app_file.read_text(encoding='utf-8')
if 'import android.app.Activity' not in text:
    text = text.replace(
        'package com.mystudycompanion.app.ui\n\n',
        'package com.mystudycompanion.app.ui\n\nimport android.app.Activity\nimport android.content.Context\nimport android.content.ContextWrapper\n',
        1,
    )
if 'import androidx.compose.ui.platform.LocalContext' not in text:
    text = text.replace(
        'import androidx.compose.ui.preferredFrameRate\n',
        'import androidx.compose.ui.preferredFrameRate\nimport androidx.compose.ui.platform.LocalContext\n',
        1,
    )
if 'import com.mystudycompanion.app.BuildConfig' not in text:
    text = text.replace(
        'import com.mystudycompanion.app.ai.AiStudyRepository\n',
        'import com.mystudycompanion.app.BuildConfig\nimport com.mystudycompanion.app.ai.AiStudyRepository\n',
        1,
    )
if 'import com.mystudycompanion.app.auth.AccountAgeGroup' not in text:
    text = text.replace(
        'import com.mystudycompanion.app.auth.AuthRepository\n',
        'import com.mystudycompanion.app.auth.AccountAgeGroup\nimport com.mystudycompanion.app.auth.AccountProvider\nimport com.mystudycompanion.app.auth.AuthRepository\n',
        1,
    )

old_state = '''    val current = navigator.current
    val scope = rememberCoroutineScope()
    val companionState by companionHubRepository.state.collectAsStateWithLifecycle()

    LaunchedEffect(account.uid, account.displayName, account.ageGroup, account.ageSource) {
        companionHubRepository.bindAccount(account)
    }

    if (companionState.profile.uid != account.uid) {
        AuthLoadingScreen()
        return
    }
    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }
'''
new_state = '''    val current = navigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val companionState by companionHubRepository.state.collectAsStateWithLifecycle()
    var ageLookupAttempted by rememberSaveable(account.uid) { mutableStateOf(false) }
    var ageLookupWorking by rememberSaveable(account.uid) { mutableStateOf(false) }
    var ageLookupError by rememberSaveable(account.uid) { mutableStateOf<String?>(null) }

    suspend fun refreshGoogleAgeFromAccount() {
        if (account.provider != AccountProvider.GOOGLE) return
        val activity = context.findActivityForAgeLookup()
        if (activity == null) {
            ageLookupError = "The Google age-check window could not be opened."
            return
        }
        ageLookupWorking = true
        ageLookupError = null
        runCatching {
            val payload = googleSignInCoordinator.requestGoogleSignIn(
                activity = activity,
                serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            )
            authRepository.signInWithGoogle(payload)
            if (payload.profileHints.ageGroup == AccountAgeGroup.UNKNOWN) {
                ageLookupError = "Google did not return a birthday or age range for this account. Choose the correct level below; the app will save it and will not guess."
            }
        }.onFailure {
            ageLookupError = it.message ?: "Google age information could not be checked."
        }
        ageLookupWorking = false
    }

    LaunchedEffect(account.uid, account.displayName, account.ageGroup, account.ageSource) {
        companionHubRepository.bindAccount(account)
    }

    if (companionState.profile.uid != account.uid) {
        AuthLoadingScreen()
        return
    }

    LaunchedEffect(account.uid, companionState.profile.needsAgeConfirmation) {
        if (
            companionState.profile.needsAgeConfirmation &&
            account.provider == AccountProvider.GOOGLE &&
            !ageLookupAttempted
        ) {
            ageLookupAttempted = true
            refreshGoogleAgeFromAccount()
        }
    }

    if (companionState.profile.needsAgeConfirmation) {
        ProfileAgeSetupScreen(
            displayName = account.greetingName,
            minorOnly = account.ageGroup == AccountAgeGroup.MINOR_UNKNOWN,
            checkingGoogle = ageLookupWorking,
            googleLookupError = ageLookupError,
            onRetryGoogle = {
                ageLookupAttempted = true
                scope.launch { refreshGoogleAgeFromAccount() }
            },
            onSelected = companionHubRepository::confirmProfileAge,
        )
        return
    }

    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }
'''
if old_state not in text:
    raise SystemExit('Could not restore signed-in age verification gate')
text = text.replace(old_state, new_state, 1)
if 'findActivityForAgeLookup' not in text.splitlines()[-20:]:
    text += '''\n\nprivate tailrec fun Context.findActivityForAgeLookup(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityForAgeLookup()
    else -> null
}\n'''
app_file.write_text(text, encoding='utf-8')

# Keep the app compatible with the already-live free Spark rules. The rules do
# not permit an ageSource field in household member documents. Age provenance
# remains local; the shared ageGroup remains synchronized.
family = app / 'family/FamilyWorshipOrganizerRepository.kt'
text = family.read_text(encoding='utf-8')
replacements = [
    ('            profile.ageSource.name,\n', ''),
    ('                    FIELD_AGE_SOURCE to profile.ageSource.name,\n', ''),
    ('                    it.ageSource.name,\n', ''),
    ('        put(FIELD_AGE_SOURCE, profile.ageSource.name)\n', ''),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit(f'Missing Firestore ageSource compatibility target: {old!r}')
    text = text.replace(old, new, 1)

# Never attempt to upload an unresolved placeholder profile.
old_sync = '''    private suspend fun syncSignedInMember(
        householdRef: com.google.firebase.firestore.DocumentReference,
        profile: CompanionProfile,
    ) {
        val fingerprint = listOf(
'''
new_sync = '''    private suspend fun syncSignedInMember(
        householdRef: com.google.firebase.firestore.DocumentReference,
        profile: CompanionProfile,
    ) {
        if (profile.needsAgeConfirmation) return
        val fingerprint = listOf(
'''
if old_sync not in text:
    raise SystemExit('Could not add unresolved-profile Firestore guard')
text = text.replace(old_sync, new_sync, 1)

# This repository is bound only for Google accounts. Household role is not age
# evidence, so the emergency profile must remain unconfirmed.
old_fallback = '''                val role = mutableState.value.householdRole
                val organizer = role == HouseholdRole.OWNER || role == HouseholdRole.ORGANIZER
                CompanionProfile(
                    uid = boundUid,
                    displayName = boundDisplayName.ifBlank { "Family Member" },
                    ageGroup = if (organizer) AgeGroup.ADULT else AgeGroup.PRETEEN,
                    ageSource = if (organizer) ProfileAgeSource.PRIVATE_OWNER else ProfileAgeSource.UNCONFIRMED,
                    role = role.toBoardRole(),
                    googleConnected = true,
                )
'''
new_fallback = '''                val role = mutableState.value.householdRole
                CompanionProfile(
                    uid = boundUid,
                    displayName = boundDisplayName.ifBlank { "Family Member" },
                    ageGroup = AgeGroup.PRETEEN,
                    ageSource = ProfileAgeSource.UNCONFIRMED,
                    role = role.toBoardRole(),
                    googleConnected = true,
                )
'''
if old_fallback not in text:
    raise SystemExit('Could not remove household-role age guess')
text = text.replace(old_fallback, new_fallback, 1)
family.write_text(text, encoding='utf-8')

# Replace the regressed resolver tests with release-blocking safety tests.
resolver_test = tests / 'companion/StudyGroupAssignmentResolverTest.kt'
resolver_test.write_text(r'''package com.mystudycompanion.app.companion

import com.mystudycompanion.app.auth.AccountAgeGroup
import com.mystudycompanion.app.auth.AccountAgeSource
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.HouseholdRole
import com.mystudycompanion.app.auth.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGroupAssignmentResolverTest {
    @Test
    fun googleIdentityLinksToExistingHouseholdProfile() {
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
            ageSource = AccountAgeSource.UNAVAILABLE,
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
    fun freshGoogleAdultOverridesStaleUnconfirmedPreteen() {
        val stored = CompanionProfile(
            uid = "adult",
            displayName = "Kaleb Franklin",
            ageGroup = AgeGroup.PRETEEN,
            ageSource = ProfileAgeSource.UNCONFIRMED,
        )
        val account = googleAccount(
            uid = "adult",
            displayName = "Kaleb Franklin",
            ageGroup = AccountAgeGroup.ADULT,
            ageSource = AccountAgeSource.GOOGLE_BIRTHDAY,
        )

        assertEquals(
            AgeGroup.ADULT to ProfileAgeSource.GOOGLE_BIRTHDAY,
            StudyGroupAssignmentResolver.resolveAge(account, stored),
        )
    }

    @Test
    fun unknownGoogleAgeNeverGuessesFromOrganizerRole() {
        val account = googleAccount(
            uid = "owner",
            displayName = "Kaleb Franklin",
            ageGroup = AccountAgeGroup.UNKNOWN,
            ageSource = AccountAgeSource.UNAVAILABLE,
            role = HouseholdRole.OWNER,
        )

        val resolved = StudyGroupAssignmentResolver.resolveAge(account, null)
        assertEquals(ProfileAgeSource.UNCONFIRMED, resolved.second)
        assertTrue(
            CompanionProfile(
                uid = account.uid,
                displayName = account.displayName,
                ageGroup = resolved.first,
                ageSource = resolved.second,
            ).needsAgeConfirmation,
        )
    }

    @Test
    fun minorUnknownCannotReuseAnAdultStoredProfile() {
        val stored = CompanionProfile(
            uid = "minor",
            displayName = "Minor Account",
            ageGroup = AgeGroup.ADULT,
            ageSource = ProfileAgeSource.USER_CONFIRMED,
        )
        val account = googleAccount(
            uid = "minor",
            displayName = "Minor Account",
            ageGroup = AccountAgeGroup.MINOR_UNKNOWN,
            ageSource = AccountAgeSource.GOOGLE_AGE_RANGE,
        )

        val resolved = StudyGroupAssignmentResolver.resolveAge(account, stored)
        assertEquals(AgeGroup.PRETEEN, resolved.first)
        assertEquals(ProfileAgeSource.UNCONFIRMED, resolved.second)
    }

    @Test
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

    private fun googleAccount(
        uid: String,
        displayName: String,
        ageGroup: AccountAgeGroup,
        ageSource: AccountAgeSource,
        role: HouseholdRole = HouseholdRole.MEMBER,
    ) = UserAccount(
        uid = uid,
        displayName = displayName,
        email = "account@example.com",
        photoUrl = null,
        provider = AccountProvider.GOOGLE,
        householdId = "household",
        householdRole = role,
        ageGroup = ageGroup,
        ageSource = ageSource,
    )
}
''', encoding='utf-8')

# Static release gates against the exact source that will be packaged.
assert profile_screen.exists()
assert 'ProfileAgeSetupScreen(' in app_file.read_text(encoding='utf-8')
assert 'get() = ageSource == ProfileAgeSource.UNCONFIRMED' in models.read_text(encoding='utf-8')
resolver_text = resolver.read_text(encoding='utf-8')
assert 'householdRole == HouseholdRole.OWNER' not in resolver_text[resolver_text.index('fun resolveAge'):]
family_text = family.read_text(encoding='utf-8')
member_doc = family_text[family_text.index('private fun memberDocument'):family_text.index('private fun ideaDocument')]
assert 'FIELD_AGE_SOURCE' not in member_doc
sync_member = family_text[family_text.index('private suspend fun syncSignedInMember'):family_text.index('private suspend fun syncIdeas')]
assert 'FIELD_AGE_SOURCE' not in sync_member
assert 'if (profile.needsAgeConfirmation) return' in sync_member

print('Applied My Study Companion 0.15.8 Google age verification and live Spark Firestore compatibility repair.')
PY
