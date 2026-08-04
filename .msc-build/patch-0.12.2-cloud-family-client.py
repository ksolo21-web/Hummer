from pathlib import Path

ROOT = Path("MyStudyCompanion")

# Add explicit household invitation/join wire contracts.
dtos = ROOT / "app/src/main/java/com/mystudycompanion/app/network/RemoteDtos.kt"
text = dtos.read_text(encoding="utf-8")
anchor = '''@Serializable
data class HouseholdCapabilitiesResponseDto(
    val householdId: String,
    val canManageFamilyWorship: Boolean,
)

'''
addition = anchor + '''@Serializable
data class CreateHouseholdInvitationRequestDto(
    val householdId: String,
)

@Serializable
data class HouseholdInvitationResponseDto(
    val householdId: String,
    val invitationCode: String,
    val expiresAtEpochSeconds: Long,
)

@Serializable
data class JoinHouseholdRequestDto(
    val invitationCode: String,
)

@Serializable
data class JoinHouseholdResponseDto(
    val householdId: String,
    val role: String,
)

'''
if text.count(anchor) != 1:
    raise SystemExit("RemoteDtos household capability anchor changed.")
dtos.write_text(text.replace(anchor, addition, 1), encoding="utf-8")

api = ROOT / "app/src/main/java/com/mystudycompanion/app/network/BackendApi.kt"
text = api.read_text(encoding="utf-8")
anchor = '''    suspend fun householdCapabilities(): HouseholdCapabilitiesResponseDto {
        val response = client.get(path = "/v1/household/capabilities")
        requireSuccess(response, "household capability check")
        return decode(response.body)
    }

'''
addition = anchor + '''    suspend fun createHouseholdInvitation(
        request: CreateHouseholdInvitationRequestDto,
    ): HouseholdInvitationResponseDto {
        val response = client.post(
            path = "/v1/household/invitations",
            body = json.encodeToString(request),
        )
        requireSuccess(response, "household invitation creation")
        return decode(response.body)
    }

    suspend fun joinHousehold(
        request: JoinHouseholdRequestDto,
    ): JoinHouseholdResponseDto {
        val response = client.post(
            path = "/v1/household/join",
            body = json.encodeToString(request),
        )
        requireSuccess(response, "household join")
        return decode(response.body)
    }

'''
if text.count(anchor) != 1:
    raise SystemExit("BackendApi household capability anchor changed.")
api.write_text(text.replace(anchor, addition, 1), encoding="utf-8")

repo = ROOT / "app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
text = repo.read_text(encoding="utf-8")
text = text.replace(
    "import com.mystudycompanion.app.network.BackendConfig\n",
    "import com.mystudycompanion.app.network.BackendConfig\n"
    "import com.mystudycompanion.app.network.CreateHouseholdInvitationRequestDto\n"
    "import com.mystudycompanion.app.network.JoinHouseholdRequestDto\n",
    1,
)
old_state = '''data class FamilyOrganizerState(
    val capabilitiesLoaded: Boolean = false,
    val canManageFamilyWorship: Boolean = false,
    val householdId: String = "",
    val isGenerating: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)
'''
new_state = '''data class FamilyOrganizerState(
    val serviceConfigured: Boolean = false,
    val capabilitiesLoaded: Boolean = false,
    val canManageFamilyWorship: Boolean = false,
    val householdId: String = "",
    val isGenerating: Boolean = false,
    val isCreatingInvitation: Boolean = false,
    val invitationCode: String? = null,
    val invitationExpiresAtEpochSeconds: Long? = null,
    val isJoiningHousehold: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)
'''
if text.count(old_state) != 1:
    raise SystemExit("FamilyOrganizerState anchor changed.")
text = text.replace(old_state, new_state, 1)
text = text.replace(
    "    private val mutableState = MutableStateFlow(FamilyOrganizerState())\n",
    "    private val mutableState = MutableStateFlow(\n"
    "        FamilyOrganizerState(serviceConfigured = backendConfig.isConfigured),\n"
    "    )\n"
    "    val cloudServiceConfigured: Boolean get() = backendConfig.isConfigured\n",
    1,
)
old_unconfigured = '''            mutableState.value = FamilyOrganizerState(
                capabilitiesLoaded = true,
                errorMessage = "The private family service has not been configured yet.",
            )
'''
new_unconfigured = '''            mutableState.value = mutableState.value.copy(
                serviceConfigured = false,
                capabilitiesLoaded = true,
                canManageFamilyWorship = false,
                householdId = "",
                errorMessage = "The private family service has not been configured yet.",
            )
'''
if text.count(old_unconfigured) != 1:
    raise SystemExit("Unconfigured family service anchor changed.")
