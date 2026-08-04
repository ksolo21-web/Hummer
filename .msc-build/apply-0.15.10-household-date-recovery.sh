#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

root = Path('MyStudyCompanion')
family = root / 'app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt'
worship = root / 'app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt'
household = root / 'app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt'


def require(condition: bool, label: str) -> None:
    if not condition:
        raise SystemExit(f'0.15.10 recovery gate failed: {label}')


def replace_function(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f'{label}: start marker not found')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f'{label}: end marker not found')
    return text[:start] + replacement.rstrip() + '\n\n' + text[end:]


# Shared, user-safe error translation. Raw Firestore implementation details must
# never be shown as if the user did something wrong.
text = family.read_text(encoding='utf-8')
error_anchor = '''internal fun familyErrorMessageForDisplay(message: String?): String? {
    val clean = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return clean.takeUnless {
        it.contains("StandaloneCoroutine", ignoreCase = true) ||
            it.contains("Coroutine was cancelled", ignoreCase = true) ||
            it.contains("Job was cancelled", ignoreCase = true)
    }
}
'''
error_replacement = error_anchor + '''
internal fun isFamilyPermissionDenied(message: String?): Boolean {
    val clean = message.orEmpty()
    return clean.contains("PERMISSION_DENIED", ignoreCase = true) ||
        clean.contains("Missing or insufficient permissions", ignoreCase = true)
}

internal fun familyHouseholdActionErrorForDisplay(
    message: String?,
    fallback: String,
): String {
    if (isFamilyPermissionDenied(message)) {
        return "Firebase rejected this household change. Refresh household access on both devices, then create a new invitation code and try again."
    }
    return familyErrorMessageForDisplay(message) ?: fallback
}
'''
require(error_anchor in text, 'family error helper anchor is missing')
text = text.replace(error_anchor, error_replacement, 1)

create_household = r'''    suspend fun createHousehold(familyName: String = "My Family") {
        val current = mutableState.value
        if (current.isCreatingHousehold || current.householdId.isNotBlank()) return

        val cleanName = familyName.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { "My Family" }
        val householdId = "hh-${UUID.randomUUID().toString().replace("-", "").take(20)}"
        mutableState.value = current.copy(
            isCreatingHousehold = true,
            errorMessage = null,
            successMessage = null,
        )

        runFamilyCatching {
            val db = firestore ?: error("Firebase is not configured.")
            require(boundUid.isNotBlank()) { "Sign in with Google first." }
            val localProfile = signedInProfile(companionHubRepository.state.value)
            val board = companionHubRepository.state.value.familyBoard.copy(
                familyName = cleanName,
                creatorUid = boundUid,
                inviteCode = "",
            )
            val boardConfig = CloudFamilyBoardConfig.fromBoard(board).copy(revision = System.currentTimeMillis())
            val boardPayload = json.encodeToString(boardConfig)
            val boardFingerprint = boardConfigFingerprint(boardConfig)

            val batch = db.batch()
            val householdRef = db.collection(HOUSEHOLDS).document(householdId)
            val memberRef = householdRef.collection(MEMBERS).document(boundUid)
            val userRef = db.collection(USERS).document(boundUid)
            val boardRef = householdRef.collection(SHARED).document(FAMILY_BOARD_DOC)
            batch.set(householdRef, mapOf(
                FIELD_OWNER_UID to boundUid,
                FIELD_FAMILY_NAME to cleanName,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ))
            batch.set(memberRef, memberDocument(
                profile = localProfile,
                role = ROLE_OWNER,
                inviteCode = "",
                includeJoinedAt = true,
            ))
            // Replace the user link instead of merging it. Older builds could
            // leave fields that the current Firestore allow-list correctly rejects.
            batch.set(userRef, mapOf(
                FIELD_UID to boundUid,
                FIELD_DISPLAY_NAME to localProfile.displayName,
                FIELD_HOUSEHOLD_ID to householdId,
                FIELD_ROLE to ROLE_OWNER,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ))
            batch.set(boardRef, mapOf(
                FIELD_PAYLOAD_JSON to boardPayload,
                FIELD_REVISION to boardConfig.revision,
                FIELD_UPDATED_BY to boundUid,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ))
            batch.commit().awaitTask()
            boardFingerprint
        }.onSuccess { boardFingerprint ->
            lastBoardConfigPayload = boardFingerprint
            companionHubRepository.applyHouseholdIdentity(
                role = FamilyBoardRole.CREATOR,
                familyName = cleanName,
                ownerUid = boundUid,
            )
            mutableState.value = mutableState.value.copy(
                isCreatingHousehold = false,
                successMessage = "Your household is ready. You can create an invitation code now.",
                errorMessage = null,
            )
            refreshCapabilities()
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isCreatingHousehold = false,
                errorMessage = familyHouseholdActionErrorForDisplay(
                    error.message,
                    "The household could not be created.",
                ),
            )
        }
    }'''

