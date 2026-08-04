package com.mystudycompanion.app.family

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.mystudycompanion.app.BuildConfig
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.AuthRepository
import com.mystudycompanion.app.auth.AuthState
import com.mystudycompanion.app.auth.HouseholdRole
import com.mystudycompanion.app.companion.AgeGroup
import com.mystudycompanion.app.companion.CompanionHubRepository
import com.mystudycompanion.app.companion.CompanionHubState
import com.mystudycompanion.app.companion.CompanionProfile
import com.mystudycompanion.app.companion.FamilyBoardRole
import com.mystudycompanion.app.companion.FamilyMemberProfile
import com.mystudycompanion.app.companion.FamilyWorshipBoard
import com.mystudycompanion.app.companion.FamilyWorshipIdea
import com.mystudycompanion.app.companion.SharedMemberProgress
import com.mystudycompanion.app.companion.WorkbookPageProgress
import com.mystudycompanion.app.data.FamilyWorshipSection
import com.mystudycompanion.app.data.FamilyWorshipStudy
import com.mystudycompanion.app.data.SpiritualSourcePolicy
import com.mystudycompanion.app.data.repository.StudyRepository
import com.mystudycompanion.app.network.BackendApi
import com.mystudycompanion.app.network.BackendConfig
import com.mystudycompanion.app.network.GenerateFamilyWorshipRequestDto
import com.mystudycompanion.app.update.ContentSyncEngine
import com.mystudycompanion.app.update.SyncResult
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FamilyOrganizerState(
    val serviceConfigured: Boolean = false,
    val capabilitiesLoaded: Boolean = false,
    val canManageFamilyWorship: Boolean = false,
    val householdId: String = "",
    val householdRole: HouseholdRole = HouseholdRole.NONE,
    val isCreatingHousehold: Boolean = false,
    val isGenerating: Boolean = false,
    val isCreatingInvitation: Boolean = false,
    val invitationCode: String? = null,
    val invitationExpiresAtEpochSeconds: Long? = null,
    val isJoiningHousehold: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@Serializable
private data class CloudFamilyBoardConfig(
    val familyName: String = "My Family",
    val creatorUid: String = "",
    val selectedIdeaId: String? = null,
    val selectedCustomTopic: String = "",
    val scheduledDateIso: String = "",
    val scheduledTime24h: String = "19:00",
    val durationMinutes: Int = 60,
    val recurringWeekly: Boolean = true,
    val revision: Long = 0L,
) {
    fun toBoard(
        members: List<FamilyMemberProfile>,
        ideas: List<FamilyWorshipIdea>,
    ): FamilyWorshipBoard = FamilyWorshipBoard(
        familyName = familyName,
        inviteCode = "",
        creatorUid = creatorUid,
        members = members,
        ideas = ideas,
        selectedIdeaId = selectedIdeaId?.takeIf { selected -> ideas.any { it.id == selected } },
        selectedCustomTopic = selectedCustomTopic,
        scheduledDateIso = scheduledDateIso,
        scheduledTime24h = scheduledTime24h,
        durationMinutes = durationMinutes.coerceIn(15, 180),
        recurringWeekly = recurringWeekly,
    )

    companion object {
        fun fromBoard(board: FamilyWorshipBoard): CloudFamilyBoardConfig = CloudFamilyBoardConfig(
            familyName = board.familyName.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { "My Family" },
            creatorUid = board.creatorUid,
            selectedIdeaId = board.selectedIdeaId,
            selectedCustomTopic = board.selectedCustomTopic.trim().replace(Regex("\\s+"), " ").take(180),
            scheduledDateIso = board.scheduledDateIso,
            scheduledTime24h = board.scheduledTime24h,
            durationMinutes = board.durationMinutes.coerceIn(15, 180),
            recurringWeekly = board.recurringWeekly,
            revision = 0L,
        )
    }
}

private data class CloudIdeaRecord(
    val idea: FamilyWorshipIdea,
    val createdByUid: String,
)

private data class CloudVoteRecord(
    val ideaId: String,
    val voterUid: String,
    val createdByUid: String,
)

@Serializable
private data class CloudFamilyWorshipSection(
    val id: String,
    val title: String,
    val detail: String,
    val officialUrl: String,
    val orderIndex: Int,
    val completed: Boolean,
)

@Serializable
private data class CloudFamilyWorshipPlan(
    val id: String,
    val householdId: String,
    val scheduledDateIso: String,
    val title: String,
    val theme: String,
    val keyScripture: String,
    val overview: String,
    val preparationQuestion: String,
    val officialUrl: String,
    val sections: List<CloudFamilyWorshipSection>,
    val revision: Long,
) {
    fun toDomain(): FamilyWorshipStudy = FamilyWorshipStudy(
        id = id,
        householdId = householdId,
        scheduledDate = LocalDate.parse(scheduledDateIso),
        title = title,
        theme = theme,
        keyScripture = keyScripture,
        overview = overview,
        preparationQuestion = preparationQuestion,
        officialUrl = officialUrl,
        sections = sections.map {
            FamilyWorshipSection(
                id = it.id,
                title = it.title,
                detail = it.detail,
                officialUrl = it.officialUrl,
                orderIndex = it.orderIndex,
                completed = it.completed,
            )
        },
        revision = revision,
    )

    companion object {
        fun fromDomain(study: FamilyWorshipStudy): CloudFamilyWorshipPlan = CloudFamilyWorshipPlan(
            id = study.id,
            householdId = study.householdId,
            scheduledDateIso = study.scheduledDate.toString(),
            title = study.title,
            theme = study.theme,
            keyScripture = study.keyScripture,
            overview = study.overview,
            preparationQuestion = study.preparationQuestion,
            officialUrl = study.officialUrl,
            sections = study.sections.map {
                CloudFamilyWorshipSection(
                    id = it.id,
                    title = it.title,
                    detail = it.detail,
                    officialUrl = it.officialUrl,
                    orderIndex = it.orderIndex,
                    completed = it.completed,
                )
            },
            revision = study.revision,
        )
    }
}

internal object FamilyWorshipPublicationValidator {
    fun requirePublishable(
        study: FamilyWorshipStudy,
        expectedHouseholdId: String,
        expectedDate: LocalDate,
        minimumRevision: Long,
    ): FamilyWorshipStudy {
        require(study.householdId == expectedHouseholdId) {
            "The generated Family Worship plan belongs to a different household."
        }
        require(study.scheduledDate == expectedDate) {
            "The generated Family Worship plan has the wrong scheduled date."
        }
        require(study.revision >= minimumRevision && minimumRevision > 0L) {
            "The generated Family Worship plan has an invalid or stale revision."
        }
        require(study.sections.isNotEmpty()) { "The generated Family Worship plan has no study sections." }
        SpiritualSourcePolicy.requireAllowed(study.officialUrl)
        study.sections.forEach { SpiritualSourcePolicy.requireAllowed(it.officialUrl) }
        return study
    }
}

/**
 * Account-owned Firebase family synchronization. Authentication supplies the
 * account identity while Firestore stores only structured household records.
 * Organizer-only scheduling data is separated from member-created ideas and
 * votes so a family member cannot overwrite household authority fields.
 */
class FamilyWorshipOrganizerRepository(
    private val authRepository: AuthRepository,
    private val companionHubRepository: CompanionHubRepository,
    private val studyRepository: StudyRepository,
    private val backendConfig: BackendConfig,
    private val backendApi: BackendApi,
    private val contentSyncEngine: ContentSyncEngine,
) {
    private val firestore: FirebaseFirestore? = if (BuildConfig.FIREBASE_CONFIGURED) {
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    } else {
        null
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()
    private val mutableState = MutableStateFlow(
        FamilyOrganizerState(serviceConfigured = firestore != null),
    )
    val state: StateFlow<FamilyOrganizerState> = mutableState
    val cloudServiceConfigured: Boolean get() = firestore != null

    private var boundUid: String = ""
    private var boundDisplayName: String = ""

    private var boardListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private var ideasListener: ListenerRegistration? = null
    private var votesListener: ListenerRegistration? = null
    private var progressListener: ListenerRegistration? = null
    private var workbookPagesListener: ListenerRegistration? = null
    private var worshipListener: ListenerRegistration? = null

    private var boardSnapshotLoaded = false
    private var membersSnapshotLoaded = false
    private var ideasSnapshotLoaded = false
    private var votesSnapshotLoaded = false
    private var progressSnapshotLoaded = false
    private var workbookPagesSnapshotLoaded = false

    private var cloudBoardConfig = CloudFamilyBoardConfig()
    private var cloudMembers: List<FamilyMemberProfile> = emptyList()
    private var cloudIdeas: List<CloudIdeaRecord> = emptyList()
    private var cloudVotes: List<CloudVoteRecord> = emptyList()

    private var lastBoardConfigPayload = ""
    private var lastMemberFingerprint = ""
    private var lastProgressPayload = ""
    private var lastWorkbookPageFingerprints: Map<String, String> = emptyMap()
    private var lastIdeaFingerprints: Map<String, String> = emptyMap()
    private var lastOwnVoteKeys: Set<String> = emptySet()

    private var familyUploadJob: Job? = null
    private var progressUploadJob: Job? = null
    private var workbookUploadJob: Job? = null

    init {
        scope.launch {
            authRepository.state.collectLatest { authState ->
                detachCloudListeners()
                val account = (authState as? AuthState.SignedIn)?.account
                if (account == null || account.provider != AccountProvider.GOOGLE || firestore == null) {
                    mutableState.value = FamilyOrganizerState(
                        serviceConfigured = firestore != null,
                        capabilitiesLoaded = true,
                        errorMessage = if (firestore == null) "Firebase is not configured in this build." else null,
                    )
                    return@collectLatest
                }
                boundUid = account.uid
                boundDisplayName = account.displayName.ifBlank { "Family Member" }
                refreshCapabilities()
            }
        }

        scope.launch {
            companionHubRepository.state.collectLatest { hubState ->
                if (!familySnapshotsReady() || boundUid.isBlank()) return@collectLatest
                familyUploadJob?.cancel()
                familyUploadJob = scope.launch {
                    delay(650)
                    synchronizeLocalFamilyState(hubState)
                }
            }
        }

        scope.launch {
            companionHubRepository.state.collectLatest { hubState ->
                if (!progressSnapshotLoaded || boundUid.isBlank()) return@collectLatest
                val payload = json.encodeToString(companionHubRepository.sharedMemberProgress())
                if (payload == lastProgressPayload) return@collectLatest
                progressUploadJob?.cancel()
                progressUploadJob = scope.launch {
                    delay(650)
                    uploadMemberProgress(payload)
                }
            }
        }

        scope.launch {
            companionHubRepository.state.collectLatest { hubState ->
                if (!workbookPagesSnapshotLoaded || boundUid.isBlank()) return@collectLatest
                workbookUploadJob?.cancel()
                workbookUploadJob = scope.launch {
                    delay(750)
                    uploadWorkbookPages(hubState)
                }
            }
        }
    }

    suspend fun refreshCapabilities() {
        val db = firestore ?: run {
            mutableState.value = mutableState.value.copy(
                serviceConfigured = false,
                capabilitiesLoaded = true,
                errorMessage = "Firebase is not configured in this build.",
            )
            return
        }
        if (boundUid.isBlank()) {
            mutableState.value = mutableState.value.copy(
                serviceConfigured = true,
                capabilitiesLoaded = true,
                householdId = "",
                householdRole = HouseholdRole.NONE,
                canManageFamilyWorship = false,
            )
            return
        }

        runCatching {
            val user = db.collection(USERS).document(boundUid).get().awaitTask()
            val householdId = user.getString(FIELD_HOUSEHOLD_ID).orEmpty()
            if (householdId.isBlank()) return@runCatching "" to HouseholdRole.NONE
            val member = db.collection(HOUSEHOLDS).document(householdId)
                .collection(MEMBERS).document(boundUid).get().awaitTask()
            require(member.exists()) { "Your household membership record is missing." }
            householdId to member.getString(FIELD_ROLE).toHouseholdRole()
        }.onSuccess { (householdId, role) ->
            mutableState.value = mutableState.value.copy(
                serviceConfigured = true,
                capabilitiesLoaded = true,
                householdId = householdId,
                householdRole = role,
                canManageFamilyWorship = role == HouseholdRole.OWNER || role == HouseholdRole.ORGANIZER,
                errorMessage = null,
            )
            if (householdId.isNotBlank()) bindHouseholdListeners(householdId, role)
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                serviceConfigured = true,
                capabilitiesLoaded = true,
                errorMessage = error.message ?: "Household access could not be checked.",
            )
        }
    }

    suspend fun createHousehold(familyName: String = "My Family") {
        val db = firestore ?: error("Firebase is not configured.")
        require(boundUid.isNotBlank()) { "Sign in with Google first." }
        if (mutableState.value.householdId.isNotBlank()) return

        val cleanName = familyName.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { "My Family" }
        val householdId = "hh-${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val localProfile = signedInProfile(companionHubRepository.state.value)
        val board = companionHubRepository.state.value.familyBoard.copy(
            familyName = cleanName,
            creatorUid = boundUid,
            inviteCode = "",
        )
        val boardConfig = CloudFamilyBoardConfig.fromBoard(board).copy(revision = System.currentTimeMillis())
        val boardPayload = json.encodeToString(boardConfig)
        val boardFingerprint = boardConfigFingerprint(boardConfig)

        mutableState.value = mutableState.value.copy(
            isCreatingHousehold = true,
            errorMessage = null,
            successMessage = null,
        )
        runCatching {
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
            batch.set(userRef, mapOf(
                FIELD_UID to boundUid,
                FIELD_DISPLAY_NAME to localProfile.displayName,
                FIELD_HOUSEHOLD_ID to householdId,
                FIELD_ROLE to ROLE_OWNER,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ), SetOptions.merge())
            batch.set(boardRef, mapOf(
                FIELD_PAYLOAD_JSON to boardPayload,
                FIELD_REVISION to boardConfig.revision,
                FIELD_UPDATED_BY to boundUid,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ))
            batch.commit().awaitTask()
        }.onSuccess {
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
                errorMessage = error.message ?: "The household could not be created.",
            )
        }
    }

    suspend fun createHouseholdInvitation() {
        val db = firestore ?: error("Firebase is not configured.")
        val current = mutableState.value
        require(current.canManageFamilyWorship) { "Only a household organizer can create invitations." }
        require(current.householdId.isNotBlank()) { "Create or join a household first." }
        if (current.isCreatingInvitation) return

        mutableState.value = current.copy(
            isCreatingInvitation = true,
            successMessage = null,
            errorMessage = null,
        )
        runCatching {
            var selectedCode = ""
            var selectedExpiry = 0L
            for (attempt in 0 until MAX_INVITATION_ATTEMPTS) {
                val candidate = invitationCode()
                val expiry = Instant.now().epochSecond + INVITATION_TTL_SECONDS
                val created = runCatching {
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
                }.getOrNull()
                if (created != null) {
                    selectedCode = candidate
                    selectedExpiry = expiry
                    break
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
                errorMessage = error.message ?: "The invitation could not be created.",
            )
        }
    }

    suspend fun joinHousehold(invitationCode: String) {
        val db = firestore ?: error("Firebase is not configured.")
        require(boundUid.isNotBlank()) { "Sign in with Google first." }
        val code = normalizeHouseholdInvitationCode(invitationCode)
        val current = mutableState.value
        require(current.householdId.isBlank()) { "This account is already connected to a household." }
        if (current.isJoiningHousehold) return

        val localProfile = signedInProfile(companionHubRepository.state.value)
        mutableState.value = current.copy(
            isJoiningHousehold = true,
            successMessage = null,
            errorMessage = null,
        )
        runCatching {
            db.runTransaction { transaction ->
                val inviteRef = db.collection(INVITATIONS).document(code)
                val invite = transaction.get(inviteRef)
                val householdId = invite.getString(FIELD_HOUSEHOLD_ID).orEmpty()
                val status = invite.getString(FIELD_STATUS).orEmpty()
                val expires = invite.getLong(FIELD_EXPIRES_AT_EPOCH_SECONDS) ?: 0L
                require(invite.exists() && householdId.isNotBlank()) { "That invitation code was not found." }
                require(status == STATUS_ACTIVE) { "That invitation has already been used or cancelled." }
                require(expires > Instant.now().epochSecond) { "That invitation has expired." }

                // Do not read the protected household document before membership exists.
                // The invitation could only have been created by an organizer, and
                // household deletion is disabled by the rules.
                val householdRef = db.collection(HOUSEHOLDS).document(householdId)
                val memberRef = householdRef.collection(MEMBERS).document(boundUid)
                val userRef = db.collection(USERS).document(boundUid)
                transaction.set(memberRef, memberDocument(
                    profile = localProfile,
                    role = ROLE_MEMBER,
                    inviteCode = code,
                    includeJoinedAt = true,
                ))
                transaction.set(userRef, mapOf(
                    FIELD_UID to boundUid,
                    FIELD_DISPLAY_NAME to localProfile.displayName,
                    FIELD_HOUSEHOLD_ID to householdId,
                    FIELD_ROLE to ROLE_MEMBER,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ), SetOptions.merge())
                transaction.update(inviteRef, mapOf(
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
                errorMessage = error.message ?: "The household could not be joined.",
            )
        }
    }

    suspend fun generateAndSend(scheduledDate: LocalDate, topic: String) {
        val db = firestore ?: error("Firebase is not configured.")
        val current = mutableState.value
        require(current.canManageFamilyWorship) { "Only a household organizer can publish Family Worship." }
        require(backendConfig.isConfigured) {
            "The private official-content service is not configured in this app build."
        }
        val cleanTopic = topic.trim().replace(Regex("\\s+"), " ").take(300)
        require(cleanTopic.length >= 3) { "Enter a Family Worship topic." }
        if (current.isGenerating) return

        mutableState.value = current.copy(isGenerating = true, successMessage = null, errorMessage = null)
        runCatching {
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
                errorMessage = error.message ?: "Family Worship could not be generated and published.",
            )
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(successMessage = null, errorMessage = null)
    }

    private fun bindHouseholdListeners(householdId: String, role: HouseholdRole) {
        val db = firestore ?: return
        detachSnapshotRegistrationsOnly()
        resetCloudSnapshotState()

        val householdRef = db.collection(HOUSEHOLDS).document(householdId)
        val boardRef = householdRef.collection(SHARED).document(FAMILY_BOARD_DOC)

        boardListener = boardRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                reportSyncError(error.message ?: "Family board sync failed.")
                return@addSnapshotListener
            }
            val payload = snapshot?.getString(FIELD_PAYLOAD_JSON).orEmpty()
            if (payload.isNotBlank()) {
                runCatching { json.decodeFromString<CloudFamilyBoardConfig>(payload) }
                    .onSuccess { config ->
                        cloudBoardConfig = config
                        lastBoardConfigPayload = boardConfigFingerprint(config)
                    }
                    .onFailure { reportSyncError("The shared family board data could not be read.") }
            } else {
                cloudBoardConfig = CloudFamilyBoardConfig.fromBoard(
                    companionHubRepository.state.value.familyBoard.copy(
                        creatorUid = companionHubRepository.state.value.familyBoard.creatorUid.ifBlank {
                            if (role == HouseholdRole.OWNER) boundUid else ""
                        },
                    ),
                )
                if (role == HouseholdRole.OWNER || role == HouseholdRole.ORGANIZER) {
                    lastBoardConfigPayload = ""
                }
            }
            boardSnapshotLoaded = true
            publishCombinedCloudBoard()
            if (payload.isBlank() && (role == HouseholdRole.OWNER || role == HouseholdRole.ORGANIZER)) {
                scope.launch { synchronizeLocalFamilyState(companionHubRepository.state.value) }
            }
        }

        membersListener = householdRef.collection(MEMBERS).addSnapshotListener { snapshots, error ->
            if (error != null) {
                reportSyncError(error.message ?: "Household member sync failed.")
                return@addSnapshotListener
            }
            cloudMembers = snapshots?.documents.orEmpty().mapNotNull(::memberFromSnapshot)
                .sortedWith(compareBy<FamilyMemberProfile> { it.role != FamilyBoardRole.CREATOR }.thenBy { it.displayName })
            membersSnapshotLoaded = true
            publishCombinedCloudBoard()
        }

        ideasListener = householdRef.collection(IDEAS).addSnapshotListener { snapshots, error ->
            if (error != null) {
                reportSyncError(error.message ?: "Family idea sync failed.")
                return@addSnapshotListener
            }
            cloudIdeas = snapshots?.documents.orEmpty().mapNotNull(::ideaFromSnapshot)
                .sortedByDescending { it.idea.createdAtEpochMillis }
            lastIdeaFingerprints = cloudIdeas
                .filter { it.createdByUid == boundUid }
                .associate { it.idea.id to ideaFingerprint(it.idea) }
            ideasSnapshotLoaded = true
            publishCombinedCloudBoard()
        }

        votesListener = householdRef.collection(IDEA_VOTES).addSnapshotListener { snapshots, error ->
            if (error != null) {
                reportSyncError(error.message ?: "Family vote sync failed.")
                return@addSnapshotListener
            }
            cloudVotes = snapshots?.documents.orEmpty().mapNotNull(::voteFromSnapshot)
            lastOwnVoteKeys = cloudVotes
                .filter { it.createdByUid == boundUid }
                .map { voteKey(it.ideaId, it.voterUid) }
                .toSet()
            votesSnapshotLoaded = true
            publishCombinedCloudBoard()
        }

        progressListener = householdRef.collection(MEMBER_PROGRESS).addSnapshotListener { snapshots, error ->
            if (error != null) {
                reportSyncError(error.message ?: "Family progress sync failed.")
                return@addSnapshotListener
            }
            snapshots?.documents.orEmpty().forEach { document ->
                val payload = document.getString(FIELD_PAYLOAD_JSON).orEmpty()
                if (payload.isBlank()) return@forEach
                runCatching { json.decodeFromString<SharedMemberProgress>(payload) }
                    .onSuccess { progress ->
                        if (document.id == boundUid) lastProgressPayload = payload
                        companionHubRepository.applyCloudMemberProgress(progress)
                    }
            }
            progressSnapshotLoaded = true
        }

        workbookPagesListener = householdRef.collection(MEMBER_WORKBOOKS).document(boundUid)
            .collection(WORKBOOK_PAGES).addSnapshotListener { snapshots, error ->
                if (error != null) {
                    reportSyncError(error.message ?: "Interactive workbook sync failed.")
                    return@addSnapshotListener
                }
                val pagesByProfile = linkedMapOf<String, MutableMap<String, WorkbookPageProgress>>()
                val fingerprints = linkedMapOf<String, String>()
                snapshots?.documents.orEmpty().forEach { document ->
                    val profileUid = document.getString(FIELD_PROFILE_UID).orEmpty()
                    val pageKey = document.getString(FIELD_PAGE_KEY).orEmpty()
                    val payload = document.getString(FIELD_PAYLOAD_JSON).orEmpty()
                    if (profileUid.isBlank() || pageKey.isBlank() || payload.isBlank()) return@forEach
                    runCatching { json.decodeFromString<WorkbookPageProgress>(payload) }
                        .onSuccess { page ->
                            pagesByProfile.getOrPut(profileUid) { linkedMapOf() }[pageKey] = page
                            fingerprints[workbookFingerprintKey(profileUid, pageKey)] = sha256(payload)
                        }
                        .onFailure { reportSyncError("A saved interactive workbook page could not be read.") }
                }
                lastWorkbookPageFingerprints = fingerprints
                pagesByProfile.forEach { (profileUid, pages) ->
                    companionHubRepository.applyCloudWorkbookPages(profileUid, pages)
                }
                workbookPagesSnapshotLoaded = true
                scope.launch { uploadWorkbookPages(companionHubRepository.state.value) }
            }

        val worshipRef = householdRef.collection(FAMILY_WORSHIP).document(CURRENT_PLAN_DOC)
        worshipListener = worshipRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                reportSyncError(error.message ?: "Family Worship sync failed.")
                return@addSnapshotListener
            }
            val payload = snapshot?.getString(FIELD_PAYLOAD_JSON).orEmpty()
            if (payload.isBlank()) return@addSnapshotListener
            scope.launch {
                runCatching { json.decodeFromString<CloudFamilyWorshipPlan>(payload).toDomain() }
                    .onSuccess { studyRepository.replaceFamilyWorshipFromHousehold(it) }
                    .onFailure { reportSyncError("The shared Family Worship plan could not be read.") }
            }
        }

        scope.launch {
            val household = runCatching { householdRef.get().awaitTask() }.getOrNull()
            val familyName = household?.getString(FIELD_FAMILY_NAME).orEmpty().ifBlank { "My Family" }
            val ownerUid = household?.getString(FIELD_OWNER_UID).orEmpty()
            companionHubRepository.applyHouseholdIdentity(role.toBoardRole(), familyName, ownerUid)
        }
    }

    private suspend fun synchronizeLocalFamilyState(hubState: CompanionHubState) {
        if (!familySnapshotsReady()) return
        val db = firestore ?: return
        val householdId = mutableState.value.householdId
        if (householdId.isBlank() || boundUid.isBlank()) return
        val householdRef = db.collection(HOUSEHOLDS).document(householdId)
        val signedInProfile = signedInProfile(hubState)

        syncSignedInMember(householdRef, signedInProfile)

        if (mutableState.value.canManageFamilyWorship) {
            syncBoardConfig(householdRef, hubState.familyBoard)
        }

        val localProfileUids = hubState.localProfiles.map { it.uid }.toSet() + boundUid
        syncIdeas(householdRef, hubState.familyBoard, localProfileUids)
        syncVotes(householdRef, hubState.familyBoard, localProfileUids)
    }

    private suspend fun syncBoardConfig(
        householdRef: com.google.firebase.firestore.DocumentReference,
        board: FamilyWorshipBoard,
    ) {
        val config = CloudFamilyBoardConfig.fromBoard(board)
        val fingerprint = boardConfigFingerprint(config)
        if (fingerprint == lastBoardConfigPayload) return
        val persisted = config.copy(revision = System.currentTimeMillis())
        val payload = json.encodeToString(persisted)
        runCatching {
            householdRef.collection(SHARED).document(FAMILY_BOARD_DOC)
                .set(mapOf(
                    FIELD_PAYLOAD_JSON to payload,
                    FIELD_REVISION to persisted.revision,
                    FIELD_UPDATED_BY to boundUid,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ), SetOptions.merge())
                .awaitTask()
            householdRef.update(mapOf(
                FIELD_FAMILY_NAME to config.familyName,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            )).awaitTask()
        }.onSuccess {
            lastBoardConfigPayload = fingerprint
        }.onFailure { reportSyncError(it.message ?: "Family board changes could not be synchronized.") }
    }

    private suspend fun syncSignedInMember(
        householdRef: com.google.firebase.firestore.DocumentReference,
        profile: CompanionProfile,
    ) {
        val fingerprint = listOf(
            profile.displayName,
            profile.ageGroup.name,
            profile.googleConnected.toString(),
        ).joinToString("|")
        if (fingerprint == lastMemberFingerprint) return
        runCatching {
            householdRef.collection(MEMBERS).document(boundUid)
                .set(mapOf(
                    FIELD_DISPLAY_NAME to profile.displayName.trim().take(100).ifBlank { "Family Member" },
                    FIELD_AGE_GROUP to profile.ageGroup.name,
                    FIELD_GOOGLE_CONNECTED to true,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ), SetOptions.merge())
                .awaitTask()
        }.onSuccess {
            lastMemberFingerprint = fingerprint
        }.onFailure { reportSyncError(it.message ?: "Your household profile could not be synchronized.") }
    }

    private suspend fun syncIdeas(
        householdRef: com.google.firebase.firestore.DocumentReference,
        board: FamilyWorshipBoard,
        localProfileUids: Set<String>,
    ) {
        val ownedRemoteIds = cloudIdeas.filter { it.createdByUid == boundUid }.map { it.idea.id }.toSet()
        val desired = board.ideas.filter { idea ->
            idea.authorUid in localProfileUids &&
                (idea.id !in cloudIdeas.map { it.idea.id }.toSet() || idea.id in ownedRemoteIds)
        }
        desired.forEach { idea ->
            val fingerprint = ideaFingerprint(idea)
            if (lastIdeaFingerprints[idea.id] == fingerprint) return@forEach
            runCatching {
                householdRef.collection(IDEAS).document(idea.id)
                    .set(ideaDocument(idea), SetOptions.merge())
                    .awaitTask()
            }.onSuccess {
                lastIdeaFingerprints = lastIdeaFingerprints + (idea.id to fingerprint)
            }.onFailure { reportSyncError(it.message ?: "A Family Worship idea could not be synchronized.") }
        }
    }

    private suspend fun syncVotes(
        householdRef: com.google.firebase.firestore.DocumentReference,
        board: FamilyWorshipBoard,
        localProfileUids: Set<String>,
    ) {
        val desiredKeys = board.ideas.flatMap { idea ->
            idea.voterUids.filter { it in localProfileUids }.map { voterUid -> voteKey(idea.id, voterUid) }
        }.toSet()

        val additions = desiredKeys - lastOwnVoteKeys
        val removals = lastOwnVoteKeys - desiredKeys
        additions.forEach { key ->
            val (ideaId, voterUid) = splitVoteKey(key)
            runCatching {
                householdRef.collection(IDEA_VOTES).document(voteDocumentId(ideaId, voterUid, boundUid))
                    .set(mapOf(
                        FIELD_IDEA_ID to ideaId,
                        FIELD_VOTER_UID to voterUid,
                        FIELD_CREATED_BY_UID to boundUid,
                        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    ))
                    .awaitTask()
            }.onSuccess { lastOwnVoteKeys = lastOwnVoteKeys + key }
                .onFailure { reportSyncError(it.message ?: "A family vote could not be synchronized.") }
        }
        removals.forEach { key ->
            val (ideaId, voterUid) = splitVoteKey(key)
            runCatching {
                householdRef.collection(IDEA_VOTES).document(voteDocumentId(ideaId, voterUid, boundUid))
                    .delete().awaitTask()
            }.onSuccess { lastOwnVoteKeys = lastOwnVoteKeys - key }
                .onFailure { reportSyncError(it.message ?: "A family vote could not be removed.") }
        }
    }

    private suspend fun uploadMemberProgress(payload: String) {
        val db = firestore ?: return
        val householdId = mutableState.value.householdId
        if (householdId.isBlank() || !progressSnapshotLoaded || payload == lastProgressPayload) return
        runCatching {
            db.collection(HOUSEHOLDS).document(householdId)
                .collection(MEMBER_PROGRESS).document(boundUid)
                .set(mapOf(
                    FIELD_UID to boundUid,
                    FIELD_PAYLOAD_JSON to payload,
                    FIELD_REVISION to System.currentTimeMillis(),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ), SetOptions.merge())
                .awaitTask()
        }.onSuccess {
            lastProgressPayload = payload
        }.onFailure { reportSyncError(it.message ?: "Study progress could not be synchronized.") }
    }


    private suspend fun uploadWorkbookPages(hubState: CompanionHubState) {
        val db = firestore ?: return
        val householdId = mutableState.value.householdId
        if (householdId.isBlank() || boundUid.isBlank() || !workbookPagesSnapshotLoaded) return
        val profileUid = hubState.activeUid.ifBlank { hubState.profile.uid }.ifBlank { boundUid }
        val dirty = hubState.interactiveWorkbooks.pageProgress.mapNotNull { (pageKey, page) ->
            val payload = json.encodeToString(page)
            if (payload.length > MAX_WORKBOOK_PAGE_JSON) {
                reportSyncError("One interactive workbook page is too large to synchronize. Clear a little ink and try again.")
                return@mapNotNull null
            }
            val fingerprintKey = workbookFingerprintKey(profileUid, pageKey)
            val fingerprint = sha256(payload)
            if (lastWorkbookPageFingerprints[fingerprintKey] == fingerprint) null
            else WorkbookPageUpload(pageKey, payload, fingerprintKey, fingerprint)
        }
        if (dirty.isEmpty()) return
        runCatching {
            dirty.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { page ->
                    val ref = db.collection(HOUSEHOLDS).document(householdId)
                        .collection(MEMBER_WORKBOOKS).document(boundUid)
                        .collection(WORKBOOK_PAGES).document(workbookPageDocumentId(profileUid, page.pageKey))
                    batch.set(ref, mapOf(
                        FIELD_ACCOUNT_UID to boundUid,
                        FIELD_PROFILE_UID to profileUid,
                        FIELD_PAGE_KEY to page.pageKey,
                        FIELD_PAYLOAD_JSON to page.payload,
                        FIELD_REVISION to System.currentTimeMillis(),
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ), SetOptions.merge())
                }
                batch.commit().awaitTask()
            }
        }.onSuccess {
            lastWorkbookPageFingerprints = lastWorkbookPageFingerprints +
                dirty.associate { it.fingerprintKey to it.fingerprint }
        }.onFailure { reportSyncError(it.message ?: "Interactive workbook pages could not be synchronized.") }
    }

    private data class WorkbookPageUpload(
        val pageKey: String,
        val payload: String,
        val fingerprintKey: String,
        val fingerprint: String,
    )

    private fun workbookFingerprintKey(profileUid: String, pageKey: String): String =
        "$profileUid\u001f$pageKey"

    private fun workbookPageDocumentId(profileUid: String, pageKey: String): String =
        sha256("$profileUid\u001f$pageKey").take(48)

    private fun publishCombinedCloudBoard() {
        if (!familySnapshotsReady()) return
        val current = companionHubRepository.state.value
        val localProfileUids = current.localProfiles.map { it.uid }.toSet() + boundUid
        val localIdeasById = current.familyBoard.ideas.associateBy { it.id }
        val votesByIdea = cloudVotes.groupBy { it.ideaId }
            .mapValues { (_, records) -> records.map { it.voterUid }.toSet() }

        val remoteIdeas = cloudIdeas.map { record ->
            val local = localIdeasById[record.idea.id]
            val preferred = if (
                record.createdByUid == boundUid &&
                local != null &&
                local.authorUid in localProfileUids
            ) local else record.idea
            preferred.copy(
                voterUids = votesByIdea[record.idea.id].orEmpty() +
                    localIdeasById[record.idea.id]?.voterUids.orEmpty().filter { it in localProfileUids },
            )
        }
        val remoteIds = remoteIdeas.map { it.id }.toSet()
        val localOnlyIdeas = current.familyBoard.ideas.filter {
            it.id !in remoteIds && it.authorUid in localProfileUids
        }
        val ideas = (remoteIdeas + localOnlyIdeas).sortedByDescending { it.createdAtEpochMillis }
        companionHubRepository.applyCloudFamilyBoard(cloudBoardConfig.toBoard(cloudMembers, ideas))
    }

    private fun familySnapshotsReady(): Boolean =
        boardSnapshotLoaded && membersSnapshotLoaded && ideasSnapshotLoaded && votesSnapshotLoaded

    private fun memberFromSnapshot(snapshot: DocumentSnapshot): FamilyMemberProfile? {
        val uid = snapshot.getString(FIELD_UID).orEmpty().ifBlank { snapshot.id }
        if (uid.isBlank()) return null
        val displayName = snapshot.getString(FIELD_DISPLAY_NAME).orEmpty().ifBlank { "Family Member" }
        val ageGroup = runCatching {
            AgeGroup.valueOf(snapshot.getString(FIELD_AGE_GROUP).orEmpty())
        }.getOrDefault(AgeGroup.ADULT)
        return FamilyMemberProfile(
            uid = uid,
            displayName = displayName,
            ageGroup = ageGroup,
            role = snapshot.getString(FIELD_ROLE).toHouseholdRole().toBoardRole(),
            googleConnected = snapshot.getBoolean(FIELD_GOOGLE_CONNECTED) ?: true,
        ).also {
            if (uid == boundUid) {
                lastMemberFingerprint = listOf(
                    it.displayName,
                    it.ageGroup.name,
                    it.googleConnected.toString(),
                ).joinToString("|")
            }
        }
    }

    private fun ideaFromSnapshot(snapshot: DocumentSnapshot): CloudIdeaRecord? {
        val id = snapshot.getString(FIELD_ID).orEmpty().ifBlank { snapshot.id }
        val createdByUid = snapshot.getString(FIELD_CREATED_BY_UID).orEmpty()
        val officialUrl = snapshot.getString(FIELD_OFFICIAL_URL).orEmpty()
        if (id.isBlank() || createdByUid.isBlank()) return null
        if (officialUrl.isNotBlank() && !SpiritualSourcePolicy.isAllowed(officialUrl)) return null
        return CloudIdeaRecord(
            idea = FamilyWorshipIdea(
                id = id,
                authorUid = snapshot.getString(FIELD_AUTHOR_UID).orEmpty().ifBlank { createdByUid },
                authorName = snapshot.getString(FIELD_AUTHOR_NAME).orEmpty().ifBlank { "Family Member" },
                topic = snapshot.getString(FIELD_TOPIC).orEmpty(),
                reason = snapshot.getString(FIELD_REASON).orEmpty(),
                scripture = snapshot.getString(FIELD_SCRIPTURE).orEmpty(),
                officialUrl = officialUrl,
                voterUids = emptySet(),
                createdAtEpochMillis = snapshot.getLong(FIELD_CREATED_AT_EPOCH_MILLIS) ?: 0L,
                used = snapshot.getBoolean(FIELD_USED) ?: false,
            ),
            createdByUid = createdByUid,
        )
    }

    private fun voteFromSnapshot(snapshot: DocumentSnapshot): CloudVoteRecord? {
        val ideaId = snapshot.getString(FIELD_IDEA_ID).orEmpty()
        val voterUid = snapshot.getString(FIELD_VOTER_UID).orEmpty()
        val createdByUid = snapshot.getString(FIELD_CREATED_BY_UID).orEmpty()
        if (ideaId.isBlank() || voterUid.isBlank() || createdByUid.isBlank()) return null
        return CloudVoteRecord(ideaId, voterUid, createdByUid)
    }

    private fun signedInProfile(hubState: CompanionHubState): CompanionProfile =
        hubState.localProfiles.firstOrNull { it.uid == boundUid }
            ?.copy(googleConnected = true)
            ?: CompanionProfile(
                uid = boundUid,
                displayName = boundDisplayName.ifBlank { "Family Member" },
                ageGroup = AgeGroup.ADULT,
                role = mutableState.value.householdRole.toBoardRole(),
                googleConnected = true,
            )

    private fun memberDocument(
        profile: CompanionProfile,
        role: String,
        inviteCode: String,
        includeJoinedAt: Boolean,
    ): Map<String, Any> = buildMap {
        put(FIELD_UID, boundUid)
        put(FIELD_DISPLAY_NAME, profile.displayName.trim().take(100).ifBlank { "Family Member" })
        put(FIELD_AGE_GROUP, profile.ageGroup.name)
        put(FIELD_ROLE, role)
        put(FIELD_GOOGLE_CONNECTED, true)
        put(FIELD_INVITE_CODE, inviteCode)
        if (includeJoinedAt) put(FIELD_JOINED_AT, FieldValue.serverTimestamp())
        put(FIELD_UPDATED_AT, FieldValue.serverTimestamp())
    }

    private fun ideaDocument(idea: FamilyWorshipIdea): Map<String, Any> {
        val officialUrl = idea.officialUrl.trim().take(500)
        require(officialUrl.isBlank() || SpiritualSourcePolicy.isAllowed(officialUrl)) {
            "Only official JW.org or Watchtower Online Library links can be synchronized."
        }
        requireCloudDocumentKey(idea.id)
        requireCloudDocumentKey(idea.authorUid)
        return mapOf(
            FIELD_ID to idea.id,
            FIELD_CREATED_BY_UID to boundUid,
            FIELD_AUTHOR_UID to idea.authorUid,
            FIELD_AUTHOR_NAME to idea.authorName.trim().take(100).ifBlank { "Family Member" },
            FIELD_TOPIC to idea.topic.trim().replace(Regex("\\s+"), " ").take(160),
            FIELD_REASON to idea.reason.trim().take(300),
            FIELD_SCRIPTURE to idea.scripture.trim().take(80),
            FIELD_OFFICIAL_URL to officialUrl,
            FIELD_CREATED_AT_EPOCH_MILLIS to idea.createdAtEpochMillis,
            FIELD_USED to idea.used,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
    }


    private fun boardConfigFingerprint(config: CloudFamilyBoardConfig): String =
        json.encodeToString(config.copy(revision = 0L))

    private fun ideaFingerprint(idea: FamilyWorshipIdea): String = sha256(
        listOf(
            idea.id,
            idea.authorUid,
            idea.authorName,
            idea.topic,
            idea.reason,
            idea.scripture,
            idea.officialUrl,
            idea.createdAtEpochMillis.toString(),
            idea.used.toString(),
        ).joinToString("\u001f"),
    )

    private fun detachSnapshotRegistrationsOnly() {
        boardListener?.remove(); boardListener = null
        membersListener?.remove(); membersListener = null
        ideasListener?.remove(); ideasListener = null
        votesListener?.remove(); votesListener = null
        progressListener?.remove(); progressListener = null
        workbookPagesListener?.remove(); workbookPagesListener = null
        worshipListener?.remove(); worshipListener = null
    }

    private fun resetCloudSnapshotState() {
        boardSnapshotLoaded = false
        membersSnapshotLoaded = false
        ideasSnapshotLoaded = false
        votesSnapshotLoaded = false
        progressSnapshotLoaded = false
        workbookPagesSnapshotLoaded = false
        cloudBoardConfig = CloudFamilyBoardConfig()
        cloudMembers = emptyList()
        cloudIdeas = emptyList()
        cloudVotes = emptyList()
        lastBoardConfigPayload = ""
        lastMemberFingerprint = ""
        lastProgressPayload = ""
        lastWorkbookPageFingerprints = emptyMap()
        lastIdeaFingerprints = emptyMap()
        lastOwnVoteKeys = emptySet()
    }

    private fun detachCloudListeners() {
        detachSnapshotRegistrationsOnly()
        familyUploadJob?.cancel(); familyUploadJob = null
        progressUploadJob?.cancel(); progressUploadJob = null
        workbookUploadJob?.cancel(); workbookUploadJob = null
        resetCloudSnapshotState()
        boundUid = ""
        boundDisplayName = ""
    }

    private fun reportSyncError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message)
    }

    private fun invitationCode(): String = buildString {
        repeat(10) { append(INVITATION_ALPHABET[random.nextInt(INVITATION_ALPHABET.length)]) }
    }.chunked(5).joinToString("-")

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase operation failed."))
            }
        }
    }

    private fun String?.toHouseholdRole(): HouseholdRole = when (this?.lowercase()) {
        ROLE_OWNER -> HouseholdRole.OWNER
        ROLE_ORGANIZER -> HouseholdRole.ORGANIZER
        ROLE_MEMBER -> HouseholdRole.MEMBER
        else -> HouseholdRole.NONE
    }

    private fun HouseholdRole.toBoardRole(): FamilyBoardRole = when (this) {
        HouseholdRole.OWNER -> FamilyBoardRole.CREATOR
        HouseholdRole.ORGANIZER -> FamilyBoardRole.CO_ORGANIZER
        HouseholdRole.MEMBER, HouseholdRole.NONE -> FamilyBoardRole.MEMBER
    }

    private fun voteKey(ideaId: String, voterUid: String): String = "$ideaId\u001f$voterUid"

    private fun splitVoteKey(key: String): Pair<String, String> {
        val separator = key.indexOf('\u001f')
        require(separator > 0 && separator < key.lastIndex) { "Invalid vote synchronization key." }
        return key.substring(0, separator) to key.substring(separator + 1)
    }

    private fun voteDocumentId(ideaId: String, voterUid: String, accountUid: String): String =
        cloudVoteDocumentId(ideaId, voterUid, accountUid)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val USERS = "users"
        const val HOUSEHOLDS = "households"
        const val MEMBERS = "members"
        const val SHARED = "shared"
        const val IDEAS = "ideas"
        const val IDEA_VOTES = "ideaVotes"
        const val MEMBER_PROGRESS = "memberProgress"
        const val MEMBER_WORKBOOKS = "memberWorkbooks"
        const val WORKBOOK_PAGES = "pages"
        const val FAMILY_WORSHIP = "familyWorship"
        const val INVITATIONS = "householdInvites"
        const val FAMILY_BOARD_DOC = "familyBoard"
        const val CURRENT_PLAN_DOC = "current"

        const val FIELD_ID = "id"
        const val FIELD_UID = "uid"
        const val FIELD_ACCOUNT_UID = "accountUid"
        const val FIELD_PROFILE_UID = "profileUid"
        const val FIELD_PAGE_KEY = "pageKey"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_FAMILY_NAME = "familyName"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_AGE_GROUP = "ageGroup"
        const val FIELD_GOOGLE_CONNECTED = "googleConnected"
        const val FIELD_HOUSEHOLD_ID = "householdId"
        const val FIELD_ROLE = "role"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_UID = "createdByUid"
        const val FIELD_AUTHOR_UID = "authorUid"
        const val FIELD_AUTHOR_NAME = "authorName"
        const val FIELD_TOPIC = "topic"
        const val FIELD_REASON = "reason"
        const val FIELD_SCRIPTURE = "scripture"
        const val FIELD_OFFICIAL_URL = "officialUrl"
        const val FIELD_CREATED_AT_EPOCH_MILLIS = "createdAtEpochMillis"
        const val FIELD_USED = "used"
        const val FIELD_IDEA_ID = "ideaId"
        const val FIELD_VOTER_UID = "voterUid"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_JOINED_AT = "joinedAt"
        const val FIELD_INVITE_CODE = "inviteCode"
        const val FIELD_EXPIRES_AT_EPOCH_SECONDS = "expiresAtEpochSeconds"
        const val FIELD_EXPIRES_AT = "expiresAt"
        const val FIELD_STATUS = "status"
        const val FIELD_USED_BY = "usedBy"
        const val FIELD_USED_AT = "usedAt"
        const val FIELD_PAYLOAD_JSON = "payloadJson"
        const val FIELD_REVISION = "revision"
        const val MAX_WORKBOOK_PAGE_JSON = 700_000
        const val FIELD_UPDATED_BY = "updatedBy"

        const val ROLE_OWNER = "owner"
        const val ROLE_ORGANIZER = "organizer"
        const val ROLE_MEMBER = "member"
        const val STATUS_ACTIVE = "active"
        const val STATUS_USED = "used"
        const val INVITATION_TTL_SECONDS = 86_400L
        const val MAX_INVITATION_ATTEMPTS = 5
        const val INVITATION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

internal fun cloudVoteDocumentId(ideaId: String, voterUid: String, accountUid: String): String {
    val account = requireCloudDocumentKey(accountUid)
    val idea = requireCloudDocumentKey(ideaId)
    val voter = requireCloudDocumentKey(voterUid)
    return "vote-$account-$idea-$voter"
}

internal fun requireCloudDocumentKey(value: String): String {
    require(value.length in 1..160 && CLOUD_DOCUMENT_KEY.matches(value)) {
        "Cloud identifiers may contain only letters, numbers, period, underscore, colon, at-sign, and hyphen."
    }
    return value
}

private val CLOUD_DOCUMENT_KEY = Regex("[A-Za-z0-9._:@-]+")

internal fun normalizeHouseholdInvitationCode(value: String): String {
    val normalized = value.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "")
    require(normalized.length in 6..32) { "Enter a valid household invitation code." }
    return normalized
}
