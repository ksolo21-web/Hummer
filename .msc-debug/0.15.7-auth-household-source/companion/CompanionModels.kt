package com.mystudycompanion.app.companion

import kotlinx.serialization.Serializable

@Serializable
enum class AgeGroup(val label: String) {
    CHILD("Child"),
    PRETEEN("Preteen"),
    TEEN("Teen"),
    ADULT("Adult"),
}

@Serializable
enum class ProfileAgeSource {
    GOOGLE_BIRTHDAY,
    GOOGLE_AGE_RANGE,
    USER_CONFIRMED,
    HOUSEHOLD_PROFILE,
    PRIVATE_OWNER,
    UNCONFIRMED,
}

@Serializable
enum class FamilyBoardRole(val label: String) {
    CREATOR("Creator / organizer"),
    CO_ORGANIZER("Co-organizer"),
    MEMBER("Family member"),
}

@Serializable
enum class BibleJourneyCategory(val label: String) {
    STORY("Story Journeys"),
    THEME("Theme Journeys"),
    TIMELINE("Timeline Journeys"),
}

@Serializable
enum class BiblePlanMode(val label: String) {
    STORY_JOURNEYS("Bible stories and themes"),
    GENESIS_TO_REVELATION("Genesis to Revelation"),
}

@Serializable
enum class YouthActivityType(val label: String) {
    COLORING("Coloring page"),
    COLOR_BY_NUMBER("Color by number"),
    WORD_SEARCH("Word search"),
    CROSSWORD("Crossword"),
    SCENARIO("What would you do?"),
    REFLECTION("Reflection worksheet"),
    MEMORY_CARD("Scripture memory card"),
}

@Serializable
data class CompanionProfile(
    val uid: String = "",
    val displayName: String = "Family Member",
    val ageGroup: AgeGroup = AgeGroup.ADULT,
    val ageSource: ProfileAgeSource = ProfileAgeSource.UNCONFIRMED,
    val role: FamilyBoardRole = FamilyBoardRole.MEMBER,
    val territory: String = "",
    val conductsFieldService: Boolean = false,
    val conductorDays: Set<String> = emptySet(),
    val preferJwLibrary: Boolean = true,
    val googleConnected: Boolean = false,
) {
    val needsAgeConfirmation: Boolean
        get() = false
}

@Serializable
data class FamilyMemberProfile(
    val uid: String,
    val displayName: String,
    val ageGroup: AgeGroup,
    val ageSource: ProfileAgeSource = ProfileAgeSource.UNCONFIRMED,
    val role: FamilyBoardRole,
    val googleConnected: Boolean = false,
)

@Serializable
data class FamilyWorshipIdea(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val topic: String,
    val reason: String = "",
    val scripture: String = "",
    val officialUrl: String = "",
    val voterUids: Set<String> = emptySet(),
    val createdAtEpochMillis: Long,
    val used: Boolean = false,
)

@Serializable
data class FamilyWorshipBoard(
    val familyName: String = "My Family",
    val inviteCode: String = "",
    val creatorUid: String = "",
    val members: List<FamilyMemberProfile> = emptyList(),
    val ideas: List<FamilyWorshipIdea> = emptyList(),
    val selectedIdeaId: String? = null,
    val selectedCustomTopic: String = "",
    val scheduledDateIso: String = "",
    val scheduledTime24h: String = "19:00",
    val durationMinutes: Int = 60,
    val recurringWeekly: Boolean = true,
    val spiritualEvents: List<ConfiguredSpiritualEvent> = emptyList(),
)

@Serializable
data class YouthActivity(
    val id: String,
    val type: YouthActivityType,
    val title: String,
    val instructions: String,
    val words: List<String> = emptyList(),
    val clues: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val artTheme: String = "open-bible",
    val seed: Int = 1,
)

@Serializable
data class PersonalStudyPlan(
    val id: String,
    val periodLabel: String,
    val title: String,
    val scriptureReference: String,
    val readingReference: String,
    val focus: String,
    val officialUrl: String,
    val youthOfficialUrl: String = "",
    val youthOfficialLabel: String = "",
    val questions: List<String> = emptyList(),
    val activities: List<YouthActivity> = emptyList(),
    val generatedAtEpochMillis: Long,
)

@Serializable
data class BibleReadingDay(
    val dayNumber: Int,
    val title: String,
    val reference: String,
    val focus: String,
    val questions: List<String>,
)

@Serializable
data class BibleJourney(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val days: List<BibleReadingDay>,
    val tags: Set<String> = emptySet(),
    val category: BibleJourneyCategory = BibleJourneyCategory.STORY,
)

@Serializable
data class BiblePlanProgress(
    val mode: BiblePlanMode = BiblePlanMode.STORY_JOURNEYS,
    val activeJourneyId: String = "david",
    val activeJourneyDayIndex: Int = 0,
    val canonicalDayIndex: Int = 0,
    val canonicalPaceDays: Int = 365,
    val completedReadingKeys: Set<String> = emptySet(),
)

@Serializable
data class AwarenessHeadline(
    val title: String,
    val domain: String,
    val url: String,
    val seenDate: String = "",
)

@Serializable
data class MinistryOutline(
    val id: String,
    val title: String,
    val territoryConcern: String,
    val scriptureReference: String,
    val questions: List<String>,
    val tryToday: String,
    val scenario: String,
    val officialUrl: String,
    val generatedAtEpochMillis: Long,
    val currentEventContext: String = "",
)

@Serializable
data class MinistryState(
    val topicRotationIndex: Int = 0,
    val currentOutline: MinistryOutline? = null,
    val headlines: List<AwarenessHeadline> = emptyList(),
    val lastAwarenessRefreshEpochMillis: Long = 0L,
    val awarenessError: String? = null,
    val refreshingAwareness: Boolean = false,
)

@Serializable
data class SharedMemberProgress(
    val uid: String,
    val bibleProgress: BiblePlanProgress = BiblePlanProgress(),
    val completedActivityIds: Set<String> = emptySet(),
    val completedStudyPlanIds: Set<String> = emptySet(),
    val eventNotebooks: EventNotebookState = EventNotebookState(),
    val interactiveWorkbooks: InteractiveWorkbookState = InteractiveWorkbookState(),
)

@Serializable
data class CompanionHubState(
    val activeUid: String = "",
    val profile: CompanionProfile = CompanionProfile(),
    val localProfiles: List<CompanionProfile> = emptyList(),
    val familyBoard: FamilyWorshipBoard = FamilyWorshipBoard(),
    val bibleProgress: BiblePlanProgress = BiblePlanProgress(),
    val bibleProgressByUid: Map<String, BiblePlanProgress> = emptyMap(),
    val dailyPlan: PersonalStudyPlan? = null,
    val weeklyPlan: PersonalStudyPlan? = null,
    val completedActivityIds: Set<String> = emptySet(),
    val completedActivityIdsByUid: Map<String, Set<String>> = emptyMap(),
    val completedStudyPlanIds: Set<String> = emptySet(),
    val completedStudyPlanIdsByUid: Map<String, Set<String>> = emptyMap(),
    val eventNotebooks: EventNotebookState = EventNotebookState(),
    val eventNotebooksByUid: Map<String, EventNotebookState> = emptyMap(),
    val interactiveWorkbooks: InteractiveWorkbookState = InteractiveWorkbookState(),
    val interactiveWorkbooksByUid: Map<String, InteractiveWorkbookState> = emptyMap(),
    val ministry: MinistryState = MinistryState(),
    val lastSavedEpochMillis: Long = 0L,
)
