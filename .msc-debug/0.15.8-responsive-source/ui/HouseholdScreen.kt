package com.mystudycompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.HouseholdRole
import com.mystudycompanion.app.auth.UserAccount
import com.mystudycompanion.app.family.FamilyWorshipOrganizerRepository
import com.mystudycompanion.app.family.familyErrorMessageForDisplay
import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec

@Composable
fun HouseholdScreen(
    account: UserAccount,
    organizerRepository: FamilyWorshipOrganizerRepository,
    layoutSpec: AdaptiveLayoutSpec,
    modifier: Modifier = Modifier,
) {
    val organizerState by organizerRepository.state.collectAsStateWithLifecycle()
    var invitationInput by rememberSaveable(account.uid) { mutableStateOf("") }
    LaunchedEffect(account.uid) { organizerRepository.requestRefreshCapabilities() }
    val locallyPrivileged = account.householdRole == HouseholdRole.OWNER || account.householdRole == HouseholdRole.ORGANIZER
    val canManage = organizerState.canManageFamilyWorship || locallyPrivileged
    val householdId = organizerState.householdId.ifBlank { account.householdId.orEmpty() }
    val googleCloudSession = account.provider == AccountProvider.GOOGLE && organizerRepository.cloudServiceConfigured
    val canCreateInvitation = googleCloudSession && organizerState.canManageFamilyWorship
    val canJoinHousehold = googleCloudSession && !organizerState.canManageFamilyWorship && householdId.isBlank()

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(layoutSpec.outerPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Household", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(28.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.FamilyRestroom, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(householdId.ifBlank { "Private household not joined" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                canManage -> "Organizer access"
                                !organizerState.capabilitiesLoaded -> "Checking household access…"
                                householdId.isBlank() -> "Create or join a household"
                                else -> "Household member access"
                            },
                        )
                    }
                    if (!organizerState.capabilitiesLoaded) CircularProgressIndicator()
                    else if (canManage) Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            familyErrorMessageForDisplay(organizerState.errorMessage)?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sharing boundary", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text("Family Worship plans, discussion questions, and selected insights can be shared. Personal notes, private reflections, and AI history are never exposed automatically.")
                }
            }
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Household connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    when {
                        !organizerRepository.cloudServiceConfigured -> {
                            Text("Firebase family synchronization has not been configured in this build.")
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Firebase configuration required")
                            }
                        }
                        account.provider != AccountProvider.GOOGLE -> {
                            Text("Connect a Google account first. Local owner mode cannot be used to invite or join family members.")
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Google sign-in required")
                            }
                        }
                        canCreateInvitation -> {
                            Text("Create a one-time invitation code for a family member. Firebase verifies the signed-in organizer and household membership.")
                            Button(
                                onClick = organizerRepository::requestCreateHouseholdInvitation,
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
                            Text("Start your own household, or enter the invitation code supplied by your household organizer.")
                            Button(
                                onClick = { organizerRepository.requestCreateHousehold() },
                                enabled = !organizerState.isCreatingHousehold && !organizerState.isJoiningHousehold,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (organizerState.isCreatingHousehold) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("  Creating household…")
                                } else {
                                    Text("Create my household")
                                }
                            }
                            Text("or", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = invitationInput,
                                onValueChange = { invitationInput = it.uppercase().take(32) },
                                label = { Text("Invitation code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { organizerRepository.requestJoinHousehold(invitationInput) },
                                enabled = !organizerState.isJoiningHousehold && !organizerState.isCreatingHousehold && invitationInput.trim().length >= 6,
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
        }
    }
}