text = text.replace(old_unconfigured, new_unconfigured, 1)
text = text.replace(
    "                    capabilitiesLoaded = true,\n                    canManageFamilyWorship = capability.canManageFamilyWorship,\n",
    "                    serviceConfigured = true,\n                    capabilitiesLoaded = true,\n                    canManageFamilyWorship = capability.canManageFamilyWorship,\n",
    1,
)
method_anchor = '''    suspend fun generateAndSend(
        scheduledDate: LocalDate,
        topic: String,
    ) {
'''
methods = '''    suspend fun createHouseholdInvitation() {
        check(backendConfig.isConfigured) { "The private family service is not configured." }
        val current = mutableState.value
        require(current.canManageFamilyWorship) { "Only the verified household organizer can create invitations." }
        require(current.householdId.isNotBlank()) { "The organizer household ID is missing." }
        if (current.isCreatingInvitation) return

        mutableState.value = current.copy(
            isCreatingInvitation = true,
            successMessage = null,
            errorMessage = null,
        )
        runCatching {
            val invitation = backendApi.createHouseholdInvitation(
                CreateHouseholdInvitationRequestDto(householdId = current.householdId),
            )
            require(invitation.householdId == current.householdId) {
                "The invitation response did not match the organizer household."
            }
            invitation.copy(invitationCode = normalizeHouseholdInvitationCode(invitation.invitationCode))
        }.onSuccess { invitation ->
            val code = invitation.invitationCode
            mutableState.value = mutableState.value.copy(
                isCreatingInvitation = false,
                invitationCode = code,
                invitationExpiresAtEpochSeconds = invitation.expiresAtEpochSeconds,
                successMessage = "Invitation code created. Share it only with your household member.",
                errorMessage = null,
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isCreatingInvitation = false,
                errorMessage = error.message ?: "The household invitation could not be created.",
            )
        }
    }

    suspend fun joinHousehold(invitationCode: String) {
        check(backendConfig.isConfigured) { "The private family service is not configured." }
        val code = normalizeHouseholdInvitationCode(invitationCode)
        val current = mutableState.value
        if (current.isJoiningHousehold) return

        mutableState.value = current.copy(
            isJoiningHousehold = true,
            successMessage = null,
            errorMessage = null,
        )
        runCatching {
            backendApi.joinHousehold(JoinHouseholdRequestDto(invitationCode = code)).also { joined ->
                require(joined.householdId.isNotBlank()) { "The household join response did not include a household ID." }
            }
        }.onSuccess { joined ->
            mutableState.value = mutableState.value.copy(
                isJoiningHousehold = false,
                capabilitiesLoaded = true,
                householdId = joined.householdId,
                canManageFamilyWorship = joined.role.equals("owner", true) || joined.role.equals("organizer", true),
                invitationCode = null,
                invitationExpiresAtEpochSeconds = null,
                successMessage = "This account joined the household. Family content will synchronize on the next refresh.",
                errorMessage = null,
            )
            contentSyncEngine.sync("household_joined")
            refreshCapabilities()
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isJoiningHousehold = false,
                errorMessage = error.message ?: "The household could not be joined.",
            )
        }
    }

    suspend fun generateAndSend(
        scheduledDate: LocalDate,
        topic: String,
    ) {
'''
if text.count(method_anchor) != 1:
    raise SystemExit("Family organizer method insertion anchor changed.")
text = text.replace(method_anchor, methods, 1)
companion_anchor = '''    fun clearMessage() {
        mutableState.value = mutableState.value.copy(successMessage = null, errorMessage = null)
    }
}
'''
companion_new = '''    fun clearMessage() {
        mutableState.value = mutableState.value.copy(successMessage = null, errorMessage = null)
    }
}

internal fun normalizeHouseholdInvitationCode(value: String): String {
    val normalized = value.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "")
    require(normalized.length in 6..32) { "Enter a valid household invitation code." }
    return normalized
}
'''
if text.count(companion_anchor) != 1:
    raise SystemExit("Family organizer normalization anchor changed.")
text = text.replace(companion_anchor, companion_new, 1)
repo.write_text(text, encoding="utf-8")

