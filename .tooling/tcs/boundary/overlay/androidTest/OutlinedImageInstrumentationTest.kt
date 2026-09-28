package com.koenterprises.territorycardstudio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
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
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
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
                .put("rawRoads",outline.roads.size)
                .put("rawCandidates",JSONArray(outline.roads.map {p->JSONObject().put("id",p.road.id).put("name",p.road.name ?: JSONObject.NULL).put("relation",p.relation.name).put("points",JSONArray(p.road.points.map {v->JSONArray(listOf(v.x,v.y))}))}))
                .put("findings",JSONArray(result.findings.map {JSONObject().put("id",it.id).put("message",it.message).put("bounds",it.sourceBounds?.let {b->JSONArray(listOf(b.left,b.top,b.right,b.bottom))} ?: JSONObject.NULL)}))
            File(context.filesDir,"outlined-actual-source-analysis.json").writeText(report.toString(2))
            assertEquals(1079,outline.boundary.width);assertEquals(547,outline.boundary.height)
            assertTrue(outline.boundary.enclosedPixels in 48000..51000)
            assertTrue(result.roads.isNotEmpty());assertTrue(result.roads.all {it.status=="context" && it.role=="context"})
            val pending=OutlinedNativeReview.from(result)
            assertEquals(pending,OutlinedNativeReview(pending.document))
            assertFalse("Unreviewed picture must not authorize registration",pending.complete(result.roads))
            val previewSource=mutableStateOf<File?>(null)
            val uiReview=mutableStateOf(pending)
            rule.setContent {Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedNativeReviewPanel(uiReview.value,result.roads,previewSource.value){r,_->uiReview.value=r}
            }}
            rule.onNodeWithTag("outlined-boundary-confirm").assertIsNotEnabled()
            rule.runOnIdle {previewSource.value=file}
            rule.waitUntil(30000){runCatching {rule.onNodeWithTag("outlined-boundary-confirm").assertIsEnabled();true}.getOrDefault(false)}
            rule.onNodeWithTag("outlined-boundary-confirm").performScrollTo().performClick()
            rule.runOnIdle {assertTrue(uiReview.value.boundaryConfirmed)}
            instrumentation.uiAutomation.takeScreenshot()?.let {bitmap->
                File(context.filesDir,"outlined-native-source-review.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            val boundaryReviewed=pending.confirmBoundary(true)
            assertNotEquals(pending.sha256,boundaryReviewed.sha256)
            assertFalse("Boundary confirmation cannot resolve roads",boundaryReviewed.complete(result.roads))
            Phase6NativeFixture(WorkspaceMode.REGULAR).use {x->
                val imported=x.sources.importFromStream(x.slot,"outlined-source.jpg","image/jpeg",file.inputStream())
                val a=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,x.kb.revision,"current_authoritative_assignment","0".repeat(64),imported.sourceFilename,"Rochester","9/28/2026",listOf("Directions: Synthetic review only; not for field use."),"full_map",x.slot.housingType,RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,result.roads,emptyList())
                val reconciliation=NativeSourceReconciliation(x.id,x.mode.name,x.kb.revision,hash,x.slot.referenceSha256,"current_assignment_map","Synthetic reviewer","2026-09-28T15:00:00Z",NativeSourceReconciliationContract.assignmentContentSha256(a),null,false,false,false,false,result.observations,emptyList(),"00000000-0000-0000-0000-000000000010",null,boundaryReviewed.sha256)
                val olderManual=x.drafts.save(NativeAuthoringDraft(a,reconciliation.copy(mode=WorkspaceMode.LETTER_WRITING.name,imageInterpretationSha256=null),VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList()),null)
                val saved=x.drafts.save(NativeAuthoringDraft(a,reconciliation,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList(),outlinedReview=boundaryReviewed),null)
                val blockedOtherMode=runCatching {x.drafts.register(x.id,WorkspaceMode.LETTER_WRITING,olderManual.revisionSha256)}.exceptionOrNull()
                assertEquals("This source requires its outlined-map review",blockedOtherMode?.message)
                val reloaded=AndroidNativeDraftStore(x.app,x.kb,x.sources,File(x.root,"native")).read(x.id,x.mode)
                val downgraded=saved.copy(outlinedReview=null,reconciliation=saved.reconciliation.copy(imageInterpretationSha256=null))
                assertTrue("Outlined review removal bypassed mandatory gates",runCatching {x.drafts.save(downgraded,saved.revisionSha256)}.isFailure)
                assertEquals(hash,File(x.root,"native/outlined-source-$hash.mode").readText())
                assertEquals(saved,reloaded);assertEquals(boundaryReviewed,reloaded?.outlinedReview)
                assertTrue("Unresolved outlined source registered",runCatching {x.drafts.register(x.id,x.mode,saved.revisionSha256)}.isFailure)
                File(context.filesDir,"outlined-persistence-evidence.json").writeText(JSONObject().put("draftSha256",saved.revisionSha256).put("reviewSha256",boundaryReviewed.sha256).put("reloadMatched",saved==reloaded).put("unresolvedRegistrationBlocked",true).toString(2))
            }
            assertTrue("Vertical Alice Ave was not read",result.recognizedText.any {it.text.contains("Alice",true) && it.text.contains("Ave",true)})
            assertTrue("Vertical Taylor Ave was not read",result.recognizedText.any {it.text.contains("Taylor",true) && it.text.contains("Ave",true)})
        } finally {file.delete()}
    }
    @Test fun splitJoinAndFindingReviewCannotBypassSourceCoverage() {
        val polygon=listOf(Point2D(10.0,10.0),Point2D(90.0,10.0),Point2D(90.0,90.0),Point2D(10.0,90.0))
        val path=listOf(Point2D(20.0,50.0),Point2D(50.0,50.0),Point2D(80.0,50.0))
        val candidate=OutlinedRoadProposal(MapImageRoad("source-a","Alpha Rd","context",path,false,false),BoundaryRoadRelation.INTERIOR)
        val finding=MapImageFinding("component-mixed","Geometry was not recovered",AxisAlignedRect(20.0,40.0,80.0,60.0))
        val extraction=OutlinedMapExtraction(OutlinedMapBoundary(100,100,polygon,6400,AxisAlignedRect(10.0,10.0,90.0,90.0)),listOf(candidate),listOf(finding))
        val transform=OutlinedMapTransform(2.0,200.0,40.0)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixture=File(context.cacheDir,"outlined-span-fixture.png")
        val bitmap=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.LTGRAY)
        val canvas=android.graphics.Canvas(bitmap)
        val paint=android.graphics.Paint().apply {color=android.graphics.Color.BLACK;style=android.graphics.Paint.Style.STROKE;strokeWidth=2f}
        canvas.drawRect(10f,10f,90f,90f,paint);paint.color=android.graphics.Color.WHITE;canvas.drawLine(20f,50f,80f,50f,paint)
        fixture.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val fixtureHash=fixture.inputStream().use(BundleIntegrity::sha256)
        val draft=InterpretedMapDraft(fixtureHash,emptyList(),emptyList(),listOf(finding),emptyList(),outlined=extraction,sourceToPage=transform)
        var review=OutlinedNativeReview.from(draft).confirmBoundary(true)
        fun output(id:String,from:Double,to:Double)=RoadGeometry(id,"Alpha Rd","alpha rd","context","context","",false,"termination","termination",4.0,OutlinedCoverageContract.slice(path,from,to).map(transform::page))
        var roads=listOf(output("part-a",0.0,0.5),output("part-b",0.5,1.0))
        review=review.reviewSpan("source-a",0.0,0.5,OutlinedSpanDisposition.ROAD,"Visible source interval",roads,"part-a")
        assertTrue(review.coverageFailures(roads).any {it.startsWith("SPAN_GAP_OR_OVERLAP") || it.startsWith("UNACCOUNTED_TAIL")})
        review=review.reviewSpan("source-a",0.5,1.0,OutlinedSpanDisposition.ROAD,"Visible source interval",roads,"part-b")
        assertTrue(review.coverageFailures(roads).isEmpty())
        assertTrue(runCatching {review.reviewFinding(finding.id,"MAP_SYMBOL","Mixed component dismissed",emptyList(),roads)}.isFailure)
        assertTrue(review.unresolvedFindings(roads).any {it.id==finding.id})
        val joined=review.join("part-a","part-b",roads);review=joined.first;roads=joined.second
        assertEquals(1,roads.size);assertTrue(review.coverageFailures(roads).isEmpty())
        assertTrue(review.coverageFailures(roads.map {it.copy(name="Changed Rd")}).any {it.startsWith("SPAN_OUTPUT_REVIEW_CHANGED")})
        val undone=review.undoJoin(roads.single().segmentId,roads)
        assertTrue(undone.first.spans.isEmpty());assertTrue(undone.second.isEmpty())
        val invalid=OutlinedNativeReview.from(draft).confirmBoundary(true).reviewSpan("source-a",0.0,0.5,OutlinedSpanDisposition.OUTSIDE_CONTEXT,"Incorrect exterior claim",emptyList(),null)
        assertTrue(invalid.coverageFailures(emptyList()).contains("NONEXTERIOR_CONTEXT_OMISSION:source-a"))
        val uiReview=mutableStateOf(OutlinedNativeReview.from(draft))
        rule.setContent {Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedNativeReviewPanel(uiReview.value,emptyList(),fixture){r,_->uiReview.value=r}
        }}
        rule.waitUntil(30000){runCatching {rule.onNodeWithTag("outlined-boundary-confirm").assertIsEnabled();true}.getOrDefault(false)}
        rule.onNodeWithTag("outlined-span-end").performScrollTo().performTextReplacement("0.5")
        rule.onNodeWithTag("outlined-disposition-OUTSIDE_CONTEXT").performScrollTo().performClick()
        rule.onNodeWithTag("outlined-source-evidence").performScrollTo().performTextReplacement("Incorrect exterior claim")
        rule.activity.runOnUiThread {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)}
        rule.waitForIdle()
        rule.onNodeWithTag("outlined-review-trace").performScrollTo().performClick()
        rule.onNodeWithTag("outlined-review-status").performScrollTo().assertTextContains("NONEXTERIOR_CONTEXT_OMISSION",substring=true)
        rule.runOnIdle {assertTrue(uiReview.value.spans.isEmpty())}
        fixture.delete()
    }

}