create_invitation = r'''    suspend fun createHouseholdInvitation() {
        val current = mutableState.value
        if (current.isCreatingInvitation) return

        mutableState.value = current.copy(
            isCreatingInvitation = true,
            successMessage = null,
            errorMessage = null,
        )
        runFamilyCatching {
            val db = firestore ?: error("Firebase is not configured.")
            require(current.canManageFamilyWorship) { "Only a household organizer can create invitations." }
            require(current.householdId.isNotBlank()) { "Create or join a household first." }

            var selectedCode = ""
            var selectedExpiry = 0L
            for (attempt in 0 until MAX_INVITATION_ATTEMPTS) {
                val candidate = invitationCode()
                val expiry = Instant.now().epochSecond + INVITATION_TTL_SECONDS
                val result = runFamilyCatching {
                    db.runTransaction { transaction ->
                        val inviteRef = db.collection(INVITATIONS).document(candidate)
                        require(!transaction.get(inviteRef).exists()) { "Invitation collision." }
                        transaction.set(inviteRef, mapOf(
                            FIELD_HOUSEHOLD_ID to current.householdId,
                            FIELD_CREATED_BY to boundUid,
                            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                            FIELD_EXPIRES_AT_EPOCH_SECONDS to expiry,
                            FIELD_EXPIRES_AT to Timestamp(expiry, 0),
                            FIELD_STATUS to STATUS_ACTIVE,
                            FIELD_USED_BY to "",
                        ))
                        candidate
                    }.awaitTask()
                }
                if (result.isSuccess) {
                    selectedCode = candidate
                    selectedExpiry = expiry
                    break
                }
                val failure = result.exceptionOrNull()
                if (!failure?.message.orEmpty().contains("Invitation collision", ignoreCase = true)) {
                    throw failure ?: IllegalStateException("The invitation could not be created.")
                }
            }
            require(selectedCode.isNotBlank()) { "A unique invitation code could not be created. Try again." }
            selectedCode to selectedExpiry
        }.onSuccess { (code, expires) ->
            mutableState.value = mutableState.value.copy(
                isCreatingInvitation = false,
                invitationCode = code,
                invitationExpiresAtEpochSeconds = expires,
                successMessage = "Invitation code created. Share it only with your family member.",
                errorMessage = null,
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isCreatingInvitation = false,
                errorMessage = familyHouseholdActionErrorForDisplay(
                    error.message,
                    "The invitation could not be created.",
                ),
            )
        }
    }'''

