package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase6NativePersistenceInstrumentationTest {
    private fun create(x:Phase6NativeFixture):NativeAuthoringDraft {
        val source=x.sources.importFromStream(x.slot,"synthetic-current.pdf","application/pdf",x.sourcePdf().inputStream())
        val road=RoadGeometry("alpha","Alpha Rd","alpha rd","yellow","perimeter","left",false,"junction","junction",4.0,listOf(Point2D(225.0,115.0),Point2D(650.0,115.0)))
        val a=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,x.kb.revision,"current_authoritative_assignment","0".repeat(64),source.sourceFilename,"Oakland Township","9/28/2026",listOf("Directions: Synthetic regression only."),"full_map",x.slot.housingType,RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,listOf(road),emptyList())
        val r=NativeSourceReconciliation(x.id,x.mode.name,x.kb.revision,source.sha256,x.slot.referenceSha256,"current_assignment_map","Synthetic reviewer","2026-09-28T04:00:00Z",NativeSourceReconciliationContract.assignmentContentSha256(a),null,true,false,false,false,
            listOf(SourceSegmentObservation(road.segmentId,road.name,road.status,road.role,road.insideSide,false,road.endpointAKind,road.endpointBKind,"Page 1 explicit inside-left source",true)),emptyList(),"00000000-0000-0000-0000-000000000001",null)
        return x.drafts.save(NativeAuthoringDraft(a,r,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList()),null)
    }
    private fun rejected(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun replacementFailurePreservesDraftAndArchivedSource() {Phase6NativeFixture(WorkspaceMode.REGULAR).use {x->
        val d=create(x);val registered=x.drafts.register(x.id,x.mode,d.revisionSha256)
        rejected {x.drafts.save(d.copy(assignment=d.assignment.copy(knowledgeBaseRevision="wrong")),d.revisionSha256)}
        assertEquals(d,x.drafts.read(x.id,x.mode));assertEquals(registered.head,x.drafts.ledger.active(x.id,x.mode.name)?.head)
        rejected {x.sources.importFromStream(x.slot,"invalid.pdf","application/pdf","broken".byteInputStream())}
        assertEquals(d.reconciliation.importedSourceSha256,x.sources.verifiedRecord(x.id)?.sha256)
        val original=x.sources.verifiedFile(x.id)!!
        x.sources.importFromStream(x.slot,"replacement.pdf","application/pdf",(x.sourcePdf()+"\n% replacement".toByteArray()).inputStream())
        assertTrue(original.exists());assertEquals(d.reconciliation.importedSourceSha256,BundleIntegrity.sha256(original.inputStream()))
        assertNull(x.drafts.ledger.current(x.id,registered.assignment.authoritySha256))
        val native=File(x.root,"native")
        assertTrue(File(native,"draft-history/${d.revisionSha256}.draft").isFile)
        assertTrue(File(native,"map-evidence/${d.reconciliation.importedSourceSha256}.source").isFile)
        x.sources.clear(x.id);assertTrue(original.exists());assertNull(x.drafts.ledger.current(x.id,registered.assignment.authoritySha256))
    }}
    @Test fun savedEditsInvalidateRegistrationAndPruningRetainsRegisteredEvidence() {Phase6NativeFixture(WorkspaceMode.REGULAR).use {x->
        val d=create(x);val registered=x.drafts.register(x.id,x.mode,d.revisionSha256)
        val changed=x.drafts.save(d.copy(assignment=d.assignment.copy(locality="Changed locality")),d.revisionSha256)
        assertNull(x.drafts.ledger.current(x.id,registered.assignment.authoritySha256))
        x.drafts.pruneUnusedDraftHistory()
        assertTrue(File(x.root,"native/draft-history/${d.revisionSha256}.draft").isFile)
        assertTrue(File(x.root,"native/draft-history/${changed.revisionSha256}.draft").isFile)
        assertEquals(changed,x.drafts.read(x.id,x.mode))
        rejected {x.drafts.save(d,d.revisionSha256)}
        assertEquals(changed,x.drafts.read(x.id,x.mode))
    }}
    @Test fun stageDraftForProcessDeath() {Phase6NativeFixture(WorkspaceMode.REGULAR).use {x->
        val d=create(x);x.drafts.register(x.id,x.mode,d.revisionSha256)
        x.authoring.prepare(x.id,x.mode,d.revisionSha256)
        assertTrue(x.coordinator.state(x.id,x.mode).inputReady)
        File(x.app.filesDir,"phase6-restart-draft.sha256").writeText(d.revisionSha256)
    }}
    @Test fun recoverDraftAfterProcessDeathRequiresFreshPreparationAndApproval() {Phase6NativeFixture(WorkspaceMode.REGULAR,false).use {x->
        val d=requireNotNull(x.drafts.read(x.id,x.mode))
        assertEquals(File(x.app.filesDir,"phase6-restart-draft.sha256").readText(),d.revisionSha256)
        assertNotNull(x.drafts.ledger.active(x.id,x.mode.name))
        assertFalse(x.coordinator.state(x.id,x.mode).inputReady)
        assertFalse(x.lifecycle.state(x.id,x.mode).active)
        rejected {x.output.validate(x.id,x.mode)}
        x.authoring.prepare(x.id,x.mode,d.revisionSha256)
        assertNotNull(x.coordinator.buildFront(x.id,x.mode).front)
        assertFalse(x.lifecycle.state(x.id,x.mode).active)
        rejected {x.output.validate(x.id,x.mode)}
    }}
}
