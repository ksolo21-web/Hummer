package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.OverlapCandidate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2IDashboardInstrumentationTest {
    private val kb get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        as TerritoryCardStudioApplication).services.knowledgeBase

    @Test fun realDashboardFiltersAssignmentsAndHonestScreeningRecords() {
        val model = TerritoryDashboardModel.from(kb)
        assertEquals(kb.assignments.size, model.items.size)
        assertEquals(kb.coverageCandidates.size, model.screeningItems.size)
        assertEquals(kb.populationSummary.coverageScreeningCandidates, model.screeningCount)
        assertEquals(20, model.screeningCount)
        assertTrue(model.screeningItems.all { it.candidate.officialTerritoryNumber == null })
        assertTrue(model.screeningItems.all { !it.candidate.fieldReleaseAllowed })
        assertTrue(model.screeningItems.none { candidate -> model.items.any { it.assignment.displayId == candidate.candidate.candidateId } })

        val c01 = model.browse("Brown Road", TerritoryDashboardFilter.SCREENING)
        assertEquals(listOf("C01"), c01.screenings.map { it.candidate.candidateId })
        assertTrue(c01.territories.isEmpty())
        assertEquals(0, model.browse("NO_SUCH_RECORD", TerritoryDashboardFilter.ALL).totalCount)

        TerritoryDashboardFilter.entries.filter { it.status != null }.forEach { filter ->
            val result = model.browse("", filter)
            assertTrue(result.territories.all { it.status == filter.status })
            assertEquals(filter == TerritoryDashboardFilter.SCREENING, result.screenings.isNotEmpty())
        }
    }

    @Test fun readinessUsesEveryScopedFindingNotBooleanConflictPresence() {
        val assignment = kb.assignments.values.first()
        val item = TerritoryDashboardModel.from(kb).items.first { it.assignment.displayId == assignment.displayId }
        val blockers = (1..3).map { OverlapCandidate("blocked-$it", listOf(assignment.displayId), "test", "test blocker $it") }
        val warnings = (1..4).map { OverlapCandidate("review-$it", listOf(assignment.displayId), "test", "test review $it") }
        val changed = kb.copy(crossTerritoryOverlapAudit = kb.crossTerritoryOverlapAudit.copy(
            blockingDuplicateWork = blockers, blockingCount = blockers.size,
            reviewCandidates = warnings, reviewCandidateCount = warnings.size
        ))
        val readiness = WorkspaceReadinessModel.from(item, changed)
        assertEquals(3, readiness.blockingConflicts)
        assertEquals(4, readiness.overlapReviewFindings)
    }

    @Test fun completeWorkspaceCollectionsHaveNoPresentationCeiling() {
        val model = TerritoryDashboardModel.from(kb)
        val roads = model.items.first { it.assignment.displayId == "A289" }.assignment
        val buildings = model.items.first { it.assignment.displayId == "A320" }.assignment
        assertTrue(roads.roads.size > 20)
        assertEquals(roads.roadCount, roads.roads.size)
        assertTrue(buildings.buildings.size > 12)
        assertEquals(buildings.buildingCount, buildings.buildings.size)
        val hashes = (1..8).map { it.toString().padStart(64, '0') }
        assertEquals(hashes, roads.copy(sourceHashes = hashes).sourceHashes)
    }
}