join_household = r'''    suspend fun joinHousehold(invitationCode: String) {
        val current = mutableState.value
        if (current.isJoiningHousehold) return

        mutableState.value = current.copy(
            isJoiningHousehold = true,
            successMessage = null,
            errorMessage = null,
        )
        runFamilyCatching {
            val db = firestore ?: error("Firebase is not configured.")
            require(boundUid.isNotBlank()) { "Sign in with Google first." }
            require(current.householdId.isBlank()) { "This account is already connected to a household." }
            val invitationCandidates = householdInvitationLookupCandidates(invitationCode)
            val localProfile = signedInProfile(companionHubRepository.state.value)

            db.runTransaction { transaction ->
                var inviteRef: com.google.firebase.firestore.DocumentReference? = null
                var invite: com.google.firebase.firestore.DocumentSnapshot? = null
                for (candidate in invitationCandidates) {
                    val candidateRef = db.collection(INVITATIONS).document(candidate)
                    val candidateSnapshot = transaction.get(candidateRef)
                    if (candidateSnapshot.exists()) {
                        inviteRef = candidateRef
                        invite = candidateSnapshot
                        break
                    }
                }
                val resolvedInviteRef = requireNotNull(inviteRef) {
                    "That invitation code was not found. Ask the organizer to create a fresh code and try again."
                }
                val resolvedInvite = requireNotNull(invite) {
                    "That invitation code was not found. Ask the organizer to create a fresh code and try again."
                }
                val resolvedCode = resolvedInviteRef.id
                val householdId = resolvedInvite.getString(FIELD_HOUSEHOLD_ID).orEmpty()
                val status = resolvedInvite.getString(FIELD_STATUS).orEmpty()
                val expires = resolvedInvite.getLong(FIELD_EXPIRES_AT_EPOCH_SECONDS) ?: 0L
                require(householdId.isNotBlank()) { "That invitation is damaged. Ask the organizer to create a fresh code." }
                require(status == STATUS_ACTIVE) { "That invitation has already been used or cancelled." }
                require(expires > Instant.now().epochSecond) { "That invitation has expired." }

                val householdRef = db.collection(HOUSEHOLDS).document(householdId)
                val memberRef = householdRef.collection(MEMBERS).document(boundUid)
                val userRef = db.collection(USERS).document(boundUid)
                transaction.set(memberRef, memberDocument(
                    profile = localProfile,
                    role = ROLE_MEMBER,
                    inviteCode = resolvedCode,
                    includeJoinedAt = true,
                ))
                // Full replacement removes stale fields left by older private builds.
                // A merge can preserve forbidden keys and make a valid invitation fail.
                transaction.set(userRef, mapOf(
                    FIELD_UID to boundUid,
                    FIELD_DISPLAY_NAME to localProfile.displayName,
                    FIELD_HOUSEHOLD_ID to householdId,
                    FIELD_ROLE to ROLE_MEMBER,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ))
                transaction.update(resolvedInviteRef, mapOf(
                    FIELD_STATUS to STATUS_USED,
                    FIELD_USED_BY to boundUid,
                    FIELD_USED_AT to FieldValue.serverTimestamp(),
                ))
                householdId
            }.awaitTask()
        }.onSuccess { householdId ->
            mutableState.value = mutableState.value.copy(
                isJoiningHousehold = false,
                householdId = householdId,
                householdRole = HouseholdRole.MEMBER,
                canManageFamilyWorship = false,
                capabilitiesLoaded = true,
                successMessage = "This account joined the household. Family information will synchronize automatically.",
                errorMessage = null,
            )
            bindHouseholdListeners(householdId, HouseholdRole.MEMBER)
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isJoiningHousehold = false,
                errorMessage = familyHouseholdActionErrorForDisplay(
                    error.message,
                    "The household could not be joined.",
                ),
            )
        }
    }'''

generate_and_send = r'''    suspend fun generateAndSend(scheduledDate: LocalDate, topic: String) {
        val current = mutableState.value
        if (current.isGenerating) return

        mutableState.value = current.copy(
            isGenerating = true,
            successMessage = null,
            errorMessage = null,
        )
        runFamilyCatching {
            val db = firestore ?: error("Firebase is not configured.")
            require(current.canManageFamilyWorship) { "Only a household organizer can publish Family Worship." }
            require(current.householdId.isNotBlank()) { "Create or join a household first." }
            require(backendConfig.isConfigured) {
                "The official-source family study service is not connected in this build. Your selected date is safe; no changes were published."
            }
            val cleanTopic = topic.trim().replace(Regex("\\s+"), " ").take(300)
            require(cleanTopic.length >= 3) { "Enter a Family Worship topic." }

            val generation = backendApi.generateFamilyWorship(
                GenerateFamilyWorshipRequestDto(
                    householdId = current.householdId,
                    scheduledDateIso = scheduledDate.toString(),
                    topic = cleanTopic,
                    notifyDevices = true,
                ),
            )
            require(generation.generated) { generation.reason.ifBlank { "The private service did not generate a plan." } }

            when (val syncResult = contentSyncEngine.sync("family_worship_generated")) {
                is SyncResult.Success -> Unit
                SyncResult.NotConfigured -> error("The private official-content service is not configured.")
                is SyncResult.UpdateRequired -> error(syncResult.message)
                is SyncResult.Offline -> error(syncResult.message)
                is SyncResult.SecurityRejected -> error(syncResult.message)
                is SyncResult.Failed -> error(syncResult.message)
            }

            val worshipId = generation.contentId.substringAfterLast(':')
            val study = studyRepository.familyWorshipSnapshot(worshipId)
                ?: error("The signed Family Worship plan was not available after synchronization.")
            FamilyWorshipPublicationValidator.requirePublishable(
                study = study,
                expectedHouseholdId = current.householdId,
                expectedDate = scheduledDate,
                minimumRevision = generation.revision,
            )

            val payload = json.encodeToString(CloudFamilyWorshipPlan.fromDomain(study))
            db.collection(HOUSEHOLDS).document(current.householdId)
                .collection(FAMILY_WORSHIP).document(CURRENT_PLAN_DOC)
                .set(mapOf(
                    FIELD_PAYLOAD_JSON to payload,
                    FIELD_REVISION to study.revision,
                    FIELD_UPDATED_BY to boundUid,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ))
                .awaitTask()
        }.onSuccess {
            mutableState.value = mutableState.value.copy(
                isGenerating = false,
                successMessage = "The official-source Family Worship plan was generated, verified, and published to the household.",
                errorMessage = null,
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isGenerating = false,
                errorMessage = familyHouseholdActionErrorForDisplay(
                    error.message,
                    "Family Worship could not be generated and published.",
                ),
            )
        }
    }'''

