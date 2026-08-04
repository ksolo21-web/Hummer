#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

root = Path('MyStudyCompanion')
family = root / 'app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt'
worship = root / 'app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt'

text = family.read_text(encoding='utf-8')
import_anchor = 'import com.mystudycompanion.app.companion.FamilyWorshipIdea\n'
import_line = 'import com.mystudycompanion.app.companion.JwLibraryLinkResolver\n'
if import_line not in text:
    if import_anchor not in text:
        raise SystemExit('0.15.11 import anchor not found')
    text = text.replace(import_anchor, import_anchor + import_line, 1)

start_marker = '    suspend fun generateAndSend(scheduledDate: LocalDate, topic: String) {'
end_marker = '    fun clearMessage() {'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('0.15.11 generateAndSend markers not found')

replacement = r'''    suspend fun generateAndSend(scheduledDate: LocalDate, topic: String) {
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
            val cleanTopic = topic.trim().replace(Regex("\\s+"), " ").take(300)
            require(cleanTopic.length >= 3) { "Enter a Family Worship topic." }

            val study = if (backendConfig.isConfigured) {
                val generation = backendApi.generateFamilyWorship(
                    GenerateFamilyWorshipRequestDto(
                        householdId = current.householdId,
                        scheduledDateIso = scheduledDate.toString(),
                        topic = cleanTopic,
                        notifyDevices = true,
                    ),
                )
                require(generation.generated) {
                    generation.reason.ifBlank { "The private service did not generate a plan." }
                }

                when (val syncResult = contentSyncEngine.sync("family_worship_generated")) {
                    is SyncResult.Success -> Unit
                    SyncResult.NotConfigured -> error("The private official-content service is not configured.")
                    is SyncResult.UpdateRequired -> error(syncResult.message)
                    is SyncResult.Offline -> error(syncResult.message)
                    is SyncResult.SecurityRejected -> error(syncResult.message)
                    is SyncResult.Failed -> error(syncResult.message)
                }

                val worshipId = generation.contentId.substringAfterLast(':')
                val synchronized = studyRepository.familyWorshipSnapshot(worshipId)
                    ?: error("The signed Family Worship plan was not available after synchronization.")
                FamilyWorshipPublicationValidator.requirePublishable(
                    study = synchronized,
                    expectedHouseholdId = current.householdId,
                    expectedDate = scheduledDate,
                    minimumRevision = generation.revision,
                )
            } else {
                buildOnDeviceOfficialSourcePlan(
                    householdId = current.householdId,
                    scheduledDate = scheduledDate,
                    topic = cleanTopic,
                )
            }

            FamilyWorshipPublicationValidator.requirePublishable(
                study = study,
                expectedHouseholdId = current.householdId,
                expectedDate = scheduledDate,
                minimumRevision = study.revision,
            )
            studyRepository.replaceFamilyWorshipFromHousehold(study)

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
                successMessage = "The official-source Family Worship plan was created and sent to your household.",
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
    }

    private fun buildOnDeviceOfficialSourcePlan(
        householdId: String,
        scheduledDate: LocalDate,
        topic: String,
    ): FamilyWorshipStudy {
        val normalized = topic.lowercase()
        val scriptures = when {
            listOf("decision", "wisdom", "choice", "choose").any(normalized::contains) ->
                listOf("Proverbs 3:5, 6", "James 1:5", "Hebrews 5:14")
            listOf("anxiety", "worry", "stress", "fear").any(normalized::contains) ->
                listOf("Philippians 4:6, 7", "Matthew 6:33, 34", "1 Peter 5:7")
            listOf("forgive", "forgiveness", "conflict", "argument").any(normalized::contains) ->
                listOf("Colossians 3:13", "Ephesians 4:32", "Matthew 5:23, 24")
            listOf("family", "marriage", "love", "respect").any(normalized::contains) ->
                listOf("Colossians 3:14", "Ephesians 5:33", "Proverbs 15:1")
            listOf("peer", "pressure", "courage", "school").any(normalized::contains) ->
                listOf("Proverbs 29:25", "Joshua 1:9", "1 Corinthians 15:33")
            listOf("honest", "honesty", "truth", "integrity").any(normalized::contains) ->
                listOf("Proverbs 12:22", "Hebrews 13:18", "Luke 16:10")
            listOf("prayer", "pray").any(normalized::contains) ->
                listOf("Philippians 4:6", "1 Thessalonians 5:17", "Psalm 65:2")
            listOf("ministry", "preaching", "witness").any(normalized::contains) ->
                listOf("Matthew 28:19, 20", "Colossians 4:6", "2 Timothy 4:5")
            else -> listOf("Psalm 119:105", "Proverbs 2:6", "James 1:22")
        }
        val keyScripture = scriptures.first()
        val revision = System.currentTimeMillis().coerceAtLeast(1L)
        val studyId = "family-${sha256("$householdId|$scheduledDate").take(24)}"
        fun scriptureUrl(reference: String): String = JwLibraryLinkResolver.bibleUrl(reference)

        return FamilyWorshipStudy(
            id = studyId,
            householdId = householdId,
            scheduledDate = scheduledDate,
            title = topic,
            theme = "Using Bible principles to make this practical for our family",
            keyScripture = keyScripture,
            overview = "This plan is created on the device and uses direct New World Translation scripture links. " +
                "It guides the family to research only official JW Library or JW.ORG material, discuss what was learned, and choose a concrete application.",
            preparationQuestion = "What real situation in our family would improve if we applied the Bible principles connected with $topic?",
            officialUrl = scriptureUrl(keyScripture),
            sections = listOf(
                FamilyWorshipSection(
                    id = "$studyId:opening",
                    title = "Opening Scripture and Purpose",
                    detail = "Read $keyScripture in context. In your own words, identify the principle that directly helps with $topic and explain why it matters now.",
                    officialUrl = scriptureUrl(keyScripture),
                    orderIndex = 0,
                ),
                FamilyWorshipSection(
                    id = "$studyId:compare",
                    title = "Compare Supporting Scriptures",
                    detail = "Read ${scriptures[1]} and ${scriptures[2]}. Compare them with $keyScripture. What additional guidance do they give about our thinking, motives, or choices?",
                    officialUrl = scriptureUrl(scriptures[1]),
                    orderIndex = 1,
                ),
                FamilyWorshipSection(
                    id = "$studyId:research",
                    title = "Research Official Material",
                    detail = "In JW Library or on JW.ORG, search for \"$topic\". Choose one official article or video that clearly supports these scriptures. Each person shares one point and identifies the source.",
                    officialUrl = scriptureUrl(scriptures[2]),
                    orderIndex = 2,
                ),
                FamilyWorshipSection(
                    id = "$studyId:practice",
                    title = "Practice the Principle",
                    detail = "Create one realistic family scenario involving $topic. Discuss two possible responses, decide which one follows the scriptures, and practice what to say or do.",
                    officialUrl = scriptureUrl(keyScripture),
                    orderIndex = 3,
                ),
                FamilyWorshipSection(
                    id = "$studyId:goal",
                    title = "Personal Goal and Review",
                    detail = "Each person chooses one specific action to complete before the next Family Worship. End by reviewing the key scripture and including the goal in the closing prayer.",
                    officialUrl = scriptureUrl(scriptures[1]),
                    orderIndex = 4,
                ),
            ),
            revision = revision,
        )
    }

'''
text = text[:start] + replacement + text[end:]
family.write_text(text, encoding='utf-8')

ui_text = worship.read_text(encoding='utf-8')
old_copy = 'Choose the next topic and date. The private service will research only official sources, build the deep dive, sign it, and send the update to your family devices.'
new_copy = 'Choose the next topic and date. The app will build the plan on this device with direct New World Translation links and official-source research steps, then send it to your family devices.'
if old_copy not in ui_text:
    raise SystemExit('0.15.11 organizer copy anchor not found')
worship.write_text(ui_text.replace(old_copy, new_copy, 1), encoding='utf-8')
PY

echo 'Applied My Study Companion 0.15.11 on-device Family Worship generator.'
