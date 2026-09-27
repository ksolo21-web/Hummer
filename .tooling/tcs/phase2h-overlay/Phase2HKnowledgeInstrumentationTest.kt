package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2HKnowledgeInstrumentationTest {
    private val kb get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TerritoryCardStudioApplication).services.knowledgeBase
    @Test fun realRecordsKeepAuthorityAndEverySourcePatchAndFinding() {
        val source = kb
        source.assignments.values.forEach { a ->
            val r = requireNotNull(KnowledgeRecords.resolve(source, a.displayId))
            assertEquals(a, r.assignment)
            assertEquals(a.sourceHashes, r.assignment.sourceHashes)
            assertEquals(source.crossTerritoryOverlapAudit.blockingDuplicateWork.filter { a.displayId in it.territories }, r.blockers)
            assertEquals(source.crossTerritoryOverlapAudit.reviewCandidates.filter { a.displayId in it.territories }, r.warnings)
            assertTrue(r.patches.any { it.id == "GLOBAL-INTERSECTION-COLOR" })
            if (a.needsNewCard) assertFalse(r.referenceEligible)
        }
        assertNull(KnowledgeRecords.resolve(source, "not-a-territory"))
        assertEquals(6, source.assignments.values.count { it.needsNewCard })
    }
    @Test fun missingReferenceAndScopedConflictsRemainTruthful() {
        val source = kb
        val a = source.assignments.values.first { !it.needsNewCard }
        val finding = OverlapCandidate("TEST ROAD", listOf(a.displayId), "test-only", "Synthetic conflict binding check")
        val audit = source.crossTerritoryOverlapAudit.copy(blockingDuplicateWork = listOf(finding), blockingCount = 1,
            reviewCandidates = emptyList(), reviewCandidateCount = 0)
        val changed = source.copy(referenceRoles = source.referenceRoles - a.referenceFile, crossTerritoryOverlapAudit = audit)
        val r = requireNotNull(KnowledgeRecords.resolve(changed, a.displayId))
        assertFalse(r.referenceEligible)
        assertTrue(r.releaseReason.contains("missing"))
        assertEquals(listOf(finding), r.blockers)
        assertTrue(r.warnings.isEmpty())
        val other = source.assignments.keys.first { it != a.displayId }
        assertTrue(requireNotNull(KnowledgeRecords.resolve(changed, other)).blockers.isEmpty())
        assertEquals(source.referenceRoles.size - 1, changed.referenceRoles.size)
    }
}