text = replace_function(text, '    suspend fun createHousehold(', '    suspend fun createHouseholdInvitation()', create_household, 'createHousehold')
text = replace_function(text, '    suspend fun createHouseholdInvitation()', '    suspend fun joinHousehold(', create_invitation, 'createHouseholdInvitation')
text = replace_function(text, '    suspend fun joinHousehold(', '    suspend fun generateAndSend(', join_household, 'joinHousehold')
text = replace_function(text, '    suspend fun generateAndSend(', '    fun clearMessage()', generate_and_send, 'generateAndSend')

# Capability checks and essential listeners get actionable messages. Optional
# family-progress listeners do not poison the whole page with a raw permission line.
old_capability_failure = '''        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                serviceConfigured = true,
                capabilitiesLoaded = true,
                errorMessage = error.message ?: "Household access could not be checked.",
            )
        }
'''
new_capability_failure = '''        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                serviceConfigured = true,
                capabilitiesLoaded = true,
                errorMessage = familyHouseholdActionErrorForDisplay(
                    error.message,
                    "Household access could not be checked.",
                ),
            )
        }
'''
require(text.count(old_capability_failure) == 1, 'capability failure target is missing')
text = text.replace(old_capability_failure, new_capability_failure, 1)
text = text.replace(
    'reportSyncError(error.message ?: "Family board sync failed.")',
    'reportSyncError(error.message ?: "Family board sync failed.", critical = true)',
    1,
)
text = text.replace(
    'reportSyncError(error.message ?: "Household member sync failed.")',
    'reportSyncError(error.message ?: "Household member sync failed.", critical = true)',
    1,
)
old_report = '''    private fun reportSyncError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message)
    }
'''
new_report = '''    private fun reportSyncError(message: String, critical: Boolean = false) {
        if (isFamilyPermissionDenied(message) && !critical) return
        mutableState.value = mutableState.value.copy(
            errorMessage = familyHouseholdActionErrorForDisplay(
                message,
                "Household synchronization was interrupted. Refresh household access and try again.",
            ),
        )
    }
'''
require(text.count(old_report) == 1, 'reportSyncError target is missing')
text = text.replace(old_report, new_report, 1)
family.write_text(text, encoding='utf-8')

# Replace the Compose experimental date picker with Android's stable platform
# dialog. Invalid saved dates and dialog launch errors remain in-page, never crashes.
text = worship.read_text(encoding='utf-8')
text = text.replace('package com.mystudycompanion.app.ui\n\n', 'package com.mystudycompanion.app.ui\n\nimport android.app.DatePickerDialog\n', 1)
for line in (
    'import androidx.compose.material3.DatePicker\n',
    'import androidx.compose.material3.DatePickerDialog\n',
    'import androidx.compose.material3.ExperimentalMaterial3Api\n',
    'import androidx.compose.material3.TextButton\n',
    'import androidx.compose.material3.rememberDatePickerState\n',
    'import java.time.Instant\n',
    'import java.time.ZoneOffset\n',
):
    text = text.replace(line, '')
