package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.OverlapCandidate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class Phase2KInstrumentationTest {
    private class Destination(var corrupt: Boolean = false, val afterWrite: (() -> Unit)? = null, val canDelete: Boolean = true) : CreatedExportDestination {
        val saved = ByteArrayOutputStream()
        var deleted = false
        override fun openOutput(): java.io.OutputStream = object : java.io.FilterOutputStream(saved) {
            override fun close() { super.close(); afterWrite?.invoke() }
        }
        override fun openInput() = ByteArrayInputStream(if (corrupt) byteArrayOf(0) else saved.toByteArray())
        override fun deleteCreated(): Boolean { deleted = canDelete; return canDelete }
    }
    @Test fun scopedAuditKeepsExactIdentityAndReadbackWithoutFieldAuthority() {
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2()
            val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == f.identity.displayId }
            val prepared = requireNotNull(f.coordinator.workspaceSnapshot(f.identity.displayId, f.mode))
            val verification = VerificationWorkflowModel.from(item,f.mode,f.source,f.kb,f.app.services.activePolicy,prepared)
            val service = Phase2KAuditService(f.kb,f.coordinator,f.sources,f.app.services.activePolicy)
            val ticket = service.snapshot(item,f.mode)
            val json = JSONObject(String(ticket.bytes))
            assertEquals(f.identity.displayId,json.getString("territory"))
            assertEquals(f.mode.name,json.getString("mode"))
            assertEquals(f.source.sha256,json.getString("sourceSha256"))
            assertEquals(f.input.canonicalSha256(),json.getString("preparedInputSha256"))
            assertEquals(prepared.inventory!!.hash,json.getString("inventorySha256"))
            assertEquals(f.state().front!!.sha256,json.getString("candidateFrontSha256"))
            assertEquals(f.state().packet!!.sha256,json.getString("candidatePacketSha256"))
            assertEquals(f.coordinator.currentCandidateVersion(f.identity.displayId,f.mode),json.getString("candidateVersion"))
            assertTrue(json.getString("fieldRelease").contains("blocked"))
            val destination = Destination()
            assertEquals(ticket.sha256,service.exportCreated(ticket,destination))
            assertArrayEquals(ticket.bytes,destination.saved.toByteArray())
            assertFalse(destination.deleted)
            val tamperedDestination=Destination(corrupt=true)
            assertTrue(runCatching { service.exportCreated(ticket,tamperedDestination) }.isFailure)
            assertTrue(tamperedDestination.deleted)
            val duringWrite=Destination(afterWrite={ f.importSource("changed-during-audit-write") })
            assertTrue(runCatching { service.exportCreated(ticket,duringWrite) }.isFailure)
            assertTrue(duringWrite.deleted)
        }
    }
    @Test fun staleAuditNeverWritesAndUnpreparedEvidenceIsHonest() {
        Phase2DBFixture(true).use { f ->
            val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == f.identity.displayId }
            val service = Phase2KAuditService(f.kb,f.coordinator,f.sources,f.app.services.activePolicy)
            fun model() = VerificationWorkflowModel.from(item,f.mode,f.sources.verifiedRecord(f.identity.displayId),f.kb,
                f.app.services.activePolicy,f.coordinator.workspaceSnapshot(f.identity.displayId,f.mode))
            val empty = JSONObject(String(service.snapshot(item,f.mode).bytes))
            assertEquals("UNVALIDATED_OR_UNPREPARED",empty.getString("findingsState"))
            assertTrue(empty.isNull("preparedInputSha256")); assertTrue(empty.isNull("inventorySha256"))
            f.prepare(); val old = service.snapshot(item,f.mode)
            f.importSource("changed-after-ticket")
            val destination=Destination()
            assertTrue(runCatching { service.exportCreated(old,destination) }.isFailure)
            assertEquals(0,destination.saved.size());assertTrue(destination.deleted)
            val newAudit=JSONObject(String(service.snapshot(item,f.mode).bytes))
            assertTrue(newAudit.isNull("candidateFrontSha256"))
            assertEquals("UNVALIDATED_OR_UNPREPARED",newAudit.getString("findingsState"))
        }
    }
    @Test fun actualOverlapRoadAndBuildingAreItemLinkedWithoutInferredIdentifiers() {
        Phase2DBFixture(false).use { f ->
            val road="Existing Alpha Rd"
            val overlap=OverlapCandidate(road,listOf(f.identity.displayId,"273"),"verified","Source geometry intersection")
            val a=f.slot.copy(buildingReauditFailures=listOf("Building member assignment missing"))
            val kb=f.kb.copy(assignments=f.kb.assignments+(f.identity.displayId to a),
                crossTerritoryOverlapAudit=f.kb.crossTerritoryOverlapAudit.copy(
                reviewCandidates=f.kb.crossTerritoryOverlapAudit.reviewCandidates+overlap,
                reviewCandidateCount=f.kb.crossTerritoryOverlapAudit.reviewCandidateCount+1))
            val item=TerritoryDashboardModel.from(kb).items.first { it.assignment.displayId==f.identity.displayId }
            for (mode in WorkspaceMode.values()) {
            val model=VerificationWorkflowModel.from(item,mode,null,kb,f.app.services.activePolicy)
            val findings=Phase2KFindings.from(item,mode,model,kb,null)
            assertTrue(findings.any { it.category=="overlap" && it.itemId==road && it.tab==(if(mode==WorkspaceMode.REGULAR) WorkspaceTab.STREETS else WorkspaceTab.DETAILS) && it.reason==overlap.reason })
            assertTrue(findings.any { it.category=="buildings" && it.tab==WorkspaceTab.BUILDINGS && it.reason=="Building member assignment missing" })
            assertTrue(findings.all { it.tab in WorkspaceModePolicy.tabs(mode) })
            if(mode!=WorkspaceMode.REGULAR) assertTrue(findings.any { it.category=="inventory" && it.itemId==null && it.itemLabel.contains("unavailable") })
            assertFalse(findings.any { it.itemId=="invented address" })
            }
        }
    }
    @Test fun failedAuditCleanupReportsRemainingCopyTruthfully() {
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2()
            val item=TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId==f.identity.displayId }
            val service=Phase2KAuditService(f.kb,f.coordinator,f.sources,f.app.services.activePolicy)
            val destination=Destination(corrupt=true,canDelete=false)
            val failure=runCatching { service.exportCreated(service.snapshot(item,f.mode),destination) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message!!.contains("could not be deleted"))
            assertFalse(destination.deleted)
            assertTrue(destination.saved.size()>0)
        }
    }

}
