package com.mystudycompanion.app.companion

import android.content.Context
import com.mystudycompanion.app.auth.AccountAgeGroup
import com.mystudycompanion.app.auth.AccountAgeSource
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.HouseholdRole
import com.mystudycompanion.app.auth.UserAccount
import com.mystudycompanion.app.data.SpiritualSourcePolicy
import com.mystudycompanion.app.family.FamilyWorshipReminderScheduler
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CompanionHubRepository(
    context: Context,
    private val currentEventsRepository: CurrentEventsRepository = CurrentEventsRepository(),
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutableState = MutableStateFlow(loadState())
    val state: StateFlow<CompanionHubState> = mutableState.asStateFlow()

    fun bindAccount(account: UserAccount) {
        val current = mutableState.value.rememberActiveProgress()
        val existing = current.localProfiles.firstOrNull { it.uid == account.uid }
        val inferredRole = when {
            existing != null -> existing.role
            account.provider == AccountProvider.PRIVATE_OWNER || account.householdRole == HouseholdRole.OWNER -> FamilyBoardRole.CREATOR
            account.householdRole == HouseholdRole.ORGANIZER -> FamilyBoardRole.CO_ORGANIZER
            else -> FamilyBoardRole.MEMBER
        }
        val inferredAge = inferAgeProfile(account, existing)
        val profile = (existing ?: CompanionProfile(uid = account.uid)).copy(
            uid = account.uid,
            displayName = account.displayName.ifBlank { "Family Member" },
            ageGroup = inferredAge.first,
            ageSource = inferredAge.second,
            role = inferredRole,
            googleConnected = account.provider == AccountProvider.GOOGLE,
        )
        val profiles = (current.localProfiles.filterNot { it.uid == profile.uid } + profile)
            .sortedWith(compareBy<CompanionProfile> { it.role != FamilyBoardRole.CREATOR }.thenBy { it.displayName })
        val shouldOwnLocalBoard = profile.role == FamilyBoardRole.CREATOR
        val boardBase = if (current.familyBoard.creatorUid.isBlank() && shouldOwnLocalBoard) {
            current.familyBoard.copy(
                creatorUid = profile.uid,
                inviteCode = current.familyBoard.inviteCode.ifBlank(::inviteCode),
            )
        } else current.familyBoard
        val board = ensureMember(boardBase, profile)
        commit(
            current.copy(
                activeUid = profile.uid,
                profile = profile,
                localProfiles = profiles,
                familyBoard = board,
                bibleProgress = current.bibleProgressByUid[profile.uid] ?: current.bibleProgress,
                completedActivityIds = current.completedActivityIdsByUid[profile.uid].orEmpty(),
                completedStudyPlanIds = current.completedStudyPlanIdsByUid[profile.uid].orEmpty(),
                eventNotebooks = (current.eventNotebooksByUid[profile.uid] ?: current.eventNotebooks).withSharedEvents(board.spiritualEvents),
                interactiveWorkbooks = current.interactiveWorkbooksByUid[profile.uid] ?: current.interactiveWorkbooks,
            ).withFreshPlans(),
        )
    }

    private fun inferAgeProfile(
        account: UserAccount,
        existing: CompanionProfile?,
    ): Pair<AgeGroup, ProfileAgeSource> {
        if (existing?.ageSource == ProfileAgeSource.USER_CONFIRMED) {
            return existing.ageGroup to existing.ageSource
        }
        if (account.provider == AccountProvider.PRIVATE_OWNER) {
            return AgeGroup.ADULT to ProfileAgeSource.PRIVATE_OWNER
        }
        val group = when (account.ageGroup) {
            AccountAgeGroup.CHILD -> AgeGroup.CHILD
            AccountAgeGroup.PRETEEN -> AgeGroup.PRETEEN
            AccountAgeGroup.TEEN -> AgeGroup.TEEN
            AccountAgeGroup.ADULT -> AgeGroup.ADULT
            AccountAgeGroup.MINOR_UNKNOWN -> existing?.ageGroup?.takeIf { it != AgeGroup.ADULT } ?: AgeGroup.PRETEEN
            AccountAgeGroup.UNKNOWN -> existing?.ageGroup ?: AgeGroup.ADULT
        }
        val source = when (account.ageSource) {
            AccountAgeSource.GOOGLE_BIRTHDAY -> ProfileAgeSource.GOOGLE_BIRTHDAY
            AccountAgeSource.GOOGLE_AGE_RANGE -> ProfileAgeSource.GOOGLE_AGE_RANGE
            AccountAgeSource.PRIVATE_OWNER -> ProfileAgeSource.PRIVATE_OWNER
            AccountAgeSource.USER_CONFIRMED -> ProfileAgeSource.USER_CONFIRMED
            AccountAgeSource.UNAVAILABLE -> existing?.ageSource ?: ProfileAgeSource.UNCONFIRMED
        }
        return group to source
    }

    fun updateProfile(
        displayName: String,
        ageGroup: AgeGroup,
        territory: String,
        conductsFieldService: Boolean,
        conductorDays: Set<String>,
        preferJwLibrary: Boolean,
    ) {
        val current = mutableState.value
        val profile = current.profile.copy(
            displayName = displayName.trim().ifBlank { current.profile.displayName },
            ageGroup = ageGroup,
            ageSource = ProfileAgeSource.USER_CONFIRMED,
            territory = territory.trim().take(100),
            conductsFieldService = conductsFieldService,
            conductorDays = conductorDays,
            preferJwLibrary = preferJwLibrary,
        )
        val profiles = current.localProfiles.map { if (it.uid == profile.uid) profile else it }
        val board = ensureMember(current.familyBoard, profile)
        commit(current.copy(profile = profile, localProfiles = profiles, familyBoard = board).withFreshPlans())
    }

    fun confirmProfileAge(ageGroup: AgeGroup) {
        val current = mutableState.value
        val profile = current.profile.copy(
            ageGroup = ageGroup,
            ageSource = ProfileAgeSource.USER_CONFIRMED,
        )
        val profiles = current.localProfiles.map { if (it.uid == profile.uid) profile else it }
        val board = ensureMember(current.familyBoard, profile)
        commit(current.copy(profile = profile, localProfiles = profiles, familyBoard = board).withFreshPlans())
    }

    fun addLocalProfile(name: String, ageGroup: AgeGroup) {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(50)
        if (clean.length < 2) return
        val uid = "local-${UUID.randomUUID()}"
        val profile = CompanionProfile(
            uid = uid,
            displayName = clean,
            ageGroup = ageGroup,
            ageSource = ProfileAgeSource.USER_CONFIRMED,
            role = FamilyBoardRole.MEMBER,
            preferJwLibrary = true,
        )
        val current = mutableState.value
        commit(
            current.copy(
                localProfiles = current.localProfiles + profile,
                familyBoard = ensureMember(current.familyBoard, profile),
            ),
        )
    }

    fun switchProfile(uid: String) {
        val current = mutableState.value.rememberActiveProgress()
        val profile = current.localProfiles.firstOrNull { it.uid == uid } ?: return
        commit(
            current.copy(
                activeUid = uid,
                profile = profile,
                bibleProgress = current.bibleProgressByUid[uid] ?: BiblePlanProgress(),
                completedActivityIds = current.completedActivityIdsByUid[uid].orEmpty(),
                completedStudyPlanIds = current.completedStudyPlanIdsByUid[uid].orEmpty(),
                eventNotebooks = (current.eventNotebooksByUid[uid] ?: EventNotebookState()).withSharedEvents(current.familyBoard.spiritualEvents),
                interactiveWorkbooks = current.interactiveWorkbooksByUid[uid] ?: InteractiveWorkbookState(),
            ).withFreshPlans(),
        )
    }

    fun renameFamily(name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(60)
        if (clean.isBlank()) return
        commit(mutableState.value.copy(familyBoard = mutableState.value.familyBoard.copy(familyName = clean)))
    }

    fun submitIdea(topic: String, reason: String, scripture: String, officialUrl: String) {
        val cleanTopic = topic.trim().replace(Regex("\\s+"), " ").take(160)
        val cleanOfficialUrl = officialUrl.trim().take(500)
        if (cleanTopic.length < 3) return
        if (cleanOfficialUrl.isNotBlank() && !SpiritualSourcePolicy.isAllowed(cleanOfficialUrl)) return
        val current = mutableState.value
        val idea = FamilyWorshipIdea(
            id = UUID.randomUUID().toString(),
            authorUid = current.profile.uid,
            authorName = current.profile.displayName,
            topic = cleanTopic,
            reason = reason.trim().take(300),
            scripture = scripture.trim().take(80),
            officialUrl = cleanOfficialUrl,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        commit(current.copy(familyBoard = current.familyBoard.copy(ideas = listOf(idea) + current.familyBoard.ideas)))
    }

    fun toggleIdeaVote(ideaId: String) {
        val current = mutableState.value
        val voter = current.profile.uid
        if (voter.isBlank()) return
        val ideas = current.familyBoard.ideas.map { idea ->
            if (idea.id != ideaId) idea
            else if (voter in idea.voterUids) idea.copy(voterUids = idea.voterUids - voter)
            else idea.copy(voterUids = idea.voterUids + voter)
        }
        commit(current.copy(familyBoard = current.familyBoard.copy(ideas = ideas)))
    }

    fun selectFamilyWorshipIdea(ideaId: String?) {
        if (!canOrganize()) return
        val current = mutableState.value
        val valid = ideaId?.takeIf { id -> current.familyBoard.ideas.any { it.id == id } }
        commit(current.copy(familyBoard = current.familyBoard.copy(selectedIdeaId = valid, selectedCustomTopic = "")))
    }

    fun selectCustomFamilyWorshipTopic(topic: String) {
        if (!canOrganize()) return
        val clean = topic.trim().replace(Regex("\\s+"), " ").take(180)
        commit(mutableState.value.copy(familyBoard = mutableState.value.familyBoard.copy(
            selectedIdeaId = null,
            selectedCustomTopic = clean,
        )))
    }

    fun scheduleFamilyWorship(dateIso: String, time24h: String, durationMinutes: Int, recurringWeekly: Boolean) {
        if (!canOrganize()) return
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return
        val time = time24h.takeIf { it.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d")) } ?: return
        val current = mutableState.value
        commit(current.copy(familyBoard = current.familyBoard.copy(
            scheduledDateIso = date.toString(),
            scheduledTime24h = time,
            durationMinutes = durationMinutes.coerceIn(15, 180),
            recurringWeekly = recurringWeekly,
        )))
        FamilyWorshipReminderScheduler.schedule(appContext, date.toString(), time, recurringWeekly)
    }

    fun setBibleMode(mode: BiblePlanMode) {
        val current = mutableState.value
        commit(current.copy(bibleProgress = current.bibleProgress.copy(mode = mode)).withFreshPlans())
    }

    fun selectJourney(journeyId: String) {
        if (BibleJourneyCatalog.journeys.none { it.id == journeyId }) return
        val current = mutableState.value
        commit(current.copy(bibleProgress = current.bibleProgress.copy(
            mode = BiblePlanMode.STORY_JOURNEYS,
            activeJourneyId = journeyId,
            activeJourneyDayIndex = 0,
        )).withFreshPlans())
    }

    fun setCanonicalPace(days: Int) {
        val current = mutableState.value
        commit(current.copy(bibleProgress = current.bibleProgress.copy(
            mode = BiblePlanMode.GENESIS_TO_REVELATION,
            canonicalPaceDays = days.coerceIn(90, 1_200),
            canonicalDayIndex = 0,
        )).withFreshPlans())
    }

    fun completeCurrentReading() {
        val current = mutableState.value
        val reading = YouthStudyPlanner.currentReading(current.bibleProgress)
        val key = readingKey(current.bibleProgress, reading)
        var progress = current.bibleProgress.copy(completedReadingKeys = current.bibleProgress.completedReadingKeys + key)
        progress = when (progress.mode) {
            BiblePlanMode.STORY_JOURNEYS -> {
                val journey = BibleJourneyCatalog.journeys.firstOrNull { it.id == progress.activeJourneyId }
                val max = journey?.days?.lastIndex ?: 0
                progress.copy(activeJourneyDayIndex = (progress.activeJourneyDayIndex + 1).coerceAtMost(max))
            }
            BiblePlanMode.GENESIS_TO_REVELATION -> {
                val max = BibleJourneyCatalog.canonicalPlan(progress.canonicalPaceDays).lastIndex
                progress.copy(canonicalDayIndex = (progress.canonicalDayIndex + 1).coerceAtMost(max))
            }
        }
        commit(current.copy(bibleProgress = progress).withFreshPlans())
    }

    fun moveReading(delta: Int) {
        val current = mutableState.value
        val progress = when (current.bibleProgress.mode) {
            BiblePlanMode.STORY_JOURNEYS -> {
                val max = BibleJourneyCatalog.journeys.firstOrNull { it.id == current.bibleProgress.activeJourneyId }
                    ?.days?.lastIndex ?: 0
                current.bibleProgress.copy(
                    activeJourneyDayIndex = (current.bibleProgress.activeJourneyDayIndex + delta).coerceIn(0, max),
                )
            }
            BiblePlanMode.GENESIS_TO_REVELATION -> {
                val max = BibleJourneyCatalog.canonicalPlan(current.bibleProgress.canonicalPaceDays).lastIndex
                current.bibleProgress.copy(
                    canonicalDayIndex = (current.bibleProgress.canonicalDayIndex + delta).coerceIn(0, max),
                )
            }
        }
        commit(current.copy(bibleProgress = progress).withFreshPlans())
    }

    fun configureEvent(
        eventType: SpiritualEventType,
        startDateIso: String,
        endDateIso: String,
        location: String,
        requestedProgramId: String = "",
    ) {
        val startDate = runCatching { LocalDate.parse(startDateIso) }.getOrNull() ?: return
        val endDate = runCatching { LocalDate.parse(endDateIso.ifBlank { startDateIso }) }.getOrNull() ?: startDate
        if (endDate < startDate) return
        val program = OfficialEventProgramCatalog.byId(requestedProgramId)
            ?.takeIf { it.eventType == eventType }
            ?: OfficialEventProgramCatalog.recommended(eventType, startDate)
        val programId = program?.id.orEmpty()
        val eventId = "event-${eventType.name.lowercase()}"
        val event = ConfiguredSpiritualEvent(
            id = eventId,
            eventType = eventType,
            programId = programId,
            startDateIso = startDate.toString(),
            endDateIso = endDate.toString(),
            location = location.trim().replace(Regex("\\s+"), " ").take(120),
        )
        val current = mutableState.value
        val sharedEvents = current.familyBoard.spiritualEvents.filterNot { it.id == eventId } + event
        val notebook = current.eventNotebooks.copy(
            events = sharedEvents,
            selectedEventId = eventId,
        )
        commit(
            current.copy(
                familyBoard = current.familyBoard.copy(spiritualEvents = sharedEvents),
                eventNotebooks = notebook,
            ),
        )
    }

    fun selectEvent(eventId: String) {
        val current = mutableState.value
        if (current.eventNotebooks.events.none { it.id == eventId }) return
        commit(current.copy(eventNotebooks = current.eventNotebooks.copy(selectedEventId = eventId)))
    }

    fun setNotebookAudience(audience: NotebookAudience) {
        val current = mutableState.value
        commit(current.copy(eventNotebooks = current.eventNotebooks.copy(audience = audience)))
    }

    fun toggleNotebookPrompt(promptId: String) {
        val current = mutableState.value
        val next = if (promptId in current.eventNotebooks.completedPromptIds) {
            current.eventNotebooks.completedPromptIds - promptId
        } else {
            current.eventNotebooks.completedPromptIds + promptId
        }
        commit(current.copy(eventNotebooks = current.eventNotebooks.copy(completedPromptIds = next)))
    }

    fun saveNotebookTalkNote(talkKey: String, note: String) {
        val current = mutableState.value
        val clean = note.trim().take(2_000)
        val notes = if (clean.isBlank()) current.eventNotebooks.notesByTalkKey - talkKey
        else current.eventNotebooks.notesByTalkKey + (talkKey to clean)
        commit(current.copy(eventNotebooks = current.eventNotebooks.copy(notesByTalkKey = notes)))
    }

    fun setActiveWorkbook(bookId: String, pageKey: String) {
        if (bookId.isBlank() || pageKey.isBlank()) return
        val current = mutableState.value
        val now = System.currentTimeMillis()
        val safeKey = pageKey.take(240)
        val existing = current.interactiveWorkbooks.pageProgress[safeKey] ?: WorkbookPageProgress()
        val opened = existing.copy(
            startedAtEpochMillis = existing.startedAtEpochMillis.takeIf { it > 0L } ?: now,
            lastOpenedAtEpochMillis = now,
        )
        val pages = (current.interactiveWorkbooks.pageProgress + (safeKey to opened))
            .entries.sortedByDescending { maxOf(it.value.updatedAtEpochMillis, it.value.lastOpenedAtEpochMillis) }
            .take(160).associate { it.toPair() }
        commit(current.copy(interactiveWorkbooks = current.interactiveWorkbooks.copy(
            activeBookId = bookId.take(180),
            activePageKey = safeKey,
            pageProgress = pages,
        )))
    }

    fun setWorkbookDifficulty(adjustment: Int) {
        val current = mutableState.value
        commit(current.copy(interactiveWorkbooks = current.interactiveWorkbooks.copy(
            difficultyAdjustment = adjustment.coerceIn(-1, 1),
        )))
    }

    fun toggleWorkbookCheck(pageKey: String, checkId: String) = updateWorkbookPage(pageKey) { page ->
        val id = checkId.take(160)
        page.copy(checkedIds = if (id in page.checkedIds) page.checkedIds - id else page.checkedIds + id)
    }

    fun setWorkbookText(pageKey: String, fieldId: String, value: String) = updateWorkbookPage(pageKey) { page ->
        val id = fieldId.take(160)
        val clean = value.take(3_000)
        page.copy(textAnswers = if (clean.isBlank()) page.textAnswers - id else page.textAnswers + (id to clean))
    }

    fun setWorkbookLetter(pageKey: String, cellId: String, value: String) = updateWorkbookPage(pageKey) { page ->
        val id = cellId.take(180)
        val clean = value.uppercase().filter(Char::isLetter).take(1)
        page.copy(letterAnswers = if (clean.isBlank()) page.letterAnswers - id else page.letterAnswers + (id to clean))
    }

    fun selectWorkbookColorNumber(pageKey: String, activityId: String, number: Int) = updateWorkbookPage(pageKey) { page ->
        val id = activityId.take(160)
        page.copy(selectedColorNumbers = page.selectedColorNumbers + (id to number.coerceIn(1, 12)))
    }

    fun applyWorkbookColor(
        pageKey: String,
        activityId: String,
        regionId: String,
        expectedNumber: Int,
        colorArgb: Long,
    ): Boolean {
        var applied = false
        updateWorkbookPage(pageKey) { page ->
            val safeActivity = activityId.take(160)
            val selected = page.selectedColorNumbers[safeActivity] ?: expectedNumber
            if (selected != expectedNumber) return@updateWorkbookPage page
            val key = regionId.take(160)
            val before = page.selectedColors[key]
            if (before == colorArgb) return@updateWorkbookPage page
            applied = true
            val change = WorkbookColorChange(safeActivity, key, before, colorArgb)
            page.copy(
                selectedColors = page.selectedColors + (key to colorArgb),
                colorUndo = (page.colorUndo + change).takeLast(80),
                colorRedo = emptyList(),
            )
        }
        return applied
    }

    fun undoWorkbookColor(pageKey: String, activityId: String) = updateWorkbookPage(pageKey) { page ->
        val safeActivity = activityId.take(160)
        val index = page.colorUndo.indexOfLast { it.activityId == safeActivity }
        if (index < 0) return@updateWorkbookPage page
        val change = page.colorUndo[index]
        val colors = if (change.beforeColorArgb == null) page.selectedColors - change.regionKey
        else page.selectedColors + (change.regionKey to change.beforeColorArgb)
        page.copy(
            selectedColors = colors,
            colorUndo = page.colorUndo.toMutableList().also { it.removeAt(index) },
            colorRedo = (page.colorRedo + change).takeLast(80),
        )
    }

    fun redoWorkbookColor(pageKey: String, activityId: String) = updateWorkbookPage(pageKey) { page ->
        val safeActivity = activityId.take(160)
        val index = page.colorRedo.indexOfLast { it.activityId == safeActivity }
        if (index < 0) return@updateWorkbookPage page
        val change = page.colorRedo[index]
        val colors = if (change.afterColorArgb == null) page.selectedColors - change.regionKey
        else page.selectedColors + (change.regionKey to change.afterColorArgb)
        page.copy(
            selectedColors = colors,
            colorRedo = page.colorRedo.toMutableList().also { it.removeAt(index) },
            colorUndo = (page.colorUndo + change).takeLast(80),
        )
    }

    fun resetWorkbookColors(pageKey: String, activityId: String) = updateWorkbookPage(pageKey) { page ->
        val prefix = "${activityId.take(150)}:"
        page.copy(
            selectedColors = page.selectedColors.filterKeys { !it.startsWith(prefix) },
            colorUndo = page.colorUndo.filterNot { it.activityId == activityId },
            colorRedo = page.colorRedo.filterNot { it.activityId == activityId },
        )
    }

    fun setWorkbookColor(pageKey: String, regionId: String, colorArgb: Long) = updateWorkbookPage(pageKey) { page ->
        page.copy(selectedColors = page.selectedColors + (regionId.take(160) to colorArgb))
    }

    fun setWorkbookMatch(pageKey: String, left: String, right: String) = updateWorkbookPage(pageKey) { page ->
        page.copy(matches = page.matches + (left.take(160) to right.take(180)))
    }

    fun setWorkbookPuzzleFound(pageKey: String, puzzleId: String, found: Boolean) = updateWorkbookPage(pageKey) { page ->
        val id = puzzleId.take(180)
        page.copy(foundPuzzleIds = if (found) page.foundPuzzleIds + id else page.foundPuzzleIds - id)
    }

    fun clearWorkbookPuzzle(pageKey: String, activityId: String) = updateWorkbookPage(pageKey) { page ->
        val prefix = activityId.take(140)
        page.copy(
            letterAnswers = page.letterAnswers.filterKeys { !it.startsWith(prefix) },
            foundPuzzleIds = page.foundPuzzleIds.filterNot { it.startsWith(prefix) }.toSet(),
        )
    }

    fun setWorkbookDrawingStep(pageKey: String, activityId: String, step: Int) = updateWorkbookPage(pageKey) { page ->
        page.copy(drawingSteps = page.drawingSteps + (activityId.take(160) to step.coerceIn(0, 12)))
    }

    fun setWorkbookInkTool(pageKey: String, activityId: String, tool: WorkbookInkTool) = updateWorkbookPage(pageKey) { page ->
        page.copy(inkTools = page.inkTools + (activityId.take(160) to tool))
    }

    fun setWorkbookInkColor(pageKey: String, activityId: String, colorArgb: Long) = updateWorkbookPage(pageKey) { page ->
        page.copy(inkColors = page.inkColors + (activityId.take(160) to colorArgb))
    }

    fun addWorkbookStroke(pageKey: String, stroke: WorkbookStroke) = updateWorkbookPage(pageKey) { page ->
        val safe = stroke.copy(
            id = stroke.id.take(80),
            width = stroke.width.coerceIn(2, 32),
            encodedPoints = stroke.encodedPoints.take(3_500),
        )
        page.copy(strokes = (page.strokes + safe).takeLast(80), redoStrokes = emptyList())
    }

    fun undoWorkbookStroke(pageKey: String) = updateWorkbookPage(pageKey) { page ->
        val last = page.strokes.lastOrNull() ?: return@updateWorkbookPage page
        page.copy(strokes = page.strokes.dropLast(1), redoStrokes = (page.redoStrokes + last).takeLast(80))
    }

    fun redoWorkbookStroke(pageKey: String) = updateWorkbookPage(pageKey) { page ->
        val last = page.redoStrokes.lastOrNull() ?: return@updateWorkbookPage page
        page.copy(strokes = (page.strokes + last).takeLast(80), redoStrokes = page.redoStrokes.dropLast(1))
    }

    fun clearWorkbookInk(pageKey: String) = updateWorkbookPage(pageKey) { page ->
        page.copy(strokes = emptyList(), redoStrokes = page.strokes.takeLast(80).reversed())
    }

    fun resetWorkbookPage(pageKey: String) {
        if (pageKey.isBlank()) return
        val current = mutableState.value
        val safeKey = pageKey.take(240)
        commit(current.copy(interactiveWorkbooks = current.interactiveWorkbooks.copy(
            pageProgress = current.interactiveWorkbooks.pageProgress - safeKey,
            activePageKey = current.interactiveWorkbooks.activePageKey.takeUnless { it == safeKey }.orEmpty(),
        )))
    }

    fun toggleWorkbookPageComplete(pageKey: String) = updateWorkbookPage(pageKey) { page ->
        page.copy(completed = !page.completed)
    }

    fun replaceWorkbookPageProgress(pageKey: String, progress: WorkbookPageProgress) {
        updateWorkbookPage(pageKey) { progress.copy(
            textAnswers = progress.textAnswers.mapValues { it.value.take(3_000) }.entries.take(32).associate { it.toPair() },
            letterAnswers = progress.letterAnswers.entries.take(80).associate { it.toPair() },
            selectedColors = progress.selectedColors.entries.take(96).associate { it.toPair() },
            selectedColorNumbers = progress.selectedColorNumbers.entries.take(24).associate { it.toPair() },
            colorUndo = progress.colorUndo.takeLast(80),
            colorRedo = progress.colorRedo.takeLast(80),
            matches = progress.matches.entries.take(24).associate { it.toPair() },
            foundPuzzleIds = progress.foundPuzzleIds.map { it.take(180) }.take(64).toSet(),
            drawingSteps = progress.drawingSteps.entries.take(24).associate { it.toPair() },
            inkTools = progress.inkTools.entries.take(24).associate { it.toPair() },
            inkColors = progress.inkColors.entries.take(24).associate { it.toPair() },
            strokes = progress.strokes.takeLast(80),
            redoStrokes = progress.redoStrokes.takeLast(80),
        ) }
    }

    private fun updateWorkbookPage(
        pageKey: String,
        transform: (WorkbookPageProgress) -> WorkbookPageProgress,
    ) {
        if (pageKey.isBlank()) return
        val current = mutableState.value
        val existing = current.interactiveWorkbooks.pageProgress[pageKey] ?: WorkbookPageProgress()
        val now = System.currentTimeMillis()
        val updated = transform(existing).copy(
            startedAtEpochMillis = existing.startedAtEpochMillis.takeIf { it > 0L } ?: now,
            lastOpenedAtEpochMillis = maxOf(existing.lastOpenedAtEpochMillis, now),
            updatedAtEpochMillis = now,
        )
        val pages = (current.interactiveWorkbooks.pageProgress + (pageKey.take(240) to updated))
            .entries.sortedByDescending { it.value.updatedAtEpochMillis }.take(160).associate { it.toPair() }
        commit(current.copy(interactiveWorkbooks = current.interactiveWorkbooks.copy(pageProgress = pages)))
    }

    fun toggleActivity(activityId: String) {
        val current = mutableState.value
        val next = if (activityId in current.completedActivityIds) current.completedActivityIds - activityId
        else current.completedActivityIds + activityId
        commit(current.copy(completedActivityIds = next))
    }

    fun togglePlanComplete(planId: String) {
        val current = mutableState.value
        val next = if (planId in current.completedStudyPlanIds) current.completedStudyPlanIds - planId
        else current.completedStudyPlanIds + planId
        commit(current.copy(completedStudyPlanIds = next))
    }

    suspend fun refreshAwareness() {
        val current = mutableState.value
        commit(current.copy(ministry = current.ministry.copy(refreshingAwareness = true, awarenessError = null)))
        runCatching { currentEventsRepository.load(current.profile.territory) }
            .onSuccess { headlines ->
                val latest = mutableState.value
                commit(latest.copy(ministry = latest.ministry.copy(
                    headlines = headlines,
                    lastAwarenessRefreshEpochMillis = System.currentTimeMillis(),
                    refreshingAwareness = false,
                    awarenessError = if (headlines.isEmpty()) "No current headlines were returned. You can still generate a ministry outline." else null,
                )))
            }
            .onFailure { error ->
                val latest = mutableState.value
                commit(latest.copy(ministry = latest.ministry.copy(
                    refreshingAwareness = false,
                    awarenessError = error.message ?: "Current territory awareness could not be refreshed.",
                )))
            }
    }

    fun generateMinistryOutline(headlineIndex: Int? = null) {
        val current = mutableState.value
        val rotation = ministryTopics[current.ministry.topicRotationIndex % ministryTopics.size]
        val headline = headlineIndex?.let { current.ministry.headlines.getOrNull(it) }
            ?: current.ministry.headlines.firstOrNull()
        val context = headline?.title.orEmpty()
        val concern = if (context.isBlank()) rotation.concern else inferConcern(context, rotation.concern)
        val outline = MinistryOutline(
            id = UUID.randomUUID().toString(),
            title = rotation.title,
            territoryConcern = concern,
            scriptureReference = rotation.scripture,
            questions = rotation.questions,
            tryToday = rotation.tryToday,
            scenario = rotation.scenario,
            officialUrl = JwLibraryLinkResolver.bibleUrl(rotation.scripture),
            generatedAtEpochMillis = System.currentTimeMillis(),
            currentEventContext = context,
        )
        commit(current.copy(ministry = current.ministry.copy(
            topicRotationIndex = current.ministry.topicRotationIndex + 1,
            currentOutline = outline,
        )))
    }

    fun clearAwarenessError() {
        val current = mutableState.value
        commit(current.copy(ministry = current.ministry.copy(awarenessError = null)))
    }

    fun canOrganize(): Boolean = mutableState.value.profile.role in setOf(
        FamilyBoardRole.CREATOR,
        FamilyBoardRole.CO_ORGANIZER,
    )

    fun selectedFamilyWorshipTopic(): String {
        val board = mutableState.value.familyBoard
        return board.selectedIdeaId?.let { id -> board.ideas.firstOrNull { it.id == id }?.topic }
            ?: board.selectedCustomTopic
    }

    fun sharedMemberProgress(): SharedMemberProgress {
        val current = mutableState.value
        return SharedMemberProgress(
            uid = current.activeUid.ifBlank { current.profile.uid },
            bibleProgress = current.bibleProgress,
            completedActivityIds = current.completedActivityIds,
            completedStudyPlanIds = current.completedStudyPlanIds,
            eventNotebooks = current.eventNotebooks,
            // Workbook pages are synchronized as one Firestore document per page.
            // Keep only the small active-book metadata in the general member-progress payload.
            interactiveWorkbooks = current.interactiveWorkbooks.copy(pageProgress = emptyMap()),
        )
    }

    fun applyCloudMemberProgress(progress: SharedMemberProgress) {
        if (progress.uid.isBlank()) return
        val current = mutableState.value
        val localWorkbook = current.interactiveWorkbooksByUid[progress.uid]
            ?: if (current.activeUid == progress.uid) current.interactiveWorkbooks else InteractiveWorkbookState()
        val mergedWorkbook = progress.interactiveWorkbooks.copy(pageProgress = localWorkbook.pageProgress)
        val next = current.copy(
            bibleProgressByUid = current.bibleProgressByUid + (progress.uid to progress.bibleProgress),
            completedActivityIdsByUid = current.completedActivityIdsByUid +
                (progress.uid to progress.completedActivityIds),
            completedStudyPlanIdsByUid = current.completedStudyPlanIdsByUid +
                (progress.uid to progress.completedStudyPlanIds),
            eventNotebooksByUid = current.eventNotebooksByUid + (progress.uid to progress.eventNotebooks),
            interactiveWorkbooksByUid = current.interactiveWorkbooksByUid +
                (progress.uid to mergedWorkbook),
            bibleProgress = if (current.activeUid == progress.uid) progress.bibleProgress else current.bibleProgress,
            completedActivityIds = if (current.activeUid == progress.uid) {
                progress.completedActivityIds
            } else current.completedActivityIds,
            completedStudyPlanIds = if (current.activeUid == progress.uid) {
                progress.completedStudyPlanIds
            } else current.completedStudyPlanIds,
            eventNotebooks = if (current.activeUid == progress.uid) {
                progress.eventNotebooks
            } else current.eventNotebooks,
            interactiveWorkbooks = if (current.activeUid == progress.uid) {
                mergedWorkbook
            } else current.interactiveWorkbooks,
        ).withFreshPlans()
        commit(next)
    }

    /** Merge page-sized cloud records without replacing newer offline edits. */
    fun applyCloudWorkbookPages(uid: String, cloudPages: Map<String, WorkbookPageProgress>) {
        if (uid.isBlank()) return
        val current = mutableState.value
        val localWorkbook = current.interactiveWorkbooksByUid[uid]
            ?: if (current.activeUid == uid) current.interactiveWorkbooks else InteractiveWorkbookState()
        val mergedPages = (localWorkbook.pageProgress.keys + cloudPages.keys).associateWith { pageKey ->
            val local = localWorkbook.pageProgress[pageKey]
            val cloud = cloudPages[pageKey]
            when {
                local == null -> cloud ?: WorkbookPageProgress()
                cloud == null -> local
                cloud.updatedAtEpochMillis >= local.updatedAtEpochMillis -> cloud
                else -> local
            }
        }
        val mergedWorkbook = localWorkbook.copy(pageProgress = mergedPages)
        commit(current.copy(
            interactiveWorkbooksByUid = current.interactiveWorkbooksByUid + (uid to mergedWorkbook),
            interactiveWorkbooks = if (current.activeUid == uid) mergedWorkbook else current.interactiveWorkbooks,
        ))
    }

    fun applyCloudFamilyBoard(board: FamilyWorshipBoard) {
        val current = mutableState.value
        val merged = ensureMember(board, current.profile)
        commit(
            current.copy(
                familyBoard = merged,
                eventNotebooks = current.eventNotebooks.withSharedEvents(merged.spiritualEvents),
            ),
        )
    }

    fun applyHouseholdIdentity(role: FamilyBoardRole, familyName: String, ownerUid: String) {
        val current = mutableState.value
        val profile = current.profile.copy(role = role, googleConnected = true)
        val profiles = current.localProfiles.map { if (it.uid == profile.uid) profile else it }
        val board = ensureMember(
            current.familyBoard.copy(
                familyName = familyName.ifBlank { current.familyBoard.familyName },
                creatorUid = ownerUid.ifBlank { current.familyBoard.creatorUid },
                inviteCode = "",
            ),
            profile,
        )
        commit(current.copy(profile = profile, localProfiles = profiles, familyBoard = board))
    }

    private fun CompanionHubState.rememberActiveProgress(): CompanionHubState {
        if (activeUid.isBlank()) return this
        return copy(
            bibleProgressByUid = bibleProgressByUid + (activeUid to bibleProgress),
            completedActivityIdsByUid = completedActivityIdsByUid + (activeUid to completedActivityIds),
            completedStudyPlanIdsByUid = completedStudyPlanIdsByUid + (activeUid to completedStudyPlanIds),
            eventNotebooksByUid = eventNotebooksByUid + (activeUid to eventNotebooks),
            interactiveWorkbooksByUid = interactiveWorkbooksByUid + (activeUid to interactiveWorkbooks),
        )
    }

    private fun CompanionHubState.withFreshPlans(): CompanionHubState {
        val usableProfile = profile.takeIf { it.uid.isNotBlank() } ?: return this
        return copy(
            dailyPlan = YouthStudyPlanner.dailyPlan(usableProfile, bibleProgress),
            weeklyPlan = YouthStudyPlanner.weeklyPlan(usableProfile, bibleProgress),
        )
    }

    private fun ensureMember(board: FamilyWorshipBoard, profile: CompanionProfile): FamilyWorshipBoard {
        val member = FamilyMemberProfile(
            uid = profile.uid,
            displayName = profile.displayName,
            ageGroup = profile.ageGroup,
            ageSource = profile.ageSource,
            role = profile.role,
            googleConnected = profile.googleConnected,
        )
        return board.copy(members = board.members.filterNot { it.uid == profile.uid } + member)
    }

    private fun EventNotebookState.withSharedEvents(sharedEvents: List<ConfiguredSpiritualEvent>): EventNotebookState {
        if (sharedEvents.isEmpty()) return this
        val selected = selectedEventId.takeIf { id -> sharedEvents.any { it.id == id } }
            ?: sharedEvents.minByOrNull { it.startDateIso }?.id.orEmpty()
        return copy(events = sharedEvents, selectedEventId = selected)
    }

    private fun readingKey(progress: BiblePlanProgress, reading: BibleReadingDay): String =
        "${progress.mode.name}:${progress.activeJourneyId}:${reading.dayNumber}:${reading.reference}"

    private fun commit(next: CompanionHubState) {
        val stamped = next.copy(lastSavedEpochMillis = System.currentTimeMillis())
        mutableState.value = stamped
        preferences.edit().putString(KEY_STATE, json.encodeToString(stamped)).apply()
    }

    private fun loadState(): CompanionHubState {
        val raw = preferences.getString(KEY_STATE, null) ?: return CompanionHubState()
        return runCatching { json.decodeFromString<CompanionHubState>(raw) }.getOrElse { CompanionHubState() }
    }

    private data class MinistryTopic(
        val title: String,
        val concern: String,
        val scripture: String,
        val questions: List<String>,
        val tryToday: String,
        val scenario: String,
    )

    private val ministryTopics = listOf(
        MinistryTopic(
            title = "Listen before choosing the scripture",
            concern = "People may be carrying financial, family, or emotional pressure that is not obvious at the door.",
            scripture = "James 1:19",
            questions = listOf(
                "What clues may reveal the concern that matters most to the person?",
                "Why is a sincere follow-up question often better than immediately returning to our prepared presentation?",
                "How can we listen without turning the visit into an interrogation?",
            ),
            tryToday = "Ask one sincere follow-up question before deciding which scripture to share.",
            scenario = "A householder says, “I am sorry, I have too much going on right now.” Practice a respectful response that acknowledges the pressure and leaves room for a brief comforting thought.",
        ),
        MinistryTopic(
            title = "Comfort people affected by frightening news",
            concern = "Repeated reports of violence, war, disasters, or instability may leave people anxious or emotionally numb.",
            scripture = "Psalm 46:1-3",
            questions = listOf(
                "How can we acknowledge fear without repeating sensational details?",
                "What does this scripture reveal about Jehovah rather than merely describing the crisis?",
                "How can we avoid suggesting that we know exactly how the person feels?",
            ),
            tryToday = "Use calm language and focus on Jehovah's qualities instead of the disturbing headline.",
            scenario = "A person says that the news makes the future feel hopeless. Prepare a 30-second reply that validates the concern and introduces the scripture naturally.",
        ),
        MinistryTopic(
            title = "Help someone under economic pressure",
            concern = "Housing, food, employment, and debt concerns can make it difficult for people to concentrate on spiritual matters.",
            scripture = "Matthew 6:31-33",
            questions = listOf(
                "Why should we avoid offering an easy answer to a serious financial problem?",
                "How can this scripture provide reassurance without minimizing practical needs?",
                "What tone would show personal interest rather than sounding rehearsed?",
            ),
            tryToday = "Acknowledge the practical concern first, then ask permission before sharing the scripture.",
            scenario = "A parent says that nearly all of their energy goes into keeping up with expenses. Practice a compassionate bridge to the scripture.",
        ),
        MinistryTopic(
            title = "Adapt when a person is busy",
            concern = "Many people are rushed, working unusual hours, caring for relatives, or managing several responsibilities.",
            scripture = "Colossians 4:5-6",
            questions = listOf(
                "What shows that we respect the person's time?",
                "How can a brief conversation still be warm and meaningful?",
                "When is it better to arrange another time instead of trying to finish a presentation?",
            ),
            tryToday = "Offer a one-sentence thought and let the person choose whether to continue.",
            scenario = "The door opens and the person immediately says they have less than a minute. Practice a respectful response and a graceful close.",
        ),
        MinistryTopic(
            title = "Speak with someone who has lost trust in religion",
            concern = "Abuse, hypocrisy, political involvement, or disappointment may have caused some people to distrust all organized religion.",
            scripture = "Micah 6:8",
            questions = listOf(
                "Why is defending religion in general usually not helpful?",
                "How can we distinguish Jehovah's standards from human wrongdoing?",
                "What question could invite the person to explain what damaged their trust?",
            ),
            tryToday = "Do not argue with the person's experience. Listen, then focus on one clear Bible standard.",
            scenario = "A householder says, “Religion is responsible for too much harm.” Practice a reply that is calm, honest, and not defensive.",
        ),
        MinistryTopic(
            title = "Work supportively with a ministry partner",
            concern = "A good partner can help notice the householder's reaction, remember a return-visit detail, and strengthen a nervous publisher.",
            scripture = "Ecclesiastes 4:9-10",
            questions = listOf(
                "How can the partner support without interrupting or taking over?",
                "What quick plan can be made before approaching the territory?",
                "How can partners give kind feedback after a conversation?",
            ),
            tryToday = "Before starting, agree on one way the partner can help and one detail both will watch for.",
            scenario = "A newer publisher loses their place during the presentation. Practice how the partner can help naturally without embarrassment.",
        ),
        MinistryTopic(
            title = "Know when to end a conversation respectfully",
            concern = "Some conversations become argumentative, repetitive, or unsafe, and continuing may not be productive.",
            scripture = "2 Timothy 2:23-25",
            questions = listOf(
                "What signs show that the goal has shifted from understanding to argument?",
                "How can mildness be maintained while ending the conversation?",
                "Why is leaving respectfully not the same as losing a debate?",
            ),
            tryToday = "Prepare one courteous closing sentence before entering the territory.",
            scenario = "A person repeatedly interrupts and demands a political argument. Practice ending the conversation warmly and without sarcasm.",
        ),
        MinistryTopic(
            title = "Follow up on genuine interest",
            concern = "Interest can be lost when details are not recorded or when the return visit repeats the first conversation without moving forward.",
            scripture = "1 Corinthians 3:6-7",
            questions = listOf(
                "Which detail from the first conversation should shape the return visit?",
                "How can we prepare one question that continues the person's own line of thought?",
                "Why should we rely on Jehovah rather than pressure the person for progress?",
            ),
            tryToday = "Record one exact concern, question, or promised reference immediately after the conversation.",
            scenario = "A person previously asked why God permits suffering. Prepare a return visit that begins with their question instead of a new subject.",
        ),
    )

    private fun inferConcern(headline: String, fallback: String): String {
        val normalized = headline.lowercase(Locale.US)
        return when {
            listOf("war", "attack", "violence", "shoot", "conflict").any(normalized::contains) ->
                "Recent reports of conflict or violence may be increasing fear, grief, or uncertainty for people in the territory."
            listOf("storm", "flood", "fire", "earthquake", "heat", "tornado").any(normalized::contains) ->
                "Severe weather or disaster reports may be affecting safety, property, routines, or emotional stability."
            listOf("job", "inflation", "price", "rent", "housing", "econom").any(normalized::contains) ->
                "Economic and housing pressure may be affecting how people think about the present and the future."
            listOf("health", "hospital", "disease", "mental", "lonely").any(normalized::contains) ->
                "Health concerns or isolation may be leaving some people exhausted, uncertain, or in need of patient listening."
            else -> fallback
        }
    }

    private fun inviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString {
            repeat(7) { index ->
                if (index == 4) append('-')
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    private companion object {
        const val PREFERENCES = "msc_companion_hub"
        const val KEY_STATE = "state_json"
    }
}
