package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@RunWith(AndroidJUnit4::class)
class OutlinedImageInstrumentationTest {
    @Test fun actualOutlinedImagePreservesBoundaryAndReadsVerticalStreetNames() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val file=File(context.cacheDir,"outlined-exact-source.jpg")
        instrumentation.context.assets.open("source-26435.jpg").use {input->file.outputStream().use {input.copyTo(it)}}
        try {
            val hash=file.inputStream().use(BundleIntegrity::sha256)
            assertEquals("a93294659bd8ec40937fde5b20a8e0f89315d20c69ebe25e92318fb5665bbec3",hash)
            val result=AndroidMapImageInterpreter().interpretOutlined(file,hash)
            val outline=requireNotNull(result.outlined);val transform=requireNotNull(result.sourceToPage)
            val report=JSONObject().put("sourceSha256",hash).put("sourceWidth",outline.boundary.width).put("sourceHeight",outline.boundary.height)
                .put("polygon",JSONArray(outline.boundary.polygon.map {JSONArray(listOf(it.x,it.y))}))
                .put("transform",JSONArray(listOf(transform.scale,transform.offsetX,transform.offsetY)))
                .put("text",JSONArray(result.recognizedText.map {JSONObject().put("text",it.text).put("bounds",JSONArray(listOf(it.bounds.left,it.bounds.top,it.bounds.right,it.bounds.bottom)))}))
                .put("roads",JSONArray(result.roads.map {JSONObject().put("id",it.segmentId).put("name",it.name).put("status",it.status).put("points",JSONArray(it.points.map {p->JSONArray(listOf(p.x,p.y))}))}))
                .put("rawRoads",outline.roads.size).put("findings",JSONArray(result.findings.map {JSONObject().put("id",it.id).put("message",it.message)}))
            File(context.filesDir,"outlined-actual-source-analysis.json").writeText(report.toString(2))
            assertEquals(1079,outline.boundary.width);assertEquals(547,outline.boundary.height)
            assertTrue(outline.boundary.enclosedPixels in 48000..51000)
            assertTrue(result.roads.isNotEmpty());assertTrue(result.roads.all {it.status=="context" && it.role=="context"})
            val pending=OutlinedNativeReview.from(result)
            assertEquals(pending,OutlinedNativeReview(pending.document))
            assertFalse("Unreviewed picture must not authorize registration",pending.complete(result.roads))
            val boundaryReviewed=pending.confirmBoundary(true)
            assertNotEquals(pending.sha256,boundaryReviewed.sha256)
            assertFalse("Boundary confirmation cannot resolve roads",boundaryReviewed.complete(result.roads))
            Phase6NativeFixture(WorkspaceMode.REGULAR).use {x->
                val imported=x.sources.importFromStream(x.slot,"outlined-source.jpg","image/jpeg",file.inputStream())
                val a=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,x.kb.revision,"current_authoritative_assignment","0".repeat(64),imported.sourceFilename,"Rochester","9/28/2026",listOf("Directions: Synthetic review only; not for field use."),"full_map",x.slot.housingType,RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,result.roads,emptyList())
                val reconciliation=NativeSourceReconciliation(x.id,x.mode.name,x.kb.revision,hash,x.slot.referenceSha256,"current_assignment_map","Synthetic reviewer","2026-09-28T15:00:00Z",NativeSourceReconciliationContract.assignmentContentSha256(a),null,false,false,false,false,result.observations,emptyList(),"00000000-0000-0000-0000-000000000010",null,boundaryReviewed.sha256)
                val saved=x.drafts.save(NativeAuthoringDraft(a,reconciliation,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList(),outlinedReview=boundaryReviewed),null)
                val reloaded=AndroidNativeDraftStore(x.app,x.kb,x.sources,File(x.root,"native")).read(x.id,x.mode)
                val downgraded=saved.copy(outlinedReview=null,reconciliation=saved.reconciliation.copy(imageInterpretationSha256=null))
                assertTrue("Outlined review removal bypassed mandatory gates",runCatching {x.drafts.save(downgraded,saved.revisionSha256)}.isFailure)
                assertEquals(saved,reloaded);assertEquals(boundaryReviewed,reloaded?.outlinedReview)
                assertTrue("Unresolved outlined source registered",runCatching {x.drafts.register(x.id,x.mode,saved.revisionSha256)}.isFailure)
                File(context.filesDir,"outlined-persistence-evidence.json").writeText(JSONObject().put("draftSha256",saved.revisionSha256).put("reviewSha256",boundaryReviewed.sha256).put("reloadMatched",saved==reloaded).put("unresolvedRegistrationBlocked",true).toString(2))
            }
            assertTrue("Vertical Alice Ave was not read",result.recognizedText.any {it.text.contains("Alice",true) && it.text.contains("Ave",true)})
            assertTrue("Vertical Taylor Ave was not read",result.recognizedText.any {it.text.contains("Taylor",true) && it.text.contains("Ave",true)})
        } finally {file.delete()}
    }
}