screen = ROOT / "app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt"
text = screen.read_text(encoding="utf-8")
# Imports needed by the interactive invitation/join controls.
text = text.replace(
    "import androidx.compose.foundation.layout.widthIn\n",
    "import androidx.compose.foundation.layout.widthIn\nimport androidx.compose.foundation.layout.size\n",
    1,
)
text = text.replace(
    "import androidx.compose.material3.Card\n",
    "import androidx.compose.material3.Button\nimport androidx.compose.material3.Card\n",
    1,
)
text = text.replace(
    "import androidx.compose.material3.OutlinedButton\n",
    "import androidx.compose.material3.OutlinedButton\nimport androidx.compose.material3.OutlinedTextField\n",
    1,
)
text = text.replace(
    "import androidx.compose.runtime.LaunchedEffect\n",
    "import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.saveable.rememberSaveable\nimport androidx.compose.runtime.setValue\n",
    1,
)
text = text.replace(
    "import com.mystudycompanion.app.auth.HouseholdRole\n",
    "import com.mystudycompanion.app.auth.AccountProvider\nimport com.mystudycompanion.app.auth.HouseholdRole\n",
    1,
)
text = text.replace(
    "import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec\n",
    "import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec\nimport kotlinx.coroutines.launch\n",
    1,
)
state_anchor = '''    val organizerState by organizerRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(account.uid) { organizerRepository.refreshCapabilities() }
    val locallyPrivileged = account.householdRole == HouseholdRole.OWNER || account.householdRole == HouseholdRole.ORGANIZER
    val canManage = organizerState.canManageFamilyWorship || locallyPrivileged
    val householdId = organizerState.householdId.ifBlank { account.householdId.orEmpty() }
'''
state_new = '''    val organizerState by organizerRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var invitationInput by rememberSaveable(account.uid) { mutableStateOf("") }
    LaunchedEffect(account.uid) { organizerRepository.refreshCapabilities() }
    val locallyPrivileged = account.householdRole == HouseholdRole.OWNER || account.householdRole == HouseholdRole.ORGANIZER
    val canManage = organizerState.canManageFamilyWorship || locallyPrivileged
    val householdId = organizerState.householdId.ifBlank { account.householdId.orEmpty() }
    val googleCloudSession = account.provider == AccountProvider.GOOGLE && organizerRepository.cloudServiceConfigured
    val canCreateInvitation = googleCloudSession && organizerState.canManageFamilyWorship
    val canJoinHousehold = googleCloudSession && !organizerState.canManageFamilyWorship && householdId.isBlank()
'''
if text.count(state_anchor) != 1:
    raise SystemExit("Household screen state anchor changed.")
text = text.replace(state_anchor, state_new, 1)
old_controls = '''            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                Text(if (canManage) "  Invitations require the private backend invitation service" else "  Join requires an organizer code and the private backend")
            }
'''
new_controls = '''            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Household connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    when {
                        !organizerRepository.cloudServiceConfigured -> {
                            Text("The HTTPS family service has not been configured in this build.")
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Cloud service configuration required")
                            }
                        }
                        account.provider != AccountProvider.GOOGLE -> {
                            Text("Connect a Google account first. Local owner mode cannot be used to invite or join family members.")
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Google sign-in required")
                            }
                        }
                        canCreateInvitation -> {
                            Text("Create a short-lived invitation code for one household member. The backend validates the signed-in organizer and household.")
                            Button(
                                onClick = { scope.launch { organizerRepository.createHouseholdInvitation() } },
                                enabled = !organizerState.isCreatingInvitation,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (organizerState.isCreatingInvitation) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("  Creating…")
                                } else {
                                    Text("Create invitation code")
                                }
                            }
                            organizerState.invitationCode?.let { code ->
                                Text("Invitation code", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                organizerState.invitationExpiresAtEpochSeconds?.let { expiry ->
                                    Text("Expires at epoch second $expiry", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        canJoinHousehold -> {
                            Text("Enter the invitation code supplied by your household organizer.")
                            OutlinedTextField(
                                value = invitationInput,
                                onValueChange = { invitationInput = it.uppercase().take(32) },
                                label = { Text("Invitation code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { scope.launch { organizerRepository.joinHousehold(invitationInput) } },
                                enabled = !organizerState.isJoiningHousehold && invitationInput.trim().length >= 6,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (organizerState.isJoiningHousehold) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("  Joining…")
                                } else {
                                    Text("Join household")
                                }
                            }
                        }
                        else -> Text("This account is already connected to the household.")
                    }
                    organizerState.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
'''
if text.count(old_controls) != 1:
    raise SystemExit("Household disabled control anchor changed.")
text = text.replace(old_controls, new_controls, 1)
screen.write_text(text, encoding="utf-8")

# Focused validation tests for invitation input. Network and real identity tests remain live gates.
test = ROOT / "app/src/test/java/com/mystudycompanion/app/family/HouseholdInvitationContractTest.kt"
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(
    '''package com.mystudycompanion.app.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HouseholdInvitationContractTest {
    @Test
    fun invitationCodeIsNormalizedBeforeSending() {
        assertEquals("AB12-CD34", normalizeHouseholdInvitationCode(" ab12-cd34 "))
    }

    @Test
    fun malformedInvitationCodeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeHouseholdInvitationCode("x")
        }
    }
}
''',
    encoding="utf-8",
)

print("Implemented authenticated household invitation and join client flow.")
