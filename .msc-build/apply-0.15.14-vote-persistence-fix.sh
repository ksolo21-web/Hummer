#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path

family = Path("MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt")
tests = Path("MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyVoteReconciliationTest.kt")

text = family.read_text(encoding="utf-8")

old_collector = '''        scope.launch {
            companionHubRepository.state.collectLatest { hubState ->
                if (!familySnapshotsReady() || boundUid.isBlank()) return@collectLatest
                familyUploadJob?.cancel()
                familyUploadJob = scope.launch {
                    delay(650)
                    synchronizeLocalFamilyState(hubState)
                }
            }
        }
'''
new_collector = '''        scope.launch {
            companionHubRepository.state.collectLatest { hubState ->
                if (!familySnapshotsReady() || boundUid.isBlank()) return@collectLatest
                capturePendingVoteIntent(hubState)
                familyUploadJob?.cancel()
                familyUploadJob = scope.launch {
                    delay(250)
                    synchronizeLocalFamilyState(hubState)
                }
            }
        }
'''
if text.count(old_collector) != 1:
    raise SystemExit("family upload collector anchor mismatch")
text = text.replace(old_collector, new_collector, 1)

sync_anchor = '''    private suspend fun syncVotes(
        householdRef: com.google.firebase.firestore.DocumentReference,
        board: FamilyWorshipBoard,
        localProfileUids: Set<String>,
    ) {
        val desiredKeys = board.ideas.flatMap { idea ->
            idea.voterUids.filter { it in localProfileUids }.map { voterUid -> voteKey(idea.id, voterUid) }
        }.toSet()

'''
sync_replacement = '''    private fun capturePendingVoteIntent(hubState: CompanionHubState) {
        val localProfileUids = hubState.localProfiles.map { it.uid }.toSet() + boundUid
        val desiredKeys = desiredVoteKeys(hubState.familyBoard, localProfileUids)
        val remoteOwnVoteKeys = cloudVotes
            .filter { it.createdByUid == boundUid }
            .map { voteKey(it.ideaId, it.voterUid) }
            .toSet()
        val delta = computeFamilyVoteIntentDelta(desiredKeys, remoteOwnVoteKeys)
        if (delta.additions == pendingVoteAdditions && delta.removals == pendingVoteRemovals) return
        pendingVoteAdditions = delta.additions
        pendingVoteRemovals = delta.removals
        publishCombinedCloudBoard()
    }

    private fun desiredVoteKeys(
        board: FamilyWorshipBoard,
        localProfileUids: Set<String>,
    ): Set<String> = board.ideas.flatMap { idea ->
        idea.voterUids
            .filter { it in localProfileUids }
            .map { voterUid -> voteKey(idea.id, voterUid) }
    }.toSet()

    private suspend fun syncVotes(
        householdRef: com.google.firebase.firestore.DocumentReference,
        board: FamilyWorshipBoard,
        localProfileUids: Set<String>,
    ) {
        val desiredKeys = desiredVoteKeys(board, localProfileUids)

'''
if text.count(sync_anchor) != 1:
    raise SystemExit("syncVotes desired-key anchor mismatch")
text = text.replace(sync_anchor, sync_replacement, 1)

helper_anchor = '''internal fun reconcileFamilyVoteKeys(
    cloudVoteKeys: Set<String>,
    pendingAdditions: Set<String>,
    pendingRemovals: Set<String>,
): Set<String> = (cloudVoteKeys + pendingAdditions) - pendingRemovals

'''
helper_replacement = '''internal data class FamilyVoteIntentDelta(
    val additions: Set<String>,
    val removals: Set<String>,
)

internal fun computeFamilyVoteIntentDelta(
    desiredVoteKeys: Set<String>,
    remoteOwnVoteKeys: Set<String>,
): FamilyVoteIntentDelta = FamilyVoteIntentDelta(
    additions = desiredVoteKeys - remoteOwnVoteKeys,
    removals = remoteOwnVoteKeys - desiredVoteKeys,
)

internal fun reconcileFamilyVoteKeys(
    cloudVoteKeys: Set<String>,
    pendingAdditions: Set<String>,
    pendingRemovals: Set<String>,
): Set<String> = (cloudVoteKeys + pendingAdditions) - pendingRemovals

'''
if text.count(helper_anchor) != 1:
    raise SystemExit("vote reconciliation helper anchor mismatch")
text = text.replace(helper_anchor, helper_replacement, 1)
family.write_text(text, encoding="utf-8")

if not tests.exists():
    raise SystemExit("0.15.12 vote reconciliation tests are missing")
test_text = tests.read_text(encoding="utf-8")
extra_tests = '''
    @Test
    fun tapIsCapturedBeforeTheDebouncedCloudWriteStarts() {
        assertEquals(
            FamilyVoteIntentDelta(
                additions = setOf("idea-a\\u001fvoter-a"),
                removals = emptySet(),
            ),
            computeFamilyVoteIntentDelta(
                desiredVoteKeys = setOf("idea-a\\u001fvoter-a"),
                remoteOwnVoteKeys = emptySet(),
            ),
        )
    }

    @Test
    fun rapidUnvoteSupersedesACloudVoteImmediately() {
        assertEquals(
            FamilyVoteIntentDelta(
                additions = emptySet(),
                removals = setOf("idea-a\\u001fvoter-a"),
            ),
            computeFamilyVoteIntentDelta(
                desiredVoteKeys = emptySet(),
                remoteOwnVoteKeys = setOf("idea-a\\u001fvoter-a"),
            ),
        )
    }
'''
if "tapIsCapturedBeforeTheDebouncedCloudWriteStarts" in test_text:
    raise SystemExit("0.15.14 vote tests already applied")
if not test_text.endswith("}\n"):
    raise SystemExit("vote test class closing anchor mismatch")
test_text = test_text[:-2] + extra_tests + "}\n"
tests.write_text(test_text, encoding="utf-8")
PY

grep -Fq 'capturePendingVoteIntent(hubState)' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -Fq 'delay(250)' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -Fq 'computeFamilyVoteIntentDelta' MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt
grep -Fq 'tapIsCapturedBeforeTheDebouncedCloudWriteStarts' MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyVoteReconciliationTest.kt

echo "Applied My Study Companion 0.15.14 vote persistence fix."