text = text.replace('@OptIn(ExperimentalMaterial3Api::class)\n', '', 1)
old_date_state = '''    var topic by rememberSaveable(study.id) { mutableStateOf("") }
    var selectedDateIso by rememberSaveable(study.id) {
        mutableStateOf(study.scheduledDate.plusWeeks(1).toString())
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val selectedDate = LocalDate.parse(selectedDateIso)

    if (showDatePicker) {
        val initialMillis = selectedDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { epochMillis ->
                            selectedDateIso = Instant.ofEpochMilli(epochMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    },
                ) { Text("Use date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
'''
new_date_state = '''    val context = LocalContext.current
    var topic by rememberSaveable(study.id) { mutableStateOf("") }
    var selectedDateIso by rememberSaveable(study.id) {
        mutableStateOf(study.scheduledDate.plusWeeks(1).toString())
    }
    var datePickerError by rememberSaveable(study.id) { mutableStateOf<String?>(null) }
    val fallbackDate = study.scheduledDate.plusWeeks(1)
    val selectedDate = runCatching { LocalDate.parse(selectedDateIso) }.getOrElse {
        selectedDateIso = fallbackDate.toString()
        fallbackDate
    }
'''
require(text.count(old_date_state) == 1, 'experimental date picker block is missing')
text = text.replace(old_date_state, new_date_state, 1)
old_button = '''            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
            }
'''
new_button = '''            OutlinedButton(
                onClick = {
                    datePickerError = null
                    runCatching {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                runCatching { LocalDate.of(year, month + 1, dayOfMonth) }
                                    .onSuccess { chosen -> selectedDateIso = chosen.toString() }
                                    .onFailure { datePickerError = "That date could not be selected. Try another date." }
                            },
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth,
                        ).show()
                    }.onFailure {
                        datePickerError = "The date selector could not open. Close and reopen Family Worship, then try again."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
            }
            datePickerError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
'''
require(text.count(old_button) == 1, 'date picker button target is missing')
text = text.replace(old_button, new_button, 1)
worship.write_text(text, encoding='utf-8')

# Give the user a direct recovery action instead of leaving an inert red error.
text = household.read_text(encoding='utf-8')
old_error_ui = '''            familyErrorMessageForDisplay(organizerState.errorMessage)?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
'''
new_error_ui = '''            familyErrorMessageForDisplay(organizerState.errorMessage)?.let { message ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(
                            onClick = organizerRepository::requestRefreshCapabilities,
                            enabled = !organizerState.isCreatingInvitation && !organizerState.isJoiningHousehold,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Refresh household access")
                        }
                    }
                }
            }
'''
require(text.count(old_error_ui) == 1, 'household error UI target is missing')
text = text.replace(old_error_ui, new_error_ui, 1)
household.write_text(text, encoding='utf-8')

# Static acceptance gates.
family_text = family.read_text(encoding='utf-8')
worship_text = worship.read_text(encoding='utf-8')
household_text = household.read_text(encoding='utf-8')
require('familyHouseholdActionErrorForDisplay' in family_text, 'safe household error translation missing')
require('critical: Boolean = false' in family_text, 'listener severity isolation missing')
require('transaction.set(userRef, mapOf(' in family_text, 'join user replacement missing')
require('), SetOptions.merge())\n                transaction.update(resolvedInviteRef' not in family_text, 'join still merges stale user fields')
require('batch.set(userRef, mapOf(' in family_text, 'create user replacement missing')
require('), SetOptions.merge())\n            batch.set(boardRef' not in family_text, 'create still merges stale user fields')
require('val db = firestore ?: error("Firebase is not configured.")' in family_text, 'safe precondition path missing')
require('DatePickerDialog(' in worship_text, 'stable platform date picker missing')
require('rememberDatePickerState' not in worship_text, 'experimental Compose date picker remains')
require('LocalDate.parse(selectedDateIso) }.getOrElse' in worship_text, 'saved date recovery missing')
require('Refresh household access' in household_text, 'household recovery action missing')

print('Applied My Study Companion 0.15.10 household pairing and family date crash recovery.')
PY
