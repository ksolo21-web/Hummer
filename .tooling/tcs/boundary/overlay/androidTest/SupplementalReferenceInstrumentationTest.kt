package com.koenterprises.territorycardstudio

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SupplementalReferenceInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun repeatedImportAndRecoveryRerenderReferencePreview() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val expected="2220b772daef3aff751a4fcffddfdbf385944af9cffad9378e2aaccb0bc81a94"
        val current="8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d"
        val input=File(context.cacheDir,"repeat-reference.pdf")
        instrumentation.context.assets.open("phase7-reference-a265.pdf").use {src->input.outputStream().use {src.copyTo(it)}}
        val store=SupplementalBuildingReferenceStore(context)
        try {
            val retained=store.importReference(Uri.fromFile(input),expected,current)
            val generation=mutableStateOf(0);var readable=false
            rule.setContent {SupplementalReferencePreview(retained,generation.value){readable=it}}
            rule.waitUntil(30000){readable}
            repeat(2){index ->
                if(index==1)retained.delete()
                assertEquals(retained.path,store.importReference(Uri.fromFile(input),expected,current).path)
                rule.runOnIdle {readable=false;generation.value++}
                rule.waitUntil(30000){readable}
            }
        } finally {input.delete()}
    }

    @Test fun exactReferenceIsRetainedWithoutReplacingCurrentGeometrySource() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val expected="2220b772daef3aff751a4fcffddfdbf385944af9cffad9378e2aaccb0bc81a94"
        val current="8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d"
        val input=File(context.cacheDir,"supplemental-test.pdf")
        instrumentation.context.assets.open("phase7-reference-a265.pdf").use {src->input.outputStream().use {src.copyTo(it)}}
        val store=SupplementalBuildingReferenceStore(context)
        try {
            val retained=store.importReference(Uri.fromFile(input),expected,current)
            assertEquals(expected,retained.inputStream().use(BundleIntegrity::sha256))
            assertEquals(expected,store.verifiedFile(expected).inputStream().use(BundleIntegrity::sha256))
            assertTrue(runCatching {store.importReference(Uri.fromFile(input),"c".repeat(64),current)}.isFailure)
            assertTrue(runCatching {store.importReference(Uri.fromFile(input),expected,expected)}.isFailure)
            val b=BuildingGeometry("current-only","500","apartment",true,"",listOf("500"),emptyList(),listOf(Point2D(300.0,100.0),Point2D(340.0,100.0),Point2D(340.0,140.0)))
            val pending=SupplementalBuildingReference(expected,current,SupplementalBuildingReferenceContract.contentSha256(b),"Page 1 upper building","Same footprint beside entrance visible in current screenshot")
            assertTrue(SupplementalBuildingReferenceContract.failures(pending,b,current,expected).contains("SUPPLEMENTAL_MATCH_UNCONFIRMED"))
            assertTrue(SupplementalBuildingReferenceContract.failures(pending.copy(confirmed=true),b,current,expected).isEmpty())
            assertTrue(SupplementalBuildingReferenceContract.failures(pending.copy(confirmed=true),b.copy(assigned=false),current,expected).contains("SUPPLEMENTAL_BUILDING_CHANGED"))
            Phase6NativeFixture(WorkspaceMode.REGULAR).use { x ->
                val slot=x.slot.copy(referenceSha256=expected)
                val kb=x.kb.copy(assignments=x.kb.assignments+(x.id to slot))
                val src=x.sources.importFromStream(slot,"synthetic-current.png","image/png",x.sourcePng().inputStream())
                val root=File(x.root,"supplemental-native")
                val drafts=AndroidNativeDraftStore(context,kb,x.sources,root)
                val road=RoadGeometry("context-1","Alpha Rd","alpha rd","context","context","",false,"termination","termination",4.0,listOf(Point2D(225.0,200.0),Point2D(600.0,200.0)))
                val assignment=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,kb.revision,"current_authoritative_assignment","0".repeat(64),src.sourceFilename,"Rochester","9/28/2026",listOf("Directions: Synthetic storage test only."),"full_map",slot.housingType,RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,listOf(road),listOf(b))
                val match=pending.copy(currentSourceSha256=src.sha256)
                val facts=NativeSourceReconciliation(x.id,"REGULAR",kb.revision,src.sha256,expected,"current_assignment_map","Synthetic reviewer","2026-09-28T20:00:00Z",NativeSourceReconciliationContract.assignmentContentSha256(assignment),null,false,false,false,false,
                    listOf(SourceSegmentObservation(road.segmentId,road.name,road.status,road.role,road.insideSide,road.accessOnly,road.endpointAKind,road.endpointBKind,"Synthetic source geometry",false)),
                    listOf(SourceBuildingObservation(b.buildingId,b.sourceMembers,b.assigned,"Synthetic storage binding only",false,match)),"00000000-0000-0000-0000-000000000050",null)
                val saved=drafts.save(NativeAuthoringDraft(assignment,facts,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList()),null)
                val reloaded=AndroidNativeDraftStore(context,kb,x.sources,root).read(x.id,WorkspaceMode.REGULAR)
                assertEquals(saved,reloaded)
                assertEquals(match,reloaded!!.reconciliation.buildings.single().supplementalReference)
                assertEquals(expected,File(root,"map-evidence/$expected.supplemental.source").inputStream().use(BundleIntegrity::sha256))
                assertTrue(runCatching {drafts.register(x.id,WorkspaceMode.REGULAR,saved.revisionSha256)}.isFailure)
            }
        } finally {input.delete()}
    }
}
